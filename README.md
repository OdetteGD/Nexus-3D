# Nexus-3D

Nexus-3D is an Android-first 3D editor starter powered by [Google Filament](https://github.com/google/filament).

## Included in this starter

- Real Filament rendering using the official Maven artifacts (1.77.2).
- A bundled GLB demo cube, so visible geometry appears immediately after launch.
- GLB model import with Android's document picker and a 64 MiB import safety limit.
- Touch orbit / pinch zoom through Filament's `ModelViewer` controls.
- Reset camera and reload demo-scene controls.
- Clear error feedback and demo-scene recovery if an imported GLB cannot be parsed.
- GitHub Actions workflow to build and upload an installable debug APK.

> This repository started with only a README. This is a real renderer foundation, not yet a complete Unity-style editor. See [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) and copy-ready prompts in [PROMPTS_FOR_CHATGPT.md](PROMPTS_FOR_CHATGPT.md).

## Build locally

1. Install Android Studio and Android SDK API 36.
2. Open this repository root in Android Studio.
3. Sync Gradle and run the `app` configuration on an Android device or emulator that supports OpenGL ES 3.0.

With Gradle 8.13 and JDK 17 installed:

```bash
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Build with GitHub Actions

Open **Actions → Android APK → Run workflow**, or push a commit / pull request. When the workflow passes, open the run, scroll to **Artifacts**, and download `nexus-3d-debug-apk`. It is a debug-signed APK for testing, not a Play Store release.

Workflow: [.github/workflows/android-apk.yml](.github/workflows/android-apk.yml)

## Filament notes

- Runtime dependencies are pinned to the same Filament version: `filament-android`, `gltfio-android`, and `filament-utils-android`.
- Filament's JNI layer must be initialized before using `ModelViewer`.
- Keep Filament runtime and any future compiled material assets on matching versions.
- References: [Filament docs](https://google.github.io/filament/) and [official Android samples](https://github.com/google/filament/tree/main/android/samples).

Next: read [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md) and [PROMPTS_FOR_CHATGPT.md](PROMPTS_FOR_CHATGPT.md).
