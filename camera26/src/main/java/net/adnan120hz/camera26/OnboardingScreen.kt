package net.adnan120hz.camera26

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val LINK_TIKTOK =
    "https://www.tiktok.com/@adnan.120hz?_r=1&_t=ZS-9AElXliY2Me"
const val LINK_WEBSITE = "https://adnan120hz.vercel.app"
const val LINK_GITHUB = "https://github.com/adnan120hz"

/**
 * First-run introduction (shown once, re-openable from Settings):
 * who built this camera, the developer's links, and a start button.
 * Plain and honest — no fake gating, just an introduction.
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    fun openUrl(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Throwable) { /* no browser: ignore, intro still works */ }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(GlassPillBrush)
                    .border(1.dp, GlassRim, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CameraBodyGlyph(Color.White, Modifier.size(64.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Kamera iOS",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Kamera bergaya iOS untuk Android — dibangun dengan jujur " +
                    "mengikuti kemampuan nyata perangkatmu.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            Text(
                "DEVELOPER",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Adnan.120hz",
                color = IosYellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IntroLinkButton("TikTok", "@adnan.120hz") { openUrl(LINK_TIKTOK) }
                IntroLinkButton("Website", "adnan120hz.vercel.app") { openUrl(LINK_WEBSITE) }
                IntroLinkButton("GitHub", "github.com/adnan120hz") { openUrl(LINK_GITHUB) }
            }

            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(IosYellow)
                    .clickable { onStart() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Mulai",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Perkenalan ini hanya tampil sekali. Bisa dibuka lagi dari Pengaturan & Info.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IntroLinkButton(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1F))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Text(value, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text("  ›", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
    }
}
