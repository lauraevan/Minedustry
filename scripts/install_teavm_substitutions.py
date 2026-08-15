#!/usr/bin/env python3
"""Copy browser-safe GWT super-source replacements into TeaVM substitution packages.

TeaVM's SubstitutionPolicy requires replacement classes under a different package.
This script keeps one canonical replacement source in the kit and relocates it
without changing its public API.
"""
from pathlib import Path
import re, shutil, sys

if len(sys.argv) != 3:
    raise SystemExit("usage: install_teavm_substitutions.py <kit-root> <Mindustry/web-teavm>")
kit=Path(sys.argv[1]).resolve(); module=Path(sys.argv[2]).resolve()
outroot=module/"src/main/java/mindustry/web/teavm/substitute"

entries=[
 (kit/"web/backend-overrides/arc/backend/gwt/emu/arc/util/OS.java", "arc.util"),
 (kit/"web/backend-overrides/arc/backend/gwt/emu/arc/audio/Soloud.java", "arc.audio"),
 (kit/"web/backend-overrides/arc/backend/gwt/emu/arc/audio/Music.java", "arc.audio"),
 (kit/"web/src/websuper/mindustry/ui/Fonts.java", "mindustry.ui"),
 (kit/"web/src/websuper/mindustry/mod/Scripts.java", "mindustry.mod"),
]
for src,pkg in entries:
    if not src.exists(): raise SystemExit(f"missing canonical browser replacement: {src}")
    text=src.read_text(encoding="utf-8")
    target_pkg="mindustry.web.teavm.substitute."+pkg
    text,n=re.subn(r"^package\s+"+re.escape(pkg)+r"\s*;",f"package {target_pkg};",text,count=1,flags=re.M)
    if n != 1: raise SystemExit(f"could not relocate package in {src}")
    # Preserve access to sibling types that were implicit in the original package.
    insert=f"\nimport {pkg}.*;\n"
    p=text.find("\n",text.find("package "))+1
    text=text[:p]+insert+text[p:]
    dst=outroot/Path(*pkg.split('.'))/src.name
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(text,encoding="utf-8")
    print(f"TeaVM substitute: {pkg}.{src.stem} -> {target_pkg}.{src.stem}")
