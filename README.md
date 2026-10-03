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

## Open/close animation honesty note (v0.7.0)

**Opening** an app is fully ours to shape: the launch uses
`ActivityOptions.makeScaleUpAnimation` anchored at the tapped icon's
real bounds, the tapped icon spring-squashes and fades into the growing
window (iOS 26 style), and the home surface zooms back slightly with a
progressive, downscale-capped blur (per performance tier) behind it.
The returning icon spring-lands at 122% → 100% when you come home.

**Closing** an app is different: Android owns the exit window flight
for third-party launchers. No third-party app can force the system to
shrink another app's window back into a specific icon — that is a
platform limit, not a missing permission, and developer-mode flags do
not change it. What we do control, we use: our own home layer
sharpens/unblurs with a spring on return and the icon the app was
opened from plays the landing spring. We do not claim more than the
platform allows.

## Style rule (binding, v0.7.0)

- **iOS 18 style = absolutely no Liquid Glass anywhere** on that
  surface: flat, solid, classic skins.
- **iOS 26 style = Liquid Glass always on** on that surface.
- The global Liquid Glass switch OFF drops every surface to the solid
  iOS 18 family — there is no "iOS 26 but glass missing" state.

## Dynamic Island (v0.7.0)

Real data only, no decoration states: clock, charging state with the
real battery percentage, a running countdown, and the currently
playing track (title + artist, tap the pill to play/pause) — all from
live system/media sessions; a state with no real data behind it is
never shown. The island is user-tunable in Settings (size, width,
horizontal/vertical offset) with a live preview, sliders apply while
you drag, values persist, and a reset returns it to the iOS-style
center-top default.

## Status

**v0.7.0 — the big build.** Everything from phases 0–5 (paged home,
Liquid Glass dock, App Library + search, Dynamic Island overlay,
hand-drawn iOS 18/26 icon packs with dark variant, custom icon shapes,
per-app custom icons from the gallery, live manual dock-glass tuning,
iOS lock layer with clock customization and BiometricPrompt unlock),
plus this build:

- **Open/close motion rebuilt**: spring-driven icon launch (squash +
  fade into the system window zoom anchored at the icon), progressive
  downscaled home blur + slight zoom-out behind the window, and a
  spring landing on return. Honest platform limit for the exit flight
  is documented above.
- **Control Center redesigned from reference screenshots**: iOS 26
  glass squircle connectivity module, squircle music card, two big
  circles (orientation lock, Do Not Disturb) and two tall draggable
  brightness/volume capsules; iOS 18 renders the same real layout in
  flat classic skins. Every control is real: torch toggle, brightness
  (with the system grant), media volume, orientation-lock write, DND
  interruption filter, system panels for Wi-Fi/Bluetooth/airplane
  (Android forbids third-party toggles), live media controls, and
  shortcuts that only appear when the target app exists.
- **Icon pack redrawn** (15 kinds, both styles + dark): thicker, more
  faithful glyphs, real device clock, plus the launcher's own icon.
  The launcher itself now appears in the grid and App Library as
  "iOS Launcher" and opens launcher Settings; the OEM-settings hide
  rule never applies to it.
- **Liquid Glass in the app's own UI** (Settings cards, icon context
  menu) when glass is on; flat solid iOS 18 when off.
- **Dynamic Island is tunable & data-real** (see above).
- **Anti-lag & adaptive**: icon bitmap cache, raster downscale cap,
  cached media-session lookups, and a grid that adapts icon size and
  spacing to the device's width/height.

Builds are debug artifacts from CI; on-device verdicts pending.

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
