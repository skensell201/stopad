# StopAd

Android project bootstrap. The current development build saves a selection of installed games; ad blocking is not implemented yet.

Project requirements, decisions and work log: [Obsidian: Projects/stopad](obsidian://open?vault=ivan_vault&file=Projects%2Fstopad).

Open in Android Studio (JDK 17+, Android SDK 36) or run `./gradlew :app:assembleDebug :app:lintDebug`.

No root, server, telemetry or browser filtering. Planned engine: per-game local VPN with domain filtering. No promise of universal ad removal.
