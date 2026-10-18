# StopAd

Project documentation and work log: [Obsidian: Projects/stopad](obsidian://open?vault=ivan_vault&file=Projects%2Fstopad).

Open in Android Studio with JDK 17+ and Android SDK 36, or run:

```sh
./gradlew :app:assembleDebug :app:lintDebug
tools/check-core.sh
```

On macOS, Android Studio's bundled JDK can be selected with:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

For network integration tests on an isolated running emulator, use `tools/check-emulator.sh emulator-5554`. This installs the app, instrumentation and three test probes, and grants VPN consent only in that emulator. Stop any existing StopAd VPN first. Add `--network` to test Wi-Fi/mobile transitions and Private DNS; this temporarily changes the isolated emulator’s Wi-Fi and Private DNS settings and restores them afterwards. Use `--lists` for updater tests, including one real download from the fixed HTTPS source. Test details and limitations are documented in Obsidian.

Use `--ui` for navigation, `--ui-large` for 160% system text, and `--lifecycle` for short screen-off DNS checks on the Android 17 emulator.

With the local signing credentials described in Obsidian, run `tools/check-release.sh` to build and verify `app/build/outputs/apk/release/app-release.apk`. Use `tools/check-release-emulator.sh emulator-5556` only on a separate clean emulator to test the actual signed release without replacing an existing debug installation.
