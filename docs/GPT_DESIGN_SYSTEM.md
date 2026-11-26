# Lirnyk — design system v0.1 / Low Beam (working brand)
2026-11-26. Original GPT implementation specification; not a rendered screenshot or final approved logo. Target Android XML Views, no image-heavy UI.

## Visual tokens
Night: background #10151B, surface #19222B, elevated #25313B, primary #F4F1E8, secondary #B5C0C9, accent #FFD38A, onAccent #182029, error #FFB4AB, border #394651.
Day: background #F4F2EB, surface #FFFFFF, elevated #E5E9EB, primary #17212A, secondary #475762, accent #875411, onAccent #FFFFFF, error #A62527, border #BBC5CB.
Semantic colors for error/status only, no green/red as sole cue. Validate contrast >=4.5:1 text, >=3:1 controls. System sans-serif (Roboto) default, optional licensed font only after size/perf review. No dynamic blur, gradients optional static, no animated album background.

## Layout
Use available dp, not raw screen pixels. Main landscape wide: left artwork (square capped 30% usable width) and source badge; right metadata, progress, previous/play/next; bottom 3-4 navigation destinations. Compact landscape (<600dp width or <360dp height): hide artwork first, one-line title/artist, keep play/next/previous and source always visible. Portrait: stack artwork/metadata/control/nav. Insets, font scale 1.0/1.3/2.0; no clipped controls.
Touch targets >=64dp on primary buttons (minimum 48dp elsewhere), play 80–112dp if height allows; spacing 8/12/16/24dp. Title 24–36sp adaptive; artist 18–22sp; secondary 16–18sp; never force 160dp play on small height. D-pad/keyboard focus ring, content descriptions, selected state, localized long strings. Artwork optional; default geometric note/road mark, no network fetch.

## Screens
Player: source badge, track/artist, progress with elapsed/duration, three transport controls, shuffle/repeat/favorite only if supported. External source shows disabled/hidden unsupported actions; no fake track. Library: tabs, search, clear unavailable labels and empty state; playlists as first-class. Sources: local and visible external sessions, permission explanation before launch settings; switch only by explicit user action. Sound: local effects only, unsupported labels; settings and diagnostics stationary-use.
No forced 10-second dismissal while typing, editing, diagnostics, accessibility, permissions or dialogs. Optional return to player for passive browsing; no inferred driving status. Do not recommend interaction while driving.

## Brand concept
Lirnyk wordmark in medium-weight sans, initial L as a continuous road line with two short parallel sound strokes (original simple geometric mark). Adaptive launcher background #10151B and amber foreground, monochrome variant; avoid Spotify marks and road-sign replicas. Keep name changeable until trademark clearance.
Alternative Tablo theme reuses component IDs, neutral dark palette, compact text-focused player, no artwork by default.

## Implementation handoff
Use semantic XML color/dimen/style resources, resource qualifiers, vectors; no large bitmap dependencies. Claude integrates in claude/mvp; screenshot checks at 800x480, 1024x600, 1280x720 plus portrait and font scaling. Actual performance and visual approval pending.
