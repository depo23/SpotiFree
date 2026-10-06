# Spotilol - v1.1.8.1 (test build)

Fork test build based on [upstream v1.1.8](https://github.com/lyssadev/Spotilol/releases/tag/1.1.8), plus the new **Player Mode picker** proposed in [lyssadev/Spotilol#110](https://github.com/lyssadev/Spotilol/pull/110).

## What's new
**Settings → Player Mode** now has three options:
- **Spotilol Player** (default): the floating card with all controls, unchanged
- **Spotify Original**: the web player's own bottom bar, unchanged
- **Full Screen Player** (new): Spotify-app style mini bar that expands to a full-screen now-playing view, with a large cover on a cover-tinted gradient, seek bar, transport controls, and timer / PiP / lyrics / queue / download / volume. Expand by tapping or swiping up the mini bar; collapse with the chevron, a swipe down, or the back button. Landscape uses a two-column layout.

## Install notes
- ⚠️ Test build, not device-tested yet.
- Installs **next to** the original Spotilol as a separate app, **Spotilol Test** (package `com.project.lol.test`). No need to uninstall anything. It has its own login, settings and downloads.
- Signed with this fork's own key.
- Signing certificate SHA-256: `38:69:B6:B9:B2:48:70:E5:3B:04:E5:7A:CA:CD:1B:3D:B5:6F:4D:18:96:D3:99:CB:F2:A2:46:00:20:F7:FB:1E`
- Built without Firebase config, so analytics and crash reporting are off.
