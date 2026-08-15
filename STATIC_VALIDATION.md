# Static validation report

This report records checks that were actually executed in the ChatGPT working container.
It is **not** a claim that the current Mindustry v8 TeaVM target has compiled or booted in a real browser yet.

## Passed here

- `python3 -m py_compile scripts/*.py web-teavm/pack_assets.py`
- `bash -n bootstrap.sh`
- `SOURCE_PINS.json` matches the exact pins embedded in `bootstrap.sh`.
- Mindustry pin: `e8bf80a1d2d5e9cf339c2fbc2a82444bf6d779d7`.
- Arc pin: `55553d17bef8bb32362c8038999a19427f508ef0` (the Arc revision requested by that Mindustry checkout).
- Historical Arc browser backend pin: `2303ab81bb76a973db8885f3ba14b6515782a1a4`.
- TeaVM primary compiler version: `0.15.0`.
- TeaVM Gradle plugin service-provider generation is checked by `verifyTeaVMExtensions` before JavaScript generation.
- Mods keep the upstream `ClassLoader` ABI while browser-only patches remove executable JAR/Rhino/worker paths.
- Reflection retains fields + constructors, not every Arc/Mindustry method.
- Official bleeding-edge Build 27630 reference metadata matches the pinned Mindustry SHA and includes expected size/SHA-256.
- `bootstrap.sh` ends by invoking `./gradlew web-teavm:distWeb --stacktrace`; it does not stop at scaffold generation.
- All TeaVM substitution policy entries have matching replacement source files (6/6).
- The host HTML contains the required `assetpack.bin` preload, `window.__mindustryAssets` population, game canvas, and `mindustryMain()` invocation.
- `pack_assets.py` passed a binary round-trip smoke test with nested text and non-text byte files.
- Browser graphics keep Arc logical dimensions separate from the DPR-scaled WebGL backbuffer.
- WebGL viewport updates to the physical backbuffer size.
- Browser input uses logical canvas coordinates rather than DPR-scaled framebuffer coordinates.
- WASD and A-Z use explicit current-Arc `KeyCode` mappings; no invalid enum-ordinal arithmetic remains.
- `scripts/validate_kit.py` currently passes **70/70** offline structural/regression checks.
- Browser staging explicitly depends on `:core:classes` and Mindustry's real `:tools:pack` task.
- The staged build is rejected unless `sprites/sprites.aatls`, `sprites/fallback/sprites.aatls`, `version.properties`, `locales`, `basepartnames`, and `icons/icons.properties` exist and are non-empty.
- TeaVM's `teavm-extension-annotation-processor:0.15.0` is explicitly present on the annotation-processor path.
- Packaged directory existence is prefix-aware, so virtual directories such as `sprites/` and `bundles/` behave like real `Fi` directories.
- Browser text input, touch-mobile layout detection, import chooser, and Blob-download export bridges are present and wired through current Arc/Mindustry APIs.
- Browser VFS storage uses versioned Base64 and retains legacy hex decode compatibility.
- Raw `music/**` and `sounds/**` are omitted from the first-boot pack only while `Audio(false)` is active; the source checkout retains the real licensed files for the later WebAudio milestone.

## Not verified in this container

The local working container has no outbound GitHub/Maven DNS access, so these acceptance gates remain open:

- Gradle dependency resolution succeeds.
- `:core:classes` succeeds against the pinned sources.
- `web-teavm:generateJavaScript` succeeds with zero TeaVM errors.
- `web-teavm:distWeb` produces a complete distribution.
- Chromium/Safari reaches the real current Mindustry loading screen and main menu.
- A real vanilla map starts and runs AI/pathfinding/fog/physics without runtime errors.
- Keyboard, mouse, touch, save/load, and editor smoke tests pass in-browser.

Do not label this port finished until those runtime gates are observed.

- TeaVM substitution policy does **not** replace `mindustry.mod.DataPatcher`; the primary route retains current v8 data/chunk patch code behind the reflection policy.
