#!/usr/bin/env bash
# Records the shell output Picket's parsers read, from a connected device, into
#   core/src/test/resources/fixtures/<manufacturer-model>/<api>/
# so parsers can be tested against what a real device prints.
#
# Usage: scripts/capture-fixtures.sh [--keep-package-names]
#
# Runs over adb only (Shizuku is not needed). Takes a minute or two. Nothing is changed on the device.
#
# PRIVACY: the fixtures are meant to be committed to a public repository. Before writing them, the
# script removes e-mail addresses, MAC/IP addresses, Wi-Fi names, intent data URIs and phone numbers,
# and replaces the names of apps *you* installed (pm list packages -3) with com.example.userN.
# The unredacted originals stay in device-results/raw/ (git-ignored) for comparison.
# Still read `git diff` before committing; if anything looks personal, delete it or tell the maintainer.

# shellcheck source=lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

keep_names=false
[[ "${1:-}" == "--keep-package-names" ]] && keep_names=true

if ! sed --version > /dev/null 2>&1; then
  command -v gsed > /dev/null 2>&1 || die "GNU sed is required (macOS: brew install gnu-sed)"
  sed() { gsed "$@"; }
fi

require_device
slug="$(device_slug)"
api="$(device_api)"
stamp="$(date +%Y%m%d-%H%M%S)"
out="$ROOT/core/src/test/resources/fixtures/$slug/$api"
raw="$ROOT/device-results/raw/$slug-$api-$stamp"
mkdir -p "$out" "$raw"
: > "$out/INDEX.tsv"
info "Device: $slug, API $api"
info "Fixtures: ${out#"$ROOT"/}"

device_summary > "$out/DEVICE.txt"
echo "captured: $stamp" >> "$out/DEVICE.txt"

# --- package-name anonymisation ---------------------------------------------------------------
anon_script="$raw/anonymise.sed"
: > "$anon_script"
if [[ "$keep_names" == false ]]; then
  "$ADB" shell -T pm list packages -3 2> /dev/null | tr -d '\r' | sed -n 's/^package://p' |
    grep -v '^app\.brokoli5191\.picket' |
    awk '{ print length($0) "\t" $0 }' | sort -rn | cut -f2 |
    awk '{ gsub(/\./, "\\."); n++
           rule = sprintf("s/(^|[^A-Za-z0-9_.])%s($|[^A-Za-z0-9_])/\\1com.example.user%d\\2/g", $0, n)
           print rule; print rule }' \
      > "$anon_script"
  info "Anonymising $(wc -l < "$anon_script") user-installed package names"
fi

anonymise() { if [[ -s "$anon_script" ]]; then sed -E -f "$anon_script"; else cat; fi; }
filter_default() { redact | anonymise; }
filter_network() { redact_network | anonymise; }

# capture <filter> <argv...>
# The file name is the argv joined with "_" ("/" becomes "%"), exactly what the tests' FixtureExecutor
# looks up, so a recorded directory can drive the collectors unchanged.
capture() {
  local filter="$1"
  shift
  local name started rc
  name="$(printf '%s_' "$@")"
  name="${name%_}"
  name="${name//\//%}"
  started=$(date +%s)
  dev_run "$raw/$name.out" "$raw/$name.err" "$@"
  rc=$?
  "$filter" < "$raw/$name.out" > "$out/$name.txt"
  if [[ -s "$raw/$name.err" ]]; then
    "$filter" < "$raw/$name.err" > "$out/$name.stderr.txt"
  fi
  printf '%s\t%s\t%s\t%s\t%ss\n' "$name" "$*" "$rc" "$(wc -c < "$out/$name.txt" | tr -d ' ')" \
    "$(($(date +%s) - started))" >> "$out/INDEX.tsv"
  printf '  %-62s exit %-3s %s bytes\n' "$name" "$rc" "$(wc -c < "$out/$name.txt" | tr -d ' ')" >&2
}

first_pid() { "$ADB" shell -T pidof "$1" 2> /dev/null | tr -d '\r' | awk '{print $1}'; }

# --- /proc -----------------------------------------------------------------------------------------
info "/proc"
mkdir -p "$out/proc"
for f in stat uptime; do
  "$ADB" exec-out cat "/proc/$f" > "$out/proc/$f" 2> /dev/null
done
launcher="$("$ADB" shell -T cmd package resolve-activity --brief -a android.intent.action.MAIN \
  -c android.intent.category.HOME 2> /dev/null | tr -d '\r' | tail -1 | cut -d/ -f1)"
pids=(1 2)
for name in system_server com.android.systemui com.android.phone surfaceflinger "$launcher" \
  com.google.android.gms.persistent; do
  [[ -n "$name" ]] || continue
  pid="$(first_pid "$name")"
  [[ -n "$pid" ]] && pids+=("$pid")
done
for pid in "${pids[@]}"; do
  mkdir -p "$out/proc/$pid"
  for f in stat status cmdline oom_score_adj; do
    if ! "$ADB" exec-out cat "/proc/$pid/$f" > "$raw/proc_${pid}_$f" 2> "$raw/proc_${pid}_$f.err"; then
      echo "unreadable" > "$out/proc/$pid/$f.error"
    fi
    if [[ "$f" == "cmdline" ]]; then
      anonymise < "$raw/proc_${pid}_$f" > "$out/proc/$pid/$f"
    else
      filter_default < "$raw/proc_${pid}_$f" > "$out/proc/$pid/$f"
    fi
    # cat prints "Permission denied" to stderr but still exits 0 on some toybox versions.
    if grep -qi 'denied\|No such' "$raw/proc_${pid}_$f.err" 2> /dev/null; then
      cp "$raw/proc_${pid}_$f.err" "$out/proc/$pid/$f.error"
    fi
  done
done
"$ADB" exec-out ls /proc 2> /dev/null | grep -c '^[0-9]' > "$out/proc/process-count.txt"

# --- processes, services, memory (same argv as ReadCommands in :core) -------------------------
info "processes and services"
U=0
SAMPLE_PKG=com.android.systemui
capture filter_default dumpsys activity services
capture filter_default dumpsys activity services "$SAMPLE_PKG"
capture filter_default dumpsys activity lru
capture filter_default dumpsys activity oom
sysui_pid="$(first_pid "$SAMPLE_PKG")"
[[ -n "$sysui_pid" ]] && capture filter_default dumpsys meminfo --checkin "$sysui_pid"
capture filter_default dumpsys package "$SAMPLE_PKG"
capture filter_default cmd appops get --user "$U" "$SAMPLE_PKG"
capture filter_default am get-standby-bucket --user "$U" "$SAMPLE_PKG"
capture filter_default pm list packages -s -U
capture filter_default cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# --- watchers ------------------------------------------------------------------------------------------
info "watchers"
for key in enabled_notification_listeners enabled_notification_policy_access_packages \
  enabled_accessibility_services accessibility_enabled enabled_input_methods default_input_method \
  always_on_vpn_app; do
  capture filter_default settings get secure "$key"
done
capture filter_default dumpsys notification
capture filter_default dumpsys notification --zen
capture filter_default dumpsys role
capture filter_default dumpsys accessibility
capture filter_default dumpsys device_policy
capture filter_default ime list -s
capture filter_default cmd appops query-op GET_USAGE_STATS allow
capture filter_default cmd appops query-op SYSTEM_ALERT_WINDOW allow
for role in SMS DIALER ASSISTANT BROWSER HOME; do
  capture filter_default cmd role get-role-holders --user "$U" "android.app.role.$role"
done
capture filter_network dumpsys vpn_management

# --- wakeups ----------------------------------------------------------------------------------------------
info "wakeups (batterystats can take a while)"
capture filter_default dumpsys alarm
capture filter_default dumpsys jobscheduler
capture filter_default dumpsys power
capture filter_default dumpsys batterystats -c android
capture filter_default am get-standby-bucket
capture filter_default cmd deviceidle whitelist

# --- help texts (document the command syntax this build accepts) --------------------------------------
info "command help texts"
capture filter_default am help
capture filter_default cmd appops help
capture filter_default cmd notification help
capture filter_default cmd role help
capture filter_default cmd deviceidle help
capture filter_default pm help
capture filter_default dpm help

cat << EOF >&2

Done. Fixtures written to ${out#"$ROOT"/}
Unredacted originals (do not commit): ${raw#"$ROOT"/}

Next:
  1. git diff --stat && git diff -- '${out#"$ROOT"/}'   # read it; remove anything personal
  2. commit the directory in a PR (or attach it as a zip to an issue)
EOF
