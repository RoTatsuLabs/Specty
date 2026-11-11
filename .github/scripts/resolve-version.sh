#!/usr/bin/env bash
# Validates the dispatch inputs and works out the version values for a build.
set -euo pipefail

: "${MODE:?}" "${SOURCE_REF:?}" "${VERSION:?}"
fail() { echo "::error::$*"; exit 1; }

if ! [[ "$SOURCE_REF" =~ ^[A-Za-z0-9._/-]+$ ]] || [[ "$SOURCE_REF" == -* || "$SOURCE_REF" == *..* ]]; then
  fail "source_ref has characters that are not allowed: $SOURCE_REF"
fi

if ! [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  fail "version must look like X.Y.Z, got: $VERSION"
fi
major=${BASH_REMATCH[1]}
minor=${BASH_REMATCH[2]}
patch=${BASH_REMATCH[3]}

code="${VERSION_CODE_INPUT:-}"
if [ -z "$code" ]; then
  case "$MODE" in
    nightly)
      code="${RUN_NUMBER:?}"
      ;;
    release)
      if (( 10#$minor >= 100 || 10#$patch >= 100 )); then
        fail "Cannot derive versionCode from $VERSION. Pass version_code explicitly."
      fi
      code=$(( 10#$major * 10000 + 10#$minor * 100 + 10#$patch ))
      ;;
    *)
      fail "Unknown mode: $MODE"
      ;;
  esac
fi

if ! [[ "$code" =~ ^[1-9][0-9]{0,9}$ ]] || (( code > 2100000000 )); then
  fail "versionCode must be a whole number between 1 and 2100000000, got: $code"
fi

if [ "$MODE" = nightly ]; then
  apk_version="${VERSION}-nightly"
else
  apk_version="$VERSION"
fi

{
  echo "gradle_version=$VERSION"
  echo "apk_version=$apk_version"
  echo "version_code=$code"
} >> "$GITHUB_OUTPUT"

echo "Version $apk_version, versionCode $code"
