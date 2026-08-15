#!/usr/bin/env python3
"""Fetch Anuken's exact bleeding-edge desktop jar matching SOURCE_PINS.json.

Optional debugging/reference helper. The normal bootstrap still builds from source.
"""
from pathlib import Path
import hashlib, json, sys, urllib.request

if len(sys.argv) not in (2, 3):
    raise SystemExit("usage: fetch_exact_be.py <kit-root> [output.jar]")
root = Path(sys.argv[1]).resolve()
pins = json.loads((root / "SOURCE_PINS.json").read_text(encoding="utf-8"))
ref = pins["official_bleeding_edge_reference"]
out = Path(sys.argv[2]).resolve() if len(sys.argv) == 3 else root / ref["desktop_asset"]
url = f"https://github.com/{ref['repository']}/releases/download/{ref['build']}/{ref['desktop_asset']}"
print(f"fetching exact official build {ref['build']} -> {out}")
h = hashlib.sha256(); total = 0
with urllib.request.urlopen(url, timeout=60) as r, out.open("wb") as f:
    while True:
        block = r.read(1024 * 1024)
        if not block:
            break
        f.write(block); h.update(block); total += len(block)
if total != ref["size_bytes"]:
    out.unlink(missing_ok=True)
    raise SystemExit(f"size mismatch: expected {ref['size_bytes']}, got {total}")
actual = h.hexdigest()
if actual.lower() != ref["sha256"].lower():
    out.unlink(missing_ok=True)
    raise SystemExit(f"sha256 mismatch: expected {ref['sha256']}, got {actual}")
print(f"verified sha256 {actual}")
