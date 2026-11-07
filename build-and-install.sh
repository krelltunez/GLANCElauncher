#!/bin/bash
set -euo pipefail

# GLANCElauncher: build the APK with Gradle and install it over ADB.
#
# Usage:
#   ./build-and-install.sh                 debug APK, install on the connected device
#   ./build-and-install.sh --release       release APK; fails without the release key
#   ./build-and-install.sh --clean         gradle clean first
#   ./build-and-install.sh --no-install    build only; the APK path is printed
#   ./build-and-install.sh --device SERIAL pick a device when several are attached
#   ./build-and-install.sh --set-home      after installing, open the "Default home app" settings
#   ./build-and-install.sh --tests         run the JVM unit tests before building
#   ./build-and-install.sh --java /path/to/jdk   JDK to run Gradle with (17 to 21 recommended)
# Flags combine, e.g. ./build-and-install.sh --clean --release --set-home
# Once keystore.properties exists, debug builds carry the release key too (app/build.gradle.kts).
# Files to publish (the checked APK and the AAB for Play) come from ./build-release.sh, not this script.
#
# Needs: the Android SDK (ANDROID_HOME or a local.properties with sdk.dir), a JDK between 17
# and 24 (Gradle 8.14 cannot run on Java 25: the build fails with just the version number,
# e.g. "What went wrong: 25.0.2"), and adb on PATH or under $ANDROID_HOME/platform-tools.
# tools/build-env.sh looks for a usable JDK in JAVA_HOME, Android Studio's bundled JBR, ~/.jdks
# and the system JVM directories. The Gradle wrapper fetches Gradle itself.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/outputs"

CLEAN=false
RELEASE=false
INSTALL=true
SET_HOME=false
RUN_TESTS=false
SERIAL="${ANDROID_SERIAL:-}"
JAVA_OVERRIDE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --clean)      CLEAN=true; shift ;;
    --release)    RELEASE=true; shift ;;
    --no-install) INSTALL=false; shift ;;
    --set-home)   SET_HOME=true; shift ;;
    --tests)      RUN_TESTS=true; shift ;;
    --java)
      [ $# -ge 2 ] || { echo "--java needs a JDK home directory"; exit 1; }
      JAVA_OVERRIDE="$2"; shift 2 ;;
    --device)
      [ $# -ge 2 ] || { echo "--device needs a serial (see: adb devices)"; exit 1; }
      SERIAL="$2"; shift 2 ;;
    -h|--help) sed -n '3,23p' "$0"; exit 0 ;;
    --bundle)  echo "The AAB comes from ./build-release.sh, which also checks the key and names the files."; exit 1 ;;
    *) echo "Unknown flag: $1 (try --help)"; exit 1 ;;
  esac
done

# ── JDK and SDK ────────────────────────────────────────────────────────────
ROOT="$SCRIPT_DIR"
# shellcheck source=tools/build-env.sh
source "$SCRIPT_DIR/tools/build-env.sh"

# ── adb ────────────────────────────────────────────────────────────────────

ADB="$(command -v adb || true)"
[ -z "$ADB" ] && [ -x "$SDK_DIR/platform-tools/adb" ] && ADB="$SDK_DIR/platform-tools/adb"
if $INSTALL && [ -z "$ADB" ]; then
  echo "adb not found on PATH or in $SDK_DIR/platform-tools. Install platform-tools or pass --no-install."
  exit 1
fi

# ── Build ──────────────────────────────────────────────────────────────────
cd "$SCRIPT_DIR"
GRADLE_TASKS=()
$CLEAN && GRADLE_TASKS+=(clean)
$RUN_TESTS && GRADLE_TASKS+=(:daydial:test :app:test)
if $RELEASE; then
  GRADLE_TASKS+=(:app:assembleRelease)
  APK="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
  OUT_APK="$OUT_DIR/glancelauncher-release.apk"
else
  GRADLE_TASKS+=(:app:assembleDebug)
  APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
  OUT_APK="$OUT_DIR/glancelauncher-debug.apk"
fi

echo "==> ./gradlew ${GRADLE_TASKS[*]}"
./gradlew "${GRADLE_TASKS[@]}"

[ -f "$APK" ] || { echo "Expected APK not found at $APK"; exit 1; }
mkdir -p "$OUT_DIR"
cp "$APK" "$OUT_APK"
echo "==> APK: $OUT_APK ($(du -h "$OUT_APK" | cut -f1))"

$INSTALL || exit 0

# ── Install ────────────────────────────────────────────────────────────────
# (No arrays here: macOS ships bash 3.2, where an empty array trips `set -u`.)
run_adb() {
  if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}

DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
COUNT="$(printf '%s\n' "$DEVICES" | sed '/^$/d' | wc -l | tr -d ' ')"
if [ "$COUNT" -eq 0 ]; then
  echo "No device in 'adb devices'. Enable USB debugging and authorise this computer, then re-run."
  "$ADB" devices
  exit 1
fi
if [ "$COUNT" -gt 1 ] && [ -z "$SERIAL" ]; then
  echo "Several devices are attached; pick one with --device SERIAL:"
  "$ADB" devices
  exit 1
fi

echo "==> adb${SERIAL:+ -s $SERIAL} install -r $OUT_APK"
run_adb install -r "$OUT_APK"

PACKAGE="app.glance.launcher"
if $SET_HOME; then
  echo "==> Opening the default Home app settings on the device"
  run_adb shell am start -a android.settings.HOME_SETTINGS >/dev/null
else
  echo "==> Launching $PACKAGE"
  run_adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  echo "    To make it the Home app: ./build-and-install.sh --set-home, or Settings → Apps → Default apps → Home app."
fi
echo "Done."
