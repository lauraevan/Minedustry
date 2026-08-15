#!/usr/bin/env python3
from pathlib import Path
import argparse, re
p=argparse.ArgumentParser(); p.add_argument("mindustry"); p.add_argument("arc"); p.add_argument("--write-report"); args=p.parse_args()
mind=Path(args.mindustry).resolve(); arc=Path(args.arc).resolve(); checks=[]
def add(name,ok,detail): checks.append((name,ok,detail))
add("TeaVM primary module",(mind/"web-teavm/build.gradle").exists(),"web-teavm/build.gradle")
add("TeaVM real ClientLauncher entry",(mind/"web-teavm/src/main/java/mindustry/web/teavm/WebLauncher.java").exists(),"current ClientLauncher")
add("TeaVM WebGL bridge",(mind/"web-teavm/src/main/java/mindustry/web/teavm/TeaVMGL20.java").exists(),"generated from official Arc GWT GL20")
add("TeaVM compiler reflection policy",(mind/"web-teavm/src/main/java/mindustry/web/teavm/compiler/MindustryReflectionPolicy.java").exists(),"current Arc JSON/reflection semantics")
add("TeaVM native substitution policy",(mind/"web-teavm/src/main/java/mindustry/web/teavm/compiler/MindustrySubstitutionPolicy.java").exists(),"native/JVM boundaries only")
policy=(mind/"web-teavm/src/main/java/mindustry/web/teavm/compiler/MindustrySubstitutionPolicy.java").read_text(encoding="utf-8")
add("Real DataPatcher retained","mindustry.mod.DataPatcher" not in policy and not (mind/"web-teavm/src/main/java/mindustry/web/teavm/substitute/mindustry/mod/DataPatcher.java").exists(),"TeaVM uses current Mindustry chunk/data patcher")
add("GWT reference module",(mind/"web/build.gradle").exists(),"historical browser backend retained")
add("Recovered official GWT backend",(mind/"web/src/arc/backend/gwt/GwtApplication.java").exists(),"Anuken/Arc historical source")
add("Current sibling Arc checkout",(arc/"arc-core/src/arc/Core.java").exists(),"Mindustry localArc composite build")
client=(mind/"core/src/mindustry/ClientLauncher.java").read_text(encoding="utf-8")
add("Browser main-thread sleep guard","limitFps && !Core.app.isWeb()" in client,"ClientLauncher.update")

vars_text=(mind/"core/src/mindustry/Vars.java").read_text(encoding="utf-8")
add("No temporary default Platform","public static Platform platform; // installed by ClientLauncher.setup" in vars_text and "new Platform(){}" not in vars_text,"Vars.platform")
add("No native JVM executable probe",'javaPath = "java"; // browser build never launches a child JVM' in vars_text and 'OS.prop("java.home")' not in vars_text,"Vars.init")

be=(mind/"core/src/mindustry/net/BeControl.java").read_text(encoding="utf-8")
add("BE native self-updater disabled","public boolean active(){\n        return false;" in be and "Timer.schedule" not in be,"BeControl")

sound=(mind/"core/src/mindustry/audio/SoundControl.java").read_text(encoding="utf-8")
add("Ambient audio worker is main-threaded","ambientSources = new Seq<>()" in sound and "class AudioThread extends Thread" not in sound and "LinkedBlockingQueue" not in sound,"SoundControl")

netclient=(mind/"core/src/mindustry/core/NetClient.java").read_text(encoding="utf-8")
add("NetClient has no daemon asset worker","Threads.daemon" not in netclient,"NetClient stream handler")

fontsub=(mind/"web-teavm/src/main/java/mindustry/web/teavm/substitute/mindustry/ui/Fonts.java").read_text(encoding="utf-8")
add("Font logical keys preserved",'Core.assets.load("default", Font.class' in fontsub and 'Core.assets.load("outline", Font.class' in fontsub and 'new FontLoader(Fonts::resolveWebFont)' in fontsub,"TeaVM Fonts")
patterns={
 "native/SoLoud references":r"\bSoloud\b|arc\.audio\.Soloud",
 "FreeType references":r"arc\.freetype|FreeTypeFont",
 "URLClassLoader references":r"\bURLClassLoader\b",
 "CountDownLatch":r"\bCountDownLatch\b",
 "java.nio.channels":r"java\.nio\.channels",
 "thread creation":r"\bnew Thread\b|Executors\.",
 "Rhino references":r"\brhino\.",
 "java.lang.reflect":r"java\.lang\.reflect|Class\.forName",
}
counts={k:0 for k in patterns}
for f in (mind/"core/src").rglob("*.java"):
    try: text=f.read_text(encoding="utf-8")
    except Exception: continue
    for k,pat in patterns.items():
        if re.search(pat,text): counts[k]+=1
report=["# Mindustry Web Port Audit\n","Generated from the pinned checkout. A structural pass is not a claim that the browser runtime has passed.\n","## Structural checks\n"]
for name,ok,detail in checks: report.append(f"- [{'x' if ok else ' '}] {name} — {detail}")
report += ["\n## Browser compatibility hotspots in current core\n"]
for k,v in counts.items(): report.append(f"- {k}: {v} Java files")
report += ["""
## Interpretation

TeaVM is the primary compiler because it preserves far more current Java/JRE behavior than the retired GWT path. Nonzero hotspot counts are not hidden: each occurrence must be translated by TeaVM, made unreachable, substituted at a platform boundary, or explicitly disabled as an unsupported browser feature.

First playable acceptance target: current vanilla campaign/sandbox/editor offline. Java/JAR mods, native SoLoud audio and raw TCP/UDP multiplayer are separate browser-compatibility milestones.
"""]
out="\n".join(report)+"\n"; print(out)
if args.write_report: Path(args.write_report).write_text(out,encoding="utf-8")
if not all(ok for _,ok,_ in checks): raise SystemExit("audit failed: structural checks did not all pass")
