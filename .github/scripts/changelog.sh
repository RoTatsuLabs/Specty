#!/usr/bin/env bash
# Prints release notes from Conventional Commit subjects since the ref in $1 (all history when empty). Fixes are left out.
set -euo pipefail

from="${1:-}"
range="HEAD"
[ -n "$from" ] && range="$from..HEAD"

features=""
improvements=""
other=""
while IFS='|' read -r hash subject; do
  type=$(printf '%s' "$subject" | sed -nE 's/^([A-Za-z]+)(\([^)]*\))?!?:.*/\L\1/p')
  [ "$type" = "fix" ] && continue
  text=$(printf '%s' "$subject" | sed -E 's/^[A-Za-z]+(\([^)]*\))?!?: *//')
  entry="- ${text} (\`${hash}\`)"$'\n'
  case "$type" in
    feat) features+="$entry" ;;
    perf | refactor) improvements+="$entry" ;;
    *) other+="$entry" ;;
  esac
done < <(git log --no-merges --pretty='%h|%s' "$range")

[ -n "$features" ] && printf '## Features\n\n%s\n' "$features"
[ -n "$improvements" ] && printf '## Improvements\n\n%s\n' "$improvements"
[ -n "$other" ] && printf '## Other changes\n\n%s\n' "$other"
[ -z "$features$improvements$other" ] && echo "No user-facing changes."
exit 0
