package net.adnan120hz.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherApp()
        }
    }
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

// ---- Simple persistent flags (skeleton; moves to DataStore in a later phase) ----
private const val PREFS_NAME = "launcher_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"
private const val KEY_DYNAMIC_ISLAND_UNLOCKED = "dynamic_island_unlocked"

private fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return resolved
        .mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
            AppEntry(
                label = label,
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun Drawable.toBitmapSafe(size: Int = 96): Bitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else size
    val h = if (intrinsicHeight > 0) intrinsicHeight else size
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bitmap
}

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

@Composable
fun HomeScreen(dynamicIslandUnlocked: Boolean) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    val dockApps = apps.take(4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF8EC5FC), Color(0xFFE0C3FC))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 48.dp,
                    bottom = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(apps) { app ->
                    AppIconCell(app = app) {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage(app.packageName)
                        if (launch != null) context.startActivity(launch)
                    }
                }
            }

            if (!dynamicIslandUnlocked) {
                Text(
                    text = "Dynamic Island iOS 26 UI masih terkunci (follow TikTok buat buka)",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            // Dock (4 slots) — Liquid Glass style placeholder
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockApps.forEach { app ->
                    AppDockIcon(app = app) {
                        val launch = context.packageManager
                            .getLaunchIntentForPackage(app.packageName)
                        if (launch != null) context.startActivity(launch)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconCell(app: AppEntry, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmapSafe().asImageBitmap() }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            fontSize = 11.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppDockIcon(app: AppEntry, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toBitmapSafe().asImageBitmap() }
    Image(
        bitmap = iconBitmap,
        contentDescription = app.label,
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    )
}
