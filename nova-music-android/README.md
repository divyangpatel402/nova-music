# NOVA MUSIC Android

Phone version scaffold for NOVA MUSIC. The Windows desktop app remains in the main Rust project; this folder is a separate Android Studio project.

## What is included

- Black and white premium NOVA MUSIC UI.
- Native local music player with folder selection, library scan, play/pause, next, and previous.
- Local library search that plays inside NOVA MUSIC instead of opening YouTube.
- Home, Local Music, Search, Downloads, Settings, and About pages.
- Safe downloads flow for direct legal file URLs. YouTube/video playback and stream ripping are intentionally not implemented.
- About page with owner and co-owner details:
  - Divyang, owner, `divyang0991__`
  - Rahul, co-owner, `rahul_00012`
  - Discord: `https://discord.gg/jHfHdRGHFj`

## Build APK

Open this folder in Android Studio, let it install Android Gradle Plugin 8.7.3, then run:

```powershell
gradle :app:assembleDebug
```

The debug APK will be created at:

```text
nova-music-android\app\build\outputs\apk\debug\app-debug.apk
```

This machine currently does not have Android SDK or Gradle on PATH, so Codex cannot compile the APK locally yet.
