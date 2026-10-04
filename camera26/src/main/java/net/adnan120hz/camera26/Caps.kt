package net.adnan120hz.camera26

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One bindable back-camera session: the logical camera (cameraId == null,
 * CameraX default) or one physical lens of a logical multi-camera.
 */
data class LensSession(
    val cameraId: String?,
    val ratio: Float,   // focal ratio relative to the main (1x) lens
    val eqMm: Float,    // 35mm-equivalent focal length of this lens; 0 = unknown
    val label: String   // "0.5", "1x", "3x"
)

/**
 * Coarse performance class of the device, used to scale real work (EIS
 * stream size, glass effects) — never to gate features or change the UI.
 */
enum class PerfTier { ENTRY, MID, FLAGSHIP }

data class DeviceCaps(
    val backSessions: List<LensSession> = emptyList(),
    val baseEqMm: Float = 0f,            // equivalent focal at 1x; 0 = unknown -> hide MM labels
    val logicalMinZoomRatio: Float = 1f, // < 1 means the logical camera fuses an ultrawide
    val fpsOptions: List<Int> = listOf(30),
    val maxFps: Int = 30,
    val hasFlashUnit: Boolean = false,
    /** True when an AVC encoder usable for the time-lapse pipeline exists. */
    val timelapseAvailable: Boolean = false,
    /** True when CONTROL_VIDEO_STABILIZATION_MODE_ON is supported (Action mode). */
    val videoStabilization: Boolean = false,
    /** True when the lens reports hardware optical stabilization (OIS). */
    val oisAvailable: Boolean = false,
    /** True when the device has a real gyroscope (software gyro-EIS input). */
    val gyroAvailable: Boolean = false,
    /** Performance class used to scale glass cost / preview effects. */
    val perfTier: PerfTier = PerfTier.FLAGSHIP
)

fun formatRatioLabel(ratio: Float): String =
    if (ratio < 0.95f) {
        val v = (ratio * 10).roundToInt() / 10f
        if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
    } else {
        "${ratio.roundToInt()}x"
    }

/** Zoom factor shown to the user for a stop, e.g. 0.5 -> "0.5", 1 -> "1x". */
fun formatZoomValue(ratio: Float): String =
    if (ratio < 0.95f) formatRatioLabel(ratio) + "x"
    else if (ratio == ratio.roundToInt().toFloat()) "${ratio.roundToInt()}x"
    else "${(ratio * 10).roundToInt() / 10f}x"

private fun eqFocalMm(chars: CameraCharacteristics): Float {
    val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return 0f
    if (focals.isEmpty()) return 0f
    val size = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0f
    if (size.width <= 0f) return 0f
    return focals.min() * 36f / size.width
}

fun computeCaps(context: Context): DeviceCaps {
    return try {
        computeCapsInternal(context)
    } catch (e: Throwable) {
        DeviceCaps()
    }
}

private fun computeCapsInternal(context: Context): DeviceCaps {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val allIds = cm.cameraIdList.toList()
    fun chars(id: String): CameraCharacteristics? =
        try { cm.getCameraCharacteristics(id) } catch (e: Throwable) { null }

    val backIds = allIds.filter {
        chars(it)?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
    if (backIds.isEmpty()) return DeviceCaps()

    val logicalId = backIds.firstOrNull { id ->
        val caps = chars(id)?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
    } ?: backIds.first()
    val logical = chars(logicalId) ?: return DeviceCaps()

    var baseEq = eqFocalMm(logical)
    val physicalIds: List<String> =
        try { logical.physicalCameraIds.toList() } catch (e: Throwable) { emptyList() }
    val physicalEqs = physicalIds.mapNotNull { pid ->
        chars(pid)?.let { pid to eqFocalMm(it) }
    }.filter { it.second > 0f }

    if (physicalEqs.isNotEmpty()) {
        // 1x baseline = physical lens closest to what the logical camera reports (main wide).
        val anchor = if (baseEq > 0f) baseEq else 24f
        val main = physicalEqs.minByOrNull { abs(it.second - anchor) }
        if (main != null && main.second > 0f) baseEq = main.second
    }

    val sessions = mutableListOf(LensSession(null, 1f, baseEq, "1x"))
    if (baseEq > 0f) {
        for ((pid, eq) in physicalEqs) {
            val r = eq / baseEq
            // Skip lenses that duplicate the main wide (ratio ~1).
            if (r < 0.82f || r > 1.3f) {
                sessions += LensSession(pid, r, eq, formatRatioLabel(r))
            }
        }
    }
    val sortedSessions = sessions
        .groupBy { (it.ratio * 10).roundToInt() }
        .values.map { group -> group.firstOrNull { it.cameraId == null } ?: group.first() }
        .sortedBy { it.ratio }

    var minZoom = 1f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val zr = logical.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        if (zr != null && zr.lower > 0f) minZoom = zr.lower
    }

    val fpsRanges = logical.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
    val fpsOpts = listOf(24, 30, 60).filter { f ->
        fpsRanges?.any { it.lower <= f && it.upper >= f } == true
    }
    val tier1 = logical.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

    val stabModes = logical.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
    val stab = stabModes?.any { it == 1 } == true // 1 = CONTROL_VIDEO_STABILIZATION_MODE_ON

    // Hardware optical stabilization (OIS): 1 = LENS_OPTICAL_STABILIZATION_ON.
    val oisModes = logical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
    val ois = oisModes?.any { it == 1 } == true

    // Motion sensor for the software gyro-EIS pipeline (restored 80dcd84
    // basis, 2.0.0): a real gyroscope only. The fix7/8 fused
    // rotation-vector "virtual gyro" takeover is removed by user order —
    // gyro-less devices use hardware stabilisation behind ACTION instead.
    val sensorManager = try {
        context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    } catch (e: Throwable) {
        null
    }
    val gyro = try {
        sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null
    } catch (e: Throwable) {
        false
    }

    // Performance tier from total RAM (and the low-RAM flag): entry-level
    // phones get a cheaper EIS stream and plainer glass, flagships the
    // full pipeline. Features and UI are identical across tiers.
    val perfTier = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        when {
            am?.isLowRamDevice == true || gb < 3.5 -> PerfTier.ENTRY
            gb < 6.0 -> PerfTier.MID
            else -> PerfTier.FLAGSHIP
        }
    } catch (e: Throwable) {
        PerfTier.MID
    }

    return DeviceCaps(
        backSessions = sortedSessions,
        baseEqMm = baseEq,
        logicalMinZoomRatio = minZoom,
        fpsOptions = if (fpsOpts.isEmpty()) listOf(30) else fpsOpts,
        maxFps = fpsRanges?.maxOfOrNull { it.upper } ?: 30,
        hasFlashUnit = tier1,
        timelapseAvailable = avcEncoderAvailable(),
        videoStabilization = stab,
        oisAvailable = ois,
        gyroAvailable = gyro,
        perfTier = perfTier
    )
}

/** True when an AVC hardware/software encoder with a byte-buffer YUV format exists. */
private fun avcEncoderAvailable(): Boolean {
    return try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            if (!ci.isEncoder) return@any false
            val type = ci.supportedTypes.firstOrNull {
                it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
            } ?: return@any false
            val formats = ci.getCapabilitiesForType(type).colorFormats
            formats.any {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                    it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            }
        }
    } catch (e: Throwable) {
        false
    }
}
