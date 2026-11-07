#!/usr/bin/env bash
# Freshness scenarios against a connected device (docs/decisions.md, decision 38).
#
# Drives the REAL path: a debug build of the launcher sends each REFRESH with
# its own signature (dev action DEV_REFRESH) and reports what the app's provider
# says (dev action DEV_DUMP, one JSON line per app on logcat). Nothing here
# pretends to be the launcher from adb shell. Needs: adb on PATH or under
# $ANDROID_HOME, a DEBUG build of the launcher installed and set as Home, and
# the app under test installed with that build's signing digest in its
# launcher_certs.xml.
#
# Usage:
#   tools/freshness-test.sh                      dayGLANCE, every scenario
#   tools/freshness-test.sh --app lastglance     the other app
#   tools/freshness-test.sh --only alive,killed  a subset (alive, dead, killed, reboot,
#                                                rare, doze, overlap)
#   tools/freshness-test.sh --no-prompt          never stop to ask for a swipe from Recents;
#                                                a scenario that needs the app closed and finds
#                                                it still running is skipped (for unattended runs)
#   tools/freshness-test.sh --device SERIAL      when several devices are attached
#   tools/freshness-test.sh --timeout 240        seconds to wait per refresh (default 180:
#                                                a headless run may wait on the OS and then
#                                                take up to its 120 s cap)
#   tools/freshness-test.sh --dump               print the app's freshness and refresh log
#                                                as the launcher reads it (for dayGLANCE also
#                                                the forecast days and whether a location is
#                                                set, from its snapshot), and exit
#   tools/freshness-test.sh --no-floor           do not wait out the app's ten-minute
#                                                floor between headless runs (the dead-
#                                                process scenarios then read `throttled`)
#
# The standby bucket and the Doze state are restored at the end, and on Ctrl-C.
# Scenarios never use `am force-stop`: a force-stopped app cannot receive
# broadcasts or run jobs until the user opens it, which would measure the wrong
# thing. `am kill` is what Android itself does under memory pressure, but a
# phone with memory to spare ignores it. So every scenario that needs the app
# closed (dead, rare, doze, overlap) tries `am kill` and, when the renderer is
# still alive, stops and asks you to swipe the app away in Recents, then checks
# it really went before measuring. `killed` alone keeps to `am kill`: it
# measures what the OS does with it, and on such a phone that is the alive path.
#
# "Dead" means no renderer, not no process: the launcher's own provider reads
# start the app's process to host the provider, so `pidof` is usually non-empty
# while the launcher is on screen. The receiver routes REFRESH on
# `rendererAlive`, which the provider reports, and so do these scenarios.
#
# Both apps answer a REFRESH at a dead process with a headless run (their docs,
# "Freshness"; docs/decisions.md, decisions 40 and 46): `scheduled`, then `ran` with
# `waitMs` (how long the OS held the job) and `headless: true`. Two headless
# runs of one app are never closer than ten minutes, so each dead-process
# scenario first waits out the floor; the full set takes about an hour.
set -euo pipefail

APP=dayglance
ONLY=""
SERIAL=""
TIMEOUT=180
FLOOR=true
PROMPT=true
DUMP_ONLY=false
while [ $# -gt 0 ]; do
  case "$1" in
    --dump) DUMP_ONLY=true; shift ;;
    --app) APP="$2"; shift 2 ;;
    --only) ONLY="$2"; shift 2 ;;
    --device) SERIAL="$2"; shift 2 ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    --no-floor) FLOOR=false; shift ;;
    --no-prompt) PROMPT=false; shift ;;
    -h|--help) sed -n '2,52p' "$0"; exit 0 ;;
    *) echo "unknown flag $1"; exit 1 ;;
  esac
done
case "$APP" in
  dayglance) PKG=com.dayglance.app; MAIN=.MainActivity ;;
  lastglance) PKG=com.lastglance.app; MAIN=.MainActivity ;;
  *) echo "--app dayglance|lastglance"; exit 1 ;;
esac
LAUNCHER=app.glance.launcher
ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then ADB="$ANDROID_HOME/platform-tools/adb"
  else echo "adb not found (PATH or \$ANDROID_HOME/platform-tools)"; exit 1; fi
fi
[ -n "$SERIAL" ] && ADB="$ADB -s $SERIAL"

sh() { $ADB shell "$@"; }
now_ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

# ── device helpers ──────────────────────────────────────────────────────────
dump() {
  # One JSON object for $APP from the launcher's DEV_DUMP, or "" on timeout. The
  # launcher logs it as `meta app=X part=i/n <chunk>` lines (logcat cuts a line
  # at about 4 KB); the parts are reassembled once all n have landed.
  $ADB logcat -c >/dev/null 2>&1 || true
  sh am start -W -n "$LAUNCHER/.LauncherActivity" -a app.glance.launcher.DEV_DUMP >/dev/null 2>&1 || true
  local deadline=$(( $(date +%s) + 15 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    local joined
    joined="$($ADB logcat -d -s GLANCEdev:I 2>/dev/null | grep "meta app=$APP part=" | python3 -c '
import re, sys
parts = {}
for line in sys.stdin:
    m = re.search(r"meta app=\S+ part=(\d+)/(\d+) (.*)$", line.rstrip("\n"))
    if m: parts[int(m.group(1))] = (int(m.group(2)), m.group(3))
if parts:
    n = max(t for t, _ in parts.values())
    if all(i in parts for i in range(1, n + 1)): print("".join(parts[i][1] for i in range(1, n + 1)))
' || true)"
    if [ -n "$joined" ]; then echo "$joined"; return 0; fi
    sleep 1
  done
  echo ""
}
field() { python3 -c 'import json,sys; d=json.loads(sys.argv[1]); v=d.get(sys.argv[2]); print("" if v is None else v)' "$1" "$2"; }
outcome_of() {
  # The final outcome recorded for a client id, or "" while it is still queued, scheduled or absent.
  python3 - "$1" "$2" <<'PY'
import json, sys
d = json.loads(sys.argv[1]); cid = sys.argv[2]
final = ""
for e in d.get("refreshLog", []):
    if e.get("clientId") == cid:
        o = e.get("outcome")
        if o in ("ran", "skipped", "failed", "unanswered", "deferred", "coalesced", "aborted", "throttled"):
            final = o
            if e.get("headless"): final += " headless"
            if e.get("reason"): final += " (" + e["reason"] + ")"
            if e.get("waitMs") is not None: final += " held %d ms" % e["waitMs"]
            if e.get("durationMs") is not None: final += " in %d ms" % e["durationMs"]
        elif o in ("queued", "scheduled") and not final: final = ""
print(final)
PY
}
preempting_cid() {
  # The run that throttled this request: bringing the launcher to the foreground sends its own
  # refresh when the data is stale, and that one starts first. Its outcome is the measurement.
  python3 - "$1" "$2" "$3" <<'PY'
import json, sys
d = json.loads(sys.argv[1]); cid = sys.argv[2]; t0 = int(sys.argv[3])
runs = [e for e in d.get("refreshLog", [])
        if e.get("clientId") != cid and e.get("outcome") != "throttled" and (e.get("requestedAt") or 0) >= t0 - 5000]
print(runs[-1]["clientId"] if runs else "")
PY
}
await_outcome() {
  # The final outcome of a client id, polled for up to TIMEOUT seconds, or "".
  local out="" deadline=$(( $(date +%s) + TIMEOUT ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    out="$(outcome_of "$(dump)" "$1")"
    [ -n "$out" ] && break
    sleep 2
  done
  echo "$out"
}
# A throttled answer behind the launcher's own refresh, followed to that refresh's outcome.
follow_throttled() {
  local out="$1" cid="$2" t0="$3"
  if [[ "$out" == throttled* ]]; then
    local other; other="$(preempting_cid "$(dump)" "$cid" "$t0")"
    if [ -n "$other" ]; then
      echo "    throttled behind the launcher's own request ${other:0:8}; following that request" >&2
      local out2; out2="$(await_outcome "$other")"
      out="launcher's own → ${out2:-no outcome within ${TIMEOUT}s}"
    fi
  fi
  echo "$out"
}
page_closed() {
  # Whether the app closed its unresponsive page after this request went unanswered (pageClosed).
  python3 - "$1" "$2" <<'PY2'
import json, sys
d = json.loads(sys.argv[1]); cid = sys.argv[2]
print("yes" if any(e.get("clientId") == cid and e.get("pageClosed") for e in d.get("refreshLog", [])) else "")
PY2
}
# An unanswered request the app recovered from: it closed its page and scheduled a headless run
# logged as `<clientId>-headless` (docs/decisions.md, decision 65; the apps' LauncherPageRetire).
# That run's outcome is the measurement.
follow_closed() {
  local out="$1" cid="$2"
  if [[ "$out" == unanswered* ]] && [ -n "$(page_closed "$(dump)" "$cid")" ]; then
    echo "    the app closed its unresponsive page; following ${cid:0:8}-headless" >&2
    local out2; out2="$(await_outcome "$cid-headless")"
    out="unanswered, page closed → ${out2:-no outcome within ${TIMEOUT}s}"
  fi
  echo "$out"
}
inflight_cid() {
  # The client id of the newest request still queued or scheduled (what a `coalesced` answer deferred to), or "".
  python3 - "$1" <<'PY'
import json, sys
d = json.loads(sys.argv[1])
cid = ""
for e in d.get("refreshLog", []):
    if e.get("outcome") in ("queued", "scheduled") and "finishedAt" not in e: cid = e.get("clientId", "")
print(cid)
PY
}
headless_floor_left() {
  # Seconds until the app's ten-minute floor since its last headless run has passed (0 when clear).
  # The worker stamps the floor when a run STARTS, which the dump carries as requestedAt + waitMs
  # (a throttled entry is not a run and does not move it).
  python3 - "$1" <<'PY'
import json, math, sys, time
d = json.loads(sys.argv[1])
starts = [(e.get("requestedAt") or 0) + (e.get("waitMs") or 0)
          for e in d.get("refreshLog", []) if e.get("headless") and e.get("outcome") != "throttled"]
last = max(starts + [0])
# Rounded up, plus a margin: the worker's own stamp lands a moment after requestedAt + waitMs,
# and a wait one second short reads `throttled` (599 s ago, floor 10 min).
left = 600 - (time.time() - last / 1000.0)
print(max(0, math.ceil(left) + 5) if last and left > 0 else 0)
PY
}
wait_for_floor() {
  # Before a dead-process scenario: wait out the floor, or the run reads `throttled`.
  $FLOOR || return 0
  local left; left="$(headless_floor_left "$(dump)")"
  if [ "${left:-0}" -gt 0 ]; then
    echo "    waiting ${left}s for the headless floor (--no-floor skips this)"
    sleep "$left"
  fi
}
send_refresh() {
  $ADB logcat -c >/dev/null 2>&1 || true
  sh am start -W -n "$LAUNCHER/.LauncherActivity" -a app.glance.launcher.DEV_REFRESH --es app "$APP" >/dev/null 2>&1 || true
  local deadline=$(( $(date +%s) + 15 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    local line
    line="$($ADB logcat -d -s GLANCEdev:I 2>/dev/null | grep "refresh app=$APP " | tail -1 || true)"
    if [ -n "$line" ]; then echo "${line##*clientId=}"; return 0; fi
    sleep 1
  done
  echo ""
}
# Whether the app reports a live renderer (a MainActivity with a WebView), from its provider.
renderer_alive() { [ "$(field "$(dump)" rendererAlive)" = "True" ]; }
open_app() { sh am start -W -n "$PKG/$MAIN" >/dev/null 2>&1 || true; sleep 6; }
go_home() { sh input keyevent KEYCODE_HOME >/dev/null 2>&1 || true; sleep 2; }
kill_app() { sh am kill "$PKG" >/dev/null 2>&1 || true; sleep 2; }
# Ends the app's renderer for a scenario that needs it closed: `am kill` first and, when the OS
# kept it, a swipe from Recents by hand (up to three tries). Fails when it is still alive.
close_app() {
  kill_app
  renderer_alive || return 0
  $PROMPT || return 1
  local try
  for try in 1 2 3; do
    echo "    $PKG is still running (am kill was ignored)."
    printf '    Swipe it away in Recents, go back to Home, then press Enter here... '
    read -r _ </dev/tty || return 1
    local deadline=$(( $(date +%s) + 20 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
      renderer_alive || { echo "    closed."; return 0; }
      sleep 2
    done
  done
  return 1
}
skip_closed() { record "$1" "skipped" "-" "the app was still running after am kill$($PROMPT && echo ' and the swipe prompt' || echo ' (--no-prompt)')"; }

RESULTS=()
record() { RESULTS+=("$1|$2|$3|$4"); }

# One refresh, measured: whether the snapshot's checkedAt advanced, how long the
# outcome took to land, and what the app recorded.
measure() {
  local scenario="$1"
  local before after checked_before checked_after cid out
  before="$(dump)"; checked_before="$(field "$before" checkedAt)"
  local t0; t0="$(now_ms)"
  cid="$(send_refresh)"
  if [ -z "$cid" ] || [ "$cid" = "none(demo)" ]; then record "$scenario" "no request" "-" "launcher on demo data for $APP"; return; fi
  out=""
  local deadline=$(( $(date +%s) + TIMEOUT ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    after="$(dump)"
    out="$(outcome_of "$after" "$cid")"
    [ -n "$out" ] && break
    sleep 2
  done
  # `coalesced`: another request was already in flight (often the launcher's own,
  # sent when the dump brought it to the foreground). Its outcome is the measurement.
  if [[ "$out" == coalesced* ]]; then
    local other; other="$(inflight_cid "${after:-$before}")"
    if [ -n "$other" ] && [ "$other" != "$cid" ]; then
      echo "    coalesced into ${other:0:8}; following that request"
      local out2=""; deadline=$(( $(date +%s) + TIMEOUT ))
      while [ "$(date +%s)" -lt "$deadline" ]; do
        after="$(dump)"
        out2="$(outcome_of "$after" "$other")"
        [ -n "$out2" ] && break
        sleep 2
      done
      out="coalesced → ${out2:-no outcome within ${TIMEOUT}s}"
    fi
  fi
  out="$(follow_throttled "$out" "$cid" "$t0")"
  out="$(follow_closed "$out" "$cid")"
  after="$(dump)"
  local elapsed=$(( $(now_ms) - t0 ))
  checked_after="$(field "${after:-$before}" checkedAt)"
  local updated="no"
  [ -n "$checked_after" ] && [ "$checked_after" != "$checked_before" ] && updated="yes"
  record "$scenario" "${out:-no outcome within ${TIMEOUT}s}" "${elapsed} ms" "snapshot updated: $updated · renderer at read: $(field "${after:-$before}" rendererAlive)"
}

# ── scenarios ───────────────────────────────────────────────────────────────
restore() {
  echo "==> restoring: standby bucket active, Doze unforced"
  sh am set-standby-bucket "$PKG" active >/dev/null 2>&1 || true
  sh dumpsys deviceidle unforce >/dev/null 2>&1 || true
  sh dumpsys battery reset >/dev/null 2>&1 || true
}
trap restore EXIT

want() { [ -z "$ONLY" ] || [[ ",$ONLY," == *",$1,"* ]]; }

if $DUMP_ONLY; then
  dump | python3 -c 'import json,sys; d=json.loads(sys.stdin.read() or "{}"); log=d.pop("refreshLog", []); print(json.dumps(d, indent=1)); print("refreshLog (oldest first):"); [print(" ", json.dumps(e)) for e in log]'
  exit 0
fi

echo "==> $APP via $LAUNCHER (timeout ${TIMEOUT}s per refresh)"
sh getprop ro.build.version.release | sed 's/^/==> Android /'

if want alive; then
  echo "==> alive: app opened, then Home"
  open_app; go_home
  measure "alive"
fi

if want dead; then
  echo "==> dead: the app closed, then a refresh"
  open_app; go_home
  if close_app; then
    wait_for_floor
    measure "dead (app closed)"
  else
    skip_closed "dead (app closed)"
  fi
fi

if want killed; then
  echo "==> killed: am kill (never force-stop)"
  open_app; go_home; kill_app
  renderer_alive && echo "    (renderer still alive after am kill; the OS kept the process)"
  wait_for_floor
  measure "killed"
fi

if want reboot; then
  echo "==> reboot: waiting for the device"
  $ADB reboot >/dev/null 2>&1 || true
  $ADB wait-for-device
  until [ "$(sh getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
  sleep 20
  wait_for_floor
  measure "after reboot"
fi

if want rare; then
  echo "==> rare standby bucket"
  open_app; go_home
  if close_app; then
    wait_for_floor
    sh am set-standby-bucket "$PKG" rare >/dev/null 2>&1 || true
    measure "rare bucket (app closed)"
    sh am set-standby-bucket "$PKG" active >/dev/null 2>&1 || true
  else
    skip_closed "rare bucket (app closed)"
  fi
fi

if want doze; then
  echo "==> forced Doze"
  open_app; go_home
  if close_app; then
    wait_for_floor
    sh dumpsys battery unplug >/dev/null 2>&1 || true
    sh dumpsys deviceidle force-idle >/dev/null 2>&1 || true
    measure "forced Doze (app closed)"
    sh dumpsys deviceidle unforce >/dev/null 2>&1 || true
    sh dumpsys battery reset >/dev/null 2>&1 || true
  else
    skip_closed "forced Doze (app closed)"
  fi
fi

if want overlap; then
  echo "==> overlap: the user opens the app while a headless refresh is in flight"
  open_app; go_home
  if ! close_app; then
    skip_closed "overlap (opened during refresh)"
  else
    wait_for_floor
    t0="$(now_ms)"
    cid="$(send_refresh)"
    # Long enough for the worker to boot its WebView, short of the cycle finishing.
    sleep 3
    open_app
    if [ -n "$cid" ] && [ "$cid" != "none(demo)" ]; then
      out=""; deadline=$(( $(date +%s) + TIMEOUT ))
      while [ "$(date +%s)" -lt "$deadline" ]; do after="$(dump)"; out="$(outcome_of "$after" "$cid")"; [ -n "$out" ] && break; sleep 2; done
      out="$(follow_throttled "$out" "$cid" "$t0")"
      out="$(follow_closed "$out" "$cid")"
      record "overlap (opened during refresh)" "${out:-no outcome within ${TIMEOUT}s}" "-" "expected: aborted (the reverse lock), or ran if the run finished first"
    else
      record "overlap (opened during refresh)" "no request" "-" "launcher on demo data for $APP"
    fi
  fi
fi

echo
echo "scenario                         | outcome                              | time     | notes"
echo "---------------------------------|--------------------------------------|----------|------"
for r in "${RESULTS[@]}"; do
  IFS='|' read -r a b c d <<<"$r"
  printf '%-32s | %-36s | %-8s | %s\n' "$a" "$b" "$c" "$d"
done
