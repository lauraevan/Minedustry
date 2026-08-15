# Port status

## Implemented in the workbench

- [x] Exact current Mindustry + Arc source pins.
- [x] Mindustry's native `localArc` composite-build path used for patched Arc.
- [x] TeaVM 0.15 primary browser target.
- [x] Official historical Arc browser backend recovered as source reference.
- [x] TeaVM WebGL bridge generated from Anuken's official `GwtGL20` implementation.
- [x] Current Arc `Application`, `Graphics`, `Input`, `Files`, settings and asset boundaries mapped to browser implementations.
- [x] Browser input feeds current Arc `InputEventQueue`/`KeyboardDevice` path.
- [x] Browser asset pack + synchronous internal `Fi` reads.
- [x] localStorage-backed writable VFS for settings/small saves.
- [x] Current Arc native loader disabled so Pixmap uses its pure-Java PNG path.
- [x] Native SoLoud removed from browser translation graph (first milestone muted).
- [x] Runtime FreeType replaced by build-time JVM font baking.
- [x] Main-thread sleep disabled on web.
- [x] Mindustry async/pathfinder/fog worker-thread paths serialized for first browser milestone.
- [x] Java/JAR mod/Rhino execution isolated from browser target.
- [x] Offline `NetProvider` (no fake raw-socket multiplayer).
- [x] TeaVM reflection policy narrowed to fields + constructors (broad class-name lookup retained for first runtime pass).
- [x] TeaVM substitution policy reduced to 6 native/JVM-only boundaries; real Platform/ContentParser/MapObjectivesDialog retained.
- [x] TeaVM service-provider generation is a hard pre-compile verification gate.
- [x] Exact official bleeding-edge Build 27630 reference recorded for the same pinned Mindustry commit.
- [x] User-supplied authorization for real assets/chunk data recorded without bundling private correspondence.
- [x] TeaVM route now retains current Mindustry `DataPatcher` instead of replacing it with a no-op/throwing browser shell; real patch behavior still awaits compiler/runtime validation.
- [x] Ready-to-run GitHub Actions compiler workflow included for a writable repo.
- [x] Default bootstrap ends by running a real `web-teavm:distWeb` compile.
- [x] Real Mindustry `:tools:pack` sprite generation is required before browser staging; normal + fallback `.aatls` files are hard-gated.
- [x] `:core:classes` generated `version.properties`, `locales`, and `basepartnames` are hard-gated before packing.
- [x] TeaVM `@Autoregistered` annotation processor is explicitly wired and generated SPI files are verified before JavaScript compilation.
- [x] Packaged virtual directories report `exists()==true` when child assets are present.
- [x] Touch-mobile browser detection selects Mindustry's mobile UI while keeping `ApplicationType.web`.
- [x] Browser text input bridge implemented for naming/search/chat-style text flows.
- [x] Browser file chooser supports real imports and Blob-download exports through Mindustry's existing `FileChooser` API.
- [x] TeaVM writable VFS uses versioned Base64 storage with legacy hex readback.
- [x] Muted first milestone omits raw audio payloads from the monolithic boot pack and skips audio-path probes while `Audio(false)` is active.

## Not yet truthfully proven in this ChatGPT environment

The local execution environment available during construction has no outbound DNS, so it cannot clone Maven/GitHub dependencies and run the actual Gradle/TeaVM compiler. GitHub access here is read-only through a connector. Therefore **zero-error TeaVM compilation and browser runtime acceptance have not been observed yet**.

## Acceptance gates for “real current Mindustry is playable in-browser”

- [ ] `web-teavm:distWeb` reaches zero compiler errors.
- [ ] Generated host page reaches real Mindustry title screen.
- [ ] Sandbox/campaign map launches.
- [ ] Mouse + keyboard movement, building, mining and combat work.
- [ ] Touch controls work on iPad/iPhone.
- [ ] Save -> reload works.
- [ ] Editor opens, edits and imports/exports maps.
- [ ] 30-minute map soak test shows no runaway memory growth.
- [ ] Chromium passes.
- [ ] Safari/iPadOS passes.
- [ ] Firefox passes.
- [ ] WebAudio implementation passes pause/resume/background-tab tests.
- [ ] Optional WebSocket relay passes multiplayer protocol tests.
- [ ] GPL source/credits are present beside any deployed build.
