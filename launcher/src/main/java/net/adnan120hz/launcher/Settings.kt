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

/** App open/close animation style (Phase 5). iOS 18 = the classic
 *  icon-to-window zoom. iOS 26 (fluid) = the same window flight driven
 *  with spring physics on the launcher side plus a progressive GPU
 *  blur of the home surface while the app opens. Motion smoothness
 *  always wins over effect cost: blur radius scales with the tier. */
enum class AnimStyle(val label: String) {
    IOS18("iOS 18 (klasik)"),
    IOS26("iOS 26 (fluid)")
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

    /** iOS 26 icon appearance (0.8.0): Terang / Gelap / Clear / Tinted. */
    var iconVariant: IconVariant
        get() = runCatching {
            IconVariant.valueOf(
                prefs.getString(KEY_ICON_VARIANT, IconVariant.LIGHT.name)!!
            )
        }.getOrDefault(IconVariant.LIGHT)
        set(value) {
            prefs.edit().putString(KEY_ICON_VARIANT, value.name).apply()
        }

    /** User-picked tint (ARGB) for the TINTED icon appearance. */
    var iconTintArgb: Int
        get() = prefs.getInt(KEY_ICON_TINT, 0xFF0A84FF.toInt())
        set(value) {
            prefs.edit().putInt(KEY_ICON_TINT, value).apply()
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

    /** Lock screen master switch (Phase 4). */
    var lockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()
        }

    /** Lock screen appearance as one snapshot (Phase 4). Compared by the
     *  Settings sliders; written back field by field. */
    fun lockPrefs(): LockPrefs = LockPrefs(
        clockScale = prefs.getFloat(KEY_LOCK_CLOCK_SCALE, 1.0f),
        clockColorArgb = prefs.getInt(KEY_LOCK_CLOCK_COLOR, 0xFFFFFFFF.toInt()),
        clockWeight = prefs.getInt(KEY_LOCK_CLOCK_WEIGHT, 700),
        clockExtended = prefs.getBoolean(KEY_LOCK_CLOCK_EXTENDED, false),
        wallpaperBlurDp = prefs.getFloat(KEY_LOCK_WALLPAPER_BLUR, 18f),
        glassIntensity = prefs.getFloat(KEY_LOCK_GLASS_INTENSITY, 0.6f),
        useBiometric = prefs.getBoolean(KEY_LOCK_USE_BIOMETRIC, true)
    )

    fun saveLockPrefs(p: LockPrefs) {
        prefs.edit()
            .putFloat(KEY_LOCK_CLOCK_SCALE, p.clockScale)
            .putInt(KEY_LOCK_CLOCK_COLOR, p.clockColorArgb)
            .putInt(KEY_LOCK_CLOCK_WEIGHT, p.clockWeight)
            .putBoolean(KEY_LOCK_CLOCK_EXTENDED, p.clockExtended)
            .putFloat(KEY_LOCK_WALLPAPER_BLUR, p.wallpaperBlurDp)
            .putFloat(KEY_LOCK_GLASS_INTENSITY, p.glassIntensity)
            .putBoolean(KEY_LOCK_USE_BIOMETRIC, p.useBiometric)
            .apply()
    }

    /** Lock elements follow the global style rule: the iOS 26 style is
     *  selected for lock elements (via [effectiveCcStyle]) AND the global
     *  Liquid Glass switch is on. GLOBAL RULE (0.7.0): iOS 26 style always
     *  shows glass; iOS 18 or glass OFF always solid. */
    fun lockGlassStyleIs26(): Boolean =
        glassEnabled && effectiveCcStyle() == CcStyle.IOS26

    /** Dynamic Island style under the same global rule: glass only when
     *  the iOS 26 look is effectively selected AND the global switch is
     *  on; otherwise the island renders solid iOS 18. */
    fun islandGlassStyleIs26(): Boolean =
        glassEnabled && effectiveCcStyle() == CcStyle.IOS26

    /** Dynamic Island master switch (0.8.0). Default ON: the island is
     *  supposed to work end to end once the user unlocked it and granted
     *  the overlay permission; before unlock the follow-gate keeps the
     *  service off regardless of this flag. Persisted so the service
     *  policy (IslandService.shouldBeRunning) survives process death. */
    var islandEnabled: Boolean
        get() = prefs.getBoolean(KEY_ISLAND_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ISLAND_ENABLED, value).apply()
        }

    /** App open/close animation style (Phase 5). */
    var animStyle: AnimStyle
        get() = runCatching {
            AnimStyle.valueOf(
                prefs.getString(KEY_ANIM_STYLE, AnimStyle.IOS26.name)!!
            )
        }.getOrDefault(AnimStyle.IOS26)
        set(value) {
            prefs.edit().putString(KEY_ANIM_STYLE, value.name).apply()
        }

    /** Update availability checks (Phase 5): a quiet once-a-day look at
     *  this repo's latest GitHub release. No ads, no tracking — the only
     *  thing ever fetched is the release tag + page URL. */
    var updateChecksEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_CHECKS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_UPDATE_CHECKS, value).apply()
        }

    /** Epoch millis of the last update check (0 = never). */
    var lastUpdateCheckMs: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()
        }

    /** Newest release version seen that is newer than the installed
     *  build, or null when up to date / never checked. Drives the
     *  settings banner + red dot. */
    var availableVersion: String?
        get() = prefs.getString(KEY_AVAILABLE_VERSION, null)
            ?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_AVAILABLE_VERSION, value).apply()
        }

    // ---- Dynamic Island customization (Phase 6 / 0.7.0) ---------------
    // User-tunable geometry for the overlay pill. All values apply LIVE
    // (the service re-reads them on an ACTION_REFRESH intent) and persist
    // here. The style (glass vs solid) still follows the global toggles:
    // Liquid Glass OFF -> solid iOS-18 island.

    /** Overall pill scale, 0.8 (small) .. 1.3 (large). */
    var islandScale: Float
        get() = prefs.getFloat(KEY_ISLAND_SCALE, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_ISLAND_SCALE, value).apply()
        }

    /** Width multiplier on the per-state base width, 0.7 .. 1.6. */
    var islandWidthFactor: Float
        get() = prefs.getFloat(KEY_ISLAND_WIDTH, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_ISLAND_WIDTH, value).apply()
        }

    /** Horizontal offset from top-center, in dp (-140 .. 140). */
    var islandOffsetXDp: Float
        get() = prefs.getFloat(KEY_ISLAND_OFFSET_X, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_ISLAND_OFFSET_X, value).apply()
        }

    /** Vertical offset from the top edge, in dp (0 .. 96). */
    var islandOffsetYDp: Float
        get() = prefs.getFloat(KEY_ISLAND_OFFSET_Y, 14f)
        set(value) {
            prefs.edit().putFloat(KEY_ISLAND_OFFSET_Y, value).apply()
        }

    fun resetIslandGeometry() {
        prefs.edit()
            .putFloat(KEY_ISLAND_SCALE, 1.0f)
            .putFloat(KEY_ISLAND_WIDTH, 1.0f)
            .putFloat(KEY_ISLAND_OFFSET_X, 0f)
            .putFloat(KEY_ISLAND_OFFSET_Y, 14f)
            .apply()
    }

    /** Release page to open when the update banner is tapped. */
    var availableReleaseUrl: String
        get() = prefs.getString(KEY_AVAILABLE_URL, null)
            ?.takeIf { it.isNotBlank() }
            ?: UpdateChecker.RELEASES_PAGE
        set(value) {
            prefs.edit().putString(KEY_AVAILABLE_URL, value).apply()
        }

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
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LOCK_CLOCK_SCALE = "lock_clock_scale"
        private const val KEY_LOCK_CLOCK_COLOR = "lock_clock_color"
        private const val KEY_LOCK_CLOCK_WEIGHT = "lock_clock_weight"
        private const val KEY_LOCK_CLOCK_EXTENDED = "lock_clock_extended"
        private const val KEY_LOCK_WALLPAPER_BLUR = "lock_wallpaper_blur"
        private const val KEY_LOCK_GLASS_INTENSITY = "lock_glass_intensity"
        private const val KEY_LOCK_USE_BIOMETRIC = "lock_use_biometric"
        private const val KEY_ANIM_STYLE = "anim_style"
        private const val KEY_UPDATE_CHECKS = "update_checks_enabled"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check_ms"
        private const val KEY_AVAILABLE_VERSION = "available_version"
        private const val KEY_AVAILABLE_URL = "available_release_url"
        private const val KEY_ISLAND_SCALE = "island_scale"
        private const val KEY_ISLAND_WIDTH = "island_width_factor"
        private const val KEY_ISLAND_OFFSET_X = "island_offset_x_dp"
        private const val KEY_ISLAND_OFFSET_Y = "island_offset_y_dp"
        private const val KEY_ISLAND_ENABLED = "island_enabled"
        private const val KEY_ICON_VARIANT = "icon_variant"
        private const val KEY_ICON_TINT = "icon_tint_argb"
    }
}

/** Effective icon-pack style under the GLOBAL GLASS RULE (0.7.0):
 *  iOS 18 icons never wear glass; iOS 26 icons always do, and the global
 *  Liquid Glass switch OFF forces everything (icons included) to the
 *  solid iOS 18 family. This is the single place that decides, so no
 *  surface can end up glass-off while labelled iOS 26. */
fun effectiveIconStyle(store: LauncherStore): IconStyle =
    if (!store.glassEnabled) IconStyle.IOS18 else store.iconStyle

/** Hard cap for the dock blur radius per performance tier: manual
 *  tuning can refine the glass look but never make an Entry-tier
 *  device render flagship-cost blur. */
fun blurCapDp(tier: PerfTier): Float = when (tier) {
    PerfTier.ENTRY -> 16f
    PerfTier.MID -> 44f
    PerfTier.FLAGSHIP -> 64f
}

/** Blur radius of the progressive home wash while an app flies open
 *  (Phase 5, iOS 26 fluid style). Smooth frames outrank effects, so
 *  the radius scales down hard on weaker tiers and always respects the
 *  same per-tier cap as the dock glass: Entry devices never pay for a
 *  flagship-size blur kernel during the transition. */
fun launchBlurRadiusDp(tier: PerfTier): Float = when (tier) {
    PerfTier.ENTRY -> 8f
    PerfTier.MID -> 22f
    PerfTier.FLAGSHIP -> 40f
}.coerceAtMost(blurCapDp(tier))
