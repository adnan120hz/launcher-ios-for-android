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

data class DeviceCaps(
    val backSessions: List<LensSession> = emptyList(),
    val baseEqMm: Float = 0f,            // equivalent focal at 1x; 0 = unknown -> hide MM labels
    val logicalMinZoomRatio: Float = 1f, // < 1 means the logical camera fuses an ultrawide
    val fpsOptions: List<Int> = listOf(30),
    val maxFps: Int = 30,
    val hasFlashUnit: Boolean = false,
    /** Highest high-speed (slow-motion) fps the device exposes; 0 = none. */
    val sloMoFps: Int = 0,
    /** True when an AVC encoder usable for the time-lapse pipeline exists. */
    val timelapseAvailable: Boolean = false,
    /** True when CONTROL_VIDEO_STABILIZATION_MODE_ON is supported (Action mode). */
    val videoStabilization: Boolean = false
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

    // Slow-motion: real high-speed ranges live on the stream configuration map,
    // not in the AE ranges (those usually top out at 60).
    var sloMax = 0
    try {
        val scm = logical.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        scm?.highSpeedVideoFpsRanges?.forEach { r -> if (r.upper > sloMax) sloMax = r.upper }
    } catch (e: Throwable) { /* device without high-speed map */ }

    val stabModes = logical.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
    val stab = stabModes?.any { it == 1 } == true // 1 = CONTROL_VIDEO_STABILIZATION_MODE_ON

    return DeviceCaps(
        backSessions = sortedSessions,
        baseEqMm = baseEq,
        logicalMinZoomRatio = minZoom,
        fpsOptions = if (fpsOpts.isEmpty()) listOf(30) else fpsOpts,
        maxFps = fpsRanges?.maxOfOrNull { it.upper } ?: 30,
        hasFlashUnit = tier1,
        sloMoFps = sloMax,
        timelapseAvailable = avcEncoderAvailable(),
        videoStabilization = stab
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
