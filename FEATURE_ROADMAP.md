# Nexus-3D feature roadmap

This repository started with only a README, so develop the editor in small verified stages. Keep the current Filament viewport working and make sure the demo cube remains visible when no imported model is available.

## Included in the foundation

- [x] Native Android Kotlin app using Google Filament 1.77.2.
- [x] Bundled GLB demo geometry at startup.
- [x] GLB importer using Android's document picker.
- [x] Orbit / pinch interactions and reset / reload-demo controls.
- [x] File-size guard, status errors, and demo fallback after failed imports.
- [x] GitHub Actions debug APK build and artifact upload.
- [x] Copy-ready prompts for future ChatGPT-assisted work.

## Phase 2 — editor and scene editing
- Scene hierarchy for nodes/entities in a loaded glTF asset.
- Tap-to-select with object name and node/entity details.
- Translate, rotate, and scale gizmos with local/world transform modes.
- Create primitive meshes (cube, sphere, plane, cylinder); delete and duplicate entities.
- Add and edit directional, point, and spot lights.
- Camera FOV and near/far clipping controls.
- Phone/tablet responsive layout that protects the viewport on narrow screens.

## Phase 3 — materials and rendering
- PBR inspector: base color, metallic, roughness, emissive, alpha mode, and double-sided options.
- Skybox and image-based lighting selection with safe defaults.
- Rendering quality controls: MSAA, FXAA, bloom, and ambient occlusion.
- Debug controls for bounds, frame time, and renderer diagnostics where supported by Filament.

## Phase 4 — projects and assets
- Project browser for models, textures, and environment maps.
- GLTF import with external resources and safe relative-path resolution; retain GLB as a reliable path.
- Versioned Nexus scene JSON for editor-created nodes and settings.
- Save/open projects, autosave, recent projects, progress, and cancellation.
- Export without losing texture/material references.

## Phase 5 — production quality
- glTF animation list and timeline.
- Undo/redo command stack.
- Crash-safe lifecycle and device memory/performance diagnostics.
- Unit tests for scene serialization and transforms plus CI pull-request checks.
- Debug APK and signed release APK build variants (store signing keys only in GitHub Actions secrets).

## Constraints
- Do not claim a feature exists until code and tests are present.
- Do not replace the working Filament initializer, model loader, or render loop without verifying APIs against the official 1.77.2 samples.
- Never substitute a placeholder canvas for the real renderer.
- Prefer one testable pull request per phase; inspect and fix actual CI failures before declaring success.
