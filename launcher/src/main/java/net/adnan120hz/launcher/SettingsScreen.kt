package net.adnan120hz.launcher

import android.content.Intent
import android.net.Uri
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
    rawIconStyle: IconStyle,
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
    animStyle: AnimStyle,
    onAnimStyleChange: (AnimStyle) -> Unit,
    updateVersion: String?,
    updateReleaseUrl: String,
    appVersion: String,
    updateChecksEnabled: Boolean,
    lastUpdateCheckMs: Long,
    onUpdateChecksChange: (Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    lockEnabled: Boolean,
    lockPrefs: LockPrefs,
    lockGlassStyleIs26: Boolean,
    onLockEnabledChange: (Boolean) -> Unit,
    onLockPrefsChange: (LockPrefs) -> Unit,
    onLockNow: () -> Unit,
    islandScale: Float,
    islandWidthFactor: Float,
    islandOffsetXDp: Float,
    islandOffsetYDp: Float,
    onIslandGeometryChange: (Float, Float, Float, Float) -> Unit,
    onResetIslandGeometry: () -> Unit,
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

    // 0.7.0 app-UI surface rule: Liquid Glass ON -> frosted glass plates
    // over the wallpaper gradient; OFF -> flat solid iOS 18 settings.
    Box(modifier = Modifier.fillMaxSize()) {
        if (glassEnabled) {
            WallpaperBackground(Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (glassEnabled) Modifier
                else Modifier.background(Color(0xFFF2F2F7))
            )
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
            if (updateVersion != null) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF3B30))
                )
            }
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
                if (updateVersion != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF007AFF))
                                .clickable {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(updateReleaseUrl)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = "Versi $updateVersion tersedia — " +
                                    "ketuk untuk memperbarui",
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = "›", fontSize = 18.sp, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                item {
                    SettingsCard(glassEnabled) {
                    SectionTitle("Gaya Ikon")
                    IconStyle.entries.forEach { style ->
                        RadioRow(
                            label = style.label,
                            selected = rawIconStyle == style,
                            onClick = { onIconStyleChange(style) }
                        )
                    }
                    Text(
                        text = "Pack ikon gambar ulang buatan sendiri bergaya iOS 18 / iOS 26 buat aplikasi umum (Telepon, Kamera, Galeri, dll). Aplikasi lain tetap pakai ikon aslinya, dibingkai masker gaya iOS biar seragam.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    if (!glassEnabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Catatan aturan gaya: Liquid Glass sedang MATI, jadi pack yang tampil adalah iOS 18 solid (kaca tidak pernah muncul di mode iOS 18, dan iOS 26 selalu dengan kaca menyala). Nyalakan Liquid Glass di bawah buat mengaktifkan tampilan iOS 26.",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsCard(glassEnabled) {
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

                    }
                    SettingsCard(glassEnabled) {
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

                    }
                    SettingsCard(glassEnabled) {
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

                    }
                    SettingsCard(glassEnabled) {
                    SectionTitle("Gaya Animasi Aplikasi")
                    AnimStyle.entries.forEach { style ->
                        RadioRow(
                            label = style.label,
                            selected = animStyle == style,
                            onClick = { onAnimStyleChange(style) }
                        )
                    }
                    Text(
                        text = "iOS 18 = zoom klasik ikon jadi jendela. iOS 26 (fluid) = zoom yang sama tapi digerakkan fisika pegas, ikon yang ditekan memantul halus, dan home screen nge-blur progresif di belakang aplikasi yang membuka (blur GPU asli di Android 12+; Android 10–11 cukup fade halus). Radius blur ngikutin tier Performa biar frame tetap mulus — kelancaran selalu didahulukan daripada efek.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    }
                    SettingsCard(glassEnabled) {
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

                    }
                    SettingsCard(glassEnabled) {
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

                    }
                    SettingsCard(glassEnabled) {
                    SectionTitle("Aplikasi Dock")
                    }
                }
                items(4) { slotIndex ->
                    SettingsCard(glassEnabled) {
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
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsCard(glassEnabled) {
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
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsCard(glassEnabled) {
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
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsCard(glassEnabled) {
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
                            text = "Island menampilkan DATA NYATA saja: jam, charging + persen baterai asli, timer berjalan, dan lagu yang diputar (judul + artis + play/pause — ketuk pill-nya). Kalau izin akses notifikasi belum diberikan, state musik tidak ditampilkan sama sekali (tidak ada state palsu).",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Atur Dynamic Island (berlaku langsung saat digeser):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Live preview of the pill at the chosen geometry.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = (150.dp * islandWidthFactor * islandScale)
                                            .coerceAtMost(330.dp),
                                        height = (37.dp * islandScale)
                                            .coerceAtMost(56.dp)
                                    )
                                    .offset(x = (islandOffsetXDp * 0.25f).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "21:41",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SliderRow(
                            label = "Ukuran island",
                            valueText = "${(islandScale * 100).toInt()}%",
                            value = islandScale,
                            valueRange = 0.8f..1.3f,
                            enabled = true,
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
                            enabled = true,
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
                            enabled = true,
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
                            enabled = true,
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
                        Text(
                            text = "Gaya island mengikuti aturan global: gaya iOS 26 terpilih + Liquid Glass menyala = island kaca; selain itu island solid ala iOS 18.",
                            fontSize = 12.sp,
                            color = Color(0xFF6E6E73)
                        )
                    }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    LockSettingsBlock(
                        lockEnabled = lockEnabled,
                        lockPrefs = lockPrefs,
                        lockGlassStyleIs26 = lockGlassStyleIs26,
                        onLockEnabledChange = onLockEnabledChange,
                        onLockPrefsChange = onLockPrefsChange,
                        onLockNow = onLockNow
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsCard(glassEnabled) {
                    SectionTitle("Pembaruan Aplikasi")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cek versi terbaru otomatis",
                            fontSize = 15.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = updateChecksEnabled,
                            onCheckedChange = { onUpdateChecksChange(it) }
                        )
                    }
                    Text(
                        text = "Paling sering sekali sehari saat launcher dibuka, diam-diam ngecek rilis terbaru di GitHub. Kalau ada versi lebih baru, muncul baner + titik penanda di Pengaturan ini. Tanpa iklan, tanpa pelacakan.",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Versi terpasang: $appVersion" +
                            if (updateVersion != null)
                                " — versi $updateVersion sudah tersedia di atas"
                            else "",
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lastUpdateCheckMs > 0L)
                            "Terakhir dicek: " + java.text.SimpleDateFormat(
                                "dd MMM yyyy, HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(lastUpdateCheckMs))
                        else
                            "Terakhir dicek: belum pernah",
                        fontSize = 12.sp,
                        color = Color(0xFF6E6E73)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onCheckUpdatesNow() }) {
                        Text("Cek sekarang")
                    }
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
}

/** Frosted-glass group card (0.7.0): Liquid Glass ON -> translucent
 *  white plate with bright rim over the wallpaper gradient; OFF -> the
 *  classic solid white iOS 18 settings group. Text inside stays dark,
 *  readable on both. */
@Composable
internal fun SettingsCard(
    glass: Boolean,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (glass) Color.White.copy(alpha = 0.78f)
                else Color.White
            )
            .then(
                if (glass) {
                    Modifier.border(
                        1.dp, Color.White.copy(alpha = 0.55f), shape
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        content()
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

/** Phase 4 — Lock screen settings: enable, permissions, live preview,
 *  and the full clock/glass customization. All changes persist through
 *  Home (LauncherStore) and preview live as the controls move. */
@Composable
private fun LockSettingsBlock(
    lockEnabled: Boolean,
    lockPrefs: LockPrefs,
    lockGlassStyleIs26: Boolean,
    onLockEnabledChange: (Boolean) -> Unit,
    onLockPrefsChange: (LockPrefs) -> Unit,
    onLockNow: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val overlayGranted = remember(tick) { Settings.canDrawOverlays(context) }
    val serviceRunning = remember(tick) { LockScreenRuntime.serviceRunning }
    val notifGranted = remember(tick) {
        notificationListenerGranted(context)
    }
    val bioAvailable = remember(tick) { biometricAvailable(context) }

    SectionTitle("Layar Kunci iOS")
    Text(
        text = "Batas jujur Android: aplikasi pihak ketiga TIDAK bisa " +
            "mengganti lockscreen sistem. Layar ini adalah LAPISAN " +
            "TAMPILAN gaya iOS yang muncul SESUDAH kunci bawaan HP " +
            "(PIN/pola/sidik jari sistem) terbuka — kunci keamanan " +
            "Android kamu tetap yang utama dan tidak dilemahkan.",
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Aktifkan layar kunci iOS",
            fontSize = 15.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = lockEnabled,
            onCheckedChange = { onLockEnabledChange(it); tick++ }
        )
    }
    Text(
        text = "Layanan: " + if (serviceRunning) "aktif" else "mati" +
            " · Izin tampil di atas aplikasi lain: " +
            if (overlayGranted) "sudah diberikan ✓" else "belum diberikan",
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row {
        if (!overlayGranted) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                tick++
            }) {
                Text("Izinkan overlay")
            }
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        }
        OutlinedButton(onClick = { onLockNow() }) {
            Text("Kunci sekarang")
        }
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        OutlinedButton(onClick = { tick++ }) {
            Text("Segarkan status")
        }
    }
    Text(
        text = "Tanpa izin overlay, layar kunci tampil sebagai lapisan " +
            "di dalam launcher (tetap muncul sesudah layar mati & dibuka).",
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Notifikasi di layar kunci: " +
            if (notifGranted) "izin akses sudah diberikan ✓"
            else "izin akses belum diberikan",
        fontSize = 14.sp,
        color = Color.Black
    )
    if (!notifGranted) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            tick++
        }) {
            Text("Izinkan akses notifikasi")
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Pratinjau langsung — semua slider di bawah mengubahnya saat itu juga:",
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
    Spacer(modifier = Modifier.height(8.dp))
    LockPreview(cfg = lockPrefs, glassStyleIs26 = lockGlassStyleIs26)
    Spacer(modifier = Modifier.height(16.dp))

    SliderRow(
        label = "Ukuran jam",
        valueText = "${(lockPrefs.clockScale * 100).toInt()}%",
        value = lockPrefs.clockScale,
        valueRange = 0.7f..1.4f,
        enabled = true,
        onValueChange = {
            onLockPrefsChange(lockPrefs.copy(clockScale = it))
        }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Jam diperpanjang (lebih besar & lebar)",
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = lockPrefs.clockExtended,
            onCheckedChange = {
                onLockPrefsChange(lockPrefs.copy(clockExtended = it))
            }
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Warna jam",
        fontSize = 14.sp,
        color = Color.Black
    )
    Spacer(modifier = Modifier.height(8.dp))
    LockColorEditor(
        colorArgb = lockPrefs.clockColorArgb,
        onColorChange = {
            onLockPrefsChange(lockPrefs.copy(clockColorArgb = it))
        }
    )
    Spacer(modifier = Modifier.height(8.dp))

    SliderRow(
        label = "Ketebalan font jam",
        valueText = "${lockPrefs.clockWeight}",
        value = lockPrefs.clockWeight.toFloat(),
        valueRange = 200f..900f,
        enabled = true,
        onValueChange = {
            val snapped = (it / 100).toInt() * 100
            onLockPrefsChange(lockPrefs.copy(clockWeight = snapped))
        }
    )
    SliderRow(
        label = "Blur wallpaper di belakang jam",
        valueText = "${lockPrefs.wallpaperBlurDp.toInt()}dp (dibatasi tier performa)",
        value = lockPrefs.wallpaperBlurDp,
        valueRange = 0f..40f,
        enabled = true,
        onValueChange = {
            onLockPrefsChange(lockPrefs.copy(wallpaperBlurDp = it))
        }
    )
    SliderRow(
        label = "Intensitas Liquid Glass elemen kunci",
        valueText = "${(lockPrefs.glassIntensity * 100).toInt()}%",
        value = lockPrefs.glassIntensity,
        valueRange = 0f..1f,
        enabled = lockGlassStyleIs26,
        onValueChange = {
            onLockPrefsChange(lockPrefs.copy(glassIntensity = it))
        }
    )
    Text(
        text = if (lockGlassStyleIs26) {
            "Gaya elemen kunci mengikuti gaya global (iOS 26 Liquid Glass)."
        } else {
            "Gaya kunci sekarang solid ala iOS 18 (Liquid Glass mati atau gaya iOS 18 dipilih) — slider kaca nonaktif."
        },
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Buka dengan sidik jari/wajah",
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = lockPrefs.useBiometric,
            onCheckedChange = {
                onLockPrefsChange(lockPrefs.copy(useBiometric = it))
            }
        )
    }
    Text(
        text = if (bioAvailable) {
            "Geser ke atas di layar kunci akan meminta sidik jari/wajah. Biometrik ini hanya membuka lapisan tampilan, bukan pengganti kunci sistem."
        } else {
            "HP ini belum ada sidik jari/wajah yang terdaftar — membuka kunci cukup geser ke atas."
        },
        fontSize = 12.sp,
        color = Color(0xFF6E6E73)
    )
}

/** Clock color picker: preset swatches + a simple HSV slider trio. */
@Composable
private fun LockColorEditor(
    colorArgb: Int,
    onColorChange: (Int) -> Unit
) {
    val swatches = listOf(
        "Putih" to 0xFFFFFFFF.toInt(),
        "Hitam" to 0xFF111111.toInt(),
        "Kuning" to 0xFFFFD60A.toInt(),
        "Biru" to 0xFF0A84FF.toInt(),
        "Hijau" to 0xFF30D158.toInt(),
        "Pink" to 0xFFFF6482.toInt(),
        "Ungu" to 0xFFBF5AF2.toInt()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatches.forEach { (_, argb) ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(argb))
                    .border(
                        width = if (argb == colorArgb) 3.dp else 1.dp,
                        color = if (argb == colorArgb) {
                            Color(0xFF007AFF)
                        } else {
                            Color.Black.copy(alpha = 0.25f)
                        },
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { onColorChange(argb) }
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))

    val baseHsv = remember(colorArgb) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(colorArgb, it)
        }
    }
    var hue by remember(colorArgb) { mutableStateOf(baseHsv[0]) }
    var sat by remember(colorArgb) { mutableStateOf(baseHsv[1]) }
    var value by remember(colorArgb) { mutableStateOf(baseHsv[2]) }
    fun applyHsv() {
        onColorChange(
            android.graphics.Color.HSVToColor(
                floatArrayOf(hue, sat, value)
            )
        )
    }
    SliderRow(
        label = "Hue",
        valueText = "${hue.toInt()}°",
        value = hue,
        valueRange = 0f..360f,
        enabled = true,
        onValueChange = { hue = it; applyHsv() }
    )
    SliderRow(
        label = "Saturasi",
        valueText = "${(sat * 100).toInt()}%",
        value = sat,
        valueRange = 0f..1f,
        enabled = true,
        onValueChange = { sat = it; applyHsv() }
    )
    SliderRow(
        label = "Terang",
        valueText = "${(value * 100).toInt()}%",
        value = value,
        valueRange = 0f..1f,
        enabled = true,
        onValueChange = { value = it; applyHsv() }
    )
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
