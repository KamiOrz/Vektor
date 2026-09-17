# Vektor

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Vektor is a minimal native M3U/HLS player for iOS and Android. It is built around a simple loop: scan or paste a playlist URL, validate it, load channels, and play locally or over the network.

The repository also includes `vektor-share`, a local-network CLI for turning a video directory into an M3U playlist with an HTTP server and terminal QR code.

## What It Does

- Scan QR codes containing HTTP/HTTPS M3U or HLS playlist URLs.
- Load playlist URLs from the clipboard.
- Parse M3U metadata including channel title, `group-title`, and `tvg-logo`.
- Play channels with native media stacks:
  - iOS: SwiftUI + `AVPlayer`
  - Android: Kotlin + Jetpack Compose + Media3 ExoPlayer
- Show channel covers from `tvg-logo`, with text fallback.
- Keep a lightweight scan history of the last 10 successfully loaded playlist URLs.
- Resume from the last valid playlist on app launch.
- Support local video testing through `vektor-share`.

## Repository Layout

```text
android/                 Android native app
ios/Vektor/              iOS native app
tools/vektor-share/      Local video directory to M3U sharing CLI
docs/prd.md              Product requirements
docs/design.md           Design specification
docs/stitch_*/           Original design references
```

## Requirements

- macOS with Xcode for iOS development.
- Android Studio or Android SDK for Android development.
- Node.js 22+ for `vektor-share`.
- Optional: `ffmpeg` for automatic video cover generation in `vektor-share --covers ffmpeg`.

## Android

Build and test:

```bash
cd android
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Android app uses:

- Kotlin
- Jetpack Compose
- CameraX + ML Kit Barcode Scanning
- Media3 ExoPlayer
- DataStore for local persistence

## iOS

Build and test on a simulator:

```bash
xcodebuild test \
  -project ios/Vektor/Vektor.xcodeproj \
  -scheme Vektor \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -quiet
```

The iOS app uses:

- SwiftUI
- `AVCaptureSession` for QR scanning
- `AVPlayer` / `AVPlayerViewController` for playback
- `UserDefaults` for lightweight local persistence

## Local Video Sharing CLI

Install dependencies:

```bash
cd tools/vektor-share
npm install
```

Run against a local video directory:

```bash
npm start -- /path/to/videos
```

Or link as a system command:

```bash
cd tools/vektor-share
npm link
vektor-share /path/to/videos
```

Useful options:

```bash
vektor-share /path/to/videos \
  --port 8787 \
  --recursive \
  --covers ffmpeg
```

Cover modes:

- `none`: do not include `tvg-logo`.
- `match`: use same-name images such as `Movie.mp4` + `Movie.jpg`.
- `ffmpeg`: use same-name images first, then generate cached JPG covers from the 10-second frame with fallback for short videos.

The phone and computer must be on the same local network. Public tunneling is intentionally out of scope.

## Current Product Boundaries

Vektor is not a content platform. It does not include accounts, cloud sync, recommendations, search, favorites, or playlist hosting. The app only consumes playlist URLs provided by the user.

See [docs/prd.md](docs/prd.md) and [docs/design.md](docs/design.md) for the detailed product and design specs.

## License

Vektor is released under the [MIT License](LICENSE). Copyright (c) 2026 KamiOrz.

Third-party dependencies and externally referenced media retain their respective licenses.
