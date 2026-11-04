#!/usr/bin/env bash
# Nooki promises that nothing leaves the phone. Fail the build if a shipped APK could break that.
set -euo pipefail
aapt2="$(find "$ANDROID_HOME/build-tools" -name aapt2 | sort -V | tail -1)"
for apk in "$@"; do
  perms="$("$aapt2" dump permissions "$apk")"
  echo "--- $apk"
  echo "$perms"
  if echo "$perms" | grep -q 'android.permission.INTERNET'; then
    echo "FAILED: $apk asks for the internet permission." >&2
    exit 1
  fi
done
echo "OK: no internet permission in any APK."
