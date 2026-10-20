# Development

## Prerequisites

- JDK 17 or newer.
- Android SDK Platform 36 and Android build tools.
- Android Studio or the included Gradle wrapper.

On macOS, Android Studio's bundled JDK can be selected with:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
```

Set your SDK path in an ignored `local.properties` file (`sdk.dir=...`) or use `ANDROID_HOME`.

## Build and test

```sh
./gradlew :app:assembleDebug :app:lintDebug
tools/check-core.sh
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Behavior changes follow TDD: reproduce with a failing test, implement the fix, then keep the suite green. Documentation-only changes do not need application logic tests.

The core checks cover domain policy, DNS parsing and bounds, UDP/TCP upstream handling, blocklist validation and rollback, and the local TCP DNS transport.

Run Android checks only on an isolated emulator, with any existing StopAD VPN stopped:

```sh
tools/check-emulator.sh emulator-5554 --ui
tools/check-emulator.sh emulator-5554 --locales
tools/check-emulator.sh emulator-5554 --ui-large
tools/check-emulator.sh emulator-5554 --network
tools/check-emulator.sh emulator-5554 --lists
tools/check-emulator.sh emulator-5554 --lifecycle
```

`--locales` checks English/Russian resources, formatted counters, and English fallback for unsupported languages. UI checks cover navigation and empty-selection handling; `--ui-large` uses 160% text and restores the scale. Other modes install test probes and temporarily grant VPN consent in the emulator. Network checks temporarily change Wi-Fi and Private DNS settings; list tests include a real HTTPS download. The runner restores settings and removes test packages it added. Android probes require API 29+; the app itself supports API 26+.

These checks do not prove compatibility with every game, manufacturer battery policy, or prolonged Doze state.

## Release signing

Official signing credentials are private and are not distributed in this repository. The public certificate is `app/release-certificate.pem`. External contributors can build debug APKs without those credentials.

Maintainers can select a private signing directory with `STOPAD_SIGNING_DIR`. It contains `release.p12` (PKCS12, alias `stopad-release`) and `storepass` (the store/key password). Keep both outside the repository and back them up securely. Without credentials, a release build is unsigned and the verification script rejects it.

```sh
tools/check-release.sh
./gradlew :app:lintRelease
```

The script verifies the APK signature against the pinned public certificate and rejects debuggable release APKs. Signed output: `app/build/outputs/apk/release/app-release.apk`.

`tools/check-release-emulator.sh emulator-5556` checks the actual signed release on a separate clean emulator; it refuses to replace an existing StopAD installation and requires the private signing credentials to sign instrumentation.

## Repository layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/` | Native Android UI, per-game VPN, DNS and list logic |
| `app/src/main/res/` | UI strings, theme and icons; English default resources and Russian translations |
| `app/src/main/assets/` | Bundled blocklist, integrity metadata and third-party notices |
| `app/src/androidTest/` | Android instrumentation checks |
| `tools/` | Core checks, release scripts and isolated test probes |
| `docs/` | Public user and developer documentation |

## Third-party material

The bundled Block List Project Ads list includes its Unlicense and attribution in `app/src/main/assets/blocklist-LICENSE.txt` and `blocklist-NOTICE.txt`. UI illustrations from design references are not bundled. Public source availability alone does not grant an additional license to the project's own code.
