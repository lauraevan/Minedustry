#!/usr/bin/env python3
"""Small, source-precise Arc changes required by the browser target.

The patch is intentionally conservative and pinned to the exact Arc revision in
SOURCE_PINS.json. This produces a WEB-ONLY Arc checkout: Core.executor creation is
delegated to the browser backend, Settings backup execution is made lazy, and the AssetManager reuses the backend executor instead of creating a JVM worker.
Do not use the patched Arc checkout as a native desktop/mobile backend without
restoring a native executor initializer.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_arc.py <Arc checkout>")

root = Path(sys.argv[1]).resolve()

core = root / "arc-core/src/arc/Core.java"
core_text = core.read_text(encoding="utf-8")
old_core = '    public static ExecutorService executor = Threads.executor("Main Executor", OS.cores);'
new_core = '    public static ExecutorService executor; // installed by the active backend; web cannot create JVM threads'
if old_core not in core_text:
    if new_core not in core_text:
        raise SystemExit("error: pinned Core executor initializer was not found; refusing an unsafe fuzzy patch")
else:
    core_text = core_text.replace(old_core, new_core, 1)
core.write_text(core_text, encoding="utf-8")
print("patched Arc Core: executor creation delegated to backend")

path = root / "arc-core/src/arc/Settings.java"
text = path.read_text(encoding="utf-8")

old_field = '    protected ExecutorService executor = Threads.executor("Settings Backup", 1);'
new_field = '    protected ExecutorService executor; // lazy: browser Settings implementations never need a backup thread'
if old_field not in text:
    if new_field not in text:
        raise SystemExit("error: pinned Settings executor initializer was not found; refusing an unsafe fuzzy patch")
else:
    text = text.replace(old_field, new_field, 1)

old_submit = '''            executor.submit(() -> {\n                //make sure two backups can't happen at once.'''
new_submit = '''            if(executor == null) executor = Threads.executor("Settings Backup", 1);\n            executor.submit(() -> {\n                //make sure two backups can't happen at once.'''
if old_submit not in text:
    if new_submit not in text:
        raise SystemExit("error: pinned Settings backup submission block was not found; refusing an unsafe fuzzy patch")
else:
    text = text.replace(old_submit, new_submit, 1)

path.write_text(text, encoding="utf-8")
print("patched Arc Settings: backup executor is now lazy")


asset_manager = root / "arc-core/src/arc/assets/AssetManager.java"
text = asset_manager.read_text(encoding="utf-8")
old_assets = '        executor = Threads.executor("Assets", 1);'
new_assets = '        executor = Core.executor != null ? Core.executor : Threads.executor("Assets", 1);'
if old_assets not in text:
    if new_assets not in text:
        raise SystemExit("error: pinned AssetManager executor initializer was not found; refusing an unsafe fuzzy patch")
else:
    text = text.replace(old_assets, new_assets, 1)
asset_manager.write_text(text, encoding="utf-8")
print("patched Arc AssetManager: browser reuses backend executor")
