#!/usr/bin/env bash
# Rehearses .github/workflows/release.yml locally: the same version rules, the same Gradle call and
# the same checks on the APK, but signed with a throwaway key that is created in a temporary
# directory and deleted afterwards. Nothing is tagged, uploaded or published.
#
# Usage: scripts/release-dry-run.sh [VERSION]      (default 0.1.0-rc1)
#
# Needs a JDK (keytool) and the Android SDK (ANDROID_HOME) with build-tools. Output:
# build/release-dry-run/picket-v<VERSION>.apk, SHA256SUMS.txt and report.txt.

# shellcheck source=lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

version="${1:-0.1.0-rc1}"
version="${version#v}"

# ---- version rules, identical to the "Resolve and validate version" step -------------------------
re='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)(-rc([1-9][0-9]?))?$'
[[ "$version" =~ $re ]] || die "'$version' is not MAJOR.MINOR.PATCH[-rcN] (MINOR/PATCH 0-99, N 1-98)"
rc="${BASH_REMATCH[5]:-}"
[[ -n "$rc" && "$rc" -gt 98 ]] && die "rc number above 98"
code=$((BASH_REMATCH[1] * 1000000 + BASH_REMATCH[2] * 10000 + BASH_REMATCH[3] * 100 + ${rc:-99}))
((code <= 2100000000)) || die "versionCode $code exceeds Android's limit"
info "Version $version, versionCode $code"

[[ -n "${ANDROID_HOME:-}" ]] || die "ANDROID_HOME is not set"
bt="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
[[ -x "$bt/apksigner" && -x "$bt/aapt2" ]] || die "no build-tools with apksigner and aapt2 under $ANDROID_HOME"

out="$ROOT/build/release-dry-run"
rm -rf "$out"
mkdir -p "$out"
report="$out/report.txt"
: > "$report"
log() { printf '%s\n' "$*" | tee -a "$report"; }

# ---- throwaway key, never reused ---------------------------------------------------------------------
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
umask 077
pass="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
keytool -genkeypair -keystore "$tmp/dry-run.jks" -storetype PKCS12 -alias dry-run -keyalg RSA \
  -keysize 3072 -validity 1 -storepass "$pass" -keypass "$pass" -dname "CN=Picket release dry run" \
  > /dev/null 2>&1 || die "keytool failed"

# ---- build, exactly as the workflow does -----------------------------------------------------------------
# A stale APK from an earlier build would break the "exactly one APK" check, as in CI.
rm -rf "$ROOT/app/build/outputs/apk/release"
(
  cd "$ROOT"
  SIGNING_KEYSTORE_PATH="$tmp/dry-run.jks" SIGNING_STORE_PASSWORD="$pass" SIGNING_KEY_ALIAS=dry-run \
    SIGNING_KEY_PASSWORD="$pass" ./gradlew :app:assembleRelease -PappVersionName="$version" -PappVersionCode="$code"
) || die "release build failed"

# ---- "Collect and verify APK" -------------------------------------------------------------------------------
shopt -s nullglob
apks=("$ROOT"/app/build/outputs/apk/release/*.apk)
((${#apks[@]} == 1)) || die "expected exactly one release APK, found ${#apks[@]}"
apk="$out/picket-v$version.apk"
cp "${apks[0]}" "$apk"

log "== apksigner verify"
verify="$("$bt/apksigner" verify --verbose --print-certs "$apk")" || die "APK signature does not verify"
log "$verify"
grep -qE "Verified using v(2|3|3\.1) scheme .*: true" <<< "$verify" || die "no v2/v3 signature"

log "== badging"
badging="$("$bt/aapt2" dump badging "$apk" | head -1)"
log "$badging"
grep -q "versionCode='$code'" <<< "$badging" || die "versionCode mismatch"
grep -q "versionName='$version'" <<< "$badging" || die "versionName mismatch"
grep -q "name='app.brokoli5191.picket'" <<< "$badging" || die "applicationId is not app.brokoli5191.picket"

# ---- checks the workflow does not need but a first release should pass -------------------------------
log "== permissions"
perms="$("$bt/aapt2" dump permissions "$apk")"
log "$perms"
grep -q "android.permission.INTERNET" <<< "$perms" && die "the release APK requests INTERNET"

log "== R8 kept the Shizuku entry points"
dexdump="$bt/dexdump"
classes="$(unzip -Z1 "$apk" 'classes*.dex' | while read -r dex; do
  unzip -p "$apk" "$dex" > "$tmp/$dex"
  "$dexdump" "$tmp/$dex" | grep -E "Class descriptor" || true
done)"
# shellcheck disable=SC2016 # $Stub is part of the class name
for keep in 'Lapp/brokoli5191/picket/privileged/service/PicketUserService;' \
  'Lapp/brokoli5191/picket/privileged/IPicketService$Stub;'; do
  grep -qF "$keep" <<< "$classes" || die "R8 removed or renamed $keep"
  log "kept $keep"
done

log "== baseline profile"
# The APK carries the profiles of its libraries (Compose and others) even without one of Picket's.
# Listing captured first: `unzip | grep -q` fails under pipefail when grep exits before unzip.
entries="$(unzip -Z1 "$apk")"
if grep -qx 'assets/dexopt/baseline.prof' <<< "$entries"; then
  log "assets/dexopt/baseline.prof present"
else
  log "no assets/dexopt/baseline.prof"
fi
if compgen -G "$ROOT/app/src/*/generated/baselineProfiles/baseline-prof.txt" > /dev/null; then
  log "includes Picket's generated profile"
else
  log "library profiles only: Picket's own profile has not been generated yet (baseline-profile.yml)"
fi

log "== size"
log "$(du -h "$apk" | cut -f1) $(basename "$apk")"

(cd "$out" && sha256sum -- *.apk > SHA256SUMS.txt && tee -a "$report" < SHA256SUMS.txt)
info "Dry run passed. Not done here: secrets check, tag checks against GitHub, attestation, publishing."
info "Report: ${report#"$ROOT"/}"
