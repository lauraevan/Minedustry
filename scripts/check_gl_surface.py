#!/usr/bin/env python3
"""Fail if the generated browser GL20 bridge is missing a current Arc GL20 method surface."""
from pathlib import Path
import re, sys
if len(sys.argv)!=3: raise SystemExit('usage: check_gl_surface.py <current GL20.java> <TeaVMGL20.java>')
iface=Path(sys.argv[1]).read_text(encoding='utf-8')
impl=Path(sys.argv[2]).read_text(encoding='utf-8')
# Method name + normalized parameter type list is enough to detect overload drift.
def sigs(text):
 out=set()
 for m in re.finditer(r'\b(?:public\s+)?(?:default\s+)?[\w<>\[\].?]+\s+(gl\w+)\s*\((.*?)\)\s*(?:;|\{)', text, re.S):
  name,args=m.group(1),m.group(2)
  parts=[]
  for arg in [x.strip() for x in args.split(',') if x.strip()]:
   arg=re.sub(r'\s+',' ',arg)
   toks=arg.split(' ')
   typ=' '.join(toks[:-1]) if len(toks)>1 else toks[0]
   parts.append(typ.replace('final ','').strip())
  out.add((name,tuple(parts)))
 return out
need=sigs(iface); have=sigs(impl); missing=sorted(need-have)
if missing:
 print('generated TeaVMGL20 is missing current Arc GL20 methods:',file=sys.stderr)
 for n,a in missing: print('  '+n+'('+', '.join(a)+')',file=sys.stderr)
 raise SystemExit(1)
print(f'GL20 surface check passed: {len(need)} current methods covered')
