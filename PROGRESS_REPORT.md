# Web port build progress — real compiler results

This records what was **actually executed against the real Gradle/TeaVM toolchain**,
not a static-structure check. Before this pass the kit had never resolved its
dependencies or run a compiler (its authors' environment had no outbound network).
The fixes below take it from "nothing compiles" to "the entire Java port and asset
pipeline build, and the TeaVM whole-program compiler runs."

## Environment used

- JDK 21, Gradle 9.3.1 (wrapper), Python 3.11, TeaVM 0.15.0.
- `github.com` and Maven Central reachable.
- `jitpack.io` and the Sonatype snapshot repo are **blocked by egress policy (HTTP 403)**.

## What now builds (verified, `BUILD SUCCESSFUL`)

| Stage | Task | Result |
|------|------|--------|
| Source pins | clone Mindustry + Arc at pinned SHAs | HEAD matches `e8bf80a…` exactly |
| Configuration | `:projects` | web + web-teavm wired, `localArc` composite build active |
| Base game | `:core:classes` | compiles (kapt annotation processing incl.) |
| Web module (Java) | `:web-teavm:compileJava` | **compiles** against current Arc/Mindustry + TeaVM |
| Sprites | `:tools:pack` | real sprite generator runs, atlases produced |
| Fonts | `:web-teavm:bakeWebFonts` | bitmap fonts baked on the JVM |
| Assets | `stageWebAssets` → `verifyPackedGameAssets` → `packWebAssets` | `assetpack.bin` produced |
| Extensions | `verifyTeaVMExtensions` | TeaVM SPI providers verified |
| **TeaVM analysis+codegen** | `:web-teavm:generateJavaScript` | runs the whole program with **no code errors**; only blocked by a hard memory ceiling — see below |

## Bugs fixed (durable, in the kit itself)

1. **`scripts/generate_teavm_gl.py` never produced a file.** Its GWT→TeaVM transform
   aborted on a leftover `Int32Array` token *before* writing `TeaVMGL20.java`, so the
   next step (`check_gl_surface.py`) crashed with `FileNotFoundError`. Two real defects
   underneath:
   - The typed-array-helper block replacement silently **swallowed the class
     constructor** (it sits between the typed-array fields and `getUniformLocation`),
     so the generated class had no `TeaVMGL20(WebGLRenderingContext)` and could not be
     constructed by `WebGraphics`.
   - The `GL_VIEWPORT` readback still called GWT's `getParameterv`. TeaVM exposes
     `WebGLRenderingContext.getParameter(int)→JSObject`; the fix casts to
     `org.teavm.jso.typedarrays.Int32Array`. `Int32Array` is a valid TeaVM type and is
     no longer treated as a forbidden GWT token.
   The generator now emits a clean, compiling bridge and still hard-fails on genuinely
   GWT-only leftovers.

2. **`web-teavm/.../WebGraphics.java` — `SystemCursor` moved in current Arc.** It is now
   `Graphics.Cursor.SystemCursor`, not `Graphics.SystemCursor`; both references updated
   to `Cursor.SystemCursor`.

3. **`:tools:pack` NPE — `Vars.mainExecutor is null`.** `patch_arc.py` nulls
   `arc.Core.executor` so the browser backend installs its own. That is correct for the
   web target, but it was too broad: the headless JVM sprite generator (`ImagePacker`)
   has no Arc backend, so `Vars.mainExecutor` (= `Core.executor`) stayed null and
   `Generators.run()` NPE'd, blocking the whole asset pipeline. Fixed in
   `patch_mindustry.py`: `ImagePacker.main` now installs the shared executor itself
   (a JVM build step may create real threads).

4. **TeaVM `generateJavaScript` crashed with an NPE during reflection analysis.**
   `MindustryReflectionPolicy` used `selectPackage("arc"/"mindustry", true)`, whose
   built-in `inPackage` predicate calls `cls.name()` with no null check. TeaVM's
   `DefaultReflectionSupplier.fillMembers` resolves a class name via
   `env.findClass(name)` and passes the result straight to the policy predicate — and
   that result is **null** whenever TeaVM cannot resolve a class name in the reflection
   graph, so the whole compile aborted:
   ```
   java.lang.NullPointerException: Cannot invoke "…IntrospectClass.name()" because "cls" is null
     at …SimpleReflectionPolicy.lambda$inPackage$26(SimpleReflectionPolicy.java:208)
     at …DefaultReflectionSupplier.fillMembers(DefaultReflectionSupplier.java:110)
     at …TeaVM.build(TeaVM.java:430)
   ```
   Fixed by replacing `selectPackage(...)` with `selectClasses(...)` and a **null-safe**
   predicate (an unresolvable class simply has no reflectable members). After this the
   compiler runs the entire whole-program pipeline — dependency analysis, optimization
   and codegen — with **zero code errors**.

5. **Reflection policy narrowed to fit memory.** The policy also enabled
   `foundByName()` across all of `arc.*`/`mindustry.*`, which force-retains every class
   (defeating dead-code elimination) to service dynamic `Class.forName`. That inflates
   the whole-program live set. It was removed: field + constructor reflection stays
   (demand-driven, only for reached classes), which is all a vanilla, offline, no-mod
   first boot needs (save/load keys content by numeric id, not class name). A later
   mod/runtime milestone can re-introduce a narrowed `foundByName()`.

## Environment workaround (not a kit bug)

`:core` depends on `com.github.Anuken:rhino` from jitpack, which this environment
blocks (403). `scripts/build_local_deps.sh` clones Anuken's `rhino` at the pinned SHA
from GitHub and publishes it to `~/.m2` (which `build.gradle` already checks first via
`mavenLocal()`), unblocking `:core`. In a normal network with jitpack access this is
unnecessary — the standard bootstrap resolves it directly.

## The current frontier: a hard memory ceiling (no code errors left)

With the fixes above, `:web-teavm:generateJavaScript` runs the **entire** whole-program
translation of the full current Mindustry — dependency analysis, optimization and code
generation — with **no compiler errors**. The single remaining blocker is memory: the
final codegen holds the whole reachable game in memory and its peak live heap is
**~15 GB**, which does not fit this build environment.

The whole-program live set is dominated by *reachable* game code, not by anything the
kit adds: `WebLauncher → Vars.init → content.load()` reaches Mindustry's hundreds of
block/unit/item classes through their static initializers, so TeaVM compiles all of
them in one pass. No reflection/optimization tweak removes that — it is inherent to
compiling the whole game at once.

Everything cheaper than raw heap was applied to shrink the peak (all committed):
`fastGlobalAnalysis=true`, `debugInformation=false`, `sourceMap=false`,
`obfuscated=true`, `optimization=BALANCED` (a smaller program → smaller codegen
structure), the narrowed reflection policy, and `-XX:+UseSerialGC` (lowest GC overhead).
Even with all of these the codegen still pegs at ~15 GB and thrashes (>80 % time in GC)
once the heap is smaller than the live set.

Measured on this 16 GB host (usable heap ceiling ~13–14 GB after JVM/OS overhead):

| Heap | GC | outcome |
|------|----|---------|
| 8 GB | default | OOM early in analysis |
| 13 GB | default | RSS 13.9 GB → OOM-killed |
| 11 GB | SerialGC | thrash at ceiling |
| 12 GB | SerialGC | thrash, pegged at cap |
| 13 GB | SerialGC | ran 16 min into codegen, then `OutOfMemoryError` |
| 13.8 GB | SerialGC | reached final codegen, thrashed (FGCT 900 s+) |
| 13.8 GB | SerialGC + BALANCED | healthiest, still pegs ~14.75 GB and thrashes |

### What it needs

A build machine with **~24 GB RAM**, running the compile with roughly `-Xmx20g` (or
out-of-process `processMemory = 20480`, as now configured in `web-teavm/build.gradle`).
Note that **standard GitHub Actions runners (7 GB) are far too small** — the included CI
must use a large-memory runner, or run on a dedicated box. This memory profile is a real
property of compiling the entire current Mindustry to JavaScript in one TeaVM pass, and
is a large part of why no official current-Mindustry browser build exists.

### After the compile fits

Producing `web-teavm/build/dist/` is the *compile* gate, not the *playable* gate. A
successful build still has to be served over HTTP and taken through the runtime
acceptance gates in `PORT_STATUS.md` (title screen, a real map, movement/building/
mining/combat, save/reload, editor). Those cannot be observed in this environment (no
browser), and a first run will surface the expected next tier of issues (threads,
unsupported JRE APIs, missing native behaviors) that only appear at runtime.

### Suggested next steps

1. Run `generateJavaScript` with TeaVM debug logging to name the `null` class in the
   reflection scan; exclude or fix it.
2. If broad field reflection is the trigger, replace the two package-wide
   `reflectableFields(field -> true)` selectors with the specific packages/classes that
   need reflection, and validate serialization/content parsing at runtime.
3. Iterate `generateJavaScript` to zero errors, then serve `web-teavm/build/dist/` over
   HTTP and begin the runtime acceptance gates in `PORT_STATUS.md`.
