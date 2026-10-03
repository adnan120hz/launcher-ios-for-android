package net.adnan120hz.launcher

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
    iconConfig: IconConfig,
    iconShape: IconShapeType,
    themeMode: ThemeMode,
    dockBlurDp: Float,
    dockBlurMax: Float,
    dockTintAlpha: Float,
    dockTintDark: Float,
    dynamicIslandUnlocked: Boolean,
    onIconStyleChange: (IconStyle) -> Unit,
    onIconShapeChange: (IconShapeType) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDockBlurChange: (Float) -> Unit,
    onDockTintAlphaChange: (Float) -> Unit,
    onDockTintDarkChange: (Float) -> Unit,
    onResetDockGlass: () -> Unit,
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
                        AppIconImage(
                            app = app,
                            sizeDp = 40.dp,
                            cfg = iconConfig
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
                        text = "Pack ikon gambar ulang buatan sendiri bergaya iOS 18 / iOS 26 buat aplikasi umum (Telepon, Kamera, Galeri, dll). Aplikasi lain tetap pakai ikon aslinya, dibingkai masker gaya iOS biar seragam.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Bentuk Ikon")
                    IconShapeType.entries.forEach { shapeType ->
                        RadioRow(
                            label = shapeType.label,
                            selected = iconShape == shapeType,
                            onClick = { onIconShapeChange(shapeType) }
                        )
                    }
                    Text(
                        text = "Berlaku ke semua ikon: pack gambar ulang, ikon asli berbingkai, dan ikon custom dari gambar kamu.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    SectionTitle("Varian Gelap (pack iOS 26)")
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = mode.label,
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) }
                        )
                    }
                    Text(
                        text = "\"Ikuti sistem\" mengikuti tema gelap/terang Android kamu. Varian gelap memakai palet ikon gelap ala iOS 26.",
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

                    SectionTitle("Kaca Dock")
                    Text(
                        text = "Pratinjau langsung — geser slider dan lihat dock berubah saat itu juga:",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DockGlassPreview(
                        glassEnabled = glassEnabled,
                        perfTier = perfTier,
                        iconStyle = iconStyle,
                        iconDark = iconConfig.dark,
                        iconShape = iconShape,
                        blurDp = dockBlurDp,
                        tintAlpha = dockTintAlpha,
                        tintDark = dockTintDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SliderRow(
                        label = "Blur kaca dock",
                        valueText = "${dockBlurDp.toInt()}dp (maks tier ini ${dockBlurMax.toInt()}dp)",
                        value = dockBlurDp,
                        valueRange = 0f..dockBlurMax,
                        enabled = glassEnabled,
                        onValueChange = { onDockBlurChange(it) }
                    )
                    SliderRow(
                        label = "Kekuatan tint putih kaca",
                        valueText = "${(dockTintAlpha * 100).toInt()}%",
                        value = dockTintAlpha,
                        valueRange = 0f..0.6f,
                        enabled = glassEnabled,
                        onValueChange = { onDockTintAlphaChange(it) }
                    )
                    SliderRow(
                        label = "Kegelapan tint dock",
                        valueText = "${(dockTintDark * 100).toInt()}%",
                        value = dockTintDark,
                        valueRange = 0f..1f,
                        enabled = glassEnabled,
                        onValueChange = { onDockTintDarkChange(it) }
                    )
                    Text(
                        text = "Berguna sesudah ganti wallpaper: atur blur & tint dock sampai serasi, berlaku LIVE. Batas blur mengikuti tier performa (Entry dibatasi biar tetap hemat & smooth).",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onResetDockGlass() }) {
                        Text("Reset kaca dock ke default tier")
                    }
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
                            AppIconImage(
                                app = entry,
                                sizeDp = 40.dp,
                                cfg = iconConfig
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

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Text(text = valueText, fontSize = 12.sp, color = Color(0xFF6E6E73))
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Live dock preview: the real glass renderer driven by the slider
 *  values, so the user sees the dock change while dragging. */
@Composable
private fun DockGlassPreview(
    glassEnabled: Boolean,
    perfTier: PerfTier,
    iconStyle: IconStyle,
    iconDark: Boolean,
    iconShape: IconShapeType,
    blurDp: Float,
    tintAlpha: Float,
    tintDark: Float
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(shape)
    ) {
        GlassBackground(
            shape = shape,
            glassEnabled = glassEnabled,
            tier = perfTier,
            screenHeight = 300.dp,
            bottomInset = 0.dp,
            modifier = Modifier.matchParentSize(),
            blurRadiusDp = blurDp,
            tintAlpha = tintAlpha,
            tintDarkness = tintDark
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                IconKind.PHONE,
                IconKind.MESSAGES,
                IconKind.CAMERA,
                IconKind.GALLERY
            ).forEach { kind ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(6.dp, iconShape.shape())
                        .clip(iconShape.shape())
                ) {
                    PackIcon(
                        kind = kind,
                        style = iconStyle,
                        dark = iconDark,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}
