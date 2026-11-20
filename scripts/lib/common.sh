#!/usr/bin/env bash
# Shared helpers for scripts/capture-fixtures.sh and scripts/device-checks.sh.
# Requires: bash 4+, adb (platform-tools) in PATH, a device with USB or wireless debugging enabled.

set -uo pipefail

ADB="${ADB:-adb}"
# shellcheck disable=SC2034 # used by the scripts that source this file
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*" >&2; }

require_device() {
  command -v "$ADB" > /dev/null 2>&1 || die "adb not found (set ADB=/path/to/adb or add platform-tools to PATH)"
  local state
  state="$("$ADB" get-state 2> /dev/null)" || die "no device connected (adb get-state failed)"
  [[ "$state" == "device" ]] || die "device state is '$state', expected 'device'"
}

# Runs a command on the device without a PTY (byte-exact output, no CRLF) and keeps stdout,
# stderr and the exit status apart. Usage: dev_run <stdout-file> <stderr-file> <argv...>
# Returns the remote exit status (adb's shell protocol forwards it).
dev_run() {
  local out="$1" err="$2"
  shift 2
  "$ADB" shell -T "$@" > "$out" 2> "$err"
}

# Single-line device property.
prop() { "$ADB" shell -T getprop "$1" 2> /dev/null | tr -d '\r\n'; }

# Stable directory names: "samsung-sm-s911b" and the SDK level.
device_slug() {
  local manufacturer model
  manufacturer="$(prop ro.product.manufacturer)"
  model="$(prop ro.product.model)"
  echo "${manufacturer}-${model}" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g'
}

device_api() { prop ro.build.version.sdk; }

device_summary() {
  cat << EOF
manufacturer: $(prop ro.product.manufacturer)
model: $(prop ro.product.model)
device: $(prop ro.product.device)
android: $(prop ro.build.version.release) (API $(prop ro.build.version.sdk), minor $(prop ro.build.version.sdk_minor))
oneui: $(prop ro.build.version.oneui)
build: $(prop ro.build.display.id)
security_patch: $(prop ro.build.version.security_patch)
fingerprint: $(prop ro.build.fingerprint)
EOF
}

# Removes personal data from captured text. Applied to every fixture before it lands in the repo:
# e-mail addresses (sync accounts appear in job, wakelock and sync names), MAC addresses, data URIs
# in intents, and phone-number-like digit runs inside "tel:" URIs.
redact() {
  sed -E \
    -e 's/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/user@example.com/g' \
    -e 's/([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}/02:00:00:00:00:00/g' \
    -e 's/(dat=)[^ }]+/\1content:REDACTED/g' \
    -e 's/(tel:)[+0-9 -]+/\1REDACTED/g'
}

# Additional pass for network dumps: IPv4/IPv6 addresses and Wi-Fi names.
redact_network() {
  redact | sed -E \
    -e 's/\b([0-9]{1,3}\.){3}[0-9]{1,3}\b/192.0.2.1/g' \
    -e 's/\b([0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\b/2001:db8::1/g' \
    -e 's/(SSID: ?)"[^"]*"/\1"REDACTED"/g' \
    -e 's/(SSID: ?)[^,"]+,/\1REDACTED,/g'
}
