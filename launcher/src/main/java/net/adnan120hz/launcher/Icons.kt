package net.adnan120hz.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import java.io.FileOutputStream

/** Shape mask applied to every home/dock/library icon. */
enum class IconShapeType(val label: String, val cornerPercent: Int) {
    SQUIRCLE("Squircle (iOS)", 27),
    ROUNDED("Kotak membulat", 16),
    CIRCLE("Lingkaran", 50);

    fun shape(): RoundedCornerShape = RoundedCornerShape(cornerPercent)
}

/** Which pack colour variant to draw. SYSTEM follows the Android
 *  dark/light system theme; LIGHT/DARK force the variant manually. */
enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang (paksa)"),
    DARK("Gelap (paksa)")
}

/** Semantic icon kinds that have a hand-drawn (original artwork) pack
 *  icon. Anything not classified falls back to the app's own icon,
 *  framed with the iOS shape mask + pack styling. */
enum class IconKind {
    PHONE, MESSAGES, BROWSER, MAIL, CAMERA, GALLERY, MAPS, CLOCK,
    WEATHER, SETTINGS, MUSIC, NOTES, CALCULATOR, CALENDAR, FILES
}

/** Everything the icon renderer needs, bundled to keep call sites tidy. */
data class IconConfig(
    val style: IconStyle,
    val dark: Boolean,
    val shape: IconShapeType,
    val shadowsEnabled: Boolean,
    val kindByPackage: Map<String, IconKind>,
    val customTick: Int
)

/**
 * Classification map: package name -> drawn icon kind.
 *
 * HOW TO EXTEND: add an entry to [WELL_KNOWN] (exact package) or rely on
 * the intent-category resolution in [buildIconKindMap], which maps the
 * device's *default* dialer/browser/mail/etc. automatically. Hardcoded
 * entries win over intent resolution (brand apps keep behaving even when
 * they are not the default handler).
 */
object IconMap {

    const val CAMERA26_PACKAGE = "net.adnan120hz.camera26"

    private val WELL_KNOWN: Map<String, IconKind> = mapOf(
        // Phone
        "com.google.android.dialer" to IconKind.PHONE,
        "com.android.dialer" to IconKind.PHONE,
        "com.samsung.android.dialer" to IconKind.PHONE,
        "com.miui.contacts" to IconKind.PHONE,
        // Messages
        "com.google.android.apps.messaging" to IconKind.MESSAGES,
        "com.samsung.android.messaging" to IconKind.MESSAGES,
        "com.android.mms" to IconKind.MESSAGES,
        "com.miui.mms" to IconKind.MESSAGES,
        // Browser
        "com.android.chrome" to IconKind.BROWSER,
        "org.mozilla.firefox" to IconKind.BROWSER,
        "com.sec.android.app.sbrowser" to IconKind.BROWSER,
        "com.microsoft.emmx" to IconKind.BROWSER,
        "com.opera.browser" to IconKind.BROWSER,
        "com.brave.browser" to IconKind.BROWSER,
        "com.duckduckgo.mobile.android" to IconKind.BROWSER,
        // Mail
        "com.google.android.gm" to IconKind.MAIL,
        "com.microsoft.office.outlook" to IconKind.MAIL,
        "com.yahoo.mobile.client.android.mail" to IconKind.MAIL,
        "com.samsung.android.email.provider" to IconKind.MAIL,
        // Camera
        CAMERA26_PACKAGE to IconKind.CAMERA,
        "com.google.android.GoogleCamera" to IconKind.CAMERA,
        "com.sec.android.app.camera" to IconKind.CAMERA,
        "com.android.camera" to IconKind.CAMERA,
        "com.android.camera2" to IconKind.CAMERA,
        "com.miui.camera" to IconKind.CAMERA,
        // Gallery / Photos
        "com.google.android.apps.photos" to IconKind.GALLERY,
        "com.sec.android.gallery3d" to IconKind.GALLERY,
        "com.miui.gallery" to IconKind.GALLERY,
        "com.android.gallery3d" to IconKind.GALLERY,
        // Maps
        "com.google.android.apps.maps" to IconKind.MAPS,
        "com.waze" to IconKind.MAPS,
        // Clock
        "com.google.android.deskclock" to IconKind.CLOCK,
        "com.android.deskclock" to IconKind.CLOCK,
        "com.sec.android.app.clockpackage" to IconKind.CLOCK,
        "com.miui.deskclock" to IconKind.CLOCK,
        // Weather
        "com.miui.weather2" to IconKind.WEATHER,
        "com.sec.android.daemonapp" to IconKind.WEATHER,
        "com.google.android.apps.weather" to IconKind.WEATHER,
        // Settings
        "com.android.settings" to IconKind.SETTINGS,
        // Music
        "com.google.android.music" to IconKind.MUSIC,
        "com.apple.android.music" to IconKind.MUSIC,
        "com.sec.android.app.music" to IconKind.MUSIC,
        "com.miui.player" to IconKind.MUSIC,
        "com.google.android.apps.youtube.music" to IconKind.MUSIC,
        // Notes
        "com.google.android.keep" to IconKind.NOTES,
        "com.samsung.android.app.notes" to IconKind.NOTES,
        "com.miui.notes" to IconKind.NOTES,
        // Calculator
        "com.google.android.calculator" to IconKind.CALCULATOR,
        "com.sec.android.app.popupcalculator" to IconKind.CALCULATOR,
        "com.miui.calculator" to IconKind.CALCULATOR,
        "com.android.calculator2" to IconKind.CALCULATOR,
        // Calendar
        "com.google.android.calendar" to IconKind.CALENDAR,
        "com.samsung.android.calendar" to IconKind.CALENDAR,
        // Files
        "com.google.android.documentsui" to IconKind.FILES,
        "com.sec.android.app.myfiles" to IconKind.FILES,
        "com.miui.fileexplorer" to IconKind.FILES,
        "com.mi.android.globalFileexplorer" to IconKind.FILES,
        "com.android.fileexplorer" to IconKind.FILES
    )

    /**
     * Build the package -> kind map for the given installed packages.
     * Intent-category resolution finds the *default* handlers on this
     * device (dialer, browser, ...); [WELL_KNOWN] entries override them.
     */
    fun buildIconKindMap(
        context: Context,
        packages: Collection<String>
    ): Map<String, IconKind> {
        val pm = context.packageManager
        val result = mutableMapOf<String, IconKind>()

        fun resolve(intent: Intent): String? = try {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" && it != context.packageName }
        } catch (e: Exception) {
            null
        }

        val intentKinds: List<Pair<Intent, IconKind>> = listOf(
            Intent(Intent.ACTION_DIAL) to IconKind.PHONE,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MESSAGING) to IconKind.MESSAGES,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_BROWSER) to IconKind.BROWSER,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_EMAIL) to IconKind.MAIL,
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) to IconKind.CAMERA,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_GALLERY) to IconKind.GALLERY,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MAPS) to IconKind.MAPS,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MUSIC) to IconKind.MUSIC,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALCULATOR) to IconKind.CALCULATOR,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR) to IconKind.CALENDAR,
            Intent(AndroidSettings.ACTION_SETTINGS) to IconKind.SETTINGS
        )
        intentKinds.forEach { (intent, kind) ->
            resolve(intent)?.let { result[it] = kind }
        }
        // camera26 always wins the CAMERA slot when installed.
        if (packages.contains(CAMERA26_PACKAGE)) {
            result[CAMERA26_PACKAGE] = IconKind.CAMERA
        }
        // Hardcoded well-known packages override intent resolution.
        WELL_KNOWN.forEach { (pkg, kind) -> result[pkg] = kind }

        return result.filterKeys { it in packages }
    }
}

/** User-picked custom icons, stored as PNG files in internal storage,
 *  one file per package name. Survives restarts; removed only by reset. */
object CustomIconStore {

    private const val DIR = "custom_icons"
    private const val SIZE_PX = 288

    private fun fileFor(context: Context, packageName: String): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return File(dir, "$packageName.png")
    }

    fun has(context: Context, packageName: String): Boolean =
        fileFor(context, packageName).exists()

    fun bitmap(context: Context, packageName: String): Bitmap? {
        val file = fileFor(context, packageName)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }
            .getOrNull()
    }

    /** Decode the picked image, downscale and persist it as a PNG. */
    fun saveFromUri(context: Context, packageName: String, uri: Uri): Boolean =
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val scaled = Bitmap.createScaledBitmap(decoded, SIZE_PX, SIZE_PX, true)
            FileOutputStream(fileFor(context, packageName)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        }.getOrDefault(false)

    fun clear(context: Context, packageName: String) {
        runCatching { fileFor(context, packageName).delete() }
    }
}
