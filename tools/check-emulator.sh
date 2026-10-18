#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
probe_serial=${1:-}
case "$probe_serial" in emulator-*) ;; *) echo 'Usage: tools/check-emulator.sh emulator-5554 (isolated emulator only)' >&2; exit 2 ;; esac
probe_adb="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/platform-tools/adb"
"$probe_adb" -s "$probe_serial" get-state
./gradlew -PwithProbe :app:assembleDebug :app:assembleDebugAndroidTest :probe:assembleGameDebug :probe:assembleBrowserDebug
"$probe_adb" -s "$probe_serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$probe_adb" -s "$probe_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$probe_adb" -s "$probe_serial" install -r tools/probe/build/outputs/apk/game/debug/probe-game-debug.apk
"$probe_adb" -s "$probe_serial" install -r tools/probe/build/outputs/apk/browser/debug/probe-browser-debug.apk
"$probe_adb" -s "$probe_serial" shell appops set com.skensel.stopad ACTIVATE_VPN allow
probe_log=$(mktemp)
trap 'rm -f "$probe_log"' EXIT
"$probe_adb" -s "$probe_serial" shell am instrument -w com.skensel.stopad.test/com.skensel.stopad.VpnIntegration > "$probe_log"
cat "$probe_log"
# am instrument can exit 0 even when an assertion or the test process failed.
rg -q '^PASS:' "$probe_log"
