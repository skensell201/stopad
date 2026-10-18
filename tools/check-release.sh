#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
release_sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
release_tools=
for release_candidate in "$release_sdk"/build-tools/*/apksigner; do
    if [ -x "$release_candidate" ]; then release_tools=${release_candidate%/apksigner}; fi
done
if [ -z "$release_tools" ]; then echo 'Install Android SDK build-tools first' >&2; exit 2; fi
./gradlew :app:assembleRelease
release_output=$(sed -n 's/.*"outputFile": "\([^"]*\.apk\)".*/\1/p' app/build/outputs/apk/release/output-metadata.json)
case "$release_output" in app-release.apk|app-release-unsigned.apk) ;; *) echo 'Unexpected release output' >&2; exit 1 ;; esac
release_apk="app/build/outputs/apk/release/$release_output"
release_signer=$("$release_tools/apksigner" verify --verbose --print-certs "$release_apk")
release_expected=$(openssl x509 -in app/release-certificate.pem -outform DER | shasum -a 256 | awk '{print $1}')
release_actual=$(printf '%s\n' "$release_signer" | sed -n 's/^.*certificate SHA-256 digest: //p' | sort -u)
if [ "$release_actual" != "$release_expected" ]; then echo 'Release signer does not match the project public certificate' >&2; exit 1; fi
release_badging=$("$release_tools/aapt2" dump badging "$release_apk")
if printf '%s\n' "$release_badging" | rg -q '^application-debuggable'; then
    echo 'Release APK must not be debuggable' >&2; exit 1
fi
echo "PASS: signed release APK, pinned certificate matches, not debuggable: $release_apk"
