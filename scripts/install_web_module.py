#!/usr/bin/env python3
from pathlib import Path
import shutil, sys

if len(sys.argv) not in (3,4):
    raise SystemExit("usage: install_web_module.py <Mindustry checkout> <gwt template> [teavm template]")
repo=Path(sys.argv[1]).resolve(); gwt=Path(sys.argv[2]).resolve(); teavm=Path(sys.argv[3]).resolve() if len(sys.argv)==4 else None
settings=repo/"settings.gradle"
if not settings.exists() or not (repo/"core").exists(): raise SystemExit("error: target does not look like a Mindustry checkout")
for name in (["web"] + (["web-teavm"] if teavm else [])):
    if (repo/name).exists(): raise SystemExit(f"error: target already has {name}/; refusing to overwrite it")
text=settings.read_text(encoding="utf-8")
needle="'desktop', 'core', 'server', 'ios', 'annotations', 'tools', 'tests'"
if needle not in text: raise SystemExit("error: settings.gradle does not match pinned layout")
mods=needle + ", 'web'" + (", 'web-teavm'" if teavm else "")
settings.write_text(text.replace(needle,mods,1),encoding="utf-8")
shutil.copytree(gwt,repo/"web")
if teavm: shutil.copytree(teavm,repo/"web-teavm")
print("installed browser modules: :web" + (" + :web-teavm" if teavm else ""))
