#!/usr/bin/env bash
# Native Java/Android-SDK-only build. No Gradle, Maven, NPM, ad SDK or analytics dependency.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" || ! -d "$SDK" ]]; then
  echo "Android SDK not available. Install Android SDK platform 35+ and build-tools 35+." >&2
  exit 2
fi
ANDROID_JAR=""
for level in 35 36 37; do
  if [[ -f "$SDK/platforms/android-$level/android.jar" ]]; then ANDROID_JAR="$SDK/platforms/android-$level/android.jar"; break; fi
done
if [[ -z "$ANDROID_JAR" ]]; then echo "Android SDK platform 35+ required." >&2; exit 2; fi
TOOLS="${BUILD_TOOLS_DIR:-}"
if [[ -z "$TOOLS" ]]; then
  mapfile -t CANDIDATES < <(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -Vr)
  for candidate in "${CANDIDATES[@]}"; do
    if [[ -x "$candidate/aapt2" && -x "$candidate/d8" && -x "$candidate/apksigner" ]]; then TOOLS="$candidate";break;fi
  done
fi
[[ -x "$TOOLS/aapt2" && -x "$TOOLS/d8" && -x "$TOOLS/zipalign" && -x "$TOOLS/apksigner" ]] || { echo "Complete Android build-tools required." >&2;exit 2; }
for tool in java javac jar zip keytool openssl python3;do command -v "$tool" >/dev/null || { echo "Missing $tool" >&2;exit 2; };done
bash test-core.sh
bash test-auth.sh
bash test-lifecycle.sh
bash test-browser-auth.sh
bash test-browser-service.sh
bash test-widget-refresh-service.sh
bash test-localization.sh
bash test-language.sh
bash test-widget-publish.sh
bash test-background-access.sh
bash test-floating-style.sh
bash test-floating-service.sh
bash test-preview.sh
bash test-syntax.sh
python3 check-project.py
python3 tests/check-adaptive-icon.py
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" dist
"$TOOLS/aapt2" compile --dir app/src/main/res -o "$OUT/resources.zip"
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest app/src/main/AndroidManifest.xml \
  --java "$OUT/gen" -A app/src/main/assets --min-sdk-version 26 --target-sdk-version 35 --version-code 13 --version-name 0.6.3 \
  -o "$OUT/base.apk" "$OUT/resources.zip"
find app/src/main/java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
[[ -f "$TOOLS/core-lambda-stubs.jar" ]] || { echo "Android build-tools core-lambda-stubs.jar required." >&2;exit 2; }
javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$TOOLS/core-lambda-stubs.jar:$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$TOOLS/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
zip -q -j "$OUT/unaligned.apk" "$OUT/dex/"*.dex
javac --release 8 -encoding UTF-8 -d "$OUT" tests/ApkAssetTests.java
java -cp "$OUT" ApkAssetTests "$OUT/unaligned.apk" app/src/main/assets
"$TOOLS/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
# Signing material lives outside the repository. Never include it in a public artifact.
KEYDIR="${WEEKLYMETER_SIGNING_DIR:-$HOME/.local/share/weeklymeter-signing}"
mkdir -p "$KEYDIR";chmod 700 "$KEYDIR"
if [[ ! -f "$KEYDIR/key.p12" ]]; then
  umask 077
  openssl rand -hex 32 > "$KEYDIR/password"
  keytool -genkeypair -keystore "$KEYDIR/key.p12" -storetype PKCS12 -storepass:file "$KEYDIR/password" \
    -alias weeklymeter -keyalg RSA -keysize 3072 -validity 3650 -dname "CN=WeeklyMeter Personal Build" >/dev/null
fi
[[ -f "$KEYDIR/password" ]] || { echo "Signing password file missing. Do not overwrite an existing signing key." >&2;exit 2; }
"$TOOLS/apksigner" sign --ks "$KEYDIR/key.p12" --ks-key-alias weeklymeter --ks-pass "file:$KEYDIR/password" \
  --out dist/WeeklyMeter.apk "$OUT/aligned.apk"
java -cp "$OUT" ApkAssetTests dist/WeeklyMeter.apk app/src/main/assets
"$TOOLS/apksigner" verify --verbose --print-certs dist/WeeklyMeter.apk > dist/APK-VERIFICATION.txt
"$TOOLS/aapt2" dump badging dist/WeeklyMeter.apk > dist/APK-METADATA.txt
(cd dist && sha256sum WeeklyMeter.apk > SHA256SUMS.txt)
cat > dist/READ-FIRST.txt <<'TXT'
This is a personal, unofficial experimental Android app.
Build/signature verification is NOT a security audit or proof of successful OpenAI login.
The app uses a public Codex browser PKCE flow and a service-internal usage endpoint.
Compare the app's chosen weekly bucket against the official usage page after login.
Never share passwords or tokens in a chat or build log.
Keep the same private signing key for future update installs.
No signing key or account credentials are included in this artifact.
TXT
printf '\nBuilt: %s/dist/WeeklyMeter.apk\n' "$PWD"
