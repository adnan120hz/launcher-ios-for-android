package net.adnan120hz.camera26

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // BUG FIX: the way back to the iOS 26 UI used to exist ONLY
                    // as the last item of the horizontally scrolling tray, so
                    // users got stuck on iOS 18. This switch is always visible.
                    UiSwitchPill18 { actions.onUiStyle(UiStyle.IOS26) }
                    if (state.mode == CamMode.VIDEO && !state.isRecording) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${state.videoRes?.label ?: "HD"} • ${state.fps}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { actions.onOpenSheet(SheetKind.RESOLUTION) }
                        )
                    }
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
                            state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO ->
                                if (state.videoTorch) IosYellow else Color.White
                            state.flash == FlashSetting.OFF -> Color.White
                            else -> IosYellow
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clickable {
                                    if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) actions.onToggleTorch()
                                    else actions.onOpenSheet(SheetKind.FLASH)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            FlashGlyph(
                                flashColor,
                                Modifier.size(20.dp),
                                off = state.mode != CamMode.VIDEO && state.mode != CamMode.SLO_MO && state.flash == FlashSetting.OFF,
                                auto = state.mode != CamMode.VIDEO && state.mode != CamMode.SLO_MO && state.flash == FlashSetting.AUTO
                            )
                        }
                    }
                    StylesGlyph(
                        if (state.styleId != null || state.filterId != null) IosYellow else Color.White,
                        Modifier.size(20.dp).clickable { actions.onOpenSheet(SheetKind.STYLES) }
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .clickable { actions.onToggleTray() },
                        contentAlignment = Alignment.Center
                    ) {
                        ChevronGlyph(Color.White, Modifier.size(20.dp), down = state.trayOpen)
                    }
                }
            }

            AnimatedVisibility(
                visible = state.sheet == SheetKind.RESOLUTION,
                enter = slideInVertically(SheetEnterSpec) { -it } + fadeIn(),
                exit = slideOutVertically(SheetExitSpec) { -it } + fadeOut()
            ) {
                ResolutionCard(
                    state, actions,
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    flat = true // iOS 18: flat black card, no Liquid Glass
                )
            }

            Spacer(Modifier.weight(1f))

            // iOS 18 bottom control bar: FLAT black (no Liquid Glass anywhere).
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                AnimatedVisibility(
                    visible = state.trayOpen,
                    enter = slideInVertically(SheetEnterSpec) { it } + fadeIn(),
                    exit = slideOutVertically(SheetExitSpec) { it } + fadeOut()
                ) {
                    Tray18(state, actions)
                }

                AnimatedVisibility(
                    visible = state.sheet == SheetKind.FLASH || state.sheet == SheetKind.EXPOSURE ||
                        state.sheet == SheetKind.TIMER || state.sheet == SheetKind.ASPECT ||
                        state.sheet == SheetKind.FILTER || state.sheet == SheetKind.STYLES ||
                        state.sheet == SheetKind.APERTURE,
                    enter = slideInVertically(SheetEnterSpec) { it / 2 } + fadeIn(),
                    exit = slideOutVertically(SheetExitSpec) { it / 2 } + fadeOut()
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
        // Only modes that genuinely work on this device are listed.
        CamMode.values().filter { state.modeAvailable(it) }.forEach { m ->
            val active = state.mode == m
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { actions.onModeSelect(m) }
                    .padding(horizontal = 9.dp)
            ) {
                Text(
                    m.label,
                    color = if (active) IosYellow else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private data class TrayItem(
    val label: String,
    val active: Boolean,
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
    // Capability-based tray (iOS 18 skin): only controls that really work on
    // this device are present — no dead items.
    items += TrayItem("FLASH",
        if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) state.videoTorch else state.flash != FlashSetting.OFF,
        { if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) actions.onToggleTorch() else actions.onOpenSheet(SheetKind.FLASH) }
    ) { c ->
        FlashGlyph(c, Modifier.size(24.dp), off = state.mode != CamMode.VIDEO && state.mode != CamMode.SLO_MO && state.flash == FlashSetting.OFF, auto = state.mode != CamMode.VIDEO && state.mode != CamMode.SLO_MO && state.flash == FlashSetting.AUTO)
    }
    if (state.nightExtAvailable) {
        items += TrayItem("NIGHT", state.nightOn, { actions.onToggleNight() }) { c ->
            NightGlyph(c, Color.Black, Modifier.size(24.dp))
        }
    }
    items += TrayItem("STYLES", state.styleId != null, { actions.onOpenSheet(SheetKind.STYLES) }) { c ->
        StylesGlyph(c, Modifier.size(24.dp))
    }
    items += TrayItem("ASPECT", false, { actions.onOpenSheet(SheetKind.ASPECT) }) { c ->
        AspectGlyph(c, aspectLabel, Modifier.size(24.dp))
    }
    items += TrayItem("EXPOSURE", state.exposureIndex != 0, { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c ->
        ExposureGlyph(c, Modifier.size(24.dp))
    }
    if (state.mode != CamMode.VIDEO && state.mode != CamMode.SLO_MO) {
        items += TrayItem("TIMER", state.timerSec > 0, { actions.onOpenSheet(SheetKind.TIMER) }) { c ->
            TimerGlyph(c, Modifier.size(24.dp))
        }
    }
    if (state.mode == CamMode.PORTRAIT &&
        state.extensionMode != androidx.camera.extensions.ExtensionMode.BOKEH
    ) {
        items += TrayItem("APERTURE", true, { actions.onOpenSheet(SheetKind.APERTURE) }) { c ->
            ApertureGlyph(c, Modifier.size(24.dp))
        }
    }
    if (state.mode == CamMode.PHOTO) {
        items += TrayItem("FILTER", state.filterId != null, { actions.onOpenSheet(SheetKind.FILTER) }) { c ->
            FilterGlyph(c, Modifier.size(24.dp))
        }
    }
    items += TrayItem("GRID", state.gridOn, { actions.onToggleGrid() }) { c ->
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
            // UI style switch is FIRST in the tray (it used to be the last
            // item, effectively unreachable -> users got stuck on iOS 18).
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
                        .background(Color.White.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("26", color = IosYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text("KE iOS 26", color = IosYellow.copy(alpha = 0.9f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
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
                }
            }
        }
    }
}

/** Always-visible flat pill in the iOS 18 top bar: one tap back to iOS 26 UI. */
@Composable
private fun UiSwitchPill18(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF2C2C2E))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("iOS 26", color = IosYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text("UI ›", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
    }
}
