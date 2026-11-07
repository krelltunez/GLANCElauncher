# GLANCElauncher Spec

Written Oct 19, 2026, superseding the Oct 17 preliminary spec; status brought up to date
Nov 6, 2026. This describes the launcher as built, with the decisions from
[decisions.md](decisions.md) folded in, and the real-data design it reads and writes the apps
through. The decision log is the record of how it got here; its numbered decisions are
referenced below as D1, D2, and so on.

**Status:** v1 is built and runs on tablets and phones. Real data ("Real data" below) is
built on all three sides and in use. Reads, writes (completing a task, logging a chore,
ticking a routine), REFRESH and both apps' headless refresh runs have been exercised on
devices. Version 0.5.0 went to testers in November 2026, signed with
the launcher's own release key, which both apps trust; the debug certificate is out of both
apps (D73). Until a provider answers, the launcher shows the seeded demo. v2 (a standalone
GLANCEvault client) and the Pro+ tier are dropped; there is no v2.

## Overview

GLANCElauncher is an Android home screen replacement that puts dayGLANCE and lastGLANCE at
the centre of the device. It is a focus launcher, not a general-purpose launcher: an
opinionated Home and planning screen, with one row of the user's own app shortcuts.

**Goals**

- Make the Day Dial the first thing seen on every unlock.
- Capture tasks in seconds from Home, by typing or voice.
- Surface planning context (month, agenda, goals, projects, upkeep, people) one swipe away.
- Leave room for the user's other apps without becoming a general launcher.

## Shell

Native Kotlin with Jetpack Compose; a `HOME` + `DEFAULT` activity, `singleTask` and
`stateNotNeeded`, so every Home press lands on the same instance and the system may drop and
recreate it freely. No DI framework: `AppGraph` is a plain class with one repository binding
(D3). No widgets: the `AppWidgetHost` area of the first cut was removed in the layout revision;
the dial takes the space and the pinned row is the user area (D5).

**Pages.** A two-page horizontal pager on every size: planning on the left, Home on the
right. Home is the default page and where the Home button and Back return. There is no
side-by-side mode: the pane split made the dial a third of the width, so the dial gets the
height on every size and planning is a swipe (D17). No user screens to the right of Home.

**Window classes.** Three layouts, chosen by the window's size, not the device:

| Class | Rule | Orientation |
|---|---|---|
| Tablet landscape | width ≥ 600 dp and width > height | free |
| Tablet portrait | width ≥ 600 dp and height ≥ width | free |
| Phone | width < 600 dp | locked to portrait (D29) |

**Overlays**, drawn over both pages: the full-screen search, the app drawer, the pinned apps
editor, Settings, the SCHED filter sheet, and a toast. Back closes the top overlay, then
returns to Home.

**Settings** opens with "Set as Home app", then groups (D74): Appearance (theme: system,
light or dark; solid wallpaper; themed icons on Android 13 and later; hide the status bar;
24-hour clock override), Home (double-tap to lock), Taskbar (on/off, labels, recents with the
optional usage access), Apps (notification dots, drawer labels, the hidden-app count), Search
(contacts in search), Backup (export and import to a file, D63) and About (diagnostics, the
open source licences, and the version, build time and commit). Locking the dial (no move,
resize or create) is the header's lock button, not a setting. Preferences persist in
SharedPreferences.

**Wallpaper.** Android paints the device wallpaper behind a launcher during its own
transitions, whatever the launcher draws, so the launcher keeps the home-screen wallpaper a
solid fill of the theme background while "Solid wallpaper" is on (the lock screen is left
alone). The launcher cannot restore the previous wallpaper: reading it back needs storage
permissions it does not ask for. So it is off by default from 1.0.0 (D15): a new install leaves
the wallpaper alone, and an older install that never touched the switch and was already painted
keeps it on.

**Insets.** The header pads for the taller of the status bar and the display cutout, so hiding
the status bar does not slide it under a camera (D31). With the taskbar off the bottom pads for
the navigation bar; with it on, the taskbar sits above the bar.

## Home

The dial is the star on every size. Nothing on Home scrolls; a swipe up on the background
opens the drawer.

**Tablet landscape.** The dial fills the height on the left in a rounded dark panel, its width
capped so the column beside it keeps at least 560 dp. The column, top to bottom: the header
(the GLANCElauncher wordmark, each app's icon to open it with its data-status badge, lock and
settings glyphs; D44, D45); the **All day & deadlines** pill (all-day items behind a
calendar glyph, deadlines due within a week behind a calendar-clock glyph, as rose chips with
"in 2d"); the **breakdown legend** in a fixed two-row order (Effort, Restore, Sleep, Focus;
Done, Routines, Frames, Unblocked, dashes for missing data) (D18, D21); the **week strip**,
seven day cells from today on the in-app month-cell metrics (tap shows the day on the dial,
long-press opens it in dayGLANCE); the **lastGLANCE cards**, Chores and People side by side
with four stats each (due now, due soon, on track, done today; reach out, unreturned, coming
up, in touch) and up to two next-up rows with Done and + Log inline; the **pinned row** (icons
only, a pencil opens the editor); the **search bar**. The strip is the one flexible piece and
takes what is left, 56 to 220 dp. The column measures itself with two, one and then no
next-up rows and keeps the first that leaves the strip at least 120 dp, so a shorter tablet
loses rows rather than the strip (D27). With the taskbar on, gaps and the pill tighten.

**Tablet portrait.** The content below takes its natural height and the dial takes what is
left, capped at the width. The pill and a six-item legend (Focus and Done dropped, since the
hub shows them) share a row; the strip is 150 dp; lastGLANCE shows the stats only; then the
pinned row and the search bar.

**Phone.** One screen, no scroll: the dial takes the full width, to 8 dp from the screen edges
where the other pieces stop at 16 (or the height left, if less), with no complications at that size; under it the pill, the six-item legend, the pinned row
and the search bar at natural height. The week strip and lastGLANCE are tablet-only. Spare
height (a tall phone, or the status bar hidden) is shared by every gap, up to 20 dp each,
rather than pooling around the dial (D28). A scrolling phone Home with the strip and
lastGLANCE was tried and rejected.

**Dial complications.** From a 520 px dial the four corners carry the in-app complications:
Inbox count (top left, opens the Inbox), Deadlines in the next seven days (top right, opens
today's calendar), a Done ring (bottom left, opens today's dial) and lastGLANCE's Due now
count (bottom right, opens the Soon filter), the last a launcher-only addition (D17, D19).

**Pinned apps.** One row, showing what fits, with a "+N" button for the rest (D41). Pins can be
apps or shortcuts another app asked to add to Home (D48). Tap launches; a long press opens the
app's menu (D53), and a long-press drag upward launches in split screen, like the taskbar. The
pencil opens an editor: the pins on top with long-press drag to reorder and a cross to remove,
the app list below to add (D23).

**Search bar.** A trigger: the drawer button, a dayGLANCE / lastGLANCE chip, a magnifier, the
hint text and the mic. Tapping opens the full-screen search above the keyboard (D18).

## The Day Dial

`:daydial` is a Gradle library module depending on Compose only, so dayGLANCE Android can
adopt it later. It is the Compose port of dayGLANCE's **in-app** dial (`DayDial.jsx`,
`dayDial.js`, `intervalLanes.js`), not the widget: the 1000-unit geometry, the ring at 300
to 385, lane packing for overlaps, HSL muting, tone states, midnight clipping with the
previous day's overrun carried in, the focus-block rule, labels yielding to the sun marks,
the layers from face to needle, the hub (clock, weekday with chevrons, Lora date, readout),
the action sheet, and the complications tiers. Its single dark look holds in both launcher
themes, as in the app. The reference's rules are ported as JUnit tests.

**Launcher-only gestures** (D1, D2). The in-app dial has no drag; the launcher adds them on
the faithful geometry: drag a block to move it (snaps to 15 minutes, clamped to the day),
drag its trailing 20 % to resize (end edge only), long-press an empty stretch to create.
Carried-in blocks and read-only calendar events do not move. Drag starts on touch slop so
the pager never fights it. The header's lock button turns all three off and is the only
sign of it: the hub carries no lock glyph, which would crowd a phone's hub. Every data-changing interaction is a callback
(`DayDialCallbacks`); the dial never writes.

**Hub sizing** (D30). The reference sizes the hub in viewport vmin, which on a phone is the
dial's own width, so below a 420 dp dial the hub takes the reference's phone values (clock
14, weekday 10, date 28, title 13, lines 11, tags 10, chevrons 20 in an overflowing hit box)
and its line heights; the tablet keeps the larger clock and chevrons. At every size the
clock, weekday and date fill the top part from the bottom up so the divider sits 5 % of the
dial below the midpoint however many lines the readout runs to; the long month falls back
to the short one by measuring the serif against 44 % of the dial, as `dialDateFits` does. On
a phone the focus readout is two lines.

**Ported with real data (D35, D36)**: the moon band and phase glyph, the next-alarm mark,
the weather ring (hour temperatures and precipitation spells) and the daylight band from solar
elevation and UV, each from the same maths and rules as the app's dial. **Not ported**: the
habit complication; the keyboard listbox; routine drag; the Gaussian glow (soft strokes stand
in). Without coordinates the daylight band falls back to a half-sine.

## Planning

Taps deep-link into the apps; nothing is edited in place except the writes the repository
offers (complete, schedule, postpone, log).

**Tablet landscape**: one scrolling column per app. **dayGLANCE**: the month grid on the in-app
cell metrics (bands in lanes, calendar events capped darker, diamonds for deadlines, a +N
badge), with the selected day feeding the agenda; the agenda, a direct port of SCHED
(`SchedView.jsx`, `SchedTaskCard.jsx`, `SchedDayExtras.jsx`, `SchedFilterPopup.jsx`) with
its rules as pure, tested functions (D24): the rolling 14-day window from the selected day,
the Overdue section, deadline cards, today's routine pills, the uniform two-line card, the
filter sheet, whose selections are remembered (D61); then Goals (rings with per-project bars,
soonest target date first, D67) and Projects (cards with progress). **lastGLANCE**: pinned
chores (D68), due now and due soon chores (the app's ChoreRow with the cadence fill), Coming
up (the People home's date tiles, four across on a tablet) and Reach out (D25). A "+" adds a
task (through the search parser), a chore, or a person (in lastGLANCE's own form) (D41).

**Tablet portrait and phone**: one column, dayGLANCE then lastGLANCE. Jump chips at the end
of the SCHED controls row (Goals, Projects, and lastGLANCE where it shares the column), up
arrows in those headers, and a Back to top row at the foot (D26). On a phone the month cells
are 10:7 rather than square, the controls chips tighten to stay on one line, project cards go
full width and the Coming up tiles are the app's three across, nine at most.

**Section links** open the matching screen in the app: the month heading opens the day, Goals
and Projects open their panel of the Goals & Projects space, the flame opens lastGLANCE's Soon
filter, the people glyph opens People, a tile opens the person.

**Not ported** (D24): SCHED's filter presets, "next instance only", and the time pickers behind
a deadline card's calendar button and a routine pill's long-press (the launcher schedules at
09:00, or the next quarter hour today). Pieces whose data the launcher does not hold (stars,
Do sessions, plan history, subtasks, day notes, focus log) are absent rather than faked. The
chore and people heatmaps stay in the app.

## App drawer and taskbar

**Drawer**: alphabetical, searchable grid, the search field at the bottom above the keyboard,
with a row of the four apps last opened from the launcher above the list (D41). Long-press
opens the shared app menu: the app's own shortcuts, App info, hide or show, pin to Home, pin
to the taskbar, uninstall (D48, D53). Icon bitmaps are cached. Opens from the search bar's grid
button, a swipe up on Home, or a swipe up on the taskbar; a swipe down closes it (D64).

**Taskbar** ("GLANCE taskbar", D6): toggleable, under both pages, above the navigation bar;
pinned apps on the left, recents on the right. Recents come from the launcher's own launch
history; optional usage access, offered in Settings and never by the taskbar, adds apps used
elsewhere (D42). Labels optional. A long press opens the app menu; a long-press drag upward
launches in split screen. It does
not stay on top of other apps, and the system's own transient taskbar cannot be suppressed by
a launcher.

## Search and voice

The search bar searches GLANCE data as well as apps, and doubles as quick capture.

- **Scope**: installed apps and their shortcuts (D47); Settings pages; phone contacts, when
  the user turns that on; dayGLANCE tasks, projects and goals; lastGLANCE chores and people;
  arithmetic; and a web search at the end (D49). It opens with suggested apps and the recent
  queries that led somewhere (D41).
- **Quick capture**: dayGLANCE's natural-language grammar (`quickAddParser.js`: times, ranges,
  dates, durations, the `@ ~ $ % !` sigils) creates a task; recurrence phrases stay in the
  title for the app (D7). The `lg ` prefix or the chip routes to lastGLANCE: a unique chore
  match becomes "Log done", otherwise "Add to lastGLANCE"; a person match offers "Log
  contact" (D8).
- **Mic**: a tap listens in the launcher, with speech recognised on the device (Android 12's
  on-device recognizer, else the system one asked to stay offline), and types what it hears
  into search, so voice finds whatever typing does. A spoken command runs itself when nothing
  is in doubt (`VoiceCommands`, `SearchEngine.autoRun`): open an app, a timer, an alarm,
  directions, a dayGLANCE task ("add…", "remind me to…"), a lastGLANCE interaction ("talked to
  Sam"), a web search; "call" and "text" open the dialer or messages for a tap. Anything else
  stays as a search. Typed, a command row follows the capture row, so Enter still adds the
  task. The microphone permission is asked on the first tap. A long press, or a phone with no
  recognizer, opens dayGLANCE's voice capture (`ACTION_VOICE_INPUT`, then
  `dayglance://voiceInput`).

## Deep links

The launcher fires dayGLANCE's `dayglance://` scheme (`today`, `day?date=&view=`, `task?id=`,
`goal?id=`, `project?id=`, `newInboxTask`, `startFocus?id=`, `voiceInput`, `inbox`,
`goal?tab=goals`, `project?tab=projects`) and explicit intents to lastGLANCE's `MainActivity`
carrying `lastglance://` URIs (`chore/<id>`, `filter/soon`, `people`, `person/<id>`,
`chores`, `pinned`, `action/addPerson`), since lastGLANCE has no intent filter for the scheme (D11). The four asks the
launcher made of the apps are done in both, merged to main and not yet tested on a device
(D22): `dayglance://inbox`, `lastglance://chores`, the `tab` parameter on goal and project
links, and the calendar and calendar-clock glyphs on SCHED's event and deadline cards.

## Architecture

```
                 GLANCElauncher UI
   (Home with the Day Dial, planning, search and mic, drawer, taskbar)
                        |
              GlanceRepository (interface)
                /                 \
      FakeRepository          ProviderRepository
      seeded demo data        ContentProvider reads, launcher-intent writes
      (demo mode)                 /                 \
                           dayGLANCE            lastGLANCE
```

The UI reads and writes through `GlanceRepository` only: `StateFlow`s for tasks, goals,
projects, routines, frames, the day shape, chores, people and availability; suspending writes
for move, move-to-day, resize, complete, toggle routine, create task, log chore, add chore and
log interaction. `FakeRepository` implements it with data generated relative to today (D4).
`ProviderRepository` is the real backend below, built over a `GlanceClient` seam so it is
tested on the JVM. `AppGraph` binds it with the fake as its fallback, and at runtime it falls
back to the fake, per app, when a provider is missing (see "Demo mode").

`VaultRepository` and the standalone GLANCEvault client are removed from the spec. The
repository interface stays as the one seam.

## Real data

Both apps are WebView apps whose data lives in the WebView's IndexedDB, which native code
cannot read. The native side has only what the app's JavaScript pushes to it; that is how the
widgets already work, and it is how the launcher works. The design, in order of build:

**1. Reads: a launcher snapshot served by a ContentProvider in each app.** Each app gains a
second snapshot builder in JS, `launcherSnapshot`, pushed to the native shared store on the
same trigger as the widget snapshot (every data change while the app runs). Its shape is the
launcher's models, not the widgets': dayGLANCE tasks with id, title, date, start, duration,
all-day, completed and completed-at, colour, priority, notes, project id, deadline, source and
energy; goals; projects; routines with completion dates; frames with their weekdays; the day
shape (awake window, sun times, 24-hour setting). lastGLANCE: chores with category, cadence,
last done and icon name; people with circle, cadence, last interaction and channel,
reciprocity and their dates. Every snapshot carries `updatedAt`. A read-only `ContentProvider`
per app (`content://com.dayglance.app.launcher/<collection>`,
`content://com.lastglance.app.launcher/<collection>`) serves one URI per collection as a
cursor of JSON rows, and the push calls `notifyChange`, so the launcher's `ContentObserver`
refreshes the repository flows without polling. Nothing is inserted, updated or deleted
through the provider.

**2. Writes: launcher-intent actions to a separate receiver in each app.** Each app gains a
`BroadcastReceiver` beside its automation `IntentReceiver`, taking the same `{action,
payload}` envelope plus a client-generated `clientId`, feeding a pending queue and the same
wake broadcast, so the JS handlers are shared. Every write the launcher sends carries that
`clientId`; the app's handlers dedupe on it (a re-sent write is acknowledged, never applied
twice), `CREATE` stores it on the new task, and the snapshot lists the ids it has handled. It is guarded by the launcher permission (below) and does not consult the public
"Automation intents" toggle, which stays off by default and unchanged for everyone else.
The actions: dayGLANCE `CREATE`, `COMPLETE`, `UNCOMPLETE`, `MOVE` (a date and an optional
time: an Inbox task is scheduled, a block moved, an all-day task postponed), `RESIZE` and
`TOGGLE_ROUTINE` (today only, as the app keeps routine completions); lastGLANCE `CREATE`,
`COMPLETE` (a chore) and `LOG_INTERACTION`. dayGLANCE runs the gestures through the MCP write
model's pure mutations, so a calendar event, a routine block or a recurring occurrence is
refused with the MCP tools' own words and a placement over a routine is refused rather than
slid. A write that fails for good is recorded in the app's ledger with its error, so the
launcher retires it on the next snapshot instead of at expiry; a failed `CREATE` is not, since
the launcher may correct the payload and re-send.

**3. Permission.** Each app declares its own permission, `com.dayglance.permission.LAUNCHER`
and `com.lastglance.permission.LAUNCHER` (Play signs each app with a different key, and a
custom permission name shared between apps can fail to install), with
`protectionLevel="signature|knownSigner"`. The known signers are the **launcher's**
certificates, not the apps'. The launcher has one, its release key: release builds refuse
to package without it, and debug builds carry it where it is present (`app/build.gradle.kts`,
D73). Its digest fills the release slot of each app's `launcher_certs.xml`. The debug
certificate that stood in during development is out of both apps, and Google Play's app
signing uses the same release key, so Play and sideloaded installs carry one certificate and
the Play slot is not needed. The launcher declares `uses-permission` for both. The provider and
the launcher receiver are both behind the permission, so Play and sideloaded builds
interoperate and no other app can read or write. `knownSigner` exists from Android 12 (API
31), so the launcher's minSdk is 31: below it the launcher could only ever show demo data
(D73).

**4. Freshness and optimistic writes.** The snapshot is as fresh as the app's last JavaScript
run: after a launcher write the app's data changes when the app drains the intent, at once if
its renderer is alive. At a dead app the receiver schedules a headless run that drains it
(unless one ran in the last ten minutes; a later re-send then starts one). Either way there is
a delay, so the launcher applies every write to its local copy at once, persists the pending write in a durable outbox under its `clientId`,
keeps it applied over each new snapshot until a snapshot shows that id (on the created task,
or in the handled list, where a failed write also appears with its error), or it expires after
24 hours, and re-sends it on resume and on each observer tick until then (at most every 15 s).

Freshness is measured from each app's last confirmation, not its last change: both apps write
a heartbeat beside the snapshot (`checkedAt` on every builder run, `syncedAt` for the latest
completed sync) and report whether their renderer is alive (D37). On every return to Home the
launcher re-reads both providers and sends each app a `REFRESH`, at most once per app every two
minutes and not when its last sync is under two minutes old (D38). An alive app runs every sync
tier and re-pushes; a dead one runs a headless refresh, the web bundle in a WebView with no
Activity that syncs, pushes the snapshot and reports, at most once per ten minutes (D40, D46).
Every request and run is logged in the app's meta. The UI shows when the data is stale: each
app's icon in Home's header carries an amber dot once its data is unconfirmed for an hour (or
is demo), Planning's header names it, and the diagnostics panel (long-press either icon) shows
each app's freshness and refresh log (D45, D54). The pill and cards read from the stale data
rather than going blank.

**5. Demo mode.** If an app's provider is absent (not installed, an older version, or a signer
the app does not know), the launcher falls back to `FakeRepository` for that
app's data and marks it as demo data in the Home and planning headers, with the install banner
still offered. The launcher works out of the box and the fake data remains the demo.

**Not used as data sources**: the Direct Access file tier (no fresher than the snapshot) and
the GLANCEvault (a server and encryption that a launcher should not depend on offline).

## Scope and non-goals

**In v1**: Home, planning, drawer, search, mic, hide apps, the taskbar with split screen,
light and dark themes, the phone layouts above, the real-data pass above. Added since: app
shortcuts in search and pinned shortcuts (D47, D48), notification dots (D50), themed icons
(D51), double-tap to lock (D52), and settings export and import (D63).

**Not in v1**: user screens, any widget grid, a taskbar that stays on top of other apps, gesture customisation beyond the basic swipes, icon packs, work-profile
separation, SCHED presets and pickers.

**Out of scope**: iOS; being a general-purpose launcher; a standalone client without the apps.

## Build

Gradle 8.14 (JDK 17 to 24), AGP 8.13, Kotlin 2.2, Compose BOM 2025.09; compileSdk 36, minSdk
31. `./build-and-install.sh` builds, installs over adb and can open the Home-app settings;
`./build-release.sh` builds and checks the files to publish. See [building.md](building.md).
