package net.adnan120hz.launcher

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.MediaMetadata
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Control Center overlay panel (slides down over the home screen).
 *
 * Honest Android rules baked in:
 * - Flashlight is a REAL toggle (CameraManager torch).
 * - Brightness writes the system value, but only after the user grants
 *   WRITE_SETTINGS; otherwise the slider row routes to that permission.
 * - Wi-Fi / Bluetooth / Airplane mode cannot be flipped by a third-party
 *   app on modern Android, so those tiles open the matching system
 *   panel/page. They are shortcuts, never fake switches.
 * - Mobile data cannot be toggled by regular apps at all, so it is not
 *   shown as a toggle.
 */
@Composable
fun ControlCenterPanel(
    style: CcStyle,
    glassEnabled: Boolean,
    tier: PerfTier,
    screenHeight: Dp,
    onClose: () -> Unit
) {
    val glass = style == CcStyle.IOS26
    val panelShape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = 36.dp, bottomEnd = 36.dp
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // scrim: tap outside closes
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
                        start = 20.dp, end = 20.dp, top = 44.dp, bottom = 22.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Control Center",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (glass) Color.White else Color.Black
                        )
                        Text(
                            text = if (glass) "gaya iOS 26 · Liquid Glass"
                            else "gaya iOS 18 · klasik",
                            fontSize = 11.sp,
                            color = if (glass) Color.White.copy(alpha = 0.75f)
                            else Color(0xFF6E6E73)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ConnectivityTiles(glass = glass)
                    Spacer(modifier = Modifier.height(14.dp))
                    BrightnessRow(glass = glass)
                    Spacer(modifier = Modifier.height(14.dp))
                    MusicRow(glass = glass)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tarik ke atas atau ketuk di luar panel buat menutup.",
                        fontSize = 11.sp,
                        color = if (glass) Color.White.copy(alpha = 0.7f)
                        else Color(0xFF6E6E73)
                    )
                }
            }
        }
    }
}

/** Glass backdrop: blurred copy of the wallpaper layer (API 31+) when the
 *  iOS 26 style is active; solid classic fill for the iOS 18 style. */
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
                        .background(Color.White.copy(alpha = 0.18f))
                )
                // edge reflection
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(
                                bottomStart = 36.dp, bottomEnd = 36.dp
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
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.30f)
                                )
                            )
                        )
                )
            }
            else -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color(0xFFF2F2F7).copy(alpha = 0.98f))
                )
            }
        }
    }
}

@Composable
private fun Tile(
    glyph: String,
    label: String,
    state: String,
    glass: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radius = if (glass) 22.dp else 16.dp
    val bg = when {
        glass && active -> Color(0xFF0A84FF).copy(alpha = 0.85f)
        glass -> Color.White.copy(alpha = 0.25f)
        active -> Color(0xFF0A84FF)
        else -> Color.White
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = glyph, fontSize = 20.sp, color = if (glass || active) Color.White else Color.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (glass || active) Color.White else Color.Black
        )
        Text(
            text = state,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (glass || active) Color.White.copy(alpha = 0.8f)
            else Color(0xFF6E6E73)
        )
    }
}

@Composable
private fun ConnectivityTiles(glass: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    // re-read system states each tick
    val wifiOn = remember(tick) { isWifiEnabled(context) }
    val btOn = remember(tick) { isBluetoothEnabled(context) }
    val airplaneOn = remember(tick) { isAirplaneOn(context) }
    var torchOn by remember { mutableStateOf(false) }

    val cm = remember {
        context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
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

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Tile(
                glyph = "✈",
                label = "Pesawat",
                state = if (airplaneOn) "Nyala" else "Mati",
                glass = glass,
                active = airplaneOn,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            Tile(
                glyph = "📶",
                label = "Wi-Fi",
                state = if (wifiOn) "Nyala" else "Mati",
                glass = glass,
                active = wifiOn,
                onClick = {
                    // System connectivity panel (Wi-Fi/data switches live there)
                    context.startActivity(
                        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            Tile(
                glyph = "ᛒ",
                label = "Bluetooth",
                state = if (btOn) "Nyala" else "Mati",
                glass = glass,
                active = btOn,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            Tile(
                glyph = "🔦",
                label = "Senter",
                state = if (torchOn) "Nyala" else "Mati",
                glass = glass,
                active = torchOn,
                onClick = {
                    val target = !torchOn
                    if (setTorch(context, target)) torchOn = target
                },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Wi-Fi/Bluetooth/Mode Pesawat dibuka lewat panel sistem (Android tidak mengizinkan app biasa mengubahnya langsung). Senter beneran nyala/mati dari sini.",
            fontSize = 10.sp,
            color = if (glass) Color.White.copy(alpha = 0.7f) else Color(0xFF6E6E73)
        )
    }
}

@Composable
private fun BrightnessRow(glass: Boolean) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(systemBrightness01(context)) }
    val canWrite = remember { canWriteSystemSettings(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (glass) 22.dp else 16.dp))
            .background(
                if (glass) Color.White.copy(alpha = 0.25f) else Color.White
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "☀", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kecerahan",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (glass) Color.White else Color.Black
            )
        }
        Slider(
            value = value,
            onValueChange = { v ->
                value = v
                setSystemBrightness01(context, v)
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (!canWrite) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(
                    text = "Izinkan ubah pengaturan sistem buat mengaktifkan slider",
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MusicRow(glass: Boolean) {
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
    val containerColor =
        if (glass) Color.White.copy(alpha = 0.25f) else Color.White
    val textColor = if (glass) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (glass) 22.dp else 16.dp))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "♪", fontSize = 20.sp, color = textColor)
        Spacer(modifier = Modifier.width(10.dp))
        when {
            !granted -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Musik",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    Text(
                        text = "Butuh izin akses notifikasi buat membaca lagu yang diputar.",
                        fontSize = 11.sp,
                        color = if (glass) Color.White.copy(alpha = 0.75f)
                        else Color(0xFF6E6E73)
                    )
                }
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
                Text(
                    text = "Tidak ada musik yang diputar",
                    fontSize = 13.sp,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
            else -> {
                val metadata = controller.metadata
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: "Tidak diketahui"
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val playing = controller.playbackState?.state ==
                    android.media.session.PlaybackState.STATE_PLAYING
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            fontSize = 11.sp,
                            color = if (glass) Color.White.copy(alpha = 0.75f)
                            else Color(0xFF6E6E73),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "⏮",
                    fontSize = 18.sp,
                    color = textColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            controller.transportControls.skipToPrevious()
                        }
                        .padding(6.dp)
                )
                Text(
                    text = if (playing) "⏸" else "▶",
                    fontSize = 20.sp,
                    color = textColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (playing) controller.transportControls.pause()
                            else controller.transportControls.play()
                        }
                        .padding(6.dp)
                )
                Text(
                    text = "⏭",
                    fontSize = 18.sp,
                    color = textColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            controller.transportControls.skipToNext()
                        }
                        .padding(6.dp)
                )
            }
        }
    }
}
