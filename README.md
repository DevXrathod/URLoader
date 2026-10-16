# URLoader ⚡

<p align="center">
  <strong>Drop a link. Get the original file.</strong><br>
  A modern, high-performance Android media downloader engineered for Android 16 (SDK 36).
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2016%20(SDK%2036)-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 16" />
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Engine-yt--dlp%20%2B%20FFmpeg-FF0000?style=flat-square&logo=youtube&logoColor=white" alt="Engine" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="License" />
</p>

---

## 🌟 Overview

**URLoader** is a minimal, contrasty Android utility that eliminates the friction of downloading online media. Built with clean architecture and Material 3 Expressive aesthetics, it turns video and audio downloads from 1,800+ websites into a single tap.

Whether shared directly from **YouTube, Instagram, TikTok, X (Twitter), Reddit**, or pasted into the composer, URLoader delivers original high-bitrate media directly to your device's `Downloads/URLoader` directory.

---

## ✨ Features

- **🚀 System Share Sheet Direct Target**: Share a link from any app directly into URLoader’s floating dialog. Download Video or Audio instantly without leaving your current app.
- **🌌 Contrast-Rich Luminous Aurora UI**: Custom hardware-accelerated animated gradient mesh background (`BlurMeshBackgroundView`) with RenderEffect Gaussian blur and deep OLED pitch-black contrast.
- **⚡ 1,800+ Supported Sites**: Powered by native `yt-dlp` and `FFmpeg` engines via JNI bindings.
- **🔄 Auto-Healing Engine**: Proactively checks for engine updates in the background. If a platform modifies its streaming cipher, URLoader automatically fetches the nightly core and retries the download seamlessly.
- **🔔 Android 16 Live Activity Notifications**: Real-time download progress with throttle management, dynamic status bar chip integration (`android.requestPromotedOngoing`), and one-tap cancellation.
- **🛡️ Clean Link Extraction**: Automatically sanitizes tracking parameters (`si`, `igsh`, `fbclid`, `utm_*`) before extracting stream manifests.
- **🎨 Zero-Dependency Micro Favicon Cache**: Fast, offline-cached domain favicons rendered via custom asynchronous L1 (memory) and L2 (disk) caching.
- **🎛️ Perceptual Quality Optimization**: Prefers higher-quality VP9/AV1 codecs with MKV remuxing over bulky transcodes, with zero audio quality degradation.

---

## 🏗️ Architecture

URLoader follows **Unidirectional Data Flow (UDF)** and **Clean MVVM** architecture principles:

```
com.dev.urloader/
├── core/
│   ├── common/             # MediaDownloadManager, UrlExtractor, PlatformDetector, FaviconLoader
│   ├── constants/          # AppConstants, PrefKeys
│   └── ui/                 # BlurMeshBackgroundView (Hardware-accelerated dynamic Canvas)
├── feature/
│   ├── download/
│   │   ├── service/        # DownloadService (ForegroundService with DATA_SYNC type)
│   │   └── ui/             # DownloadHistoryAdapter (ListAdapter with DiffUtil)
│   └── share/
│       └── ui/             # ShareReceiverActivity (Edge-to-edge floating modal)
├── MainActivity.kt         # Primary dashboard & live activity observer
└── URLoaderApp.kt          # Application lifecycle & background engine init
```

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| **Language** | Kotlin 2.0+ (Coroutines, StateFlow) |
| **Target SDK** | Android 16 / SDK 36 (minSdk 24) |
| **Download Engine** | [youtubedl-android](https://github.com/junkfood02/youtubedl-android) (yt-dlp + FFmpeg) |
| **UI System** | ViewBinding + Material 3 Design System |
| **Dependency Mgmt** | Gradle Version Catalogs (`libs.versions.toml`) |
| **Storage & Sharing** | Scoped Storage + FileProvider with secure URI grants |

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- JDK 17+
- Android SDK 36 installed

### Build from Terminal
```bash
# Clone the repository
git clone https://github.com/<your-username>/URLoader.git
cd URLoader

# Run Unit Tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK (Local Signed)
./gradlew assembleRelease
```

Generated APKs will be located at:
`app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

---

## 🧪 Testing

URLoader includes unit tests covering:
- Safe URL extraction and tracking parameter stripping
- Platform detection logic (YouTube, Instagram, TikTok, X, Reddit, Generic)
- Media download format sorting and container fallback configurations
- Persistent history serialization and error recovery

Run the test suite with:
```bash
./gradlew testDebugUnitTest
```

---

## 🤝 Contributing

Contributions are welcome! Please check out [CONTRIBUTING.md](.github/CONTRIBUTING.md) for local development setup, coding guidelines, and pull request procedures.

For security concerns, please refer to our [Security Policy](.github/SECURITY.md).

---

## ⚖️ Disclaimer & License

This project is intended strictly for personal archiving and educational purposes. Ensure you comply with the terms of service of any host platform and respect copyright laws.

Distributed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE) for details.
