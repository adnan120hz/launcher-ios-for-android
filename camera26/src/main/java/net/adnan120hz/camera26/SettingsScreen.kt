package net.adnan120hz.camera26

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsBg = Color(0xFF0B0B0D)
private val SettingsCard = Color(0xFF1C1C1F)

/**
 * In-app Settings & Info screen: camera preference(s), developer credit,
 * links and licensing — opened from the control sheet, per product spec.
 */
@Composable
fun CameraSettingsScreen(state: CameraState, onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Throwable) {
            null
        }
    } ?: "0.5.0"

    fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Throwable) {
            state.toast = "Tidak ada aplikasi untuk membuka tautan"
        }
    }

    Box(Modifier.fillMaxSize().background(SettingsBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    color = Color.White,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(end = 14.dp)
                )
                Text(
                    "Pengaturan & Info",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(22.dp))

            // ------------------------------------------------ kamera
            SettingsSectionTitle("KAMERA")
            SettingsCardBox {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Grid", color = Color.White, fontSize = 15.sp)
                        Text(
                            "Garis bantu komposisi 3×3 di viewfinder",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = state.gridOn,
                        onCheckedChange = {
                            state.gridOn = it
                            state.persistAll()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF3A3A3C)
                        )
                    )
                }
            }
            Text(
                "Resolusi & frame rate video diatur dari pill format di mode Video; " +
                    "mode dan kontrol yang tidak didukung perangkat tampil redup dan tidak dapat dipilih.",
                color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(24.dp))

            // ------------------------------------------------ kredit
            SettingsSectionTitle("KREDIT")
            SettingsCardBox {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "Developer Adnan.120hz",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dirancang & dibangun oleh Adnan.120hz.",
                        color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp
                    )
                }
                SettingsDivider()
                SettingsLinkRow("GitHub", "github.com/adnan120hz") {
                    openUrl("https://github.com/adnan120hz")
                }
                SettingsDivider()
                SettingsLinkRow("Website", "adnan120hz.vercel.app") {
                    openUrl("https://adnan120hz.vercel.app")
                }
                SettingsDivider()
                SettingsLinkRow("TikTok", "@adnan.120hz") {
                    openUrl("https://www.tiktok.com/@adnan.120hz?_r=1&_t=ZS-9AElXliY2Me")
                }
            }
            Spacer(Modifier.height(24.dp))

            // ------------------------------------------------ lisensi
            SettingsSectionTitle("LISENSI")
            SettingsCardBox {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "© Adnan.120hz. Seluruh ikon dan elemen antarmuka dalam aplikasi ini " +
                            "digambar ulang dari nol — bukan aset Apple. iOS dan iPhone adalah " +
                            "merek dagang Apple Inc.; aplikasi ini adalah karya independen dan " +
                            "tidak berafiliasi maupun didukung oleh Apple.",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Komponen open-source yang digunakan: AndroidX CameraX, Jetpack " +
                            "Compose dan Kotlin (Apache License 2.0), serta ML Kit Selfie " +
                            "Segmentation (Google). Lisensi masing-masing komponen berlaku " +
                            "untuk bagiannya.",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // ------------------------------------------------ versi
            SettingsSectionTitle("VERSI")
            SettingsCardBox {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kamera iOS", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(versionName, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCardBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsCard)
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun SettingsLinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text("  ›", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
    }
}
