package net.adnan120hz.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/** All launchable apps (drawer-visible), including this launcher itself
 *  so the user always keeps an "iOS Launcher" entry (label overridden)
 *  that leads to the launcher settings — even while it is the default
 *  home app. The launcher's own entry is rendered with the icon pack. */
fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    val selfEntry = try {
        val info = pm.getApplicationInfo(context.packageName, 0)
        AppEntry(
            label = "iOS Launcher",
            packageName = context.packageName,
            icon = pm.getApplicationIcon(info)
        )
    } catch (e: Exception) {
        null
    }
    val others = resolved
        .mapNotNull { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val label = info.loadLabel(pm)?.toString() ?: return@mapNotNull null
            AppEntry(
                label = label,
                packageName = pkg,
                icon = info.loadIcon(pm)
            )
        }
    return (others + listOfNotNull(selfEntry))
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Load one app's entry by package name (used by the dock, whose defaults
 *  like the dialer may not show up in the launcher-category query). */
fun loadAppEntry(context: Context, packageName: String): AppEntry? {
    val pm = context.packageManager
    return try {
        val info = pm.getApplicationInfo(packageName, 0)
        AppEntry(
            label = pm.getApplicationLabel(info).toString(),
            packageName = packageName,
            icon = pm.getApplicationIcon(info)
        )
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

fun isPackageInstalled(context: Context, packageName: String): Boolean =
    loadAppEntry(context, packageName) != null

fun Drawable.toBitmapSafe(size: Int = 192): Bitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else size
    val h = if (intrinsicHeight > 0) intrinsicHeight else size
    // Cap the raster at 192px: icons render at <=64dp, so anything bigger
    // is wasted memory and decode time on scroll (anti-lag).
    val scale = if (w > 192 || h > 192) {
        192f / maxOf(w, h)
    } else {
        1f
    }
    val bw = (w * scale).toInt().coerceAtLeast(1)
    val bh = (h * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, bw, bh)
    draw(canvas)
    return bitmap
}

/** Tiny decoded-icon bitmap cache so scrolling the App Library / pager
 *  never re-decodes the same drawable twice (anti-lag). Bounded FIFO. */
object IconBitmapCache {
    private const val MAX_ENTRIES = 240
    private val lock = Any()
    private val cache = LinkedHashMap<String, Bitmap>(64, 0.75f, false)

    fun get(key: String): Bitmap? = synchronized(lock) { cache[key] }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(lock) {
            cache[key] = bitmap
            while (cache.size > MAX_ENTRIES) {
                val eldest = cache.entries.firstOrNull()?.key ?: break
                cache.remove(eldest)
            }
        }
    }
}

/**
 * Phase 5 outgoing-launch state. Written by [launchApp] the moment an
 * app actually starts; read by the home surface, which progressively
 * blurs (iOS 26 fluid style) and spring-squashes the tapped icon while
 * the new window flies open. Cleared when the launcher resumes (the
 * "close" flight back home) or when an in-app layer takes over, so the
 * blur always washes back out — never gets stuck on.
 *
 * Phase 6 (0.7.0): also tracks the landing — when the user comes back,
 * [landingPackage] names the icon that should spring-land (the system
 * exit flight itself is system-owned; see README honesty note).
 */
object OutgoingLaunch {
    var activePackage: String? by mutableStateOf(null)
    var landingPackage: String? by mutableStateOf(null)
}

fun launchApp(
    context: Context,
    packageName: String,
    sourceView: View?,
    sourceRect: Rect?
) {
    // Never actually launch ourselves: the caller routes our own entry
    // to Settings. Guard anyway so a stray caller can't loop us.
    if (packageName == context.packageName) return
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Mark the outgoing launch only now that we know the app exists.
    OutgoingLaunch.activePackage = packageName
    OutgoingLaunch.landingPackage = null
    val options = when {
        sourceView != null && sourceRect != null && sourceRect.width() > 0 ->
            // Icon-to-window flight: the target window grows out of the
            // tapped icon's rect (works for both styles; the launcher
            // surface adds springs+blur for iOS 26).
            ActivityOptions.makeScaleUpAnimation(
                sourceView,
                sourceRect.left,
                sourceRect.top,
                sourceRect.width(),
                sourceRect.height()
            )
        else ->
            // No known icon bounds: gentle cross-fade instead of a cut.
            ActivityOptions.makeCustomAnimation(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
    }
    try {
        context.startActivity(launch, options.toBundle())
    } catch (e: Exception) {
        // Launch died after all — don't leave the home surface blurred.
        OutgoingLaunch.activePackage = null
    }
}

/** First-run dock defaults: Phone, Messages (or Browser), Camera
 *  (this project's camera26 app when installed, else the system camera),
 *  Gallery. Resolved from whatever the device actually has. */
fun resolveDefaultDock(context: Context): List<String> {
    val pm = context.packageManager
    fun resolve(intent: Intent): String? = try {
        val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (pkg == "android") null else pkg
    } catch (e: Exception) {
        null
    }

    val phone = resolve(Intent(Intent.ACTION_DIAL))
    val messages = resolve(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
    ) ?: resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER))
    val camera = if (isPackageInstalled(context, "net.adnan120hz.camera26")) {
        "net.adnan120hz.camera26"
    } else {
        resolve(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    }
    val gallery = resolve(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
    )
    return listOfNotNull(phone, messages, camera, gallery).distinct().take(4)
}
