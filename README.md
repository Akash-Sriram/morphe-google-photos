# Google Photos

Morphe patches for **Google Photos**, inspired by and derived from [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced).

Features Pixel spoofing for unlimited original-quality backups, non-root GmsCore/MicroG support, granular DCIM folder backup control (ported from [RevealedSoulEven/XposedPhotosFIX](https://github.com/RevealedSoulEven/XposedPhotosFIX)), an in-app flag manager with curated feature recommendations from [polodarb/GMS-Flags-Reborn](https://github.com/polodarb/GMS-Flags-Reborn), and offline neural model delivery.

---

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.4.1](https://github.com/Akash-Sriram/morphe-google-photos/releases/tag/v1.4.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details open>
<summary>Google Photos&nbsp;&nbsp;•&nbsp;&nbsp;7 patches</summary>
<br>

**Supported versions:**

| 7.92.0.977185651 | 7.93.0.982110057 (experimental) |
| :---: | :---: |

| Patch | Description |
|---|---|
| [Account avatar](#account-avatar) | Loads and displays account profile avatars across the top toolbar, Bento menu, and account switcher. |
| [Bake memory style flags](#bake-memory-style-flags) | Hard-codes the Styles in Memories feature flags into the DEX. |
| [Enable DCIM folders backup control](#enable-dcim-folders-backup-control) | Disables always on backup for the Camera and other DCIM folders, allowing you to control backup for each folder individually. This will make the app default to having no folders backed up. |
| [Enable Phenotype flag manager](#enable-phenotype-flag-manager) | Enables an in-app flag manager in Photos Settings to customize experimental UI redesigns and feature flags. |
| [GmsCore support](#gmscore-support) | Allows the app to work without root by using a different package name when patched using a GmsCore instead of Google Play Services. |
| [Model Readiness Gates](#model-readiness-gates) | Bypasses the 0MB Mobile Data Download check for AI models and reports them as loaded. |
| [Spoof features](#spoof-features) | Spoofs the device to enable Google Pixel exclusive features, including unlimited storage. |

</details>

<!-- PATCHES_END -->

---

## Phenotype Flags

Flags configured and editable in-app via **Settings > Morphe Flags**:

<details open>
<summary>Presets&nbsp;&nbsp;•&nbsp;&nbsp;17 flags</summary>
<br>

| Flag ID | Type | Default | Description |
|---|---|---|---|
| `2675` | Boolean | `true` | Modern UI components |
| `2892` | Boolean | `true` | Memories stories |
| `3013` | Long | `1` | Gemini AI / Ask Photos tab |
| `3023` | Boolean | `true` | Enhanced Memories navigation |
| `3024` | Boolean | `true` | Floating bottom navigation bar |
| `3026` | Boolean | `true` | Redesigned Memories carousel |
| `3606` | Boolean | `true` | Modern layout controls |
| `3611` | Boolean | `true` | Updated UI styling |
| `4306` | Boolean | `true` | Dynamic action bars |
| `4311` | Boolean | `true` | Floating navigation styling |
| `45683689` | Boolean | `true` | "AI Enhance" V2 preset in photo editor |
| `45705305` | Boolean | `true` | "Tap, circle or brush to select" editor tool |
| `45732792` | Boolean | `true` | Updated grid view components |
| `45743215` | Boolean | `true` | Floating date capsule pill `[ Today ]` |
| `45753590` | Boolean | `true` | "On this device" top bar filter |
| `45762698` | Long | `2` | Collections Shelves V2 layout |
| `45802110` | Long | `2` | Collections Shelves V2 content view |

</details>

---

## Pre-built APKs

* [GooglePhotos-Patched Releases](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)
* [Google Photos on APKMirror](https://www.apkmirror.com/apk/google-inc/photos/)

---

## Credits

* [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced) - Base Google Photos patches
* [RevealedSoulEven/XposedPhotosFIX](https://github.com/RevealedSoulEven/XposedPhotosFIX) - DCIM folder backup control
* [polodarb/GMS-Flags-Reborn](https://github.com/polodarb/GMS-Flags-Reborn) - Phenotype flag suggestions & recipe API
* [Morphe Patcher](https://github.com/MorpheApp) & [ReVanced](https://github.com/ReVanced) - Patcher framework and tooling
