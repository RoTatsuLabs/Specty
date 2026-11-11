#!/usr/bin/env bash
# Creates the release as a draft, checks the uploaded assets, then publishes it
# unless RELEASE_TYPE is draft. A failed run leaves the draft in place for inspection.
set -euo pipefail

: "${GH_TOKEN:?}" "${REPO:?}" "${TAG:?}" "${SOURCE_SHA:?}" "${APP:?}" "${VERSION:?}" "${RELEASE_TYPE:?}" "${RELEASE_DIR:?}"
fail() { echo "::error::$*"; exit 1; }

case "$RELEASE_TYPE" in
  draft | prerelease | release) ;;
  *) fail "release_type must be draft, prerelease or release, got: $RELEASE_TYPE" ;;
esac

bash "$(dirname "$0")/check-tag.sh"

notes="$RELEASE_DIR/notes.md"
[ -s "$notes" ] || fail "Release notes are missing or empty."

mapfile -t assets < <(find "$RELEASE_DIR" -maxdepth 1 -type f -name '*.apk' | sort)
[ "${#assets[@]}" -gt 0 ] || fail "No APK found in $RELEASE_DIR."
for f in "${assets[@]}"; do
  [ -s "$f" ] || fail "APK is empty: $f"
done

flags=(--draft)
if [ "$RELEASE_TYPE" = prerelease ]; then
  flags+=(--prerelease)
fi

gh release create "$TAG" --repo "$REPO" --target "$SOURCE_SHA" --title "$APP $VERSION" \
  --notes-file "$notes" "${flags[@]}" "${assets[@]}"

id="$(gh api --paginate "repos/$REPO/releases?per_page=100" --jq ".[] | select(.tag_name == \"$TAG\" and .draft) | .id" | head -1)"
[ -n "$id" ] || fail "The draft release for $TAG was not found after creating it."

uploaded="$(gh api "repos/$REPO/releases/$id" --jq '.assets[] | select(.state == "uploaded") | .name' | sort)"
expected="$(printf '%s\n' "${assets[@]##*/}" | sort)"
if [ "$uploaded" != "$expected" ]; then
  fail "Uploaded assets do not match. Expected APKs: $(tr '\n' ' ' <<< "$expected") Got: $(tr '\n' ' ' <<< "$uploaded"). The draft was left in place."
fi

target="$(gh api "repos/$REPO/releases/$id" --jq '.target_commitish')"
[ "$target" = "$SOURCE_SHA" ] || fail "Release targets $target, expected $SOURCE_SHA. The draft was left in place."

if [ "$RELEASE_TYPE" != draft ]; then
  gh api -X PATCH "repos/$REPO/releases/$id" -F draft=false > /dev/null
  [ "$(gh api "repos/$REPO/releases/$id" --jq '.draft')" = false ] || fail "The release is still a draft after publishing."
fi

url="$(gh api "repos/$REPO/releases/$id" --jq '.html_url')"
echo "Release: $url"
{
  echo "### $APP $VERSION"
  echo "- Type: $RELEASE_TYPE"
  echo "- Source commit: \`$SOURCE_SHA\`"
  echo "- Release: $url"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
