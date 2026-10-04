package net.adnan120hz.camera26

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.tan

/**
 * Panorama, for real: a guided sweep-and-stitch mode.
 *
 * Shutter starts the sweep. While the user pans slowly, the gyroscope's
 * integrated yaw (GyroTracker) triggers a still capture every
 * [PANO_STEP_DEG]; on devices without a gyroscope the same guide runs on
 * timed captures with evenly assumed angles (quality gate stated in the
 * UI). Shutter again (or auto-complete at [PANO_TARGET_DEG] / the frame
 * cap) finishes: frames are cylindrically projected by their yaw and
 * feather-blended into ONE JPEG in Pictures/Camera26. If stitching fails,
 * the middle frame is saved as a normal photo and the UI says so —
 * nothing silently pretends to be a panorama.
 *
 * Honest limits: best on static scenes; moving subjects can ghost at the
 * seams, exposure differences between frames can band, and yaw-only
 * projection ignores pitch/roll, so keep the phone level. The final feel
 * (guide pacing, too-fast threshold) is only provable on a real device.
 */

// Sweep geometry / pacing — one place to retune after device tests.
internal const val PANO_STEP_DEG = 8f
internal const val PANO_TARGET_DEG = 140f
internal const val PANO_MAX_FRAMES = 18
internal const val PANO_WORK_H = 720
internal const val PANO_TIMER_DEG_PER_SEC = 10f
internal const val PANO_TOO_FAST_DEG_S = 40f

/** One captured panorama frame with the yaw + horizontal FOV at capture. */
data class PanoFrame(val bmp: Bitmap, val yawDeg: Float, val hfovDeg: Float)

/** Mutable per-sweep bookkeeping kept by the screen (not Compose state). */
class PanoRuntime {
    var startYaw = 0f
    var lastYaw = 0f
    var lastCaptureYaw = 0f
    var minYaw = 0f
    var maxYaw = 0f
    var dirSign = 1f
    var dirLocked = false
    var captureBusy = false
    var yawVelEma = 0f

    fun reset(yaw: Float) {
        startYaw = yaw
        lastYaw = yaw
        lastCaptureYaw = yaw
        minYaw = yaw
        maxYaw = yaw
        dirSign = 1f
        dirLocked = false
        captureBusy = false
        yawVelEma = 0f
    }
}

/**
 * Yaw source + frame store for one panorama sweep. With a gyroscope the
 * yaw is the twist about the device's vertical (screen-Y) axis relative
 * to the sweep start, so the pan direction is detected, not assumed.
 * Without one, yaw advances at a fixed assumed rate (timed mode).
 */
class PanoEngine(context: Context) {
    private val tracker = GyroTracker(context.applicationContext)

    var useGyro: Boolean = false
        private set

    @Volatile
    var running: Boolean = false
        private set

    private var startQuat: FloatArray? = null
    private var startTimeNs = 0L
    private var lastYaw = 0f
    private val frames = ArrayList<PanoFrame>()

    val frameCount: Int get() = frames.size

    fun start(withGyro: Boolean) {
        stop()
        useGyro = withGyro && tracker.available
        startQuat = null
        lastYaw = 0f
        startTimeNs = SystemClock.elapsedRealtimeNanos()
        running = true
        if (useGyro) tracker.start()
    }

    fun stop() {
        if (useGyro) tracker.stop()
        running = false
    }

    /** Current pan yaw in degrees relative to the sweep start. */
    fun yawDeg(): Float {
        if (!running) return lastYaw
        if (!useGyro) {
            val dt = (SystemClock.elapsedRealtimeNanos() - startTimeNs) / 1e9f
            lastYaw = PANO_TIMER_DEG_PER_SEC * dt
            return lastYaw
        }
        val q = tracker.rawOrientationAt(SystemClock.elapsedRealtimeNanos())
            ?: return lastYaw
        val q0 = startQuat ?: run {
            // Baseline latches on the first readable orientation.
            startQuat = q
            return 0f
        }
        val dq = GyroTracker.multiply(GyroTracker.conjugate(q0), q)
        var w = dq[0]
        var y = dq[2]
        if (w < 0f) {
            w = -w
            y = -y
        }
        lastYaw = Math.toDegrees(2.0 * atan2(y.toDouble(), w.toDouble())).toFloat()
        return lastYaw
    }

    fun addFrame(frame: PanoFrame) {
        frames += frame
    }

    /** Hand the captured frames to the stitch step (engine forgets them). */
    fun takeFrames(): List<PanoFrame> {
        val out = frames.toList()
        frames.clear()
        return out
    }

    /** Drop captured frames (cancel), recycling their bitmaps. */
    fun discardFrames() {
        frames.forEach { try { it.bmp.recycle() } catch (e: Throwable) { /* already gone */ } }
        frames.clear()
    }
}

/** Real horizontal field of view (degrees) of the facing camera at [zoomRatio]. */
fun cameraHfovDeg(context: Context, front: Boolean, zoomRatio: Float): Float {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val facing = if (front) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val id = cm.cameraIdList.firstOrNull { cid ->
            cm.getCameraCharacteristics(cid)
                .get(CameraCharacteristics.LENS_FACING) == facing
        } ?: return 62f
        val chars = cm.getCameraCharacteristics(id)
        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val phys = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focals == null || focals.isEmpty() || phys == null || phys.width <= 0f) {
            return 62f
        }
        val sensorHfov = 2.0 * atan(phys.width / (2.0 * focals.min()))
        // Digital zoom narrows the effective FOV (and a sub-1x ultra-wide
        // session widens it) — the stitch only needs the angle per frame.
        val eff = 2.0 * atan(tan(sensorHfov / 2.0) / zoomRatio.coerceAtLeast(0.2f))
        Math.toDegrees(eff).toFloat().coerceIn(20f, 110f)
    } catch (e: Throwable) {
        62f
    }
}

/** Cylindrical-projection stitcher (yaw-only, feather-blended overlaps). */
object PanoStitcher {

    /** Downscale a captured still to the panorama working height. */
    fun downscale(bmp: Bitmap, targetH: Int): Bitmap {
        if (bmp.height <= targetH) return bmp
        val w = (bmp.width.toFloat() * targetH / bmp.height).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, targetH, true)
        if (scaled !== bmp) {
            try { bmp.recycle() } catch (e: Throwable) { /* caller owns it */ }
        }
        return scaled
    }

    /**
     * Stitch [frames] into one panorama bitmap, or null when the sweep did
     * not travel far enough / the geometry is unusable — the caller then
     * saves the best single frame and says so honestly.
     */
    fun stitch(frames: List<PanoFrame>): Bitmap? {
        if (frames.size < 2) return null
        val sorted = frames.sortedBy { it.yawDeg }
        val travelDeg = sorted.last().yawDeg - sorted.first().yawDeg
        if (travelDeg < PANO_STEP_DEG * 0.6f) return null
        val ref = sorted[sorted.size / 2]
        val outH = ref.bmp.height
        if (outH <= 0 || ref.bmp.width <= 0) return null
        val refHalfRad = Math.toRadians(ref.hfovDeg.toDouble()) / 2.0
        var f = (ref.bmp.width / 2.0) / tan(refHalfRad)
        val spanDeg = travelDeg + ref.hfovDeg
        var outW = ceil(f * Math.toRadians(spanDeg.toDouble())).toInt()
        val maxW = 10000
        if (outW > maxW) {
            f *= maxW.toDouble() / outW
            outW = maxW
        }
        if (outW < ref.bmp.width) return null
        val thetaStart = Math.toRadians(sorted.first().yawDeg.toDouble()) - refHalfRad
        val featherRad = Math.toRadians(
            maxOf(6.0, minOf(ref.hfovDeg * 0.30, 18.0))
        )

        // Per-frame column LUTs: source x, 1/cos (vertical cylindrical
        // stretch) and feather weight for every output column it covers.
        class Lut(
            val frame: PanoFrame,
            val pixels: IntArray,
            val xFrom: Int,
            val sx: FloatArray,
            val invCos: FloatArray,
            val wgt: FloatArray
        )

        val luts = sorted.map { fr ->
            val fx = (fr.bmp.width / 2.0) /
                tan(Math.toRadians(fr.hfovDeg.toDouble()) / 2.0)
            val yawR = Math.toRadians(fr.yawDeg.toDouble())
            val half = Math.toRadians(fr.hfovDeg.toDouble()) / 2.0
            val xFrom = maxOf(0, floor(f * (yawR - half - thetaStart)).toInt())
            val xTo = minOf(outW - 1, ceil(f * (yawR + half - thetaStart)).toInt())
            val count = maxOf(0, xTo - xFrom + 1)
            val sx = FloatArray(count)
            val invCos = FloatArray(count)
            val wgt = FloatArray(count)
            for (i in 0 until count) {
                val x = xFrom + i
                val d = (x / f + thetaStart) - yawR
                sx[i] = (fx * tan(d) + fr.bmp.width / 2.0).toFloat()
                invCos[i] = (1.0 / cos(d)).toFloat()
                val t = ((half - abs(d)) / featherRad).coerceIn(0.0, 1.0)
                wgt[i] = (t * t * (3.0 - 2.0 * t)).toFloat()
            }
            val px = IntArray(fr.bmp.width * fr.bmp.height)
            fr.bmp.getPixels(px, 0, fr.bmp.width, 0, 0, fr.bmp.width, fr.bmp.height)
            Lut(fr, px, xFrom, sx, invCos, wgt)
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val bandH = 64
        val acc = FloatArray(outW * bandH * 3)
        val wacc = FloatArray(outW * bandH)
        val bandPx = IntArray(outW * bandH)
        var y0 = 0
        while (y0 < outH) {
            val rows = minOf(bandH, outH - y0)
            acc.fill(0f)
            wacc.fill(0f)
            for (lut in luts) {
                val fw = lut.frame.bmp.width
                val fh = lut.frame.bmp.height
                val yScale = fh.toFloat() / outH
                for (row in 0 until rows) {
                    val y = y0 + row
                    val bandRow = row * outW
                    for (i in lut.sx.indices) {
                        val wgt = lut.wgt[i]
                        if (wgt <= 0.001f) continue
                        val sourceX = lut.sx[i]
                        if (sourceX < 0f || sourceX > fw - 1) continue
                        val sourceY = (y - outH / 2f) * lut.invCos[i] * yScale + fh / 2f
                        if (sourceY < 0f || sourceY > fh - 1) continue
                        // Bilinear sample.
                        val x1 = floor(sourceX).toInt()
                        val y1 = floor(sourceY).toInt()
                        val x2 = minOf(x1 + 1, fw - 1)
                        val y2 = minOf(y1 + 1, fh - 1)
                        val fxr = sourceX - x1
                        val fyr = sourceY - y1
                        val p00 = lut.pixels[y1 * fw + x1]
                        val p10 = lut.pixels[y1 * fw + x2]
                        val p01 = lut.pixels[y2 * fw + x1]
                        val p11 = lut.pixels[y2 * fw + x2]
                        val r = bilinear(p00, p10, p01, p11, fxr, fyr, 16)
                        val g = bilinear(p00, p10, p01, p11, fxr, fyr, 8)
                        val b = bilinear(p00, p10, p01, p11, fxr, fyr, 0)
                        val idx = (bandRow + lut.xFrom + i) * 3
                        acc[idx] += r * wgt
                        acc[idx + 1] += g * wgt
                        acc[idx + 2] += b * wgt
                        wacc[bandRow + lut.xFrom + i] += wgt
                    }
                }
            }
            for (i in 0 until rows * outW) {
                val wgt = wacc[i]
                bandPx[i] = if (wgt > 1e-4f) {
                    val r = (acc[i * 3] / wgt).toInt().coerceIn(0, 255)
                    val g = (acc[i * 3 + 1] / wgt).toInt().coerceIn(0, 255)
                    val b = (acc[i * 3 + 2] / wgt).toInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    0xFF000000.toInt()
                }
            }
            out.setPixels(bandPx, 0, outW, 0, y0, outW, rows)
            y0 += rows
        }
        return out
    }

    private fun bilinear(
        p00: Int, p10: Int, p01: Int, p11: Int,
        fx: Float, fy: Float, shift: Int
    ): Float {
        val c00 = (p00 shr shift) and 0xFF
        val c10 = (p10 shr shift) and 0xFF
        val c01 = (p01 shr shift) and 0xFF
        val c11 = (p11 shr shift) and 0xFF
        val top = c00 + (c10 - c00) * fx
        val bot = c01 + (c11 - c01) * fx
        return top + (bot - top) * fy
    }
}

/** Saves a panorama (or fallback frame) as JPEG into Pictures/Camera26. */
object PanoSaver {
    fun saveJpeg(context: Context, bmp: Bitmap): Uri? {
        return try {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            val resolver = context.contentResolver
            val name = "PANO_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Camera26"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { it.write(baos.toByteArray()) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Throwable) {
            null
        }
    }
}
