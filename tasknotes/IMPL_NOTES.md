# Impl notes — 0.8.0 fidelity + Dynamic Island (living doc)

## Diagnosis: kenapa island "belum berfungsi" di HP (0.7.0)
1. IslandService = plain background Service started with context.startService()
   (SettingsScreen "Aktifkan Island", Home.pushIslandRefresh). Once the user
   leaves the launcher, Android kills it; START_STICKY does not resurrect
   non-foreground services reliably on modern Android, and startService() from
   background throws IllegalStateException (caught silently everywhere).
2. Manifest: service declared with NO foregroundServiceType; no
   REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission at all.
3. No auto-start: nothing restarts the island except the manual button; after
   process death it stays dead and the Settings "Status: mati" is the only
   clue.
4. Status surface: IslandState.running is process-local memory (lost on
   death) and Settings shows it without reasons; no battery-exemption
   guidance.

## Fix plan (0.8.0, decisions taken)
- IslandService -> foreground service (specialUse), startForeground in
  onCreate before window attach; notification opens the app; manifest gains
  foregroundServiceType=specialUse + subtype property (same pattern as
  LockScreenService, proven in 0.7.0) + REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
- Static helpers: IslandService.start/stop/showTest/showTimer/refreshGeometry
  using startForegroundService, all try/catch wrapped.
- Auto-restart policy: IslandService.shouldBeRunning(ctx) = unlocked &&
  islandEnabled pref && overlay granted. Home calls IslandService.ensureRunning
  from LaunchedEffect whenever islandEnabled/unlock state changes (foreground
  start = allowed). Battery exemption requested via system intent from the
  island page, with honest copy (no Play-violating promise: we just ask once).
- IslandState gains process-local mirrors: windowAttached, lastError (short
  code for Settings: NO_OVERLAY / START_REFUSED / OK), foregroundActive.
- LauncherStore: new KEY island_enabled (default true) so the toggle is a
  durable pref, not just process state.
- Dedicated DynamicIslandScreen (own file) replaces the buried Settings card:
  live in-app preview reusing the real IslandPill, forced-mode preview strip
  (clock/music/charging/timer drawn by IslandPillContent with test=false so
  preview never fakes overlay state), status rows with reasons, test button
  "Tampilkan island sekarang" (starts service + ACTION_SHOW_NOW forces a
  visible pill for 12s), sliders stay (user decision: keep), reset, style
  note follows global glass rule. Follow-gate UI preserved verbatim when
  locked.
- SettingsScreen main list becomes a doorway row: "Dynamic Island — status".

## Other 0.8.0 fidelity fixes (from LAUNCHER-GAP-ANALYSIS.md)
- Home: jiggle edit mode (long-press enters, icon rotation via graphicsLayer,
  tap empty exits; menu still on tap in edit mode), PageDots spring width,
  icon variant wiring (Terang/Gelap/Clear/Tinted + tint swatches) via
  IconConfig.variant/tintArgb; PackIcon CLEAR = translucent plate + white
  glyph; TINTED = plate dark + glyph in tint colour; originals get
  ColorFilter tint (mono for CLEAR light variant, tint for TINTED).
- ControlCenter: RoundButton press-scale spring (glass interactive rule),
  panel drag-up (vertical drag on panel closes beyond -60dp), version text
  0.8.0. Home: CC top-edge strip unchanged.
- Store: KEY_ICON_VARIANT/KEY_ICON_TINT, IconVariant enum, TINT swatches.

## Deviations / open questions (recorded, not silent)
- iOS glass interactive also has slight brightness shift on press; we do
  scale only (Android Compose has no specular layer here) — noted in report.
- Library keyboard bug (gap doc item 5): prior agent claimed pointer fix in
  Home.kt:1036; verified during implementation pass, see below.
- Island expanded press-hold: left OPEN (gap doc §7) — needs gesture pass.

## Deviation recorded (0.8.0): Clear/Tinted rendering technique
True one-ink rendering of the pack glyphs would require redrawing every
glyph path in a mono parameterised colour (drawGlyph hardcodes its palette
per shape). To keep real calendar date / clock hands and stay in scope,
PackIcon CLEAR/TINTED draw: plate (clear frost / dark) -> full glyph ->
mono veil 0.22 alpha -> mono re-ink of calendar/clock + soft aura for the
rest. Visual result: one-ink dominant read at icon size; NOT a pixel-pure
iOS tint. Honest grade: structural approximation, device-verify pending.
Original (bitmap) icons get ColorFilter.tint: CLEAR -> light monochrome
approx, TINTED -> tint colour. Custom user bitmaps stay untouched full
colour (they're the user's own artwork).

## Checks before commit 2 (done while writing)
- SettingsScreen old references to IslandService context.startService /
  startTimer: replaced by IslandService.start/stop helpers (grep shows only
  helper calls remain). Island geometry sliders/preview removed from the
  buried card -> doorway row only; full controls live in
  DynamicIslandScreen.kt.
- SliderRow visibility widened to internal (DynamicIslandScreen reuses it).
- Brace fix in SettingsScreen: the CC card that contained the island card
  needed one extra closing brace after the doorway-row replacement; added
  at the item boundary before "Pembaruan Aplikasi" (verified by re-reading
  the region).
- Island.kt: import ordering left to compiler (fully-qualified FGS types
  used inline: NotificationManager/NotificationChannel/PendingIntent/
  NotificationCompat/ServiceInfo/Build/PowerManager).
- PageDots keeps the 19.dp spacer convention (dots row total height
  unchanged: 7dp dot + 12dp vertical padding = 19dp).
- Jiggle: rotation applied only to the icon layer (label stable), per-icon
  phase from packageName hash, ~110ms half-cycle; long-press enters edit
  mode AND opens the same context menu; tap in edit opens menu; empty tap
  exits. No deletes/uninstalls invented (iOS-drag/remove not modelled).
- onIslandEnabledChange in SettingsScreen island-page wiring writes the
  pref then starts/stops the FGS immediately.
- Library keyboard note (gap doc item #5): the claimed "pointer fix" at
  Home.kt:1036 from the earlier agent does NOT match this tree (that line
  is inside SettingsScreen wiring). Rechecked Library.kt during this pass:
  the real library search is Library.kt-local state; see Library.kt fix in
  commit 3. Recorded here so the false "already fixed" claim is not
  propagated.

## Library search (gap #5) — settled by reading Library.kt itself
Composition: search TextField + LazyColumn list. No pointerInput anywhere;
"pass-through pointer hack" from the earlier agent's note does not exist in
this tree. The plausible unasked-keyboard mechanism: focusable TextField
attaching inside the pager page receiving focus -> IME shows. Fix (0.8.0):
field rendered disabled (still visibly a search field) until the user taps
it; tap flips enabled + FocusRequester.requestFocus(). Keyboard rises only
from that tap. Trade-off recorded: first tap enables, second tap places
cursor naturally once focused (requestFocus on enable keeps it one-tap in
practice). Not "pass-through" — the gap doc wording is corrected below.
