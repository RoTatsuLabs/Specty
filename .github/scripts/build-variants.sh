#!/usr/bin/env bash
# Builds every requested ABI variant, then validates and packages each APK.
# VARIANTS is a space separated list of label:expected-abis, for example
#   arm64-v8a:arm64-v8a universal:arm64-v8a,x86_64
# The label "universal" builds without an ABI restriction.
set -euo pipefail

: "${GRADLE_TASK:?}" "${APK_DIR:?}" "${VARIANTS:?}" "${APP:?}" "${APK_VERSION:?}" "${PACKAGE_ID:?}" "${VERSION_CODE:?}" "${OUT_DIR:?}"
fail() { echo "::error::$*"; exit 1; }

root="${GITHUB_WORKSPACE:?}"
cd "$root"

# The project has no Gradle wrapper, so the workflow installs Gradle and puts it on the path.
gradle=(gradle)

# The declared filter list is kept so the universal build can restore it after a single-ABI build.
original="$(mktemp)"
cp app/build.gradle.kts "$original"

for entry in $VARIANTS; do
  label="${entry%%:*}"
  expected="${entry#*:}"

  # The app declares all three ABIs in defaultConfig. Narrow that list in this temporary
  # checkout so each APK contains only its requested ABI. Do not use
  # -Pandroid.injected.build.abi for this: it marks the APK as test-only, and Android
  # refuses to install a test-only APK.
  cp "$original" app/build.gradle.kts
  if [ "$label" != universal ]; then
    sed -i -E "s/abiFilters[[:space:]]*\+=.*/abiFilters += listOf(\"$label\")/" app/build.gradle.kts
  fi

  rm -rf "$APK_DIR"

  echo "::group::Build $label"
  "${gradle[@]}" "$GRADLE_TASK" --stacktrace
  echo "::endgroup::"

  mapfile -t apks < <(find "$APK_DIR" -maxdepth 1 -name '*.apk' 2>/dev/null)
  if [ "${#apks[@]}" -ne 1 ]; then
    echo "APK files under app/build:"
    find app/build -name '*.apk' || true
    fail "Expected one APK in $APK_DIR for $label, found ${#apks[@]}."
  fi

  APK="${apks[0]}" ABI_LABEL="$label" EXPECTED_ABIS="$expected" bash "$root/.github/scripts/package-apk.sh"
done

cp "$original" app/build.gradle.kts
