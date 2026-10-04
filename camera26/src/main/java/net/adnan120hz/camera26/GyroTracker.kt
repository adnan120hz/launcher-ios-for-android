package net.adnan120hz.camera26

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ALL software-EIS tuning constants in ONE place — after the user's
 * walking-record test on the real phone, the "smooth, not jelly, not
 * stiff" feel is dialled in by editing ONLY this object.
 */
object EisTuning {
    /** Gaussian trajectory-smoothing window (±ms around each frame). */
    const val SMOOTH_WINDOW_MS = 120L

    /** Never counter-rotate 100% of the shake — keeps pans feeling natural. */
    const val CORRECTION_GAIN = 0.88f

    /** Hard clamp on the per-frame warp angle (degrees). */
    const val MAX_CORRECTION_DEG = 14f

    /** Spike guard: the correction itself may not swing faster (deg/sec). */
    const val CORRECTION_RATE_DEG_S = 300f

    /** Adaptive crop: total crop fraction from tiny to heavy shake. */
    const val CROP_MIN = 0.08f
    const val CROP_MAX = 0.18f

    /** Shake amplitude (deg of raw-vs-smoothed delta) mapped onto the crop. */
    const val SHAKE_LOW_DEG = 0.4f
    const val SHAKE_HIGH_DEG = 4.0f

    /** Per-frame smoothing of the shake estimate and of crop changes:
     *  the crop grows fairly fast (protect the edges) but shrinks slowly
     *  so the view never visibly "zoom-pumps". */
    const val SHAKE_EMA_ALPHA = 0.05f
    const val CROP_GROW_ALPHA = 0.10f
    const val CROP_SHRINK_ALPHA = 0.02f
    const val CROP_INITIAL = 0.12f

    /** Generous 1080p-class encoder bitrate (per-pixel, clamped). */
    const val BITRATE_PER_PIXEL = 9L
    const val BITRATE_MIN = 10_000_000
    const val BITRATE_MAX = 24_000_000

    /** Mild GL unsharp amount after the warp (0 = off). */
    const val UNSHARP_AMOUNT = 0.30f

    /** Crop fraction for a measured shake amplitude. */
    fun cropForShake(ampDeg: Float): Float {
        val t = ((ampDeg - SHAKE_LOW_DEG) / (SHAKE_HIGH_DEG - SHAKE_LOW_DEG))
            .coerceIn(0f, 1f)
        return CROP_MIN + (CROP_MAX - CROP_MIN) * t
    }
}

/** A gain-scaled, clamped counter-rotation for one camera frame. */
data class Correction(val quat: FloatArray, val deltaDeg: Float)

/**
 * Software gyroscope tracker for the ACTION (EIS) pipeline and PANO.
 *
 * Integrates raw angular velocity into device-orientation quaternions on a
 * dedicated sensor thread at the sensor's fastest rate
 * (SENSOR_DELAY_FASTEST), keeping a short ring buffer of timestamped
 * samples. Two queries:
 *  - [rawOrientationAt]: the interpolated raw orientation (PANO yaw).
 *  - [correctionAt]: the EIS counter-rotation for a frame timestamp —
 *    the frame's raw orientation vs a Gaussian-smoothed TRAJECTORY
 *    orientation (window ±[EisTuning.SMOOTH_WINDOW_MS] around the frame),
 *    scaled by [EisTuning.CORRECTION_GAIN] and angle-clamped. Smoothing the
 *    trajectory (instead of chasing the raw orientation) keeps deliberate
 *    pans natural instead of locked-stiff, while the high-frequency shake
 *    — raw minus trajectory — is what gets warped away.
 *
 * Quaternion layout everywhere in this file: [w, x, y, z], Hamilton product.
 * All math is defensive: any failure yields null and the pipeline falls back.
 */
class GyroTracker(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** True when this device really has a gyroscope to integrate. */
    val available: Boolean get() = gyroSensor != null

    private class Sample(val timestampNs: Long, val raw: FloatArray)

    private val lock = Any()
    private val samples = ArrayDeque<Sample>()
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    // Integration state (sensor thread only).
    private var current = floatArrayOf(1f, 0f, 0f, 0f)
    private var lastTimestampNs = 0L

    /** Keep ~3 seconds of samples; frames never look back further. */
    private val bufferNs = 3_000_000_000L

    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not needed */ }

        override fun onSensorChanged(event: SensorEvent) {
            try {
                val ts = event.timestamp
                if (lastTimestampNs == 0L) {
                    lastTimestampNs = ts
                    push(ts)
                    return
                }
                var dt = (ts - lastTimestampNs) / 1e9f
                lastTimestampNs = ts
                if (dt <= 0f) return
                if (dt > 0.05f) dt = 0.05f // sensor stalls must not explode the integral

                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                val omega = sqrt(wx * wx + wy * wy + wz * wz)
                if (omega > 1e-6f) {
                    val halfAngle = omega * dt / 2f
                    val s = sin(halfAngle) / omega
                    val dq = floatArrayOf(cos(halfAngle), wx * s, wy * s, wz * s)
                    current = normalize(multiply(current, dq))
                }
                push(ts)
            } catch (e: Throwable) {
                // A bad sample must never kill the pipeline.
            }
        }
    }

    private fun push(ts: Long) {
        synchronized(lock) {
            samples.addLast(Sample(ts, current.copyOf()))
            while (samples.size > 2 && ts - samples.first().timestampNs > bufferNs) {
                samples.removeFirst()
            }
        }
    }

    fun start() {
        if (gyroSensor == null || thread != null) return
        try {
            current = floatArrayOf(1f, 0f, 0f, 0f)
            lastTimestampNs = 0L
            synchronized(lock) { samples.clear() }
            val t = HandlerThread("camera26-gyro")
            t.start()
            thread = t
            handler = Handler(t.looper)
            sensorManager?.registerListener(
                listener, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST, handler
            )
        } catch (e: Throwable) {
            stop()
        }
    }

    fun stop() {
        try {
            sensorManager?.unregisterListener(listener)
        } catch (e: Throwable) { /* already stopped */ }
        try {
            thread?.quitSafely()
        } catch (e: Throwable) { /* ignore */ }
        thread = null
        handler = null
    }

    /** Interpolated raw orientation at [timestampNs] (caller holds [lock]). */
    private fun rawAtLocked(timestampNs: Long): FloatArray? {
        if (samples.size < 2) return null
        if (timestampNs < samples.first().timestampNs) return null
        if (timestampNs > samples.last().timestampNs) {
            // Small grace window at the head: queries arrive a few ms after
            // the last gyro sample; clamp to the newest sample.
            return if (timestampNs - samples.last().timestampNs < 50_000_000L) {
                samples.last().raw.copyOf()
            } else {
                null
            }
        }
        var hi = 1
        while (hi < samples.size && samples[hi].timestampNs < timestampNs) hi++
        val b = samples[minOf(hi, samples.size - 1)]
        val a = samples[maxOf(0, hi - 1)]
        val span = (b.timestampNs - a.timestampNs).toFloat()
        val t = if (span > 0f) (timestampNs - a.timestampNs) / span else 0f
        return slerp(a.raw, b.raw, t)
    }

    /**
     * Raw orientation at [timestampNs], spherically interpolated between
     * the neighbouring gyro samples; null when the buffer does not cover
     * that time (frame older than the buffer or gyro not running yet).
     */
    fun rawOrientationAt(timestampNs: Long): FloatArray? {
        synchronized(lock) {
            return rawAtLocked(timestampNs)
        }
    }

    /**
     * EIS counter-rotation for a camera frame at [timestampNs]:
     * q_corr = (Gaussian-smoothed trajectory) * (raw)^-1 at that instant,
     * gain-scaled and angle-clamped per [EisTuning]. [Correction.deltaDeg]
     * is the PRE-gain shake angle, used to size the adaptive crop.
     */
    fun correctionAt(timestampNs: Long): Correction? {
        synchronized(lock) {
            if (samples.size < 2) return null
            if (timestampNs < samples.first().timestampNs) return null
            if (timestampNs > samples.last().timestampNs &&
                timestampNs - samples.last().timestampNs >= 50_000_000L
            ) {
                return null
            }
            val raw = rawAtLocked(timestampNs) ?: return null

            // Gaussian-weighted average of the trajectory around the frame
            // (quaternions sign-aligned to the frame's raw orientation so
            // the double cover cannot cancel the average out).
            val windowNs = EisTuning.SMOOTH_WINDOW_MS * 1_000_000L
            val sigma = (windowNs / 2).toDouble().coerceAtLeast(1.0)
            var sw = 0.0
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (s in samples) {
                val dt = (s.timestampNs - timestampNs).toDouble()
                if (dt > windowNs || dt < -windowNs) continue
                val wgt = exp(-(dt * dt) / (2.0 * sigma * sigma))
                var qw = s.raw[0]
                var qx = s.raw[1]
                var qy = s.raw[2]
                var qz = s.raw[3]
                if (qw * raw[0] + qx * raw[1] + qy * raw[2] + qz * raw[3] < 0f) {
                    qw = -qw; qx = -qx; qy = -qy; qz = -qz
                }
                sw += wgt * qw
                sx += wgt * qx
                sy += wgt * qy
                sz += wgt * qz
            }
            val norm = sqrt(sw * sw + sx * sx + sy * sy + sz * sz)
            if (norm < 1e-9) return null
            val smooth = floatArrayOf(
                (sw / norm).toFloat(), (sx / norm).toFloat(),
                (sy / norm).toFloat(), (sz / norm).toFloat()
            )

            // Delta that carries the raw frame orientation onto the
            // smoothed trajectory — the counter-rotation to warp by.
            var delta = multiply(smooth, conjugate(raw))
            if (delta[0] < 0f) {
                delta = floatArrayOf(-delta[0], -delta[1], -delta[2], -delta[3])
            }
            val angleDeg = Math.toDegrees(
                2.0 * acos(delta[0].toDouble().coerceIn(-1.0, 1.0))
            ).toFloat()
            val clampFrac = if (angleDeg > 1e-6f) {
                EisTuning.MAX_CORRECTION_DEG / angleDeg
            } else {
                EisTuning.CORRECTION_GAIN
            }
            val frac = minOf(EisTuning.CORRECTION_GAIN, clampFrac)
            val corrected = slerp(IDENTITY, delta, frac)
            return Correction(corrected, angleDeg)
        }
    }

    companion object {
        private val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f)

        /** Hamilton product a * b for [w,x,y,z] quaternions. */
        fun multiply(a: FloatArray, b: FloatArray): FloatArray {
            val aw = a[0]; val ax = a[1]; val ay = a[2]; val az = a[3]
            val bw = b[0]; val bx = b[1]; val by = b[2]; val bz = b[3]
            return floatArrayOf(
                aw * bw - ax * bx - ay * by - az * bz,
                aw * bx + ax * bw + ay * bz - az * by,
                aw * by - ax * bz + ay * bw + az * bx,
                aw * bz + ax * by - ay * bx + az * bw
            )
        }

        fun conjugate(q: FloatArray): FloatArray =
            floatArrayOf(q[0], -q[1], -q[2], -q[3])

        fun normalize(q: FloatArray): FloatArray {
            val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            if (n < 1e-9f) return floatArrayOf(1f, 0f, 0f, 0f)
            return floatArrayOf(q[0] / n, q[1] / n, q[2] / n, q[3] / n)
        }

        /** Shortest-path spherical interpolation between unit quaternions. */
        fun slerp(a: FloatArray, b: FloatArray, t: Float): FloatArray {
            var bw = b[0]; var bx = b[1]; var by = b[2]; var bz = b[3]
            var dot = a[0] * bw + a[1] * bx + a[2] * by + a[3] * bz
            if (dot < 0f) {
                dot = -dot; bw = -bw; bx = -bx; by = -by; bz = -bz
            }
            if (dot > 0.9995f) {
                return normalize(
                    floatArrayOf(
                        a[0] + t * (bw - a[0]),
                        a[1] + t * (bx - a[1]),
                        a[2] + t * (by - a[2]),
                        a[3] + t * (bz - a[3])
                    )
                )
            }
            val theta = acos(dot.coerceIn(-1f, 1f))
            val sinTheta = sin(theta)
            val wa = sin((1f - t) * theta) / sinTheta
            val wb = sin(t * theta) / sinTheta
            return floatArrayOf(
                wa * a[0] + wb * bw,
                wa * a[1] + wb * bx,
                wa * a[2] + wb * by,
                wa * a[3] + wb * bz
            )
        }

        /** Rotation angle (degrees) between two unit quaternions. */
        fun angleBetweenDeg(a: FloatArray, b: FloatArray): Float {
            val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
            return Math.toDegrees(
                2.0 * acos(abs(dot).toDouble().coerceIn(0.0, 1.0))
            ).toFloat()
        }

        /** 3x3 rotation matrix (row-major) for a unit [w,x,y,z] quaternion. */
        fun toMatrix3(q: FloatArray): FloatArray {
            val w = q[0]; val x = q[1]; val y = q[2]; val z = q[3]
            return floatArrayOf(
                1f - 2f * (y * y + z * z), 2f * (x * y - w * z), 2f * (x * z + w * y),
                2f * (x * y + w * z), 1f - 2f * (x * x + z * z), 2f * (y * z - w * x),
                2f * (x * z - w * y), 2f * (y * z + w * x), 1f - 2f * (x * x + y * y)
            )
        }
    }
}
