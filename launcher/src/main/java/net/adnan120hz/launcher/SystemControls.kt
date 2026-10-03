package net.adnan120hz.launcher

import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Package of the device's OEM Settings app ("Settings bawaan"), used to
 *  optionally hide its entry from this launcher's App Library. */
fun resolveSystemSettingsPackage(context: Context): String? = try {
    context.packageManager
        .resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
        ?.activityInfo?.packageName
        ?.takeIf { it != "android" && it != context.packageName }
} catch (e: Exception) {
    null
}

// ---------- Connectivity states (read-only; third-party apps on modern
// Android cannot flip Wi-Fi/Bluetooth/Airplane directly, so the Control
// Center opens the relevant system panels instead of faking toggles). ---

fun isWifiEnabled(context: Context): Boolean = try {
    val wm = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    @Suppress("DEPRECATION")
    wm.isWifiEnabled
} catch (e: Exception) {
    false
}

fun isBluetoothEnabled(context: Context): Boolean = try {
    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bm.adapter?.isEnabled == true
} catch (e: Exception) {
    false
}

fun isAirplaneOn(context: Context): Boolean = try {
    Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON,
        0
    ) != 0
} catch (e: Exception) {
    false
}

// ---------- Torch (flashlight) — real toggle via CameraManager. ----------

fun torchCameraId(context: Context): String? = try {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.cameraIdList.firstOrNull { id ->
        cm.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
} catch (e: Exception) {
    null
}

fun setTorch(context: Context, on: Boolean): Boolean = try {
    val id = torchCameraId(context) ?: return false
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.setTorchMode(id, on)
    true
} catch (e: Exception) {
    false
}

// ---------- Screen brightness (system) — writing needs WRITE_SETTINGS. ---

fun canWriteSystemSettings(context: Context): Boolean =
    Settings.System.canWrite(context)

fun systemBrightness01(context: Context): Float = try {
    val raw = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS
    )
    (raw / 255f).coerceIn(0f, 1f)
} catch (e: Exception) {
    0.5f
}

fun setSystemBrightness01(context: Context, value: Float): Boolean {
    if (!canWriteSystemSettings(context)) return false
    return try {
        val cr = context.contentResolver
        Settings.System.putInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS,
            (value.coerceIn(0.02f, 1f) * 255).toInt()
        )
        true
    } catch (e: Exception) {
        false
    }
}

// ---------- Now playing (media session) — needs the notification-listener
// access the user grants to MusicListenerService in system settings. ------

fun notificationListenerGranted(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

fun activeMediaController(context: Context): MediaController? {
    if (!notificationListenerGranted(context)) return null
    return try {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as MediaSessionManager
        val component = ComponentName(context, MusicListenerService::class.java)
        msm.getActiveSessions(component).firstOrNull()
    } catch (e: Exception) {
        null
    }
}
