#!/usr/bin/env bash
# Downloads the Spotify App Remote SDK 0.8.0 (Apache-2.0) from Spotify's official repository at a pinned commit and
# checks its SHA-256. The binary is not committed to this repository.
set -euo pipefail
cd "$(dirname "$0")/.."
COMMIT=5aa4d62465f61a0677081ae9a3108177d0365fc3
NAME=spotify-app-remote-release-0.8.0.aar
SHA256=b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198
OUT=spotify/app/libs/$NAME
mkdir -p "$(dirname "$OUT")"
if [ ! -f "$OUT" ] || ! echo "$SHA256  $OUT" | sha256sum -c --status; then
  curl -fsSL -o "$OUT.tmp" "https://raw.githubusercontent.com/spotify/android-sdk/$COMMIT/app-remote-lib/$NAME"
  mv "$OUT.tmp" "$OUT"
fi
echo "$SHA256  $OUT" | sha256sum -c
