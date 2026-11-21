#!/usr/bin/env bash
# One-time setup of Picket's release signing. Run it on your own computer, never in CI or a cloud
# session: it creates the release keystore and stores the four secrets .github/workflows/release.yml
# reads (SIGNING_KEYSTORE_B64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD).
#
# Usage: scripts/setup-release-signing.sh [--dry-run] [KEYSTORE]   (default: ~/picket-release.jks)
#
# Needs keytool (JDK) and base64; without --dry-run also the GitHub CLI, logged in with access to the
# repository's secrets. The password is read from the terminal, never echoed and never put on a
# command line. --dry-run creates the keystore and lists what would be stored, without touching GitHub.
#
# Back up the keystore and the password (password manager plus an offline copy). GitHub secrets
# cannot be read back, and without the key no update can ever be installed over an existing Picket.

# shellcheck source=lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

REPO="Brokoli5191/Picket"
ALIAS="picket"

dry_run=false
if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=true
  shift
fi
keystore="${1:-$HOME/picket-release.jks}"

command -v keytool > /dev/null || die "keytool not found; install a JDK"
command -v base64 > /dev/null || die "base64 not found"
if ! $dry_run; then
  command -v gh > /dev/null || die "GitHub CLI (gh) not found"
  gh auth status > /dev/null 2>&1 || die "gh is not logged in; run 'gh auth login' first"
fi
[[ -e "$keystore" ]] && die "$keystore exists; refusing to overwrite a signing key"

read -r -s -p "Password for the new keystore (at least 6 characters): " pass
echo >&2
read -r -s -p "Repeat it: " again
echo >&2
[[ "$pass" == "$again" ]] || die "the passwords differ"
((${#pass} >= 6)) || die "keytool needs at least 6 characters"

umask 077
export PICKET_SIGNING_PASS="$pass"
out="$(keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA \
  -keysize 4096 -validity 10000 -dname "CN=Picket" \
  -storepass:env PICKET_SIGNING_PASS -keypass:env PICKET_SIGNING_PASS 2>&1)" || die "keytool failed: $out"
fingerprint="$(keytool -list -v -keystore "$keystore" -storetype PKCS12 -alias "$ALIAS" \
  -storepass:env PICKET_SIGNING_PASS 2> /dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -1)"
keystore_b64="$(base64 < "$keystore" | tr -d '\r\n')"

# printf is a shell builtin, so values never show up in the process list; gh reads them from stdin.
store_secret() {
  if $dry_run; then
    info "would set $1 (${#2} characters)"
    return
  fi
  printf '%s' "$2" | gh secret set "$1" --repo "$REPO" > /dev/null || die "could not set $1"
  info "set $1"
}
store_secret SIGNING_KEYSTORE_B64 "$keystore_b64"
store_secret SIGNING_STORE_PASSWORD "$pass"
# PKCS12 has one password for store and key.
store_secret SIGNING_KEY_PASSWORD "$pass"
store_secret SIGNING_KEY_ALIAS "$ALIAS"
unset PICKET_SIGNING_PASS pass again keystore_b64

info "Keystore: $keystore (alias $ALIAS)"
info "Certificate SHA-256: ${fingerprint:-unknown}"
info "Back up the keystore and the password now. Never commit the keystore."
