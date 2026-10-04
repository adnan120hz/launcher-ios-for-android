package net.adnan120hz.camera26

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Shared iOS-style spring specs: sheets/trays/cards always bounce, never tween stiffly. */
val SheetEnterSpec: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
val SheetExitSpec: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)

class CameraActions(
    val onModeSelect: (CamMode) -> Unit,
    val onOpenSheet: (SheetKind) -> Unit,
    val onCloseSheet: () -> Unit,
    val onShutterTap: () -> Unit,
    val onShutterHoldStart: () -> Unit,
    val onShutterHoldEnd: () -> Unit,
    val onFlip: () -> Unit,
    val onZoomTo: (Float) -> Unit,
    val onDialShow: () -> Unit,
    val onFlash: (FlashSetting) -> Unit,
    val onToggleTorch: () -> Unit,
    val onTimer: (Int) -> Unit,
    val onAspect: (PhotoAspect) -> Unit,
    val onExposure: (Int) -> Unit,
    val onVideoRes: (VideoResOption) -> Unit,
    val onFps: (Int) -> Unit,
    val onToggleGrid: () -> Unit,
    val onToggleNight: () -> Unit,
    val onThumbnailTap: () -> Unit,
    /** Cancel an in-progress panorama sweep (guide ✕). Default: no-op. */
    val onPanoCancel: () -> Unit = {}
)

@Composable
fun ShutterButton(
    state: CameraState,
    actions: CameraActions,
    modifier: Modifier = Modifier,
    /** TIME-LAPSE idle look: a dotted ring around the shutter, like iOS. */
    dottedRing: Boolean = false
) {
    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val recording = state.isRecording || state.timelapseRunning || state.panoSweeping
    val innerSize by animateDpAsState(
        targetValue = if (recording) 32.dp else 62.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shutterInner"
    )
    Box(
        modifier
            .size(78.dp)
            .scale(pressScale.value)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        scope.launch {
                            pressScale.animateTo(
                                0.92f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                        val up = withTimeoutOrNull(380) { waitForUpOrCancellation() }
                        if (up == null) {
                            actions.onShutterHoldStart()
                            waitForUpOrCancellation()
                            actions.onShutterHoldEnd()
                        } else {
                            actions.onShutterTap()
                        }
                        scope.launch {
                            pressScale.animateTo(
                                1f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (dottedRing) {
            androidx.compose.foundation.Canvas(Modifier.size(86.dp)) {
                drawCircle(
                    Color.White.copy(alpha = 0.85f),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.4.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
        } else {
            Box(
                Modifier
                    .size(78.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.75f), CircleShape)
            )
        }
        val innerColor =
            if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO ||
                state.mode == CamMode.TIME_LAPSE || recording
            ) Color(0xFFFF3B30) else Color.White
        Box(
            Modifier
                .size(innerSize)
                .clip(if (recording) RoundedCornerShape(9.dp) else CircleShape)
                .background(innerColor)
        )
    }
}

@Composable
fun ThumbnailButton(
    state: CameraState,
    actions: CameraActions,
    modifier: Modifier = Modifier
) {
    val bmp = state.thumbBitmap
    if (bmp == null) {
        // iOS shows nothing here until a photo exists — no placeholder glyph.
        Spacer(modifier.size(46.dp))
        return
    }
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C2E))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable { actions.onThumbnailTap() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

@Composable
fun FlipButton(actions: CameraActions, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable { actions.onFlip() },
        contentAlignment = Alignment.Center
    ) {
        FlipGlyph(Color.White, Modifier.size(26.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickZoomButtons(
    state: CameraState,
    actions: CameraActions,
    modifier: Modifier = Modifier
) {
    val stops = state.quickStops()
    // At most four stops (0.5 / 1x / 2x / 8x) — always the full-size row.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        stops.forEach { stop ->
            val active = abs(state.zoomRatio - stop) < 0.07f
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassPillBrush)
                    .border(1.dp, if (active) IosYellow.copy(alpha = 0.55f) else GlassRim, CircleShape)
                    .combinedClickable(
                        onClick = { actions.onZoomTo(stop) },
                        onLongClick = { actions.onDialShow(); actions.onZoomTo(stop) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatRatioLabel(stop),
                    color = if (active) IosYellow else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** Circular control inside the sheets. Functional controls only light up;
 *  [enabled] = false renders it dimmed (its onClick still fires, so the UI
 *  can explain honestly why it is unavailable). */
@Composable
fun ControlButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (enabled) GlassButton else GlassButton.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            glyph(
                when {
                    !enabled -> Color.White.copy(alpha = 0.32f)
                    active -> IosYellow
                    else -> Color.White
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) IosYellow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) IosYellow else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** One square swatch in the FILTER / STYLES picker, rendered from the real grade matrix. */
@Composable
private fun GradeSwatch(
    topColor: Color,
    bottomColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(topColor, bottomColor)
                    )
                )
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) IosYellow else Color.White.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
        )
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (selected) IosYellow else Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            modifier = Modifier.width(60.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** Square-swatch picker for FILTER / STYLES (real colour grades) + adjustment sliders. */
@Composable
private fun GradeSwatches(
    presets: List<GradePreset>,
    selectedId: String?,
    intensity: Float,
    onSelect: (String?) -> Unit
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // "No grade" swatch: an ungraded neutral gradient.
        GradeSwatch(
            Color(0xFF8A8F98), Color(0xFF4A4F57),
            "Tidak ada", selectedId == null
        ) { onSelect(null) }
        presets.forEach { preset ->
            val (top, bottom) = gradeSwatchColors(preset, intensity)
            GradeSwatch(top, bottom, preset.label, selectedId == preset.id) {
                onSelect(preset.id)
            }
        }
    }
}

/** Intensity + warmth sliders: real adjustments layered onto the active grade. */
@Composable
private fun GradeAdjustSliders(state: CameraState) {
    if (state.activeGrade() == null) return
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Intensitas", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Slider(
            value = state.gradeIntensity,
            onValueChange = { state.gradeIntensity = it },
            onValueChangeFinished = { state.persistAll() },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = IosYellow,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Text(
            "${(state.gradeIntensity * 100).toInt()}%",
            color = IosYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tone", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text("Dingin", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
        Slider(
            value = state.gradeWarmth,
            onValueChange = { state.gradeWarmth = it },
            onValueChangeFinished = { state.persistAll() },
            valueRange = -30f..30f,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = IosYellow,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Text("Hangat", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Diterapkan nyata pada foto saat dijepret" +
            if (android.os.Build.VERSION.SDK_INT >= 31) " dan terlihat langsung di pratinjau." else ".",
        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
    )
}

/** The ƒ slider: controls Portrait background-blur strength on the segmentation path. */
@Composable
fun ApertureSlider(state: CameraState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ƒ", color = Color.White, fontSize = 20.sp)
        Slider(
            value = state.apertureF,
            onValueChange = { state.apertureF = it },
            onValueChangeFinished = { state.persistAll() },
            valueRange = 1.4f..16f,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            colors = SliderDefaults.colors(
                thumbColor = IosYellow,
                activeTrackColor = Color.White.copy(alpha = 0.85f),
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Text(
            String.format(Locale.US, "ƒ/%.1f", state.apertureF),
            color = IosYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Mengatur kekuatan blur latar Portrait di perangkat ini (ƒ kecil = blur kuat).",
        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
    )
}

/** Sub-panel shown after tapping a sheet item (FLASH / EXPOSURE / TIMER / ASPECT / FILTER / STYLES / APERTURE). */
@Composable
fun SubPanelContent(kind: SheetKind, state: CameraState, actions: CameraActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
        val title = when (kind) {
            SheetKind.FLASH -> "FLASH"
            SheetKind.EXPOSURE -> "EXPOSURE"
            SheetKind.TIMER -> "TIMER"
            SheetKind.ASPECT -> "ASPECT"
            SheetKind.FILTER -> "FILTER"
            SheetKind.STYLES -> "STYLES"
            SheetKind.APERTURE -> "APERTURE"
            SheetKind.ACTION -> "ACTION"
            SheetKind.CONFIG -> "CONFIG"
            else -> ""
        }
        Text(title, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when (kind) {
            SheetKind.FLASH -> {
                if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip("Mati", !state.videoTorch) {
                            if (state.videoTorch) {
                                actions.onToggleTorch()
                                state.showBanner("FLASH OFF")
                            }
                        }
                        OptionChip("Nyala", state.videoTorch) {
                            if (!state.videoTorch) {
                                actions.onToggleTorch()
                                state.showBanner("FLASH ON")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Flash video memakai lampu senter kamera.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip("Auto", state.flash == FlashSetting.AUTO) {
                            actions.onFlash(FlashSetting.AUTO)
                            state.showBanner("FLASH AUTO")
                        }
                        OptionChip("On", state.flash == FlashSetting.ON) {
                            actions.onFlash(FlashSetting.ON)
                            state.showBanner("FLASH ON")
                        }
                        OptionChip("Off", state.flash == FlashSetting.OFF) {
                            actions.onFlash(FlashSetting.OFF)
                            state.showBanner("FLASH OFF")
                        }
                    }
                }
            }
            SheetKind.EXPOSURE -> {
                if (state.exposureSupported) {
                    val valueText = String.format(
                        Locale.US, "%+.1f", state.exposureIndex * state.exposureStep
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("−", color = Color.White, fontSize = 20.sp)
                        Slider(
                            value = state.exposureIndex.toFloat(),
                            onValueChange = { actions.onExposure(it.toInt()) },
                            valueRange = state.exposureMin.toFloat()..state.exposureMax.toFloat(),
                            steps = (state.exposureMax - state.exposureMin - 1).coerceAtLeast(0),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = IosYellow,
                                activeTrackColor = Color.White.copy(alpha = 0.85f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                        Text("+", color = Color.White, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(valueText, color = IosYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Exposure compensation tidak didukung kamera ini.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp
                    )
                }
            }
            SheetKind.TIMER -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChip("Off", state.timerSec == 0) {
                        actions.onTimer(0)
                        state.showBanner("TIMER OFF")
                    }
                    OptionChip("3s", state.timerSec == 3) {
                        actions.onTimer(3)
                        state.showBanner("TIMER 3S")
                    }
                    OptionChip("5s", state.timerSec == 5) {
                        actions.onTimer(5)
                        state.showBanner("TIMER 5S")
                    }
                    OptionChip("10s", state.timerSec == 10) {
                        actions.onTimer(10)
                        state.showBanner("TIMER 10S")
                    }
                }
            }
            SheetKind.ASPECT -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChip("4:3", state.aspect == PhotoAspect.RATIO_4_3) { actions.onAspect(PhotoAspect.RATIO_4_3) }
                    OptionChip("16:9", state.aspect == PhotoAspect.RATIO_16_9) { actions.onAspect(PhotoAspect.RATIO_16_9) }
                    OptionChip("1:1", state.aspect == PhotoAspect.SQUARE) { actions.onAspect(PhotoAspect.SQUARE) }
                }
            }
            SheetKind.FILTER -> {
                GradeSwatches(
                    presets = FilterPresets,
                    selectedId = state.filterId,
                    intensity = state.gradeIntensity,
                    onSelect = { id ->
                        state.filterId = id
                        if (id != null) state.styleId = null
                        state.persistAll()
                    }
                )
                GradeAdjustSliders(state)
            }
            SheetKind.STYLES -> {
                GradeSwatches(
                    presets = StylePresets,
                    selectedId = state.styleId,
                    intensity = state.gradeIntensity,
                    onSelect = { id ->
                        state.styleId = id
                        if (id != null) state.filterId = null
                        state.persistAll()
                    }
                )
                GradeAdjustSliders(state)
            }
            SheetKind.APERTURE -> {
                ApertureSlider(state)
            }
            SheetKind.ACTION -> {
                // Master ACTION toggle.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChip("Nonaktif", !state.actionOn) { state.actionOn = false }
                    OptionChip("Aktif", state.actionOn) { state.actionOn = true }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "EIS (SOFTWARE)", color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.caps.motionAvailable -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OptionChip("Nonaktif", !state.eisEnabled) {
                                state.eisEnabled = false
                                state.persistAll()
                            }
                            OptionChip("Aktif", state.eisEnabled) {
                                state.eisEnabled = true
                                state.persistAll()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                state.mode == CamMode.SLO_MO ->
                                    "SLO-MO memakai stabilisasi hardware (API); EIS software berlaku untuk mode VIDEO."
                                state.caps.gyroAvailable && state.eisActive ->
                                    "EIS software sedang aktif — sumber gerak: Gyroscope."
                                state.caps.gyroAvailable ->
                                    "EIS software (gyro) menyala otomatis saat ACTION aktif di mode VIDEO."
                                state.eisActive ->
                                    "EIS software sedang aktif — sumber gerak: Sensor gerak (akselerometer + kompas), karena perangkat ini tanpa gyroscope."
                                else ->
                                    "Perangkat ini tanpa gyroscope — EIS software memakai sensor gerak gabungan (akselerometer + kompas) dan menyala otomatis saat ACTION aktif di mode VIDEO."
                            },
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                    state.caps.videoStabilization || state.caps.oisAvailable -> {
                        Text(
                            "Tidak ada sensor gerak yang dapat dipakai — ACTION memakai stabilisasi hardware (OIS/EIS via API kamera).",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                    else -> {
                        Text(
                            "Perangkat ini tidak melaporkan gyroscope, sensor gerak gabungan, maupun stabilisasi hardware.",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Pipeline EIS maks 1920×1080 demi performa — pilihan 4K direkam sebagai 1080p saat EIS aktif (ditandai di layar). Rolling shutter tidak dikoreksi.",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
            }
            SheetKind.CONFIG -> {
                ConfigPanel(state)
            }
            else -> Unit
        }
    }
}

/**
 * CONFIG panel: this app's own photo-quality configuration. Preset chips
 * fill the five sliders; any slider move marks the setup "Kustom". All of
 * it is applied for real to captured photos (PhotoConfigProcessor) — the
 * panel says so instead of pretending to be a GCam XML import.
 */
@Composable
private fun ConfigPanel(state: CameraState) {
    // Preset chips.
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PhotoConfigPresets.forEach { preset ->
            OptionChip(preset.label, state.configPresetId == preset.id) {
                state.applyConfigPreset(preset)
            }
        }
        if (state.configPresetId == "custom") {
            OptionChip("Kustom", true) { /* already custom */ }
        }
    }
    Spacer(Modifier.height(14.dp))

    @Composable
    fun configSlider(
        label: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.width(96.dp)
            )
            Slider(
                value = value,
                onValueChange = { v ->
                    onChange(v)
                    state.markConfigCustom()
                },
                onValueChangeFinished = { state.persistAll() },
                valueRange = range,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = IosYellow,
                    activeTrackColor = Color.White.copy(alpha = 0.85f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                )
            )
            Text(
                valueText,
                color = IosYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(46.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }

    configSlider(
        "Ketajaman", "${(state.configSharpness * 100).roundToInt()}",
        state.configSharpness, 0f..1f
    ) { state.configSharpness = it }
    configSlider(
        "Saturasi", String.format(Locale.US, "%.2f", state.configSaturation),
        state.configSaturation, 0.5f..1.6f
    ) { state.configSaturation = it }
    configSlider(
        "Kontras", String.format(Locale.US, "%.2f", state.configContrast),
        state.configContrast, 0.6f..1.5f
    ) { state.configContrast = it }
    configSlider(
        "Gamma", String.format(Locale.US, "%.2f", state.configGamma),
        state.configGamma, 0.6f..1.6f
    ) { state.configGamma = it }
    configSlider(
        "Reduksi Noise", "${(state.configDenoise * 100).roundToInt()}",
        state.configDenoise, 0f..1f
    ) { state.configDenoise = it }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Config bawaan aplikasi ini — diterapkan nyata pada hasil foto.",
            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable { state.applyConfigPreset(PhotoConfigPresets.first()) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Reset", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * RESOLUTION + FRAME RATE card. Every option up to 4K / 60fps is always
 * visible; combinations this device does not support are dimmed and cannot
 * be tapped (the user's explicit rule), supported ones select normally.
 */
@Composable
fun ResolutionCard(state: CameraState, actions: CameraActions, modifier: Modifier = Modifier) {
    val resChoices = listOf(
        VideoResOption("4K", "UHD"),
        VideoResOption("HD", "FHD"),
        VideoResOption("720p", "HD"),
        VideoResOption("SD", "SD")
    )
    val supportedRes = state.videoResOptions.map { it.qualityName }.toSet()
    val fpsChoices = listOf(24, 30, 60)
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(GlassPanelBrush)
            .border(1.dp, GlassRim, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RESOLUTION", color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(104.dp)
            )
            resChoices.forEach { opt ->
                val supported = opt.qualityName in supportedRes
                val selected = supported && state.videoRes?.qualityName == opt.qualityName
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = supported) { actions.onVideoRes(opt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(IosYellow))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        opt.label,
                        color = when {
                            !supported -> Color.White.copy(alpha = 0.30f)
                            selected -> IosYellow
                            else -> Color.White
                        },
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FRAME RATE", color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(104.dp)
            )
            fpsChoices.forEach { f ->
                val supported = f in state.caps.fpsOptions
                val selected = supported && state.fps == f
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = supported) { actions.onFps(f) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(IosYellow))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        f.toString(),
                        color = when {
                            !supported -> Color.White.copy(alpha = 0.30f)
                            selected -> IosYellow
                            else -> Color.White
                        },
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Arc zoom dial (iOS 26). Drag horizontally to zoom. */
@Composable
fun ZoomDial(
    state: CameraState,
    onZoom: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startRatio by remember { mutableFloatStateOf(1f) }
    var acc by remember { mutableFloatStateOf(0f) }
    val gesture = Modifier.pointerInput(Unit) {
        detectDragGestures(
            // Anchor the gesture to the DISPLAYED ratio, not the pending
            // target: if a button animation was still in flight, starting
            // from the stale target made the dial appear to jump (and
            // spring) back — the reported "mental ke 1x" behaviour.
            onDragStart = {
                startRatio = state.zoomRatio
                acc = 0f
                state.zoomDialDriven = true
            },
            onDragEnd = {
                state.zoomDialDriven = false
                onDone()
            },
            onDragCancel = {
                state.zoomDialDriven = false
                onDone()
            },
            onDrag = { change, drag ->
                change.consume()
                acc += drag.x
                onZoom(startRatio * exp(acc / 240f))
            }
        )
    }
    ArcDial(state, gesture.then(modifier))
}

/**
 * iOS-style tick adjuster (Styles TONE / WARMTH, Exposure): a row of small
 * vertical ticks with the taller yellow centre tick marking 0. Drag
 * horizontally; [norm] is -1..1 and maps to the control's real range.
 */
@Composable
fun TickControl(
    label: String,
    valueText: String,
    norm: Float,
    onNormChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(7.dp))
                Text(valueText, color = IosYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
        }
        androidx.compose.foundation.Canvas(
            Modifier
                .width(118.dp)
                .height(24.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onDone() },
                        onDragCancel = { onDone() }
                    ) { change, drag ->
                        change.consume()
                        onNormChange((norm + drag / 130f).coerceIn(-1f, 1f))
                    }
                }
        ) {
            val ticks = 21
            val stepX = size.width / (ticks - 1)
            val centre = (ticks - 1) / 2
            // The scale slides under a fixed yellow marker, like iOS.
            val shift = -norm * size.width / 2f
            for (i in 0 until ticks) {
                val x = i * stepX + shift
                if (x < -4f || x > size.width + 4f) continue
                val isCentre = i == centre
                val h = if (isCentre) size.height * 0.92f else size.height * 0.52f
                drawLine(
                    color = Color.White.copy(alpha = if (isCentre) 0.95f else 0.5f),
                    start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2f),
                    end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2f),
                    strokeWidth = if (isCentre) 2.2f else 1.4f
                )
            }
            // Fixed yellow centre marker.
            drawLine(
                color = IosYellow,
                start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                strokeWidth = 2.4f
            )
        }
    }
}

/** Style/filter name pill + page dots shown above the adjustment bar (iOS). */
@Composable
fun GradeLabelWithDots(kind: SheetKind, state: CameraState) {
    val presets = if (kind == SheetKind.STYLES) StylePresets else FilterPresets
    val activeId = if (kind == SheetKind.STYLES) state.styleId else state.filterId
    val activeIdx = presets.indexOfFirst { it.id == activeId }
    val label = presets.getOrNull(activeIdx)?.label?.uppercase()
        ?: if (kind == SheetKind.STYLES) "STANDARD" else "TANPA FILTER"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (kind == SheetKind.STYLES) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == activeIdx.coerceAtLeast(0)) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(IosYellow)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The iOS 26 adjustment bar that replaces the sheet card for STYLES /
 * FILTER / EXPOSURE: a dark pill with an ✕ at the left and the real
 * controls inside — TONE & WARMTH tick scales for Styles, the square
 * swatch row for Filters, and the exposure tick scale with its yellow
 * value for Exposure. Everything here writes the same real state the
 * capture pipeline reads.
 */
@Composable
fun IosAdjustBar(kind: SheetKind, state: CameraState, actions: CameraActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (kind == SheetKind.STYLES || kind == SheetKind.FILTER) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GradeLabelWithDots(kind, state)
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xE62C2C30))
                .border(1.dp, GlassRim, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable { actions.onOpenSheet(SheetKind.GRID) },
                contentAlignment = Alignment.Center
            ) {
                CloseGlyph(Color.White, Modifier.size(15.dp))
            }
            Spacer(Modifier.width(12.dp))
            when (kind) {
                SheetKind.STYLES -> {
                    TickControl(
                        label = "TONE",
                        valueText = (state.gradeIntensity * 100).roundToInt().toString(),
                        norm = (state.gradeIntensity * 2f - 1f).coerceIn(-1f, 1f),
                        onNormChange = { n ->
                            state.gradeIntensity = ((n + 1f) / 2f).coerceIn(0f, 1f)
                        },
                        onDone = { state.persistAll() },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    TickControl(
                        label = "WARMTH",
                        valueText = state.gradeWarmth.roundToInt().toString(),
                        norm = (state.gradeWarmth / 30f).coerceIn(-1f, 1f),
                        onNormChange = { n -> state.gradeWarmth = n * 30f },
                        onDone = { state.persistAll() },
                        modifier = Modifier.weight(1f)
                    )
                }
                SheetKind.FILTER -> {
                    Box(Modifier.weight(1f)) {
                        GradeSwatches(
                            presets = FilterPresets,
                            selectedId = state.filterId,
                            intensity = state.gradeIntensity,
                            onSelect = { id ->
                                state.filterId = id
                                if (id != null) state.styleId = null
                                state.persistAll()
                            }
                        )
                    }
                }
                SheetKind.EXPOSURE -> {
                    if (state.exposureSupported) {
                        val range = (state.exposureMax - state.exposureMin).coerceAtLeast(1)
                        val norm = ((state.exposureIndex - state.exposureMin).toFloat() / range) * 2f - 1f
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                String.format(
                                    Locale.US, "%+.1f",
                                    state.exposureIndex * state.exposureStep
                                ),
                                color = IosYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            TickControl(
                                label = "",
                                valueText = "",
                                norm = norm,
                                onNormChange = { n ->
                                    val idx = state.exposureMin +
                                        (((n + 1f) / 2f) * range).roundToInt()
                                    actions.onExposure(
                                        idx.coerceIn(state.exposureMin, state.exposureMax)
                                    )
                                },
                                onDone = { }
                            )
                        }
                    } else {
                        Text(
                            "Exposure compensation tidak didukung kamera ini.",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 * Tap-to-focus reticle: yellow iOS box with centre ticks, the sun riding
 * its right-side track with the exposure drag, and the AE/AF LOCK tag.
 * Shared by production (CameraScreen) and the screenshot harness so both
 * render the exact same code.
 */
@Composable
fun FocusReticle(state: CameraState, modifier: Modifier = Modifier) {
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val w = size.width
            val sw = 1.6.dp.toPx()
            drawRect(IosYellow, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            val t = w * 0.09f
            val c = w / 2
            drawLine(IosYellow, androidx.compose.ui.geometry.Offset(c, 0f), androidx.compose.ui.geometry.Offset(c, t), sw)
            drawLine(IosYellow, androidx.compose.ui.geometry.Offset(c, w - t), androidx.compose.ui.geometry.Offset(c, w), sw)
            drawLine(IosYellow, androidx.compose.ui.geometry.Offset(0f, c), androidx.compose.ui.geometry.Offset(t, c), sw)
            drawLine(IosYellow, androidx.compose.ui.geometry.Offset(w - t, c), androidx.compose.ui.geometry.Offset(w, c), sw)
        }
        val sunNorm = if (state.exposureSupported) {
            (state.exposureIndex - state.exposureMin).toFloat() /
                (state.exposureMax - state.exposureMin).coerceAtLeast(1)
        } else 0.5f
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 21.dp)
                .width(1.5.dp)
                .height(56.dp)
                .background(IosYellow.copy(alpha = 0.55f))
        )
        SunGlyph(
            IosYellow,
            Modifier
                .size(17.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 29.dp, y = ((0.5f - sunNorm) * 52).dp)
        )
        if (state.focusLocked) {
            Text(
                "AE/AF LOCK",
                color = Color.Black,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(IosYellow)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}
