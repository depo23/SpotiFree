# Spotilol - v1.1.8.12 (test build)

Fork test build based on [upstream v1.1.8](https://github.com/lyssadev/Spotilol/releases/tag/1.1.8), plus the new **Player Mode picker** proposed in [lyssadev/Spotilol#110](https://github.com/lyssadev/Spotilol/pull/110).

## New in 1.1.8.12: Canvas fix, Canvas/Video settings, remembered player state, video podcast probe
- **Settings → Playback → Canvas / Video Podcasts** (both on by default) set the web player's own *Videos and Canvas* settings. On first launch, and whenever you flip one, Spotilol briefly opens Spotify's settings page, applies them, and goes back.
- **Full Screen Player remembers its state:** if you leave it expanded it stays expanded for new songs and after restarting the app; if you minimize it, it stays minimized.
- Why Canvas was missing: Spotify's own Canvas setting was off. With it off, Spotify answers "no Canvas" for every song.
- With Canvas on, the web player also played its own copy of the Canvas in the hidden sidebar. In the Full Screen Player that copy is now suppressed, so only one video downloads and plays.
- Probe log: DRM support check (`[probe] drm …`) and the web player's queries while an episode plays (`[probe] gql …`), to find out why video podcasts don't show video.

## New in 1.1.8.11: Canvas (test)
- **Canvas** in the Full Screen Player: songs with a Spotify Canvas loop it behind the controls, like the Spotify app. The film icon (top right of the expanded player) is always visible: lit = Canvas on, dimmed = this song has no Canvas.
- To test: turn on **Settings → Debug → Collect Debug**, play a few popular songs (most big releases have a Canvas), expand the full-screen player, then **Copy Probe Log** (look for `[probe] canvas`).

## New in 1.1.8.7
- Speed picker: no more flash of Spotify's native speed menu, and only the main speeds from the native list are shown (no 0.1 steps).

## New in 1.1.8.6
- **Speed picker for episodes**: the speed button now opens a list of speeds with the current one highlighted, and waits for you to pick one. Tap outside to cancel.

## New in 1.1.8.5: video embed test
**Settings → Debug → Test Video Embed** opens the playing episode's Spotify video embed over the app and logs whether real video plays.
1. Turn on **Collect Debug**.
2. Play a video podcast episode (e.g. K-Pop ON! Video Podcast).
3. Settings → Debug → **Test Video Embed**, tap play in the embed, wait about 20s, then tap **Close**.
4. Settings → Debug → **Copy Probe Log** and paste the result.

## New in 1.1.8.4 (Full Screen Player)
- **Sharp cover art**: uses the 640px cover instead of the blurry 64px thumbnail (also sent upstream in lyssadev/Spotilol#110).
- **Podcast controls**: when an episode plays, shuffle/repeat become **skip back / forward 15s**, a **speed** button appears (tap for the next speed), and the heart becomes **Add to Your Episodes** (+ / ✓).

## Media probe
Settings now has a visible **Debug** section:
1. Settings → Player Mode → **Full Screen Player**, then turn on **Collect Debug**. The probe starts right away.
2. Play a **video podcast** for about 20s (one confirmed to have video in the Spotify app), plus a music video if your account has them. Also tap the speed button once on an episode. For each one, also expand the full-screen player for a few seconds, then collapse it. For the video ones, also open and close Spotify's now-playing panel.
3. Tap **Copy Probe Log** and paste the result.

Used to design video and podcast support in the full-screen player.

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
