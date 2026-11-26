# GPT static review — TASK 001
2026-11-26; exact commit 4d0280da8f88e1649273b823dd4cc1eb1c92fd1a. Read commit diff, TASK001_REPORT.md, PROJECT_STATUS.md and full PlaybackService.kt, PlayerConnection.kt, SystemHooks.kt, BaseActivity.kt. Partial static review, no runtime tests, no independent APK hash or CI verification. Do not merge as complete MVP.

## Findings
P0: PlaybackService.restore filters saved IDs and substitutes ids.first() when original currentId is absent, contrary to never-switch-track contract. onPlaybackResumption also uses coerceAtLeast(0) and does not wait for library.loaded. Require explicit missing track state, wait for exact ID, no auto-start other song. Test missing/late media, early controller.
P0: waitForMedia auto-plays when drive appears. Pause does not cancel pending wait (onPlayWhenReadyChanged cancels only if true; a pause while already paused may emit nothing). Explicit pause/stop/source switch must cancel resume intent; test.
P1: onPlayerError counts errors but onMediaItemTransition resets consecutiveErrors on non-repeat transitions, potentially defeating bounded skip with repeat-all. Bound attempts across failing queue, stop and expose error. Test all corrupt files, repeat all, removed USB.
P1: PlayerConnection asynchronous connect/release: listener sets pending=null and controller=c without checking generation/current future; stale completion after release/reconnect risks controller/ticker leak or queued command misrouting. Use generation, cancel/release stale future, clear queue on failure, bounded actions, tests.
P1: BootReceiver uses startService under modern background limits; runCatching swallows failure. Verify API 26-34 startup/FGS promotion within deadline, Android 35+ no boot playback; document failure/fallback. No physical evidence.
P1: PlaybackService exported and onGetSession returns session for all callers; review controller authorization via onConnect/command grants; allow intended system/steering controls, prevent untrusted apps from replacing queue or arbitrary data access. Test foreign package, media buttons, notification.
P1: android.yml uses workflow-level contents:write and a shared mutable test-build tag deleted/recreated on every push including main; concurrency is per-ref. Simultaneous branches may clobber release, docs-only main lacks Gradle. Split CI/publish privileges, branch-specific immutable assets, explicit publishing rule, path filters. Debug key stability across runners unverified.
P1: BaseActivity 10s idle timeout on all secondary screens unless overridden; verify diagnostics and setup, accessibility and editing exempt. Do not dismiss a permission flow.
P2: Size is reported 1.06 MB, not independently verified; minified startup test essential. JVM tests cover model rather than full service lifecycle.

## Gate
Static review confirms foundation, not complete MVP: external controller, named playlists, effects, design assets and physical tests remain. Verify APK on emulator/phone and head unit; no main merge until owner decision. Add regression tests for findings, preserve offline/no-notification-reading guarantees.
