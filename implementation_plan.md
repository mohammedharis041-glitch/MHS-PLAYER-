# Implementation Plan — Open-Source Project Preparation & Cleanup

The goal of this task is to prepare the **MHS Player** project for a clean, stable, and professional public open-source upload on GitHub/GitLab, while preserving all existing premium features, gesture-controlled UX, background MediaSession playback, local FFmpeg decoding support, and ML Kit translation features perfectly.

---

## User Review Required

> [!IMPORTANT]
> - **Local Properties & Secrets**: The `local.properties` file containing the Android SDK path (`sdk.dir`) will be safely removed from the repository preparation workspace (which is ignored by Git anyway), and a template `local.properties.example` will be provided for new developers to easily set up their environment.
> - **Gradle Generated & Sub-Project Cleanup**: Root-level directories `.gradle/`, `.idea/`, `build/`, `app/build/`, `.kotlin/`, `.cleanup_backup/` and the inactive React Native FFmpeg source tree `ffmpeg-kit-android-16KB-main/` will be completely deleted. This will reduce project upload size by 99% without affecting compilation.
> - **Unused Layouts Removal**: The layout `activity_vlc_player.xml` is confirmed to be completely unused (not referenced in code or the AndroidManifest) and will be deleted.

---

## Proposed Changes

### Phase 1: Remove Private/Generated Files

We will run cleanup commands to physically delete the following directories from the filesystem to ensure only clean code and wrapper scripts are included:

- [DELETE] `.gradle/`
- [DELETE] `.idea/`
- [DELETE] `.kotlin/`
- [DELETE] `build/`
- [DELETE] `app/build/`
- [DELETE] `.cleanup_backup/`
- [DELETE] `ffmpeg-kit-android-16KB-main/`
- [DELETE] `local.properties` (kept as `.example` template)

---

### Phase 2: Security & Privacy Cleanup

Verify that there are no hardcoded secrets or personal tokens in `SettingsRepository.kt` or provider classes (`OpenSubtitlesProvider.kt`, `MsoneProvider.kt`, `SubtitleCatProvider.kt`). All subtitle API keys are already dynamically read from settings preferences.

- Create [NEW] [local.properties.example](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/local.properties.example) template.

---

### Phase 3: Open Source Sanitization

Delete the legacy, unused VLC-related layout file:
- [DELETE] [activity_vlc_player.xml](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/res/layout/activity_vlc_player.xml)

---

### Phase 4: Project Organization

We will create the requested sub-packages under `com.mhs.player.player` and migrate files to achieve the target folder structure.

#### Package target structure:
- `com.mhs.player.player.gestures/`
- `com.mhs.player.player.translation/`
- `com.mhs.player.player.utils/`

#### Moves & Reorganizations:
1. **Gestures Package** (`com.mhs.player.player.gestures`):
   - Move `GestureController.kt` from `com.mhs.player.player.controller` to `com.mhs.player.player.gestures`.
   - Move `GestureOverlay.kt` from `com.mhs.player.player.ui` to `com.mhs.player.player.gestures`.
2. **Translation Package** (`com.mhs.player.player.translation`):
   - Move `SubtitleTranslator.kt` from `com.mhs.player.player.subtitles` to `com.mhs.player.player.translation`.
3. **Utils Package** (`com.mhs.player.player.utils`):
   - Move `SubtitleUtils.kt` from `com.mhs.player.player.subtitles` to `com.mhs.player.player.utils`.

#### Imports Update Check:
Update all package headers and references across files:
- [MODIFY] [GestureController.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/controller/GestureController.kt)
- [MODIFY] [GestureOverlay.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/ui/GestureOverlay.kt)
- [MODIFY] [SubtitleTranslator.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/subtitles/SubtitleTranslator.kt)
- [MODIFY] [SubtitleUtils.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/subtitles/SubtitleUtils.kt)
- [MODIFY] [PlayerViewModel.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/ui/screens/PlayerViewModel.kt) (update GestureController import)
- [MODIFY] [MainActivity.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/MainActivity.kt) (update GestureController import)
- [MODIFY] [AppModule.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/di/AppModule.kt) (update GestureController & SubtitleTranslator imports)
- [MODIFY] [PlayerScreen.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/ui/screens/PlayerScreen.kt) (update GestureOverlay import)
- [MODIFY] [ExternalPlayerScreen.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/ui/screens/ExternalPlayerScreen.kt) (update GestureOverlay import)
- [MODIFY] [PlayerController.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/controller/PlayerController.kt) (update SubtitleTranslator import)
- [MODIFY] [MsoneProvider.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/subtitles/providers/MsoneProvider.kt) (update SubtitleUtils import)
- [MODIFY] [SubtitleCatProvider.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/subtitles/providers/SubtitleCatProvider.kt) (update SubtitleUtils import)
- [MODIFY] [OpenSubtitlesProvider.kt](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/app/src/main/java/com/mhs/player/player/subtitles/providers/OpenSubtitlesProvider.kt) (update SubtitleUtils import)

---

### Phase 5: Gradle & Build Cleanup

Verify compile settings and target compatibility. Standardize configuration files for local compilation. Since dependencies and Gradle wrapper files are fully operational and clean, we will only verify that builds are successful.

---

### Phase 6: Documentation

Update/write high-quality, professional markdown documentation:
- [MODIFY] [README.md](file:///c:/Users/mhs/Desktop/mhsplayer%2017,05,26/MHSPlayer%20v1/MHSPlayer%20v1/README.md) (Premium styled with clear build steps, details about local FFmpeg decoder integration, ML Kit subtitles, folder structures, and developer credits).

---

### Phase 7: Performance & Stability

Ensure the clean, reorganized codebase compiles flawlessly and retains exact working behaviors (no dead coroutines, no lost states, extremely smooth player pipeline).

---

## Verification Plan

### Automated Build Verification
1. Run `./gradlew clean` to ensure all temporary logs and generated caches are deleted.
2. Run `./gradlew assembleDebug` to verify that package movements, imports, and dependencies compile flawlessly.

### Manual Verification
1. We will verify that the app builds and that all core player operations continue to run beautifully, including:
   - Video and audio playback.
   - Picture-in-picture mode.
   - Gesture overlays (volume, brightness, seeking).
   - On-device translation via ML Kit.
   - Subtitle searches (Msone, SubtitleCat, OpenSubtitles).
