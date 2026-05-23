# MHS Player — Playback Pipeline & Rendering Engine

This document provides a technical breakdown of MHS Player's core media playback architecture. It details Media3 ExoPlayer integration, background service lifecycles, gesture controls, and hardware-accelerated video decoding.

---

## 1. Playback Architecture & Media3 ExoPlayer

The core playback loop is managed by `PlayerController.kt` (under `com.mhs.player.player.controller`), which acts as a robust wrapper around Jetpack Media3 ExoPlayer.

```text
+-----------------------------------------------------------------------+
|                             PlayerScreen                              |
+-----------------------------------------------------------------------+
                                  | Collect StateFlows
                                  v
+-----------------------------------------------------------------------+
|                           PlayerController                            |
+-----------------------------------------------------------------------+
   | Connects via MediaController           | Controls Custom Modules
   v                                        +-------------------> Equalizer
+-----------------------------+             +-------------------> AI Translator
|     MhsPlaybackService      |             +-------------------> Gesture Overlay
|  (MediaSession / ExoPlayer) |
+-----------------------------+
   | Initiates
   v
+----------------------------------------+
|        DefaultRenderersFactory         |
|  - MediaCodecVideoRenderer (Hardware)  |
|  - FFmpegAudioRenderer (Software/Ext)  |
+----------------------------------------+
```

### Key Configurations:
* **`DefaultLoadControl`**: Customized to optimize buffering strategy:
  * Min Buffer: 15,000ms
  * Max Buffer: 50,000ms
  * Buffer for Playback: 2,500ms
  * Buffer for Replay: 5,000ms
* **Dynamic Track Selector**: Leverages `DefaultTrackSelector` to support runtime audio and subtitle track switching via interactive Compose overlays.

---

## 2. Background Media3 Service Lifecycle

To ensure uninterrupted audio playback and handle Android system OS process restrictions, MHS Player implements a full `MediaSessionService`: **`MhsPlaybackService`**.

* **MediaSession Coupling**: Automatically binds the active `ExoPlayer` instance to a `MediaSession`. This enables integration with:
  * System-level Lock Screen Controls.
  * Wearable device controls (Android Wear OS).
  * Bluetooth Headset gestures (play, pause, next).
  * Android Auto.
* **Persistent Notification & Foreground transition**: Integrates a `PlayerNotificationManager` that updates a custom media notification with movie metadata, artwork, and action controls. The service transitions to the foreground state while active, protecting the app from low-memory system terminations.

---

## 3. High-Fidelity Custom Rendering & FFmpeg

MHS Player provides broad compatibility with modern and legacy media containers by building a custom-tuned Media3 Renderer pipeline.

* **Hybrid Hardware/Software Decoding**:
  * **Hardware Acceleration (`MediaCodec`)**: Handles complex high-bitrate video decoding (AVC, HEVC, VP9, AV1) using the chip's physical decoders to achieve high frame rates and battery efficiency.
  * **FFmpeg Software Extension Decoders**: Seamlessly compiled to handle advanced multi-channel audio codecs (such as AC3, EAC3, DTS, DTS-HD, and TrueHD) that standard device manufacturers omit due to licensing costs. This prevents "no audio track" errors and guarantees consistent audio quality.
* **Smart Enhancement GlEffect (`SmartEnhanceEngine`)**:
  * Employs customized OpenGL ES Shaders applied directly to the video rendering surface.
  * Enhances video contrast, saturates colors dynamically, and applies high-speed edge sharpening in real time without increasing CPU consumption.

---

## 4. Touch Gestures Rendering & Math Layer

Interaction with the video surface is powered by a high-precision gesture system located in `com.mhs.player.player.gestures`.

* **`GestureController.kt`**: Bridges raw screen touch coordinates to media actions.
* **`GestureOverlay.kt`**: A full-screen declarative Compose canvas layer that captures inputs, displays visual status indicators, and draws premium animations.

### Custom Swipe Gesture Calculations:
1. **Vertical Right Side Swipe (Volume Control)**:
   $$\Delta \text{Volume} = - \left( \frac{\Delta Y}{\text{Screen Height}} \right) \times \text{Max Volume}$$
   Includes logarithmic audio adjustments to feel intuitive to the human ear.
2. **Vertical Left Side Swipe (Brightness Control)**:
   $$\Delta \text{Brightness} = - \left( \frac{\Delta Y}{\text{Screen Height}} \right)$$
   Updates the window layout attributes dynamically without modifying system settings.
3. **Horizontal Swipe (Seek Navigation)**:
   $$\Delta \text{Time} = \left( \frac{\Delta X}{\text{Screen Width}} \right) \times \text{Seek Scale (e.g. 100 seconds)}$$
   Triggers interactive, frame-accurate preview seek overlays (`SeekPreviewPopup`) containing high-speed video thumbnail snapshots generated by `PreviewFrameManager`.
