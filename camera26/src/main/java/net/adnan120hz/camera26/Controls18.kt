package net.adnan120hz.camera26

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Controls18(state: CameraState, actions: CameraActions) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Solid black top bar (iOS 18).
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.mode == CamMode.VIDEO && !state.isRecording) {
                    Text(
                        "${state.videoRes?.label ?: "HD"} • ${state.fps}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { actions.onOpenSheet(SheetKind.RESOLUTION) }
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.nightOn && !state.facingFront) {
                        NightGlyph(IosYellow, Color.Black, Modifier.size(20.dp))
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
                    }
                    LiveGlyph(
                        Color.White.copy(alpha = 0.4f),
                        Modifier.size(20.dp).clickable { actions.onSegera("Live Photo") },
                        auto = false
                    )
                    StylesGlyph(
                        Color.White,
                        Modifier.size(20.dp).clickable { actions.onSegera("Photographic Styles") }
                    )
                    ChevronGlyph(
                        Color.White,
                        Modifier.size(20.dp).clickable { actions.onToggleTray() },
                        down = state.trayOpen
                    )
                }
            }

            AnimatedVisibility(
                visible = state.sheet == SheetKind.RESOLUTION,
                enter = slideInVertically(tween(200)) { -it } + fadeIn(),
                exit = slideOutVertically(tween(160)) { -it } + fadeOut()
            ) {
                ResolutionCard(
                    state, actions,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = state.trayOpen,
                    enter = slideInVertically(tween(200)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(160)) { it } + fadeOut()
                ) {
                    Tray18(state, actions)
                }

                AnimatedVisibility(
                    visible = state.sheet == SheetKind.FLASH || state.sheet == SheetKind.EXPOSURE ||
                        state.sheet == SheetKind.TIMER || state.sheet == SheetKind.ASPECT,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.82f))
                    ) {
                        SubPanelContent(state.sheet, state, actions)
                    }
                }

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.dialVisible) {
                        ZoomDial(state, actions.onZoomTo, onDone = {}, arc = false)
                    } else {
                        QuickZoomButtons(state, actions, Modifier.padding(vertical = 8.dp))
                    }
                }

                ModeRow18(state, actions)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThumbnailButton(state, actions, round = false)
                    ShutterButton(state, actions, glassRing = false)
                    FlipButton(actions)
                }
            }
        }
    }
}

@Composable
private fun ModeRow18(state: CameraState, actions: CameraActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CamMode.values().forEach { m ->
            val available = state.modeAvailable(m)
            val active = state.mode == m
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { actions.onModeSelect(m) }
                    .padding(horizontal = 9.dp)
            ) {
                Text(
                    m.label,
                    color = when {
                        active -> IosYellow
                        available -> Color.White.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.32f)
                    },
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
                if (!available) {
                    Text("SEGERA", color = IosYellow.copy(alpha = 0.8f), fontSize = 6.sp)
                }
            }
        }
    }
}

private data class TrayItem(
    val label: String,
    val active: Boolean,
    val segera: Boolean,
    val onClick: () -> Unit,
    val glyph: @Composable (Color) -> Unit
)

@Composable
private fun Tray18(state: CameraState, actions: CameraActions) {
    val aspectLabel = when (state.aspect) {
        PhotoAspect.RATIO_4_3 -> "4:3"
        PhotoAspect.RATIO_16_9 -> "16:9"
        PhotoAspect.SQUARE -> "1:1"
    }
    val items = mutableListOf<TrayItem>()
    items += TrayItem("FLASH",
        if (state.mode == CamMode.VIDEO) state.videoTorch else state.flash != FlashSetting.OFF,
        false,
        { if (state.mode == CamMode.VIDEO) actions.onToggleTorch() else actions.onOpenSheet(SheetKind.FLASH) }
    ) { c ->
        FlashGlyph(c, Modifier.size(24.dp), off = state.mode != CamMode.VIDEO && state.flash == FlashSetting.OFF, auto = state.mode != CamMode.VIDEO && state.flash == FlashSetting.AUTO)
    }
    if (state.nightExtAvailable) {
        items += TrayItem("NIGHT", state.nightOn, false, { actions.onToggleNight() }) { c ->
            NightGlyph(c, Color.Black, Modifier.size(24.dp))
        }
    }
    items += TrayItem("LIVE", false, true, { actions.onSegera("Live Photo") }) { c ->
        LiveGlyph(c, Modifier.size(24.dp), auto = true)
    }
    items += TrayItem("STYLES", false, true, { actions.onSegera("Photographic Styles") }) { c ->
        StylesGlyph(c, Modifier.size(24.dp))
    }
    items += TrayItem("ASPECT", false, false, { actions.onOpenSheet(SheetKind.ASPECT) }) { c ->
        AspectGlyph(c, aspectLabel, Modifier.size(24.dp))
    }
    items += TrayItem("EXPOSURE", state.exposureIndex != 0, false, { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c ->
        ExposureGlyph(c, Modifier.size(24.dp))
    }
    items += TrayItem("TIMER", state.timerSec > 0, false, { actions.onOpenSheet(SheetKind.TIMER) }) { c ->
        TimerGlyph(c, Modifier.size(24.dp))
    }
    items += TrayItem("FILTER", false, true, { actions.onSegera("Filter") }) { c ->
        FilterGlyph(c, Modifier.size(24.dp))
    }
    items += TrayItem("GRID", state.gridOn, false, { actions.onToggleGrid() }) { c ->
        GridGlyph(c, Modifier.size(24.dp))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable { item.onClick() }
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        item.glyph(if (item.active) IosYellow else Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp)
                    if (item.segera) {
                        Text("SEGERA", color = IosYellow, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // UI style switch lives at the end of the tray.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { actions.onUiStyle(UiStyle.IOS26) }
                    .padding(vertical = 2.dp)
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("26", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("KE iOS 26", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp)
            }
        }
    }
}
