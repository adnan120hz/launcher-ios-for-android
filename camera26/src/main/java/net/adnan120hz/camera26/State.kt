package net.adnan120hz.camera26

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.extensions.ExtensionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

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

enum class SheetKind { NONE, GRID, FLASH, EXPOSURE, TIMER, ASPECT, RESOLUTION, FILTER, STYLES, APERTURE, ACTION, CONFIG }

data class VideoResOption(val label: String, val qualityName: String)

class CameraState(context: Context) {
    // Nullable so screenshot tests (layoutlib contexts) can construct the
    // state even where SharedPreferences is unavailable; real devices always
    // get the real preferences store.
    private val prefs: SharedPreferences? = try {
        context.getSharedPreferences("camera26_prefs", Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    var mode by mutableStateOf(CamMode.PHOTO)
    var facingFront by mutableStateOf(false)

    var flash by mutableStateOf(
        when (prefs?.getString("flash", "AUTO")) {
            "OFF" -> FlashSetting.OFF
            "ON" -> FlashSetting.ON
            else -> FlashSetting.AUTO
        }
    )
    var videoTorch by mutableStateOf(false)
    var timerSec by mutableStateOf(0)
    var aspect by mutableStateOf(
        when (prefs?.getString("aspect", "4_3")) {
            "16_9" -> PhotoAspect.RATIO_16_9
            "1_1" -> PhotoAspect.SQUARE
            else -> PhotoAspect.RATIO_4_3
        }
    )
    var gridOn by mutableStateOf(prefs?.getBoolean("grid", false) ?: false)

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
    var dialVisible by mutableStateOf(false)

    /**
     * True while the zoom ARC DIAL (not buttons/pinch) is the input that
     * last moved the zoom target — selects the dial's own, tighter zoom
     * smoothing in the animator. Runtime only.
     */
    var zoomDialDriven by mutableStateOf(false)

    /** Session flag: the CINEMATIC "not supported" toast shows ONCE. */
    var cinematicNoticeShown by mutableStateOf(false)

    // First-run onboarding ("Developer Adnan.120hz" intro): shown once,
    // persisted, re-openable from Settings. Runtime visibility is kept
    // separate so re-opening does not reset the "seen" flag.
    var onboardingDone by mutableStateOf(prefs?.getBoolean("onboarded", false) ?: false)
    var onboardingVisible by mutableStateOf(false)

    /** Latest GitHub camera release found by the update checker (runtime). */
    var updateTag by mutableStateOf<String?>(null)
    var updateUrl by mutableStateOf<String?>(null)

    // CONFIG (this app's own photo-quality settings, PhotoConfig.kt):
    // five real adjustments applied to captured photos, persisted.
    var configPresetId by mutableStateOf(prefs?.getString("config_preset", "default") ?: "default")
    var configSharpness by mutableFloatStateOf(prefs?.getFloat("config_sharpness", 0f) ?: 0f)
    var configSaturation by mutableFloatStateOf(prefs?.getFloat("config_saturation", 1f) ?: 1f)
    var configContrast by mutableFloatStateOf(prefs?.getFloat("config_contrast", 1f) ?: 1f)
    var configGamma by mutableFloatStateOf(prefs?.getFloat("config_gamma", 1f) ?: 1f)
    var configDenoise by mutableFloatStateOf(prefs?.getFloat("config_denoise", 0f) ?: 0f)

    fun photoConfig(): PhotoConfig = PhotoConfig(
        sharpness = configSharpness,
        saturation = configSaturation,
        contrast = configContrast,
        gamma = configGamma,
        denoise = configDenoise
    )

    /** Apply a CONFIG preset: fills the sliders, persists. */
    fun applyConfigPreset(preset: PhotoConfigPreset) {
        configPresetId = preset.id
        configSharpness = preset.config.sharpness
        configSaturation = preset.config.saturation
        configContrast = preset.config.contrast
        configGamma = preset.config.gamma
        configDenoise = preset.config.denoise
        persistAll()
    }

    /** Any manual slider move turns the preset into "Kustom". */
    fun markConfigCustom() {
        if (configPresetId != "custom") configPresetId = "custom"
    }

    // Mode carousel pill (below the shutter): horizontal drag offset in px,
    // written by the pill's gesture handler and settled back to 0 on release.
    var carouselDragPx by mutableFloatStateOf(0f)

    // True while a finger presses/drag the carousel pill: drives the iOS 26
    // Liquid Glass jelly-bubble on the selected capsule (idle = plain capsule).
    var carouselPressed by mutableStateOf(false)

    // When a small capture aspect (4:3 / 1:1) letterboxes the preview, the
    // expand (⤢) button flips the preview to full-bleed; capture is unaffected.
    var previewFilled by mutableStateOf(false)

    var settingsOpen by mutableStateOf(false)

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
    var fps by mutableStateOf(prefs?.getInt("fps", 30) ?: 30)
    var savedQualityName: String? = prefs?.getString("video_quality", null)

    var caps by mutableStateOf(DeviceCaps())
    var capsReady by mutableStateOf(false)
    var nightExtAvailable by mutableStateOf(false)
    var bokehExtAvailable by mutableStateOf(false)
    var nightOn by mutableStateOf(false)

    var toast by mutableStateOf<String?>(null)

    // iOS-style transient status banner ("FLASH ON", "NIGHT MODE OFF", …)
    // shown at the top centre whenever a toggle changes. The nonce restarts
    // the auto-dismiss timer on every change.
    var bannerText by mutableStateOf<String?>(null)
    var bannerNonce by mutableStateOf(0)
    fun showBanner(text: String) {
        bannerText = text
        bannerNonce += 1
    }

    /** Portrait lighting effect id (see PortraitLights in Grade.kt). */
    var portraitLightId by mutableStateOf(
        prefs?.getString("portrait_light", "natural") ?: "natural"
    )
    var micGranted by mutableStateOf(false)
    var micAsked by mutableStateOf(false)
    var bindError by mutableStateOf<String?>(null)

    // Real, device-gated features (no dead controls anywhere in the UI):
    // FILTER / STYLES are real colour grades applied at capture; APERTURE
    // drives the portrait background-blur strength; ACTION is video
    // stabilization; TIME-LAPSE runs an interval-capture encoder.
    var filterId by mutableStateOf<String?>(prefs?.getString("filter_id", null))
    var styleId by mutableStateOf<String?>(prefs?.getString("style_id", null))
    var apertureF by mutableStateOf(prefs?.getFloat("aperture_f", 2.8f) ?: 2.8f)
    var actionOn by mutableStateOf(false)

    // Software gyro EIS (ACTION mode): the GL gyro pipeline takes over the
    // VIDEO preview + recording when ACTION is on and the user toggle allows.
    var eisEnabled by mutableStateOf(prefs?.getBoolean("eis_enabled", true) ?: true)
    /** Runtime: the EIS GL pipeline currently owns the camera/preview. */
    var eisActive by mutableStateOf(false)
    /** Runtime latch: the pipeline failed on this device -> hw/plain fallback. */
    var eisFailed by mutableStateOf(false)
    /** Runtime: the EIS GL preview has actually presented frames, so the UI
     *  may swap the CameraX preview out for the GL surface. Until the first
     *  frame lands, the CameraX preview stays composed (never a black
     *  viewfinder while the pipeline spins up or fails). */
    var eisPreviewLive by mutableStateOf(false)

    /** True while ACTION-EIS records a >1080p selection at its 1080p cap. */
    val eisCapped: Boolean
        get() = eisActive && videoRes?.qualityName == "UHD"
    var timelapseRunning by mutableStateOf(false)
    var timelapseFrames by mutableStateOf(0)
    val timelapseIntervalMs: Long = 1000L

    // Panorama (real sweep-and-stitch, see Pano.kt): shutter starts a guided
    // sweep; the gyroscope (or a timed fallback) triggers frame captures at
    // even yaw steps and the frames are cylindrically stitched into one JPEG.
    var panoSweeping by mutableStateOf(false)
    var panoStitching by mutableStateOf(false)
    var panoProgress by mutableFloatStateOf(0f)
    var panoFrameCount by mutableStateOf(0)
    var panoTooFast by mutableStateOf(false)
    var panoDirRight by mutableStateOf(true)

    // Grade adjustment (Styles/Filters): overall intensity + warmth shift,
    // applied for real on top of the selected preset's matrix.
    var gradeIntensity by mutableFloatStateOf(prefs?.getFloat("grade_intensity", 1f) ?: 1f)
    var gradeWarmth by mutableFloatStateOf(prefs?.getFloat("grade_warmth", 0f) ?: 0f)

    /** The active colour grade preset: a chosen filter wins over a style, like iOS. */
    fun activeGrade(): GradePreset? =
        filterId?.let { id -> FilterPresets.firstOrNull { it.id == id } }
            ?: styleId?.let { id -> StylePresets.firstOrNull { it.id == id } }
            ?: if (mode == CamMode.PORTRAIT) {
                // Portrait lighting rides the same real grade pipeline:
                // a chosen lighting look grades the captured portrait.
                PortraitLights.firstOrNull { it.preset.id == portraitLightId }
                    ?.takeIf { it.real && it.preset.id != "natural" }?.preset
            } else null

    /** The active grade as a colour matrix, including intensity/warmth adjustments. */
    fun activeGradeMatrix(): FloatArray? =
        activeGrade()?.matrix(gradeIntensity, gradeWarmth)

    /** Portrait blur strength 0..1 derived from the ƒ slider (wide ƒ = strong blur). */
    val apertureStrength: Float
        get() = ((16f - apertureF) / (16f - 1.4f)).coerceIn(0.05f, 1f)

    /**
     * Whether the hardware exposes a wider-than-1x view: the logical camera's
     * zoom-ratio range dips below 1 (fused ultra-wide) or a physical
     * ultra-wide lens session exists. Only then does the 0.5 stop appear.
     */
    val hasUltraWide: Boolean
        get() = caps.logicalMinZoomRatio < 0.99f || caps.backSessions.any { it.ratio < 0.95f }

    /**
     * Capability-based mode availability: CINEMATIC has no reliable
     * public-API path on this platform, so it stays visible in the carousel
     * but dimmed and unselectable; SLO-MO needs real >=120fps support.
     * PANO is real (gyro-guided sweep + cylindrical stitch, Pano.kt) and
     * always available — without a gyroscope it falls back to timed
     * captures with the same guide.
     */
    fun modeAvailable(m: CamMode): Boolean = when (m) {
        CamMode.PHOTO, CamMode.VIDEO -> true
        // BOKEH extension when the OEM provides it, otherwise the real
        // ML Kit segmentation portrait pipeline.
        CamMode.PORTRAIT -> bokehExtAvailable || PortraitFallback.available
        CamMode.TIME_LAPSE -> caps.timelapseAvailable
        CamMode.SLO_MO -> caps.sloMoFps >= 120
        CamMode.PANO -> true
        CamMode.CINEMATIC -> false
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

    /** Raw zoom ceiling before the dial-minimum clamp (no quickStops dependency). */
    private fun computedMaxZoom(): Float {
        var m = currentSessionRatio * sessionMaxZoom
        if (caps.backSessions.any { it.ratio > 1.3f }) m = maxOf(m, 40f)
        return minOf(40f, m)
    }

    /**
     * Absolute ratio of the ultra-wide stop: the physical ultra-wide
     * session's real ratio when one is bindable, else 0.5 (logical camera
     * fusing the ultra-wide below 1x). Only meaningful when [hasUltraWide].
     */
    private fun uwStopRatio(): Float =
        caps.backSessions.firstOrNull { it.ratio < 0.95f }?.ratio ?: 0.5f

    /**
     * Quick-pick zoom stops (absolute ratios) shown as small buttons.
     * The user's exact rule (iPhone 17 Pro pattern, nothing else):
     * 0.5 · 1x · 2x · 8x — never a row of integer steps and never a
     * per-lens telephoto button (a 4x lens does NOT add a 4x button).
     *
     *  - 0.5 only when an ultra-wide is hardware-detected (the logical
     *    camera fuses below 1x, or a physical ultra-wide lens exists);
     *    the value is the real ultra-wide ratio.
     *  - 1x (main lens) and 2x are always shown.
     *  - 8x only when this device's zoom range reaches it. Below that,
     *    the device's real maximum takes its place (real number label);
     *    when the maximum is 2x or less, no top stop is shown at all.
     *
     * Physical telephoto lenses are still picked up by the zoom engine
     * itself ([sessionFor]) as the ratio sweeps past them. Continuous
     * zoom (arc dial via long-press on any stop, pinch, spring) still
     * runs smoothly to min(40x, device max); these are only the buttons.
     */
    fun quickStops(): List<Float> {
        if (facingFront) return listOf(1f)
        val maxZ = computedMaxZoom()
        val stops = mutableListOf<Float>()
        if (hasUltraWide) stops += uwStopRatio()
        stops += 1f
        if (maxZ >= 2f) stops += 2f
        when {
            maxZ >= 8f -> stops += 8f
            maxZ > 2f -> stops += maxZ
        }
        return stops.filter { it <= maxZ + 0.01f }.distinct().sorted()
    }

    val dialMin: Float
        get() = minOf(
            quickStops().firstOrNull() ?: 1f,
            currentSessionRatio * sessionMinZoom
        ).coerceAtLeast(0.1f)

    /** Absolute zoom ceiling: 40x, or the device's real maximum when lower. */
    val dialMax: Float
        get() = computedMaxZoom().coerceAtLeast(dialMin + 1f)

    val currentEqMm: Int
        get() = if (caps.baseEqMm > 0f) (zoomRatio * caps.baseEqMm).roundToInt() else 0

    fun persistAll() {
        val p = prefs ?: return
        p.edit()
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
            .putString("filter_id", filterId)
            .putString("style_id", styleId)
            .putString("portrait_light", portraitLightId)
            .putFloat("aperture_f", apertureF)
            .putBoolean("eis_enabled", eisEnabled)
            .putFloat("grade_intensity", gradeIntensity)
            .putFloat("grade_warmth", gradeWarmth)
            .putString("config_preset", configPresetId)
            .putFloat("config_sharpness", configSharpness)
            .putFloat("config_saturation", configSaturation)
            .putFloat("config_contrast", configContrast)
            .putFloat("config_gamma", configGamma)
            .putFloat("config_denoise", configDenoise)
            .putBoolean("onboarded", onboardingDone)
            .apply()
    }
}
