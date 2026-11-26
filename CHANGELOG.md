# Changelog

## 0.1.2 — P0 install blocker
- Removed the notification listener (Play Protect blocks sideloaded apps that declare BIND_NOTIFICATION_LISTENER_SERVICE) and everything that depended on it; the head-unit check now lists players offering Android's public media connection instead.
- CI: merged-manifest permission allowlist and sensitive-component denylist, APK signature verification, optional stable test signing key.
- docs/EXTERNAL_PLAYERS.md: official ways to control other players.

## 0.1.x — TASK 001 (claude/mvp)
- MVP foundation: Media3 playback service, library from MediaStore and picked folders with an on-disk index, queue with shuffle/repeat, resume after restart (waits for the drive, never jumps to another song), boot start by setting, «Ближнє світло» player, library, sources, settings, head-unit check with a shareable report.
- CI: tests, lint, minified release APK, guards for network permissions, the empty notification listener and the 8 MB size limit; pre-release `test-build`.
