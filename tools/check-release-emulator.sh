#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
release_serial=${1:-}
case "$release_serial" in emulator-*) ;; *) echo 'Usage: tools/check-release-emulator.sh emulator-5556 (clean isolated emulator)' >&2; exit 2 ;; esac
release_sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}
release_adb="$release_sdk/platform-tools/adb"
"$release_adb" -s "$release_serial" get-state
for release_package in com.skensel.stopad com.skensel.stopad.test com.skensel.stopad.probe.game com.skensel.stopad.probe.browser com.skensel.stopad.probe.manual; do
    if "$release_adb" -s "$release_serial" shell pm path "$release_package" | rg -q '^package:'; then
        echo "Use a clean isolated emulator: existing $release_package is preserved" >&2; exit 2
    fi
done
tools/check-release.sh
./gradlew -PwithProbe :app:assembleDebugAndroidTest :probe:assembleGameDebug :probe:assembleBrowserDebug :probe:assembleManualDebug
release_tools=
for release_candidate in "$release_sdk"/build-tools/*/apksigner; do if [ -x "$release_candidate" ]; then release_tools=${release_candidate%/apksigner}; fi; done
release_signing=${STOPAD_SIGNING_DIR:-$HOME/.local/share/stopad/signing}
release_temp=$(mktemp -d)
release_installed=0
release_dns=$("$release_adb" -s "$release_serial" shell settings get global private_dns_mode | tr -d '\r')
case "$release_dns" in null|off|opportunistic|hostname) ;; *) echo 'Unexpected Private DNS test setting' >&2; exit 2 ;; esac
cleanup() {
    release_exit=$?
    trap - EXIT
    if [ "$release_installed" = 1 ]; then
        "$release_adb" -s "$release_serial" shell am force-stop com.skensel.stopad >/dev/null 2>&1 || true
        "$release_adb" -s "$release_serial" shell appops set com.skensel.stopad ACTIVATE_VPN default >/dev/null 2>&1 || true
        for release_package in com.skensel.stopad.test com.skensel.stopad.probe.game com.skensel.stopad.probe.browser com.skensel.stopad.probe.manual; do
            "$release_adb" -s "$release_serial" uninstall "$release_package" >/dev/null 2>&1 || true
        done
    fi
    if [ "$release_dns" = null ]; then "$release_adb" -s "$release_serial" shell settings delete global private_dns_mode >/dev/null 2>&1 || true
    else "$release_adb" -s "$release_serial" shell settings put global private_dns_mode "$release_dns" >/dev/null 2>&1 || true; fi
    rm -rf "$release_temp"
    exit "$release_exit"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# The test APK must share the real release signer to instrument a non-debuggable app.
"$release_tools/apksigner" sign --ks "$release_signing/release.p12" --ks-key-alias stopad-release --ks-pass "file:$release_signing/storepass" --out "$release_temp/release-test.apk" app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$release_adb" -s "$release_serial" install app/build/outputs/apk/release/app-release.apk
release_installed=1
"$release_adb" -s "$release_serial" install "$release_temp/release-test.apk"
for release_flavor in game browser manual; do "$release_adb" -s "$release_serial" install "tools/probe/build/outputs/apk/$release_flavor/debug/probe-$release_flavor-debug.apk"; done
"$release_adb" -s "$release_serial" shell appops set com.skensel.stopad ACTIVATE_VPN allow
"$release_adb" -s "$release_serial" shell settings put global private_dns_mode off
for release_mode in networkTests listTests uiTests; do
    echo "Release instrumentation: $release_mode"
    "$release_adb" -s "$release_serial" shell am instrument -w -e "$release_mode" true com.skensel.stopad.test/com.skensel.stopad.VpnIntegration > "$release_temp/$release_mode.log" 2>&1 || { cat "$release_temp/$release_mode.log"; exit 1; }
    cat "$release_temp/$release_mode.log"
    rg -q '^PASS:' "$release_temp/$release_mode.log"
    "$release_adb" -s "$release_serial" shell am force-stop com.skensel.stopad
done
echo 'PASS: release APK installed; real network, list updates and UI instrumentation passed'
