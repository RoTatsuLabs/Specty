#!/usr/bin/env bash
# Fails when the release tag or a release for it already exists in the app repository.
set -euo pipefail

: "${GH_TOKEN:?}" "${REPO:?}" "${TAG:?}"
fail() { echo "::error::$*"; exit 1; }

[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Unexpected tag format: $TAG"

if out="$(gh api "repos/$REPO/git/ref/tags/$TAG" 2>&1)"; then
  fail "Tag $TAG already exists in $REPO."
elif ! grep -q 'HTTP 404' <<< "$out"; then
  fail "Could not check tags in $REPO: $out"
fi

existing="$(gh api --paginate "repos/$REPO/releases?per_page=100" --jq ".[] | select(.tag_name == \"$TAG\") | .html_url")"
[ -z "$existing" ] || fail "A release for $TAG already exists: $existing"

echo "Tag $TAG is free in $REPO."
