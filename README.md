# Lirnyk

A lightweight offline music centre for Android car head units: its own player for USB, SD and internal memory, with one big
remote for any installed music app. It starts fast on weak hardware, works with the steering-wheel buttons and needs no
account and no internet.

> *Кожній дорозі — своя пісня.*

**Status:** MVP in development (TASK 001). This is not a release, and nothing has been tested in a car yet. Project status
and responsibilities: [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md).

## Principles

- **Offline.** v1 has no `INTERNET` permission, so it has no telemetry by construction. CI fails the build if the permission appears.
- **Light.**
  - Platform Views, no AppCompat, no Compose.
  - Few dependencies: Media3, RecyclerView, core-ktx, coroutines.
  - Minimum Android 6.0.
  - CI keeps the release APK under 8 MB.
- **Honest.** No demo data. A feature that the head unit does not support is shown as unsupported.
- **Safe while driving.**
  - Large touch targets and one main button.
  - Animations under 150 ms.
  - After 10 s without a touch, secondary screens return to the player.

## Modules

| Module | What |
|---|---|
| `core:model` | Pure Kotlin (JVM tests): tracks, library grouping and search, queue, resume record, head-unit report |
| `core:media` | Library scanning (MediaStore + picked folders), playback service (Media3), resume, boot start, session probe |
| `core:ui` | «Ближнє світло» theme (day/night), strings uk/en/pl, base screen, form builder |
| `feature:player` | Main screen = player |
| `feature:library` | Folders, albums, artists, all tracks, favourites, search, alphabet bar |
| `feature:settings` | Sources (drives, picked folders, permission) and Settings |
| `feature:diagnostics` | «Перевірка магнітоли»: keys, drives, codecs, other players, startup timing |

## Build

```
./gradlew test :app:lintDebug :app:assembleRelease
```

Strings are generated with `python3 scripts/strings.py`. Edit the table in that script, not the XML.
