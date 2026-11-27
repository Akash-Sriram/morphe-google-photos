# Morphe Google Photos (MGP)

[![Dev Release](https://img.shields.io/github/v/release/Akash-Sriram/morphe-google-photos?include_prereleases&label=Dev%20Release&color=blue)](https://github.com/Akash-Sriram/morphe-google-photos/releases)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3.0-green.svg)](https://www.gnu.org/licenses/gpl-3.0)

**MGP Patch** brings unlimited unmetered Pixel backup, GmsCore/MicroG non-root support, on-device AI model seeding, and in-app flag controls to **Google Photos** — derived from [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced).

[📦 Ready-to-Install APKs](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases) &nbsp;&bull;&nbsp; [🩹 Latest Patch Bundle (.mpp)](https://github.com/Akash-Sriram/morphe-google-photos/releases) &nbsp;&bull;&nbsp; [📖 Architecture Knowledge](docs/GOOGLE_PHOTOS_KNOWLEDGE.md)

---

## 🎯 Supported Google Photos Versions

<!-- PATCHES_START EXPANDED -->
> **[v1.19.0-dev.7](https://github.com/Akash-Sriram/morphe-google-photos/releases/tag/v1.19.0-dev.7)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`dev`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**4 patches** across **1 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**Google Photos**](PATCHES.md#google-photos-comgoogleandroidappsphotos) | 4 | `8.0.0.1011730683` (stable)<br><sub>+ 5 experimental (`8.1`–`8.2`)</sub> | [`com.google.android.apps.photos`](https://play.google.com/store/apps/details?id=com.google.android.apps.photos) |
<!-- PATCHES_END -->

<details>
<summary><b>🔍 View All Specific Target Versions</b></summary>

* `8.0.0.1011730683` *(Recommended Stable Anchor)*
* `8.1.0.1020990585` *(Experimental)*
* `8.1.0.1023737660` *(Experimental)*
* `8.2.0.1023899193` *(Experimental)*
* `8.2.0.1026567706` *(Experimental)*
* `8.2.1.1030128190` *(Experimental Latest)*

</details>

---

## 🧩 The 4 Consolidated Patches

Starting with `v1.19.0-dev`, all fragmented patches are unified into **4 user-visible patches**. Permanent patch-time decisions are eliminated — all behavioral settings are controlled dynamically inside **Morphe Settings**:

| Patch | Default | Description |
|---|:---:|---|
| **Morphe Core** | `ON` | Master engine: backup identity spoofing, DCIM folder decoupling, on-device AI seeding, Phenotype flag manager, and Material 3 Morphe Settings. |
| **AMOLED dark theme** | `ON` | Pure pitch-black (`#000000`) surfaces for OLED displays while preserving clean light theme. |
| **GmsCore support** | `ON` | Universal MicroG / non-root operation, push notifications channel, and synchronized account avatar bridge. |
| **Custom branding** | `OFF` | Renames the launcher title and application icon to custom Morphe branding. |

---

## 🚀 Key Features

### 🔄 Dynamic Backup Profiles & Video Boost Passthrough
* Switch instantly between **Pixel XL** (unmetered Original Quality), **Pixel 5** (unmetered Storage Saver), or **Original Device** from Morphe Settings without re-patching.
* **Auto-Sync Mode**: Automatically switches spoofed profile based on chosen backup quality in Photos settings.

<details>
<summary><b>Details: How Video Boost Passthrough works for Pixel 8/9 Pro users</b></summary>

Switching to the **Original Device (Disabled)** profile restores your physical device identity (`Build.MODEL`, `Build.DEVICE`). Genuine Pixel users can upload raw files for server-side **Video Boost** and **Night Sight Video** cloud processing, then switch back to **Pixel XL** to back up the resulting video permanently with zero quota usage.
</details>

### ⚡ Bytecode-Level Identity Spoofing (Android 12 – 16)
* Injects `invoke-static` calls directly at the smali bytecode level, bypassing Android 12+ reflection blocks on `static final` fields to eliminate quota leakage.
* Excludes video decoders and ExoPlayer (`Landroidx/media`, `Lcom/google/android/exoplayer2`) from spoofing to preserve hardware-accelerated 4K/60fps/HDR playback.

### 📁 DCIM Decoupling & Selective Folder Backup
* Removes the forced backup constraint on `DCIM/Camera`, granting individual backup toggles for every folder under **Collections $\rightarrow$ On this device**.
* Intelligently routes queries in `PhotosTimelineFilter` so the main timeline remains decoupled while camera albums, cover thumbnails, and stories preview cleanly.
* **Creation Interceptor**: Intercepts Made-For-You (MFY) cards and Stories, exporting them cleanly to **`Pictures/Google Photos`** with original EXIF capture timestamps. Once the folder's backup toggle is enabled, Google Photos backs them up through the unmetered Pixel pipeline at **0 bytes quota**.

### 🎨 Material 3 Morphe Settings & Flag Manager
* Dedicated full-screen Material 3 settings interface following system Material You and AMOLED themes.
* Search and toggle hundreds of experimental Phenotype flags without root, or apply one-tap vetted presets (**Ultra Pixel Experience**, **Modern UI & Bento Layout**, **Floating Navigation Bar**).

### 🤖 On-Device AI / ML Model Seeder
* Automatically checks, downloads, and seeds on-device assets for **Gemini Nano**, **Magic Eraser**, **Segmentation Filters**, and **Portrait Light**.
* Bypasses the 0MB Mobile Data Download (MDD) check and enforces stable CPU fallback on low-tier GPUs.

### 🌐 Universal GmsCore / MicroG Support
* Seamless non-root operation without proprietary Google Play Services.
* GmsCore push notification channels for background uploads and partner sharing.
* Synchronized Avatar Bridge rendering high-res profile photos across the toolbar, Bento disc, and account switcher.

### 🛠️ Native Toolkit & Media Utilities
* Batch creation downloader, local deduplication, and media filtering tools.
* Storage & cache cleaner (purges thumbnail memory cache and transient download artifacts).
* Safe Offload exporter cleanly routing exports to **`Pictures/GooglePhotos_Export`** to prevent auto-rebackup loops.

---

## ⚠️ Nuances & Server-Side Limitations

<details>
<summary><b>1. Memories / Stories & Made-For-You Creations</b></summary>

Saving creations through Google's cloud renderer triggers server-side processing on Google's backend that bypasses on-device spoofing and debits Google Drive storage quota.  
**Solution**: MGP intercepts creations and exports them locally to `Pictures/Google Photos` with original EXIF timestamps. Enabling backup on this device folder lets Google Photos back them up through the unmetered Pixel pipeline at **0 bytes quota**.
</details>

<details>
<summary><b>2. Video Boost & Night Sight Video</b></summary>

Google's cloud GPU clusters only process Video Boost uploads for eligible physical devices. Set the profile to **Original Device (Disabled)** in Morphe Settings for the upload, then switch back to **Pixel XL** once the boost completes.
</details>

<details>
<summary><b>3. "Free Up Space" Storage Percentage Display</b></summary>

The percentage displayed on the *"Free up space"* screen measures total device storage usage across the entire phone (`/data` via `StatFs`), not spoofed Pixel capacity.
</details>

---

## 🙏 Credits & Acknowledgements

This project is made possible thanks to the work of open-source contributors:

| Project | Author | Contribution |
|---|---|---|
| **[De-Vanced](https://github.com/RookieEnough/De-Vanced)** | [RookieEnough](https://github.com/RookieEnough) | Original foundation and inspiration for Google Photos patching |
| **[GMS-Flags-Reborn](https://github.com/polodarb/GMS-Flags-Reborn)** | [polodarb](https://github.com/polodarb) | In-app Phenotype flag manipulation concepts and flag databases |
| **[XposedPhotosFIX](https://github.com/RevealedSoulEven/XposedPhotosFIX)** | [RevealedSoulEven](https://github.com/RevealedSoulEven) | DCIM and selective folder backup decoupling implementations |
| **[Google-Photos-Toolkit](https://github.com/xob0t/Google-Photos-Toolkit)** | [xob0t](https://github.com/xob0t) | Media management utilities and batch creation tools |
| **[MorpheApp](https://github.com/MorpheApp)** | [Morphe](https://github.com/MorpheApp) | Core patch engine, runtime extensions, and patcher ecosystem |

---

## 📦 Ready-to-Install Builds

Pre-built and signed APKs are compiled automatically and available at **[Akash-Sriram/GooglePhotos-Patched](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)**.
