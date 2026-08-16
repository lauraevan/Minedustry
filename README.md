# Mindustry v8 Web Port Workbench

A reproducible engineering workbench for compiling the **current Mindustry Java codebase** for browsers. This is not Mindustry Classic, an iframe, streaming, or a TypeScript/Pixi remake.

Pinned upstream revisions:

- Mindustry `e8bf80a1d2d5e9cf339c2fbc2a82444bf6d779d7`
- Arc `55553d17bef8bb32362c8038999a19427f508ef0`
- Last Arc revision before Anuken removed the official GWT backend: `2303ab81bb76a973db8885f3ba14b6515782a1a4`
- TeaVM `0.15.0` (primary compiler)
- GWT `2.13.1` (historical compatibility/reference target)

## Architecture

The primary route is **current Java -> TeaVM -> browser JavaScript**. Anuken's historical browser backend is still recovered from Git history, but used mainly as the known-correct reference for Arc's WebGL/input/browser behavior.

Why TeaVM is primary: current Arc/Mindustry use substantially more modern Java/JRE behavior than the old GWT era. TeaVM 0.15 provides modern reflection policy/substitution APIs and browser WebGL APIs that accept Java NIO buffers directly.

The game entry point is still `mindustry.ClientLauncher`. Gameplay/content classes are not rewritten in JavaScript.

## What bootstrap does

1. Clones exact Mindustry + Arc revisions as siblings.
2. Uses Mindustry's own `localArc` composite-build mechanism automatically.
3. Applies browser-safe Arc changes to that exact local Arc source.
4. Recovers Anuken's official historical Arc GWT backend.
5. Installs `:web-teavm` (primary) and `:web` (GWT reference/fallback).
6. Generates a TeaVM `GL20` implementation from Anuken's official GWT `GwtGL20` source instead of hand-transcribing it.
7. Uses TeaVM substitutions only for six browser-impossible boundaries: native loading, OS/process helpers, SoLoud/music, runtime FreeType, and Rhino scripts. The real `Platform`, `ContentParser`, `DataPatcher`, and `MapObjectivesDialog` classes remain in the game.
8. Keeps current Arc/Mindustry field + constructor reflection available through a TeaVM reflection policy while avoiding blanket reflective retention of every method.
9. Runs Mindustry's real `:core:classes` generation and `:tools:pack` sprite pipeline before browser asset staging; missing atlases/generated metadata are hard build failures.
10. Bakes current Mindustry fonts on the JVM at build time and loads bitmap fonts in-browser under Mindustry's original logical asset keys.
11. Packs the real generated `core/assets` into a deterministic browser asset pack so synchronous `Fi` reads still work during startup. Raw `music/**` and `sounds/**` are omitted only while the first milestone uses `Audio(false)`.
12. Uses a versioned Base64 localStorage byte VFS for settings/small saves, with migration from the earlier hex format.
13. Provides touch-mobile layout detection, browser text input, and real browser import/export downloads through Mindustry's existing platform APIs.
14. Runs `web-teavm:distWeb` as the default hard compile gate.

## Run

Requirements: network access, Git, Python 3, and JDK 17+.

```bash
unzip mindustry-web-port-kit.zip
cd mindustry-web-port-kit
./bootstrap.sh ./work
```

In a sandboxed/CI network where `jitpack.io` is blocked (Gradle reports HTTP 403 for
`com.github.Anuken:rhino`), first run `scripts/build_local_deps.sh ./work` to build that
dependency from GitHub source into your local Maven repo, then run `bootstrap.sh`.

**Build machine RAM:** the TeaVM whole-program compile of the full game peaks at ~15 GB
of live heap. Use a machine with **~24 GB RAM** and give the compile ~20 GB (the kit sets
TeaVM `processMemory = 20480`). Standard 7 GB CI runners are too small. See
`PROGRESS_REPORT.md` for the full list of verified build stages, all the fixes applied,
and the measured memory profile.

If the compile gate succeeds, output is:

```text
work/Mindustry/web-teavm/build/dist/
```

Serve that directory over HTTP. Do not use `file://` as the browser acceptance test.

## What does not count as "finished"

This kit deliberately does **not** call itself a playable v8 release merely because the build files exist. A real release requires observed browser tests: title screen, a real map, movement/building/mining/combat, saves, editor, and soak tests.

Current first milestone intentionally has:

- offline play only; browsers cannot directly open Mindustry's normal raw TCP/UDP transports,
- Java/JAR mod execution disabled; JVM class loading is not a browser primitive,
- audio muted behind an API-compatible browser substitution until WebAudio is implemented,
- current Mindustry **data/chunk patching is retained** on the TeaVM route and will be validated by the real compiler/runtime; the historical GWT fallback still uses a reduced patcher.

## Exact official build reference

`SOURCE_PINS.json` records Anuken/MindustryBuilds bleeding-edge Build 27630 as a bytecode/reference build for the same pinned Mindustry commit. `scripts/fetch_exact_be.py` can fetch it in a normal networked environment and verifies both byte size and SHA-256 before accepting it. The normal bootstrap continues to build from source.

A ready-to-use GitHub Actions workflow is included at `.github/workflows/web-port.yml`; when this kit is placed in a writable GitHub repository, it runs the exact bootstrap and uploads the browser distribution only after the real TeaVM compile succeeds.

## Asset authorization and licensing

The project owner supplied correspondence approving use of Mindustry assets and chunk data for this web-port project subject to GPLv3 compliance and attribution; see `ASSET_AUTHORIZATION.md`. The private correspondence itself is not bundled.

Mindustry is GPL-3.0; keep its license/source availability when distributing modified builds. Arc is Apache-2.0. Historical Arc source recovered by this workbench retains its original notices.
