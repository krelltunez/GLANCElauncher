# Decision log

The numbered decisions behind GLANCElauncher, in the order they were made, from the first proof
of concept on fake data through the real-data pass to the first tester release. Each one records
what was chosen and why, and they are kept as written: a later decision can revise or supersede
an earlier one (decision 46 supersedes 39, for example), and some name files that have since
moved or been removed, such as `tools/jvmcheck/`. "The spec" in the early entries is the
original brief; [spec.md](spec.md) describes the launcher as built. Code comments cite these
entries by number, so the numbers never change. Files and pull requests named "its PR" or
"there" belong to the dayGLANCE or lastGLANCE repository.

1. **The reference dial has no move, resize or create gestures.** The in-app web dial inspects
   on hover/tap and opens an action sheet; there is no drag. The spec asks for move and resize
   callbacks, so they were added as launcher-era interactions on the faithful geometry: drag a
   block to move it (snaps to 15 minutes, clamped to the day), drag its trailing 20 % (at least
   10 minutes) to resize, long-press an empty stretch of the ring to create. Blocks carried in
   from the previous day and read-only calendar events do not move. Only the end edge resizes.
2. **Drag starts on touch slop, not on long-press.** On narrow widths the dial sits inside a
   pager, so a drag that begins on a block is consumed by the dial and the pager never sees
   it; a drag that begins on empty face scrolls the pager. If this proves twitchy on a tablet,
   switching to long-press-to-drag is a one-line change in `DayDial.kt`.
3. **No DI framework.** `AppGraph` is a plain class; Hilt or Koin would add a plugin and
   generated code to a proof of concept for one binding.
4. **Fake data is generated relative to today** so the demo always shows a live day: a full
   schedule, a block that overruns midnight from yesterday, inbox items, project history that
   drives the progress bars, a month of agenda items, and chores and people across fine, due
   soon and overdue.
5. **No widgets on Home.** An `AppWidgetHost` area shipped in the first cut and was removed in
   the layout revision (decision 17): the dial takes the space, and the pinned row is the
   user area. The host code is in git history if widgets return.
6. **Dock split screen** is a long-press drag upward on a dock icon, which launches the app
   with `FLAG_ACTIVITY_LAUNCH_ADJACENT`. Unpinning from the dock happens in the drawer's pick
   mode (tap a pinned app), so a long-press on the dock can be the drag.
7. **Quick capture grammar** follows dayGLANCE's `quickAddParser.js` (times, ranges, dates,
   durations, `@ ~ $ % !` sigils); recurrence is not ported, the phrase stays in the title for
   dayGLANCE to handle when the real write path lands.
8. **lastGLANCE routing** is the `lg ` prefix or the chip in the search bar. A unique chore
   match becomes "Log done", otherwise "Add to lastGLANCE". People matches offer "Log contact".
9. **Progress maths** uses dayGLANCE's duration-weighted rule for projects and the weighted
   average for goals (`packages/agenda-core/src/progress.js`), not the widget's count ratio.
10. **lastGLANCE states** use the app's rules: ratio = elapsed / cadence; due soon at 0.75,
    overdue at 1, with the continuous green → amber → red colour.
11. **Deep links**: dayGLANCE's are the documented `dayglance://` scheme (`day?date=`,
    `task?id=`, `goal?id=`, `project?id=`, `newInboxTask`, `startFocus`). lastGLANCE has no
    intent filter for `lastglance://`, so those go as explicit intents to its `MainActivity`,
    which parses the URI. If either app is missing the launcher shows a toast and, on Home, an
    install banner (hidden while the fake repository reports both installed).
12. **24-hour clock** follows dayGLANCE's setting from the repository, with a launcher
    override in Settings.
13. **Fonts**: Lora is bundled as two variable TTFs from Google Fonts (OFL) and used only for
    the wordmark and the dial date, as on the web. Everything else is the system sans.
14. **Icons**: Material core icons plus a few hand-drawn lucide-style vectors
    (`ui/Icons.kt`); the extended Material set was not worth its size.
15. **The wallpaper never shows.** Android paints the device wallpaper behind the launcher during
    its own transitions (home gesture, app launches), whatever the launcher draws. So the
    launcher keeps the home-screen wallpaper a solid fill of the current theme background
    ("Solid wallpaper" in Settings, on by default; `SET_WALLPAPER` is a normal permission). It
    rewrites only when the colour changes and leaves the lock screen alone. Turning it off stops
    the rewrites but does not restore the previous wallpaper: reading it back needs storage
    permissions the launcher does not ask for. Custom backgrounds can come later on top of this.
    **Revisited (Oct 19):** because the previous wallpaper cannot be restored, a public release
    must either default this off or warn before the first rewrite. Fine for the personal build;
    a requirement before anyone else installs it.

16. **Calendar events in the fake data** ("Standup", "Design review", "Team offsite", ...) stand
    in for imported calendar events. As in dayGLANCE, imported events are read-only: they draw
    on the dial and in the month cells with a darker left cap, and the agenda shows a calendar
    glyph instead of a checkbox. Only dayGLANCE tasks can be completed from the launcher.

17. **Layout revision after the first tablet session.** The side-by-side planning pane made
    the dial a third of the width. Home now gives the dial the full height on every size,
    with the All Day pill, the breakdown legend, a lastGLANCE stats card, one pinned row and
    the search bar beside it (under it in portrait), and nothing scrolls. The in-app
    complications sit in the dial's corners once it measures 520 px or more: Done ring, Inbox
    and Deadlines counts, plus lastGLANCE's overdue count as a launcher-only fourth. The Now
    card went away: the hub narrates now and next, and the counts moved into the corners.
    Planning is a swipe to the left; in landscape it is one column per app.

18. **Search takes the screen.** The bar on Home is a trigger; tapping it opens a full-screen
    search above the keyboard (the bar with the keyboard behind it hid the results). Magnifier
    first, then the text. The breakdown legend has eight entries (the in-app seven plus blocks
    done over planned) so it fills two even rows.
19. **Complication taps.** Done opens today's dial, Deadlines today's calendar, Due now opens
    lastGLANCE with the Soon filter, Inbox opens the Inbox via `dayglance://inbox`. dayGLANCE
    had no inbox deep link (`newInboxTask` opens the capture form); the action was added to the
    app on Oct 19 (merged to main, not yet tested on a device), see decision 22.

20. **Chore icons come from the installed lastGLANCE.** The app ships every lucide glyph as an
    `ic_lucide_*` drawable and stores a chore's icon as the lucide name, so the launcher reads
    the drawable out of the app's own resources (`createPackageContext`) and tints it. No
    1.7k vectors in the launcher, and the icons match the app by construction. Without the app
    or the glyph, a sparkle stands in.
21. **Home's right column, landscape, after the second session**: All day & deadlines (one
    pill with a heading, deadlines as rose chips), the breakdown in a fixed two-row order
    (Effort, Restore, Sleep, Focus; Done, Routines, Frames, Unblocked, dashes for missing
    data), this week as seven day cells (tap shows the day on the dial, long-press opens it
    in dayGLANCE), the chores and people cards with four stats each (due now, due soon, on
    track, done today; reach out, unreturned, coming up, in touch), the pinned row and the
    search bar. The Inbox drag-to-schedule idea did not fit and is parked.

22. **Small asks of the apps**, wired on the launcher side first and then done in the apps
    (both merged to main on Oct 19, not yet tested on a device): `dayglance://inbox` opens the
    Inbox tab (the Inbox corner fires it); `lastglance://chores` switches to Chores (the Chores
    card fires it); a `tab` parameter on the goal and project links, `dayglance://goal?tab=goals`
    and `dayglance://project?tab=projects`, opens the Goals and Projects panels of the Goals &
    Projects space when no id is focused (the planning screen's "Goals" and "Projects" links
    fire them; `resolveGoalsLink` reads it and `GoalDashboard` selects the panel); and SCHED's
    own cards now hold the checkbox slot with a calendar glyph on an event and lead a deadline
    card with the calendar-with-clock glyph, as the launcher's port does.
23. **Pinned apps editor.** The pencil opens an editor: the pins on top, long-press drag to
    reorder, a cross to remove, the app list below to add. Home itself has no reorder or
    unpin gesture; a long-press drag upward on a pinned app launches it in split screen, like
    the dock. The drawer's long-press is a menu (App info, hide, pin to Home or dock,
    uninstall), the drawer's search sits at the bottom above the keyboard, and icon bitmaps
    are cached so scrolling does not re-decode them.

24. **The planning agenda is a direct port of SCHED**, not a summary of it. `SchedAgenda.kt`
    carries the rules from `useSchedAgendaState.js`, `schedAgenda.js` and the top of
    `SchedTaskCard.jsx` as pure functions with tests (the rolling window from the selected day,
    all-day-first ordering, the Overdue section with both kinds of lateness, deadline rows,
    empty-day hiding, the past-due / happening-now / finished-event treatment, the filter
    combo, the next-quarter-hour slot). `SchedView.kt` draws `SchedView.jsx`, `SchedTaskCard.jsx`,
    `SchedDayExtras.jsx` and `SchedFilterPopup.jsx` with the Tailwind metrics and colour stops
    carried over one to one. The differences are only what the launcher cannot do in place:
    a card opens the task in dayGLANCE instead of the in-app editor (an event card opens its
    day); the notes button and the Inbox deadline button open the task; a deadline card's
    calendar button schedules at 09:00 (today: the next quarter hour, 09:00 at the earliest)
    where the app opens a clock picker; a routine pill's long-press picker is not ported; the
    day header opens the day where the app shows the day's note and focus-log buttons; the
    filter sheet has no presets or recurring-tasks section and the view toggles live for the
    session, since the launcher has no recurrence data and does not share the app's
    localStorage. Pieces whose data the launcher does not hold (stars, Do sessions, plan
    history, subtasks) are absent rather than faked. Two glyphs were added here first and are
    now in the app's own SCHED too (decision 22): a calendar in the checkbox slot on an event
    card and a calendar-with-clock leading a deadline card. "Schedule for today" and "Postpone to
    tomorrow" are real writes through the repository (`moveTask`, the new `moveTaskToDay`),
    and putting an Inbox task on the clock drops its deadline and priority as the app does.

25. **Planning's lastGLANCE column gains "Coming up"**, a port of the People home's card: the
    next 30 days of birthdays and anniversaries as square tiles, soonest first, the week-out ones
    in the pink tint; a tile opens the person. The app draws three to a row, nine at most; the
    launcher's tablet column is wider, so four to a row, eight at most, with the name and date
    kind at the Reach out rows' sizes (14 and 12 sp) so the tiles are not mostly air; a phone takes the app's three and nine.
    Project cards go full width on a phone. It sits above
    "Reach out" because this is the planning screen. The app's "turning N" suffix is absent
    because the launcher's person record carries a month and day, not a year. The chore and
    people heatmaps are deliberately not on the launcher: the app has two of them now, one per
    mode, and a planning column has room for one at most.

26. **Jumps within the planning column.** The agenda can run long with Goals and Projects
    below it, so the SCHED controls row ends with "Goals" and "Projects" jump buttons (and
    "lastGLANCE" in portrait, where that section is in the same column), each of those headers
    carries a small up arrow, and a "Back to top" row closes the column. They animate the
    column's own scroll; the sections report their offsets in content coordinates, which the
    scroll does not move. To keep the row to one line on a phone the chips' side padding drops
    from 10 to 8 dp there, and SCHED's "Empty days" toggle moves off the row into the filter
    sheet, as a Show/Hide row beside Completed tasks and Past events.
27. **The landscape column gives up lastGLANCE rows before it crushes the week strip.** A
    Samsung tablet with a shorter column showed the strip at its minimum, overlapping the
    lastGLANCE wordmark. The column is now a `SubcomposeLayout` that measures everything below
    the strip with two, one and then no next-up rows on the cards and keeps the first that leaves
    the strip at least 120 dp; failing all three it shows the stats alone and gives the strip
    whatever is left, never under 56 dp. The strip caps at 220 dp as before. Nothing is tied to
    a device size, so any tablet lands on the rule its height allows.

28. **Phone Home is one screen.** Under 600 dp of width (the phone window class) Home is its
    own layout rather than the tablet portrait one squeezed, and nothing scrolls: the dial takes
    the full width (or the height left, if less) with no complications at that size, and under
    it the All day & deadlines pill, the six-item legend (Focus and Done dropped, as in tablet
    portrait, since the hub shows them), the pinned row and the search bar at their natural
    heights. The week strip and lastGLANCE are tablet-only; a scrolling version with both was
    tried first and rejected. Spare height (a tall phone, or the status bar hidden) is shared by
    every gap, up to 20 dp each, rather than pooling above and below the dial, which read
    bottom-heavy. Planning has not had its phone pass yet.

29. **Phones stay in portrait**, as the stock launchers do: `LauncherActivity` requests
    portrait when the smallest width is under 600 dp. Home has no landscape job on a phone, and
    the 336 dp-tall column beside a full-height dial would hold the header, the pill, the legend
    and the search bar and nothing else. Tablets keep both orientations.
30. **The hub at phone size follows the in-app dial.** The reference sizes the hub in viewport
    vmin, which on a phone is the dial's own width, so every clamp sits at its minimum there:
    clock 14, weekday 10, date 28, readout title 13, lines 11, tags 10, chevrons 20 with an 8
    px hit. Below a 420 dp dial the launcher's hub takes those values and the reference's
    tighter rhythm (3 / 4 / 5 / 5 dp between the rows) and widens its text column from 56 % to
    60 % of the dial; the tablet keeps the larger clock and chevrons it was tuned to. The long
    month's fallback to the short one is now measured in the serif against 44 % of the dial, the
    reference's `dialDateFits`, rather than estimated, so a phone shows "October 18" where the
    estimate had been dropping to "Oct 18". Two departures from the reference, at every size:
    the clock, weekday and date fill the top part from the bottom up so the divider sits 5 % of
    the dial below its midpoint, where the balanced tablet stack had put it, however many lines
    the readout runs to (the reference centres the whole stack), and on a phone the focus
    readout's "next at 6:00 PM · in 16m" is two lines, since the hub narrows toward its foot and
    the one line would span the chord. Phone sizes also set explicit line heights at the
    reference's ratios (1.5 for the sans, 1.25 for the serif, 2 dp between readout lines):
    Material's default 24 sp line had been padding a 10 sp weekday with 14 sp of air, and the
    chevrons' tap boxes now overflow the row instead of adding 32 dp to it, as the reference's
    absolutely positioned buttons do.

31. **The header pads for the camera cutout, not just the status bar.** With the status bar
    hidden its inset is zero but the cutout remains, and the header slid under the camera.
    `topBarsPadding` pads for the taller of the two.

32. **Real data, dayGLANCE first.** The first real-data PR lands in dayGLANCE
    (`docs/launcher-integration.md` there): `buildLauncherSnapshot` beside the widget builder,
    pushed on the same trigger; a read-only `LauncherProvider` at
    `content://com.dayglance.app.launcher/<collection>` with JSON-row cursors and
    `notifyChange` on every push; `com.dayglance.permission.LAUNCHER` as
    `signature|knownSigner` over the launcher's certificates; and `LauncherIntentReceiver`
    for `CREATE` and `COMPLETE`, idempotent on a client id. Three details decided there:
    launcher writes queue in their own pending slot (the automation slot is single-depth by
    design and would drop a burst of two completes from a dead app); the client id travels
    beside the payload, not inside it, because the intents package's payload schemas are strict
    and shared with Tasker; and `COMPLETE` from the launcher names the task by id, where the
    automation action matches by title. The certificate digests started as placeholders; the
    debug slot was filled on Oct 19 with the launcher's one certificate (its release build is
    debug-signed, so the debug key signs everything), the release and Play slots stay
    placeholders until the launcher gets a release key.

33. **Real data, lastGLANCE second.** The lastGLANCE PR (`docs/launcher-integration.md` there)
    follows the dayGLANCE pattern: a pure `buildLauncherSnapshot` pushed by its own hook on the
    widget snapshot's triggers, a read-only `LauncherProvider` at
    `content://com.lastglance.app.launcher/<collection>` for `chores`, `people` and `meta`,
    `com.lastglance.permission.LAUNCHER` over the same three launcher digests, and a
    `LauncherIntentReceiver` for `CREATE`, `COMPLETE` and `LOG_INTERACTION` with its own queue
    slot, drained ahead of the automation slot and deduped on the client id. Three details
    differ from dayGLANCE, each because lastGLANCE's data layer differs: `COMPLETE` names the
    chore by `chore_id` and logs a completion event (a chore is never "already complete", so
    only the client id dedupes it); `LOG_INTERACTION` uses the client id as the interaction's
    own `sync_id`, so the People layer's `interactionExists` backs the ledger across devices;
    and a created chore's `clientId` is joined into the snapshot from the ledger rather than
    stored on the row, because both lastGLANCE sync tiers map chore fields one by one and a
    new field would have to be taught to both engines and both apply paths for a fact the
    ledger already holds where the write landed. The launcher checks the created row and
    `meta.handledWrites` either way, so `ProviderRepository` retires an outbox entry by one
    rule for both apps. The snapshot's ids are the rows' `sync_id`s, and `people` carries
    `snoozedUntil` and `unreturned` so the launcher can hide and order as the app does.

34. **Real data, the launcher's half.** `ProviderRepository` is real and bound in `AppGraph`,
    with `FakeRepository` as its fallback. It sits on a `GlanceClient` seam (read a provider's
    rows, broadcast a write, observe an authority, is-installed, the Android 12 floor), so the
    repository, the snapshot parser and the outbox are unit-tested on the JVM with a scripted
    client; `AndroidGlanceClient` is the Android plumbing and nothing more. Choices made there:
    every flow is recomputed from three inputs (the latest snapshot per app, the pending writes,
    the fallback's flows), so demo and live mix per app with no branching in the UI. A write is
    applied to the launcher's copy at once as a `Patch`, persisted in the outbox under its
    client id (SharedPreferences, synchronous), broadcast to the app's launcher receiver with the
    id beside the payload, kept applied over every new snapshot until that snapshot shows the id
    (in `meta.handledWrites`, or on the created row), re-sent on each read of that provider and
    on every return to the foreground (at most every 15 s), and dropped after 24 hours.
    `MOVE`, `RESIZE`, `UNCOMPLETE` and `TOGGLE_ROUTINE` (`task_id` with `date` and `start`,
    `task_id` with `duration`, `task_id`, `routine_id` with `date`) are the dayGLANCE second PR's
    actions; that PR also records a write that fails for good in the ledger with its error, so
    the launcher retires the entry and its optimistic change reverts on the next snapshot
    rather than at expiry. The provider lists frames per day, so a launcher `Frame` gained `dates` and
    `appliesTo` prefers it to the weekday set; untimed routines are left out of the dial's band;
    a snoozed person (`snoozedUntil`) leaves Reach out as in the app. The header carries a
    one-line data status (`dataStatusText`): which app is on demo data, and the oldest live
    snapshot's age once past an hour. JVM tests get the real org.json because android.jar's copy
    is a stub that throws; in `tools/jvmcheck` the stub is cut out of the test android.jar, since
    file dependencies sort ahead of modules on the classpath.

35. **Moon and alarm on the dial.** With real days on the ring, two of the deferred layers:
    the moon band and phase glyph (`daydial/Lunar.kt`, a port of dayGLANCE's truncated Meeus
    series, and `DialSky.kt`, its `computeMoonBand` rules: the sun-down minutes the moon is up,
    opacity by altitude and lit fraction, the glyph at the moon's apex held off a stretch's
    ends) and the next clock-app alarm (`DialAlarm.kt`, the port of `dialAlarmMark`: tomorrow's
    alarm at 00 with its time beside the midnight label from 18:00 on, today's at its minute
    with the glyph alone, hour labels yielding as they do to the sun marks). The alarm comes
    from `AlarmManager.getNextAlarmClock()`, re-read each minute, with no app involved; the
    moon needs the observer's coordinates, which `DayShape` now carries as `lat` and `lon`.
    dayGLANCE's snapshot does not send them yet (its weather PR adds them beside the hourly
    weather), so on live data the moon appears once that lands; the demo seeds Denver so it
    shows there now. Phase values were checked against the January 2026 almanac (full on the
    3rd, new on the 18th).

36. **Weather on the dial, and the daylight band from the sky.** With dayGLANCE's snapshot
    carrying its hourly forecast (`weather`, one row per date), the weather location and the
    dial's alarm prefs, the last of the deferred layers: `daydial/DialWeather.kt` ports the
    weather ring's rules (WMO codes to rain or snow, spells merged by hour, the arc broken at its
    midpoint for one glyph, the day's peak UV) and `DayDial` draws hour temperatures as quiet
    numerals at the 3-hour stations inside the needle's root with precipitation as a thin arc,
    solid for rain and dashed for snow; `daydial/Solar.kt` ports `getSunElevation`, and
    `DialSky.daylightBand` replaces the half-sine with the sun's elevation against the latitude's
    best-day peak, scaled by the day's peak UV when the forecast has one. The ring and the
    elevation band appear only for dates the forecast or the coordinates cover; otherwise the
    dial draws as before. The alarm mark now follows dayGLANCE's own prefs (`dialAlarm`: on,
    from which hour), so the two dials agree. `GlanceRepository` gained a `weather` flow, and
    the demo seeds two days so the ring shows there.

37. **Freshness, measured from confirmation, per app.** The stale indicator measured the wrong
    thing: a snapshot is pushed only when its content changes, so `updatedAt` said when
    something last moved, not when the app last confirmed anything. Both apps now write a
    heartbeat beside the snapshot (their docs, "Freshness"): `checkedAt` on every run of the
    builder, changed or not, and on each foreground; `syncedAt` as the latest completed cycle
    across the sync tiers that are on, read from the engines so a disabled tier adds nothing.
    `Freshness.confirmedAt` is `syncedAt`, else `checkedAt`, else `updatedAt`, and the header
    names each app that is past an hour or on demo ("dayGLANCE 3h · lastGLANCE demo"), reading
    "live" otherwise so there is always something to long-press. The apps also report
    `rendererAlive`, a process static that a dead process answers false to by construction, and
    a `REFRESH` action routed by it: alive, the app runs every tier and re-pushes; dead,
    `deferred`; one in flight, `coalesced`. REFRESH is not a write (no outbox, no ledger; a
    client id for dedupe and logging only), and every request is logged in the app's meta with
    the liveness it saw and, from the web layer, `ran` or `failed` with timing and reason, so
    standby-bucket deferral and dead-process gaps are visible over the coming weeks. The
    diagnostics panel (long-press the header status) shows all of it per app and has a
    "Refresh now" button. Decided for the next steps: lastGLANCE stays on the alive path for
    now, with a one-day headless Capacitor Bridge spike scheduled right after this lands (its
    code throwaway, its result reported before anything builds on it); dayGLANCE gets a headless
    worker for the dead-process case, with headless mode as an ALLOWLIST (only the data layer,
    both sync tiers and the pushes initialise, so a future feature cannot run twice by default),
    a reverse lock for the live instance, and the refresh log recording each run.

38. **REFRESH on resume, throttled, and a device test script.** On every return to the
    foreground the launcher re-reads both providers and then asks each live app to confirm its
    data, at most once per app every two minutes and not at all when the app's last completed
    sync is under two minutes old (`ProviderRepository.maybeRequestRefreshes`). No schedule:
    periodic freshness while an app is alive is the app's own polling, and a periodic run of a
    dead app is the battery complaint dayGLANCE's widget plan already refused.
    `tools/freshness-test.sh` runs the scenarios against a connected device (app alive; process
    killed with `am kill`, never `force-stop`, which leaves an app unable to receive broadcasts
    or run jobs; after a reboot; in the rare standby bucket; under forced Doze; the user opening
    the app while a refresh is in flight) and prints, per scenario, the recorded outcome, the
    time to it and whether the snapshot updated, restoring the bucket and Doze state after. It
    drives the real path through two debug-build-only dev actions on the launcher
    (`DEV_REFRESH`, which sends one REFRESH under the launcher's own signature, and `DEV_DUMP`,
    which logs each app's freshness and refresh log as JSON) rather than having `adb shell`
    impersonate the launcher. The real acceptance test is cross-device: a change made on another
    device appears in this device's launcher, and the diagnostics panel explains any delay.

39. **lastGLANCE has no headless path; it stays on the alive path.** (Superseded by 46.) The one-day Capacitor
    Bridge spike, done from the Capacitor source rather than on a device: `Bridge.Builder`
    accepts only an `AppCompatActivity` or a `Fragment`, the private constructor takes the
    Activity and reads its Intent, `CapConfig.loadDefault(getActivity())` needs it, and every
    plugin resolves its Context through the bridge. There is no Activity-less way to boot
    the Capacitor WebView; the two alternatives are a translucent "headless" Activity, which
    comes to the foreground and is not headless, and a Capacitor-free mini-bridge that
    re-implements CapacitorHttp and the secure store for the sync layer alone (two to three
    days, and a second copy of the plugin surface to keep in step). Neither is worth it for
    a dead-process refresh that the alive path already covers most of the time: the Capacitor
    WebView keeps running in the background with `KeepRunning` on, so a killed lastGLANCE is
    the only case, and its REFRESH reads `deferred` until the user opens it. The spike's code
    was throwaway and none of it is kept; this decision is what it bought.

40. **dayGLANCE refreshes headlessly at a dead process (its PR; the launcher reads the
    result).** The receiver no longer answers `deferred`: with no renderer it enqueues an
    expedited WorkManager job that boots the web bundle in an Activity-less WebView, where a
    separate root (`src/headless/`) runs one cycle of every configured sync tier with the
    app's own payload and apply, pushes the snapshot (keeping the last weather, which it does
    not fetch) and the heartbeat, and reports. The rules the app's CLAUDE.md now carries:
    headless mode is an ALLOWLIST (the root composes only the data layer, both tiers and the
    pushes; the native bridge exposes the same list and nothing else, so a feature that must
    not run headlessly is one the root does not import); every synced slice is read by the
    same reader the app uses, because the payload would otherwise push a different default
    to every device as an edit; the reverse lock is SIGNAL ABORT, not wait-with-cap (the
    live instance destroys the headless WebView on the main thread before its own loads,
    and the root checks at each phase boundary, since the app runs its own cycle at start and
    a run has no local edits to lose, so waiting would only hold the splash); the tiers run
    one after the other (two applies in flight would each drop what the other pulled); ten
    minutes between headless runs, a two-minute cap (an alive cycle measured 75 s on a real
    account), and every step in the refresh log.
    What the launcher sees: `scheduled` at the receiver, then `startedAt`, `waitMs` (how
    long the OS held the job: a standby bucket or Doze shows up here, not as a lost request)
    and `headless: true` when it runs, then `ran` or `failed` with a reason, `aborted` (the
    app opened) or `throttled` (inside the floor); `meta.headlessRunning` while one is in
    progress. The parser, the diagnostics panel ("ran · headless · renderer dead · held
    12 s · 4100 ms") and `tools/freshness-test.sh` read all of it; the script waits out the
    floor before each dead-process scenario (about an hour for the full set, `--no-floor`
    to skip) and expects `aborted` from the overlap scenario on dayGLANCE.

41. **The first phone pass (Oct 21).** Thirteen findings from living with it on a phone, each
    fixed at its cause. Data: calendar events reached the launcher only for the days dayGLANCE
    happened to be drawing, and never after a headless refresh; dayGLANCE now sends its widget's
    fixed-window device-calendar fetch (yesterday through six days ahead) and a headless snapshot
    keeps the previous events (dayGLANCE's PR). The alarm mark read `AlarmManager`'s next alarm
    raw, which is any app's (calendar and task reminders included); `alarm/ClockAlarm.kt` ports
    dayGLANCE's GLANCEahead rule, clock apps only. The dial had no temperatures because dayGLANCE
    skipped its forecast when the header-weather switch was off, a switch a phone does not show;
    the native apps now always fetch it, and the labels have a 9sp floor so a phone-sized dial
    can read them. Sunrise and sunset drew the same sun; they are Lucide's two glyphs now, as in
    the app, and the readout drops its tag line on a phone-sized hub. Search showed other apps'
    icons: `AppIcon` used `produceState`, whose value is not keyed, so a reused row kept the
    previous app's bitmap. Interaction: pulling down on Home opens the notification shade
    (`StatusBarManager` by reflection, as launchers do, behind EXPAND_STATUS_BAR); pinned-app
    reordering dropped the drag after one swap because the items were not keyed, and the strip
    scrolls; Home's pinned row shows what fits and a "+N" button opens the rest in a pop-up with
    the editor. The drawer is four columns on a phone with a labels switch, and above the
    alphabetical list sits a row of the four apps last opened from the launcher; search opens
    with suggested apps (frecency, a week's half-life) and the recent queries that led somewhere.
    Both come from the launcher's own launch history (`prefs/LaunchHistory.kt`), not usage
    access, so they need no permission. Planning has a "+" for a task (the search parser, so
    "dentist friday 3pm" schedules), a chore, or a person, which opens lastGLANCE's own form
    through a new `lastglance://action/addPerson` link (its PR): a person has circles, dates and
    a cadence a one-line write would leave unset. Home's header carries each app's icon to open it.
    On a phone the dial is a full-width square and the content under it adapts, never the other
    way round: a dial squeezed by a few lines of chips broke its hub and labels. The "All day &
    deadlines" pill is two lines at most (one per row, the rest behind "+N", which opens the day
    in dayGLANCE), and a phone too short for everything drops the legend before the dial gives up
    any width (`PhoneColumnPlan`). The summary strip stays at six stats, but a stat with nothing
    to say that day (no routines, no frames, no sleep window) lends its place to a hidden one that
    has data, in place so nothing else moves (`LegendSlots`): Done first on a phone, where the dial
    has no corner readouts, Focus first on a tablet in portrait, whose corner already shows Done.
42. **Dock recents from the launch history; the calendar stays dayGLANCE's.** The dock asked
    for usage access before it showed a single recent app. Its recents now come from the
    launcher's own launch history, as the drawer's and search's do (`apps/DockRecents.kt`), so
    they work with no permission. Usage access is an opt-in line under the recents switch in
    Settings, never a prompt in the dock: with it, an app used anywhere (opened from a
    notification, say) counts too, the later of the two times wins, and a package shows as the
    activity last opened from here. Hidden apps no longer appear among the recents. Calendar
    events added in another app with dayGLANCE closed reach Home through dayGLANCE's own
    provider, which now reads the device calendar on every query with the user's calendar
    filter (its PR). The launcher considered reading `CalendarContract` itself and chose not
    to: that needs a calendar permission here and a second copy of dayGLANCE's event rules,
    while the provider already runs in a process holding the permission. Nothing changes on
    this side: the launcher re-reads the provider on every return to Home and whenever it
    notifies.
43. **A short phone keeps a one-row legend.** Decision 41 left the six-stat legend out when it
    did not fit beside a full-width dial, which on a phone with the search bar (no taskbar) left
    the spare height spread into wide gaps. A piece in the phone column can now name a smaller
    form (`PhoneColumnPlan` fallbacks): the legend first falls back to one row of Effort,
    Restore and Unblocked, which always have a value, and is left out only when even that does
    not fit. The dial still never gives up width while anything can give way.
44. **The launcher's own wordmark.** Home's header read "dayGLANCE" (with "launcher" beside it
    on tablets), though the launcher frames both apps. It now reads GLANCElauncher: "GLANCE" in
    Lora 700 in the text colour, "launcher" in Lora 500 italic, muted (`LauncherWordmark`). No
    brand colour, because orange means dayGLANCE and green lastGLANCE, and both apps' icons sit
    beside it. The family's case contrast stays, with the weight reversed since GLANCE comes
    first. Planning keeps dayGLANCE's wordmark, as Home's lastGLANCE section keeps lastGLANCE's:
    there it names whose data the page shows.
45. **Data status as a badge; a build footer.** With GLANCElauncher in Home's header there was
    no room for the status text ("dayGLANCE 3h"). The status now rides on each app's own icon in
    the header: nothing while its data is fresh, an amber dot once it is past an hour (or demo),
    where tapping the icon opens the app and so refreshes it. Long-press an icon for the
    diagnostics panel, which had been reachable only by long-pressing the status text. Settings
    gains a Data section (each app's status in words and a Diagnostics row) and a footer with the
    version, build time and commit (`BuildConfig.BUILD_TIME` and `GIT_SHA`, from Gradle value
    sources so the configuration cache cannot replay a stale time). Planning keeps its status text,
    since its header has room.

46. **lastGLANCE refreshes headlessly too (its PR; supersedes 39).** Decision 39 stands on
    its facts: Capacitor cannot boot without an Activity. What changed is the price of the
    Capacitor-free mini-bridge it priced at two to three days and a second copy of the plugin
    surface. lastGLANCE's sync engines work on its own IndexedDB rather than on React state, so
    there is no payload and apply to lift out of App, and a run touches only four plugins.
    `LauncherRefreshWorker` (ported from dayGLANCE's) boots the bundle in an Activity-less
    WebView at Capacitor's own origin, `https://localhost`, so it shares the app's storage, and
    its `HeadlessBridge` stands in for those four: the secure store (whose crypto moved into a
    class both use, not a copy), HTTP on OkHttp for WebDAV and both vault engines, and the
    launcher push. The rest is decision 40's: an allowlist, the signal-abort reverse lock, ten
    minutes between runs and a two-minute cap, every step in the refresh log. One difference:
    lastGLANCE registers its PWA service worker in the native build, which controls that origin,
    so right after an update a headless page can be served the previous, pre-headless bundle;
    the worker tears down a page that has not called the bridge within 20 s and logs why, and
    routes the service worker's update check to the installed files so the next run is current.
    A dead lastGLANCE's REFRESH now reads `scheduled` then `ran`, not `deferred`, and
    `tools/freshness-test.sh --app lastglance` waits out its floor as it does dayGLANCE's.

47. **Search finds apps' shortcuts, as Sesame does.** Search lists every app's shortcuts: the
    actions it offers on a long-press of its icon (manifest and dynamic), and the conversations
    messaging apps keep for the launcher (cached shortcuts, Android 11+), so "mom" offers the
    chat with Mom and "chrome inc" a new incognito tab. A shortcut matches by its own label (long
    or short, ranked as an app's name is), by app and label together (every word of the query
    starts a word of either), or by the app's name, or a word of it, which lists that app's
    shortcuts under it in the app's own order. At most five rows, below the apps; a hidden
    app's shortcuts stay hidden. A row shows the shortcut's own icon and its app ("Chrome ·
    Shortcut", "Signal · Conversation"), and a tap starts it through `LauncherApps.startShortcut`;
    one that has gone since the list loaded opens its app instead. `AppsRepository` loads the
    list off the main thread, keeps it current through `onShortcutsChanged` and package changes,
    and reloads when search opens. Android lets only the default home app read other apps'
    shortcuts, so the list is empty until GLANCElauncher is that app. The ranking is
    `SearchEngine.shortcutMatches`, tested on the JVM.

48. **Pinned shortcuts: "Add to Home screen", and an app's shortcuts on long-press.** An app's
    "Add to Home screen" (Chrome's page, a contact, a Maps route) is a request Android sends to
    the default launcher as `CONFIRM_PIN_SHORTCUT`; with no activity for it the request went
    nowhere. `PinShortcutActivity` now answers it with a small sheet (the shortcut's icon, its
    label and app, Cancel or Add); Add accepts the request, so Android pins the shortcut for
    this launcher, and puts it at the end of Home's pinned row. The pin list holds both kinds of
    key, an app's "package/class" and a shortcut's "shortcut:package#id" (`Pins`), so the row,
    its overflow and the editor show, launch, reorder and remove either. Android's own list of
    shortcuts pinned by this launcher follows Home (`Pins.pinChanges`, applied on every change to
    the pins): one removed from Home is released, so its app may delete it. Long-pressing an app
    in the drawer now lists its own shortcuts first, up to four in the app's order
    (`ShortcutMenu`, declared or published ones, not pins or cached conversations): a tap starts
    one, the pin beside it puts it on Home or takes it off. Both need GLANCElauncher to be the
    default home app, as decision 47's search does. Widgets (`CONFIRM_PIN_APPWIDGET`) stay
    unhandled: the launcher hosts none.

49. **Search falls back to sums, Settings, contacts and the web.** Four kinds of row join search,
    each ranked by a pure, tested piece of `ui/search`. A query that is arithmetic ("12*7",
    "(3+4)/2", "2^10") is answered first, and a tap copies the answer (`Calculator`: precedence,
    parentheses, right-associative powers, x and the typed signs; a bare number, a date such as
    "10/22" or "2026-10-22" and a dashed phone number are not sums, so quick capture keeps
    them). Settings pages come right after the apps ("bluetooth", "brightness", "dnd"),
    from a table of public Settings actions with keywords (`SettingsPages`, at most three;
    a device without a page opens Settings itself). Contacts are opt-in: Settings → Contacts in
    search asks for the contacts permission, and then names match by the start of their words
    and a tap opens the contact; without the setting or the permission nothing is read. Every
    search ends with "Search the web for …", the default web search app (or a browser search
    page where there is none).

50. **Notification dots.** An app with a notification to read shows a dot on its icon in the
    drawer, the taskbar and the pinned row, as Android's own launcher does. The source is a
    `NotificationListenerService` (`NotificationDotsService`) that the system binds only once the
    user grants notification access from the line under Settings → Notification dots; until
    then, or with the switch off, nothing is drawn. Which notifications count is pure and tested
    (`NotificationDots`, Launcher3's rules): the channel must allow badges, and ongoing ones
    (music, navigation, a foreground service), group summaries, empty ones, a paused app's and
    the launcher's own do not count. The set is rebuilt from the active list on every post,
    removal and ranking change, so a missed event cannot leave a dot stuck, and keyed by user,
    so a work-profile app's notification does not mark the personal copy. The listener reads
    only which app posted what kind of notification; no content is kept. The dot is the accent
    colour, not the header's amber status dot, and the header's dayGLANCE and lastGLANCE icons
    do not take one: there a dot already means the data is stale. Search, the editors and
    shortcut pins show none.

51. **Themed icons.** On Android 13 and later, Settings → Themed icons (off by default, as
    Android's own) draws every app that ships a monochrome icon layer as that layer in the
    GLANCE blues: dayGLANCE's today chip, blue-700 on blue-100 in light and blue-300 on slate in
    dark, both above 4.5:1. The tile is an `AdaptiveIconDrawable` of the palette colour with the
    tinted layer on top, so it takes the system's icon shape like every other icon. Apps
    without a monochrome layer keep their own colours, as on a Pixel, rather than being
    flattened into an unreadable tint. The palette and the bitmap cache key are pure and tested
    (`ThemedIcons`): each of plain, light and dark caches under its own key, so switching the
    setting or the theme never shows an icon drawn for the other. The monochrome layer is
    tinted on a copy, never on the cached drawable the plain icon shares. Shortcut icons stay
    as their apps draw them.

52. **Double-tap to lock.** On Android 9 and later, Settings → Double-tap to lock (off by
    default) locks the screen on a double-tap of Home's empty space. The only way a home app can
    lock and leave fingerprint and face unlock working is an accessibility service's
    `GLOBAL_ACTION_LOCK_SCREEN`; a device admin's `lockNow` demands the PIN on the next unlock and
    is harder to uninstall. `LockScreenService` asks for no events and cannot read window
    content, and does nothing but that one action. Turning the switch on first shows a
    disclosure (what the service is for, what it does not do, that Android's Accessibility page
    comes next), then opens that page; the line under the switch shows whether the service is
    running and opens the page again. A double-tap while the switch is on and the service off
    says where to turn it on rather than doing nothing (`DoubleTapLock`, pure and tested). The
    detector sits on Home's background only: the dial, cards, icons and search take their own
    taps, so a double-tap on them never locks, and with the switch off no detector is attached,
    so nothing waits to tell a single tap from a double one. There are no build flavors, so the
    service ships in every build. **For Google Play** the Console's accessibility declaration
    has to be filled in (the API is used for a non-accessibility purpose; the in-app disclosure
    is the prominent disclosure Play asks for, and a video of it is part of the declaration);
    without it the listing is rejected. The sideloaded build needs nothing further.

53. **Long-press on a pinned app opens its menu.** The drawer's long-press menu (the app's own
    shortcuts, App info, hide or show, pin or unpin on Home and the dock, uninstall) is now one
    shared `AppMenu`, and a long-press on Home's pinned row or its overflow pop-up opens it, so
    the same press offers the same things wherever the app sits. A pinned shortcut is not an
    app, so its menu is shorter (`AppMenuRows`, pure and tested): open its app, App info, remove
    from Home; hiding, the dock and uninstalling would act on an app the user did not press.
    The dock's apps, pinned and recent, open it the same way. Long-press and drag upward still
    opens the app in split screen: the menu appears when the press registers and the drag
    closes it. The long-press is the click handler's own, not only
    the drag detector's, because a click handler without one treats releasing a held press as a
    tap and would launch the app under the menu.

54. **A refresh log that says when nothing synced.** Overnight Oct 22 to 23 both apps were
    alive in the background and the launcher showed them stale for eight hours, with logs that
    hid why: dayGLANCE's read `ran · 1 ms` again and again while its last completed sync stayed
    at 22:58 (a vault cycle frozen mid-request held its in-flight guard, every later cycle
    returned at once, and REFRESH took any return for a completed cycle), and lastGLANCE's read
    `queued` for eight hours (its web layer never answered). Killing the apps fixed both, since
    the headless path starts fresh. The apps now log `skipped` with the reason when no cycle
    completed (dayGLANCE also joins a cycle in flight for up to 20 s first, keeping itself awake
    meanwhile), and `unanswered` when their minute-long keep-awake hold ran out with no answer,
    saying how late the hold began. The diagnostics panel colours `skipped` as a warning and
    `unanswered` as an error, and calls a request still `queued` two minutes on "never
    answered": the hold marks the ones it waited on, so one left queued means the hold itself
    never ran, which is what Android does when an app's expedited-job quota is spent
    (`RefreshLogView`, pure and tested). Releasing a stuck cycle outright (a watchdog) is left
    out: two overlapping vault cycles risk data, and the join may be enough.

55. **The Inbox complication shows dayGLANCE's own Inbox count.** It counted every open dateless
    task in the snapshot, and on a tablet showed far more than the app: the rows carry the
    multi-user lens but none of the Inbox filters (project tasks hidden by default, priority,
    tags, completed, standalone), and Bucket List items and completed tasks with a deadline were
    counted too. Reimplementing the filters here would drift from the app, and their preferences
    are device-local to it, so dayGLANCE now sends the count it shows (`inboxCount` in `meta`,
    from the one Inbox filter its badge, dial and headless refresh share) and the complication
    shows that (`InboxCount`, pure and tested). A dayGLANCE build that sends none, and the demo,
    fall back to counting rows as before. The planning screen's own lists are unchanged.

56. **The dial's clock and date open the clock and calendar apps.** As on a stock home-screen
    clock: a tap on the hub's clock (shown on today only) opens the default clock app on its
    alarms (`AlarmClock.ACTION_SHOW_ALARMS`, the one intent every clock app answers; it needs
    the normal-level `SET_ALARM` permission), and a tap on the date opens the default calendar
    at the date the dial shows (`content://com.android.calendar/time/<ms>`, falling back to the
    calendar's main screen for an app that cannot open a date). On another day the date still
    opens the calendar there, which is where the user was looking; the clock is not shown then.
    The instant is local noon (`SystemApps.calendarTimeMillis`, tested), so a calendar reading it
    in another zone or across a daylight-saving change lands on the same day. Both taps are
    optional `DayDialCallbacks`, so a host that passes none keeps plain text; they carry no
    ripple, as text on a clock face, but are labelled for accessibility.

57. **The dial follows dayGLANCE's layer toggles.** dayGLANCE's dial has a Layers panel (sun and
    moon, weather, calendar events, routines, focus, frames), device-local in the app. The
    launcher's dial now draws what the app's dial on the same device does: dayGLANCE sends the
    toggles in the snapshot's `dayShape.dialLayers` and the dial drops what is off (`DialLayers`,
    tested). Calendar events off drops imported events only; the user's own tasks, Obsidian's
    included, always draw. The blocks, the legend under the dial and the all-day chips follow the
    same filtered list, as the app's totals do. Sun and moon off takes the sunrise and sunset
    marks, the daylight band and the moon; weather off takes the temperature ring. Focus is the
    app's alone (the launcher has no focus data), and the alarm mark already follows the app.
    There is no launcher-side switch: one place to set it, as with the alarm and the day window;
    an override, like the 24-hour clock's, can come later if Home ever needs to differ. A build
    that sends no toggles, or names only some, draws every unnamed layer, as before.
58. **The hub's month form is decided per month.** The hub spells the month out ("November 24")
    when the long date fits 44 % of the dial and abbreviates it ("Nov 24") when not, measured
    in the serif as the app does. Measured per date, a phone's long months switched form
    partway through ("November 1", then "Nov 24"). Now the widest date of the month decides
    for every day in it (`DialGeometry.monthDateFits`, tested). On a phone that abbreviates
    the long names and keeps March to July whole; a tablet keeps every name. Measuring rather
    than listing months keeps other languages right. dayGLANCE's dial takes the same rule.
59. **The phone dial runs wider than the column.** On a phone the dial card sits 8 dp from the
    screen edges while the other pieces keep 16, and pads its face by 2 dp rather than 6, so the
    dial is about 24 dp larger across and down. The band beyond the ring holds only the 00 and
    12 labels and the sun glyphs (the 06 and 18 labels are never drawn, as in the app's compact
    dial), so the sides had room to spare. The header, pill, legend, pinned row and search bar
    keep their 16 dp margins. The extra height comes out of the spare gaps; a phone without them
    gives way as before (`PhoneColumnPlan`: the legend falls back, then goes, before the dial
    shrinks). Tablets are unchanged.
60. **Frames and the hub read as the app's dial does.** Four gaps closed (`DialFrames`, tested):
    a tap on a frame's outline (a 24 dp target, as the app's wide transparent stroke) reads it in
    the hub and lights its outline; where frames nest, the hub names the innermost one now is in,
    not the first listed; "available" comes from the frame's own free time, which dayGLANCE now
    sends per day (`frames[].slots`, computed as its dial does), and a build that sends none has
    the busy time merged, so overlapping blocks no longer count twice; and the hub's titles are
    medium weight, with no colour dot on the block it narrates unasked (one tapped keeps it).
61. **SCHED remembers its filters.** The agenda's colour, tag and project filters and its three
    show/hide toggles (empty days, completed tasks, past events) were kept for the session only,
    so they reset whenever the launcher's process restarted. They are now kept in the launcher's
    settings (`SchedViewPrefs`, tested). dayGLANCE keeps its toggles the same way; its filter
    selections are per session there, with named presets instead, and the launcher has no
    presets, so it keeps the selection itself. A remembered project that has been deleted is
    ignored rather than emptying the agenda (`SchedFilters.live`); how far the window was
    extended is not kept.
62. **The mic listens in the launcher, with no AI.** A tap opens search listening: speech is
    recognised on the device (Android 12's on-device recognizer where the phone has one, else the
    system recognizer asked to stay offline) and typed into the field as it is heard, so voice
    finds whatever typing does: apps and shortcuts, contacts, sums, Settings pages, GLANCE data,
    quick capture. A fixed set of leading verbs (`VoiceCommands`, tested) makes a command:
    open, call, text, timer, alarm, navigate, add or remind me to, talked to, search for. When
    the speaker stops, a command runs itself only when nothing is in doubt (one app, one person,
    or a timer, alarm, route, task or web search: `SearchEngine.autoRun`, tested); "call" and
    "text" open the dialer or messages with the number, never a call on their own; everything
    else stays as a search to tap. Typed, a command row follows the capture row, so Enter on a
    typed task still adds the task. The launcher now asks for the microphone, on the first tap
    only; denied, or on a phone with no recognizer, the mic falls back to dayGLANCE's voice
    capture, which a long press always opens. No wake word: always-on listening is the default
    assistant's alone, and costs battery. On-device language models (Gemini Nano on some
    phones) could take looser phrasing later; the grammar stays the fallback.
63. **Settings export to a file and import back.** A build signed with a different key cannot
    install over the debug-signed one, so moving to a release key means an uninstall, and the
    launcher's settings live only in its own storage. Settings → Backup writes them to a JSON
    file the user picks and reads one back (`SettingsBackup`, tested): pins, hidden apps, theme,
    every toggle, the agenda's filters, launch history and recent searches. A file is a whole
    snapshot: importing one replaces every setting it covers, after a confirmation, and a setting
    the file lacks goes back to its default. A value of the wrong type is skipped by name rather
    than refusing the file; a file from a newer format version is refused. Permissions and the
    Home-app choice are Android's and stay out; the wallpaper colour last applied is per-device
    bookkeeping and stays out too. A test fails when a key is added to `LauncherPrefs` without
    being declared to the backup, so a new setting cannot be silently left out of it.
64. **Swipe down closes the drawer.** On the header, or on the grid once it is scrolled to the
    top, a downward drag pulls the drawer down (it follows at half the pull) and closes it on
    release past 64 dp of pull, or on a flick down; short of that it springs back, and dragging
    back up unwinds the pull before the grid scrolls again (`DrawerPullToClose`, tested). Only a
    finger starts a pull: a fling that carries the grid to its top stops there, so scrolling up
    fast through the list never closes it by accident.
65. **A closed page reads as handled.** Both apps now close a backgrounded page that stopped
    answering and schedule a headless run in its place (their LauncherPageRetire): the
    `unanswered` entry gains `pageClosed: true`, and the run is logged as `<clientId>-headless`.
    The panel shows such an entry amber with "page closed" instead of red (`RefreshLogView`,
    tested), since the app recovered by itself; an unanswered request it left open stays red.
    `tools/freshness-test.sh` follows the `-headless` run and records its outcome. Ages in the
    panel read "just now" rather than "now ago" (`formatAgo`).
66. **A routine tap sends the state it asks for.** `TOGGLE_ROUTINE` carries `completed`, the
    opposite of what the launcher shows for the routine, and the pending patch sets that state
    rather than flipping (`Outbox.applyToRoutines`, tested). dayGLANCE sets it too, and changes
    nothing when it already holds. A flip undid a tick whenever the launcher's view was behind:
    a routine ticked on another device and not yet synced, or a snapshot that carried the
    change before the ledger did (the bug dayGLANCE #2228 fixed showed exactly that). An older
    dayGLANCE ignores the field and flips, as before; an entry saved before it was recorded
    still flips.
67. **Goals run by target date, then by progress.** The Planning screen's Goals section lists
    active goals soonest target date first, so an overdue goal leads, as dayGLANCE's Goals
    screen does (`sortGoalsForCarousel`). Goals with no target date follow, most complete
    first. That part differs from the app on purpose: the app keeps them in creation order,
    and the user asked for progress. Ties keep the snapshot's order (`SchedAgenda.goalsInOrder`,
    tested). It was the snapshot's order throughout, which read as random.
68. **Pinned chores lead the lastGLANCE section.** lastGLANCE marks each chore row `pinned`
    (pinned and in season, its Pinned row's rule; lastGLANCE #394). The Planning screen lists
    every pin above Due soon, in the app's order (`Cadence.pinned`, tested: most overdue first,
    no cadence or never done last, ties by name), with the chore's category leading the
    subtitle as on the app's pinned tiles. Nothing shows when nothing is pinned or lastGLANCE
    predates the field. A pin that is due also shows under Due soon, within its cap of eight,
    as a pin also shows in its own category in the app. The pin icon opens `lastglance://pinned`
    (lastGLANCE #395): Chores with Soon off, on the Pinned tab on a phone. It first reused the
    flame's Soon link, which turned Soon on.
69. **A caught-up Home card shows what is up next.** When nothing needs doing in Chores or
    People, the card used to sit empty beside a busy one. It now lists what comes due next in
    the same rows, quieter, with "All caught up · up next" on its header line: on-track chores
    and people not yet due (snoozed people left out), soonest first. The note sits on the
    header line so it adds no height, and side by side a caught-up card lists no more rows
    than the busy card's due rows, so it can never make the pair taller than the busy card
    alone (`LastGlanceRows`, tested). Beside another caught-up card, or stacked, it takes the
    usual rows. A layout with no room for rows (the phone) is unchanged.
70. **The day turns over by itself.** The dial's day and the Planning month were set to today
    when the launcher started and never moved, so after midnight they stayed on yesterday. The
    controller now keeps its own `today` and checks it each minute and each time the launcher
    comes back on screen (the minute's delay does not run while the device sleeps). Once the
    date turns, a view still on the old today moves to the new one, and a day the user went to
    stays put; the month follows only with the selected day, and only from the old today's
    month (`DayRollover`, tested). The month view's today highlight reads the same `today`.
71. **Apps open with their icon splash.** From Android 13 the system draws a launched app's
    splash as its bare background colour unless the launch asks for the icon, as a stock
    launcher's does. The launcher asked for nothing, so dayGLANCE opened on a blank navy
    screen for as long as it took to load (about four seconds on a Galaxy phone). Every app
    launch from Home now passes `LaunchOptions.iconSplash()`: the dayGLANCE and lastGLANCE
    links, the drawer and pins, app shortcuts, the dial's clock and calendar, and search
    actions. Checked on a phone; nothing to test on the JVM, since the options are the
    platform's.
72. **Back on Home does nothing.** Back closes an overlay, or returns Home from Planning. On
    Home with nothing open, the back handler was switched off, so the gesture went to the
    system, which closed the launcher's task and opened a new one: Home paged away and back.
    A launcher is the bottom of the back stack, so the handler now stays on and ignores back
    there. Checked on a tablet: back on Home keeps the same task with no transition, and back
    from Planning and from the drawer behaves as before.
73. **Testers get 0.5.0, signed with the launcher's own key.** A release build reads the key from
    a git-ignored `keystore.properties` or the environment, and refuses to package without it
    rather than fall back to debug: both apps admit the launcher by this key. The refusal is at
    packaging, so a clone or CI with no key still configures, unit-tests and builds debug. Where
    the key is present, debug builds use it too, since a debug-signed build could neither install
    over the release launcher nor be admitted by release apps. `minSdk` goes from 26 to 31: below
    Android 12 the apps cannot admit another signer, so older devices could only ever show demo
    data. Settings lists the open source licences the APK must carry, from `licenses/`.
74. **Settings is grouped.** It had grown into one list in the order things were added, with the
    taskbar's rows apart from each other and "Set as Home app", a new user's first need, near the
    bottom. It now opens with that button, then Appearance, Home, Taskbar, Apps, Search, Backup and
    About. Recents only change what the taskbar shows, so that row hides with the taskbar, as its
    labels row did. On screen it is the taskbar throughout; nothing stored changed, so
    an exported settings file still imports.
75. **Settings explains the restricted-settings step.** From Android 13 an app installed from a
    downloaded file cannot be given notification or accessibility access until it is allowed in
    App info, and Android only offers that after one refused attempt. No app can skip it or tell
    that it was blocked, so while either access is off a hint under its link gives the steps, with
    a button to App info. A Play install is exempt, so the hint hides there and needs no removing
    at launch.
76. **Chore days match lastGLANCE.** "Tomorrow" and "in 2d" came from the fraction of a day left,
    so a three-day chore done two days ago, in the morning, read "today" where the app read
    "tomorrow". The labels now follow the app's `formatElapsed` and `formatNextDue`: "due now" by
    time elapsed, everything else by the calendar. The one difference is on purpose: a chore done
    just after midnight and seen just before the next one reads "23h ago", where the app rounds
    to one day and reads "0d ago". Planning's clock also ran only when the data changed, so with
    the app closed its days and fill bars could lag by hours; it now ticks each minute, like Home.
77. **Chore icons find their drawable under any spelling.** lastGLANCE's drawables date from June
    and hold one file per icon, 730 of them under the `Lucide*` alias. Since its #404 the picker
    offers plain names, so a chore could store `ShowerHead` with only
    `ic_lucide_lucide_shower_head` to find, and the launcher drew its fallback for 842 of the
    picker's 1,767 icons. The lookup now tries the stored name, the name with and without the
    prefix, and lucide's other names for the icon (`assets/lucide-aliases.txt`, generated by
    `tools/gen-lucide-aliases.mjs`). That leaves 64 icons newer than the drawables, which still
    fall back until lastGLANCE regenerates them.
78. **A project on hold is open, listed last, and asks for nothing.** dayGLANCE's #2344 added
    an `on-hold` project status: a pause, neither done nor put away. The launcher read the
    unknown value as active, so a paused project kept its place and its target-date warning.
    `GoalStatus.ON_HOLD` now carries it. The Planning screen's Projects cards and the SCHED
    filter's project groups still list it, after the active projects (dayGLANCE's
    `pausedLast`), and the card says "On hold" where the target date goes, with no warning. It
    still counts toward its goal's progress, since `ProgressMath.goal` already counts every
    project that is not archived. dayGLANCE never sends the status on a goal.
