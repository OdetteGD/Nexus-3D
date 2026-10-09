# Copy-ready prompts for ChatGPT

Send one prompt at a time after connecting ChatGPT to GitHub or giving it access to this repository.

## 1. Master repository audit and safe implementation

```text
Work on https://github.com/OdetteGD/Nexus-3D. The app is an Android Kotlin project using Google Filament 1.77.2; the starter branch is feat/filament-android-apk. First inspect the actual repository tree, recent commits, pull requests, and latest GitHub Actions run. Never assume files or features exist without reading them.

Keep the real Filament renderer. Preserve the bundled demo-cube.glb startup geometry, GLB import, touch orbit/pinch, reset/reload controls, 64 MiB import limit, and Android APK workflow.

Implement one small, coherent improvement after checking the matching official Filament APIs. Add tests where practical. Build the app, inspect all failures, fix root causes, and rerun. Do not use placeholder UI instead of rendering. Do not delete unrelated files. Work on a feature branch, commit the changes, push, and open/update a PR. Report exact commit, PR, changed files, and workflow status. Never claim the APK build passed unless the workflow actually passed.
```

## 2. Debug blank viewport or missing GLB geometry

```text
Inspect https://github.com/OdetteGD/Nexus-3D and diagnose why the Filament 3D viewport or imported .glb geometry might be blank. Read MainActivity, the Filament dependency versions, manifest, surface lifecycle, loader code, latest failed Actions logs, and matching official Filament 1.77.2 Android samples.

Trace initialization, SurfaceView attachment, render callback scheduling, swap-chain readiness, GLB buffer contents, asset/resource loading, bounds and root transform, camera projection, lighting, and lifecycle cleanup. Add useful diagnostics and keep valid demo geometry visible when import fails. Do not merely hide exceptions, fake a preview, randomly change dependencies, or say it is fixed without a passing APK build. Commit and push a verified fix on a branch and link the workflow run.
```

## 3. Implement the next editor phase

```text
Using the actual code in https://github.com/OdetteGD/Nexus-3D, implement Phase 2 from FEATURE_ROADMAP.md: a scene hierarchy, tap-to-select, entity details, editable translate/rotate/scale controls, and primitive creation. Inspect current code and official Filament 1.77.2 APIs first. Keep touch controls usable and preserve the startup demo and GLB import.

Implement only the pieces that can be completed coherently. Use proper Filament entity, transform, and renderable APIs with explicit ownership and destruction. Add unit tests for pure transform logic where practical. Build the debug APK, inspect/fix failures, then commit and push a feature branch and open a PR. Clearly list anything still not implemented.
```

## 4. Add scene saving and project assets

```text
Extend https://github.com/OdetteGD/Nexus-3D with a versioned project format to save and reopen editor-created nodes, transforms, camera settings, lights, and material settings. Inspect the actual scene representation first. Use Android document-provider APIs and safe relative resource paths; do not lose GLTF texture references or pretend a GLB stores every editor-specific setting. Preserve import, the default demo fallback, and the APK workflow. Add migration/error handling and serialization tests. Run build/tests, fix failures, commit, and open a PR with an accurate summary.
```

## 5. Repair a failed Actions run

```text
Investigate the latest failed Android APK workflow for https://github.com/OdetteGD/Nexus-3D. Read failed steps and full logs, find the first meaningful error instead of a downstream symptom, inspect the relevant Gradle files and dependency coordinates, and compare versions with official Android/Filament documentation. Make the smallest correct fix. Do not blindly change many versions, remove tests just to get green, or report success before a new run passes. Commit and push to a feature branch and provide the commit SHA and exact workflow URL.
```
