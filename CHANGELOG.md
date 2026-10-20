# Changelog

## 0.6.1

First public GitHub release.

- Added a dark purple interface with lavender cards and turquoise accents.
- Removed the large Home banner so protection controls appear first.
- Standardized the displayed product name as StopAD.
- Kept compatibility with Android 8.0 and the existing official release signing key.

Verification: core DNS/policy/blocklist/TCP checks, release signature and non-debuggable checks, Android navigation smoke test, and release lint. The design was also checked at 160% system text. No claim of support for all games is made.

## 0.6.0 (previous local release)

- Added local DNS-over-TCP support and upstream TCP fallback.
- Added verified release signing and release-emulator checks.
- Tested controlled DNS flows, network changes, Private DNS handling, and a short screen-off session.
