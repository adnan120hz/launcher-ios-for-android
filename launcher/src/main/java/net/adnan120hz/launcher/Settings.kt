package net.adnan120hz.launcher

import android.content.Context

/** Icon pack style. Phase 3: selects the hand-drawn pack (iOS 18 flat
 *  classic vs iOS 26 liquid glass, incl. dark variant) and the corner /
 *  shadow treatment for framed original icons. */
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

    /** Shape mask for all icons (Phase 3). */
    var iconShape: IconShapeType
        get() = runCatching {
            IconShapeType.valueOf(
                prefs.getString(KEY_ICON_SHAPE, IconShapeType.SQUIRCLE.name)!!
            )
        }.getOrDefault(IconShapeType.SQUIRCLE)
        set(value) {
            prefs.edit().putString(KEY_ICON_SHAPE, value.name).apply()
        }

    /** Light/dark variant of the iOS 26 icon pack (Phase 3). */
    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(
                prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!
            )
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    /** Manual dock glass tuning (Phase 3). dockBlurDp < 0 means
     *  "follow the performance tier default". The effective radius is
     *  always capped per tier so Entry devices stay cheap. */
    var dockBlurDp: Float
        get() = prefs.getFloat(KEY_DOCK_BLUR, -1f)
        set(value) {
            prefs.edit().putFloat(KEY_DOCK_BLUR, value).apply()
        }

    var dockTintAlpha: Float
        get() = prefs.getFloat(KEY_DOCK_TINT_ALPHA, 0.20f)
        set(value) {
            prefs.edit().putFloat(KEY_DOCK_TINT_ALPHA, value).apply()
        }

    var dockTintDark: Float
        get() = prefs.getFloat(KEY_DOCK_TINT_DARK, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_DOCK_TINT_DARK, value).apply()
        }

    fun effectiveDockBlurDp(): Float =
        (if (dockBlurDp >= 0f) dockBlurDp else perfTier.blurRadiusDp)
            .coerceAtMost(blurCapDp(perfTier))

    companion object {
        const val PREFS_NAME = "launcher_prefs"
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_GLASS_ENABLED = "glass_enabled"
        private const val KEY_PERF_TIER = "perf_tier"
        private const val KEY_DOCK_PACKAGES = "dock_packages"
        private const val KEY_CC_STYLE = "cc_style"
        private const val KEY_HIDE_SETTINGS_LIB = "hide_settings_in_library"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DOCK_BLUR = "dock_blur_dp"
        private const val KEY_DOCK_TINT_ALPHA = "dock_tint_alpha"
        private const val KEY_DOCK_TINT_DARK = "dock_tint_dark"
    }
}

/** Hard cap for the dock blur radius per performance tier: manual
 *  tuning can refine the glass look but never make an Entry-tier
 *  device render flagship-cost blur. */
fun blurCapDp(tier: PerfTier): Float = when (tier) {
    PerfTier.ENTRY -> 16f
    PerfTier.MID -> 44f
    PerfTier.FLAGSHIP -> 64f
}
