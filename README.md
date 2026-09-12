# De-Vanced

[![Release](https://img.shields.io/github/v/release/Akash-Sriram/De-Vanced?style=flat-square&color=blue)](https://github.com/Akash-Sriram/De-Vanced/releases)
[![Build & Patch](https://img.shields.io/badge/Prebuilt%20APK-GooglePhotos--Patched-green?style=flat-square)](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)
[![License](https://img.shields.io/github/license/Akash-Sriram/De-Vanced?style=flat-square)](LICENSE)

Modular Morphe patches for **Google Photos** enabling Pixel-exclusive features, unlimited cloud storage, MicroG/GmsCore authentication, and offline AI editing models.

---

## 🧩 Patches & Features

| Patch | Key Features |
|---|---|
| **Spoof features** | • **Unlimited Original Quality Backup** (spoofs Pixel XL)<br>• **Pixel AI Tools Unlocked**: Magic Eraser, Portrait Blur, Color Pop, Sky |
| **GmsCore support** | • Non-root Google account login via MicroG / GmsCore<br>• Custom package name (`app.morphe.android.apps.photos`) coexistence with stock app |
| **Account avatar** | • Restores Google profile picture across Top Toolbar, Bento Menu, and Account Switcher |
| **Enable DCIM backup control** | • Granular per-folder backup toggles (Screenshots, WhatsApp, Camera)<br>• Stops forced auto-backup of entire DCIM directory |
| **Enable Phenotype flag manager** | • In-app flag editor under `Settings > 🛠️ Morphe Flags`<br>• Search, toggle, export/import flags and UI presets (Floating Nav, Collections V2, Memories) |
| **Bake memory style flags** | • Hard-codes scrapbook graphic borders, typography number cutouts, and 3D depth pop-outs in Memories carousel |
| **AI Model Auto-Seeder** | • In-app background downloader for all 52 TensorFlow Lite neural models<br>• Instant out-of-the-box Magic Eraser & Portrait Blur on any device (e.g. Galaxy S24) without root |

---

## 📲 Pre-built APKs

Ready-to-install builds patched with this bundle are available at:
👉 **[Akash-Sriram/GooglePhotos-Patched](https://github.com/Akash-Sriram/GooglePhotos-Patched/releases)**

---

## 🛠️ Building & Releasing

### Build Patches Bundle Locally
```bash
./gradlew :patches:buildAndroid generatePatchesList
```

### Trigger a Release (CLI)
```bash
# Bumps version, publishes patches, and triggers GooglePhotos-Patched APK build
gh workflow run release.yml --repo Akash-Sriram/De-Vanced -f release_type=patch
```

For full CLI recipes (ADB, model syncing, permissions, and debugging), see **[COMMANDS.md](COMMANDS.md)**.
