# Spotilol - v1.2.5.2 (test build)

Based on upstream v1.2.4, with the Full Screen Player features and automatic updates.

## Fixes
- Update test: this build exists to check that the updater finds and installs a newer version.
- Changelog and update checks now find pre-release test builds ("Could not load release notes" is fixed).

## New
- **Automatic updates**: Spotilol checks for a new version when it opens (at most once an hour). If one is found, it downloads in the background and shows a popup. Tap **Install** and confirm in Android's installer; no need to visit GitHub.
- **Check for updates**: Settings → Spotilol Version → *Check for updates* checks right away.
- Tapping **Later** hides the popup for a day.
- The first install asks you to allow installs from Spotilol (Android settings).


Fork build based on [upstream v1.1.8](https://github.com/lyssadev/Spotilol/releases/tag/1.1.8), with the features proposed in [lyssadev/Spotilol#110](https://github.com/lyssadev/Spotilol/pull/110).

## New
- **Full Screen Player**: Settings → Playback → Player Mode → Full Screen Player. A big Now Playing screen like the Spotify app: tap the mini player to open it, swipe down (or press Back) to close it. It stays expanded or minimized the way you leave it.
- **Canvas**: songs with a Canvas loop their video behind the player. Switch it with Settings → Playback → Canvas, or the film button in the player.
- **Video podcasts**: video episodes play their video in the expanded player. Turn the phone sideways for widescreen videos to watch full screen, with controls across the bottom; tap the video to hide or show them.
- **Podcast controls**: skip 15 seconds back or forward, and playback speed.
- **Canvas / Video Podcasts settings** keep Spotify's own *Videos and Canvas* settings in sync (both on by default).
- **Slimmer top bar** with a smaller toggle, leaving more room for Spotify.
- A short **What's new** card on first launch explains all of the above.
