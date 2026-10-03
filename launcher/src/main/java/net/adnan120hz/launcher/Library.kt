package net.adnan120hz.launcher

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibraryScreen(
    apps: List<AppEntry>,
    iconStyle: IconStyle,
    shadowsEnabled: Boolean,
    dynamicIslandUnlocked: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val groups = remember(filtered) {
        filtered
            .groupBy { app ->
                app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }
            .toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 48.dp)
    ) {
        Text(
            text = "App Library",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Launcher settings entry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.25f))
                .clickable { onOpenSettings() }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pengaturan Launcher",
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(text = "›", fontSize = 18.sp, color = Color.White)
        }

        if (!dynamicIslandUnlocked) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Dynamic Island iOS 26 UI masih terkunci (follow TikTok developer buat membukanya — lihat onboarding).",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search field — filters the list below in real time
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.85f))
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari aplikasi") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            when {
                apps.isEmpty() -> item {
                    Text(
                        text = "Memuat aplikasi…",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                filtered.isEmpty() -> item {
                    Text(
                        text = "Tidak ada aplikasi yang cocok.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> groups.forEach { (letter, groupApps) ->
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = letter,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    items(groupApps) { app ->
                        var rect by remember(app.packageName) {
                            mutableStateOf<Rect?>(null)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    launchApp(context, app.packageName, rootView, rect)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                app = app,
                                sizeDp = 44.dp,
                                iconStyle = iconStyle,
                                shadowsEnabled = shadowsEnabled,
                                onBounds = { rect = it }
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            Text(
                                text = app.label,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
