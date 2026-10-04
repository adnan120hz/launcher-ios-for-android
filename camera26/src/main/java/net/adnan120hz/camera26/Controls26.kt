package net.adnan120hz.camera26

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Content height (dp) of the solid-black control strip at the bottom
 * (shutter row + mode row + margins, excluding the navigation-bar inset).
 * The live preview always ends at the top of this strip; CameraScreen
 * reserves exactly this much room so shutter and pills sit on black.
 */
internal const val BOTTOM_STRIP_HEIGHT_DP = 166f

/** Extra strip height while the PORTRAIT lighting wheel rides the strip. */
internal const val PORTRAIT_BAR_HEIGHT_DP = 80f

/**
 * Real height (dp, excluding nav inset) of the bottom control strip for
 * the current state. The preview region must be computed from THIS (not
 * the bare constant) or the preview creeps up behind the top pill band
 * whenever the strip grows (Portrait wheel) — the missing top black band.
 */
internal fun bottomStripHeightDp(state: CameraState): Float =
    BOTTOM_STRIP_HEIGHT_DP + if (
        state.mode == CamMode.PORTRAIT && state.sheet == SheetKind.NONE
    ) PORTRAIT_BAR_HEIGHT_DP else 0f

/** Height of the top pills (40dp touch boxes + 1dp glass padding ×2). */
private val TOP_PILL_HEIGHT_DP = 42.dp

@Composable
private fun GlassPill(
    modifier: Modifier = Modifier,
    hPad: androidx.compose.ui.unit.Dp = 14.dp,
    vPad: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(GlassPillBrush)
            .border(1.dp, GlassRim, RoundedCornerShape(50))
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun Controls26(state: CameraState, actions: CameraActions) {
    val inspection = LocalInspectionMode.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Preview region: the top bar floats over the preview, and
            // the zoom row rides the preview's bottom edge (iOS-style).
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                // The top pills live fully inside the black band between
                // the status bar and the preview's top edge, vertically
                // CENTRED in that band: never riding on the preview image
                // (the fix4 placement sat too low) and never floating up
                // against the status bar (fix3). When a photo-family aspect
                // letterboxes the preview, the image starts lower in the
                // region and the band grows; the pills stay centred in it.
                // Full-bleed modes have no black band (imageTop == 0) and
                // keep the classic top inset over the preview.
                val letterboxedMode = state.mode == CamMode.PHOTO ||
                    state.mode == CamMode.PORTRAIT || state.mode == CamMode.TIME_LAPSE
                val previewArea = previewAreaDp(
                    aspect = state.aspect,
                    filled = state.previewFilled,
                    letterboxedMode = letterboxedMode,
                    screenW = maxWidth.value,
                    screenH = maxHeight.value
                )
                val imageTopDp = (maxHeight.value - previewArea.second).coerceAtLeast(0f).dp
                val density = LocalDensity.current
                val statusTopDp = with(density) {
                    WindowInsets.statusBars.getTop(density).toDp()
                }
                // Band = status-bar bottom .. preview top edge. Centre the
                // pill in it. The Row below already applies
                // statusBarsPadding() + 8dp, so only the surplus over that
                // classic position is added (never negative).
                val bandHDp = (imageTopDp - statusTopDp).coerceAtLeast(0.dp)
                val pillCentredTopDp = statusTopDp + (bandHDp - TOP_PILL_HEIGHT_DP) / 2f
                val pillExtraTop =
                    (pillCentredTopDp - statusTopDp - 8.dp).coerceAtLeast(0.dp)
                Column {
                    // ---------------------------------------------- top bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp + pillExtraTop, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Format pill for the video family: "HD RES  30 FPS"
                        // (SLO-MO shows its real frame rate, e.g. 240 FPS).
                        val videoFamily = state.mode == CamMode.VIDEO ||
                            state.mode == CamMode.SLO_MO || state.mode == CamMode.TIME_LAPSE
                        Column {
                            if (videoFamily && !state.isRecording && !state.timelapseRunning) {
                                GlassPill(
                                    Modifier
                                        .height(40.dp)
                                        .clickable { actions.onOpenSheet(SheetKind.RESOLUTION) }
                                ) {
                                    Text(
                                        state.videoRes?.label ?: "HD",
                                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(" RES", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                    Spacer(Modifier.width(9.dp))
                                    val fpsText = when (state.mode) {
                                        CamMode.SLO_MO ->
                                            if (state.caps.sloMoFps > 0) state.caps.sloMoFps else state.fps
                                        CamMode.TIME_LAPSE -> 30
                                        else -> state.fps
                                    }
                                    Text(
                                        fpsText.toString(),
                                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(" FPS", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            // Software EIS status: visible while the GL pipeline
                            // is live; names the motion source honestly and
                            // flags the 1080p cap when 4K is picked.
                            if (state.eisActive) {
                                Spacer(Modifier.height(6.dp))
                                GlassPill {
                                    RunnerGlyph(IosYellow, Modifier.size(14.dp), off = false)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        when {
                                            state.eisCapped -> "EIS · maks 1080p"
                                            state.caps.gyroAvailable -> "EIS · Gyroscope"
                                            else -> "EIS · Sensor gerak"
                                        },
                                        color = IosYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            // Contextual top-right pill, exactly like the iOS 26
                            // reference video: PHOTO carries moon (Night) ·
                            // flash · Styles · grid in ONE pill (front camera:
                            // moon · flash · Live · grid); the video family
                            // carries flash · grid. Active icons glow yellow.
                            // Icons sit in 40dp touch boxes (visual glyph and
                            // pill outline unchanged) — no more precision taps.
                            GlassPill(hPad = 6.dp, vPad = 1.dp) {
                                val videoFamilyPill = state.mode == CamMode.VIDEO ||
                                    state.mode == CamMode.SLO_MO || state.mode == CamMode.TIME_LAPSE
                                if (!videoFamilyPill && state.nightExtAvailable) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clickable {
                                                val wasOn = state.nightOn
                                                actions.onToggleNight()
                                                state.showBanner(
                                                    if (wasOn) "NIGHT MODE OFF" else "NIGHT MODE ON"
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        NightGlyph(
                                            if (state.nightOn) IosYellow else Color.White,
                                            Color(0xFF4C4C50),
                                            Modifier.size(19.dp)
                                        )
                                    }
                                }
                                run {
                                    val flashColor = when {
                                        state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO ->
                                            if (state.videoTorch) IosYellow else Color.White
                                        state.flash == FlashSetting.OFF -> Color.White
                                        else -> IosYellow
                                    }
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clickable {
                                                if (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) {
                                                    val wasOn = state.videoTorch
                                                    actions.onToggleTorch()
                                                    state.showBanner(
                                                        if (wasOn) "FLASH OFF" else "FLASH ON"
                                                    )
                                                } else {
                                                    actions.onOpenSheet(SheetKind.FLASH)
                                                }
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
                                if (!videoFamilyPill) {
                                    if (state.facingFront) {
                                        // Live Photo has no real implementation on
                                        // this platform: shown like the reference,
                                        // honestly dimmed and unusable.
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clickable {
                                                    state.toast = "Live Photo belum tersedia"
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LiveGlyph(
                                                Color.White.copy(alpha = 0.35f),
                                                Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clickable {
                                                    actions.onOpenSheet(SheetKind.STYLES)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            StylesGlyph(
                                                if (state.styleId != null) IosYellow else Color.White,
                                                Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clickable { actions.onOpenSheet(SheetKind.GRID) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    SixDotsGlyph(Color.White, Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = state.sheet == SheetKind.RESOLUTION,
                        enter = if (inspection) EnterTransition.None
                        else slideInVertically(SheetEnterSpec) { -it } + fadeIn(),
                        exit = if (inspection) ExitTransition.None
                        else slideOutVertically(SheetExitSpec) { -it } + fadeOut()
                    ) {
                        ResolutionCard(
                            state, actions,
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                }

                // Zoom stops / arc dial ride the preview's bottom edge.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.dialVisible) {
                        ZoomDial(state, actions.onZoomTo, onDone = {})
                    } else {
                        QuickZoomButtons(
                            state, actions,
                            Modifier.padding(vertical = 6.dp)
                        )
                    }
                    // Expand (⤢): front camera only (user rule) — never on
                    // the back camera's zoom row. Flips the letterboxed
                    // preview to full-bleed; capture is unaffected.
                    if (state.facingFront && !state.dialVisible) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 18.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GlassPillBrush)
                                .border(1.dp, GlassRim, CircleShape)
                                .clickable { state.previewFilled = !state.previewFilled },
                            contentAlignment = Alignment.Center
                        ) {
                            ExpandGlyph(Color.White, Modifier.size(19.dp))
                        }
                    }
                }
                // PANO: the iOS-style sweep guide rides the preview centre.
                if (state.mode == CamMode.PANO) {
                    PanoGuide(state, actions)
                }
            }

            // Solid-black control strip: shutter + mode row sit on black
            // in every mode; the preview ends above this strip.
            // navigationBarsPadding + margin keep it above the nav bar.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                // PORTRAIT: the lighting wheel (curved dot arc, cube in the
                // centre, yellow effect label) rides above the shutter,
                // replacing the flat in-sheet chip.
                if (state.mode == CamMode.PORTRAIT && state.sheet == SheetKind.NONE) {
                    PortraitLightBar(state)
                }
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterButton(
                        state, actions,
                        dottedRing = state.mode == CamMode.TIME_LAPSE && !state.timelapseRunning
                    )
                }

                // Fixed 18dp side margins, fixed 46dp ends, pill takes the
                // middle space that remains — nothing can clip the screen
                // edge or stack onto the pill on narrow devices.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThumbnailButton(state, actions)
                    Box(
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ModeCarouselPill(state, actions)
                    }
                    FlipButton(actions)
                }
            }
        }

        // iOS-style transient status banner at the top centre ("FLASH ON",
        // "NIGHT MODE OFF", "LIVE OFF" …): yellow pill for on-states, white
        // otherwise; springs in and auto-dismisses (CameraScreen timer).
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 54.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = state.bannerText != null,
                enter = if (inspection) EnterTransition.None
                else slideInVertically(
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                ) { -it } + fadeIn(),
                exit = if (inspection) ExitTransition.None
                else slideOutVertically { -it / 2 } + fadeOut()
            ) {
                val t = state.bannerText ?: ""
                val yellow = t.contains("ON") || t.contains("AUTO")
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background((if (yellow) IosYellow else Color.White).copy(alpha = 0.96f))
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(t, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Sheet26(state, actions, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * PANO sweep guide (iOS-style): a centre track with a progress fill, a
 * travelling frame window carrying the direction chevron, captured-frame
 * ticks, a hint pill and — while sweeping — a ✕ cancel button. Idle text
 * tells the user to tap the shutter and pan slowly; during the sweep it
 * warns (yellow) when the pan is too fast for clean stitching.
 */
@Composable
private fun PanoGuide(state: CameraState, actions: CameraActions) {
    val hint = when {
        state.panoStitching -> "Menyimpan panorama…"
        state.panoSweeping && state.panoTooFast -> "Terlalu cepat — geser lebih pelan"
        state.panoSweeping -> "Tetap geser perlahan…"
        else -> "Ketuk tombol rana, lalu geser HP perlahan"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
                val progress = state.panoProgress.coerceIn(0f, 1f)
                val progressW = maxWidth * progress
                // Track.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
                // Progress fill.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(progressW)
                        .height(2.dp)
                        .background(
                            if (state.panoTooFast) IosYellow else Color.White
                        )
                )
                // Captured-frame ticks along the completed stretch.
                for (i in 0 until state.panoFrameCount) {
                    val frac = (i * PANO_STEP_DEG / PANO_TARGET_DEG).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = maxWidth * frac - 2.dp, y = 7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.75f))
                    )
                }
                // Travelling frame window with the direction chevron.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = progressW - 15.dp)
                        .size(width = 30.dp, height = 20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.5.dp, Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(width = 12.dp, height = 10.dp)) {
                        val dir = if (state.panoDirRight) 1f else -1f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val armX = size.width * 0.28f
                        val armY = size.height * 0.34f
                        drawLine(
                            Color.White,
                            Offset(cx - dir * armX, cy - armY),
                            Offset(cx + dir * armX, cy),
                            strokeWidth = 2.2f
                        )
                        drawLine(
                            Color.White,
                            Offset(cx + dir * armX, cy),
                            Offset(cx - dir * armX, cy + armY),
                            strokeWidth = 2.2f
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    hint,
                    color = if (state.panoSweeping && state.panoTooFast) IosYellow else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (state.panoSweeping) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { actions.onPanoCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * The iOS 26 mode picker: a wide glass pill below the shutter that IS the
 * carousel. The selected mode sits in the bright capsule at the centre
 * (yellow label); neighbouring mode labels peek from inside the same pill.
 * Drag horizontally on the pill to rotate modes; tap a neighbour to jump.
 * Modes this device cannot really run are dimmed and cannot be selected.
 */
/**
 * fix8 — the ONE carousel settle rule, shared by the pill drag below and
 * the preview swipe (CameraScreen.swipeMode): the index exactly one hop
 * from [from] in [dir] that names an AVAILABLE mode (unavailable modes
 * are skipped, never landed on), or [from] itself when no available mode
 * lies that way. A released gesture can therefore never overshoot by a
 * mode, and both gesture paths always agree.
 */
internal fun nextAvailableModeIndex(
    modes: List<CamMode>,
    from: Int,
    dir: Int,
    available: (CamMode) -> Boolean
): Int {
    var i = from + dir
    while (i in modes.indices) {
        if (available(modes[i])) return i
        i += dir
    }
    return from
}

/** The single spring every carousel settle uses (base slide + drag return). */
private val CarouselSettleSpec: SpringSpec<Float> =
    spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)

@Composable
private fun ModeCarouselPill(state: CameraState, actions: CameraActions) {
    val modes = remember { CamMode.values().toList() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val gapPx = with(density) { 22.dp.toPx() }
    // The pill takes whatever middle width the bottom row leaves (capped),
    // so its geometry comes from the real constraints, not a fixed width.
    val pillMaxWidthDp = 250.dp

    val textWidthsPx = remember(textMeasurer) {
        modes.map { m ->
            textMeasurer.measure(m.label, labelStyle).size.width.toFloat()
        }
    }
    // Centre x of each label inside the (untranslated) label strip. Each
    // label sits centred in a box of (text width + gap), so the visual
    // centre is half a box in — measuring to the text edge would skew
    // every item half a gap from the capsule.
    val centersPx = remember(textWidthsPx) {
        val out = ArrayList<Float>(modes.size)
        var x = 0f
        modes.indices.forEach { i ->
            out += x + (textWidthsPx[i] + gapPx) / 2f
            x += textWidthsPx[i] + gapPx
        }
        out
    }
    val selectedIdx = modes.indexOf(state.mode).coerceAtLeast(0)
    val baseTarget = -centersPx[selectedIdx]
    val baseAnim by animateFloatAsState(
        targetValue = baseTarget,
        animationSpec = CarouselSettleSpec,
        label = "carouselBase"
    )

    fun settleDrag() {
        val drag = state.carouselDragPx
        // fix8: settle by the shared one-hop rule (nextAvailableModeIndex,
        // also used by the preview swipe). The old pitch-count math let a
        // firm flick jump TWO modes (beta device: PORTRAIT -> PHOTO landed
        // on VIDEO), and around unavailable modes its walk-back branch
        // moved the base target against the drag spring — the SLO-MO /
        // TIME-LAPSE "mental-mental".
        val pitchPx = if (centersPx.size > 1) {
            (centersPx.last() - centersPx.first()) / (centersPx.size - 1)
        } else {
            1f
        }
        val steps = (-drag / pitchPx).roundToInt()
        if (steps != 0) {
            val dir = if (steps > 0) 1 else -1
            val target = nextAvailableModeIndex(modes, selectedIdx, dir) {
                state.modeAvailable(it)
            }
            if (target != selectedIdx) actions.onModeSelect(modes[target])
        }
        // One settle spring (same spec as the base slide) returns the drag
        // offset to rest — no second animation fighting the landing.
        scope.launch {
            Animatable(state.carouselDragPx).animateTo(
                0f, CarouselSettleSpec
            ) { state.carouselDragPx = this.value }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Press bookkeeping for the jelly bubble: tap-press and drag each
        // hold a share; the bubble lives while any share is held.
        var pressCount by remember { mutableStateOf(0) }
        LaunchedEffect(pressCount) { state.carouselPressed = pressCount > 0 }
        // The pill widens as the drag travels farther (iOS shows more of the
        // mode list mid-swipe), springing back when the finger lifts.
        val dragExtraDp = with(density) { abs(state.carouselDragPx).toDp() } * 0.55f
        val targetWidthDp = minOf(maxWidth, pillMaxWidthDp + minOf(dragExtraDp, 64.dp))
        val pillWidthDp by animateDpAsState(
            targetValue = targetWidthDp,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow),
            label = "carouselWidth"
        )
        val pillHalfPx = with(density) { (pillWidthDp / 2).toPx() }
        val pillWidthPx = with(density) { pillWidthDp.toPx() }
        val fadePx = with(density) { 24.dp.toPx() }
        val translation = pillHalfPx + baseAnim + state.carouselDragPx
        Box(
            Modifier
                .width(pillWidthDp)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(GlassPillBrush)
                .border(1.dp, GlassRim, RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressCount++
                            tryAwaitRelease()
                            pressCount = (pressCount - 1).coerceAtLeast(0)
                        },
                        onTap = { pos ->
                            var best = -1
                            var bestDist = Float.MAX_VALUE
                            modes.indices.forEach { i ->
                                val d = abs(pos.x - (translation + centersPx[i]))
                                if (d < bestDist) {
                                    bestDist = d
                                    best = i
                                }
                            }
                            if (best >= 0) {
                                val m = modes[best]
                                when {
                                    best == selectedIdx -> actions.onOpenSheet(SheetKind.GRID)
                                    !state.modeAvailable(m) -> {
                                        // CINEMATIC explains itself ONCE per
                                        // session; after that the dimmed
                                        // label stays silent when tapped.
                                        if (m == CamMode.CINEMATIC) {
                                            if (!state.cinematicNoticeShown) {
                                                state.cinematicNoticeShown = true
                                                state.toast =
                                                    "Mode CINEMATIC hanya tersedia di perangkat yang mendukungnya"
                                            }
                                        } else {
                                            state.toast = "Mode ${m.label} tidak didukung di perangkat ini"
                                        }
                                    }
                                    else -> actions.onModeSelect(m)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { pressCount++ },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            state.carouselDragPx += amount
                        },
                        onDragEnd = {
                            pressCount = (pressCount - 1).coerceAtLeast(0)
                            settleDrag()
                        },
                        onDragCancel = {
                            pressCount = (pressCount - 1).coerceAtLeast(0)
                            settleDrag()
                        }
                    )
                }
        ) {
            // Bright capsule fixed behind the selected label. Untouched, it
            // is exactly the plain capsule; while the pill is pressed or
            // dragged it swells into the iOS 26 Liquid Glass jelly bubble:
            // both axes scale up (squash-stretch follows drag travel), the
            // frost gradient, specular highlight and bright rim fade in
            // with the swell, and on release a low-damping spring lets it
            // wobble (2-3 small bounces) back to normal while the strip
            // snaps. Pill width and carousel geometry never change.
            val dragAbs = abs(state.carouselDragPx)
            val bulgeXTarget = if (state.carouselPressed) {
                1.10f + minOf(dragAbs / 1400f, 0.05f)
            } else 1f
            val bulgeYTarget = if (state.carouselPressed) {
                1.08f + minOf(dragAbs / 2200f, 0.02f)
            } else 1f
            val bubbleSpec: SpringSpec<Float> = if (state.carouselPressed) {
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
            } else {
                spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessMediumLow)
            }
            val bulgeX by animateFloatAsState(bulgeXTarget, bubbleSpec, label = "bubbleX")
            val bulgeY by animateFloatAsState(bulgeYTarget, bubbleSpec, label = "bubbleY")
            val bubbleProgress = ((bulgeX - 1f) / 0.15f).coerceIn(0f, 1f)
            // Entry-tier GPUs skip the extra glass layers entirely (the
            // swell itself stays); mid/flagship render the full glass.
            val fullGlass = state.caps.perfTier != PerfTier.ENTRY
            val selWidthDp = with(density) { textWidthsPx[selectedIdx].toDp() } + 30.dp
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(selWidthDp)
                    .height(36.dp)
                    .graphicsLayer {
                        scaleX = bulgeX
                        scaleY = bulgeY
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.30f))
            ) {
                // Liquid Glass layers — alpha follows the swell, so at rest
                // the capsule is pixel-identical to the plain one: frost
                // gradient, specular highlight across the top, bright rim.
                if (fullGlass) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = bubbleProgress }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.38f),
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.22f)
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .graphicsLayer { alpha = bubbleProgress }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        Color.White.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.48f * bubbleProgress),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
            // Sliding label strip (clipped by the pill itself). Unbounded width:
            // the strip is wider than the pill, and a width-capped Row would
            // starve the labels past the pill's edge of any layout space.
            Row(
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
                    .offset { IntOffset(translation.roundToInt(), 0) }
                    // The edge fade must dissolve ONLY the label strip. With
                    // an offscreen compositing layer, the DstIn mask applies
                    // to this Row alone — without it, it punches through the
                    // pill glass (and everything beneath) at the pill edges.
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        // iOS-style edge fade, aligned to the pill's window
                        // (strip coordinates): labels peeking at the pill's
                        // ends dissolve instead of being hard-clipped.
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                (fadePx / pillWidthPx) to Color.Black,
                                (1f - fadePx / pillWidthPx) to Color.Black,
                                1f to Color.Transparent,
                                startX = -translation,
                                endX = -translation + pillWidthPx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                modes.forEachIndexed { i, m ->
                    val available = state.modeAvailable(m)
                    val active = i == selectedIdx
                    Box(
                        Modifier
                            .width(with(density) { (textWidthsPx[i] + gapPx).toDp() })
                            .graphicsLayer {
                                if (active) {
                                    // Faint glass distortion of the selected
                                    // label inside the swollen bubble (zero
                                    // at rest, follows the swell).
                                    scaleX = 1f + (bulgeX - 1f) * 0.35f
                                    scaleY = 1f - (bulgeY - 1f) * 0.25f
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            m.label,
                            color = when {
                                active -> IosYellow
                                !available -> Color.White.copy(alpha = 0.32f)
                                else -> Color.White.copy(alpha = 0.82f)
                            },
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class SheetItem(
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val glyph: @Composable (Color) -> Unit
)

@Composable
private fun Sheet26(state: CameraState, actions: CameraActions, modifier: Modifier = Modifier) {
    val inspection = LocalInspectionMode.current
    val visible = state.sheet != SheetKind.NONE && state.sheet != SheetKind.RESOLUTION
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (inspection) EnterTransition.None
        else slideInVertically(SheetEnterSpec) { it } + fadeIn(),
        exit = if (inspection) ExitTransition.None
        else slideOutVertically(SheetExitSpec) { it } + fadeOut()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(GlassPanelBrush)
                .border(1.dp, GlassRim, RoundedCornerShape(34.dp))
                .pointerInput(Unit) {
                    // Swipe-down-to-close must accumulate: a single move
                    // event's delta is only a few px, so the old
                    // per-event threshold almost never fired.
                    var dragTotal = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = { dragTotal = 0f },
                        onDragCancel = { dragTotal = 0f }
                    ) { _, dragAmount ->
                        dragTotal += dragAmount
                        if (dragTotal > 90f) {
                            dragTotal = 0f
                            actions.onCloseSheet()
                        }
                    }
                }
                .padding(vertical = 14.dp)
        ) {
            // Tapping a grid item shrinks the card into its sub-panel with
            // the same spring language; STYLES / FILTER / EXPOSURE get the
            // iOS adjustment bar (✕ + tick scales / swatches).
            AnimatedContent(
                targetState = state.sheet,
                transitionSpec = {
                    (slideInVertically(SheetEnterSpec) { it / 3 } + fadeIn()) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(SheetExitSpec) { it / 3 })
                },
                label = "sheetContent"
            ) { kind ->
                when (kind) {
                    SheetKind.GRID -> SheetGrid26(state, actions)
                    SheetKind.STYLES, SheetKind.FILTER, SheetKind.EXPOSURE ->
                        IosAdjustBar(kind, state, actions)
                    SheetKind.FLASH, SheetKind.TIMER, SheetKind.ASPECT,
                    SheetKind.APERTURE, SheetKind.ACTION, SheetKind.CONFIG -> {
                        Column {
                            Text(
                                "‹  Kontrol",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clickable { actions.onOpenSheet(SheetKind.GRID) }
                                    .padding(horizontal = 22.dp, vertical = 2.dp)
                            )
                            SubPanelContent(kind, state, actions)
                        }
                    }
                    else -> Unit
                }
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
    val flashItem = SheetItem("FLASH", state.flash != FlashSetting.OFF,
        { actions.onOpenSheet(SheetKind.FLASH) }) { c ->
        FlashGlyph(c, Modifier.size(28.dp), off = state.flash == FlashSetting.OFF, auto = state.flash == FlashSetting.AUTO)
    }
    val exposureItem = SheetItem("EXPOSURE", state.exposureIndex != 0,
        { actions.onOpenSheet(SheetKind.EXPOSURE) }) { c -> ExposureGlyph(c, Modifier.size(28.dp)) }
    when (state.mode) {
        CamMode.PHOTO -> {
            items += flashItem
            // LIVE has no real implementation on this platform yet: visible
            // like the reference sheet, but honestly dimmed and unusable.
            items += SheetItem("LIVE", false,
                { state.toast = "Live Photo belum tersedia" },
                enabled = false) { c -> LiveGlyph(c, Modifier.size(28.dp)) }
            items += SheetItem("TIMER", state.timerSec > 0,
                { actions.onOpenSheet(SheetKind.TIMER) }) { c -> TimerGlyph(c, Modifier.size(28.dp)) }
            items += exposureItem
            items += SheetItem("STYLES", state.styleId != null,
                { actions.onOpenSheet(SheetKind.STYLES) }) { c ->
                StylesGlyph(c, Modifier.size(28.dp))
            }
            items += SheetItem("FILTER", state.filterId != null,
                { actions.onOpenSheet(SheetKind.FILTER) }) { c ->
                FilterGlyph(c, Modifier.size(28.dp))
            }
            items += SheetItem("ASPECT", false,
                { actions.onOpenSheet(SheetKind.ASPECT) }) { c -> AspectGlyph(c, aspectLabel, Modifier.size(28.dp)) }
            // CONFIG: this app's own photo-quality configuration
            // (presets + sharpness/saturation/contrast/gamma/denoise,
            // applied for real at capture — see PhotoConfig.kt).
            items += SheetItem("CONFIG", !state.photoConfig().isNeutral,
                { actions.onOpenSheet(SheetKind.CONFIG) }) { c ->
                ConfigGlyph(c, Modifier.size(28.dp))
            }
            items += if (state.nightExtAvailable) {
                SheetItem("NIGHT MODE", state.nightOn,
                    {
                        val wasOn = state.nightOn
                        actions.onToggleNight()
                        state.showBanner(if (wasOn) "NIGHT MODE OFF" else "NIGHT MODE ON")
                    }) { c -> NightGlyph(c, Color(0xFF4C4C50), Modifier.size(28.dp)) }
            } else {
                SheetItem("NIGHT MODE", false,
                    { state.toast = "Night Mode tidak didukung di perangkat ini" },
                    enabled = false) { c -> NightGlyph(c, Color(0xFF4C4C50), Modifier.size(28.dp)) }
            }
        }
        CamMode.VIDEO, CamMode.SLO_MO -> {
            items += SheetItem("FLASH", state.videoTorch,
                { actions.onToggleTorch() }) { c -> FlashGlyph(c, Modifier.size(28.dp)) }
            items += exposureItem
            // ACTION opens its own panel: master toggle + the software
            // gyro-EIS toggle. Available when this device can stabilize for
            // real: a gyroscope, the fused motion sensor (virtual gyro on
            // gyro-less phones), or hardware stabilization.
            val actionSupported = state.caps.motionAvailable ||
                state.caps.videoStabilization || state.caps.oisAvailable
            items += if (actionSupported) {
                SheetItem("ACTION", state.actionOn,
                    { actions.onOpenSheet(SheetKind.ACTION) }) { c ->
                    RunnerGlyph(c, Modifier.size(28.dp), off = !state.actionOn)
                }
            } else {
                SheetItem("ACTION", false,
                    { state.toast = "Stabilisasi ACTION tidak didukung di perangkat ini" },
                    enabled = false) { c -> RunnerGlyph(c, Modifier.size(28.dp), off = true) }
            }
        }
        CamMode.PORTRAIT -> {
            items += flashItem
            // The ƒ item only exists where it truly controls the blur:
            // the segmentation pipeline. With an OEM BOKEH extension the
            // strength is the OEM's, so no fake control is shown.
            if (state.extensionMode != androidx.camera.extensions.ExtensionMode.BOKEH) {
                items += SheetItem("APERTURE", true,
                    { actions.onOpenSheet(SheetKind.APERTURE) }) { c ->
                    ApertureGlyph(c, Modifier.size(28.dp))
                }
            }
            items += exposureItem
        }
        CamMode.TIME_LAPSE -> {
            items += exposureItem
            items += SheetItem("ASPECT", false,
                { actions.onOpenSheet(SheetKind.ASPECT) }) { c -> AspectGlyph(c, aspectLabel, Modifier.size(28.dp)) }
        }
        else -> Unit
    }

    // fix8: Pengaturan & Grid are tiles in the SAME grid, same glass
    // circles as FLASH/EXPOSURE/ACTION (the iPhone sheet does exactly
    // this). The old footer row — small gear + "Pengaturan & Info" text
    // on the left, Grid label on the right, under a divider — collided
    // with the FLASH/ACTION tiles on the narrow beta device. There is no
    // header/footer row left to collide with anything.
    items += SheetItem("PENGATURAN", false,
        {
            actions.onCloseSheet()
            state.settingsOpen = true
        }) { c -> GearGlyph(c, Modifier.size(28.dp)) }
    items += SheetItem("GRID", state.gridOn,
        { actions.onToggleGrid() }) { c -> GridGlyph(c, Modifier.size(28.dp)) }

    // ONE root inside the AnimatedContent slot: AnimatedContent stacks
    // multiple emitted roots on top of each other, so emitting the grid
    // rows directly made every row render at the SAME position — tiles,
    // labels and the old footer all overlapping (the beta device photo,
    // and visible in the old screenshot goldens too). The bottom padding
    // keeps the last row's labels clear of the card edge.
    Column(Modifier.padding(bottom = 10.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowItems.forEach { item ->
                    ControlButton(item.label, item.active, item.onClick, item.glyph, item.enabled)
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.width(86.dp)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        // PORTRAIT extras (reference sheet): NATURAL LIGHT chip + the
        // lighting control simplified to the one control that is real
        // here — the ƒ slider, which literally sets the segmentation
        // blur strength.
        if (state.mode == CamMode.PORTRAIT &&
            state.extensionMode != androidx.camera.extensions.ExtensionMode.BOKEH
        ) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                    .height(1.dp).background(Color.White.copy(alpha = 0.10f))
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
                Text(
                    "Intensitas blur latar",
                    color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                ApertureSlider(state)
            }
        }
    }
}

/**
 * PORTRAIT lighting wheel: a curved arc of dots over the shutter with the
 * cube glyph at the selected effect and the yellow effect-name label above
 * (iOS 26 reference). Effects that truly grade the capture (Natural /
 * Studio / Contour) are selectable; Stage looks need real relighting and
 * stay honestly dimmed.
 */
@Composable
private fun PortraitLightBar(state: CameraState) {
    val lights = PortraitLights
    val selIdx = lights.indexOfFirst { it.preset.id == state.portraitLightId }
        .coerceAtLeast(0)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(IosYellow)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                lights[selIdx].preset.label.uppercase(),
                color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.width(240.dp).height(52.dp)) {
            lights.forEachIndexed { i, light ->
                val frac = if (lights.size > 1) i / (lights.size - 1f) else 0.5f
                val xDp = (14 + frac * 196).dp
                val yDp = (30 - kotlin.math.sin(Math.PI * frac).toFloat() * 20).dp
                val selected = i == selIdx
                Box(
                    Modifier
                        .offset(x = xDp, y = yDp)
                        .size(if (selected) 34.dp else 22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color(0xFF3A3A3E) else Color.Transparent
                        )
                        .border(
                            if (selected) 1.dp else 0.dp,
                            if (selected) IosYellow.copy(alpha = 0.8f) else Color.Transparent,
                            CircleShape
                        )
                        .clickable {
                            if (light.real) {
                                state.portraitLightId = light.preset.id
                                state.persistAll()
                                state.showBanner(light.preset.label.uppercase())
                            } else {
                                state.toast =
                                    "${light.preset.label} belum tersedia di perangkat ini"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        CubeGlyph(IosYellow, Modifier.size(19.dp))
                    } else {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (light.real) Color.White.copy(alpha = 0.85f)
                                    else Color.White.copy(alpha = 0.30f)
                                )
                        )
                    }
                }
            }
        }
    }
}
