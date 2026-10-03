package net.adnan120hz.launcher

import android.graphics.Rect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
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
    cfg: IconConfig,
    dynamicIslandUnlocked: Boolean,
    updateAvailable: Boolean = false,
    onOpenSettings: () -> Unit,
    onIconLongPress: (AppEntry) -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    var query by remember { mutableStateOf("") }
    // 0.8.0: the keyboard must open ONLY when the user taps the search
    // field itself. A normally-focusable TextField inside a launcher
    // page grabbed window focus whenever the page attached, popping
    // the keyboard unasked. The field stays unfocused until tapped;
    // tapping flips it into the real, focusable editor.
    var searchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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
            if (updateAvailable) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF3B30))
                )
                Spacer(modifier = Modifier.size(10.dp))
            }
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

        // Search field — filters the list below in real time. Only the
        // field itself can raise the keyboard (see searchFocused above).
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
                enabled = searchFocused,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledTextColor = Color(0xFF1C1C1E),
                    disabledPlaceholderColor = Color(0xFF6E6E73)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (searchFocused) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures {
                                    searchFocused = true
                                }
                            }
                        }
                    )
            )
            if (searchFocused) {
                LaunchedEffect(Unit) {
                    try {
                        focusRequester.requestFocus()
                    } catch (e: Exception) {
                        // focus already moved; editor still works
                    }
                }
            }
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
                    items(groupApps, key = { it.packageName }) { app ->
                        var rect by remember(app.packageName) {
                            mutableStateOf<Rect?>(null)
                        }
                        val isSelfApp = app.packageName == context.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelfApp) {
                                            // "iOS Launcher" row = door
                                            // into launcher Settings; it
                                            // never launches the activity
                                            // and is never hidden by the
                                            // OEM-settings hide rule.
                                            onOpenSettings()
                                        } else {
                                            launchApp(context, app.packageName, rootView, rect)
                                        }
                                    },
                                    onLongClick = { onIconLongPress(app) }
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                app = app,
                                sizeDp = 44.dp,
                                cfg = cfg,
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
