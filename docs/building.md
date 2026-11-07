# Building GLANCElauncher

How to build, install and test the launcher from source, and where things are in the
repository.

## What you need

- **A JDK from 17 to 24.** Gradle 8.14 cannot run on Java 25: with a Java 25 `JAVA_HOME` the
  build fails with nothing but the version number as the message (`What went wrong: 25.0.2`).
  Android Studio's bundled JDK works.
- **The Android SDK**, found through `ANDROID_HOME` or a `local.properties` file with
  `sdk.dir=/path/to/sdk`. The Android Gradle plugin fetches the platform and build tools it
  needs.
- **adb**, on `PATH` or under the SDK's `platform-tools`, to install on a device.

The Gradle wrapper fetches Gradle itself. The toolchain is Gradle 8.14, AGP 8.13, Kotlin 2.2 and
the Compose BOM 2025.09, with compileSdk and targetSdk 36 and minSdk 31 (Android 12). Versions
live in `gradle/libs.versions.toml`.

Both build scripts source `tools/build-env.sh`, which picks the JDK and the SDK. For the JDK it
tries, in order: `--java /path/to/jdk` if given, `JAVA_HOME` if it is 17 to 24, Android Studio's
bundled JBR in the usual install locations, `/usr/libexec/java_home` on a Mac, then `~/.jdks`,
`/usr/lib/jvm` and `/Library/Java/JavaVirtualMachines`. For the SDK it reads `ANDROID_HOME`,
`ANDROID_SDK_ROOT`, `local.properties`, then the default install locations. It stops with a
message saying what to install if it finds neither.

## Signing, and why your own build shows demo data

The launcher reads dayGLANCE's and lastGLANCE's data through permissions that each app grants
only to launchers signed with a key it trusts (see "Permissions" in the [README](../README.md)).
The apps trust the release key only. A build from source is signed with your own Android debug
key, so it installs and runs, but both apps refuse it and it shows the seeded demo data in their
place. Everything that does not need the apps (the drawer, search, the taskbar, Settings) works
as usual.

Release builds need the release key and refuse to package without it, rather than fall back to
the debug key. The key comes from a git-ignored `keystore.properties` at the repository root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

or from the environment variables `GLANCE_KEYSTORE_FILE`, `GLANCE_KEYSTORE_PASSWORD`,
`GLANCE_KEY_ALIAS` and `GLANCE_KEY_PASSWORD`. The refusal happens at packaging, so a checkout
with no key still configures, runs the unit tests and builds debug. Where the key is present,
debug builds use it too, so they install over the release build and the apps keep answering
them. A build signed with one key cannot install over a build signed with another: switching
means uninstalling first (`adb uninstall app.glance.launcher` removes it from every user
profile). Settings → Backup can export the launcher's settings before that and import them
after.

## Building and installing for development

`./build-and-install.sh` builds the APK, copies it to `outputs/`, installs it over adb with
`adb install -r` and launches it:

```
./build-and-install.sh                 debug APK, install on the connected device
./build-and-install.sh --release       release APK; fails without the release key
./build-and-install.sh --clean         gradle clean first
./build-and-install.sh --no-install    build only; the APK path is printed
./build-and-install.sh --device SERIAL pick a device when several are attached
./build-and-install.sh --set-home      after installing, open the "Default home app" settings
./build-and-install.sh --tests         run the JVM unit tests before building
./build-and-install.sh --java /path/to/jdk   JDK to run Gradle with
./build-and-install.sh --help
```

Flags combine, for example `./build-and-install.sh --clean --release --set-home`. The APK lands
in `outputs/glancelauncher-debug.apk` or `outputs/glancelauncher-release.apk`. This script makes
no AAB, and it does not check which key signed a `--release` APK.

By hand, the equivalent is `./gradlew assembleDebug` followed by
`adb install -r app/build/outputs/apk/debug/app-debug.apk`. If you run `./gradlew` directly,
set `JAVA_HOME` to a JDK 17 to 24 first, or put `org.gradle.java.home=/path/to/jdk` in
`~/.gradle/gradle.properties`. Then pick GLANCElauncher when pressing Home, or use Settings →
"Set as Home app" inside the launcher.

## Building a release

`./build-release.sh` takes no flags and does the same thing every time:

1. Stops if tracked files have uncommitted changes, then switches to `main` and pulls
   (fast-forward only).
2. Asks for the versionCode, suggesting one more than its last build that passed the check
   (recorded in `outputs/.last-versioncode`) or, the first time, one more than the fallback in
   `app/build.gradle.kts`. Return takes the suggestion; without a terminal it takes it
   unasked. The code goes to Gradle as `-PversionCode`, so nothing is committed.
3. Builds the release APK (for sideloading) and the AAB (for Google Play).
4. Checks that both are signed with the release key, by its SHA-256 digest, and stops if
   either is not.
5. Only then writes `outputs/glancelauncher-<version>.apk` and `.aab`, replacing an earlier
   build of the same version, and prints the version, the versionCode and each file's SHA-256.

It needs the same JDK and SDK as above, plus the release key. Every build gives a different
SHA-256, since the build time is inside the APK, so publish the exact file whose digest it
printed.

## Tests

```
./gradlew :daydial:test :app:test
```

These are JVM unit tests: the Day Dial's geometry and gestures (ported from dayGLANCE's
`dayDial.test.js`), quick capture, search ranking, the snapshot parser, the outbox and the
repository against a scripted client, settings backup, and the rest of the pure pieces. The
`Android` workflow in `.github/workflows/android.yml` runs them and builds the debug APK on
every pull request and on `main`.

### Freshness on a device

`tools/freshness-test.sh` measures how quickly the apps answer the launcher's REFRESH requests
on a connected device: with the app alive, killed, after a reboot, in the rare standby bucket,
under forced Doze, and with the user opening the app while a refresh is in flight. It prints one
line per scenario with the outcome the app recorded, the time to it and whether the snapshot
updated, and restores the standby bucket and Doze state afterwards.

```
tools/freshness-test.sh [--app lastglance] [--only alive,killed] [--device SERIAL]
tools/freshness-test.sh --help
```

It drives the real path through two actions that only debug builds of the launcher have, so it
needs a debug build installed and set as Home, and the app under test must admit that build
(one signed with the release key). Each dead-process scenario first waits out the app's
ten-minute floor between headless runs, so the full set takes about an hour; `--no-floor` skips
the wait. See decisions 38, 40 and 46 in [decisions.md](decisions.md).

### Chore icon aliases

`tools/gen-lucide-aliases.mjs` regenerates `app/src/main/assets/lucide-aliases.txt`, the lucide
icons known under more than one name, from a lastGLANCE checkout. Rerun it when lastGLANCE moves
its `lucide-react` version (decision 77):

```
node tools/gen-lucide-aliases.mjs ../lastGLANCE
```

## Layout of the repository

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml   Gradle build and versions
daydial/        Day Dial library module. Compose only; no dependency on :app.
app/            The launcher: HOME activity, screens, data, search, taskbar, drawer.
licenses/       What the APK carries (Lora, Lucide, Apache-2.0 libraries); Settings shows these files.
tools/          Build environment, the freshness test and the icon alias generator.
docs/           The spec, the decision log and these notes.
```

### `:daydial` (the shared Day Dial)

`app.glance.daydial` is the Compose rewrite of dayGLANCE's **in-app** Day Dial
(`src/components/DayDial.jsx`, `src/utils/dayDial.js`, `src/utils/intervalLanes.js` in the
dayGLANCE repo), not its widget.

- `DialGeometry.kt`: pure geometry in the reference's 1000-unit box. Midnight at the top,
  clockwise, 0.25° per minute; radii (ring 300 to 385, ticks 400 to 436, bezel 424, labels 478);
  the `min(3, span/6)` angular pad; intensity by duration saturating at 180 minutes; the
  concentric-lane packing for overlaps (`IntervalLanes.kt`); HSL muting (saturation cap 0.5,
  lightness 0.73); tone states (completed, owed, past); midnight clipping with the previous
  day's overrun carried in; the focus-block rule (running wins, nested beats container, else
  next); labels yielding to the sun marks within 30 minutes.
- `DayDial.kt`: the composable. Layers bottom to top: face and bezel, ticks, hour labels,
  daylight band, sleep, sun marks, schedule wedges (fill, glow, crisp outer edge, check on
  completed), focus rail, frame outlines, routine bars with the name chip, now needle with
  the afterglow trail. The hub in the centre shows the clock, weekday with day chevrons, the
  Lora date, and the readout (inspected block, current frame with time available, running or
  next block, or "Nothing else scheduled"). Tap a block to inspect it; tap again for the
  action sheet (mark complete, start focus when running, open in planner).
- `Lunar.kt`, `Solar.kt`, `DialSky.kt`, `DialWeather.kt`, `DialAlarm.kt`: the moon, the
  daylight band, the weather ring and the alarm mark (decisions 35 and 36).
- `DayDialCallbacks.kt`: every data-changing interaction is a callback. The dial never writes.
- `src/test`: the reference's `dayDial.test.js` rules ported as JUnit tests (angles, ticks,
  padding, lanes, clipping, tone, focus block, drag maths, hit testing).

The module keeps the single dark look of the reference in both launcher themes; that is a
deliberate property of the in-app dial.

### `:app` (the launcher)

- `data/GlanceRepository.kt`: the one interface the UI reads and writes through.
  `ProviderRepository` is the real backend (decision 34): the two apps' ContentProviders read
  through `data/provider/GlanceClient.kt`, rows mapped by `data/provider/SnapshotParser.kt`,
  writes held in `data/provider/Outbox.kt` until a snapshot shows them. `FakeRepository` is the
  seeded demo it falls back to, per app. `di/AppGraph.kt` holds the single binding.
- `ui/LauncherRoot.kt`: a two-page pager on every size, Home as the default page and planning
  a swipe away. Home presses and Back return to Home. The taskbar renders under both.
- `ui/home/`: Home, with the dial as the star. Landscape: the dial fills the height on the
  left; beside it the All Day pill, the breakdown (the in-app legend), the week strip, the
  lastGLANCE cards, one row of pinned apps and the search bar with the mic. Portrait stacks the
  same column under a full-width dial. Nothing scrolls; swipe up opens the drawer. The dial's
  four corners carry complications: a Done ring, Inbox and Deadlines counts, and lastGLANCE's
  Due now count.
- `ui/planning/`: landscape is one column per app, each scrolling on its own: dayGLANCE
  (month grid on the in-app cell metrics, the SCHED agenda for the selected day, goal rings
  with per-project bars, project cards) and lastGLANCE (pinned and due chores, Coming up,
  Reach out). Portrait stacks them. Taps deep-link into the apps.
- `ui/drawer/`: alphabetical searchable drawer; long-press opens the app menu.
- `ui/dock/`: the GLANCE taskbar; pinned on the left, recents on the right.
- `ui/search/SearchEngine.kt` + `quickadd/QuickAddParser.kt`: search over apps, shortcuts,
  Settings pages, contacts and GLANCE data, quick capture with dayGLANCE's natural-language
  grammar, `lg ` prefix or chip for lastGLANCE.
- `ui/settings/`, `ui/diagnostics/`: Settings, and the diagnostics panel (long-press either
  app's icon in Home's header).
- `system/`: the Android pieces: the notification-dot listener, the lock-screen accessibility
  service, the notification shade, on-device voice recognition and contacts for search.
- `intents/GlanceIntents.kt`: `dayglance://` and `lastglance://` deep links and the voice
  intent.
