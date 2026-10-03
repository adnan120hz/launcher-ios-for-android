package net.adnan120hz.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherApp()
        }
    }
}

// ---- Simple persistent flags (skeleton; moves to DataStore in a later phase) ----
private const val PREFS_NAME = "launcher_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"
private const val KEY_DYNAMIC_ISLAND_UNLOCKED = "dynamic_island_unlocked"

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var onboarded by remember {
        mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    }
    var dynamicIslandUnlocked by remember {
        mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_ISLAND_UNLOCKED, false))
    }

    if (!onboarded) {
        OnboardingFlow(
            onFinish = {
                prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
                onboarded = true
            },
            onConfirmFollowed = {
                prefs.edit().putBoolean(KEY_DYNAMIC_ISLAND_UNLOCKED, true).apply()
                dynamicIslandUnlocked = true
            }
        )
    } else {
        HomeScreen(dynamicIslandUnlocked = dynamicIslandUnlocked)
    }
}

private const val TIKTOK_URL =
    "https://www.tiktok.com/@adnan.120hz?_r=1&_t=ZS-9AFVyGwfDCI"

// Multi-step onboarding skeleton: intro -> developer credit -> follow gate.
// Layout standard: consistent 24.dp page padding, 16.dp spacing everywhere.
@Composable
fun OnboardingFlow(
    onFinish: () -> Unit,
    onConfirmFollowed: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF5EA9FF), Color(0xFFDCEEFF))
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> {
                    Text("iOS Launcher", fontSize = 34.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "An iOS-style home screen for Android.\nNo ads. Ever.",
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { step = 1 }) {
                        Text("Mulai")
                    }
                }
                1 -> {
                    Text("Developer", fontSize = 26.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Dibuat oleh",
                        fontSize = 15.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Adnan.120hz",
                        fontSize = 22.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { step = 2 }) {
                        Text("Lanjut")
                    }
                }
                else -> {
                    // Follow gate placeholder: the special feature
                    // "Dynamic Island iOS 26 UI" stays locked until the user
                    // confirms (self-declared) that they followed the TikTok
                    // account. Real follow state can NOT be verified
                    // automatically; this is an honor-system confirmation.
                    Text(
                        "Fitur Khusus Terkunci",
                        fontSize = 24.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Dynamic Island iOS 26 UI",
                        fontSize = 18.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Follow dulu TikTok developer buat membuka fitur ini.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TIKTOK_URL))
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Buka TikTok & Follow")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            onConfirmFollowed()
                            onFinish()
                        }
                    ) {
                        Text("Saya sudah follow", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Lewati",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFinish() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
