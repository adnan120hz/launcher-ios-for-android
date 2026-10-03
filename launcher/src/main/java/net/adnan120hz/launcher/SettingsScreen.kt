package net.adnan120hz.launcher

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
    onIconStyleChange: (IconStyle) -> Unit,
    onGlassChange: (Boolean) -> Unit,
    onTierChange: (PerfTier) -> Unit,
    onDockChange: (List<String>) -> Unit,
    onResetDock: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

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
