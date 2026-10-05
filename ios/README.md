# MHS Player — iOS

Native iOS companion for MHS Player.

## Current foundation
- SwiftUI interface
- AVKit / AVPlayer playback
- Local media import
- Audio/video library
- Play/pause
- iOS background-audio capability foundation
- iPhone/iPad orientation support

## Architecture

The Android application remains unchanged. The iOS implementation lives under `ios/MHSPlayer` and uses Apple's native media stack.

### Planned parity
1. Persistent media library and history
2. Queue / AVQueuePlayer
3. Subtitle loading and styling
4. Gesture controls and seek preview
5. Picture-in-picture
6. Lock-screen / Control Center transport controls
7. External display / AirPlay
8. Advanced codec strategy for formats not handled by AVFoundation
9. Music mode and album-art UI
10. Settings and theme system

## Building

Open `ios/MHSPlayer.xcodeproj` in Xcode on macOS, select an iOS Simulator or connected iPhone, and build.

The repository also includes a GitHub Actions workflow that validates the Xcode project on a macOS runner.
