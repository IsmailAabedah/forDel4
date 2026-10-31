#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/reference/CarCheck-v1.1.apk"
SRC="$ROOT/reference/CarCheck-jadx-sources.zip"
test -s "$APK"
test -s "$SRC"
echo "CarCheck reference APK: $(stat -c '%s bytes' "$APK")"
echo "Extracted source archive: $(stat -c '%s bytes' "$SRC")"
sha256sum "$APK" "$SRC"
unzip -l "$SRC"
