# Lirnyk — MVP requirements, GPT baseline
Date: 2026-11-26. Reference code: 4d0280da8f88e1649273b823dd4cc1eb1c92fd1a.
Working brand, not trademark-cleared. Owner decides release. TASK 001 foundation is not the complete original MVP.

## P0 / TASK 002
- Local offline playback: MediaStore and SAF removable storage, queue, seek, shuffle, repeat, favorites, searchable folders/albums/artists; persistent named playlists with create/rename/delete/add/remove/reorder; M3U import as P1 unless needed for launch. No files silently deleted.
- External source control: one platform MediaSessionManager adapter, optional notification access, disabled by default. List active sessions excluding our own, explicit user selection; show metadata/actions only as exposed; handle session disappearance, permission revoke, switch sources without two players playing. No Spotify API/DRM bypass; no catalog promise. Local app must work without access or external apps. No notification content access.
- Local audio effects: equalizer, bass boost, balance only if supported on local audio session; presets, bypass, audio session recreation, gain/headroom to prevent clipping; clearly mark unsupported; never claim external audio effects.
- Resume opt-in, no unexpected playback, honor pause and media removal; no automatic change to wrong song. Restore exact track/position when available. Audio focus and noisy output.
- UI: landscape/portrait, 800x480 physical pixels and low dp configurations, large controls, no modal auto-dismiss during permission/setup, diagnostics or active editing; separate day/night, UA/EN/PL selection and system default. Design from gpt/design.
- API 23 minimum conditional on actual dependencies; no INTERNET permission in v1, no analytics, no payments. Companion/local Wi-Fi deferred.
- QA: run emulator smoke test API 23 and modern API if available, test minified artifact (not just debug); real device required for compatibility. 10k tracks, 12 h playback, 1 GB RAM benchmark, cold start measured; PSS target <=60 MB and start <=2 s are targets, APK <=8 MiB. Report method and failures. Never claim physical tests from JVM.
- CI: branch-specific immutable test artifacts, no publishing on documentation-only commits; preserve existing test APKs; SHA256, version/commit, no secret logs, minimal token permissions; maintain update-compatible test signing, release signing separately owner-managed.
- Do not merge main or publish Google Play without owner's approval.

## Delivery
TASK 002 implementation in claude/mvp; reference this document in report. PR with test evidence, feature matrix (implemented/tested/not supported), known gaps, versioned APK. Owner's physical checks do not block implementation.
