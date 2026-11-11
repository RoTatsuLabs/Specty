#!/usr/bin/env bash
# Decodes the signing keystore and checks that the alias and password open it.
set -euo pipefail

: "${KEYSTORE_B64:?Signing keystore secret is empty}" "${SIGNING_ALIAS:?Signing alias secret is empty}" "${SIGNING_PASSWORD:?Signing password secret is empty}"
fail() { echo "::error::$*"; exit 1; }

keystore="${RUNNER_TEMP:?}/signing.jks"
umask 077
printf '%s' "$KEYSTORE_B64" | base64 -d > "$keystore" 2>/dev/null || fail "The keystore secret is not valid base64."
[ -s "$keystore" ] || fail "The decoded keystore is empty."

if ! keytool -list -keystore "$keystore" -storepass:env SIGNING_PASSWORD -alias "$SIGNING_ALIAS" > /dev/null 2>&1; then
  rm -f "$keystore"
  fail "The keystore did not open with the given alias and password."
fi

echo "SIGNING_KEYSTORE=$keystore" >> "$GITHUB_ENV"
echo "Signing keystore is ready."
