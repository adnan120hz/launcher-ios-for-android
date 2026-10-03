package net.adnan120hz.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val APPS_PER_PAGE = 20 // 4 columns x 5 rows, iOS-style

@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC))
            )
        )
    )
}

/**
 * Glass backdrop: on API 31+ this draws a copy of the wallpaper layer,
 * blurred with a real RenderEffect (androidx.compose.ui.draw.blur),
 * clipped to [shape] and aligned so the gradient matches the wallpaper
 * behind the element. On API 29-30 (or with glass off) it falls back to
 * a translucent gradient / solid iOS-18 style fill.
 *
 * [blurRadiusDp] overrides the tier blur (already tier-capped by the
 * caller); [tintAlpha] is the white glass tint strength and
 * [tintDarkness] mixes a black shade over it — the manual dock tuning.
 */
@Composable
internal fun GlassBackground(
    shape: RoundedCornerShape,
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    blurRadiusDp: Float? = null,
    tintAlpha: Float = 0.20f,
    tintDarkness: Float = 0f
) {
    Box(modifier.clip(shape)) {
        when {
            glassEnabled && Build.VERSION.SDK_INT >= 31 -> {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .requiredHeight(screenHeight)
                        .offset(y = bottomInset)
                        .blur((blurRadiusDp ?: tier.blurRadiusDp).dp)
                ) {
                    WallpaperBackground(Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = tintAlpha))
                )
                if (tintDarkness > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Color.Black.copy(alpha = tintDarkness * 0.55f)
                            )
                    )
                }
            }
            glassEnabled -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.28f)
                                )
                            )
                        )
                )
                if (tintDarkness > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Color.Black.copy(alpha = tintDarkness * 0.55f)
                            )
                    )
                }
            }
            else -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            lerp(
                                Color(0xFFEDEFF2).copy(alpha = 0.96f),
                                Color.Black.copy(alpha = 0.96f),
                                tintDarkness * 0.35f
                            )
                        )
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .border(
                    1.dp,
                    if (glassEnabled) Color.White.copy(alpha = 0.35f)
                    else Color.Black.copy(alpha = 0.06f),
                    shape
                )
        )
    }
}

/**
 * App icon renderer — Phase 3 priority:
 *  1. user-picked custom image (persistent PNG), if any;
 *  2. hand-drawn pack icon for classified apps (iOS 18 flat /
 *     iOS 26 liquid glass incl. dark variant);
 *  3. the app's original icon, framed with the iOS shape mask and
 *     pack styling (iOS 26 gets a glossy top highlight + edge light).
 *
 * Reports its window bounds for the launch scale-up animation.
 */
@Composable
fun AppIconImage(
    app: AppEntry,
    sizeDp: Dp,
    cfg: IconConfig,
    modifier: Modifier = Modifier,
    onBounds: ((Rect) -> Unit)? = null
) {
    val context = LocalContext.current
    val shape = cfg.shape.shape()
    val elevation = when {
        !cfg.shadowsEnabled -> 0.dp
        cfg.style == IconStyle.IOS26 -> 8.dp
        else -> 4.dp
    }
    val customBitmap = remember(app.packageName, cfg.customTick) {
        CustomIconStore.bitmap(context, app.packageName)
    }
    val kind = cfg.kindByPackage[app.packageName]
    val originalBitmap = remember(app.packageName, kind, customBitmap) {
        if (customBitmap == null && kind == null) {
            IconBitmapCache.get(app.packageName)
                ?: app.icon.toBitmapSafe().also {
                    IconBitmapCache.put(app.packageName, it)
                }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .onGloballyPositioned { coords ->
                if (onBounds != null) {
                    val p = coords.positionInWindow()
                    val s = coords.size
                    onBounds(
                        Rect(
                            p.x.toInt(),
                            p.y.toInt(),
                            (p.x + s.width).toInt(),
                            (p.y + s.height).toInt()
                        )
                    )
                }
            }
            .shadow(elevation, shape)
            .clip(shape)
    ) {
        when {
            customBitmap != null -> {
                Image(
                    bitmap = customBitmap.asImageBitmap(),
                    contentDescription = app.label,
                    modifier = Modifier.matchParentSize()
                )
            }
            kind != null -> {
                PackIcon(
                    kind = kind,
                    style = cfg.style,
                    dark = cfg.dark,
                    modifier = Modifier.matchParentSize(),
                    variant = cfg.effectiveVariant(),
                    tintArgb = cfg.tintArgb
                )
            }
            else -> {
                if (originalBitmap != null) {
                    // 0.8.0 iOS 26 appearances for real app icons:
                    // CLEAR reads as a light monochrome glyph, TINTED
                    // re-inks the bitmap in the user's tint. Full colour
                    // stays for LIGHT/DARK (the normal iOS default).
                    val cf = when (cfg.effectiveVariant()) {
                        IconVariant.CLEAR ->
                            androidx.compose.ui.graphics.ColorFilter.tint(
                                Color.White.copy(alpha = 0.92f),
                                androidx.compose.ui.graphics.BlendMode.SrcIn
                            )
                        IconVariant.TINTED ->
                            androidx.compose.ui.graphics.ColorFilter.tint(
                                Color(cfg.tintArgb),
                                androidx.compose.ui.graphics.BlendMode.SrcIn
                            )
                        else -> null
                    }
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.matchParentSize(),
                        colorFilter = cf
                    )
                }
            }
        }
        // iOS 26 pack treatment over framed original / custom icons:
        // glassy top highlight + bright edge.
        if (cfg.style == IconStyle.IOS26 && kind == null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (cfg.dark) 0.20f else 0.30f),
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (cfg.dark) 0.30f else 0.55f),
                        shape
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconCell(
    app: AppEntry,
    cfg: IconConfig,
    rootView: View,
    animStyle: AnimStyle,
    onIconLongPress: (AppEntry) -> Unit,
    onSelfClick: () -> Unit = {},
    iconSize: Dp = 58.dp,
    // 0.8.0 jiggle edit mode (iOS): while editing, every icon rotates
    // gently around its centre with a per-icon phase; long-press enters.
    editing: Boolean = false,
    onEnterEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    var rect by remember { mutableStateOf<Rect?>(null) }
    val jiggleAngle = remember(app.packageName, editing) {
        if (!editing) null else {
            val phase = (app.packageName.hashCode() and 0x7fffffff) % 1000 / 1000f
            Animatable(-1.1f + phase * 0.4f)
        }
    }
    LaunchedEffect(jiggleAngle) {
        val anim = jiggleAngle ?: return@LaunchedEffect
        while (true) {
            anim.animateTo(
                -anim.value,
                animationSpec = tween(
                    durationMillis = 110 + (anim.value.hashCode() % 30).absoluteValue,
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
        }
    }
    // Phase 5 (iOS 26 fluid): the tapped icon spring-squashes while its
    // app flies open — physics, never a stiff linear shrink — and
    // springs back when the user returns home. iOS 18 stays crisp.
    val isOutgoing = animStyle == AnimStyle.IOS26 &&
        OutgoingLaunch.activePackage == app.packageName
    val pressScale by animateFloatAsState(
        targetValue = if (isOutgoing) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconPress"
    )
    // 0.7.0: while the app window grows out of this icon, the icon
    // itself dissolves into it (quick fade), exactly like the reference
    // video where the glyph becomes the window.
    val outgoingAlpha by animateFloatAsState(
        targetValue = if (isOutgoing) 0f else 1f,
        animationSpec = tween(durationMillis = 210),
        label = "iconOutAlpha"
    )
    // 0.7.0 "close" flight, launcher side: on return this icon springs
    // in from 122% with a soft bounce (landing), while the system plays
    // its own exit window animation over us.
    val landing = animStyle == AnimStyle.IOS26 &&
        OutgoingLaunch.landingPackage == app.packageName
    val landingScale = remember { Animatable(1f) }
    LaunchedEffect(landing) {
        if (landing) {
            landingScale.snapTo(1.22f)
            landingScale.animateTo(
                1f,
                spring(
                    dampingRatio = 0.45f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    val isSelf = app.packageName == context.packageName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = {
                    if (editing) {
                        // iOS jiggle semantics: tapping any icon while
                        // editing keeps the editor open — here the same
                        // per-icon menu the long-press used to open.
                        onIconLongPress(app)
                    } else if (isSelf) {
                        // Our own entry never launches the activity; it
                        // is the user's door into launcher Settings.
                        onSelfClick()
                    } else {
                        launchApp(context, app.packageName, rootView, rect)
                    }
                },
                onLongClick = {
                    if (editing) onIconLongPress(app)
                    else {
                        onEnterEdit()
                        onIconLongPress(app)
                    }
                }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(
            app = app,
            sizeDp = iconSize,
            cfg = cfg,
            modifier = Modifier.graphicsLayer {
                val combined = pressScale * landingScale.value
                scaleX = combined
                scaleY = combined
                alpha = outgoingAlpha
                jiggleAngle?.let { rotationZ = it.value }
            },
            onBounds = { rect = it }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    Color.Black.copy(alpha = 0.45f),
                    Offset(0f, 1f),
                    3f
                )
            )
        )
    }
}

@Composable
private fun AppPage(
    pageApps: List<AppEntry>,
    cfg: IconConfig,
    rootView: View,
    animStyle: AnimStyle,
    onLongPressHome: () -> Unit,
    onIconLongPress: (AppEntry) -> Unit,
    onSelfClick: () -> Unit,
    editing: Boolean = false,
    onEnterEdit: () -> Unit = {},
    onExitEdit: () -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Adaptive grid geometry (0.7.0): icon size & rhythm scale with
        // the actual device width/height so small and large phones both
        // stay neat instead of one fixed layout stretched everywhere.
        val widthDp = maxWidth
        val iconSize = when {
            widthDp < 340.dp -> 50.dp
            widthDp > 430.dp -> 64.dp
            else -> 58.dp
        }
        val rowSpacing = when {
            maxHeight < 560.dp -> 10.dp
            maxHeight > 760.dp -> 20.dp
            else -> 18.dp
        }
        // Empty-area long press opens launcher settings; icon cells
        // consume their own long presses (icon context menu). In jiggle
        // edit mode a plain tap on empty space exits instead (iOS).
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(editing) {
                    detectTapGestures(
                        onLongPress = { onLongPressHome() },
                        onTap = { if (editing) onExitEdit() }
                    )
                }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            pageApps.chunked(4).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowApps.forEach { app ->
                        Box(modifier = Modifier.weight(1f)) {
                            AppIconCell(
                                app, cfg, rootView, animStyle,
                                onIconLongPress, onSelfClick,
                                iconSize = iconSize,
                                editing = editing,
                                onEnterEdit = onEnterEdit
                            )
                        }
                    }
                    repeat(4 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 0) {
        Spacer(modifier = Modifier.height(19.dp))
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            // 0.8.0 (iOS 26 page indicator): the active dot spring-widens
            // into a small pill as the page changes, instead of a static
            // 7.dp dot — same physics language as the rest of the system.
            val targetWidth = if (i == current) 18.dp else 7.dp
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = targetWidth,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "pageDot"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(width)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (i == current) Color.White
                        else Color.White.copy(alpha = 0.45f)
                    )
            )
        }
    }
}

@Composable
private fun SearchPill(
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .clickable { onClick() }
        ) {
            GlassBackground(
                shape = shape,
                glassEnabled = glassEnabled,
                tier = tier,
                screenHeight = screenHeight,
                // pill sits directly above the dock (dock outer height ~106.dp + 6.dp gap)
                bottomInset = 112.dp,
                modifier = Modifier.matchParentSize()
            )
            Text(
                text = "Search",
                fontSize = 13.sp,
                color = if (glassEnabled) Color.White else Color(0xFF3A3A3C),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun GlassDock(
    dockEntries: List<AppEntry?>,
    cfg: IconConfig,
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    blurRadiusDp: Float,
    tintAlpha: Float,
    tintDarkness: Float,
    rootView: View,
    onEmptySlotClick: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        GlassBackground(
            shape = shape,
            glassEnabled = glassEnabled,
            tier = tier,
            screenHeight = screenHeight,
            bottomInset = 12.dp,
            modifier = Modifier.matchParentSize(),
            blurRadiusDp = blurRadiusDp,
            tintAlpha = tintAlpha,
            tintDarkness = tintDarkness
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockEntries.forEach { entry ->
                if (entry != null) {
                    var rect by remember(entry.packageName) { mutableStateOf<Rect?>(null) }
                    AppIconImage(
                        app = entry,
                        sizeDp = 58.dp,
                        cfg = cfg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                launchApp(context, entry.packageName, rootView, rect)
                            },
                        onBounds = { rect = it }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { onEmptySlotClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            color = if (glassEnabled) Color.White else Color(0xFF3A3A3C)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    dynamicIslandUnlocked: Boolean,
    onDynamicIslandUnlock: () -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val store = remember { LauncherStore(context) }

    var iconStyle by remember { mutableStateOf(store.iconStyle) }
    var glassEnabled by remember { mutableStateOf(store.glassEnabled) }
    var perfTier by remember { mutableStateOf(store.perfTier) }
    var dockPackages by remember { mutableStateOf(store.dockPackages) }
    var ccStyle by remember { mutableStateOf(store.ccStyle) }
    var animStyle by remember { mutableStateOf(store.animStyle) }
    var hideSettingsInLibrary by remember {
        mutableStateOf(store.hideSettingsInLibrary)
    }
    var iconShape by remember { mutableStateOf(store.iconShape) }
    var themeMode by remember { mutableStateOf(store.themeMode) }
    var iconVariant by remember { mutableStateOf(store.iconVariant) }
    var iconTintArgb by remember { mutableStateOf(store.iconTintArgb) }
    var dockBlurDp by remember { mutableStateOf(store.dockBlurDp) }
    var dockTintAlpha by remember { mutableStateOf(store.dockTintAlpha) }
    var dockTintDark by remember { mutableStateOf(store.dockTintDark) }
    var customTick by remember { mutableStateOf(0) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var pendingCustomPkg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showControlCenter by remember { mutableStateOf(false) }
    // 0.8.0 jiggle edit mode (iOS long-press behaviour).
    var editingIcons by remember { mutableStateOf(false) }

    // ---- Phase 4: iOS lock screen -------------------------------------
    // The overlay service is the primary surface; this in-app layer is
    // the fallback for when the overlay window cannot be drawn. Both
    // render the same LockScreenContent.
    var lockEnabled by remember { mutableStateOf(store.lockEnabled) }
    var lockPrefs by remember { mutableStateOf(store.lockPrefs()) }
    var showLockInApp by remember { mutableStateOf(false) }
    var lockFallbackVisible by remember { mutableStateOf(false) }

    val lockUnlockLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            showLockInApp = false
            lockFallbackVisible = false
        } else {
            // Cancelled/failed: stay locked, offer the swipe-only exit.
            lockFallbackVisible = true
        }
    }

    fun requestLockUnlock() {
        if (lockPrefs.useBiometric && biometricAvailable(context)) {
            lockUnlockLauncher.launch(
                Intent(context, LockUnlockActivity::class.java)
            )
        } else {
            showLockInApp = false
            lockFallbackVisible = false
        }
    }

    /** "Kunci sekarang" (Settings / Control Center): prefer the overlay
     *  window; fall back to the in-app layer when overlay is not granted. */
    fun lockNow() {
        showControlCenter = false
        if (Settings.canDrawOverlays(context)) {
            try {
                context.startService(
                    Intent(context, LockScreenService::class.java)
                        .setAction(LockScreenService.ACTION_SHOW)
                )
            } catch (e: Exception) {
                showLockInApp = true
                lockFallbackVisible = false
            }
        } else {
            showLockInApp = true
            lockFallbackVisible = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    LockScreenRuntime.screenOffPending = true
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF)
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Phase 5/6: coming back home = the "close" flight.
                // Name the returning icon so it spring-lands on arrival
                // (0.7.0), then end the outgoing-launch wash so the home
                // surface sharpens back. The system exit window flight is
                // system-owned — README states this limit honestly.
                val outgoing = OutgoingLaunch.activePackage
                if (outgoing != null) {
                    OutgoingLaunch.landingPackage = outgoing
                }
                OutgoingLaunch.activePackage = null
                if (LockScreenRuntime.screenOffPending) {
                    LockScreenRuntime.screenOffPending = false
                    // The service (when running) shows the overlay itself.
                    if (lockEnabled && !LockScreenRuntime.serviceRunning) {
                        showLockInApp = true
                        lockFallbackVisible = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // already unregistered
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // The service could not draw its overlay -> show the lock here.
    LaunchedEffect(LockScreenRuntime.showInAppRequest) {
        if (LockScreenRuntime.showInAppRequest) {
            LockScreenRuntime.showInAppRequest = false
            showLockInApp = true
            lockFallbackVisible = false
        }
    }

    // Phase 5: opening any in-app layer also ends an outgoing-launch
    // wash (it normally ends on ON_RESUME after the app flight).
    LaunchedEffect(showSettings, showLockInApp) {
        if (showSettings || showLockInApp) {
            OutgoingLaunch.activePackage = null
        }
    }

    // 0.7.0: the landing spring takes ~0.7s; afterwards forget which
    // icon landed so a later recomposition doesn't replay it.
    LaunchedEffect(OutgoingLaunch.landingPackage) {
        if (OutgoingLaunch.landingPackage != null) {
            kotlinx.coroutines.delay(900)
            OutgoingLaunch.landingPackage = null
        }
    }

    // Bring the lock service back when the launcher opens with the
    // feature switched on (e.g. after the process was killed).
    LaunchedEffect(Unit) {
        if (store.lockEnabled && !LockScreenRuntime.serviceRunning) {
            try {
                context.startService(
                    Intent(context, LockScreenService::class.java)
                )
            } catch (e: Exception) {
                // Platform refused the start; in-app layer still works.
            }
        }
    }

    // ---- Phase 5: update availability --------------------------------
    // Quiet check of this repo's latest GitHub release: at most once a
    // day when the launcher opens (plus manual checks from Settings).
    // A newer version only surfaces as a banner + red dot — no spam,
    // no ads, no tracking. Cached result renders immediately.
    var availableUpdate by remember { mutableStateOf(store.availableVersion) }
    var availableReleaseUrl by remember {
        mutableStateOf(store.availableReleaseUrl)
    }
    var updateChecksEnabled by remember {
        mutableStateOf(store.updateChecksEnabled)
    }
    var lastUpdateCheckMs by remember {
        mutableLongStateOf(store.lastUpdateCheckMs)
    }
    var manualCheckTick by remember { mutableIntStateOf(0) }
    val appVersion = remember { UpdateChecker.currentVersionName(context) }

    // ---- Dynamic Island geometry (0.7.0, user-tunable, LIVE) --------
    var islandScale by remember { mutableStateOf(store.islandScale) }
    var islandWidthFactor by remember {
        mutableStateOf(store.islandWidthFactor)
    }
    var islandOffsetXDp by remember { mutableStateOf(store.islandOffsetXDp) }
    var islandOffsetYDp by remember { mutableStateOf(store.islandOffsetYDp) }

    fun pushIslandRefresh() {
        if (!IslandState.running) return
        // 0.8.0: go through the foreground-service start helper — a
        // plain startService() call is refused from the background on
        // modern Android and the island would silently ignore sliders.
        IslandService.start(context, IslandService.ACTION_REFRESH)
    }

    // 0.8.0 island auto-revive: whenever Home appears and the island is
    // supposed to be on (unlocked + enabled + overlay), make sure the
    // foreground service is actually alive. This is the fix for
    // "dynamic island belum berfungsi" after the system kills us.
    LaunchedEffect(dynamicIslandUnlocked) {
        IslandService.ensureRunning(context, dynamicIslandUnlocked)
    }

    suspend fun runUpdateCheck(manual: Boolean) {
        if (!manual && !store.updateChecksEnabled) return
        val nowMs = System.currentTimeMillis()
        if (!manual &&
            nowMs - store.lastUpdateCheckMs < UpdateChecker.CHECK_INTERVAL_MS
        ) {
            return
        }
        val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
        store.lastUpdateCheckMs = System.currentTimeMillis()
        lastUpdateCheckMs = store.lastUpdateCheckMs
        if (info != null) {
            if (UpdateChecker.isNewer(info.versionName, appVersion)) {
                store.availableVersion = info.versionName
                store.availableReleaseUrl = info.htmlUrl
                availableUpdate = info.versionName
                availableReleaseUrl = info.htmlUrl
            } else {
                store.availableVersion = null
                availableUpdate = null
            }
        }
    }

    LaunchedEffect(Unit) { runUpdateCheck(manual = false) }
    LaunchedEffect(manualCheckTick) {
        if (manualCheckTick > 0) runUpdateCheck(manual = true)
    }

    val apps by produceState<List<AppEntry>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }

    // Icon kind classification (drawn pack) for installed + dock apps.
    val kindByPackage by produceState<Map<String, IconKind>>(
        initialValue = emptyMap(),
        apps, dockPackages
    ) {
        value = withContext(Dispatchers.IO) {
            IconMap.buildIconKindMap(
                context,
                apps.map { it.packageName } + dockPackages
            )
        }
    }

    val systemDark = isSystemInDarkTheme()
    val iconDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val shadowsEnabled = perfTier != PerfTier.ENTRY
    // GLOBAL GLASS RULE (0.7.0): glass OFF forces the solid iOS 18 icon
    // family even if the user last picked "iOS 26" — iOS 26 without
    // glass anywhere is a bug state, never rendered.
    val renderIconStyle =
        if (!glassEnabled) IconStyle.IOS18 else iconStyle
    // 0.8.0: the iOS 26 appearance (Terang/Gelap/Clear/Tinted) extends
    // the old themeMode — themeMode still drives `dark`; variant picks
    // the Clear/Tinted families. effectiveVariant() in IconConfig maps
    // DARK to the dark pack, so dark follows variant OR themeMode.
    val iconCfg = IconConfig(
        style = renderIconStyle,
        dark = iconDark || iconVariant == IconVariant.DARK,
        shape = iconShape,
        shadowsEnabled = shadowsEnabled,
        kindByPackage = kindByPackage,
        customTick = customTick,
        variant = iconVariant,
        tintArgb = iconTintArgb
    )
    val effectiveDockBlur = (if (dockBlurDp >= 0f) dockBlurDp
        else perfTier.blurRadiusDp).coerceAtMost(blurCapDp(perfTier))

    // System photo picker for custom icons (no storage permission needed).
    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val pkg = pendingCustomPkg
        pendingCustomPkg = null
        if (uri != null && pkg != null) {
            if (CustomIconStore.saveFromUri(context, pkg, uri)) {
                customTick++
            }
        }
    }

    LaunchedEffect(Unit) {
        if (dockPackages.isEmpty()) {
            val defaults = withContext(Dispatchers.IO) { resolveDefaultDock(context) }
            if (defaults.isNotEmpty()) {
                store.dockPackages = defaults
                dockPackages = defaults
            }
        }
    }

    if (showLockInApp) {
        LockScreenContent(
            cfg = lockPrefs,
            glassStyleIs26 = store.lockGlassStyleIs26(),
            maxBlurDp = blurCapDp(perfTier),
            notifications = LockNotificationStore.items,
            notifAccessGranted = notificationListenerGranted(context),
            showFallback = lockFallbackVisible,
            onRequestUnlock = { requestLockUnlock() },
            onDismissFallback = {
                showLockInApp = false
                lockFallbackVisible = false
            },
            onCameraClick = {
                showLockInApp = false
                lockFallbackVisible = false
                val pkg = "net.adnan120hz.camera26"
                if (isPackageInstalled(context, pkg)) {
                    launchApp(context, pkg, rootView, null)
                } else {
                    try {
                        context.startActivity(
                            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        // no camera app on this device
                    }
                }
            }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            apps = apps,
            iconStyle = renderIconStyle,
            rawIconStyle = iconStyle,
            glassEnabled = glassEnabled,
            perfTier = perfTier,
            dockPackages = dockPackages,
            ccStyle = ccStyle,
            hideSettingsInLibrary = hideSettingsInLibrary,
            iconConfig = iconCfg,
            iconShape = iconShape,
            themeMode = themeMode,
            dockBlurDp = effectiveDockBlur,
            dockBlurMax = blurCapDp(perfTier),
            dockTintAlpha = dockTintAlpha,
            dockTintDark = dockTintDark,
            dynamicIslandUnlocked = dynamicIslandUnlocked,
            onIconStyleChange = { iconStyle = it; store.iconStyle = it },
            onIconShapeChange = { iconShape = it; store.iconShape = it },
            onThemeModeChange = { themeMode = it; store.themeMode = it },
            iconVariant = iconVariant,
            iconTintArgb = iconTintArgb,
            onIconVariantChange = { iconVariant = it; store.iconVariant = it },
            onIconTintChange = { iconTintArgb = it; store.iconTintArgb = it },
            onDockBlurChange = { dockBlurDp = it; store.dockBlurDp = it },
            onDockTintAlphaChange = {
                dockTintAlpha = it; store.dockTintAlpha = it
            },
            onDockTintDarkChange = {
                dockTintDark = it; store.dockTintDark = it
            },
            onResetDockGlass = {
                dockBlurDp = -1f; store.dockBlurDp = -1f
                dockTintAlpha = 0.20f; store.dockTintAlpha = 0.20f
                dockTintDark = 0f; store.dockTintDark = 0f
            },
            onGlassChange = { glassEnabled = it; store.glassEnabled = it },
            onTierChange = { perfTier = it; store.perfTier = it },
            animStyle = animStyle,
            onAnimStyleChange = { animStyle = it; store.animStyle = it },
            updateVersion = if (updateChecksEnabled) availableUpdate else null,
            updateReleaseUrl = availableReleaseUrl,
            appVersion = appVersion,
            updateChecksEnabled = updateChecksEnabled,
            lastUpdateCheckMs = lastUpdateCheckMs,
            onUpdateChecksChange = { enabled ->
                updateChecksEnabled = enabled
                store.updateChecksEnabled = enabled
                if (enabled) manualCheckTick++
            },
            onCheckUpdatesNow = { manualCheckTick++ },
            onDockChange = { updated -> dockPackages = updated; store.dockPackages = updated },
            onResetDock = {
                val defaults = resolveDefaultDock(context)
                dockPackages = defaults
                store.dockPackages = defaults
            },
            onCcStyleChange = { ccStyle = it; store.ccStyle = it },
            onHideSettingsChange = {
                hideSettingsInLibrary = it
                store.hideSettingsInLibrary = it
            },
            onDynamicIslandUnlock = onDynamicIslandUnlock,
            lockEnabled = lockEnabled,
            lockPrefs = lockPrefs,
            lockGlassStyleIs26 = store.lockGlassStyleIs26(),
            onLockEnabledChange = { enabled ->
                lockEnabled = enabled
                store.lockEnabled = enabled
                try {
                    if (enabled) {
                        context.startService(
                            Intent(context, LockScreenService::class.java)
                        )
                    } else {
                        context.stopService(
                            Intent(context, LockScreenService::class.java)
                        )
                    }
                } catch (e: Exception) {
                    // Platform refused; the toggle stays stored and the
                    // in-app layer remains the fallback path.
                }
            },
            onLockPrefsChange = { updated ->
                lockPrefs = updated
                store.saveLockPrefs(updated)
            },
            onLockNow = {
                showSettings = false
                lockNow()
            },
            islandScale = islandScale,
            islandWidthFactor = islandWidthFactor,
            islandOffsetXDp = islandOffsetXDp,
            islandOffsetYDp = islandOffsetYDp,
            onIslandGeometryChange = { scale, width, x, y ->
                islandScale = scale
                islandWidthFactor = width
                islandOffsetXDp = x
                islandOffsetYDp = y
                store.islandScale = scale
                store.islandWidthFactor = width
                store.islandOffsetXDp = x
                store.islandOffsetYDp = y
                pushIslandRefresh()
            },
            onResetIslandGeometry = {
                store.resetIslandGeometry()
                islandScale = store.islandScale
                islandWidthFactor = store.islandWidthFactor
                islandOffsetXDp = store.islandOffsetXDp
                islandOffsetYDp = store.islandOffsetYDp
                pushIslandRefresh()
            },
            onOpenControlCenter = {
                showSettings = false
                showControlCenter = true
            },
            onBack = { showSettings = false }
        )
        return
    }

    // App Library list: optionally hide the OEM Settings entry (list only).
    val systemSettingsPkg = remember { resolveSystemSettingsPackage(context) }
    val libraryApps = remember(apps, hideSettingsInLibrary, systemSettingsPkg) {
        if (hideSettingsInLibrary && systemSettingsPkg != null) {
            apps.filter { it.packageName != systemSettingsPkg }
        } else {
            apps
        }
    }

    val appPages = remember(apps) { apps.chunked(APPS_PER_PAGE) }
    val libraryPageIndex = appPages.size
    val pagerState = rememberPagerState(pageCount = { appPages.size + 1 })
    val scope = rememberCoroutineScope()

    val dockEntries = remember(dockPackages, apps) {
        val entries = dockPackages.map { pkg ->
            apps.firstOrNull { it.packageName == pkg } ?: loadAppEntry(context, pkg)
        }
        (entries + List(4) { null }).take(4)
    }

    // ---- Phase 5: outgoing-launch progress ---------------------------
    // 0 = home at rest, 1 = the launched app has fully flown open.
    // In the iOS 26 fluid style the whole home surface (wallpaper +
    // icons) progressively blurs behind the opening window, like the
    // reference: driven by a spring so the wash eases in with physics
    // instead of a stiff linear ramp, and washed back out on return.
    // iOS 18 keeps the classic crisp zoom — no blur, short tween.
    // Blur is a real GPU RenderEffect (Modifier.blur, API 31+); below
    // that the layer only fades slightly. Radius is tier-scaled (and
    // capped per tier) so smooth frames always outrank the effect.
    val fluidActive = animStyle == AnimStyle.IOS26 &&
        OutgoingLaunch.activePackage != null
    val launchProgress by animateFloatAsState(
        targetValue = if (fluidActive) 1f else 0f,
        animationSpec = if (fluidActive) {
            spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            tween(durationMillis = 220)
        },
        label = "launchProgress"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        Box(
            Modifier
                .matchParentSize()
                .blur((launchProgress * launchBlurRadiusDp(perfTier)).dp)
                .graphicsLayer {
                    // iOS feel: the whole home surface also pulls back
                    // slightly (zoom-out) behind the opening window.
                    val pullBack = 1f - launchProgress * 0.055f
                    scaleX = pullBack
                    scaleY = pullBack
                    alpha = 1f - launchProgress * 0.10f
                }
        ) {
        WallpaperBackground(Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page < appPages.size) {
                    AppPage(
                        pageApps = appPages[page],
                        cfg = iconCfg,
                        rootView = rootView,
                        animStyle = animStyle,
                        onLongPressHome = { showSettings = true },
                        onIconLongPress = { menuApp = it },
                        onSelfClick = { showSettings = true },
                        editing = editingIcons,
                        onEnterEdit = { editingIcons = true },
                        onExitEdit = { editingIcons = false }
                    )
                } else {
                    AppLibraryScreen(
                        apps = libraryApps,
                        cfg = iconCfg,
                        dynamicIslandUnlocked = dynamicIslandUnlocked,
                        updateAvailable = availableUpdate != null,
                        onOpenSettings = { showSettings = true },
                        onIconLongPress = { menuApp = it }
                    )
                }
            }

            if (pagerState.currentPage < libraryPageIndex) {
                PageDots(count = appPages.size, current = pagerState.currentPage)
            } else {
                Spacer(modifier = Modifier.height(19.dp))
            }

            SearchPill(
                glassEnabled = glassEnabled,
                tier = perfTier,
                screenHeight = screenHeight,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(libraryPageIndex) }
                }
            )

            GlassDock(
                dockEntries = dockEntries,
                cfg = iconCfg,
                glassEnabled = glassEnabled,
                tier = perfTier,
                screenHeight = screenHeight,
                blurRadiusDp = effectiveDockBlur,
                tintAlpha = dockTintAlpha,
                tintDarkness = dockTintDark,
                rootView = rootView,
                onEmptySlotClick = { showSettings = true }
            )
        }
        }

        // Top-edge swipe opens the Control Center overlay.
        if (!showControlCenter) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 6f) showControlCenter = true
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = showControlCenter,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            ControlCenterPanel(
                style = if (!glassEnabled) CcStyle.IOS18 else ccStyle,
                glassEnabled = glassEnabled,
                tier = perfTier,
                screenHeight = screenHeight,
                onClose = { showControlCenter = false },
                onLockNow = { lockNow() }
            )
        }

        // Icon long-press context menu: custom icon / reset / settings.
        menuApp?.let { app ->
            val hasCustom = remember(app.packageName, customTick) {
                CustomIconStore.has(context, app.packageName)
            }
            IconContextMenu(
                app = app,
                cfg = iconCfg,
                glass = glassEnabled,
                hasCustom = hasCustom,
                onDismiss = { menuApp = null },
                onChangeIcon = {
                    pendingCustomPkg = app.packageName
                    menuApp = null
                    iconPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onResetIcon = {
                    CustomIconStore.clear(context, app.packageName)
                    customTick++
                    menuApp = null
                },
                onOpenSettings = {
                    menuApp = null
                    showSettings = true
                }
            )
        }
    }
}

@Composable
private fun IconContextMenu(
    app: AppEntry,
    cfg: IconConfig,
    glass: Boolean,
    hasCustom: Boolean,
    onDismiss: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                // 0.7.0: the app's own sheets follow the same glass rule
                // — glass plate when Liquid Glass is on, solid iOS 18
                // sheet when it is off. NEVER grey-on-grey mud.
                .background(
                    if (glass) Brush.verticalGradient(
                        listOf(
                            Color(0xFF2B3448).copy(alpha = 0.92f),
                            Color(0xFF171D2B).copy(alpha = 0.94f)
                        )
                    ) else Brush.verticalGradient(
                        listOf(Color(0xFFF2F2F7), Color(0xFFF2F2F7))
                    )
                )
                .then(
                    if (glass) {
                        Modifier.border(
                            1.dp, Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(24.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconImage(app = app, sizeDp = 64.dp, cfg = cfg)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = app.label,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (glass) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            MenuRow("🖼  Ganti ikon dari galeri", glass, onChangeIcon)
            if (hasCustom) {
                MenuRow("↩  Reset ke ikon bawaan", glass, onResetIcon)
            }
            MenuRow("⚙  Pengaturan launcher", glass, onOpenSettings)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Batal",
                fontSize = 15.sp,
                color = Color(0xFF007AFF),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MenuRow(label: String, glass: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        color = if (glass) Color.White else Color(0xFF1C1C1E),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (glass) Color.White.copy(alpha = 0.18f)
                else Color.White
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
}
