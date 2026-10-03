# iOS Launcher for Android

An iOS-style launcher for Android — home screen, dock, control center and lock
screen that feel like an iPhone, rebuilt natively for Android with Kotlin and
Jetpack Compose. No ads, no trackers, no gimmicks.

Two apps live in this repo:

- **`:launcher`** (`net.adnan120hz.launcher`) — the iOS-style home launcher.
- **`:camera26`** (`net.adnan120hz.camera26`) — an iOS 26 style camera app,
  shipped as its own APK and bundled with the launcher experience.

## Planned feature set

- Home screen that mirrors the iOS layout (~90% look and feel), with an
  icon grid, iOS-style dock and wallpaper-aware dock adjustments
- **Liquid Glass dockbar** that updates live when settings change
- **Control Center in two styles** — iOS 18 and iOS 26 (Liquid Glass)
- **App Library** with categories and search, plus a search button on the
  home screen
- **iOS-style lock screen** — customizable clock (color, blur, glass),
  sliders, and widgets
- Smooth open/close animations with real motion blur, in two flavours:
  iOS 18 style and iOS 26 fluid style
- Two icon packs (iOS 18 style and iOS 26 style, including dark mode),
  redrawn by hand — no Apple assets
- **Dynamic Island** overlay (asks for the overlay permission)
- Optional "reduce glass" mode = classic iOS 18 look and better battery life
- Performance tiers: entry-level (reduced glass), midrange (balanced),
  flagship (full effects) — tuned so animations stay smooth on every tier

## Special unlock: Dynamic Island (follow gate)

One special feature — the **Dynamic Island iOS 26 UI** — starts locked.
During onboarding, a follow-gate step asks the user to follow the developer
on TikTok; a "Saya sudah follow" (I followed) self-confirmation unlocks it
(the flag is stored persistently). Following cannot be verified
automatically — TikTok exposes no way to check it — so this is an
honor-system confirmation by design.

## Layout standard

Every screen follows one tidy layout standard: consistent page padding and
spacing, aligned grids and rows. No cluttered or uneven screens.

## Status

**Phase 1: core launcher (v0.2.0).** Ready to test on a device:

- iOS-style paged home screen (4x5 grid per page, real installed-app icons
  and labels); tap an icon to launch with a scale-up animation from the
  icon's position
- Liquid Glass dock with 4 slots. On Android 12+ (API 31) the dock backdrop
  is a real RenderEffect blur of the wallpaper layer; on older versions it
  falls back to a translucent gradient. Dock apps are user-chosen and
  persistent (first-run defaults: Phone, Messages/Browser, Camera — this
  project's Camera iOS 26 app when installed — Gallery)
- App Library as the page after the last home page: all apps grouped A-Z
  with a search field that filters in real time. The "Search" pill above
  the dock jumps to it
- Launcher settings (App Library entry, or long-press the home screen):
  icon style iOS 26 / iOS 18 (corner radius + shadow for now; redrawn icon
  packs arrive in Phase 3), Liquid Glass on/off (off = solid iOS-18 style,
  easier on battery), performance tier Entry/Mid/Flagship (blur radius,
  Entry also drops icon shadows), dock app picker + reset
- Onboarding with the TikTok follow gate from Phase 0 is unchanged

Still spec for later phases: Control Center (iOS 18 / iOS 26 styles),
working Dynamic Island overlay, lock screen, motion-blur fluid animations,
redrawn icon packs, real CameraX camera. Features land phase by phase.

Phase 0: project foundation. The launcher skeleton (onboarding, installed-app
grid, 4-slot glass dock) and the camera UI skeleton build in CI on every push.

## Building

APKs are built by GitHub Actions (`.github/workflows/build-apks.yml`):
`launcher-debug-apk` and `camera26-debug-apk` are uploaded as workflow
artifacts. Locally:

```bash
./gradlew :launcher:assembleDebug :camera26:assembleDebug
```

Requires JDK 17 and Android SDK 35. minSdk 29, targetSdk 35.

## Credits

- **Developer: Adnan.120hz**
- GitHub: https://github.com/adnan120hz
- Website: https://adnan120hz.vercel.app
- TikTok: https://www.tiktok.com/@adnan.120hz?_r=1&_t=ZS-9AElXliY2Me

This project is not affiliated with Apple Inc. All icons and artwork are
original redraws; no Apple assets are used.
