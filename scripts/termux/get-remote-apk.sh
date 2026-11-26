#!/data/data/com.termux/files/usr/bin/bash
# Lirnyk Remote (Spotify prototype) — private delivery to the owner's phone.
# Downloads the newest successful build of claude/spotify, decrypts it with the passphrase made by setup-signing.sh,
# checks its SHA-256 and opens the Android installer. The APK is never public.
#
# Usage:   bash get-remote-apk.sh
set -euo pipefail
set +x

REPO="legioner666/Lirnyk"
DIR="$HOME/.lirnyk-signing"
[ -f "$DIR/apk-passphrase.txt" ] || { echo "Run setup-signing.sh first (no passphrase in $DIR)."; exit 1; }
command -v gh >/dev/null && command -v openssl >/dev/null || pkg install -y gh openssl-tool

RUN=$(gh run list --repo "$REPO" --workflow spotify.yml --branch claude/spotify --status success --limit 1 --json databaseId --jq '.[0].databaseId')
[ -n "$RUN" ] || { echo "No successful build of claude/spotify yet."; exit 1; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
gh run download "$RUN" --repo "$REPO" --dir "$WORK"
ENC=$(find "$WORK" -name '*.apk.enc' | head -1)
[ -n "$ENC" ] || { echo "Build $RUN has no APK (was the signing key set up?)."; exit 1; }
APK="${ENC%.enc}"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -md sha256 -in "$ENC" -out "$APK" -pass file:"$DIR/apk-passphrase.txt"
(cd "$(dirname "$APK")" && sha256sum -c "$(basename "$APK").sha256")

[ -d "$HOME/storage/downloads" ] || termux-setup-storage
DEST="$HOME/storage/downloads/$(basename "$APK")"
cp "$APK" "$DEST"
echo "Saved: $DEST"
echo "SHA-256: $(sha256sum "$DEST" | cut -d' ' -f1)"
termux-open "$DEST" || echo "Open the file from Downloads to install it."
