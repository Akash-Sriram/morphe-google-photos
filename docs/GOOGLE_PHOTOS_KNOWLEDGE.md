# Google Photos & MicroG-RE Architecture Knowledge Base

This document serves as the permanent source of truth and persistent memory for Google Photos patching, AI/ML model provisioning, Stories typography, and MicroG-RE subsystem integration.

---

## 1. AI / ML Models & Mobile Data Download (MDD)

### Open CDN Architecture (No Attestation Required)
> [!IMPORTANT]
> **Google's ML/AI Model CDN is completely open and unauthenticated.**
> Model assets (`.tflite.enc`, `.pb.enc`, JSON configs) hosted on:
> - `https://edgedl.me.gvt1.com/edgedl/photos/photos-mi-models/...`
> - `https://www.gstatic.com/photos-mi-models/...`
> - `https://www.gstatic.com/photos-editing/...`
> 
> **DO NOT** require device attestation, Play Integrity, KeyMint, or authentication tokens. They are direct, static HTTP/HTTPS assets freely downloadable by any client.

### Model Storage & Encryption
* **File Format**: Neural models are downloaded as encrypted binaries (`.tflite.enc` or `.pb.enc`). Decryption happens entirely on-device at runtime by Google Photos native libraries (`libphoto_editor.so`) using internal keys.
* **Storage Location**: Stored inside Google Photos' private sandbox directory:
  ```
  /data/data/<package_name>/files/mdd/
  ```
  with physical filenames formatted as `datadownloadfile_<id>`.

### MDD Manifest System
The local Mobile Data Download (MDD) registry is maintained in Android `shared_prefs`:
1. `gms_icing_mdd_groups.xml`: Defines group configurations (e.g., `udon` for Magic Eraser, `unblur_v1_cpu`, `buttercup`, `photos_cinematic`, `cubelut`, `grainy_film`, `light_leak`, `tonefix`, `face_retouch`, `portrait_preprocessed_image`, `spotlight`), mapping each model group to its CDN URLs, expected SHA-1 hashes, and target filenames.
2. `gms_icing_mdd_shared_files.xml`: Maps SHA-1 checksums to the local `datadownloadfile_*` storage filenames.
3. `gms_icing_mdd_migrations.xml`: Tracks MDD migration version states.

### Dual Package Support
Both official and modded package names are supported in manifests and seeder:
* `com.google.android.apps.photos` (Official)
* `app.morphe.android.apps.photos` (Morphe modded)

### Seeder & Runtime Engine (`PhotosModelSeeder`)
* **Dynamic Seeding**: Coordinates parallel downloads (3 worker threads) to hydrate missing models.
* **Manifest Injection**: Synthesizes and writes complete, package-aligned MDD XML manifests into `shared_prefs` to bypass waiting for slow or broken server pushes.
* **Low-Tier GPU Guard**: Automatically purges GPU-only models (e.g., `unblur_v2_gpu`) on low-tier or incompatible GPUs to enforce stable CPU fallback.
* **MddCrashGuard**: Intercepts and suppresses uncaught MDD thread exceptions (`bvrh`, `bwyy`, `ptz-rcs`) during background initialization.
* **Readiness Gates**: Bytecode hook in `ModelDownloadManager` forces `AVAILABLE` state and `isReady() == true` to prevent the UI from getting stuck on "0 MB" or disabled editor chips.

---

## 2. Stories & Typography Architecture

### Open Fonts CDN
> [!NOTE]
> Downloadable fonts are served from public, unauthenticated Google CDN endpoints:
> `https://fonts.gstatic.com/s/a/...` (cataloged in `fonts.gstatic.com/s/a/directory.xml`).

Supported typography includes:
* **Google Sans**: Regular, Medium, Bold, Display Regular, Display Bold
* **Story & Memory Styles**: DM Serif (Italic/Regular), BioRhyme, Sarina, Press Start 2P, Caveat, Handlee, Montserrat, Oswald, Playfair Display

### Downloadable Fonts via GmsCore (`FontsProvider`)
* Standard Android apps request downloadable fonts via `FontsContract` directed at authority `com.google.android.gms.fonts`.
* **MicroG-RE Current State**: `FontsProvider.kt` in MicroG has historically been a stub hardcoded to return `/system/fonts/Roboto-Regular.ttf`.
* **Client Patch Workaround (`StoryFontResolver`)**: Bytehooks `SkottieFontUriResolver` and `SkottieFontLoaderDispatcher` in Google Photos to fetch TTFs directly into app storage.
* **Native MicroG-RE Solution**: Upgrading `FontsProvider.kt` in MicroG-RE to parse font queries, download requested TTFs from `fonts.gstatic.com` to MicroG cache, and return valid `ParcelFileDescriptor` handles resolves Stories and all Google typography system-wide without client bytecode patching.

---

## 3. MicroG-RE Subsystem & IPC Integration

### Status of Upstream MicroG-RE Features:
| Feature | Upstream PR | Status | Architectural Role |
| :--- | :--- | :--- | :--- |
| **Phenotype Live Sync** | [PR #350](https://github.com/MorpheApp/MicroG-RE/pull/350) | **Merged** | Live Heterodyne server sync and client IPC flag distribution for modded Google apps (`app.morphe.android.apps.photos`). |
| **GCM / MCS Push Routing** | [PR #362](https://github.com/MorpheApp/MicroG-RE/pull/362) | **Merged** | Dynamic package aliasing and MCS push routing for modded Google apps without hardcoded package mismatch errors. |
| **FontsProvider** | — | **Planned** | Dynamic Google Fonts CDN resolution in GmsCore to eliminate client-side typography hooks. |

---

## 4. Architectural Separation of Concerns

When planning changes across Google Photos and MicroG-RE:
* **Belongs in `morphe-google-photos` (Client Patch)**:
  * Local sandbox file seeding (`/data/data/<pkg>/files/mdd/`) because external apps (including MicroG) cannot write across Android UID boundaries.
  * Internal UI readiness gates, editor tool chip unlocks, and Pixel feature spoofing.
  * Local XML manifest rehydration in `shared_prefs`.
* **Belongs in `MicroG-RE` (GMS Core Service)**:
  * GCM / MCS push registration and notification delivery.
  * System-wide Downloadable Fonts provider (`com.google.android.gms.fonts`).
  * Phenotype server sync and experiment flag IPC.
  * Global GMS authentication and account management.
