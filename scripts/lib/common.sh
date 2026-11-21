#!/usr/bin/env bash
# Shared helpers for the scripts in this directory.
# The device scripts need bash 4+, adb (platform-tools) in PATH and a device with USB or wireless debugging.

set -uo pipefail

ADB="${ADB:-adb}"
# shellcheck disable=SC2034 # used by the scripts that source this file
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*" >&2; }

require_device() {
  command -v "$ADB" > /dev/null 2>&1 ||
    die "adb not found. Debian/Ubuntu: sudo apt install adb; Fedora/Arch: install android-tools (or set ADB=/path/to/adb)"
  local devices count state
  devices="$("$ADB" devices 2> /dev/null | sed -n '2,$p' | sed '/^[[:space:]]*$/d')"
  count="$(printf '%s' "$devices" | grep -c . || true)"
  if [[ -z "${ANDROID_SERIAL:-}" ]] && ((count > 1)); then
    printf '%s\n' "$devices" >&2
    die "more than one device: pick one with ANDROID_SERIAL=<serial> $0"
  fi
  ((count > 0)) || die "no device found: enable USB debugging (or connect wireless debugging) and check 'adb devices'"
  state="$("$ADB" get-state 2>&1)"
  case "$state" in
    device) ;;
    *unauthorized*) die "the phone has not allowed this computer yet: unlock it and confirm the USB debugging prompt" ;;
    *offline*) die "the device is offline: reconnect the cable or run 'adb kill-server' and try again" ;;
    *) die "unexpected device state: $state" ;;
  esac
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
