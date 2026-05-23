# Contributing to MHS Player

First off, thank you for taking the time to contribute! 🎉

This document outlines the guidelines and best practices for contributing to **MHS Player**. Following these guidelines helps ensure a professional, stable, and highly performant media player codebase.

---

## 🛠️ Getting Started

### Prerequisites
To build and develop MHS Player, you need:
* **Android Studio** (Koala | 2024.1.1 or newer recommended)
* **Android SDK** (API Level 36 compile SDK support)
* **JDK 17** (Ensure Java 17 is configured in your system environment and IDE Gradle settings)
* **A physical Android device or emulator** running Android 8.0 (API Level 26) or newer.

### Local Setup
1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/mhs-player.git
   cd mhs-player
   ```
2. **Setup Local Configuration**:
   * Copy `local.properties.example` to `local.properties`:
     ```bash
     cp local.properties.example local.properties
     ```
   * Open `local.properties` and verify/adjust the `sdk.dir` path pointing to your Android SDK folder.
3. **Build the project**:
   * Open the project folder in Android Studio and let Gradle sync complete.
   * Verify by building the app using the command line:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 🌿 Git Branching & Workflow

We follow a clean and structured git workflow:
1. **Always base your work off the `dev` branch** (or `main` if `dev` is not present).
2. **Create a descriptive feature or bugfix branch**:
   * `feature/amazing-new-gesture`
   * `bugfix/subtitles-offset-sync`
   * `docs/update-readme-assets`
3. **Make clean, atomic commits**. Write descriptive commit messages using imperative mood (e.g., `Add gesture vertical brightness control`).
4. **Push your branch and open a Pull Request (PR)** against the target branch. Use our PR template and verify that all checklist criteria are satisfied.

---

## 🎨 Code Style & Quality Guidelines

### Kotlin & Jetpack Compose
* Follow official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide).
* **Compose Best Practices**:
  * Keep Composables side-effect free, robust, and state-hoisted.
  * Use semantic modifier keys and prioritize stable Compose layouts.
  * Ensure all UI screens are highly responsive, fully matching theme guidelines (M3 standards).

### Media3 & ExoPlayer Integration
* Keep video/audio rendering pipelines cleanly decoupled from UI layer using `PlayerController` and `MhsPlaybackService`.
* Offload intensive operations (like parsing huge SRT/ASS subtitle files or invoking cloud AI translation APIs) to dedicated `Dispatchers.IO` coroutine pools. Keep the Main Thread responsive and free from stutter.

### Logging Best Practices
* Do **NOT** use verbose, un-checked logs (`Log.v`, `Log.d`) in hot playback loops.
* Utilize the custom `logVerbose(...)` and `logDebug(...)` inline helper methods which safely check `com.mhs.player.BuildConfig.DEBUG`. This shields release builds from performance degradation and spam.

---

## 🧪 Testing and Quality Control

Before submitting your PR, please execute the local verification suite:
1. **Code Format Check**:
   * Format your files using the standard IDE Kotlin formatting rules.
2. **Build and Compilation**:
   ```bash
   ./gradlew clean assembleDebug
   ```
3. **Lint Check**:
   ```bash
   ./gradlew lintDebug
   ```

Thank you for helping us make MHS Player the ultimate Android multimedia playing utility! 🎬
