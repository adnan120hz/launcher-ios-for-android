package net.adnan120hz.launcher

import android.content.Context

/** Icon rendering style. Phase 1: only corner radius + shadow differ;
 *  fully redrawn icon packs arrive in Phase 3. */
enum class IconStyle(val label: String) {
    IOS26("iOS 26"),
    IOS18("iOS 18")
}

/** Performance tier. Drives the glass blur radius (cheaper = smoother on
 *  weak GPUs) and whether icon drop shadows are drawn. */
enum class PerfTier(val label: String, val blurRadiusDp: Float) {
    ENTRY("Entry", 10f),
    MID("Mid", 24f),
    FLAGSHIP("Flagship", 40f)
}

/** Control Center visual style. Liquid Glass (iOS 26) needs the global
 *  Liquid Glass toggle ON; otherwise the panel falls back to iOS 18. */
enum class CcStyle(val label: String) {
    IOS26("iOS 26 (Liquid Glass)"),
    IOS18("iOS 18 (klasik)")
}

/** Persistent launcher settings, stored in the same prefs file the
 *  Phase 0 onboarding already uses (its keys stay untouched). */
class LauncherStore(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var iconStyle: IconStyle
        get() = runCatching {
            IconStyle.valueOf(prefs.getString(KEY_ICON_STYLE, IconStyle.IOS26.name)!!)
        }.getOrDefault(IconStyle.IOS26)
        set(value) {
            prefs.edit().putString(KEY_ICON_STYLE, value.name).apply()
        }

    var glassEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GLASS_ENABLED, value).apply()
        }

    var perfTier: PerfTier
        get() = runCatching {
            PerfTier.valueOf(prefs.getString(KEY_PERF_TIER, PerfTier.MID.name)!!)
        }.getOrDefault(PerfTier.MID)
        set(value) {
            prefs.edit().putString(KEY_PERF_TIER, value.name).apply()
        }

    /** Ordered package names for the 4 dock slots. Empty until first run
     *  resolves device defaults. */
    var dockPackages: List<String>
        get() = prefs.getString(KEY_DOCK_PACKAGES, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) {
            prefs.edit().putString(KEY_DOCK_PACKAGES, value.joinToString(",")).apply()
        }

    var ccStyle: CcStyle
        get() = runCatching {
            CcStyle.valueOf(prefs.getString(KEY_CC_STYLE, CcStyle.IOS26.name)!!)
        }.getOrDefault(CcStyle.IOS26)
        set(value) {
            prefs.edit().putString(KEY_CC_STYLE, value.name).apply()
        }

    /** Hide the OEM Settings app entry from the App Library list while
     *  this launcher's Control Center covers quick settings. Hides the
     *  library entry only, never the system app itself. Default ON. */
    var hideSettingsInLibrary: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SETTINGS_LIB, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_SETTINGS_LIB, value).apply()
        }

    /** Effective Control Center style: global glass OFF forces iOS 18. */
    fun effectiveCcStyle(): CcStyle =
        if (!glassEnabled) CcStyle.IOS18 else ccStyle

    companion object {
        const val PREFS_NAME = "launcher_prefs"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_GLASS_ENABLED = "glass_enabled"
        private const val KEY_PERF_TIER = "perf_tier"
        private const val KEY_DOCK_PACKAGES = "dock_packages"
        private const val KEY_CC_STYLE = "cc_style"
        private const val KEY_HIDE_SETTINGS_LIB = "hide_settings_in_library"
    }
}
