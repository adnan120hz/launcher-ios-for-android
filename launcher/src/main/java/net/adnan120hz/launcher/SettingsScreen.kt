package net.adnan120hz.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    apps: List<AppEntry>,
    iconStyle: IconStyle,
    glassEnabled: Boolean,
    perfTier: PerfTier,
    dockPackages: List<String>,
    ccStyle: CcStyle,
    hideSettingsInLibrary: Boolean,
    dynamicIslandUnlocked: Boolean,
    onIconStyleChange: (IconStyle) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onTierChange: (PerfTier) -> Unit,
    onDockChange: (List<String>) -> Unit,
    onResetDock: () -> Unit,
    onCcStyleChange: (CcStyle) -> Unit,
    onHideSettingsChange: (Boolean) -> Unit,
    onDynamicIslandUnlock: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pickerSlot by remember { mutableStateOf<Int?>(null) }
    var permTick by remember { mutableIntStateOf(0) }
    val overlayGranted = remember(permTick) {
        Settings.canDrawOverlays(context)
    }
    val islandRunning = remember(permTick) { IslandState.running }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ Kembali",
                fontSize = 16.sp,
                color = Color(0xFF007AFF),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (pickerSlot != null) pickerSlot = null else onBack()
                    }
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                text = if (pickerSlot != null) "Pilih Aplikasi Dock"
                else "Pengaturan Launcher",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        val slot = pickerSlot
        if (slot != null) {
            Text(
                text = "Ketuk aplikasi buat mengisi Slot ${slot + 1}:",
                fontSize = 13.sp,
                color = Color(0xFF6E6E73)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(apps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val updated = dockPackages.toMutableList()
                                while (updated.size < 4) updated.add("")
                                updated[slot] = app.packageName
                                onDockChange(updated.filter { it.isNotBlank() })
                                pickerSlot = null
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bmp = remember(app.packageName) {
                            app.icon.toBitmapSafe().asImageBitmap()
                        }
                        Image(
                            bitmap = bmp,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                        Text(text = app.label, fontSize = 15.sp, color = Color.Black)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    SectionTitle("Gaya Ikon")
                    IconStyle.entries.forEach { style ->
                        RadioRow(
                            label = style.label,
                            selected = iconStyle == style,
                            onClick = { onIconStyleChange(style) }
                        )
                    }
                    Text(
                        text = "Baru memengaruhi radius sudut & bayangan ikon; pack ikon gambar ulang menyusul di fase berikutnya.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Liquid Glass")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Efek kaca di dock & elemen glass",
                            fontSize = 15.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = glassEnabled,
                            onCheckedChange = { onGlassChange(it) }
                        )
                    }
                    Text(
                        text = "Mati = gaya solid ala iOS 18, lebih hemat baterai.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Performa")
                    PerfTier.entries.forEach { tier ->
                        RadioRow(
                            label = "${tier.label} — radius blur ${tier.blurRadiusDp.toInt()}dp",
                            selected = perfTier == tier,
                            onClick = { onTierChange(tier) }
                        )
                    }
                    Text(
                        text = "Entry mengurangi blur & mematikan bayangan ikon supaya tetap smooth di HP entry-level.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Aplikasi Dock")
                }
                items(4) { slotIndex ->
                    val pkg = dockPackages.getOrNull(slotIndex)
                    val entry = remember(pkg, apps) {
                        pkg?.let {
                            apps.firstOrNull { a -> a.packageName == it }
                                ?: loadAppEntry(context, it)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { pickerSlot = slotIndex }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry != null) {
                            val bmp = remember(entry.packageName) {
                                entry.icon.toBitmapSafe().asImageBitmap()
                            }
                            Image(
                                bitmap = bmp,
                                contentDescription = entry.label,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            Text(
                                text = "Slot ${slotIndex + 1}: ${entry.label}",
                                fontSize = 15.sp,
                                color = Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = "Slot ${slotIndex + 1}: (kosong — ketuk buat memilih)",
                                fontSize = 15.sp,
                                color = Color(0xFF6E6E73),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(text = "›", fontSize = 18.sp, color = Color(0xFF6E6E73))
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Control Center")
                    CcStyle.entries.forEach { style ->
                        RadioRow(
                            label = style.label,
                            selected = ccStyle == style,
                            onClick = { onCcStyleChange(style) }
                        )
                    }
                    Text(
                        text = if (!glassEnabled)
                            "Liquid Glass mati, jadi Control Center otomatis pakai gaya iOS 18."
                        else
                            "Buka dengan geser dari tepi atas home screen, atau tombol di bawah.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onOpenControlCenter() }) {
                        Text("Buka Control Center")
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("App Library")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sembunyikan Settings bawaan dari App Library",
                            fontSize = 15.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = hideSettingsInLibrary,
                            onCheckedChange = { onHideSettingsChange(it) }
                        )
                    }
                    Text(
                        text = "Cuma menyembunyikan ikon Settings bawaan HP dari daftar App Library (saat Control Center launcher dipakai). Aplikasi Settings-nya sendiri tidak diutak-atik.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Dynamic Island")
                    if (!dynamicIslandUnlocked) {
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
                        OutlinedButton(onClick = { onDynamicIslandUnlock() }) {
                            Text("Saya sudah follow — buka fitur")
                        }
                        Text(
                            text = "Follow tidak bisa diverifikasi otomatis; ini konfirmasi mandiri (sistem kepercayaan), sama seperti onboarding.",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    } else {
                        Text(
                            text = "Izin tampil di atas aplikasi lain (overlay): " +
                                if (overlayGranted) "sudah diberikan ✓"
                                else "belum diberikan",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!overlayGranted) {
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "Status Dynamic Island: " +
                                if (islandRunning) "aktif" else "mati",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = {
                                if (overlayGranted) {
                                    context.startService(
                                        Intent(context, IslandService::class.java)
                                    )
                                } else {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                permTick++
                            }) {
                                Text("Aktifkan Island")
                            }
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            OutlinedButton(onClick = {
                                context.stopService(
                                    Intent(context, IslandService::class.java)
                                )
                                permTick++
                            }) {
                                Text("Matikan Island")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            context.startService(
                                Intent(context, IslandService::class.java)
                                    .setAction(IslandService.ACTION_TIMER)
                            )
                            permTick++
                        }) {
                            Text("Tes timer 1 menit di Island")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { permTick++ }) {
                            Text("Segarkan status izin")
                        }
                        Text(
                            text = "Island menampilkan jam, status charging, timer, dan lagu yang diputar (lagu butuh izin akses notifikasi dari baris Musik di Control Center).",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { onResetDock() }) {
                        Text("Reset Dock ke Default")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "iOS Launcher for Android — Developer: Adnan.120hz",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF6E6E73),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(text = label, fontSize = 15.sp, color = Color.Black)
    }
}
