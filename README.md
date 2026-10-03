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

## Lock screen honesty note

Android does not let a third-party app replace the system lock screen
(keyguard). The iOS-style lock screen here is a **display layer shown
right after your real Android lock** (PIN/pattern/fingerprint) opens —
the same approach every iOS lock-screen app on Android uses. Your system
lock stays fully in charge of device security; the fingerprint/face
prompt on this layer only dismisses the visual layer. This is stated
again inside the app (Settings › Layar Kunci iOS).

## Status

**Phase 4: iOS lock screen (launcher v0.5.0).** On top of phases 0–3
(paged home, Liquid Glass dock, App Library + search, Control Center in
iOS 18/iOS 26 styles, Dynamic Island overlay, hand-drawn iOS 18/26 icon
packs with dark variant, custom icon shapes, per-app custom icons from
the gallery, live manual dock-glass tuning):

- iOS-style lock layer: big clock + date over your wallpaper, swipe-up
  indicator, two bottom shortcuts — flashlight (real torch toggle) and
  camera (opens this project's Camera iOS 26 app, or the system camera)
- Appears after the screen turns off and the system lock is passed
  (screen-state watcher service + overlay window), from the "Kunci"
  button in Settings / Control Center, and as an in-launcher layer when
  the overlay permission is not granted
- Swipe up to dismiss; fingerprint/face (BiometricPrompt) is asked when
  the device has one enrolled and the setting is on, otherwise swipe
  only. A cancelled biometric attempt offers an explicit swipe fallback
- Notification stack in iOS style, fed by real notifications once you
  grant notification access (Settings › Layar Kunci iOS); until then the
  lock shows an honest "permission needed" card instead of fake entries
- Full customization in Settings, all live-previewed and persistent:
  clock size slider, "extended" (bigger & wider) clock, clock color
  (preset swatches + HSV picker), font weight, wallpaper blur behind
  the clock, Liquid Glass intensity on lock elements. Lock elements
  follow the global style — Liquid Glass off or iOS 18 style selected
  makes them solid iOS-18 style

Still spec for later phases: motion-blur open/close animations
(iOS 18 / iOS 26 fluid), real CameraX camera polish. Features land
phase by phase.

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
