#!/data/data/com.termux/files/usr/bin/bash
# Lirnyk — one-time setup of the persistent TEST signing key, run by the owner in Termux on the phone.
#
# What it does:
#   1. Creates a test signing key on THIS phone (or reuses the one made earlier — never overwrites it).
#   2. Stores it in GitHub Actions secrets of legioner666/Lirnyk with the GitHub CLI:
#        LIRNYK_TEST_KEYSTORE_BASE64, LIRNYK_TEST_KEYSTORE_PASSWORD, LIRNYK_APK_PASSPHRASE
#      and the public certificate fingerprint as the repository variable LIRNYK_TEST_CERT_SHA1.
#   3. Prints the SHA-1 fingerprint to enter in the Spotify dashboard (Android packages).
#
# The private key and passwords never leave the phone except into GitHub's encrypted secrets.
# Nothing secret is printed. Keep a backup of ~/.lirnyk-signing (losing it = a new key = reinstall + new SHA-1 in Spotify).
#
# Usage:   bash setup-signing.sh
set -euo pipefail
set +x

REPO="legioner666/Lirnyk"
DIR="$HOME/.lirnyk-signing"
KS="$DIR/lirnyk-test.p12"
ALIAS="lirnyk-test"   # must match app/build.gradle.kts

echo "== Lirnyk test signing setup for $REPO"

# --- tools -----------------------------------------------------------------------------------------------------------
need() { command -v "$1" >/dev/null 2>&1; }
if ! need keytool || ! need gh || ! need openssl; then
  echo "Installing openjdk-17, gh and openssl (Termux packages)…"
  pkg install -y openjdk-17 gh openssl-tool
fi

# --- GitHub login ----------------------------------------------------------------------------------------------------
if ! gh auth status >/dev/null 2>&1; then
  echo "Log in to GitHub (a browser window or a one-time code will open):"
  gh auth login --hostname github.com --git-protocol https --web
fi
gh repo view "$REPO" >/dev/null || { echo "No access to $REPO with this GitHub account."; exit 1; }

# --- key -------------------------------------------------------------------------------------------------------------
umask 077
mkdir -p "$DIR"
if [ -f "$KS" ]; then
  echo "Reusing the existing key in $DIR (not overwritten)."
else
  echo "Creating a new test key (RSA 4096, 30 years)…"
  openssl rand -base64 48 | tr -d '/+=\n' | head -c 40 > "$DIR/password.txt"
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
    -storepass:file "$DIR/password.txt" -dname "CN=Lirnyk test builds, O=Lirnyk, C=UA" >/dev/null 2>&1
fi
if [ ! -f "$DIR/apk-passphrase.txt" ]; then
  # Encrypts the private Spotify-prototype APK in CI; scripts/termux/get-remote-apk.sh decrypts it on this phone.
  openssl rand -base64 48 | tr -d '/+=\n' | head -c 48 > "$DIR/apk-passphrase.txt"
fi

SHA1=$(keytool -list -v -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" -storepass:file "$DIR/password.txt" 2>/dev/null | awk '/SHA1:/ {print $2; exit}')
SHA256=$(keytool -list -v -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" -storepass:file "$DIR/password.txt" 2>/dev/null | awk '/SHA256:/ {print $2; exit}')
[ -n "$SHA1" ] || { echo "Could not read the key fingerprint."; exit 1; }

# --- GitHub secrets (values go through stdin, never through the command line or the screen) -----------------------
base64 -w0 "$KS" | gh secret set LIRNYK_TEST_KEYSTORE_BASE64 --repo "$REPO"
gh secret set LIRNYK_TEST_KEYSTORE_PASSWORD --repo "$REPO" < "$DIR/password.txt"
gh secret set LIRNYK_APK_PASSPHRASE --repo "$REPO" < "$DIR/apk-passphrase.txt"
gh variable set LIRNYK_TEST_CERT_SHA1 --repo "$REPO" --body "$SHA1"

echo
echo "Done. Secrets and the SHA-1 variable are set in $REPO."
echo
echo "Certificate fingerprints (public, safe to share):"
echo "  SHA-1:   $SHA1"
echo "  SHA-256: $SHA256"
echo
echo "Spotify dashboard → your app → Edit → Android packages → add:"
echo "  Package name:      app.lirnyk.remote"
echo "  SHA-1 fingerprint: $SHA1"
echo
echo "Back up the folder $DIR (for example to a private USB stick). Do not send it to anyone."
