#!/usr/bin/env python3
"""Offline validation for the Mindustry web-port kit itself.

This does not replace the Gradle/TeaVM compiler gate. It catches drift and
known structural regressions before a networked build is attempted.
"""
from pathlib import Path
import json
import re
import struct
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
failures = []
passes = []


def check(name, condition, detail=""):
    (passes if condition else failures).append((name, detail))
    print(f"[{'PASS' if condition else 'FAIL'}] {name}" + (f" — {detail}" if detail else ""))


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


pins = json.loads(read("SOURCE_PINS.json"))
bootstrap = read("bootstrap.sh")
check("Mindustry source pin", pins["mindustry"]["sha"] in bootstrap, pins["mindustry"]["sha"])
check("Arc source pin", pins["arc"]["sha"] in bootstrap, pins["arc"]["sha"])
check("Historical Arc GWT pin", pins["arc"]["last_revision_with_gwt_backend"] in bootstrap,
      pins["arc"]["last_revision_with_gwt_backend"])
check("TeaVM version pin", f'id "org.teavm" version "{pins["teavm"]}"' in read("web-teavm/build.gradle"), pins["teavm"])

be = pins["official_bleeding_edge_reference"]
check("Exact BE reference build", be["build"] == 27630 and be["mindustry_sha"] == pins["mindustry"]["sha"],
      f'build {be["build"]}')
check("Exact BE reference digest", len(be["sha256"]) == 64 and be["desktop_asset"].endswith(".jar"), be["sha256"])

policy = read("web-teavm/src/main/java/mindustry/web/teavm/compiler/MindustrySubstitutionPolicy.java")
subs = re.findall(r'replace\(sink, "([^"]+)"', policy)
expected_subs = {
    "arc.util.ArcNativesLoader",
    "arc.util.OS",
    "arc.audio.Soloud",
    "arc.audio.Music",
    "mindustry.ui.Fonts",
    "mindustry.mod.Scripts",
}
check("TeaVM substitution set", set(subs) == expected_subs and len(subs) == 6,
      f"{len(subs)} browser boundaries")
check("Real DataPatcher retained",
      "mindustry.mod.DataPatcher" not in policy
      and not (ROOT / "web-teavm/src/main/java/mindustry/web/teavm/substitute/mindustry/mod/DataPatcher.java").exists(),
      "TeaVM route uses pinned Mindustry implementation")
check("TeaVM substitution installer agrees", "DataPatcher.java" not in read("scripts/install_teavm_substitutions.py"))

reflection = read("web-teavm/src/main/java/mindustry/web/teavm/compiler/MindustryReflectionPolicy.java")
check("Arc reflective fields", 'selectPackage("arc", true)' in reflection and '.reflectableFields(field -> true)' in reflection)
check("Mindustry reflective fields", 'selectPackage("mindustry", true)' in reflection and reflection.count('.reflectableFields(field -> true)') >= 2)
check("Reflective constructors", reflection.count('.reflectableMethods(method -> method.isConstructor())') >= 2)
check("Class-by-name retention", reflection.count('.foundByName()') >= 2)

html = read("web-teavm/webapp/index.html")
for token in ("assetpack.bin", "window.__mindustryAssets", "mindustryMain()", 'id="game"'):
    check(f"Host boot contract: {token}", token in html)

fonts = read("web-teavm/src/main/java/mindustry/web/teavm/substitute/mindustry/ui/Fonts.java")
for key in ("default", "outline", "monospace", "icon", "iconLarge", "logic", "tech"):
    check(f"Logical font asset key: {key}", f'Core.assets.load("{key}", Font.class' in fonts)
check("Baked font resolver installed", "new FontLoader(Fonts::resolveWebFont)" in fonts)
check("Old wrong default font key absent", 'Core.assets.load("webfonts/default.fnt"' not in fonts)

font_baker = read("web-teavm/fonttools/mindustry/web/tools/WebFontBaker.java")
check("Font baker writes PNG pages", "PixmapIO.writePng" in font_baker)
check("Font baker writes BMFont", 'dir.child(name + ".fnt").writeString' in font_baker)
check("Font baker uses current page dimensions", "packer.getPageWidth()" in font_baker and "packer.getPageHeight()" in font_baker)

assets = read("web-teavm/src/main/java/mindustry/web/teavm/WebAssets.java")
check("TeaVM-safe JS asset enumeration", "JSArray<JSString>" in assets and "JSString" in assets)
check("Packaged directories count as existing",
      'return has(p)||hasPrefix(p+"/");' in assets and 'if(p.isEmpty())return true;' in assets)

webapp = read("web-teavm/src/main/java/mindustry/web/teavm/WebApplication.java")
check("Web application type preserved", "ApplicationType.web" in webapp)
check("Touch-mobile browser layout detection", "@Override public boolean isMobile(){ return mobileBrowser(); }" in webapp
      and "navigator.maxTouchPoints" in webapp)

webfi = read("web-teavm/src/main/java/mindustry/web/teavm/WebFi.java")
check("Browser save move stays in VFS", "public void moveTo(Fi dest)" in webfi and "copyTo(dest);" in webfi)
check("Repeated save flush stores full buffer", "private void store()" in webfi and "WebVfs.write(path, toByteArray());" in webfi
      and webfi.count("store();") >= 2)
check("Memory mapping fails explicitly", "Memory-mapped files are unavailable in browsers" in webfi)

vfs = read("web-teavm/src/main/java/mindustry/web/teavm/WebVfs.java")
check("Compact versioned VFS encoding", 'return "b64:" + Base64.getEncoder().encodeToString(bytes);' in vfs
      and 'if(text.startsWith("b64:"))' in vfs)
check("Legacy hex VFS migration", "Legacy pre-Base64 web-port format" in vfs and "Character.digit" in vfs)

webgraphics = read("web-teavm/src/main/java/mindustry/web/teavm/WebGraphics.java")
check("Logical/backbuffer dimensions separated", "getBackBufferWidth()" in webgraphics and "getBackBufferHeight()" in webgraphics)
check("Physical viewport resize", re.search(r"gl20\.glViewport\(0\s*,\s*0\s*,\s*physicalW\s*,\s*physicalH\)", webgraphics) is not None)
glgen = read("scripts/generate_teavm_gl.py")
check("GL generator rejects leftover GWT typed arrays",
      all(token in glgen for token in ("Float32Array", "Int32Array", "Int16Array", "Uint8ArrayNative")))

webinput = read("web-teavm/src/main/java/mindustry/web/teavm/WebInput.java")
for code, key in ((65, "a"), (68, "d"), (83, "s"), (87, "w")):
    check(f"Explicit key map {code}->{key}", re.search(rf"case\s+{code}\s*:\s*return\s+KeyCode\.{key}\s*;", webinput) is not None)
check("No ordinal alphabet key mapping", "KeyCode.values()[" not in webinput)
check("Browser text-input bridge", "@Override public void getTextInput(TextInput input)" in webinput
      and "window.prompt" in webinput and "isShowingTextInput" in webinput)

chooser = read("web-teavm/src/main/java/mindustry/web/teavm/WebFileChooser.java")
launcher = read("web-teavm/src/main/java/mindustry/web/teavm/WebLauncher.java")
check("Browser import file chooser", "input.type='file'" in chooser and "file.arrayBuffer()" in chooser
      and "params.handleChooseResult(imported)" in chooser)
check("Browser export download Fi", "class DownloadFi extends WebFi" in chooser and "new Blob([bytes]" in chooser
      and "download(downloadName, jsBytes)" in chooser)
check("Platform chooser wired", "showFileChooser(FileChooserParams params)" in launcher and "WebFileChooser.show(params)" in launcher)

patcher = read("scripts/patch_mindustry.py")
for label, token in (
    ("Vars browser platform pruning", "avoids default ArcNet platform in TeaVM graph"),
    ("Vars JVM launch probe pruning", "browser build never launches a child JVM"),
    ("BE updater browser disable", "BeControl"),
    ("NetClient daemon removal", "NetClient streamed asset handler uses browser app queue"),
    ("Ambient audio thread removal", "SoundControl ambient scan moved onto browser game loop"),
    ("Muted audio file probes removed", "muted browser audio skips raw audio asset probes"),
):
    check(label, token in patcher)

build = read("web-teavm/build.gradle")
check("Real Mindustry sprite pack runs before staging",
      'dependsOn("bakeWebFonts", ":core:classes", ":tools:pack")' in build)
check("Packed sprite atlases are hard build gates",
      'tasks.register("verifyPackedGameAssets")' in build
      and 'sprites/sprites.aatls' in build
      and 'sprites/fallback/sprites.aatls' in build
      and 'dependsOn("stageWebAssets", "verifyPackedGameAssets")' in build)
check("Generated Mindustry metadata is hard-gated",
      all(token in build for token in ('version.properties', '"locales"', '"basepartnames"', 'icons/icons.properties')))
check("Muted raw audio omitted from first boot pack",
      'exclude("music/**", "sounds/**")' in build)
check("TeaVM extension annotation processor wired",
      'annotationProcessor "org.teavm:teavm-extension-annotation-processor:0.15.0"' in build)
check("TeaVM service files verified", "verifyTeaVMExtensions" in build and "META-INF/services" in build)
check("Real JavaScript compiler gate", 'tasks.named("generateJavaScript")' in build and 'tasks.register("distWeb"' in build)

workflow = read(".github/workflows/web-port.yml")
check("CI calls real bootstrap/compile", "./bootstrap.sh" in workflow and "web-teavm:distWeb" in bootstrap)
check("CI artifact is browser dist", "web-teavm/build/dist" in workflow)

check("Authorization note present", (ROOT / "ASSET_AUTHORIZATION.md").is_file()
      and "GPLv3" in read("ASSET_AUTHORIZATION.md") and "chunk data" in read("ASSET_AUTHORIZATION.md"))
check("Private authorization screenshots not bundled",
      not any(p.suffix.lower() in {".jpg", ".jpeg", ".png"} and "authorization" in p.name.lower()
              for p in ROOT.rglob("*")))
check("No generated Python cache files bundled",
      not any(p.name == "__pycache__" or p.suffix == ".pyc" for p in ROOT.rglob("*")))

# Exercise the packer with nested text + binary files and independently decode it.
with tempfile.TemporaryDirectory(prefix="mindustry-pack-test-") as td:
    td = Path(td)
    src = td / "src"
    (src / "nested").mkdir(parents=True)
    (src / "hello.txt").write_bytes(b"hello")
    (src / "nested/blob.bin").write_bytes(bytes([0, 1, 2, 255]))
    packed = td / "assetpack.bin"
    proc = subprocess.run([sys.executable, str(ROOT / "web-teavm/pack_assets.py"), str(src), str(packed)],
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    check("Asset packer exits cleanly", proc.returncode == 0, proc.stdout.strip())
    if proc.returncode == 0:
        raw = packed.read_bytes()
        manifest_len = struct.unpack(">I", raw[:4])[0]
        manifest = json.loads(raw[4:4 + manifest_len].decode("utf-8"))
        payload = raw[4 + manifest_len:]
        recovered = {}
        for entry in manifest["files"]:
            start = entry["offset"]
            recovered[entry["path"]] = payload[start:start + entry["length"]]
        check("Asset pack binary round-trip",
              recovered == {"hello.txt": b"hello", "nested/blob.bin": bytes([0, 1, 2, 255])},
              f'{len(recovered)} files')

print(f"\n{len(passes)} checks passed; {len(failures)} failed.")
if failures:
    raise SystemExit(1)
