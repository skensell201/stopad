# StopAD

Read and maintain project documentation in the Obsidian vault:
`/Users/skensel/Documents/ivan_vault/ivan_vault/Projects/stopad.md`.
Record requirements, architecture decisions, verification and next steps there after work.
Keep README concise with installation/build steps and links to the public documentation. Maintain public user documentation in docs/ in English, including release notes. Keep internal work logs in Obsidian.

V1 scope: Android games, no browser filtering. Never claim all ads are blocked without evidence. Never show protection as enabled before the real VPN engine establishes successfully. Never establish a VPN with an empty allowed-app list (that would cover all apps).

Design: purple + turquoise; never blue + yellow/gold/amber.

Development: use TDD for behavior changes. Write and run the failing test first, implement the minimal fix, then refactor while keeping the suite green. Record red/green evidence and verification limits in Obsidian.
