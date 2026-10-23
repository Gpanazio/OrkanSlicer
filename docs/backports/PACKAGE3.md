# Package 3 on the English 0.8.204 prebuilt-native base

Base: `7d0eb23614533fcbdf88bb7c32646f5c62e04ba9` (`brick/backport-memory-dense-mesh`).
Package 1 and Package 2 remain ancestors. Version 0.8.204/code 239, application ID,
forced English locale, existing UI shell and native binaries remain unchanged.

## Selected upstream changes

- `f5798c5a7b0b`: independent bed-surface temperatures, legacy backfill only when
  absent, `curr_bed_type` selection and active-surface pre-slice summary. Existing
  204 catalogs receive only the required setting keys. Skip native surface
  defaults before legacy backfill so a default zero cannot masquerade as an
  explicit imported temperature. Explicit zero is preserved.
- `16d72235f11c`: selected own filament profiles retain PA and enable flags over
  temporary project settings; positive PA edits enable PA. Preparation reports
  actual Klipper/Marlin commands, distinguishing filament overrides from custom
  commands. Comments, including inline comments, cannot supply executable PA.
- `579326ae93a8`: expose bridge width and relative angle in the existing process
  editor, coupled mm/percent conversion, nozzle-width maximum and explicit zero
  (automatic) choice. Keep the existing English labels and category structure.
- `43166eb7`: adapt the config/conversion and executable-PA scenarios to JVM
  tests. Its device-native bridge-role/G-code slicing assertions were not run.
- `9e247432941e`: explicit object filament assignment uses the existing uniform
  native assignment, replacing imported volume/paint choices rather than only
  changing the object base extruder.
- `d29131f84861`: adapt the hostile project endpoint/API-key exclusion test.
  This verifies the existing 3MF reader filtering; no Compose password editor is
  imported.
- `cd4f054fe10e`: canvas click receives copied coordinates and runs on GL.
- `b0931f4a2dea`, `6f24b0f6`, `7804f943`, `2a401883`: adapt orientation, lay-on-face,
  position, rotation, scale and paint selection to the existing 204 handlers.
  Native geometry reads and preview mutations happen on GL; Compose state is
  published on main with load-generation guards. Editor snapshots refresh after
  undo/model replacement. No later tool panel or multi-selection transform UI.
- `4251b3620b55`, `39055276690b`: seed history after each GL import commit, record
  committed scale changes, reset actual geometry scale and seat on bed. History
  owns bounded snapshots, captures on GL at commit time, and handles empty
  workspace undo/redo. Pure JVM tests exercise ownership and history semantics;
  source checks verify integration with native scale and import callbacks.
- `5c3c4f59`, `97823c27`, `957c88d6`: bind existing object, context, simplify and
  paint actions to model identity, revision, count and selection. Recheck on GL
  before mutation, including deferred move-to-plate work. No later ModelMenuItems
  or workspace panels are imported.

## Exclusions and ABI boundaries

- `dba2eaac6dcf` independent printer/process/filament import choices change the
  import dialog and recipe. They are excluded. MainActivity and the workspace
  profile sheet remain byte-for-byte identical to the Package 2 base.
- `b65895454b87` committed rotation reset requires JNI `model_reset_rotation`.
  It is absent from the shipped 0.8.204 library. The existing legacy preview reset
  remains; this package does not claim to reset committed native rotation.
- The later mirror path in `7804f943` requires JNI `model_mirror`, also absent.
  No mirror command, declaration or replacement UI is added. Existing mirrored
  imports remain supported; scale no longer feeds mirror signs into the
  positive-only absolute-scale API.
- No C++ or JNI declarations change. No UI2 migration, translations, KromaCut,
  updater, version bump or printer/device deployment is included.

## Verification boundaries

Run `./gradlew test :app:assembleDebug -PusePrebuiltNative=true` and
`python3 -m unittest scripts/test_sync_native_cache.py`. Verify the APK certificate
and every ARM64 native entry against `native-bundle.json`.

The JVM suite covers policies, command parsing, conversions, history ownership
and stale bindings, plus explicitly labeled source wiring checks. It does not
execute Android GLES or the native ARM64 slicer. Static preservation of existing
strings, import/profile routes and JNI does not prove rendered identity, device
behavior, bridge-role G-code or physical print quality. No device or printer is
accessed by this package's verification.
