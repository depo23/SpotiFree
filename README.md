<div align="center">
  <img src="art/bgwelcome.png" alt="SpotiFree" style="width: 100%; max-width: 900px; margin-bottom: 20px; box-shadow: 0 8px 32px rgba(0,0,0,0.5);">
</div>

<h1 align="center">SpotiFree</h1>

<p align="center">
  <a href="https://github.com/depo23/SpotiFree/stargazers">
    <img src="https://img.shields.io/github/stars/depo23/SpotiFree?style=for-the-badge&logo=starship&labelColor=0d0d0d&color=1DB954" alt="stars"/>
  </a>
  &nbsp;
  <a href="https://github.com/depo23/SpotiFree/releases">
    <img src="https://img.shields.io/github/downloads/depo23/SpotiFree/total?style=for-the-badge&logo=download&labelColor=0d0d0d&color=1DB954" alt="downloads"/>
  </a>
  &nbsp;
  <a href="https://github.com/depo23/SpotiFree/releases/latest">
    <img src="https://img.shields.io/github/v/release/depo23/SpotiFree?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="version"/>
  </a>
  &nbsp;
  <a href="https://github.com/depo23/SpotiFree/forks">
    <img src="https://img.shields.io/github/forks/depo23/SpotiFree?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="forks"/>
  </a>
  &nbsp;
  <a href="https://github.com/depo23/SpotiFree/commits/main">
    <img src="https://img.shields.io/github/last-commit/depo23/SpotiFree?style=for-the-badge&logo=git&labelColor=0d0d0d&color=1DB954" alt="last commit"/>
  </a>
</p>

<p align="center">
  an Android app that wraps Spotify's web player with built-in adblocking. no root, no shady mods, just your Spotify account on a slick WebView.
</p>

<p align="center">
  based on <strong>lyssadev</strong>'s <strong>Spotilol</strong> (a Kotlin port of deviato's <strong>Spotifuck</strong>), with a Spotify-style full-screen player, Canvas and video podcasts.
</p>

---



## Download

<div align="center">
  <a href="https://github.com/depo23/SpotiFree/releases/latest">
    <img src="https://img.shields.io/github/v/release/depo23/SpotiFree?style=for-the-badge&logo=github&labelColor=0d0d0d&color=1DB954" alt="Download APK"/>
  </a>
</div>

download the `.apk` and install it on your device. you may need to toggle **"Install from unknown sources"** in your Settings.

---

## Features

- blocks audio ads, trackers and telemetry
- media notification with play, pause, skip, seek, like, shuffle and repeat
- lock screen, Bluetooth and headset controls
- home screen widgets for the player and album art
- Android Auto: browse and search your library from the car
- offline downloads via the InnerTube API, with tags and cover art
- multiple account profiles and a sleep timer
- lyrics with five styles and a picture in picture view
- AMOLED dark mode, Material You and accent color themes
- custom CSS, canvas and video podcasts
- mobile layout tweaks and playlist sorting
- wake lock and power save mode
- automatic and manual update checker

---

## Requirements

- Android 9.0+ (API 28)
- a Spotify account
- Google Chrome / WebView (comes with your phone)

---

## Quick Start

install the APK, open it, done. no setup, no certificates, no fuss. it just works out of the box.

---

## Build It Yourself

```bash
git clone https://github.com/depo23/SpotiFree
cd SpotiFree
./gradlew assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Google Services

spotifree doesn't use a Firebase project and sends no analytics or performance data (both are switched off in the manifest). the Firebase libraries are still part of the build, though, so Gradle expects an `app/google-services.json` to exist. you don't need a real one: save this placeholder as `app/google-services.json` (it's git-ignored):

```json
{
  "project_info": {"project_number": "000000000000", "project_id": "ci-placeholder", "storage_bucket": "ci-placeholder.appspot.com"},
  "client": [{
    "client_info": {"mobilesdk_app_id": "1:000000000000:android:0000000000000000", "android_client_info": {"package_name": "com.spotifree.app"}},
    "api_key": [{"current_key": "AIzaSyA-ci-placeholder-0000000000000000"}]
  }],
  "configuration_version": "1"
}
```

release builds on GitHub use exactly this placeholder and also turn crash reporting off. to do the same in your build, add `<meta-data android:name="firebase_crashlytics_collection_enabled" android:value="false" />` inside `<application>` in `AndroidManifest.xml`.

---

## Contributing

contributions are welcome. open issues, throw PRs, suggest stuff. free for all.

---

## Credits

**deviato** reverse-engineered the original Spotifuck. **lyssadev** ported the core logic from smali to Kotlin as Spotilol. **depo23** maintains SpotiFree, built on Spotilol.

all rights reserved. lyssadev and deviato.