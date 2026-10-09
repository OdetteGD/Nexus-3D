# Nexus-3D

Nexus-3D is an Android-first 3D scene editor powered by the official [Google Filament](https://github.com/google/filament) renderer (Filament 1.77.2). It renders real GLB geometry; it does not use a static preview canvas.

## Editor features in this branch

- Filament viewport with a bundled demo GLB at startup, touch orbit/pinch zoom, and camera reset.
- Binary glTF (.glb) import through Android document picker, with a 64 MiB input limit and fallback/error reporting.
- Editor-owned cube, sphere, plane, and cylinder primitives rendered by Filament through generated GLB geometry.
- Object selection cycling, duplicate, rename, delete, and step-based translate/rotate/scale controls.
- Versioned Nexus scene JSON save/open, GLB export, and undo/redo snapshots.
- Unit tests for scene serialization, transforms, validation, and generated GLB structure.
- GitHub Actions CI that builds and verifies a debug APK and an unsigned release APK, then uploads both as an artifact.

The editor is functional but **not yet a full Unity-style engine**. The exact limitations—such as external-resource `.gltf` import, editable lights/material inspectors, gizmos, animation timeline, hierarchy tree, and device runtime verification—are recorded in [FEATURE_ROADMAP.md](FEATURE_ROADMAP.md). Do not interpret an unchecked item there as implemented.

## Build locally

Use JDK 17, Android SDK Platform 37, Android Build Tools 36.0.0, Android Gradle Plugin 9.1.1, and Gradle 9.3.1.

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`

The unsigned release APK can be built with `gradle --no-daemon assembleRelease`; it is not ready for distribution until you configure signing credentials securely.

## Build with GitHub Actions

Open **Actions → Android APK → Run workflow**, or push a commit / pull request. When the workflow passes, open that run and download the `nexus-3d-apks` artifact. It contains:

- `app-debug.apk` — debug-signed APK for testing.
- `app-release-unsigned.apk` — unsigned release variant, not Play Store ready.

Workflow: [.github/workflows/android-apk.yml](.github/workflows/android-apk.yml)

No signing keys or passwords are stored in this repository. Configure them as GitHub Actions secrets before adding a signed release workflow.

## Filament notes

- Runtime dependencies are pinned to the same Filament version: `filament-android`, `gltfio-android`, and `filament-utils-android`.
- Filament JNI is initialized before constructing `ModelViewer`.
- Keep Filament runtime and any compiled material assets on matching versions.
- References: [Filament docs](https://google.github.io/filament/) and [official Android samples](https://github.com/google/filament/tree/main/android/samples).
