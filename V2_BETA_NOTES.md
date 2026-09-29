# MHS Player v2-beta

Beta branch for the next major update. Base: v1.1.5 (main @ e3b9e30)

## New features in this beta

### 1. Subtitle fixes
- Fix: tapping "Off" no longer deletes the loaded subtitle track. It now disables rendering but keeps the track cached with one-tap re-enable.
- Shows persistent chip: "Subtitles: Off — [filename]"

### 2. Subtitle font selection (covers issue #2)
- New Settings → Subtitles → Appearance screen with live preview
- 4 bundled fonts: Inter, Roboto Condensed, Poppins, Noto Sans Malayalam
- Import custom .ttf/.otf via SAF, stored in app-private fonts/
- Options: size, bold, colour, edge style, background opacity

### 3. Per-video settings memory
- New Room entity `VideoSettings(videoId, audioTrackIndex, subtitleTrackId, playbackSpeed, subtitleEnabled)`
- Auto-save on pause / track change, restore on open
- "Reset for this video" in overflow menu

### 4. Sleep timer
- 15/30/60/90 min + custom
- Fade-out volume in last 30s
- End action: pause (configurable to stop-and-exit)
- Notification with cancel + remaining time chip in player

### 5. Polish
- Miniplayer stability follow-up
- Continue Watching improvements
- Dynamic colour theming prep

## Testing
- [ ] Subtitle Off → track persists
- [ ] Font picker applies live, survives restart
- [ ] Imported .ttf works
- [ ] Per-video settings restore correctly
- [ ] Sleep timer pauses at 0, works in background

## Branch
`v2-beta` — merge to main only after beta testing.
