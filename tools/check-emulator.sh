#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
probe_serial=${1:-}
probe_mode=${2:-}
case "$probe_serial" in emulator-*) ;; *) echo 'Usage: tools/check-emulator.sh emulator-5554 [--ui|--ui-large|--lists|--network|--lifecycle] (isolated emulator only)' >&2; exit 2 ;; esac
case "$probe_mode" in ''|--ui|--ui-large|--lists|--network|--lifecycle) ;; *) echo 'Unknown test mode' >&2; exit 2 ;; esac
probe_ui=0
case "$probe_mode" in --ui|--ui-large) probe_ui=1 ;; esac
probe_adb="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/platform-tools/adb"
"$probe_adb" -s "$probe_serial" get-state
# One runner avoids Android silently retaining only the last instrumentation for a target.
if [ "$probe_ui" = 1 ]; then
    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
else
    ./gradlew -PwithProbe :app:assembleDebug :app:assembleDebugAndroidTest :probe:assembleGameDebug :probe:assembleBrowserDebug :probe:assembleManualDebug
fi
probe_log=$(mktemp)
probe_previous_op=$("$probe_adb" -s "$probe_serial" shell appops get com.skensel.stopad ACTIVATE_VPN 2>/dev/null || true)
case "$probe_previous_op" in
    *'ACTIVATE_VPN: allow'*) probe_previous_mode=allow ;;
    *'ACTIVATE_VPN: ignore'*) probe_previous_mode=ignore ;;
    *'ACTIVATE_VPN: deny'*) probe_previous_mode=deny ;;
    *'ACTIVATE_VPN: foreground'*) probe_previous_mode=foreground ;;
    *) probe_previous_mode=default ;;
esac
probe_added_game=0
probe_added_browser=0
probe_added_manual=0
probe_added_test=0
probe_test_started=0
cleanup() {
    probe_exit=$?
    trap - EXIT
    if [ "$probe_test_started" = 1 ] && [ "$probe_ui" = 0 ]; then
        # Also closes a TUN if instrumentation crashed before its finally block ran.
        "$probe_adb" -s "$probe_serial" shell am force-stop com.skensel.stopad >/dev/null 2>&1 || true
    fi
    "$probe_adb" -s "$probe_serial" shell appops set com.skensel.stopad ACTIVATE_VPN "$probe_previous_mode" >/dev/null 2>&1 || true
    for probe_flavor in game browser manual; do
        case "$probe_flavor" in game) probe_added=$probe_added_game ;; browser) probe_added=$probe_added_browser ;; manual) probe_added=$probe_added_manual ;; esac
        if [ "$probe_added" = 1 ]; then "$probe_adb" -s "$probe_serial" uninstall "com.skensel.stopad.probe.$probe_flavor" >/dev/null 2>&1 || true; fi
    done
    if [ "$probe_added_test" = 1 ]; then "$probe_adb" -s "$probe_serial" uninstall com.skensel.stopad.test >/dev/null 2>&1 || true; fi
    rm -f "$probe_log"
    exit "$probe_exit"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$probe_adb" -s "$probe_serial" install -r app/build/outputs/apk/debug/app-debug.apk
if ! "$probe_adb" -s "$probe_serial" shell pm path com.skensel.stopad.test | rg -q '^package:'; then probe_added_test=1; fi
"$probe_adb" -s "$probe_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [ "$probe_ui" = 0 ]; then
    for probe_flavor in game browser manual; do
        if ! "$probe_adb" -s "$probe_serial" shell pm path "com.skensel.stopad.probe.$probe_flavor" | rg -q '^package:'; then
            case "$probe_flavor" in game) probe_added_game=1 ;; browser) probe_added_browser=1 ;; manual) probe_added_manual=1 ;; esac
        fi
        "$probe_adb" -s "$probe_serial" install -r "tools/probe/build/outputs/apk/$probe_flavor/debug/probe-$probe_flavor-debug.apk"
    done
    "$probe_adb" -s "$probe_serial" shell appops set com.skensel.stopad ACTIVATE_VPN allow
fi
probe_test_started=1
case "$probe_mode" in
    --ui) set -- -e uiTests true ;;
    --ui-large) set -- -e uiTests true -e largeFonts true ;;
    --lists) set -- -e listTests true ;;
    --network) set -- -e networkTests true ;;
    --lifecycle) set -- -e lifecycleTests true ;;
    *) set -- ;;
esac
"$probe_adb" -s "$probe_serial" shell am instrument -w "$@" com.skensel.stopad.test/com.skensel.stopad.VpnIntegration > "$probe_log" 2>&1 || { cat "$probe_log"; exit 1; }
cat "$probe_log"
# am instrument can exit 0 even when an assertion or the test process failed.
rg -q '^PASS:' "$probe_log"
