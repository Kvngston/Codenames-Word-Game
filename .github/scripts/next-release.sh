#!/usr/bin/env bash
# Picks the next vX.Y.Z version for HEAD and creates a GitHub release for it.
# Patch bump by default; #minor or #major in any commit message since the last
# release bumps those instead. Writes `tag=<version>` to $GITHUB_OUTPUT.
set -euo pipefail

output="${GITHUB_OUTPUT:-/dev/stdout}"
semver='^v[0-9]+\.[0-9]+\.[0-9]+$'

# A re-run for a commit that is already released deploys that release again.
existing="$(git tag --points-at HEAD | grep -E "$semver" | sort -V | tail -n 1 || true)"
if [ -n "$existing" ]; then
  echo "HEAD is already released as $existing"
  echo "tag=$existing" >> "$output"
  exit 0
fi

latest="$(git tag --list 'v*' | grep -E "$semver" | sort -V | tail -n 1 || true)"
if [ -z "$latest" ]; then
  next="v1.0.0"
else
  messages="$(git log --format=%B "$latest..HEAD")"
  IFS=. read -r major minor patch <<< "${latest#v}"
  if grep -qiE '#major\b' <<< "$messages"; then
    next="v$((major + 1)).0.0"
  elif grep -qiE '#minor\b' <<< "$messages"; then
    next="v$major.$((minor + 1)).0"
  else
    next="v$major.$minor.$((patch + 1))"
  fi
fi

gh release create "$next" --target "$(git rev-parse HEAD)" --title "$next" --generate-notes
echo "Released $next (previous: ${latest:-none})"
echo "tag=$next" >> "$output"
