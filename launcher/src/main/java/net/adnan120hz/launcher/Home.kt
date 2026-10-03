package net.adnan120hz.launcher

import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 */
@Composable
private fun GlassBackground(
    shape: RoundedCornerShape,
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier
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
                        .blur(tier.blurRadiusDp.dp)
                ) {
                    WallpaperBackground(Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.20f))
                )
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
            }
            else -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFFEDEFF2).copy(alpha = 0.96f))
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

/** Plain app icon image; reports its window bounds for launch animation. */
@Composable
fun AppIconImage(
    app: AppEntry,
    sizeDp: Dp,
    iconStyle: IconStyle,
    shadowsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onBounds: ((Rect) -> Unit)? = null
) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmapSafe().asImageBitmap() }
    val radius = if (iconStyle == IconStyle.IOS26) 15.dp else 12.dp
    val shape = RoundedCornerShape(radius)
    val elevation = when {
        !shadowsEnabled -> 0.dp
        iconStyle == IconStyle.IOS26 -> 8.dp
        else -> 4.dp
    }
    Image(
        bitmap = iconBitmap,
        contentDescription = app.label,
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
    )
}

@Composable
fun AppIconCell(
    app: AppEntry,
    iconStyle: IconStyle,
    shadowsEnabled: Boolean,
    rootView: View
) {
    val context = LocalContext.current
    var rect by remember { mutableStateOf<Rect?>(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { launchApp(context, app.packageName, rootView, rect) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(
            app = app,
            sizeDp = 58.dp,
            iconStyle = iconStyle,
            shadowsEnabled = shadowsEnabled,
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
    iconStyle: IconStyle,
    shadowsEnabled: Boolean,
    rootView: View,
    onLongPressHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPressHome() })
            }
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        pageApps.chunked(4).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowApps.forEach { app ->
                    Box(modifier = Modifier.weight(1f)) {
                        AppIconCell(app, iconStyle, shadowsEnabled, rootView)
                    }
                }
                repeat(4 - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
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
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(7.dp)
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
    iconStyle: IconStyle,
    glassEnabled: Boolean,
    tier: PerfTier,
    shadowsEnabled: Boolean,
    screenHeight: Dp,
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
            modifier = Modifier.matchParentSize()
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
                        iconStyle = iconStyle,
                        shadowsEnabled = shadowsEnabled,
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
    var hideSettingsInLibrary by remember {
        mutableStateOf(store.hideSettingsInLibrary)
    }
    var showSettings by remember { mutableStateOf(false) }
    var showControlCenter by remember { mutableStateOf(false) }

    val apps by produceState<List<AppEntry>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
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

    if (showSettings) {
        SettingsScreen(
            apps = apps,
            iconStyle = iconStyle,
            glassEnabled = glassEnabled,
            perfTier = perfTier,
            dockPackages = dockPackages,
            ccStyle = ccStyle,
            hideSettingsInLibrary = hideSettingsInLibrary,
            dynamicIslandUnlocked = dynamicIslandUnlocked,
            onIconStyleChange = { iconStyle = it; store.iconStyle = it },
            onGlassChange = { glassEnabled = it; store.glassEnabled = it },
            onTierChange = { perfTier = it; store.perfTier = it },
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

    val shadowsEnabled = perfTier != PerfTier.ENTRY
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
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
                        iconStyle = iconStyle,
                        shadowsEnabled = shadowsEnabled,
                        rootView = rootView,
                        onLongPressHome = { showSettings = true }
                    )
                } else {
                    AppLibraryScreen(
                        apps = libraryApps,
                        iconStyle = iconStyle,
                        shadowsEnabled = shadowsEnabled,
                        dynamicIslandUnlocked = dynamicIslandUnlocked,
                        onOpenSettings = { showSettings = true }
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
                iconStyle = iconStyle,
                glassEnabled = glassEnabled,
                tier = perfTier,
                shadowsEnabled = shadowsEnabled,
                screenHeight = screenHeight,
                rootView = rootView,
                onEmptySlotClick = { showSettings = true }
            )
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
                onClose = { showControlCenter = false }
            )
        }
    }
}
