<p align="center">
  <img src="https://raw.githubusercontent.com/Dev97633/Debloater-next/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Debloater Icon" width="120"/>
</p>

<h1 align="center">Debloater-next</h1>

<p align="center">
  A safe, open-source Android debloater using Shizuku — no root required.
</p>

<p align="center">
  <a href="https://github.com/Dev97633/Debloater-next/releases">
    <img src="https://img.shields.io/github/v/release/Dev97633/Debloater-next?style=for-the-badge&logo=github&color=green" alt="Latest Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/Dev97633/Debloater-next?style=for-the-badge&color=blue" alt="License: MIT">
  </a>
  <a href="https://github.com/yourusername/Debloater/stargazers">
    <img src="https://img.shields.io/github/stars/Dev97633/Debloater-next?style=for-the-badge&color=yellow" alt="Stars">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-orange?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
</p>

## Features

- Uninstall or disable apps with confirmation dialog
- No root required — uses Shizuku for privileged operations
- Easy to restore uninstalled and Disable apps
- easy to find current app status 


## ⚠️ Important Warning
**Debloater allows you to uninstall or disable any app on your device, including system apps.**

- **Disabling/uninstalling critical system apps may cause:**
  - Bootloops
  - Soft-bricks
  - Loss of important features (camera, calls, Wi-Fi, etc.)
  - Need for factory reset or re-flashing firmware

- **You are solely responsible** for any damage or issues caused by using this app.
- **Always research before disabling/uninstalling any app** — especially ones with system-level permissions.
- **Use at your own risk** — the developer is not liable for any data loss or device damage.
  

## Requirements

- Android 7.0+ (API 24+)
- [Shizuku](https://shizuku.rikka.app) installed and running (via ADB or wireless debugging)

## Installation

1. Download the latest APK from [Releases](https://github.com/Dev97633/Debloater-next/releases)
2. Install the APK on your device
3. Install and start Shizuku (see Shizuku guide)
4. Open Debloater → follow onboarding → grant Shizuku permission on the last screen

## Usage

1. Launch the app
2. Go through quick onboarding
3. Tap "Grant" on the last screen to connect to Shizuku
4. Search apps, disable bloatware, or uninstall safely

## Building from Source

```bash
git clone https://github.com/Dev97633/Debloater-next.git
cd Debloater
./gradlew assembleDebug 
```

## Credits

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** by Rikka — the core engine for privileged operations without root
- Icon from **[Icon Kitchen](https://icon.kitchen)** 
