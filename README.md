<p align="center">
  <img src="https://raw.githubusercontent.com/Dev97633/Debloater-next/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Debloater Icon" width="120"/>
</p>

<h1 align="center">Debloater-next</h1>

<p align="center">
  A fast, clean, and modern Android debloater powered by Shizuku — no root required.
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
  <img src="https://img.shields.io/badge/Powered%20by-Shizuku-purple?style=for-the-badge" alt="Shizuku">
</p>

## Features

- Real-time search by app name or package
- Uninstall or disable apps with confirmation dialog
- Pull-to-refresh to update the list instantly
- System apps highlighted in a different color
- Smooth scrolling and modern Material You design (dynamic colors on Android 12+)
- No root required — uses Shizuku for privileged operations
- Beautiful onboarding flow + About screen with device specs

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/screenshot1.png" width="300"/></td>
    <td><img src="screenshots/screenshot2.png" width="300"/></td>
    <td><img src="screenshots/screenshot3.png" width="300"/></td>
  </tr>
  <tr>
    <td>Main list with search</td>
    <td>Pull-to-refresh</td>
    <td>About screen</td>
  </tr>
</table>

*(Add your own screenshots here later — take them from emulator/device)*

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
