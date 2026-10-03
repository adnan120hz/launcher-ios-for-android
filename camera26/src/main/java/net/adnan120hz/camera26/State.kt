package net.adnan120hz.camera26

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.extensions.ExtensionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

enum class UiStyle { IOS26, IOS18 }

enum class CamMode(val label: String) {
    TIME_LAPSE("TIME-LAPSE"),
    SLO_MO("SLO-MO"),
    CINEMATIC("CINEMATIC"),
    VIDEO("VIDEO"),
    PHOTO("PHOTO"),
    PORTRAIT("PORTRAIT"),
    PANO("PANO")
}

enum class FlashSetting { OFF, AUTO, ON }

enum class PhotoAspect { RATIO_4_3, RATIO_16_9, SQUARE }

enum class SheetKind { NONE, GRID, FLASH, EXPOSURE, TIMER, ASPECT, RESOLUTION }

data class VideoResOption(val label: String, val qualityName: String)

class CameraState(context: Context) {
    private val prefs = context.getSharedPreferences("camera26_prefs", Context.MODE_PRIVATE)

    var uiStyle by mutableStateOf(
        if (prefs.getString("ui_style", "ios26") == "ios18") UiStyle.IOS18 else UiStyle.IOS26
    )
    var mode by mutableStateOf(CamMode.PHOTO)
    var facingFront by mutableStateOf(false)

    var flash by mutableStateOf(
        when (prefs.getString("flash", "AUTO")) {
            "OFF" -> FlashSetting.OFF
            "ON" -> FlashSetting.ON
            else -> FlashSetting.AUTO
        }
    )
    var videoTorch by mutableStateOf(false)
    var timerSec by mutableStateOf(0)
    var aspect by mutableStateOf(
        when (prefs.getString("aspect", "4_3")) {
            "16_9" -> PhotoAspect.RATIO_16_9
            "1_1" -> PhotoAspect.SQUARE
            else -> PhotoAspect.RATIO_4_3
        }
    )
    var gridOn by mutableStateOf(prefs.getBoolean("grid", false))

    // Exposure compensation (index into the device range).
    var exposureIndex by mutableStateOf(0)
    var exposureMin by mutableStateOf(0)
    var exposureMax by mutableStateOf(0)
    var exposureStep by mutableStateOf(0f)
    val exposureSupported: Boolean get() = exposureMax > exposureMin

    // Zoom. zoomRatio is the *applied/displayed* ratio, driven frame-by-frame
    // by the zoom animator; gestures only move zoomTarget and the animator
    // chases it with a spring (smooth zoom, never jumps).
    var zoomRatio by mutableStateOf(1f)          // displayed absolute ratio (applied)
    var zoomTarget by mutableFloatStateOf(1f)   // where the user asked to go
    var desiredSessionId by mutableStateOf<String?>(null) // null = logical camera
    var sessionBound by mutableStateOf(false)   // controller bound to desired session
    var currentSessionRatio by mutableStateOf(1f)
    var sessionMinZoom by mutableStateOf(1f)
    var sessionMaxZoom by mutableStateOf(10f)

    var sheet by mutableStateOf(SheetKind.NONE)
    var trayOpen by mutableStateOf(false)       // iOS 18 chevron tray
    var modeStripVisible by mutableStateOf(false) // iOS 26 transient mode labels
    var dialVisible by mutableStateOf(false)

    var focusPoint by mutableStateOf<Offset?>(null)
    var focusLocked by mutableStateOf(false)
    var focusNonce by mutableStateOf(0)

    var countdown by mutableStateOf<Int?>(null)
    var isRecording by mutableStateOf(false)
    var recordSeconds by mutableStateOf(0)
    var quickTake by mutableStateOf(false)

    var thumbBitmap by mutableStateOf<Bitmap?>(null)
    var thumbUri by mutableStateOf<Uri?>(null)

    var videoResOptions by mutableStateOf<List<VideoResOption>>(emptyList())
    var videoRes by mutableStateOf<VideoResOption?>(null)
    var fps by mutableStateOf(prefs.getInt("fps", 30))
    var savedQualityName: String? = prefs.getString("video_quality", null)

    var caps by mutableStateOf(DeviceCaps())
    var capsReady by mutableStateOf(false)
    var nightExtAvailable by mutableStateOf(false)
    var bokehExtAvailable by mutableStateOf(false)
    var nightOn by mutableStateOf(false)

    var toast by mutableStateOf<String?>(null)
    var micGranted by mutableStateOf(false)
    var micAsked by mutableStateOf(false)
    var bindError by mutableStateOf<String?>(null)

    fun modeAvailable(m: CamMode): Boolean = when (m) {
        CamMode.PHOTO, CamMode.VIDEO -> true
        CamMode.PORTRAIT -> bokehExtAvailable && !facingFront
        else -> false
    }

    /** ExtensionMode to bind with, or NONE. Portrait=Bokeh, Night=Night extension. */
    val extensionMode: Int
        get() = when {
            mode == CamMode.PORTRAIT && bokehExtAvailable && !facingFront -> ExtensionMode.BOKEH
            nightOn && nightExtAvailable && !facingFront && mode == CamMode.PHOTO -> ExtensionMode.NIGHT
            else -> ExtensionMode.NONE
        }

    /** Widest..longest bindable sessions for the back camera. */
    fun sessions(): List<LensSession> {
        val all = caps.backSessions
        if (all.isEmpty()) return listOf(LensSession(null, 1f, caps.baseEqMm, "1x"))
        // When the logical camera already fuses the ultrawide (min zoom < 1),
        // the physical ultrawide session is unnecessary.
        return if (caps.logicalMinZoomRatio < 1f) {
            all.filter { it.ratio >= 0.95f || it.cameraId == null }
                .ifEmpty { all }
        } else all
    }

    /** Pick the session that should serve [target] absolute zoom ratio. */
    fun sessionFor(target: Float): LensSession {
        if (facingFront) return LensSession(null, 1f, caps.baseEqMm, "1x")
        val list = sessions()
        var chosen = list.first()
        for (s in list) {
            if (target >= s.ratio * 0.98f) chosen = s
        }
        return chosen
    }

    /** Quick-pick zoom stops (absolute ratios) shown as small buttons. */
    fun quickStops(): List<Float> {
        if (facingFront) return listOf(1f)
        val stops = sortedSetOf<Float>()
        sessions().forEach { stops += it.ratio }
        if (caps.logicalMinZoomRatio < 1f) stops += 0.5f
        stops += 1f
        val maxApprox = minOf(100f, currentSessionRatio * sessionMaxZoom)
        return stops.filter { it <= maxApprox + 0.01f }.sorted()
    }

    val dialMin: Float
        get() = minOf(
            quickStops().firstOrNull() ?: 1f,
            currentSessionRatio * sessionMinZoom
        ).coerceAtLeast(0.1f)

    val dialMax: Float
        get() {
            var m = currentSessionRatio * sessionMaxZoom
            if (caps.backSessions.any { it.ratio > 1.3f }) m = maxOf(m, 100f)
            return minOf(100f, m).coerceAtLeast(dialMin + 1f)
        }

    val currentEqMm: Int
        get() = if (caps.baseEqMm > 0f) (zoomRatio * caps.baseEqMm).roundToInt() else 0

    fun persistAll() {
        prefs.edit()
            .putString("ui_style", if (uiStyle == UiStyle.IOS18) "ios18" else "ios26")
            .putString("flash", flash.name)
            .putString(
                "aspect", when (aspect) {
                    PhotoAspect.RATIO_4_3 -> "4_3"
                    PhotoAspect.RATIO_16_9 -> "16_9"
                    PhotoAspect.SQUARE -> "1_1"
                }
            )
            .putBoolean("grid", gridOn)
            .putInt("fps", fps)
            .putString("video_quality", videoRes?.qualityName)
            .apply()
    }
}
