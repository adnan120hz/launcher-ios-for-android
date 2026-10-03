package net.adnan120hz.camera26

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(GlassPillBrush)
            .border(1.dp, GlassRim, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun Controls26(state: CameraState, actions: CameraActions) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.mode == CamMode.VIDEO && !state.isRecording) {
                    GlassPill(Modifier.clickable { actions.onOpenSheet(SheetKind.RESOLUTION) }) {
                        Text(
                            state.videoRes?.label ?: "HD",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text(" RES", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            state.fps.toString(),
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text(" FPS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                GlassPill {
                    if (state.nightOn && !state.facingFront) {
                        NightGlyph(IosYellow, Color(0xFF4C4C50), Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    if (!state.facingFront) {
                        val flashColor = when {
                            state.mode == CamMode.VIDEO ->
                                if (state.videoTorch) IosYellow else Color.White
                            state.flash == FlashSetting.OFF -> Color.White
                            else -> IosYellow
                        }
                        Box(
                            Modifier
                                .size(22.dp)
                                .clickable {
                                    if (state.mode == CamMode.VIDEO) actions.onToggleTorch()
                                    else actions.onOpenSheet(SheetKind.FLASH)
                                }
                        ) {
                            FlashGlyph(
                                flashColor,
                                Modifier.size(20.dp),
                                off = state.mode != CamMode.VIDEO && state.flash == FlashSetting.OFF,
                                auto = state.mode != CamMode.VIDEO && state.flash == FlashSetting.AUTO
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    if (state.mode == CamMode.PHOTO) {
                        // Quick Styles access, like the iOS 26 top-right corner.
                        StylesGlyph(
                            Color.White,
                            Modifier
                                .size(20.dp)
                                .clickable { actions.onSegera("Photographic Styles") }
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    SixDotsGlyph(
                        Color.White,
                        Modifier
                            .size(20.dp)
                            .clickable { actions.onOpenSheet(SheetKind.GRID) }
                    )
                }
            }

            AnimatedVisibility(
                visible = state.sheet == SheetKind.RESOLUTION,
                enter = slideInVertically(SheetEnterSpec) { -it } + fadeIn(),
                exit = slideOutVertically(SheetExitSpec) { -it } + fadeOut()
            ) {
                ResolutionCard(
                    state, actions,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = state.modeStripVisible,
                enter = slideInVertically(SheetEnterSpec) { it / 3 } + fadeIn(),
                exit = fadeOut(tween(250))
            ) {
                ModeStrip26(state, actions)
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.dialVisible) {
                    ZoomDial(state, actions.onZoomTo, onDone = {}, arc = true)
                } else {
                    QuickZoomButtons(state, actions, Modifier.padding(vertical = 6.dp))
                }
            }

            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                ShutterButton(state, actions, glassRing = true)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThumbnailButton(state, actions, round = true)
                ModePill26(state, actions)
                FlipButton(actions)
            }
        }

        Sheet26(state, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ModeStrip26(state: CameraState, actions: CameraActions) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CamMode.values().forEach { m ->
            val available = state.modeAvailable(m)
            val active = state.mode == m
            Text(
                m.label,
                color = when {
                    active -> IosYellow
                    available -> Color.White.copy(alpha = 0.75f)
                    else -> Color.White.copy(alpha = 0.32f)
                },
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clickable { actions.onModeSelect(m) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ModePill26(state: CameraState, actions: CameraActions) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassPillBrush)
            .border(1.dp, GlassRim, RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        listOf(CamMode.VIDEO, CamMode.PHOTO).forEach { m ->
            val active = state.mode == m
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) Color.White.copy(alpha = 0.30f) else Color.Transparent
                    )
                    .clickable {
                        if (active) actions.onOpenSheet(SheetKind.GRID)
                        else actions.onModeSelect(m)
                    }
                    .padding(horizontal = 22.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    m.label,
                    color = if (active) IosYellow else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private data class SheetItem(
    val label: String,
    val active: Boolean,
    val segera: Boolean,
    val onClick: () -> Unit,
    val glyph: @Composable (Color) -> Unit
)

@Composable
private fun Sheet26(state: CameraState, actions: CameraActions, modifier: Modifier = Modifier) {
    val visible = state.sheet != SheetKind.NONE && state.sheet != SheetKind.RESOLUTION
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(SheetEnterSpec) { it } + fadeIn(),
        exit = slideOutVertically(SheetExitSpec) { it } + fadeOut()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(GlassPanelBrush)
                .border(1.dp, GlassRim, RoundedCornerShape(34.dp))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 42f) actions.onCloseSheet()
                    }
                }
                .padding(vertical = 14.dp)
        ) {
            when (state.sheet) {
                SheetKind.GRID -> SheetGrid26(state, actions)
                SheetKind.FLASH, SheetKind.EXPOSURE, SheetKind.TIMER, SheetKind.ASPECT -> {
                    Text(
                        "‹  Kontrol",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { actions.onOpenSheet(SheetKind.GRID) }
                            .padding(horizontal = 22.dp, vertical = 2.dp)
                    )
                    SubPanelContent(state.sheet, state, actions)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SheetGrid26(state: CameraState, actions: CameraActions) {
    val aspectLabel = when (state.aspect) {
        PhotoAspect.RATIO_4_3 -> "4:3"
        PhotoAspect.RATIO_16_9 -> "16:9"
        PhotoAspect.SQUARE -> "1:1"
    }
    val items = mutableListOf<SheetItem>()
    when (state.mode) {
        CamMode.PHOTO -> {
            items += SheetItem("FLASH", state.flash != FlashSetting.OFF, false,
                { actions.onOpenSheet(SheetKind.FLASH) }) { c ->
                FlashGlyph(c, Modifier.size(28.dp), off = state.flash == FlashSetting.OFF, auto = state.flash == FlashSetting.AUTO)
            }
            items += SheetItem("LIVE", false, true, { actions.onSegera("Live Photo") }) { c ->
                LiveGlyph(c, Modifier.size(28.dp), auto = true)
            }
            items += SheetItem("TIMER", state.timerSec > 0, false,
                { actions.onOpenSheet(SheetKind.TIMER) }) { c -> TimerGlyph(c, Modifier.size(28.dp)) }
            items += SheetItem("EXPOSURE", state.exposureIndex != 0, false,
                { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c -> ExposureGlyph(c, Modifier.size(28.dp)) }
            items += SheetItem("STYLES", false, true, { actions.onSegera("Photographic Styles") }) { c ->
                StylesGlyph(c, Modifier.size(28.dp))
            }
            items += SheetItem("FILTER", false, true, { actions.onSegera("Filter") }) { c ->
                FilterGlyph(c, Modifier.size(28.dp))
            }
            items += SheetItem("ASPECT", false, false,
                { actions.onOpenSheet(SheetKind.ASPECT) }) { c -> AspectGlyph(c, aspectLabel, Modifier.size(28.dp)) }
            if (state.nightExtAvailable) {
                items += SheetItem("NIGHT MODE", state.nightOn, false,
                    { actions.onToggleNight() }) { c -> NightGlyph(c, Color(0xFF4C4C50), Modifier.size(28.dp)) }
            }
        }
        CamMode.VIDEO -> {
            items += SheetItem("FLASH", state.videoTorch, false,
                { actions.onToggleTorch() }) { c -> FlashGlyph(c, Modifier.size(28.dp)) }
            items += SheetItem("EXPOSURE", state.exposureIndex != 0, false,
                { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c -> ExposureGlyph(c, Modifier.size(28.dp)) }
            items += SheetItem("ACTION", false, true, { actions.onSegera("Action Mode") }) { c ->
                RunnerGlyph(c, Modifier.size(28.dp), off = true)
            }
        }
        CamMode.PORTRAIT -> {
            items += SheetItem("FLASH", state.flash != FlashSetting.OFF, false,
                { actions.onOpenSheet(SheetKind.FLASH) }) { c ->
                FlashGlyph(c, Modifier.size(28.dp), off = state.flash == FlashSetting.OFF, auto = state.flash == FlashSetting.AUTO)
            }
            items += SheetItem("APERTURE", false, true, { actions.onSegera("Aperture") }) { c ->
                ApertureGlyph(c, Modifier.size(28.dp))
            }
            items += SheetItem("EXPOSURE", state.exposureIndex != 0, false,
                { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c -> ExposureGlyph(c, Modifier.size(28.dp)) }
        }
        else -> Unit
    }

    items.chunked(3).forEach { rowItems ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            rowItems.forEach { item ->
                ControlButton(item.label, item.active, item.segera, item.onClick, item.glyph)
            }
            repeat(3 - rowItems.size) { Spacer(Modifier.width(86.dp)) }
        }
        Spacer(Modifier.height(8.dp))
    }

    // Footer: UI style switch + grid toggle.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.10f))
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tampilan", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            OptionChip("iOS 26", state.uiStyle == UiStyle.IOS26) { actions.onUiStyle(UiStyle.IOS26) }
            Spacer(Modifier.width(8.dp))
            OptionChip("iOS 18", state.uiStyle == UiStyle.IOS18) { actions.onUiStyle(UiStyle.IOS18) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { actions.onToggleGrid() }
        ) {
            GridGlyph(if (state.gridOn) IosYellow else Color.White, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Grid",
                color = if (state.gridOn) IosYellow else Color.White,
                fontSize = 12.sp
            )
        }
    }
}
