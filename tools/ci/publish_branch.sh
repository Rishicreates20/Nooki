#!/usr/bin/env bash
# Puts a folder of CI output on its own throwaway branch, replacing whatever was there.
# Usage: publish_branch.sh <branch> <folder>
set -euo pipefail
branch="$1"
folder="$2"
[ -d "$folder" ] || { echo "Nothing to publish in $folder"; exit 0; }
work="$(mktemp -d)"
cp -r "$folder"/. "$work"/
cd "$work"
git init -q -b "$branch"
git add -A
git -c user.name="nooki-ci" -c user.email="nooki-ci@users.noreply.github.com" \
  commit -q -m "CI output for ${GITHUB_SHA:-local}"
git push -q -f "https://x-access-token:${TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$branch"
echo "Published $folder to $branch"
