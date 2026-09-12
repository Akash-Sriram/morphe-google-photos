# [1.0.0](https://github.com/Akash-Sriram/De-Vanced/releases/tag/v1.0.0) (2026-09-13)

Welcome to the inaugural standalone release of **De-Vanced (Morphe Patches)**! This release consolidates months of advanced research and engineering into a unified, out-of-the-box experience for Google Photos on both rooted and non-rooted devices (including Samsung Galaxy S24, Pixel, OnePlus, Xiaomi, and more).

---

### ✨ Core Highlights & Features

#### 🧠 Neural Model Auto-Seeder & Background Streaming (`PhotosModelSeeder`)
- **Seamless Non-Root Delivery**: Automatically provisions all 52 official TensorFlow Lite ML models (~241.5 MB) required for Google Photos AI editing tools.
- **In-App Background Downloader**: On fresh installs (such as on Samsung Galaxy S24), streams and unzips the verified model pack directly from GitHub Releases CDN with zero user intervention.
- **Clear-Data Immunity**: Simultaneously caches all models and MDD manifests into persistent external storage (`/storage/emulated/0/Android/media/app.morphe.android.apps.photos/`), ensuring models instantly rehydrate after "Clear Data" without needing network access.
- **Model Readiness Smali Gates**: Hard-baked bytecode overrides for `Lanqb`, `Lanrk`, `Laspz`, and `Larea` to report Magic Eraser, Portrait Segmenter, and Sky replacement models as loaded and ready.

#### ☁️ Unlimited Original Quality Backup (Pixel 2016 Spoof)
- **Permanent Unmetered Cloud Storage**: Default feature spoofing set to original Google Pixel (`NEXUS_PRELOAD`) for unlimited photo and video backups at full original quality.
- **Tensor TPU Crash Prevention**: Selectively avoids newer Pixel 8/9 spoof flags (`PIXEL_2023_MIDYEAR_PRELOAD`) that trigger missing `.darwinn` Tensor NPU hardware traps on Qualcomm Snapdragon and Samsung Exynos chipsets.

#### 🎛️ Complete Official Phenotype Flags Engine (2,492 Flags)
- **Zero-Config Feature Parity**: Embeds the full set of 2,492 production phenotype flags (`PhenotypeSeedData.java`), automatically seeded into SharedPreferences on first boot.
- **In-App Phenotype Flag Manager**: Access and customize experimental Google Photos flags directly from the app toolbar with built-in search, toggle overrides, and JSON import/export.
- **Dynamic Google Account Synchronization**: Automatically links the active Google account token across multi-account environments to preserve individual settings.

#### 👤 Dynamic Google Account Avatar Bridge
- **Native Account Switcher Support**: Fetches and renders user Google profile avatars dynamically in circular anti-aliased views across top app bars and account switcher sheets without requiring Google Play Services signature spoofing.

#### 🎨 Scrapbook Memories & Local Font Provider Bypass
- **Offline Font Resolution**: Hard-bakes a local font provider bypass (`BakeMemoryStyleFlagsPatch.kt`) pointing to system `/system/fonts/` (Roboto, Noto Serif), eliminating corrupted cutout text or crashes caused by missing GMS font certificates.
- **Memory Styles**: Fully unlocks modern scrapbooking styles, animated collage templates, and memory music playback.

---

### 📦 Compatibility
- **Target Application**: Google Photos (`com.google.android.apps.photos` / `app.morphe.android.apps.photos`)
- **Supported Versions**: `7.0.0` - `7.92.0+`
- **Supported Android Versions**: Android 8.0 (API 26) through Android 15 (API 35)
- **Supported Architectures**: `arm64-v8a`, `armeabi-v7a`
