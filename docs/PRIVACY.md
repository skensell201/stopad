# Privacy and limitations

StopAD uses a local Android VPN interface to intercept DNS traffic from selected games. It is not a remote VPN tunnel and does not hide your IP address. Browsers are excluded. The app does not install a root certificate or decrypt HTTPS content.

Allowed DNS queries are sent to Cloudflare (`1.1.1.1`) over unencrypted UDP or TCP. Cloudflare and the network path can observe these DNS requests. Game traffic otherwise continues through its normal network connection. This version requires Android Private DNS to be disabled for filtering.

The app does not persist a history of queried domains or include analytics/advertising SDKs. Selections, manually added apps, domain exceptions, and blocklist data are stored locally. Session request counters are not a history of browsing or game activity.

The bundled advertising-domain list is based on [The Block List Project](https://github.com/blocklistproject/Lists). Manual updates fetch the fixed upstream URL over HTTPS. GitHub and the upstream infrastructure receive the download request. Updates are initiated by the user; a running session uses its existing snapshot until restarted.

Ads can remain, and false positives can break game features. Rewarded videos may become unavailable. Apps with their own encrypted DNS and ads served from game-content domains may bypass filtering. Only one Android VPN can be active at a time.

Downloading releases and submitting issues use GitHub's services and are subject to [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). Public issues are visible to others. StopAD's repository being public does not make local user settings public.

This document describes the current implementation; it is not a claim of universal blocking or anonymity. Report discrepancies through [GitHub Issues](https://github.com/skensell201/stopad/issues).
