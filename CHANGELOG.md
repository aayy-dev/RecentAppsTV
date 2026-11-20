# Changelog

## Unreleased

Magisk module build (`magisk-root`): a hybrid of this app and [TV Recents](https://github.com/jcgw22/android-tv-recents).

- **Module hardening (from an adversarial review):** refuses to install on a non-TV or below Android 10; the boot guard also
  watches for system-server restart loops; the build fails if the privapp allowlist misses a permission or the APK is
  absent; no `START_ACTIVITIES_FROM_BACKGROUND`; the trigger service is switched on once and stays off if the user turns it
  off; learning a button refuses power, search, assistant and gamepad keys and never half-delivers a key.
- **Trigger service stays the user's:** the "turned on once" marker lives outside the module folder (an update no longer
  switches it back on); Customize shows the service's real state, and choosing a trigger turns it on again; entries are
  compared as components; a saved button that is refused now counts as none; assistant key refused too.
- **The ⚙ Settings button is now "Customize"** (a sliders icon), and the Settings page is titled Customize, so neither is
  mistaken for Android's own settings. The launcher icon, the Menu key and the button all open it.
- **Pinned apps on their own row** above the recent apps (`RowId.PINS`); a running pin stays in the recents row too.
  ↑ / ↓ move between the rows, ↑ from the top row reaches the ⚙ Settings button, which left the row for the top right.
- **Options only on hold OK.** ↑ no longer opens the menu.
- **Quick switch** (TV Recents): opened over an app, the focus starts on the app before it.
- **↓ then OK removes** (TV Recents), besides ↓ ↓; Back cancels a pending removal before it closes the overlay.
- **Open with** (Settings): Hold Home (default) / Double Home / a remote button you choose / Off, plus the Recents key,
  through an accessibility key filter (`trigger/TriggerService`, rules in `logic/TriggerEngine`) that the app turns on
  itself as a system app (`WRITE_SECURE_SETTINGS`; also `service.sh`). Holding Home in Double Home mode still opens the
  Dashboard.
- **The launcher icon opens Settings**; the overlay opens from the remote (and shortcuts / button mappers).
- Layout: the pinned apps sit in a centred, outlined section that does not scroll, each focused pin showing only its
  name right under the icon; "Recent apps" is at the top left, the Settings button at the top centre; the app count
  ("3 apps") is centred under the recents row and no longer counts pins.
- New launcher icon and TV banner with a transparent background (three stacked cards; the banner adds the
  "Recent Apps" wordmark, outlined from Manrope). Adaptive icon in `mipmap-anydpi-v26`; `tools/icon/make_icon.py`
  regenerates all of it.
- **Settings rebuilt in the format of Google TV's own settings** (as InstallerX Revived's TV pages do): a two-pane
  page of borderless rows that become a light pill when focused, each choice on its own page with radio rows, a live
  miniature of the overlay on the right, real switches in the app lists. Focus and D-pad movement are the framework's
  (`androidx.tv` Surface); the hand-written key state machine (`SettingsNav.key`, `SetRow`, `SetState`) is gone, and
  with it the right-to-left layout.
- **The trigger service runs in its own process** (`:trigger`). A key filter has 500 ms to answer; in the main process
  the overlay's start-up made the Home release time out and reach the system, which then went Home right after the
  overlay opened. Settings talks to it with package-internal broadcasts.
- Visual pass over the overlay (from an end-to-end audit): the count line steps aside for the remove hint and for
  toasts; toasts take the key hints' place; a vertical budget (`fit`) keeps Large cards with large pins on screen and
  settles a Small layout lower; a menu opens above its card (a pin: above the pinned frame) with the other section and
  the header out of the way; the pinned frame grows for the focused pin's name, which sits centred under its icon, and
  no longer runs off screen with many pins; the Settings button has the cards' halo; the pin badge sits on a Circle
  card's outline; idle cards, the Kill all tile and the pinned frame are opaque; key hints follow the focus; the empty
  state has the overlay's header and footer; Fade is darker at the top; wording is consistent ("Kill all",
  "Hide from Recent apps", "Card size").
- **Fade background** (TV Recents' dim, 60 → 80 % black top to bottom) is the new default; *Pinned position* is gone.

- Root without Shizuku: `./gradlew :magisk:moduleZip` wraps the release APK in a Magisk module that installs it as a
  privileged system app (privapp allowlist + a framework overlay naming it the system recents component, for
  `REMOVE_TASKS`). The app sees it holds `REAL_GET_TASKS` and then calls the system services directly and runs
  `dumpsys` as itself (`shizuku/Privileged.kt`); a normal install still uses Shizuku, unchanged.

## 1.10.1 — 2026-11-09

- The balloon's header no longer shows "Running" / "Not running" under the app name; only "Pinned" for a pinned app.

## 1.10 — 2026-11-09

- OK on the app under the overlay closes the overlay instead of playing the select zoom and switching (`Overlay.activate`,
  `OvState.current`). Current app = first stored task that is not this app nor excluded, none when it is the launcher
  (`Stored.current`); with Shizuku off, the newest usage resume (`Stored.currentOf`).

## 1.9 — 2026-11-09

- Balloon drawn bottom-up (Close · App info · Add to exceptions · Pin to row); Pin, nearest the card, is focused first;
  UP climbs, DOWN steps back and closes the balloon from Pin (`Overlay.ctxKey`).
- UP on a card lowered by the first DOWN puts it back instead of opening the menu (`overlayKey`).
- Close uses an ✕ icon (`ic_close`) instead of the bin.

## 1.8 — 2026-11-09

- The button that opens the list closes it when pressed again: a second launch while the overlay shows closes it
  (`onNewIntent` → `Overlay.relaunched`). No key remapping in the app.
- "Open Recent Apps" in button mappers' Shortcuts lists (`ShortcutActivity`, `ACTION_CREATE_SHORTCUT`) — the list
  tvQuickActions builds with `queryIntentActivities(CREATE_SHORTCUT)`.
- ↑ on an app opens its menu; ↓ ↓ still removes. Menu: Pin · Exceptions · App info · Close (Settings removed; Close
  removes the app, a running pin keeps its pin).
- The focused app is always centred (no more pinned row ends).
- Springs (critically damped) for ←/→ scroll, slot moves, focus scale and the card lift; smoother fly-off easing.
  A50 frames: 53–58 % slow vs 58–72 % before, median 17–19 ms vs 19–22 ms.
- The ⚙ fades the "N apps" line while focused (they met on a short screen); dropped the wrong
  `android.shortcut.conversation` category from the launcher shortcut.

## 1.7 — 2026-11-09

- The row reads every task the system stores (`dumpsys activity recents` through Shizuku), not only the "visible" few:
  the TV hides tasks unused for 6 h from that list (2 shown while 47 were stored). Each stored app still reopens where it was.
- Usage access, read when the row opens (no background service; the system keeps ~10 days of events): apps used in the
  last 3 days that own no task fill the places left. The app grants itself usage access through Shizuku.
- Removal ledger: an app removed in the row (↑↑, Kill all) stays out until it is used again, so usage data can't bring it back.
- Shizuku off (after a TV reboot): the row is built from the last saved list + usage access and apps open from their start
  screen; removals are queued and done when Shizuku is back. The gate says "Allow Recent Apps in Shizuku" when only the grant is missing.
- Up to 20 apps; apps with a task always get a place before usage-only apps. Kill all never removes a home task.
- A removal queued while Shizuku was off also force-stops the app when it is replayed (unless the app was used again).
- The ⚙ button fades out while the "press again to remove" hint is up (they overlapped next to Kill all).
- Reopen on the A50 (Shizuku in shell mode): 0.18–0.33 s; with Shizuku off ~0.1 s.

## 1.6 — 2026-11-08
- Data methods checked against AOSP (SystemUI ActivityManagerWrapper, WM Shell RecentTasksController, Launcher3) and on the
  A50 with Shizuku in shell mode (as on the TV):
  - read: `getRecentTasks(max, RECENT_IGNORE_UNAVAILABLE, user)` first (system recents, most recent first); root tasks,
    `am stack list`, dumpsys stay as fallbacks. The old note "shell lacks REAL_GET_TASKS" was wrong.
  - switch: `startActivityFromRecents` first. Fixed: as shell, `moveTaskToFront(null package)` threw
    "package=null does not belong to uid=2000", so every switch on the TV fell back to a monkey relaunch; the fallback
    now passes `com.android.shell`.
  - Fixed: the system Settings app (FallbackHome, priority -1000) was taken for a launcher and never shown.
  - force-stop uses the current user (was user 0). removeTask + force-stop for dismiss / Kill all were already right.
- Tests: 150, 100 % lines and branches, 42 of 42 gate mutants caught.

## 1.5 — 2026-11-08
- The ⚙ Settings button moved above Kill all: ↑ on Kill all focuses it, OK opens Settings, ↓ returns, ← goes on to the
  apps. Without Kill all on screen it stays at the right end of the row.
- README brought up to date for 1.2–1.5 (controls, what's new, device screenshots).
- Tests: 143, 100 % lines and branches, 41 of 41 gate mutants caught.

## 1.4 — 2026-11-08
- Fixed (found on the TV): holding OK pinned the app at once and never showed the balloon menu. A real remote repeats
  a held OK every ~50 ms and a repeat ran the menu's first row. Now the hold is a 550 ms timer (design), repeats of OK
  are ignored, and the OK that opened the menu runs no row until it is released.
- New ⚙ Settings tile at the right end of the row — the Google TV remote has no Menu key, so 1.2/1.3 left Settings
  reachable only through the (broken) hold menu.
- Device tests now use a virtual remote (`tools/perf/rkey`, uinput) that holds keys like a real one; 1.3's
  `input keyevent --longpress` sent a single repeat and hid the bug.
- Tests: 137, 100 % lines and branches, 34 of 34 gate mutants caught.

## 1.3 — 2026-11-08
- Background image picker for TV (as Projectivy Launcher): the picker apps (Gallery, file managers, Files…) listed
  and asked directly — the Android TV "documents" stub is skipped — plus an in-app D-pad grid of the device's pictures
  (MediaStore, newest first; READ_MEDIA_IMAGES, granted on install with `-g`).
- The picture is prepared once: sub-sampled decode, centre crop to the screen shape, scaled to ≤ 1920×1080, JPEG in
  app storage; shown centre-cropped; decoded as a hardware bitmap only while the overlay shows.
- Tests: 130, 100 % lines and branches.

## 1.2 — 2026-11-08
- Design update "Recent Apps TV 2": pinned apps (group, badge, running dot, left/right, small/same size, list with Unpin
  all, empty-with-pins), select zoom on OK (tile → full screen in the app colour + name, then the app opens), balloon
  hold-OK menu (Pin/Unpin, Add to exceptions, App info, Settings) with toast, ↑ or ↓ twice to remove, centred header
  with counts, footer key hints, backgrounds Dim / Blur / Colour / Image, Exceptions Hide all / Show all, settings in
  four sections.
- Fixed: the name under the focused card blinked out and back on every left/right move (now a per-card cross-fade,
  whole-pixel motion); OK opened the app with no visible animation; an OK in the hold menu could also open the app on
  release; a release during an animation could eat the next OK.
- Tests: 122, 100 % lines and branches, 25 of 25 gate mutants caught.

## 1.1 — 2026-11-08
- Claude Design "Recent Apps TV" applied in full: RTL layout, focus ring + glow, name under the focused app, tinted
  Kill-all tile, settings/exceptions/Shizuku screens, motion spec (scrim fade, rise-in, focus glide, gap closing,
  fly-off with trail, staggered Kill all, fade-out on Back).
- Faster: hot reopen (the overlay steps aside instead of finishing), binder read of the task list (no process spawn),
  icons/labels prepared off the main thread and cached on disk, settings in SharedPreferences, R8.
- Zero cost while hidden: no service, timer or frame.
- Fixes: variable font rendered at weight 200; OK eaten after the hold menu; OK during a fly-off switched to the app
  being removed; volume keys swallowed while open; context menu width on small screens.
- Tests: logic moved to plain Kotlin (`logic/`), flow map with 96 tests, 100 % lines and branches, gates mutation-tested.

## 1.0 — 2026-05-31
- First release.
