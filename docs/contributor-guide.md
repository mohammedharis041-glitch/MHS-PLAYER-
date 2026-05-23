# MHS Player — Contributor & Development Guide

Welcome! We are excited to have you contribute to **MHS Player**. This guide outlines the setup procedures, code style standards, and verification guidelines required to keep the project clean, maintainable, and premium as an open-source showcase.

---

## 1. Local Workspace & SDK Requirements

To build MHS Player from source, verify that your local development environment meets the following specifications:
* **Operating System**: Windows 10/11, macOS, or Linux.
* **Android Studio**: Android Studio Koala (or newer).
* **Java Development Kit (JDK)**: JDK 17 (Preferred: Use the bundled JetBrains Runtime `jbr` inside Android Studio directory).
* **Android SDK**: API Level 34 (Android 14) SDK and Build Tools installed.

---

## 2. Directory & Package Reorganization

The codebase adheres to a strict modular package structure. When adding new files, place them according to their domain:
* **`core/`**: Shared infrastructure (logging, coroutines, constants, and utilities).
* **`database/`**: Room database, entities, and DAOs.
* **`di/`**: Hilt module declarations.
* **`media/`**: Local storage scanner, tree builder, and resolver.
* **`player/controller/`**: Media3 wrappers, playbacks, and cue flows.
* **`player/controls/`**: Player UI controls and gesture overlays.
* **`player/ai/`**: Advanced AI, language detection, and translation engines.
* **`player/subtitles/`**: Custom parsers (`parser/`), providers, and downloaders.
* **`ui/`**: Application screens, navigation routes, and theme attributes.

---

## 3. Code Styling & Guidelines

To ensure consistency, we follow the standard Kotlin style guides:
* **Formatting**: Use Android Studio's built-in Kotlin formatter (`Ctrl + Alt + L` on Windows/Linux or `Cmd + Option + L` on macOS).
* **Thread Offloading**: Never execute disk I/O, heavy parsing, or network calls on the Main/UI thread. Always inject the `DispatcherProvider` and offload workloads to `Dispatchers.IO`:
  ```kotlin
  withContext(dispatcherProvider.io) {
      // Execute disk or network operations
  }
  ```
* **State Management**: Compose layouts must remain stateless. All states must be hoisted and observed as state flows:
  ```kotlin
  val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
  ```
* **Dependency Injection**: Utilize Hilt constructor injection whenever possible. Do not write manual service locators or static factory injectors.

---

## 4. Verification & Validation Steps

Before submitting a Pull Request (PR), you must verify that your changes compile successfully and do not introduce regressions.

### Compilation Check:
Open a terminal in the root directory and execute the Gradle compilation command using Android Studio's bundled JDK:

**On Windows (PowerShell)**:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat compileDebugSources
```

**On macOS / Linux**:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew compileDebugSources
```

### Full Release Verification Build:
To verify resource packaging, Proguard rules, and full dependency verification:

```powershell
.\gradlew.bat assembleDebug
```
Ensure that no deprecated compiler warnings are escalated to errors and that Hilt's compile-time graph compiles successfully without cycle errors.
