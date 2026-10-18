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

For network integration tests on an isolated running emulator, use `tools/check-emulator.sh emulator-5554`. This installs the app, instrumentation and two test probes, and grants VPN consent only in that emulator. Stop any existing StopAd VPN first. Test details and limitations are documented in Obsidian.
