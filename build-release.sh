#!/bin/bash
set -euo pipefail

# GLANCElauncher: build what to publish. Switches to main, pulls, builds the release APK (for
# sideloading) and AAB (for Play) with the versionCode it asks for, and checks both carry the
# release key. Only then does it write
# them to outputs/glancelauncher-<version>.apk and .aab (replacing an earlier build of the same
# version), and prints the versionCode Play needs raised each upload and the SHA-256s for the
# tester notes. Stops rather than continuing if any step fails or the key is wrong.
#
# Usage: ./build-release.sh   (no flags: it does the same thing every time)
#
# The versionCode: it suggests one more than the last build that passed the check (recorded in
# outputs/.last-versioncode, which git ignores) or, the first time, one more than the fallback in
# app/build.gradle.kts. Return takes the suggestion; type a number to use another. Without a
# terminal it takes the suggestion. Nothing is committed, so main keeps the fallback.
#
# Needs a JDK 17 to 24 and the Android SDK (found by tools/build-env.sh, as for
# ./build-and-install.sh), plus keystore.properties (docs/building.md).
# Everything is in main() so bash has read it all before the checkout and pull can change this file.

# The release certificate both apps trust (launcher_certs.xml). Not a secret.
EXPECTED_DIGEST="7e6d39d03c5d4c4f4c947c4d7f9657e9ae6ab94d296c97d684333450c2b7791d"

main() {
  local root build_tools digest aab_digest keytool version code name apk aab last fallback suggested
  if [ $# -gt 0 ]; then
    echo "./build-release.sh takes no flags; it always builds main and checks the key."
    echo "For a release build on a device: ./build-and-install.sh --release"
    exit 1
  fi
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  cd "$root"

  # ── main, up to date ─────────────────────────────────────────────────────
  if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Uncommitted changes to tracked files; commit or stash them first:"
    git status --short --untracked-files=no
    exit 1
  fi
  echo "==> git checkout main"
  git checkout -q main
  echo "==> git pull"
  git pull --ff-only
  echo "==> Building $(git log -1 --format='%h %s')"

  # ── Build ────────────────────────────────────────────────────────────────
  ROOT="$root"
  # shellcheck source=tools/build-env.sh
  source "$root/tools/build-env.sh"
  # ── versionCode ──────────────────────────────────────────────────────────
  # Asked before the build, so the rest runs unattended. Play refuses a code it has seen.
  last=""
  [ -f outputs/.last-versioncode ] && last="$(tr -dc '0-9' < outputs/.last-versioncode)"
  fallback="$(sed -n 's/.*versionCode = .*?: *\([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
  if [ -n "$last" ]; then suggested=$((last + 1)); else suggested=$(( ${fallback:-0} + 1 )); fi
  code=""
  if [ -t 0 ]; then
    read -r -p "==> versionCode for this build [$suggested]: " code || code=""
  fi
  code="${code:-$suggested}"
  if ! [[ "$code" =~ ^[1-9][0-9]*$ ]]; then
    echo "The versionCode must be a positive whole number, got: $code"
    exit 1
  fi
  echo "==> versionCode $code${last:+ (last build: $last)}"

  # Checked where Gradle writes them; nothing lands in outputs/ until the key matches.
  apk="$root/app/build/outputs/apk/release/app-release.apk"
  aab="$root/app/build/outputs/bundle/release/app-release.aab"
  rm -f "$apk" "$aab"
  echo "==> ./gradlew -PversionCode=$code :app:assembleRelease :app:bundleRelease"
  ./gradlew -PversionCode="$code" :app:assembleRelease :app:bundleRelease
  [ -f "$apk" ] || { echo "Expected APK not found at $apk"; exit 1; }
  [ -f "$aab" ] || { echo "Expected AAB not found at $aab"; exit 1; }

  # ── Signature ────────────────────────────────────────────────────────────
  build_tools="$(ls -d "$SDK_DIR"/build-tools/* 2>/dev/null | tail -1)"
  if [ -z "$build_tools" ]; then
    echo "No build-tools under $SDK_DIR; cannot check the signature."
    exit 1
  fi

  # An APK that does not verify makes apksigner fail; that lands on the WRONG KEY message below.
  digest="$("$build_tools/apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')" || true
  # apksigner reads APKs only; an AAB is a signed jar, which keytool reads.
  keytool="$JAVA_HOME/bin/keytool"
  aab_digest="$("$keytool" -printcert -jarfile "$aab" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')" || true
  echo
  echo "APK signed with: ${digest:-(no signature found)}"
  echo "AAB signed with: ${aab_digest:-(no signature found)}"
  echo "Expected:        $EXPECTED_DIGEST"
  if [ "$digest" != "$EXPECTED_DIGEST" ] || [ "$aab_digest" != "$EXPECTED_DIGEST" ]; then
    echo
    echo "WRONG KEY. Do not publish either one: the apps trust only the release key."
    exit 1
  fi
  echo "The signatures match."
  echo

  # ── Copy ─────────────────────────────────────────────────────────────────
  version="$("$build_tools/aapt2" dump badging "$apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
  [ -n "$version" ] || { echo "Could not read the version from the APK."; exit 1; }
  if [ "$("$build_tools/aapt2" dump badging "$apk" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")" != "$code" ]; then
    echo "The APK does not carry versionCode $code; app/build.gradle.kts must read -PversionCode."
    exit 1
  fi
  name="glancelauncher-$version"
  mkdir -p "$root/outputs"
  echo "$code" > "$root/outputs/.last-versioncode"
  cp -f "$apk" "$root/outputs/$name.apk"
  cp -f "$aab" "$root/outputs/$name.aab"
  apk="$root/outputs/$name.apk"
  aab="$root/outputs/$name.aab"

  echo
  echo "Version:     $version (versionCode $code)"
  echo "APK:         ${apk#"$root"/}"
  echo "APK SHA-256: $(shasum -a 256 "$apk" | cut -d' ' -f1)"
  echo "AAB:         ${aab#"$root"/}"
  echo "AAB SHA-256: $(shasum -a 256 "$aab" | cut -d' ' -f1)"
}

main "$@"
