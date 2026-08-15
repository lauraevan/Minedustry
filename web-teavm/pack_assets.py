#!/usr/bin/env python3
from pathlib import Path
import json, struct, sys
if len(sys.argv)!=3: raise SystemExit('usage: pack_assets.py <asset-dir> <output.bin>')
root=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]).resolve()
entries=[]; payload=[]; offset=0
for p in sorted(x for x in root.rglob('*') if x.is_file()):
    rel=p.relative_to(root).as_posix(); data=p.read_bytes()
    entries.append({'path':rel,'offset':offset,'length':len(data)})
    payload.append(data); offset += len(data)
manifest=json.dumps({'version':1,'files':entries},separators=(',',':')).encode('utf-8')
out.parent.mkdir(parents=True,exist_ok=True)
with out.open('wb') as f:
    f.write(struct.pack('>I',len(manifest))); f.write(manifest)
    for data in payload: f.write(data)
print(f'packed {len(entries)} files / {offset} bytes -> {out}')
