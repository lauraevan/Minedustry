#!/usr/bin/env python3
"""
Copies Anuken/Arc's last official GWT backend into the isolated Mindustry web module
and performs only deterministic namespace migrations.

This script deliberately does NOT mass-guess changed method names. Remaining API drift
is left for the compiler/audit to expose explicitly.
"""
from pathlib import Path
import shutil, sys, re

if len(sys.argv) != 3:
    raise SystemExit("usage: import_legacy_backend.py <legacy backend src> <web src>")

src = Path(sys.argv[1]).resolve()
dst = Path(sys.argv[2]).resolve()
if not src.exists():
    raise SystemExit(f"error: legacy backend source missing: {src}")
dst.mkdir(parents=True, exist_ok=True)

# Copy the historical tree first, preserving GWT super-source/resources/reflection support.
for item in src.iterdir():
    target = dst / item.name
    if target.exists():
        if item.is_dir():
            shutil.copytree(item, target, dirs_exist_ok=True)
        else:
            shutil.copy2(item, target)
    else:
        if item.is_dir():
            shutil.copytree(item, target)
        else:
            shutil.copy2(item, target)

# Deterministic Java namespace changes.
replacements = [
    ("io.anuke.arc.backends.gwt", "arc.backend.gwt"),
    ("io.anuke.arc.collection.", "arc.struct."),
    ("io.anuke.arc.", "arc."),
    ("ApplicationType.WebGL", "ApplicationType.web"),
    ("arc.graphics.glutils.GLVersion", "arc.graphics.gl.GLVersion"),
    ("Time.nanoTime()", "Time.nanos()"),
    ("gdx.assetpath", "arc.assetpath"),
    ("gdx.assetfilterclass", "arc.assetfilterclass"),
    ("gdx.assetoutputpath", "arc.assetoutputpath"),
    ("gdx.files.classpath", "arc.files.classpath"),
    ("FileType.Internal", "FileType.internal"),
    ("FileType.Classpath", "FileType.classpath"),
    ("FileType.External", "FileType.external"),
    ("FileType.Absolute", "FileType.absolute"),
    ("FileType.Local", "FileType.local"),
]

for p in dst.rglob("*"):
    if p.is_file() and (p.suffix in {".java", ".xml", ".gwt"} or p.name.endswith(".gwt.xml")):
        try:
            text = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        old = text
        for a, b in replacements:
            text = text.replace(a, b)
        # Old Arc collection Array became Seq.
        text = text.replace("import arc.struct.Array;", "import arc.struct.Seq;")
        text = re.sub(r"\bArray<", "Seq<", text)
        text = re.sub(r"\bnew Array<", "new Seq<", text)
        text = re.sub(r"\bnew Array\(\)", "new Seq()", text)
        text = text.replace("import arc.files.FileHandle;", "import arc.files.Fi;")
        text = re.sub(r"\bFileHandle\b", "Fi", text)
        if text != old:
            p.write_text(text, encoding="utf-8")

# Move the main package tree to match current Arc naming.
old_arc = dst / "io" / "anuke" / "arc"
new_arc = dst / "arc"
if old_arc.exists():
    new_arc.mkdir(parents=True, exist_ok=True)
    shutil.copytree(old_arc, new_arc, dirs_exist_ok=True)
    shutil.rmtree(dst / "io" / "anuke" / "arc")

old_backend = new_arc / "backends" / "gwt"
new_backend = new_arc / "backend" / "gwt"
if old_backend.exists():
    new_backend.parent.mkdir(parents=True, exist_ok=True)
    if new_backend.exists():
        shutil.copytree(old_backend, new_backend, dirs_exist_ok=True)
        shutil.rmtree(old_backend)
    else:
        shutil.move(str(old_backend), str(new_backend))

# Fix super-source's package path after namespace migration.
emu_old = new_backend / "emu" / "io" / "anuke" / "arc"
emu_new = new_backend / "emu" / "arc"
if emu_old.exists():
    emu_new.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(emu_old, emu_new, dirs_exist_ok=True)
    shutil.rmtree(new_backend / "emu" / "io" / "anuke" / "arc")

# Remove the obsolete module descriptor; the kit installs a modern descriptor.
for candidate in [
    new_arc / "backends" / "arc_backends_gwt.gwt.xml",
    new_arc / "backend" / "arc_backends_gwt.gwt.xml",
]:
    if candidate.exists():
        candidate.unlink()

# Old SoundManager2/Flash audio is not the target. Current boot starts muted.
for name in ["GwtAudio.java", "GwtMusic.java", "GwtSound.java", "GwtNet.java"]:
    p = new_backend / name
    if p.exists():
        p.rename(p.with_suffix(".java.disabled"))


# Install hand-migrated current-Arc overrides after the historical tree is copied.
overrides = Path(__file__).resolve().parents[1] / "web" / "backend-overrides"
if overrides.exists():
    shutil.copytree(overrides, dst, dirs_exist_ok=True)
    print("current-Arc backend overrides installed")

print("legacy backend imported; deterministic namespace migration complete")
