package net.adnan120hz.camera26

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test

/**
 * Screenshot verification of the real Compose camera UI (rendered on the JVM
 * via Paparazzi/layoutlib — no device). The live preview is represented by a
 * dark gradient inside the exact preview area the production layout computes;
 * a simulated Android navigation bar (48dp) is drawn at the bottom so the
 * bottom cluster's clearance above it can be verified visually.
 */
class CameraUiScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    private fun baseState(ctx: Context): CameraState = CameraState(ctx).apply {
        caps = DeviceCaps(
            // iPhone 17 Pro-style lens set: ultra-wide + main + 4x telephoto.
            // Quick stops must read EXACTLY 0.5 / 1x / 2x / 8x — the 4x
            // telephoto lens adds no button of its own (user rule).
            backSessions = listOf(
                LensSession("uw", 0.5f, 13f, "0.5"),
                LensSession(null, 1f, 24f, "1x"),
                LensSession("tele", 4f, 96f, "4x")
            ),
            baseEqMm = 24f,
            logicalMinZoomRatio = 0.5f,
            fpsOptions = listOf(24, 30, 60),
            maxFps = 60,
            hasFlashUnit = true,
            sloMoFps = 240,
            timelapseAvailable = true,
            videoStabilization = true,
            oisAvailable = true,
            gyroAvailable = true
        )
        capsReady = true
        bokehExtAvailable = true
        nightExtAvailable = true
        exposureMin = -6
        exposureMax = 6
        exposureStep = 0.5f
        videoResOptions = listOf(
            VideoResOption("4K", "UHD"),
            VideoResOption("HD", "FHD"),
            VideoResOption("720p", "HD")
        )
        videoRes = videoResOptions[1]
        fps = 30
        sessionMinZoom = 0.5f
        sessionMaxZoom = 30f
        aspect = PhotoAspect.RATIO_4_3
        flash = FlashSetting.AUTO
        thumbBitmap = fakeThumb()
    }

    private fun fakeThumb(): Bitmap? = try {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, 160f,
                android.graphics.Color.rgb(70, 90, 120),
                android.graphics.Color.rgb(20, 24, 32),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, 160f, 160f, paint)
        bmp
    } catch (e: Throwable) {
        null
    }

    private fun actions(state: CameraState) = CameraActions(
        onModeSelect = { state.mode = it },
        onOpenSheet = { state.sheet = it },
        onCloseSheet = { state.sheet = SheetKind.NONE },
        onShutterTap = {},
        onShutterHoldStart = {},
        onShutterHoldEnd = {},
        onFlip = { state.facingFront = !state.facingFront },
        onZoomTo = { state.zoomTarget = it; state.zoomRatio = it },
        onDialShow = { state.dialVisible = true },
        onFlash = { state.flash = it },
        onToggleTorch = { state.videoTorch = !state.videoTorch },
        onTimer = { state.timerSec = it },
        onAspect = { state.aspect = it },
        onExposure = { state.exposureIndex = it },
        onVideoRes = { state.videoRes = it },
        onFps = { state.fps = it },
        onToggleGrid = { state.gridOn = !state.gridOn },
        onToggleNight = { state.nightOn = !state.nightOn },
        onThumbnailTap = {}
    )

    @Composable
    private fun SimulatedNavBar() {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xF2000000))
                .padding(horizontal = 90.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            // 3-button navigation glyphs: triangle (back), circle (home), square (recents)
            ComposeCanvas(Modifier.size(22.dp)) {
                val w = size.width
                drawLine(
                    Color.White.copy(alpha = 0.85f),
                    Offset(w * 0.70f, w * 0.15f), Offset(w * 0.30f, w * 0.5f),
                    strokeWidth = 2.4f
                )
                drawLine(
                    Color.White.copy(alpha = 0.85f),
                    Offset(w * 0.30f, w * 0.5f), Offset(w * 0.70f, w * 0.85f),
                    strokeWidth = 2.4f
                )
            }
            ComposeCanvas(Modifier.size(22.dp)) {
                drawCircle(
                    Color.White.copy(alpha = 0.85f),
                    radius = size.width * 0.32f,
                    style = Stroke(2.4f)
                )
            }
            ComposeCanvas(Modifier.size(22.dp)) {
                drawRect(
                    Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(size.width * 0.22f, size.width * 0.22f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.width * 0.56f),
                    style = Stroke(2.4f)
                )
            }
        }
    }

    @Composable
    private fun Harness(state: CameraState) {
        val cfg = LocalConfiguration.current
        val letterboxedMode = state.mode == CamMode.PHOTO || state.mode == CamMode.PORTRAIT ||
            state.mode == CamMode.TIME_LAPSE
        // Preview region: screen minus the black control strip (166dp of
        // content) and the simulated 48dp navigation bar — mirrors the
        // production layout, where the shutter/pill sit on the strip.
        val regionH = cfg.screenHeightDp.toFloat() - BOTTOM_STRIP_HEIGHT_DP - 48f
        val area = previewAreaDp(
            aspect = state.aspect,
            filled = state.previewFilled,
            letterboxedMode = letterboxedMode,
            screenW = cfg.screenWidthDp.toFloat(),
            screenH = regionH
        )
        val fullBleed = area.first >= cfg.screenWidthDp.toFloat() - 0.5f &&
            area.second >= regionH - 0.5f
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Stand-in for the live camera preview: gradient inside the
            // production-computed preview area at the region's bottom edge
            // (black bands stay black and merge into the strip).
            Box(Modifier.fillMaxWidth().height(regionH.dp)) {
                Box(
                    (if (fullBleed) Modifier.fillMaxSize()
                    else Modifier.align(Alignment.BottomCenter).size(area.first.dp, area.second.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF232936), Color(0xFF0D1017))
                            )
                        )
                )
            }
            // 48dp here simulates the real navigation-bar inset the
            // production UI applies via WindowInsets.navigationBars.
            Box(Modifier.fillMaxSize().padding(bottom = 48.dp)) {
                Controls26(state, actions(state))
            }
            // Tap-focus reticle: the exact production composable, placed
            // where the test state's focus point says (preview coords, px).
            state.focusPoint?.let { fp ->
                val halfPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    46.dp.toPx()
                }
                Box(
                    Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (fp.x - halfPx).toInt(), (fp.y - halfPx).toInt()
                            )
                        }
                        .size(92.dp)
                ) {
                    FocusReticle(state, Modifier.fillMaxSize())
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                SimulatedNavBar()
            }
        }
    }

    @Test
    fun photoIdle() {
        val state = baseState(paparazzi.context)
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun carouselMidDrag() {
        val state = baseState(paparazzi.context).apply {
            // Frozen mid-swipe frame: pill dragged 70px toward PORTRAIT.
            carouselDragPx = -70f
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun photoSheet() {
        val state = baseState(paparazzi.context).apply {
            sheet = SheetKind.GRID
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun videoMode() {
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.VIDEO
            aspect = PhotoAspect.RATIO_16_9
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun sloMoMode() {
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.SLO_MO
            aspect = PhotoAspect.RATIO_16_9
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun videoActionSheet() {
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.VIDEO
            aspect = PhotoAspect.RATIO_16_9
            actionOn = true
            eisEnabled = true
            sheet = SheetKind.ACTION
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun videoEisActive() {
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.VIDEO
            aspect = PhotoAspect.RATIO_16_9
            actionOn = true
            eisEnabled = true
            // 4K selected while EIS is live -> the 1080p cap flag must show.
            videoRes = videoResOptions[0]
            eisActive = true
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun frontCamera() {
        val state = baseState(paparazzi.context).apply {
            // Front camera rules: quick stops collapse to 1x, and the
            // expand (⤢) button appears above the shutter. On the back
            // camera it must not exist (see photoIdle: 4:3, no ⤢).
            facingFront = true
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun settingsScreen() {
        val state = baseState(paparazzi.context)
        paparazzi.snapshot { CameraSettingsScreen(state, onBack = {}) }
    }

    @Test
    fun stylesPanel() {
        // iOS adjustment bar: ✕ + TONE/WARMTH tick scales, VIBRANT label
        // pill + page dots above it (no Material sliders).
        val state = baseState(paparazzi.context).apply {
            styleId = "vibrant"
            gradeIntensity = 0.8f
            gradeWarmth = 6f
            sheet = SheetKind.STYLES
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun exposurePanel() {
        // Exposure tick bar with the yellow "+1.0" value, plus the
        // transient "FLASH ON" status banner at the top centre.
        val state = baseState(paparazzi.context).apply {
            exposureIndex = 2
            bannerText = "FLASH ON"
            sheet = SheetKind.EXPOSURE
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun tapFocus() {
        // Yellow focus box with the sun on its right-side track (drag
        // exposure); exposure pushed +1 step so the sun rides up.
        val state = baseState(paparazzi.context).apply {
            focusPoint = androidx.compose.ui.geometry.Offset(560f, 950f)
            exposureIndex = 1
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun portraitLight() {
        // PORTRAIT idle: lighting wheel (cube + curved dots) and the
        // yellow NATURAL LIGHT label above the shutter.
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.PORTRAIT
            portraitLightId = "natural"
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun panoIdle() {
        // PANO is a real mode now (not dimmed): full-region preview with
        // the iOS-style sweep guide centred — track, travelling frame
        // window with direction chevron, hint pill.
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.PANO
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun panoSweep() {
        // Mid-sweep: progress fill part-way, captured-frame ticks, the
        // frame window travelled with the pan, shutter in stop state.
        val state = baseState(paparazzi.context).apply {
            mode = CamMode.PANO
            panoSweeping = true
            panoProgress = 0.45f
            panoFrameCount = 6
            panoDirRight = true
        }
        paparazzi.snapshot { Harness(state) }
    }

    @Test
    fun carouselSwipeGif() {
        // Motion evidence attempt: Paparazzi 1.3.5 gif() is View-based, so
        // the same Compose harness is hosted in a ComposeView while a
        // scripted "finger" runs the real interaction: rest (plain capsule)
        // -> press (capsule swells into the Liquid Glass jelly bubble) ->
        // drag out -> release (bubble wobbles back down, strip snaps back).
        val state = baseState(paparazzi.context)
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    // Rest: capsule exactly as in the still screenshots.
                    delay(250)
                    // Finger lands: the selected capsule swells up.
                    state.carouselPressed = true
                    delay(300)
                    // Drag toward PORTRAIT with the bubble swollen.
                    anim.animateTo(
                        -190f,
                        spring(
                            dampingRatio = 0.80f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { state.carouselDragPx = value }
                    // Finger lifts: bubble deflates on its low-damping
                    // spring while the strip springs back to rest.
                    state.carouselPressed = false
                    anim.animateTo(
                        0f,
                        spring(
                            dampingRatio = 0.80f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { state.carouselDragPx = value }
                }
                Harness(state)
            }
        }
        paparazzi.gif(view, "carouselSwipe", 0L, 2400L, 30)
    }

    /**
     * Recording harness: the same layout as [Harness], plus the two pieces
     * of motion that production (CameraScreen) layers over Controls26 and
     * the stills harness omits — the tap-focus reticle pop and the mode-
     * change dim pulse — replicated with production's exact specs. The
     * LocalInspectionMode override is local to this recording: on a real
     * device inspection mode is off, so the sheet / ResolutionCard use
     * their real spring transitions instead of snapping open instantly.
     */
    @Composable
    private fun RecordingHarness(state: CameraState) {
        val cfg = LocalConfiguration.current
        val letterboxedMode = state.mode == CamMode.PHOTO || state.mode == CamMode.PORTRAIT ||
            state.mode == CamMode.TIME_LAPSE
        val regionH = cfg.screenHeightDp.toFloat() - BOTTOM_STRIP_HEIGHT_DP - 48f
        val area = previewAreaDp(
            aspect = state.aspect,
            filled = state.previewFilled,
            letterboxedMode = letterboxedMode,
            screenW = cfg.screenWidthDp.toFloat(),
            screenH = regionH
        )
        val fullBleed = area.first >= cfg.screenWidthDp.toFloat() - 0.5f &&
            area.second >= regionH - 0.5f
        // CameraScreen: focus Scale snap 1.3 -> MediumBouncy spring settle.
        val focusScale = remember { Animatable(1f) }
        LaunchedEffect(state.focusNonce) {
            if (state.focusPoint != null) {
                focusScale.snapTo(1.3f)
                focusScale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
            }
        }
        // CameraScreen: mode change = brief black pulse 0.30 -> 0 (150ms).
        val modePulse = remember { Animatable(0f) }
        var firstModeEffect = remember { androidx.compose.runtime.mutableStateOf(true) }
        LaunchedEffect(state.mode) {
            if (firstModeEffect.value) {
                firstModeEffect.value = false
            } else {
                modePulse.snapTo(0.30f)
                modePulse.animateTo(0f, tween(150))
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().height(regionH.dp)) {
                Box(
                    (if (fullBleed) Modifier.fillMaxSize()
                    else Modifier.align(Alignment.BottomCenter).size(area.first.dp, area.second.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF232936), Color(0xFF0D1017))
                            )
                        )
                )
                if (modePulse.value > 0f) {
                    Box(
                        (if (fullBleed) Modifier.fillMaxSize()
                        else Modifier.align(Alignment.BottomCenter).size(area.first.dp, area.second.dp))
                            .background(Color.Black.copy(alpha = modePulse.value))
                    )
                }
            }
            Box(Modifier.fillMaxSize().padding(bottom = 48.dp)) {
                CompositionLocalProvider(LocalInspectionMode provides false) {
                    Controls26(state, actions(state))
                }
            }
            state.focusPoint?.let { fp ->
                val halfPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    46.dp.toPx()
                }
                Box(
                    Modifier
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                (fp.x - halfPx).toInt(), (fp.y - halfPx).toInt()
                            )
                        }
                        .size(92.dp)
                        .scale(focusScale.value)
                ) {
                    FocusReticle(state, Modifier.fillMaxSize())
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) {
                SimulatedNavBar()
            }
        }
    }

    @Test
    fun walkthroughRecord() {
        // Full UI walkthrough for the user, mirroring the reference-video
        // interaction order. Paparazzi records an APNG (30fps, 13.2s);
        // it is converted to the fix6 MP4/GIF outside the test.
        val state = baseState(paparazzi.context)
        val view = ComposeView(paparazzi.context).apply {
            setContent {
                val drag = remember { Animatable(0f) }
                val warmth = remember { Animatable(6f) }
                val dragSpec = spring<Float>(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow
                )
                LaunchedEffect(Unit) {
                    // 1 — PHOTO idle.
                    delay(1100)
                    // 2 — tap centre: focus box + sun pop in.
                    state.focusPoint = androidx.compose.ui.geometry.Offset(560f, 950f)
                    state.focusNonce++
                    delay(1300)
                    // 3 — grid icon: control sheet springs up.
                    state.sheet = SheetKind.GRID
                    delay(1100)
                    // (production auto-clears the focus box ~2.8s after tap)
                    state.focusPoint = null
                    delay(200)
                    // 4 — STYLES sub-panel; drag WARMTH once (6 -> 16).
                    state.styleId = "vibrant"
                    state.gradeIntensity = 0.8f
                    state.gradeWarmth = 6f
                    state.sheet = SheetKind.STYLES
                    delay(800)
                    warmth.snapTo(6f)
                    warmth.animateTo(
                        16f, tween(durationMillis = 750, easing = FastOutSlowInEasing)
                    ) { state.gradeWarmth = value }
                    delay(300)
                    // 5 — sheet deflates back down to PHOTO idle.
                    state.sheet = SheetKind.NONE
                    delay(750)
                    // 6 — carousel PHOTO -> VIDEO: jelly bubble, snap,
                    // red shutter, "HD RES 30 FPS" format pill.
                    state.carouselPressed = true
                    delay(220)
                    drag.animateTo(175f, dragSpec) { state.carouselDragPx = value }
                    state.mode = CamMode.VIDEO
                    state.aspect = PhotoAspect.RATIO_16_9
                    state.carouselPressed = false
                    drag.animateTo(0f, dragSpec) { state.carouselDragPx = value }
                    delay(400)
                    // 7 — format pill: ResolutionCard opens, holds, closes.
                    state.sheet = SheetKind.RESOLUTION
                    delay(1300)
                    state.sheet = SheetKind.NONE
                    delay(500)
                    // 8 — carousel back to PHOTO; white shutter; tidy close.
                    state.carouselPressed = true
                    delay(220)
                    drag.animateTo(-175f, dragSpec) { state.carouselDragPx = value }
                    state.mode = CamMode.PHOTO
                    state.aspect = PhotoAspect.RATIO_4_3
                    state.carouselPressed = false
                    drag.animateTo(0f, dragSpec) { state.carouselDragPx = value }
                }
                RecordingHarness(state)
            }
        }
        paparazzi.gif(view, "walkthrough", 0L, 13200L, 30)
    }
}
