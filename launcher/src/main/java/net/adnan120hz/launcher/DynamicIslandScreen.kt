package net.adnan120hz.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated Dynamic Island page (0.8.0). Until now the island controls
 * were buried inside a long settings card, the service was a plain
 * background service the system killed, and the user had no idea why
 * "dynamic island belum berfungsi". This page is the island's home:
 * live preview drawn by the exact overlay renderer, honest status with
 * reasons, permission shortcuts, a visible test button, and the
 * user-tunable geometry sliders (kept, per user decision).
 *
 * The follow-gate is untouched: locked until the user confirms the
 * TikTok follow (honor system — a real follow cannot be verified).
 * Everything the island displays stays real-data-only.
 */
@Composable
fun DynamicIslandScreen(
    unlocked: Boolean,
    onUnlock: () -> Unit,
    islandEnabled: Boolean,
    onIslandEnabledChange: (Boolean) -> Unit,
    islandScale: Float,
    islandWidthFactor: Float,
    islandOffsetXDp: Float,
    islandOffsetYDp: Float,
    onIslandGeometryChange: (Float, Float, Float, Float) -> Unit,
    onResetIslandGeometry: () -> Unit,
    glassEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    var tick by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }
    val key = tick
    val overlayGranted = remember(key) { Settings.canDrawOverlays(context) }
    val batteryIgnored = remember(key) {
        IslandState.isBatteryOptimizationIgnored(context)
    }
    val notifListenerGranted = remember(key) {
        notificationListenerGranted(context)
    }
    val serviceRunning = IslandState.running
    val windowAttached = IslandState.windowAttached
    val lastError = IslandState.lastError
    val glassStyle = remember(key) { store.islandGlassStyleIs26() }
    val nowText = remember(key) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    val previewCharging = IslandState.chargingPct

    val statusText = when {
        !unlocked -> "Terkunci — fitur khusus (lihat di bawah)"
        !islandEnabled -> "Mati (saklar island di halaman ini)"
        !overlayGranted -> "Izin overlay belum diberikan — island tidak bisa menggambar"
        serviceRunning && windowAttached ->
            "Aktif ✓ — pill tampil di atas layar"
        serviceRunning && lastError == "START_REFUSED" ->
            "Service jalan tapi jendela ditolak sistem (START_REFUSED) — coba matikan lalu aktifkan lagi"
        serviceRunning ->
            "Service jalan — jendela belum menempel (tunggu sebentar / tes di bawah)"
        lastError == "START_REFUSED" ->
            "Start ditolak sistem (START_REFUSED) — buka halaman ini lalu ketuk \"Tampilkan island sekarang\""
        lastError == "NO_OVERLAY" ->
            "Izin overlay dicabut — berikan lagi lewat tombol di bawah"
        else -> "Belum jalan — ketuk \"Tampilkan island sekarang\""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (glassEnabled) Modifier
                else Modifier.background(Color(0xFFF2F2F7))
            )
    ) {
        if (glassEnabled) {
            WallpaperBackground(Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "‹ Kembali",
                    fontSize = 16.sp,
                    color = Color(0xFF007AFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBack() }
                        .padding(6.dp)
                )
                Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                Text(
                    text = "Dynamic Island",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(glassEnabled) {
                SectionTitle("Pratinjau langsung")
                // The exact overlay pill renderer, fed live data. The
                // strip below previews every state with the same
                // renderer at the tuned geometry — what you see here is
                // literally what the overlay draws.
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IslandPill()
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Semua wujud pill (renderer yang sama persis dengan overlay):",
                    fontSize = 12.sp,
                    color = Color(0xFF6E6E73)
                )
                Spacer(modifier = Modifier.height(10.dp))
                IslandMode.CLOCK.let {
                    IslandPillContent(
                        mode = it, scale = islandScale,
                        widthFactor = islandWidthFactor, glass = glassStyle,
                        timeText = nowText, chargingPct = previewCharging,
                        timerLeftSec = 0,
                        musicTitle = null, musicArtist = null,
                        musicPlaying = false
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                IslandPillContent(
                    mode = IslandMode.CHARGING, scale = islandScale,
                    widthFactor = islandWidthFactor, glass = glassStyle,
                    timeText = nowText,
                    chargingPct = if (previewCharging >= 0) previewCharging else 82,
                    timerLeftSec = 0,
                    musicTitle = null, musicArtist = null,
                    musicPlaying = false
                )
                Spacer(modifier = Modifier.height(10.dp))
                IslandPillContent(
                    mode = IslandMode.TIMER, scale = islandScale,
                    widthFactor = islandWidthFactor, glass = glassStyle,
                    timeText = nowText, chargingPct = previewCharging,
                    timerLeftSec = 60,
                    musicTitle = null, musicArtist = null,
                    musicPlaying = false
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pratinjau wujud saja — angka charging/timer di strip ini contoh tampilan. Overlay aslinya HANYA memakai data nyata HP kamu.",
                    fontSize = 11.sp,
                    color = Color(0xFF6E6E73)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(glassEnabled) {
                SectionTitle("Status")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Island aktif",
                        fontSize = 15.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = islandEnabled && unlocked,
                        enabled = unlocked,
                        onCheckedChange = { onIslandEnabledChange(it) }
                    )
                }
                Text(
                    text = "Status: $statusText",
                    fontSize = 14.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(10.dp))
                StatusRow(
                    label = "Izin tampil di atas aplikasi lain (overlay)",
                    ok = overlayGranted,
                    okText = "sudah diberikan",
                    badText = "belum diberikan"
                )
                StatusRow(
                    label = "Pengecualian baterai (anti dimatikan sistem)",
                    ok = batteryIgnored,
                    okText = "sudah dikecualikan",
                    badText = "belum — island bisa dimatikan HP saat hemat daya"
                )
                StatusRow(
                    label = "Akses notifikasi (buat state musik)",
                    ok = notifListenerGranted,
                    okText = "sudah diberikan",
                    badText = "belum — state musik tidak ditampilkan (bukan bug)"
                )

                if (!unlocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🔒 Terkunci — fitur khusus Dynamic Island iOS 26 UI.",
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(TIKTOK_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) {
                        Text("Buka TikTok & Follow")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onUnlock() }) {
                        Text("Saya sudah follow — buka fitur")
                    }
                    Text(
                        text = "Follow tidak bisa diverifikasi otomatis; ini konfirmasi mandiri (sistem kepercayaan), sama seperti onboarding.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        OutlinedButton(
                            enabled = islandEnabled,
                            onClick = {
                                if (!overlayGranted) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    val ok = IslandService.start(
                                        context,
                                        IslandService.ACTION_SHOW_NOW
                                    )
                                    if (ok) countdown = 12
                                }
                                tick++
                            }
                        ) {
                            Text(
                                if (countdown > 0) "Island tampil… ${countdown}s"
                                else "Tampilkan island sekarang"
                            )
                        }
                    }
                    LaunchedEffect(countdown) {
                        if (countdown > 0) {
                            delay(1000)
                            countdown--
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            val ok = IslandService.start(
                                context,
                                IslandService.ACTION_TIMER
                            )
                            if (ok) countdown = 12
                            tick++
                        }) {
                            Text("Tes timer 1 menit")
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                        OutlinedButton(onClick = {
                            IslandService.stop(context)
                            tick++
                        }) {
                            Text("Matikan island")
                        }
                    }
                    if (!overlayGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) {
                            Text("Izinkan overlay")
                        }
                    }
                    if (!batteryIgnored) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (e2: Exception) {
                                    // no battery settings screen on this device
                                }
                            }
                            tick++
                        }) {
                            Text("Kecualikan dari optimasi baterai")
                        }
                        Text(
                            text = "Jujur saja: tanpa pengecualian ini, sebagian HP (terutama yang agresif hemat daya) bisa mematikan island setelah beberapa menit. Dengan pengecualian, island jalan sebagai foreground service dan jauh lebih awet — tetap tidak ada jaminan 100% di semua merk.",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { tick++ }) {
                        Text("Segarkan status")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(glassEnabled) {
                SectionTitle("Atur Dynamic Island (berlaku langsung)")
                SliderRow(
                    label = "Ukuran island",
                    valueText = "${(islandScale * 100).toInt()}%",
                    value = islandScale,
                    valueRange = 0.8f..1.3f,
                    enabled = unlocked,
                    onValueChange = {
                        onIslandGeometryChange(
                            it, islandWidthFactor,
                            islandOffsetXDp, islandOffsetYDp
                        )
                    }
                )
                SliderRow(
                    label = "Lebar island",
                    valueText = "${(islandWidthFactor * 100).toInt()}%",
                    value = islandWidthFactor,
                    valueRange = 0.7f..1.6f,
                    enabled = unlocked,
                    onValueChange = {
                        onIslandGeometryChange(
                            islandScale, it,
                            islandOffsetXDp, islandOffsetYDp
                        )
                    }
                )
                SliderRow(
                    label = "Geser kiri–kanan",
                    valueText = "${islandOffsetXDp.toInt()}dp",
                    value = islandOffsetXDp,
                    valueRange = -140f..140f,
                    enabled = unlocked,
                    onValueChange = {
                        onIslandGeometryChange(
                            islandScale, islandWidthFactor,
                            it, islandOffsetYDp
                        )
                    }
                )
                SliderRow(
                    label = "Geser atas–bawah",
                    valueText = "${islandOffsetYDp.toInt()}dp",
                    value = islandOffsetYDp,
                    valueRange = 0f..96f,
                    enabled = unlocked,
                    onValueChange = {
                        onIslandGeometryChange(
                            islandScale, islandWidthFactor,
                            islandOffsetXDp, it
                        )
                    }
                )
                OutlinedButton(onClick = { onResetIslandGeometry() }) {
                    Text("Reset posisi island (tengah-atas ala iOS)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gaya island mengikuti aturan global: gaya iOS 26 terpilih + Liquid Glass menyala = island kaca; selain itu island solid ala iOS 18. Island menampilkan DATA NYATA saja: jam, charging + persen baterai asli, timer berjalan, dan lagu yang diputar (judul + artis + play/pause — ketuk pill-nya).",
                    fontSize = 12.sp,
                    color = Color(0xFF6E6E73)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (ok) "✓" else "•",
            fontSize = 14.sp,
            color = if (ok) Color(0xFF1E7D32) else Color(0xFFB8860B),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = "$label: " + if (ok) okText else badText,
            fontSize = 13.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
    }
}
