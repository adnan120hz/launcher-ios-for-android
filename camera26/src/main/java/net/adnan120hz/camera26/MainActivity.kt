package net.adnan120hz.camera26

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CameraApp()
        }
    }
}

private fun cameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

@Composable
fun CameraApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    var granted by remember { mutableStateOf(cameraGranted(context)) }
    var asked by remember { mutableStateOf(false) }
    var permanent by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { g ->
        granted = g
        asked = true
        if (!g && activity != null) {
            permanent = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.CAMERA
            )
        }
    }

    // Re-check when returning from system Settings.
    val componentActivity = context as? ComponentActivity
    DisposableEffect(componentActivity) {
        val lifecycle = componentActivity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = cameraGranted(context)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraScreen()
        } else {
            PermissionScreen(
                asked = asked,
                permanent = permanent,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    try {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Throwable) { /* ignore */ }
                }
            )
        }
    }
}

@Composable
private fun PermissionScreen(
    asked: Boolean,
    permanent: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CameraBodyGlyph(Color.White, Modifier.size(92.dp))
        Spacer(Modifier.height(26.dp))
        Text(
            "Akses Kamera",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Camera26 membutuhkan akses ke kamera agar kamu bisa mengambil foto dan merekam video. Foto dan video hanya disimpan di galeri perangkatmu.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
        if (permanent) {
            Text(
                "Izin kamera dimatikan. Aktifkan lewat Pengaturan sistem untuk melanjutkan.",
                color = IosYellow,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IosYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text("Buka Pengaturan", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IosYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    if (asked) "Coba Lagi" else "Izinkan Akses Kamera",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
