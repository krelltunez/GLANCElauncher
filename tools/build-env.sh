# GLANCElauncher: the JDK and Android SDK for a Gradle build. Sourced, not run, by
# ./build-and-install.sh and ./build-release.sh.
#
# Reads ROOT (the repository root) and, if set, JAVA_OVERRIDE (a JDK home to use). Exports
# JAVA_HOME and ANDROID_HOME and sets SDK_DIR, or exits with what to install.

# ── JDK ────────────────────────────────────────────────────────────────────
# Gradle 8.14 runs on Java 17 to 24. A newer JDK on PATH (Java 25 is common now) makes the
# build fail with nothing but the version string as the error message, so pick a usable one.
java_major() {  # prints the major version of the JDK at $1, or nothing
  local v
  v="$("$1/bin/java" -version 2>&1 | grep -m1 ' version ' | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
  [ -n "$v" ] && echo "$v"
}
java_ok() { local m; m="$(java_major "$1" 2>/dev/null || true)"; [ -n "$m" ] && [ "$m" -ge 17 ] && [ "$m" -le 24 ]; }

if [ -n "${JAVA_OVERRIDE:-}" ]; then
  java_ok "$JAVA_OVERRIDE" || { echo "--java $JAVA_OVERRIDE is not a JDK 17 to 24"; exit 1; }
  export JAVA_HOME="$JAVA_OVERRIDE"
elif [ -n "${JAVA_HOME:-}" ] && java_ok "$JAVA_HOME"; then
  :
else
  FOUND=""
  CANDIDATES=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "${LOCALAPPDATA:-}/Programs/Android Studio/jbr"
    "/opt/android-studio/jbr" "$HOME/android-studio/jbr" "/snap/android-studio/current/jbr"
  )
  for d in "${CANDIDATES[@]}"; do [ -n "$d" ] && [ -d "$d" ] && java_ok "$d" && FOUND="$d" && break; done
  if [ -z "$FOUND" ] && [ -x /usr/libexec/java_home ]; then
    for v in 21 17 22 23 24 20 19 18; do
      d="$(/usr/libexec/java_home -v "$v" 2>/dev/null || true)"
      [ -n "$d" ] && java_ok "$d" && FOUND="$d" && break
    done
  fi
  if [ -z "$FOUND" ]; then
    for d in "$HOME"/.jdks/* /usr/lib/jvm/* /Library/Java/JavaVirtualMachines/*/Contents/Home; do
      [ -d "$d" ] && java_ok "$d" && FOUND="$d" && break
    done
  fi
  if [ -n "$FOUND" ]; then
    export JAVA_HOME="$FOUND"
  else
    CURRENT="$(java -version 2>&1 | grep -m1 ' version ' || true)"
    echo "No JDK 17 to 24 found (current: ${CURRENT:-none}). Gradle 8.14 cannot run on Java 25."
    echo "Install one (e.g. Temurin 21, or Android Studio, whose bundled JBR is picked up automatically)"
    echo "and re-run, or pass --java /path/to/jdk."
    exit 1
  fi
fi
echo "==> JDK: $JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | grep -m1 ' version '))"

# ── SDK ────────────────────────────────────────────────────────────────────
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f "$ROOT/local.properties" ]; then
  SDK_DIR="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | sed 's/\\:/:/g')"
fi
if [ -z "$SDK_DIR" ]; then
  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "${LOCALAPPDATA:-}/Android/Sdk"; do
    [ -n "$candidate" ] && [ -d "$candidate" ] && SDK_DIR="$candidate" && break
  done
fi
if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
  echo "Android SDK not found. Set ANDROID_HOME, or create local.properties with sdk.dir=/path/to/sdk."
  exit 1
fi
export ANDROID_HOME="$SDK_DIR"
