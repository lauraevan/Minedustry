#!/usr/bin/env python3
"""Generate TeaVM WebGL GL20 bridge from Anuken/Arc's last official GWT GL20 backend.

Input must already be normalized by import_legacy_backend.py, so its Arc package names
match the pinned current Arc tree. The transform changes only browser interop plumbing;
GL semantics/method bodies remain based on Anuken's official backend wherever possible.
"""
from pathlib import Path
import re, sys

if len(sys.argv) != 3:
    raise SystemExit("usage: generate_teavm_gl.py <normalized GwtGL20.java> <TeaVMGL20.java>")
src = Path(sys.argv[1]); out = Path(sys.argv[2])
if not src.exists(): raise SystemExit(f"missing normalized GWT GL source: {src}")
text = src.read_text(encoding="utf-8")
if "class GwtGL20 implements GL20" not in text:
    raise SystemExit("input is not the expected historical GwtGL20")

text = re.sub(r"^package\s+arc\.backend\.gwt;", "package mindustry.web.teavm;", text, count=1, flags=re.M)
text = text.replace("public class GwtGL20 implements GL20", "public class TeaVMGL20 implements GL20")
text = text.replace("protected GwtGL20(", "public TeaVMGL20(")

# Remove GWT browser/typed-array imports. TeaVM exposes WebGL + java.nio Buffer overloads directly.
lines=[]
for line in text.splitlines():
    s=line.strip()
    if s.startswith("import com.google.gwt.") or s == "import java.nio.HasArrayBufferView;":
        continue
    lines.append(line)
text="\n".join(lines)+"\n"
insert = "import org.teavm.jso.webgl.*;\nimport org.teavm.jso.typedarrays.Uint8Array;\nimport org.teavm.jso.typedarrays.Int32Array;\nimport java.util.ArrayList;\n"
pos = text.find("\n", text.find("package ")) + 1
text = text[:pos] + "\n" + insert + text[pos:]

# Pure Java handle table instead of the historical JS array wrapper.
text = text.replace("IntMap<", "HandleTable<")
text = text.replace("IntMap.create()", "new HandleTable<>()")

# TeaVM WebGL accepts FloatBuffer/IntBuffer/Buffer directly. Remove GWT typed-array copying.
# NOTE: the historical constructor lives inside this block (between the typed-array fields
# and getUniformLocation), so the replacement must re-emit it or the class loses its
# WebGLRenderingContext constructor and no longer compiles.
start = text.find("    Float32Array floatBuffer")
end = text.find("    private WebGLUniformLocation getUniformLocation", start)
if start < 0 or end < 0:
    raise SystemExit("could not locate historical typed-array helper block")
replacement = (
    "    public TeaVMGL20(WebGLRenderingContext gl){\n"
    "        this.gl = gl;\n"
    "        this.gl.pixelStorei(WebGLRenderingContext.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 0);\n"
    "    }\n\n"
    "    private FloatBuffer copy(FloatBuffer buffer){ return buffer; }\n"
    "    private IntBuffer copy(IntBuffer buffer){ return buffer; }\n"
    "    private ShortBuffer copy(ShortBuffer buffer){ return buffer; }\n\n"
)
text = text[:start] + replacement + text[end:]

# GL_VIEWPORT readback: TeaVM's WebGLRenderingContext exposes getParameter(int)->JSObject,
# not GWT's getParameterv; cast the result to a TeaVM Int32Array.
text = text.replace("Int32Array array = gl.getParameterv(pname);",
                    "Int32Array array = (Int32Array)gl.getParameter(pname);")

# WebGL upload/readback can operate on Java NIO buffers in TeaVM.
def replace_method(source, signature_fragment, new_body):
    i=source.find(signature_fragment)
    if i < 0: raise SystemExit(f"method not found: {signature_fragment}")
    brace=source.find('{',i); depth=0; j=brace
    while j < len(source):
        if source[j]=='{': depth+=1
        elif source[j]=='}':
            depth-=1
            if depth==0:
                return source[:brace+1] + "\n" + new_body + "\n    " + source[j:]
        j+=1
    raise SystemExit(f"unclosed method: {signature_fragment}")

text = replace_method(text,
    "public void glBufferData(int target, int size, Buffer data, int usage)",
    "        gl.bufferData(target, data, usage);")
text = replace_method(text,
    "public void glBufferSubData(int target, int offset, int size, Buffer data)",
    "        gl.bufferSubData(target, offset, data);")
text = replace_method(text,
    "public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels)",
    "        gl.readPixels(x, y, width, height, format, type, Uint8Array.fromJavaBuffer(pixels));")
text = replace_method(text,
    "public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type,",
    "        gl.texImage2D(target, level, internalformat, width, height, border, format, type, pixels);")
text = replace_method(text,
    "public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type,",
    "        gl.texSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);")

# Replace the historical JS-native IntMap class at the end.
marker = "    static final class HandleTable<T extends JavaScriptObject>"
idx = text.find(marker)
if idx < 0:
    # after IntMap< replacement the old class declaration may still literally say IntMap.
    idx = text.find("    static final class IntMap<T extends JavaScriptObject>")
if idx < 0:
    raise SystemExit("could not locate historical JS handle table")
# Find the class' matching closing brace, leaving the outer TeaVMGL20 brace intact.
brace=text.find('{',idx); depth=0; j=brace
while j < len(text):
    if text[j]=='{': depth+=1
    elif text[j]=='}':
        depth-=1
        if depth==0: break
    j+=1
handle='''    static final class HandleTable<T>{\n        private final ArrayList<T> values = new ArrayList<>();\n        HandleTable(){ values.add(null); }\n        T get(int key){ return key > 0 && key < values.size() ? values.get(key) : null; }\n        void put(int key, T value){ while(values.size() <= key) values.add(null); values.set(key, value); }\n        int add(T value){ values.add(value); return values.size() - 1; }\n        T remove(int key){ T value = get(key); if(key > 0 && key < values.size()) values.set(key, null); return value; }\n    }'''
text = text[:idx] + handle + text[j+1:]

# Any GWT-only leftovers here mean the transform silently missed something.
# Int32Array is intentionally NOT listed: TeaVM provides org.teavm.jso.typedarrays.Int32Array,
# which the GL_VIEWPORT readback legitimately uses after the getParameter rewrite above.
for forbidden in [
    "com.google.gwt", "JavaScriptObject", "HasArrayBufferView",
    "Uint8ArrayNative", "Float32Array", "Int16Array",
    "ArrayBufferView", "TypedArrays.", "GWT.isProdMode", "getParameterv"
]:
    if forbidden in text:
        raise SystemExit(f"unhandled GWT-only token remains: {forbidden}")

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(text, encoding="utf-8")
print(f"generated TeaVM GL20 bridge: {out}")
