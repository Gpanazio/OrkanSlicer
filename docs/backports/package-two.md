# Memory and dense-mesh backport to 0.8.204-en

This branch preserves version 0.8.204/code 239, the existing Android UI and the
published 0.8.204 ARM64 native bundle. It does not add JNI declarations or native
source changes. Original geometry remains available for slicing. Simplification
for surface editing requires explicit user confirmation and changes the loaded
model, not the source file.

## Included

- `3a599ebbccd1`: low-RAM detection, default viewport scale 70% (explicit saved
  preferences still win), 25 initial preview layers, cheap inactive-plate bounds,
  cleanup of surplus GL meshes and preservation of inactive-plate metadata.
- `ef2a3511e593`, `9c35a8e86ed1`, `5d026b5b201c`, `517c8ba3`: bounds-based object
  picking above 20,000 triangles, without synchronous native raycast index
  construction on touch. Approximate hits cannot place paint, embossing or
  measurements. Paint allocation is guarded in the renderer as well as the UI.
  Explicit simplification uses headroom and reports when the result is still too
  dense. Queued simplification rejects changed model/selection.
- `2576984305a7`: import failure dialog and an English explanation for
  `STL_NO_CONNECTED_FACETS`; bridge validation/linkage/fallback messages are also
  English. This mapping does not make the old native library emit that new code.
- `52a7da1e92df`: safe Kotlin cancellation-exception propagation only. The callback
  remains `onProgress(Float): Unit`, preserving the prebuilt ABI.
- `4c47f8dd`: bundled changelog now displays 0.8.204; every locale entry contains
  the English release notes so this dialog cannot fall back to German.

## Already present / excluded

- The base already releases rebuildable active/inactive GL meshes before loading
  a G-code viewer (`setGCodeViewer`). This safe resource lifecycle is retained.
  `d5c37080b72c` active-mesh decimation uses the new JNI method
  `glmodel_init_from_model_object_preview`; neither that call nor its triangle
  budgets are imported because the shipped library does not implement it.
- `52a7da1e92df` native preparation cancellation checkpoints require a C++ rebuild,
  including the Boolean callback contract. No claim of interruptible native STL
  preparation is made here.
- `e4570724d9d5` consists entirely of C++ changes: normal-scan optimization and
  empty repaired STL rejection remain pending a native rebuild.
- The native error-code emission from `2576984305a7`, and native work such as
  `c2b143db4f12` and `bf1c3707`, remain pending a C++/JNI rebuild and device tests.
- `4c53afa8` adds generated-M12 Android instrumentation using the later UI2
  workspace harness. It contains no production fix. That harness is not imported;
  the 20k policy and bounds geometry have JVM coverage in this branch.
- UI2 shell/navigation/profile-card changes, label revisions, KromaCut editor and
  assets, auto-updater, version bumps and translated strings are excluded.

## Verification boundaries

Run from the repository with JDK 17 and an Android SDK:

```sh
./gradlew test :app:assembleDebug -PusePrebuiltNative=true
python3 -m unittest scripts/test_sync_native_cache.py
python3 scripts/sync_native_cache.py check
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/OrkanSlicer_<commit>.apk
```

Expected signer SHA-256:
`564ed79c9aab18668e919fe1d3018c8cb4baea7795ac575e38e8c6942025c51c`.

JVM coverage includes low-memory policy, preview ranges, ray/box intersection,
20,000/20,001 boundaries, approximate-hit marking, raycaster access without native
initialization, English STL errors, changelog/version alignment and Cockpit
labels. Source wiring assertions cover native-index bypass and paint guard
ordering; they are not Android GL execution tests. Existing resource values,
UI/navigation/layout sources, forced-English bootstrap and native ABI are checked
against the base separately.

No device installation, rendered Android UI comparison, native stress test,
G-code equivalence test or physical printing is claimed. No P20, Monstrinho or
Moonraker access is needed for this package. The PR must remain unmerged.

Local validation: 501 JVM cases per variant (debug, release, sliceTest), zero
failures/errors/skips; 4 Python native-cache cases passed. The pinned cache check
verified all 54 ARM64 libraries. Full command logs and signer evidence are stored
in the task delivery directory alongside the report.
