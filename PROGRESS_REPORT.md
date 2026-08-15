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
| **TeaVM compile** | `:web-teavm:generateJavaScript` | **reached, then fails — see frontier** |

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

## Environment workaround (not a kit bug)

`:core` depends on `com.github.Anuken:rhino` from jitpack, which this environment
blocks (403). `scripts/build_local_deps.sh` clones Anuken's `rhino` at the pinned SHA
from GitHub and publishes it to `~/.m2` (which `build.gradle` already checks first via
`mavenLocal()`), unblocking `:core`. In a normal network with jitpack access this is
unnecessary — the standard bootstrap resolves it directly.

## The current frontier (open)

`:web-teavm:generateJavaScript` — the actual TeaVM Java→JS translation of the whole
game — crashes during reflection dependency analysis:

```
org.teavm.tooling.builder.BuildException: java.lang.NullPointerException:
  Cannot invoke "org.teavm.extension.introspect.IntrospectClass.name()" because "cls" is null
  at org.teavm.extension.spi.reflection.SimpleReflectionPolicy.lambda$inPackage$26(SimpleReflectionPolicy.java:208)
  at org.teavm.extension.spi.reflection.SimpleReflectionPolicy.classAccessibleMembers(SimpleReflectionPolicy.java:59)
  at org.teavm.reflection.DefaultReflectionSupplier.fillMembers(DefaultReflectionSupplier.java:110)
  at org.teavm.reflection.ReflectionDependencyListener.gatherAccessibleFields(...)
  ...
  at org.teavm.vm.TeaVM.build(TeaVM.java:430)
```

This is the first time this codebase has been run through TeaVM at all. The kit's
`MindustryReflectionPolicy` reflects **every field in all of `arc.*` and `mindustry.*`**
(`selectPackage("arc", true).reflectableFields(field -> true)`), and TeaVM's
introspector hits a class it resolves to `null` while walking that graph.

This is a genuine whole-program-translation problem, not a one-line fix: it needs
either narrowing the reflection policy to the classes that actually require reflective
field access (Arc JSON serialization + Mindustry content/`DataPatcher`) without
breaking those at runtime, or identifying and excluding the specific class TeaVM nulls
on (needs a TeaVM debug/verbose run). Expect a long tail of further TeaVM issues after
it (threads, native methods, unsupported JRE APIs) before a browser boot is possible —
this is the part no current-Mindustry web port has solved, which is why offline v8 has
no official browser build.

### Suggested next steps

1. Run `generateJavaScript` with TeaVM debug logging to name the `null` class in the
   reflection scan; exclude or fix it.
2. If broad field reflection is the trigger, replace the two package-wide
   `reflectableFields(field -> true)` selectors with the specific packages/classes that
   need reflection, and validate serialization/content parsing at runtime.
3. Iterate `generateJavaScript` to zero errors, then serve `web-teavm/build/dist/` over
   HTTP and begin the runtime acceptance gates in `PORT_STATUS.md`.
