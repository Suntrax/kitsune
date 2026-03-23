# Kitsune

A modern manga reading app for Android.

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![MinSDK](https://img.shields.io/badge/MinSDK-26-orange.svg)

## Important

I do not host any manga content. All content is sourced from third-party websites.

## Features

- **Search** - Find manga fromatsu.moe
- **Reader** - Read chapters with image preloading
- **Progress Tracking** - Continue where you left off
- **Explore** - Browse featured and categorized manga
- **Home** - Personal reading lists and continue reading
- **Dark Mode** - Eye-friendly dark theme

## Requirements

- Android 8.0+ (API 26+)

## Installation

Download the APK from [Releases](https://github.com/Suntrax/kitsune/releases) and install.

## Tech Stack

- Kotlin + Jetpack Compose
- Coil for image loading
- atsu.moe API
- MVVM Architecture

## Project Structure

```
app/src/main/java/com/blissless/manga/
├── data/           # Repositories and data sources
├── ui/
│   ├── components/ # Reusable UI components
│   ├── screens/    # App screens
│   └── theme/      # Material theming
├── MainActivity.kt
└── MainViewModel.kt
```

## Disclaimer

This app is for educational purposes only. I do not host, upload, or distribute any manga content. All content is provided by third-party sources.
