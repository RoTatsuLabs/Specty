#!/usr/bin/env bash
# Validates one APK, then writes {APP}_{version}_{ABI}.apk and the matching ZIP into OUT_DIR.
# A build type that is debuggable on purpose (a preview build) sets ALLOW_DEBUGGABLE=1.
set -euo pipefail

: "${APK:?}" "${APP:?}" "${APK_VERSION:?}" "${ABI_LABEL:?}" "${PACKAGE_ID:?}" "${VERSION_CODE:?}" "${EXPECTED_ABIS:?}" "${OUT_DIR:?}"
fail() { echo "::error::$*"; exit 1; }

[ -s "$APK" ] || fail "APK is missing or empty: $APK"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -d "$sdk/build-tools" ] || fail "Android SDK build-tools were not found."
tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
[ -x "$tools/aapt2" ] || fail "aapt2 was not found in $tools."

badging="$("$tools/aapt2" dump badging "$APK")"
header="$(grep -m1 '^package:' <<< "$badging")"
found_pkg="$(sed -n "s/.* name='\([^']*\)'.*/\1/p" <<< "$header")"
found_code="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$header")"
found_name="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$header")"

[ "$found_pkg" = "$PACKAGE_ID" ] || fail "Package ID is $found_pkg, expected $PACKAGE_ID."
[ "$found_code" = "$VERSION_CODE" ] || fail "versionCode is $found_code, expected $VERSION_CODE."
[ "$found_name" = "$APK_VERSION" ] || fail "versionName is $found_name, expected $APK_VERSION."

manifest="$("$tools/aapt2" dump xmltree --file AndroidManifest.xml "$APK")"
if grep -q 'android:testOnly' <<< "$manifest"; then
  fail "APK is marked test-only and cannot be installed."
fi
if [ "${ALLOW_DEBUGGABLE:-0}" != 1 ] && grep 'android:debuggable' <<< "$manifest" | grep -qE 'true|0xffffffff'; then
  fail "APK is debuggable."
fi

found_abis="$(unzip -Z1 "$APK" | awk -F/ '$1 == "lib" && NF > 2 { print $2 }' | sort -u | paste -sd, -)"
if [ "$EXPECTED_ABIS" != any ]; then
  wanted_abis="$(tr ',' '\n' <<< "$EXPECTED_ABIS" | sort -u | paste -sd, -)"
  [ "$found_abis" = "$wanted_abis" ] || fail "ABIs in the APK are '${found_abis:-none}', expected '$wanted_abis'."
fi

certs="$("$tools/apksigner" verify --print-certs "$APK")" || fail "APK signature check failed for $APK."
if grep -q 'CN=Android Debug' <<< "$certs"; then
  fail "APK is signed with the Android debug key."
fi

name="${APP}_${APK_VERSION}_${ABI_LABEL}"
mkdir -p "$OUT_DIR"
cp "$APK" "$OUT_DIR/$name.apk"
(cd "$OUT_DIR" && rm -f "$name.zip" && zip -q -X "$name.zip" "$name.apk")

[ "$(unzip -Z1 "$OUT_DIR/$name.zip")" = "$name.apk" ] || fail "$name.zip does not contain exactly $name.apk."
unzip -tq "$OUT_DIR/$name.zip" > /dev/null || fail "$name.zip failed the integrity test."

echo "$name: package $found_pkg, versionName $found_name, versionCode $found_code, ABIs ${found_abis:-none}, signature verified"
