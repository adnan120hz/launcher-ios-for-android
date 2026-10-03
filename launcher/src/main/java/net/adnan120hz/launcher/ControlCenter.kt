package net.adnan120hz.launcher

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Control Center overlay panel (slides down over the home screen),
 * redesigned in 0.7.0 from the user's reference screenshots:
 *
 *  - iOS 26 (Liquid Glass): connectivity squircle with four round glass
 *    buttons (blue when on), squircle music card, two big white circles
 *    (orientation lock, Do Not Disturb) and two tall glass capsules
 *    (brightness, volume) filled from the bottom, then a grid of small
 *    glass circles.
 *  - iOS 18: same real layout, classic skins — smaller radii, darker
 *    opaque tiles, blue/ash circles, small labels under the small
 *    circles. GLOBAL RULE (user, 0.7.0): style iOS 18 = absolutely no
 *    glass anywhere in this panel; style iOS 26 = glass on.
 *
 * Honesty rules: every visible control DOES something real.
 * - Flashlight = real torch toggle (CameraManager).
 * - Brightness capsule = real system brightness (WRITE_SETTINGS);
 *   without that grant a tap routes to the grant screen.
 * - Volume capsule = real media volume (AudioManager, no grant needed).
 * - Orientation lock = real ACCELEROMETER_ROTATION write (same grant
 *   as brightness); Do Not Disturb = real interruption filter when
 *   policy access is granted, otherwise routes to that grant.
 * - Wi-Fi / Bluetooth / Airplane cannot be flipped by a third-party app
 *   on modern Android, so those circles open the matching system panel.
 * - Small circles open the real device apps (camera26/system camera,
 *   calculator, clock, OEM settings) — hidden when the target doesn't
 *   exist, never dummy.
 */
@Composable
fun ControlCenterPanel(
    style: CcStyle,
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    onClose: () -> Unit,
    onLockNow: () -> Unit
) {
    val glass = style == CcStyle.IOS26
    val panelShape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = if (glass) 40.dp else 26.dp,
        bottomEnd = if (glass) 40.dp else 26.dp
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onClose() }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
        ) {
            Box {
                CcBackdrop(
                    glass = glass,
                    tier = tier,
                    screenHeight = screenHeight,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier.padding(
                        start = 18.dp, end = 18.dp, top = 42.dp, bottom = 20.dp
                    )
                ) {
                    CcTopRow(glass = glass, onLockNow = onLockNow)
                    Spacer(modifier = Modifier.height(12.dp))
                    CcMiddleRow(glass = glass)
                    Spacer(modifier = Modifier.height(14.dp))
                    CcSmallGrid(glass = glass, onLockNow = onLockNow)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Versi 0.7.0",
                        fontSize = 10.sp,
                        color = if (glass) Color.White.copy(alpha = 0.55f)
                        else Color(0xFF8E8E93)
                    )
                }
            }
        }
    }
}

@Composable
private fun CcBackdrop(
    glass: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when {
            glass && Build.VERSION.SDK_INT >= 31 -> {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .requiredHeight(screenHeight)
                        .blur(tier.blurRadiusDp.dp)
                ) {
                    WallpaperBackground(Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFF1A2233).copy(alpha = 0.30f))
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.06f)
                                )
                            ),
                            RoundedCornerShape(
                                bottomStart = 40.dp, bottomEnd = 40.dp
                            )
                        )
                )
            }
            glass -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF3A4A66).copy(alpha = 0.88f),
                                    Color(0xFF1A2233).copy(alpha = 0.92f)
                                )
                            )
                        )
                )
            }
            else -> {
                // iOS 18: fully solid, absolutely no glass.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFF17171A))
                )
            }
        }
    }
}

// ---------------------------------------------------------------- top row

@Composable
private fun CcTopRow(glass: Boolean, onLockNow: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectivityModule(
            glass = glass,
            modifier = Modifier.weight(1f)
        )
        MusicModule(
            glass = glass,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Squircle "glass plate" for iOS 26 / solid plate for iOS 18. */
@Composable
private fun ModulePlate(
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(if (glass) 30.dp else 22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (glass) Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.13f)
                    )
                ) else Brush.verticalGradient(
                    listOf(Color(0xFF2C2C2E), Color(0xFF232325))
                )
            )
            .then(
                if (glass) {
                    Modifier.border(
                        1.dp, Color.White.copy(alpha = 0.35f), shape
                    )
                } else {
                    Modifier
                }
            )
            .padding(12.dp)
    ) {
        content()
    }
}

@Composable
private fun RoundButton(
    glyph: CcGlyph,
    glass: Boolean,
    active: Boolean,
    big: Boolean = false,
    glyphColor: Color? = null,
    onClick: () -> Unit
) {
    val size = if (big) 62.dp else 52.dp
    val bg = when {
        glass && active -> Color(0xFF0A84FF)
        glass -> Color.White.copy(alpha = 0.22f)
        active -> Color(0xFF0A84FF)
        else -> Color(0xFF48484A)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (glass) {
                    Modifier.border(
                        1.dp,
                        Color.White.copy(alpha = if (active) 0.25f else 0.30f),
                        RoundedCornerShape(50)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CcGlyphIcon(
            glyph = glyph,
            color = glyphColor ?: Color.White,
            modifier = Modifier.size(if (big) 30.dp else 25.dp)
        )
    }
}

@Composable
private fun ConnectivityModule(glass: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    val wifiOn = remember(tick) { isWifiEnabled(context) }
    val btOn = remember(tick) { isBluetoothEnabled(context) }
    val airplaneOn = remember(tick) { isAirplaneOn(context) }
    var torchOn by remember { mutableStateOf(false) }

    val cm = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    DisposableEffect(cm) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId(context)) torchOn = enabled
            }
        }
        cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { cm.unregisterTorchCallback(callback) }
    }

    ModulePlate(glass = glass, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(
                    glyph = CcGlyph.PLANE,
                    glass = glass,
                    active = airplaneOn,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                RoundButton(
                    glyph = CcGlyph.WIFI,
                    glass = glass,
                    active = wifiOn,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundButton(
                    glyph = CcGlyph.BLUETOOTH,
                    glass = glass,
                    active = btOn,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                RoundButton(
                    glyph = CcGlyph.FLASHLIGHT,
                    glass = glass,
                    active = torchOn,
                    glyphColor = if (torchOn) Color(0xFFFFD60A)
                    else Color.White,
                    onClick = {
                        val target = !torchOn
                        if (setTorch(context, target)) torchOn = target
                    }
                )
            }
        }
    }
}

@Composable
private fun MusicModule(glass: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    val controller = remember(tick) { activeMediaController(context) }
    val granted = remember(tick) { notificationListenerGranted(context) }
    val ink = Color.White
    val subInk = if (glass) Color.White.copy(alpha = 0.70f) else Color(0xFFAEAEB2)

    ModulePlate(glass = glass, modifier = modifier) {
        Column {
            when {
                !granted -> {
                    CcGlyphIcon(
                        CcGlyph.MUSIC_NOTE, ink, Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Musik",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink
                    )
                    Text(
                        text = "Butuh izin akses notifikasi buat membaca & mengontrol lagu.",
                        fontSize = 11.sp,
                        color = subInk
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Text("Izinkan", fontSize = 12.sp)
                    }
                }
                controller == null -> {
                    CcGlyphIcon(
                        CcGlyph.MUSIC_NOTE, ink, Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tidak ada musik yang diputar",
                        fontSize = 13.sp,
                        color = subInk
                    )
                }
                else -> {
                    val metadata = controller.metadata
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                        ?: "Tidak diketahui"
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val art = remember(metadata) {
                        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    }
                    val playing = controller.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (art != null) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (glass) Color.White.copy(alpha = 0.25f)
                                        else Color(0xFF3A3A3C)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CcGlyphIcon(
                                    CcGlyph.MUSIC_NOTE, ink, Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (artist.isNotBlank()) {
                                Text(
                                    text = artist,
                                    fontSize = 11.sp,
                                    color = subInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CcGlyphIcon(
                            CcGlyph.PREV, ink,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    controller.transportControls.skipToPrevious()
                                }
                                .padding(6.dp)
                                .size(20.dp)
                        )
                        CcGlyphIcon(
                            if (playing) CcGlyph.PAUSE else CcGlyph.PLAY,
                            ink,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (playing) controller.transportControls.pause()
                                    else controller.transportControls.play()
                                }
                                .padding(6.dp)
                                .size(26.dp)
                        )
                        CcGlyphIcon(
                            CcGlyph.NEXT, ink,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    controller.transportControls.skipToNext()
                                }
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- middle row

@Composable
private fun CcMiddleRow(glass: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    val canWrite = remember(tick) { canWriteSystemSettings(context) }
    val rotationLocked = remember(tick, canWrite) {
        if (canWrite) {
            try {
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION, 1
                ) == 0
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }
    val nm = remember {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    val dndAccess = remember(tick) { nm.isNotificationPolicyAccessGranted }
    val dndOn = remember(tick, dndAccess) {
        dndAccess && nm.currentInterruptionFilter !=
            NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun openWriteSettingsGrant() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Two big circles: orientation lock + Do Not Disturb.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigSystemCircle(
                glyph = CcGlyph.ROTATION_LOCK,
                glass = glass,
                active = rotationLocked,
                onClick = {
                    if (canWrite) {
                        try {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.ACCELEROMETER_ROTATION,
                                if (rotationLocked) 1 else 0
                            )
                        } catch (e: Exception) {
                            // write refused; state re-reads next tick
                        }
                        tick++
                    } else {
                        openWriteSettingsGrant()
                    }
                }
            )
            BigSystemCircle(
                glyph = CcGlyph.MOON,
                glass = glass,
                active = dndOn,
                onClick = {
                    if (dndAccess) {
                        nm.setInterruptionFilter(
                            if (dndOn) {
                                NotificationManager.INTERRUPTION_FILTER_ALL
                            } else {
                                NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            }
                        )
                        tick++
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }
        // Two tall capsules: brightness (left) + volume (right).
        BrightnessCapsule(glass = glass, canWrite = canWrite, onNeedGrant = {
            openWriteSettingsGrant()
        })
        VolumeCapsule(glass = glass)
    }
}

/** Big white circle (iOS 26 reference) / classic circle (iOS 18). */
@Composable
private fun BigSystemCircle(
    glyph: CcGlyph,
    glass: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        glass && active -> Color(0xFFFF453A)
        glass -> Color.White
        active -> Color(0xFFFF453A)
        else -> Color(0xFF48484A)
    }
    val glyphColor = when {
        glass && !active -> Color(0xFFFF453A)
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(
                if (glass) {
                    Modifier.border(
                        1.dp, Color.White.copy(alpha = 0.4f),
                        RoundedCornerShape(50)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CcGlyphIcon(
            glyph = glyph,
            color = glyphColor,
            modifier = Modifier.size(30.dp)
        )
    }
}

/** Vertical glass/solid capsule slider, filled white from the bottom.
 *  The fill is a bottom-aligned box sized by the capsule's own height
 *  (measured once) so layout stays simple and cheap. */
@Composable
private fun CapsuleSlider(
    glass: Boolean,
    glyph: CcGlyph,
    fraction: Float,
    onDragFraction: (Float) -> Unit,
    onTap: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    val heightDp = 132.dp
    var capsuleHeightPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(heightDp)
            .clip(shape)
            .background(
                if (glass) Color.White.copy(alpha = 0.20f)
                else Color(0xFF2C2C2E)
            )
            .then(
                if (glass) {
                    Modifier.border(
                        1.dp, Color.White.copy(alpha = 0.35f), shape
                    )
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned { capsuleHeightPx = it.size.height }
    ) {
        // Fill from the bottom.
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0.001f && capsuleHeightPx > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(
                        with(androidx.compose.ui.platform.LocalDensity.current) {
                            (capsuleHeightPx * f).toDp()
                        }
                    )
                    .background(Color.White)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val hPx = size.height.toFloat().coerceAtLeast(1f)
                            onDragFraction(-dragAmount / hPx)
                        }
                    )
                }
                .clickable { onTap() },
            contentAlignment = Alignment.BottomCenter
        ) {
            CcGlyphIcon(
                glyph = glyph,
                color = if (fraction > 0.45f) Color(0xFF0A84FF)
                else if (glass) Color.White else Color(0xFFFFD60A),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(24.dp)
            )
        }
    }
}

@Composable
private fun BrightnessCapsule(
    glass: Boolean,
    canWrite: Boolean,
    onNeedGrant: () -> Unit
) {
    val context = LocalContext.current
    var fraction by remember { mutableFloatStateOf(systemBrightness01(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2500)
            fraction = systemBrightness01(context)
        }
    }
    CapsuleSlider(
        glass = glass,
        glyph = CcGlyph.SUN,
        fraction = if (canWrite) fraction else 0.0f,
        onDragFraction = { delta ->
            if (canWrite) {
                fraction = (fraction + delta).coerceIn(0.02f, 1f)
                setSystemBrightness01(context, fraction)
            }
        },
        onTap = {
            if (!canWrite) onNeedGrant()
        }
    )
}

@Composable
private fun VolumeCapsule(glass: Boolean) {
    val context = LocalContext.current
    val am = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var fraction by remember {
        mutableFloatStateOf(
            am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    .coerceAtLeast(1)
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            fraction = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                .toFloat() /
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    .coerceAtLeast(1)
        }
    }
    CapsuleSlider(
        glass = glass,
        glyph = CcGlyph.SPEAKER,
        fraction = fraction,
        onDragFraction = { delta ->
            fraction = (fraction + delta).coerceIn(0f, 1f)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (fraction * max).toInt(),
                0
            )
        },
        onTap = {
            context.startActivity(
                Intent(Settings.Panel.ACTION_VOLUME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
}

// -------------------------------------------------------------- small grid

private data class CcShortcut(
    val glyph: CcGlyph,
    val label: String,
    val intent: Intent
)

@Composable
private fun CcSmallGrid(glass: Boolean, onLockNow: () -> Unit) {
    val context = LocalContext.current
    val shortcuts = remember {
        buildCcShortcuts(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shortcuts.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    SmallCircle(
                        glyph = item.glyph,
                        label = item.label,
                        glass = glass,
                        onClick = {
                            context.startActivity(
                                item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        // Final row: lock now (our own lock layer, always available).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallCircle(
                glyph = CcGlyph.LOCK,
                label = "Kunci",
                glass = glass,
                onClick = onLockNow,
                modifier = Modifier.weight(1f)
            )
            SmallCircle(
                glyph = CcGlyph.GEAR,
                label = "Atur",
                glass = glass,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun SmallCircle(
    glyph: CcGlyph,
    label: String,
    glass: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (glass) Color.White.copy(alpha = 0.20f)
                    else Color(0xFF2C2C2E)
                )
                .then(
                    if (glass) {
                        Modifier.border(
                            1.dp, Color.White.copy(alpha = 0.30f),
                            RoundedCornerShape(50)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            CcGlyphIcon(
                glyph = glyph,
                color = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }
        if (!glass) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color(0xFFAEAEB2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Real shortcuts: rows appear only when the target app exists. */
private fun buildCcShortcuts(context: Context): List<CcShortcut> {
    val pm = context.packageManager
    fun launchIntentFor(pkg: String?): Intent? {
        if (pkg == null) return null
        return pm.getLaunchIntentForPackage(pkg)
    }
    fun resolve(intent: Intent): String? = try {
        pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            ?.takeIf { it != "android" }
    } catch (e: Exception) {
        null
    }

    val result = mutableListOf<CcShortcut>()

    val cameraPkg = if (isPackageInstalled(context, IconMap.CAMERA26_PACKAGE)) {
        IconMap.CAMERA26_PACKAGE
    } else {
        resolve(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }
    launchIntentFor(cameraPkg)?.let {
        result.add(CcShortcut(CcGlyph.CAMERA, "Kamera", it))
    }

    val calcPkg = resolve(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
    )
    launchIntentFor(calcPkg)?.let {
        result.add(CcShortcut(CcGlyph.CALCULATOR, "Kalkulator", it))
    }

    val clockPkg = resolve(
        Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
    )
    clockPkg?.let { pkg ->
        launchIntentFor(pkg)?.let {
            result.add(CcShortcut(CcGlyph.TIMER, "Jam", it))
        }
    }

    return result
}
