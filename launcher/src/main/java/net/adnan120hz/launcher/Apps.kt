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

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

/** All launchable apps (drawer-visible), excluding this launcher itself. */
fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return resolved
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

fun Drawable.toBitmapSafe(size: Int = 96): Bitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else size
    val h = if (intrinsicHeight > 0) intrinsicHeight else size
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bitmap
}

/** Launch an app with an iOS-style scale-up animation from the tapped
 *  icon's window bounds when they are known. */
fun launchApp(
    context: Context,
    packageName: String,
    sourceView: View?,
    sourceRect: Rect?
) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (sourceView != null && sourceRect != null && sourceRect.width() > 0) {
        val options = ActivityOptions.makeScaleUpAnimation(
            sourceView,
            sourceRect.left,
            sourceRect.top,
            sourceRect.width(),
            sourceRect.height()
        )
        context.startActivity(launch, options.toBundle())
    } else {
        context.startActivity(launch)
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
