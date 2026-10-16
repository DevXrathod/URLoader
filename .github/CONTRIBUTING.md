# Contributing to URLoader

Thank you for your interest in contributing to URLoader! We welcome bug reports, feature suggestions, UI polish, and pull requests.

---

## 🧭 Code of Conduct

Please maintain a respectful, constructive, and collaborative tone when opening issues or participating in pull request discussions.

---

## 🛠️ Development Setup

1. **Prerequisites**:
   - Android Studio Ladybug (2024.2+) or newer.
   - JDK 17 (Eclipse Temurin or Android Studio bundled JDK).
   - Android SDK 36 (Android 16 platform installed via SDK Manager).

2. **Clone & Open**:
   ```bash
   git clone https://github.com/<your-username>/URLoader.git
   cd URLoader
   ```
   Open the project folder directly in Android Studio. Allow Gradle to sync.

3. **Verify Locally**:
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

---

## 📐 Coding Conventions

We adhere strictly to the **Kotlin Coding Conventions** and modern Android architecture guidelines:

- **Architecture**: Clean MVVM with Unidirectional Data Flow (UDF). Keep Activities and Fragments thin; expose state via `StateFlow` and handle background work in dedicated services/managers.
- **Resource Discipline**: **No hardcoded literals.** Every string routes through `res/values/strings.xml`, spacing through `dimens.xml`, colors through `colors.xml`, and constants through `AppConstants.kt` or `PrefKeys.kt`.
- **Target SDK 36**: Use current Android 16 APIs (edge-to-edge, `WindowInsetsCompat`, `FileProvider`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC`). Do not introduce deprecated APIs.
- **Crash Prevention**: Guard against nullability, empty collections, I/O failures, and permission states with explicit conditions or `try/catch`. Never use force-unwraps (`!!`) or unchecked casts.
- **Imports**: Add explicit top-level imports; do not write inline fully-qualified class names.
- **Formatting**: Respect `.editorconfig` (4-space indentation, UTF-8, LF line endings).

---

## 🧪 Testing Guidelines

Before opening a pull request:
1. Run `./gradlew testDebugUnitTest` to confirm all unit tests pass.
2. Add new unit tests in `app/src/test/` for any new utility, parser, or business logic.
3. Verify that `./gradlew assembleDebug` and `./gradlew assembleRelease` build with zero errors.

---

## 📥 Submitting a Pull Request

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Commit your changes with clear, descriptive commit messages.
3. Push to your fork and submit a Pull Request against the `main` branch.
4. Fill out the provided Pull Request template completely.
