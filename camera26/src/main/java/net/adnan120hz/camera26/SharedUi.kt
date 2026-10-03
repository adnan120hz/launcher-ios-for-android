package net.adnan120hz.camera26

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val onUiStyle: (UiStyle) -> Unit,
    val onToggleNight: () -> Unit,
    val onThumbnailTap: () -> Unit,
    val onSegera: (String) -> Unit,
    val onToggleTray: () -> Unit
)

@Composable
fun ShutterButton(
    state: CameraState,
    actions: CameraActions,
    glassRing: Boolean,
    modifier: Modifier = Modifier
) {
    val pressScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val recording = state.isRecording
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
                                0.9f,
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
        Box(
            Modifier
                .size(78.dp)
                .border(
                    if (glassRing) 3.dp else 5.dp,
                    Color.White.copy(alpha = if (glassRing) 0.75f else 1f),
                    CircleShape
                )
        )
        val innerColor =
            if (state.mode == CamMode.VIDEO || recording) Color(0xFFFF3B30) else Color.White
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
    round: Boolean,
    modifier: Modifier = Modifier
) {
    val bmp = state.thumbBitmap
    Box(
        modifier
            .size(46.dp)
            .clip(if (round) CircleShape else RoundedCornerShape(9.dp))
            .background(Color(0xFF2C2C2E))
            .border(1.dp, Color.White.copy(alpha = 0.25f), if (round) CircleShape else RoundedCornerShape(9.dp))
            .clickable { actions.onThumbnailTap() },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            CameraBodyGlyph(Color.White.copy(alpha = 0.5f), Modifier.size(24.dp))
        }
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

@Composable
fun QuickZoomButtons(
    state: CameraState,
    actions: CameraActions,
    modifier: Modifier = Modifier
) {
    val stops = state.quickStops()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        stops.forEach { stop ->
            val active = abs(state.zoomRatio - stop) < 0.07f
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (active) 0.62f else 0.42f))
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

/** Circular control inside the sheets, with label and optional SEGERA tag. */
@Composable
fun ControlButton(
    label: String,
    active: Boolean,
    segera: Boolean = false,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit
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
                .background(GlassButton),
            contentAlignment = Alignment.Center
        ) {
            glyph(if (active) IosYellow else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        if (segera) {
            Text("SEGERA", color = IosYellow, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
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

/** Sub-panel shown after tapping a sheet item (FLASH / EXPOSURE / TIMER / ASPECT). */
@Composable
fun SubPanelContent(kind: SheetKind, state: CameraState, actions: CameraActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
        val title = when (kind) {
            SheetKind.FLASH -> "FLASH"
            SheetKind.EXPOSURE -> "EXPOSURE"
            SheetKind.TIMER -> "TIMER"
            SheetKind.ASPECT -> "ASPECT"
            else -> ""
        }
        Text(title, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when (kind) {
            SheetKind.FLASH -> {
                if (state.mode == CamMode.VIDEO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip("Mati", !state.videoTorch) { if (state.videoTorch) actions.onToggleTorch() }
                        OptionChip("Nyala", state.videoTorch) { if (!state.videoTorch) actions.onToggleTorch() }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Flash video memakai lampu senter kamera.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionChip("Auto", state.flash == FlashSetting.AUTO) { actions.onFlash(FlashSetting.AUTO) }
                        OptionChip("On", state.flash == FlashSetting.ON) { actions.onFlash(FlashSetting.ON) }
                        OptionChip("Off", state.flash == FlashSetting.OFF) { actions.onFlash(FlashSetting.OFF) }
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
                    OptionChip("Off", state.timerSec == 0) { actions.onTimer(0) }
                    OptionChip("3s", state.timerSec == 3) { actions.onTimer(3) }
                    OptionChip("5s", state.timerSec == 5) { actions.onTimer(5) }
                    OptionChip("10s", state.timerSec == 10) { actions.onTimer(10) }
                }
            }
            SheetKind.ASPECT -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionChip("4:3", state.aspect == PhotoAspect.RATIO_4_3) { actions.onAspect(PhotoAspect.RATIO_4_3) }
                    OptionChip("16:9", state.aspect == PhotoAspect.RATIO_16_9) { actions.onAspect(PhotoAspect.RATIO_16_9) }
                    OptionChip("1:1", state.aspect == PhotoAspect.SQUARE) { actions.onAspect(PhotoAspect.SQUARE) }
                }
            }
            else -> Unit
        }
    }
}

/** Top card: RESOLUTION (HD/4K) + FRAME RATE (24/30/60) from real device caps. */
@Composable
fun ResolutionCard(state: CameraState, actions: CameraActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(GlassPanel)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RESOLUTION", color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(104.dp)
            )
            state.videoResOptions.forEach { opt ->
                val selected = state.videoRes?.qualityName == opt.qualityName
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { actions.onVideoRes(opt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(IosYellow))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        opt.label,
                        color = if (selected) IosYellow else Color.White,
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
            state.caps.fpsOptions.forEach { f ->
                val selected = state.fps == f
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { actions.onFps(f) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(IosYellow))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        f.toString(),
                        color = if (selected) IosYellow else Color.White,
                        fontSize = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Arc zoom dial (iOS 26) / horizontal strip (iOS 18). Drag horizontally to zoom. */
@Composable
fun ZoomDial(
    state: CameraState,
    onZoom: (Float) -> Unit,
    onDone: () -> Unit,
    arc: Boolean,
    modifier: Modifier = Modifier
) {
    var startRatio by remember { mutableFloatStateOf(1f) }
    var acc by remember { mutableFloatStateOf(0f) }
    val gesture = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { startRatio = state.zoomRatio; acc = 0f },
            onDragEnd = { onDone() },
            onDragCancel = { onDone() },
            onDrag = { change, drag ->
                change.consume()
                acc += drag.x
                onZoom(startRatio * exp(acc / 240f))
            }
        )
    }
    if (arc) ArcDial(state, gesture.then(modifier)) else StripDial(state, gesture.then(modifier))
}
