# Nexus-3D implementation status

This file records the state of the checked-in implementation. A checkbox means code exists; **CI status is reported separately** and must not be inferred from the checkbox. Nexus-3D uses the official Google Filament Android libraries (1.77.2) and renders GLB/GLTF assets through Filament's real renderer.

## Implemented in the current branch

- [x] Native Android Kotlin application using Google Filament 1.77.2 and a real Filament `ModelViewer`.
- [x] Bundled `demo-cube.glb` startup scene and recovery to the bundled demo if a model load fails.
- [x] Android document-picker import for binary glTF (`.glb`) with a 64 MiB size limit, file validation, background file reads, and visible error reporting.
- [x] Mobile touch orbit and pinch zoom via Filament's `ModelViewer` input handler; reset-view control.
- [x] Editor-owned scene document with cube, sphere, plane, and cylinder mesh generation as binary glTF (GLB) geometry.
- [x] Scene-object add, cycle-selection, duplicate, rename, and delete controls.
- [x] Scene-object translation, rotation, and scale step controls that modify node transforms in the generated GLB.
- [x] Versioned Nexus scene JSON save/open with validation, finite transform checks, and a 500-object safety limit.
- [x] Export editor-owned geometry as GLB.
- [x] In-memory undo/redo snapshots for editor scene operations (up to 50 undo states).
- [x] Scrollable touch-friendly control rows for narrow phone screens.
- [x] JVM unit tests added for scene JSON round-tripping, transform operations, primitive GLB chunk/accessor structure, object duplication/deletion, and invalid versions/scales.
- [x] GitHub Actions installs JDK 17, Android API 37 and build tools without the obsolete `tools` package; configures AGP/Gradle; runs unit tests; builds and verifies debug and unsigned release APKs; and uploads APK artifacts.
- [x] No signing passwords or keystore files are stored in the repository.

## Explicit limitations (not implemented; do not describe as complete)

- Imported external GLB assets render in Filament, but they are not yet converted into the editor-owned scene model. The scene editing controls operate on the Nexus scene and switch away from an imported asset when used.
- Textual `.gltf` import with external buffers/images, safe relative resource resolution, and texture dependency preservation are not implemented; the importer currently accepts `.glb`.
- There is no full expandable scene hierarchy tree or tap-to-select hit-testing. Selection currently cycles through editor-owned objects.
- Transform editing uses working step buttons, not drag gizmos or local/world coordinate modes.
- Camera orbit/zoom and reset are available; dedicated pan, focus-selected, and projection/FOV/near/far controls are not implemented.
- User-created directional, point, and spot lights and a light-property inspector are not implemented.
- The editor scene uses a default glTF PBR material. Editable material/texture inspectors (base color, metallic, roughness, emissive, alpha mode, double-sided), skybox/IBL selection, shadows, bloom, FXAA, SSAO, and quality presets are not implemented.
- There is no project browser, recent-project list, autosave/recovery, progress cancellation UI, or texture/environment asset manager.
- glTF animation discovery/playback and a timeline are not implemented.
- Frame-time/FPS, GPU memory, and renderer diagnostics panels are not implemented; current diagnostics are status/error messages only.
- The release workflow builds an **unsigned** release APK. A distributable signed release requires the owner to configure keystore credentials as GitHub Actions secrets.
- CI unit tests validate deterministic scene/GLB logic. Device/emulator rendering, touch interactions, and lifecycle behavior still require Android runtime testing; a successful compilation alone does not prove these runtime paths.

## Verification record

The original failed workflow stopped because `android-actions/setup-android@v3` requested the removed SDK `tools` package. The workflow now resolves the installed command-line tools directly and installs only named valid SDK packages.

The next observed build failure showed Filament 1.77.2 requires `compileSdk >= 37`; the project was upgraded to Android Gradle Plugin 9.1.1, Gradle 9.3.1, and API 37 using the official Android compatibility guidance. The latest run must be checked before recording a passing build or linking an APK artifact.
