#!/usr/bin/env bash
# Runs every action Picket offers against the harmless :testtarget app, then its inverse, and
# records the state before, after and after undo. The result decides what docs/COMMANDS.md may
# call "verified".
#
# Usage:
#   ./gradlew :testtarget:assembleDebug
#   scripts/device-checks.sh [--keep-target]
#
# Needs adb only (no Shizuku). The commands run with the same shell uid that Shizuku lends Picket.
# Everything touched is either the test target or restored afterwards (the default browser role is
# put back to its previous holder). --keep-target leaves the test target installed.
#
# Output: device-results/<device>-<api>-<time>/summary.md (paste into a GitHub issue) and log.txt
# (every command with stdout, stderr and exit status).

# shellcheck source=lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

keep_target=false
[[ "${1:-}" == "--keep-target" ]] && keep_target=true

PKG=app.brokoli5191.picket.testtarget
NLS="$PKG/.DummyNotificationListener"
A11Y="$PKG/.DummyAccessibilityService"
A11Y_FULL="$PKG/$PKG.DummyAccessibilityService"
ADMIN="$PKG/.DummyAdminReceiver"
FGS="$PKG/.DummyForegroundService"
MAIN="$PKG/.MainActivity"
PERM=android.permission.READ_CALENDAR
ROLE=android.app.role.BROWSER
USER_ID=0
APK="$ROOT/testtarget/build/outputs/apk/debug/testtarget-debug.apk"

require_device
[[ -f "$APK" ]] || die "test target not built: run ./gradlew :testtarget:assembleDebug first"

slug="$(device_slug)"
api="$(device_api)"
dir="$ROOT/device-results/$slug-$api-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$dir"
LOG="$dir/log.txt"
SUMMARY="$dir/summary.md"
: > "$LOG"

OUT=""
ERR=""
RC=0
# x <argv...>: run on the device, log everything, leave stdout/stderr/exit in OUT/ERR/RC.
x() {
  local o e
  o="$(mktemp)"
  e="$(mktemp)"
  dev_run "$o" "$e" "$@"
  RC=$?
  OUT="$(tr -d '\r' < "$o")"
  ERR="$(tr -d '\r' < "$e")"
  rm -f "$o" "$e"
  {
    printf '$ %s\n' "$*"
    [[ -n "$OUT" ]] && printf '%s\n' "$OUT"
    [[ -n "$ERR" ]] && printf '[stderr] %s\n' "$ERR"
    printf '[exit %d]\n\n' "$RC"
  } >> "$LOG"
  return "$RC"
}

section() {
  printf '\n### %s\n\n' "$1" >> "$LOG"
  info "$1"
}

# One-line, table-safe rendering of command output.
oneline() { printf '%s' "$1" | tr '\n' ' ' | sed -E 's/\|/\\|/g; s/ +/ /g; s/^ //; s/ $//' | cut -c1-120; }

declare -a ROWS=()
row() { ROWS+=("| $1 | \`$2\` | $3 | $4 | $5 | $6 | **$7** | $8 |"); }

contains() { [[ "$1" == *"$2"* ]]; }

# ---- setup -------------------------------------------------------------------------------------------
section "Install test target"
"$ADB" install -r -t "$APK" >> "$LOG" 2>&1 || die "adb install failed (see $LOG)"

# Original values of everything shared with the rest of the system, restored by cleanup().
x settings get secure enabled_accessibility_services
ORIG_A11Y="$OUT"
x cmd role get-role-holders --user "$USER_ID" "$ROLE"
ORIG_BROWSER="$(printf '%s' "$OUT" | head -1)"

cleanup() {
  section "Cleanup"
  x cmd notification disallow_listener "$NLS" "$USER_ID"
  x cmd notification disallow_dnd "$PKG" "$USER_ID"
  if [[ -z "$ORIG_A11Y" || "$ORIG_A11Y" == "null" ]]; then
    x settings --user "$USER_ID" delete secure enabled_accessibility_services
  else
    x settings --user "$USER_ID" put secure enabled_accessibility_services "$ORIG_A11Y"
  fi
  if [[ -n "$ORIG_BROWSER" && "$ORIG_BROWSER" != "$PKG" ]]; then
    x cmd role add-role-holder --user "$USER_ID" "$ROLE" "$ORIG_BROWSER"
  fi
  x cmd role remove-role-holder --user "$USER_ID" "$ROLE" "$PKG"
  x cmd deviceidle whitelist "-$PKG"
  x dpm remove-active-admin --user "$USER_ID" "$ADMIN"
  x pm enable --user "$USER_ID" "$PKG"
  if [[ "$keep_target" == false ]]; then
    "$ADB" uninstall "$PKG" >> "$LOG" 2>&1
  fi
}
trap cleanup EXIT

start_target() { # start the app (and optionally its foreground service), then go home
  x am start -W -n "$MAIN" ${1:+--ez start_fgs true}
  sleep 2
  x input keyevent KEYCODE_HOME
  sleep "${2:-2}"
}
pid_of_target() { x pidof "$PKG"; printf '%s' "$OUT"; }

# ---- 1. force stop ----------------------------------------------------------------------------------
section "Force stop"
start_target fgs
before="$(pid_of_target)"
x cmd activity force-stop --user "$USER_ID" "$PKG"
rc=$RC
after="$(pid_of_target)"
verdict=FAIL
[[ $rc -eq 0 && -n "$before" && -z "$after" ]] && verdict=PASS
row "Force stop" "cmd activity force-stop --user $USER_ID $PKG" "$rc" "pid '$before' → '$after'" "–" "–" "$verdict" ""

# ---- 2. kill background processes -------------------------------------------------------------------
section "Kill background processes"
start_target "" 5
before="$(pid_of_target)"
x cmd activity kill --user "$USER_ID" "$PKG"
rc=$RC
sleep 1
after="$(pid_of_target)"
verdict=FAIL
[[ $rc -eq 0 && -n "$before" && -z "$after" ]] && verdict=PASS
[[ $rc -eq 0 && -n "$after" ]] && verdict=NO-EFFECT
row "Kill background processes" "cmd activity kill --user $USER_ID $PKG" "$rc" "pid '$before' → '$after'" "–" "–" "$verdict" "only cached/background processes are killable"
x am force-stop "$PKG"

# ---- 3. stop a specific service ---------------------------------------------------------------------
section "Stop service"
start_target fgs
x dumpsys activity services "$PKG"
before="$(contains "$OUT" "DummyForegroundService" && echo running || echo absent)"
x cmd activity stop-service --user "$USER_ID" -n "$FGS"
rc=$RC # 255 for every outcome; the message decides
stop_out="$(oneline "$OUT $ERR")"
sleep 1
x dumpsys activity services "$PKG"
after="$(contains "$OUT" "DummyForegroundService" && echo running || echo absent)"
verdict=FAIL
[[ "$before" == running && "$after" == absent ]] && verdict=PASS
[[ "$stop_out" == *"Service stopped"* ]] || verdict=FAIL
row "Stop service" "cmd activity stop-service --user $USER_ID -n $FGS" "$rc" "$before → $after ($stop_out)" "–" "–" "$verdict" "exit is 255 even on success; only exported services; fallback: force stop"
x am force-stop "$PKG"

# ---- 4. restrict background execution ---------------------------------------------------------------
section "RUN_ANY_IN_BACKGROUND"
x cmd appops get --user "$USER_ID" "$PKG" RUN_ANY_IN_BACKGROUND
before="$(oneline "$OUT")"
x cmd appops set --user "$USER_ID" "$PKG" RUN_ANY_IN_BACKGROUND ignore
rc=$RC
x cmd appops get --user "$USER_ID" "$PKG" RUN_ANY_IN_BACKGROUND
after="$(oneline "$OUT")"
x cmd appops set --user "$USER_ID" "$PKG" RUN_ANY_IN_BACKGROUND allow
inv_rc=$RC
x cmd appops get --user "$USER_ID" "$PKG" RUN_ANY_IN_BACKGROUND
restored="$(oneline "$OUT")"
verdict=FAIL
[[ $rc -eq 0 && "$after" == *ignore* && $inv_rc -eq 0 && "$restored" == *allow* ]] && verdict=PASS
row "Restrict background" "cmd appops set --user $USER_ID $PKG RUN_ANY_IN_BACKGROUND ignore" "$rc" "'$before' → '$after'" \
  "\`… allow\` (exit $inv_rc)" "'$restored'" "$verdict" ""

# ---- 5. standby bucket -----------------------------------------------------------------------------------
section "Standby bucket"
x am get-standby-bucket --user "$USER_ID" "$PKG"
before="$(oneline "$OUT")"
bucket=restricted
[[ "$api" -lt 30 ]] && bucket=rare
x cmd activity set-standby-bucket --user "$USER_ID" "$PKG" "$bucket"
rc=$RC
x am get-standby-bucket --user "$USER_ID" "$PKG"
after="$(oneline "$OUT")"
restore_to="$before"
[[ "$restore_to" =~ ^[0-9]+$ ]] || restore_to=active
x cmd activity set-standby-bucket --user "$USER_ID" "$PKG" "$restore_to"
inv_rc=$RC
x am get-standby-bucket --user "$USER_ID" "$PKG"
restored="$(oneline "$OUT")"
verdict=FAIL
[[ $rc -eq 0 && "$after" != "$before" && $inv_rc -eq 0 && "$restored" == "$before" ]] && verdict=PASS
row "Standby bucket → $bucket" "cmd activity set-standby-bucket --user $USER_ID $PKG $bucket" "$rc" "'$before' → '$after'" \
  "\`… $restore_to\` (exit $inv_rc)" "'$restored'" "$verdict" "previous bucket from journal"

# ---- 6. notification listener ------------------------------------------------------------------------
section "Notification listener"
x cmd notification allow_listener "$NLS" "$USER_ID"
x settings get secure enabled_notification_listeners
before="$(contains "$OUT" "$PKG" && echo allowed || echo absent)"
x cmd notification disallow_listener "$NLS" "$USER_ID"
rc=$RC
x settings get secure enabled_notification_listeners
after="$(contains "$OUT" "$PKG" && echo allowed || echo absent)"
x cmd notification allow_listener "$NLS" "$USER_ID"
inv_rc=$RC
x settings get secure enabled_notification_listeners
restored="$(contains "$OUT" "$PKG" && echo allowed || echo absent)"
x dumpsys notification
in_dump="$(contains "$OUT" "$PKG" && echo "listed in dumpsys" || echo "not in dumpsys")"
verdict=FAIL
[[ $rc -eq 0 && "$before" == allowed && "$after" == absent && "$restored" == allowed ]] && verdict=PASS
row "Revoke notification listener" "cmd notification disallow_listener $NLS $USER_ID" "$rc" "$before → $after" \
  "\`allow_listener\` (exit $inv_rc)" "$restored, $in_dump" "$verdict" "state read from settings + dumpsys"

# ---- 7. accessibility service ------------------------------------------------------------------------
section "Accessibility service"
with_target="$A11Y"
[[ -n "$ORIG_A11Y" && "$ORIG_A11Y" != "null" ]] && with_target="$ORIG_A11Y:$A11Y"
x settings --user "$USER_ID" put secure enabled_accessibility_services "$with_target"
sleep 2
x dumpsys accessibility
before="$(contains "$OUT" "DummyAccessibilityService" && echo bound || echo unbound)"
# The action: remove only our entry, keep every other one exactly as it was.
x settings get secure enabled_accessibility_services
without="$(printf '%s' "$OUT" | tr ':' '\n' | grep -v -x -e "$A11Y" -e "$A11Y_FULL" | paste -sd ':' -)"
# Same argv as Picket: `cmd settings`, which /system/bin/settings only wraps.
if [[ -z "$without" ]]; then
  x cmd settings --user "$USER_ID" delete secure enabled_accessibility_services
else
  x cmd settings --user "$USER_ID" put secure enabled_accessibility_services "$without"
fi
rc=$RC
sleep 2
x dumpsys accessibility
after="$(contains "$OUT" "DummyAccessibilityService" && echo bound || echo unbound)"
x settings get secure enabled_accessibility_services
others_kept="$([[ "$OUT" == "${ORIG_A11Y}" || (-z "$without" && "$OUT" == "null") ]] && echo "others kept" || echo "CHANGED: $(oneline "$OUT")")"
x cmd settings --user "$USER_ID" put secure enabled_accessibility_services "$with_target"
inv_rc=$RC
sleep 2
x dumpsys accessibility
restored="$(contains "$OUT" "DummyAccessibilityService" && echo bound || echo unbound)"
verdict=FAIL
[[ $rc -eq 0 && "$before" == bound && "$after" == unbound && "$restored" == bound && "$others_kept" == "others kept" ]] && verdict=PASS
row "Disable accessibility service" "cmd settings --user $USER_ID put secure enabled_accessibility_services <others>" "$rc" \
  "$before → $after, $others_kept" "\`put … <previous value>\` (exit $inv_rc)" "$restored" "$verdict" ""

# ---- 8. runtime permission ---------------------------------------------------------------------------
section "Runtime permission"
x pm grant --user "$USER_ID" "$PKG" "$PERM"
perm_state() { x dumpsys package "$PKG"; printf '%s' "$OUT" | grep -m1 "$PERM: granted=" | sed -E 's/.*granted=([a-z]+).*/\1/'; }
before="$(perm_state)"
x cmd package revoke --user "$USER_ID" "$PKG" "$PERM"
rc=$RC
after="$(perm_state)"
x pm grant --user "$USER_ID" "$PKG" "$PERM"
inv_rc=$RC
restored="$(perm_state)"
x pm revoke --user "$USER_ID" "$PKG" "$PERM"
verdict=FAIL
[[ $rc -eq 0 && "$before" == true && "$after" == false && "$restored" == true ]] && verdict=PASS
row "Revoke runtime permission" "cmd package revoke --user $USER_ID $PKG $PERM" "$rc" "granted=$before → $after" \
  "\`pm grant …\` (exit $inv_rc)" "granted=$restored" "$verdict" ""

# ---- 9. app-ops (overlay, usage access) --------------------------------------------------------------
for op in SYSTEM_ALERT_WINDOW GET_USAGE_STATS; do
  section "App-op $op"
  x cmd appops get --user "$USER_ID" "$PKG" "$op"
  original="$(oneline "$OUT")"
  x cmd appops set --user "$USER_ID" "$PKG" "$op" allow
  x cmd appops get --user "$USER_ID" "$PKG" "$op"
  before="$(oneline "$OUT")"
  x cmd appops set --user "$USER_ID" "$PKG" "$op" ignore
  rc=$RC
  x cmd appops get --user "$USER_ID" "$PKG" "$op"
  after="$(oneline "$OUT")"
  x cmd appops set --user "$USER_ID" "$PKG" "$op" allow
  inv_rc=$RC
  x cmd appops get --user "$USER_ID" "$PKG" "$op"
  restored="$(oneline "$OUT")"
  x cmd appops set --user "$USER_ID" "$PKG" "$op" default
  verdict=FAIL
  [[ $rc -eq 0 && "$after" == *ignore* && "$restored" == *allow* ]] && verdict=PASS
  row "Revoke app-op $op" "cmd appops set --user $USER_ID $PKG $op ignore" "$rc" "'$before' → '$after'" \
    "\`… allow\` (exit $inv_rc)" "'$restored'" "$verdict" "originally '$original'"
done

# ---- 10. battery optimisation exemption --------------------------------------------------------------
section "Battery optimisation"
x cmd deviceidle whitelist "+$PKG"
listed() { x cmd deviceidle whitelist; contains "$OUT" "$PKG" && echo exempt || echo "not exempt"; }
before="$(listed)"
x cmd deviceidle whitelist "-$PKG"
rc=$RC
remove_out="$(oneline "$OUT")"
after="$(listed)"
x cmd deviceidle whitelist "+$PKG"
inv_rc=$RC
restored="$(listed)"
x cmd deviceidle whitelist "-$PKG"
verdict=FAIL
[[ $rc -eq 0 && "$before" == exempt && "$after" == "not exempt" && "$restored" == exempt ]] && verdict=PASS
row "Remove battery-optimisation exemption" "cmd deviceidle whitelist -$PKG" "$rc" "$before → $after ($remove_out)" \
  "\`… +$PKG\` (exit $inv_rc)" "$restored" "$verdict" ""

# ---- 11. default role holder -------------------------------------------------------------------------
section "Role holder ($ROLE)"
holders() { x cmd role get-role-holders --user "$USER_ID" "$ROLE"; oneline "$OUT"; }
x cmd role add-role-holder --user "$USER_ID" "$ROLE" "$PKG"
before="$(holders)"
x cmd role remove-role-holder --user "$USER_ID" "$ROLE" "$PKG"
rc=$RC
after="$(holders)"
x cmd role add-role-holder --user "$USER_ID" "$ROLE" "$PKG"
inv_rc=$RC
restored="$(holders)"
verdict=FAIL
[[ $rc -eq 0 && "$before" == *"$PKG"* && "$after" != *"$PKG"* && "$restored" == *"$PKG"* ]] && verdict=PASS
row "Remove default role holder" "cmd role remove-role-holder --user $USER_ID $ROLE $PKG" "$rc" "'$before' → '$after'" \
  "\`add-role-holder\` (exit $inv_rc)" "'$restored'" "$verdict" "original holder '$ORIG_BROWSER' restored in cleanup"

# ---- 12. notification policy (DND) access ------------------------------------------------------------
section "DND access"
dnd_state() {
  x dumpsys notification --zen
  # Filter first, then test the captured lines: with pipefail, `… | grep -q` can report a match as
  # failure when grep -q exits early and the writer gets SIGPIPE.
  local primary
  primary="$(printf '%s' "$OUT" | grep -E "^\s+.* \(user: $USER_ID isPrimary: true")"
  grep -qF "$PKG" <<< "$primary" && echo granted || echo absent
}
x cmd notification allow_dnd "$PKG" "$USER_ID"
before="$(dnd_state)"
x cmd notification disallow_dnd "$PKG" "$USER_ID"
rc=$RC
after="$(dnd_state)"
x cmd notification allow_dnd "$PKG" "$USER_ID"
inv_rc=$RC
restored="$(dnd_state)"
verdict=FAIL
[[ $rc -eq 0 && "$before" == granted && "$after" == absent && "$restored" == granted ]] && verdict=PASS
row "Revoke DND access" "cmd notification disallow_dnd $PKG $USER_ID" "$rc" "$before → $after" \
  "\`allow_dnd\` (exit $inv_rc)" "$restored" "$verdict" "state read from dumpsys notification --zen"

# ---- 13. disable app for user ------------------------------------------------------------------------
section "Disable for user"
disabled() { x pm list packages -d --user "$USER_ID" "$PKG"; contains "$OUT" "package:$PKG" && echo disabled || echo enabled; }
before="$(disabled)"
x cmd package disable-user --user "$USER_ID" "$PKG"
rc=$RC
disable_out="$(oneline "$OUT")"
after="$(disabled)"
x pm enable --user "$USER_ID" "$PKG"
inv_rc=$RC
restored="$(disabled)"
verdict=FAIL
[[ $rc -eq 0 && "$before" == enabled && "$after" == disabled && "$restored" == enabled ]] && verdict=PASS
row "Disable app for user" "cmd package disable-user --user $USER_ID $PKG" "$rc" "$before → $after ($disable_out)" \
  "\`pm enable --user $USER_ID\` (exit $inv_rc)" "$restored" "$verdict" ""
x pm disable-user --user "$USER_ID" "$PKG"
x cmd package default-state --user "$USER_ID" "$PKG"
inv_rc=$RC
default_out="$(oneline "$OUT $ERR")"
restored="$(disabled)"
verdict=FAIL
[[ $inv_rc -eq 0 && "$restored" == enabled ]] && verdict=PASS
row "Disable app for user (exact inverse)" "pm disable-user --user $USER_ID $PKG" "–" "disabled" \
  "\`pm default-state --user $USER_ID\` (exit $inv_rc, $default_out)" "$restored" "$verdict" "restores the default state instead of an explicit enable"

# ---- 14. device admin --------------------------------------------------------------------------------
section "Device admin"
admin_state() { x dumpsys device_policy; contains "$OUT" "$PKG" && echo active || echo inactive; }
x dpm set-active-admin --user "$USER_ID" "$ADMIN"
set_rc=$RC
before="$(admin_state)"
x dpm remove-active-admin --user "$USER_ID" "$ADMIN"
rc=$RC
remove_out="$(oneline "$OUT $ERR")"
after="$(admin_state)"
verdict=FAIL
[[ $set_rc -eq 0 && $rc -eq 0 && "$before" == active && "$after" == inactive ]] && verdict=PASS
row "Remove device admin (test-only app)" "dpm remove-active-admin --user $USER_ID $ADMIN" "$rc" "$before → $after ($remove_out)" \
  "–" "–" "$verdict" "non-test admins: Picket opens Settings instead"

# ---- summary ------------------------------------------------------------------------------------------
{
  echo "## Picket device checks"
  echo
  echo '```'
  device_summary
  echo "script: $(git -C "$ROOT" rev-parse --short HEAD 2> /dev/null || echo unknown)"
  echo '```'
  echo
  echo "| Action | Command | Exit | Effect | Inverse | After undo | Verdict | Notes |"
  echo "|---|---|---|---|---|---|---|---|"
  printf '%s\n' "${ROWS[@]}"
  echo
  echo "Full log with stdout/stderr of every command: \`${LOG#"$ROOT"/}\` (attach it if something failed)."
} > "$SUMMARY"

info "Summary: ${SUMMARY#"$ROOT"/}"
cat "$SUMMARY"
