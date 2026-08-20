#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mindustry.py <Mindustry checkout>")

repo = Path(sys.argv[1]).resolve()
client = repo / "core/src/mindustry/ClientLauncher.java"
settings = repo / "settings.gradle"

if not client.exists() or not settings.exists():
    raise SystemExit("error: not a Mindustry checkout")


def exact_replace(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if old not in text:
        if new in text:
            print(f"already patched: {label}")
            return
        raise SystemExit(f"error: expected {label} block was not found; pinned upstream drift detected")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"patched: {label}")


# Client main-loop/browser host compatibility.
text = client.read_text(encoding="utf-8")
old = """        if(limitFps){
            long current = Time.nanos();
            if(nextFrame > current){
                long toSleep = nextFrame - current;
                Threads.sleep(toSleep / 1000000, (int)(toSleep % 1000000));
            }
        }"""
new = """        // A browser's animation frame is already scheduled by the web backend.
        // Sleeping the browser main thread would freeze rendering/input.
        if(limitFps && !Core.app.isWeb()){
            long current = Time.nanos();
            if(nextFrame > current){
                long toSleep = nextFrame - current;
                Threads.sleep(toSleep / 1000000, (int)(toSleep % 1000000));
            }
        }"""
if old not in text and new not in text:
    raise SystemExit("error: expected ClientLauncher frame limiter block was not found; upstream drift detected")
text = text.replace(old, new, 1)

old2 = "if(OS.isIos || OS.isAndroid) return;"
new2 = "if(OS.isIos || OS.isAndroid || Core.app.isWeb()) return;"
if old2 not in text and new2 not in text:
    raise SystemExit("error: expected fileDropped guard was not found; upstream drift detected")
text = text.replace(old2, new2, 1)
# Browser checkout has no JVM process properties or meaningful max heap. Remove
# those startup probes instead of relying on partial JRE emulation.
old3 = '        String dataDir = System.getProperty("mindustry.data.dir", OS.env("MINDUSTRY_DATA_DIR"));'
new3 = '        String dataDir = null; // browser storage is provided by GwtFiles/GwtSettings'
if old3 not in text and new3 not in text:
    raise SystemExit("error: expected ClientLauncher data-dir property probe was not found")
text = text.replace(old3, new3, 1)

old4 = """        long ram = Runtime.getRuntime().maxMemory();

        if(!OS.isIos) Log.info("[RAM] Available: @", Strings.formatByteCount(ram));"""
new4 = """        // Browser JavaScript has no JVM max heap value to report."""
if old4 not in text and new4 not in text:
    raise SystemExit("error: expected ClientLauncher JVM RAM probe was not found")
text = text.replace(old4, new4, 1)

client.write_text(text, encoding="utf-8")
print("patched: browser-safe frame limiting, file-drop guard, and JVM startup probes")


# Vars.init probes java.home/jre paths on native desktop. The browser never launches
# a child JVM, and allowing this probe to reach java.io.File is both useless and
# dangerous under TeaVM. Keep the public javaPath field but use a harmless sentinel.
vars_path = repo / "core/src/mindustry/Vars.java"
vars_text = vars_path.read_text(encoding="utf-8")
old = """        javaPath =
            new Fi(OS.prop("java.home")).child("bin/java").exists() ? new Fi(OS.prop("java.home")).child("bin/java").absolutePath() :
            Core.files.local("jre/bin/java").exists() ? Core.files.local("jre/bin/java").absolutePath() : // Unix
            Core.files.local("jre/bin/java.exe").exists() ? Core.files.local("jre/bin/java.exe").absolutePath() : // Windows
            "java";"""
new = '        javaPath = "java"; // browser build never launches a child JVM'
if old not in vars_text and new not in vars_text:
    raise SystemExit("error: expected Vars javaPath desktop probe was not found")
vars_text = vars_text.replace(old, new, 1)

old_platform = '    public static Platform platform = new Platform(){};'
new_platform = '    public static Platform platform; // installed by ClientLauncher.setup; avoids default ArcNet platform in TeaVM graph'
if old_platform not in vars_text and new_platform not in vars_text:
    raise SystemExit("error: expected Vars default Platform initializer was not found")
vars_text = vars_text.replace(old_platform, new_platform, 1)

vars_path.write_text(vars_text, encoding="utf-8")
print("patched: Vars JVM path probe and temporary default Platform removed")

# The bleeding-edge self-updater is a desktop process-management feature. It
# schedules periodic network checks and can download/exec replacement JARs;
# none of that is meaningful or safe in a browser. Preserve BeControl's public
# API while making the updater inert so these JVM/process paths are not in the
# browser's reachable call graph.
becontrol_path = repo / "core/src/mindustry/net/BeControl.java"
be_text = becontrol_path.read_text(encoding="utf-8")
old = '''    /** @return whether this is a bleeding edge build. */
    public boolean active(){
        return Version.type.equals("bleeding-edge") && !steam;
    }'''
new = '''    /** Browser ports never self-replace their application binary. */
    public boolean active(){
        return false;
    }'''
if old not in be_text and new not in be_text:
    raise SystemExit("error: expected BeControl active() block was not found")
be_text = be_text.replace(old, new, 1)

start = be_text.find('    public void init(){')
end = be_text.find('\n    /** asynchronously checks for updates. */', start)
new_init = '''    public void init(){
        // Browser build: no periodic BE updater or native process/JAR replacement.
    }
'''
if start == -1 or end == -1:
    if new_init not in be_text:
        raise SystemExit("error: expected BeControl init() block was not found")
else:
    be_text = be_text[:start] + new_init + be_text[end:]

start = be_text.find('    /** asynchronously checks for updates. */')
method = be_text.find('    public void checkUpdate(Boolc done){', start)
end = be_text.find('\n    /** @return whether a new update is available */', method)
new_check = '''    /** Browser build does not perform native bleeding-edge self-updates. */
    public void checkUpdate(Boolc done){
        done.get(false);
    }
'''
if method == -1 or end == -1:
    if new_check not in be_text:
        raise SystemExit("error: expected BeControl checkUpdate() block was not found")
else:
    be_text = be_text[:start] + new_check + be_text[end:]

start = be_text.find('    /** shows the dialog for updating the game on desktop, or a prompt for doing so on the server */')
method = be_text.find('    public void showUpdateDialog(){', start)
end = be_text.find('\n    private void download(', method)
new_show = '''    /** Browser build has no native application-binary updater. */
    public void showUpdateDialog(){
    }
'''
if method == -1 or end == -1:
    if new_show not in be_text:
        raise SystemExit("error: expected BeControl showUpdateDialog() block was not found")
else:
    be_text = be_text[:start] + new_show + be_text[end:]

becontrol_path.write_text(be_text, encoding="utf-8")
print("patched: native bleeding-edge self-updater disabled for browser")

# NetClient registers a handler that normally spawns a daemon thread to unpack
# streamed server assets. The first browser milestone is offline-only, and JS
# has no JVM daemon thread. Keep the handler/API but marshal the same work onto
# the application queue so the thread constructor is absent from reachable code.
netclient_path = repo / "core/src/mindustry/core/NetClient.java"
netclient_text = netclient_path.read_text(encoding="utf-8")
old = '''                //make this new thread block as it loads the assets
                Threads.daemon(() -> {
                    Log.info("Receiving asset data: @", Strings.formatByteCount(data.total));

                    try{
                        NetworkIO.loadAssets(data.incrementalStream);

                        //after receiving assets, tell the server that the client is ready to handle the world
                        Core.app.post(Call::requestWorld);
                    }catch(Exception e){
                        Core.app.post(() -> {
                            ui.showException("@receiving.assets.fail", e);
                            disconnectQuietly();
                        });
                    }
                });'''
new = '''                // Browser target has no JVM daemon threads. Future WebSocket
                // transport can replace this with chunked/asynchronous decoding.
                Core.app.post(() -> {
                    Log.info("Receiving asset data: @", Strings.formatByteCount(data.total));

                    try{
                        NetworkIO.loadAssets(data.incrementalStream);
                        Core.app.post(Call::requestWorld);
                    }catch(Exception e){
                        Core.app.post(() -> {
                            ui.showException("@receiving.assets.fail", e);
                            disconnectQuietly();
                        });
                    }
                });'''
if old not in netclient_text and new not in netclient_text:
    raise SystemExit("error: expected NetClient streamed-asset daemon block was not found")
netclient_text = netclient_text.replace(old, new, 1)
netclient_path.write_text(netclient_text, encoding="utf-8")
print("patched: NetClient streamed asset handler uses browser app queue")

# SoundControl's ambient source collector is a dedicated JVM Thread backed by a
# LinkedBlockingQueue. Browsers run the game loop on the JS main thread. Keep
# the same ambient-source/falloff calculations, but scan those sources from
# updateLoops() instead of spawning a worker.
sound_path = repo / "core/src/mindustry/audio/SoundControl.java"
sound_text = sound_path.read_text(encoding="utf-8")
sound_text = sound_text.replace("import java.util.concurrent.*;\n\n", "")
old = '''    protected @Nullable AudioThread ambientThread;
    protected boolean launchingAmbientThread;
    protected Seq<SoundData> localData = new Seq<>();'''
new = '''    /** Browser target keeps ambient sources on the main game loop. */
    protected Seq<AmbientSource> ambientSources = new Seq<>();'''
if old not in sound_text and new not in sound_text:
    raise SystemExit("error: expected SoundControl ambient worker fields were not found")
sound_text = sound_text.replace(old, new, 1)

old = '''            launchingAmbientThread = false;
            if(ambientThread != null){
                ambientThread.running = false;
                ambientThread.interrupt();
                ambientThread = null;
            }'''
new = '''            ambientSources.clear();'''
if old not in sound_text and new not in sound_text:
    raise SystemExit("error: expected SoundControl reset worker block was not found")
sound_text = sound_text.replace(old, new, 1)

start = sound_text.find('    public void addAmbientSource(AmbientSource source){')
end = sound_text.find('\n    protected void updateLoops(){', start)
new_add = '''    public void addAmbientSource(AmbientSource source){
        if(headless) return;
        ambientSources.addUnique(source);
    }
'''
if start == -1 or end == -1:
    if new_add not in sound_text:
        raise SystemExit("error: expected SoundControl addAmbientSource() block was not found")
else:
    sound_text = sound_text[:start] + new_add + sound_text[end:]

needle = '''        if(state.isPaused()) return;

        float avol = Core.settings.getInt("ambientvol", 100) / 100f;'''
replacement = '''        if(state.isPaused()) return;

        // Advance ambient-source accumulation on the browser game loop instead
        // of a sleeping JVM audio thread.
        for(int i = 0; i < ambientSources.size; i++){
            AmbientSource source = ambientSources.get(i);
            if(!source.isValid()){
                ambientSources.remove(i--);
                continue;
            }
            if(source.shouldAmbientSound()){
                float volume = source.getAmbientVolume();
                if(volume > 0.00001f){
                    Sound sound = source.getAmbientSound();
                    loop(sounds.get(sound, SoundData::new), sound, source, volume, 1f);
                }
            }
        }

        float avol = Core.settings.getInt("ambientvol", 100) / 100f;'''
if needle not in sound_text and replacement not in sound_text:
    raise SystemExit("error: expected SoundControl updateLoops insertion point was not found")
sound_text = sound_text.replace(needle, replacement, 1)

start = sound_text.find('        //grab data from ambient thread')
end = sound_text.find('\n    }\n\n    protected static class SoundData', start)
if start != -1 and end != -1:
    sound_text = sound_text[:start] + sound_text[end:]
elif 'ambientThread.outputData' in sound_text:
    raise SystemExit("error: could not remove SoundControl ambient thread output block")

start = sound_text.find('    static class AudioThread extends Thread{')
if start != -1:
    # AudioThread is the final nested class before SoundControl's closing brace.
    sound_text = sound_text[:start] + '}\n'
elif 'class AudioThread extends Thread' in sound_text:
    raise SystemExit("error: could not remove SoundControl AudioThread class")

for token in ('ambientThread', 'launchingAmbientThread', 'LinkedBlockingQueue<', 'extends Thread'):
    if token in sound_text:
        raise SystemExit(f"error: SoundControl browser patch left worker token: {token}")

sound_path.write_text(sound_text, encoding="utf-8")
print("patched: SoundControl ambient scan moved onto browser game loop")

# AsyncCore: browser branch executes the exact AsyncProcess implementations serially.
# This worktree is browser-only, so JVM thread-pool construction is removed entirely.
async_core = repo / "core/src/mindustry/async/AsyncCore.java"
text = async_core.read_text(encoding="utf-8")
text = text.replace("import java.util.concurrent.*;\n\n", "")
text = text.replace(
"""    //futures to be awaited
    private final Seq<Future<?>> futures = new Seq<>();

    private ExecutorService executor;

""", "")
old = """            futures.clear();

            //init executor with size of potentially-modified process list
            if(executor == null){
                executor = Executors.newFixedThreadPool(processes.size, r -> {
                    Thread thread = new Thread(r, \"AsyncLogic-Thread\");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, e) -> Threads.throwAppException(e));
                    return thread;
                });
            }

            //submit all tasks
            for(AsyncProcess p : processes){
                if(p.shouldProcess()){
                    futures.add(executor.submit(p::process));
                }
            }"""
new = """            // Browser target: execute the same async processes serially. This avoids
            // JVM thread APIs while preserving process ordering and simulation code.
            for(AsyncProcess p : processes){
                if(p.shouldProcess()){
                    p.process();
                }
            }"""
if old not in text and new not in text:
    raise SystemExit("error: expected AsyncCore worker block was not found")
text = text.replace(old, new, 1)
old = """    private void complete(){
        //wait for all threads to stop processing
        for(var future : futures){
            try{
                future.get();
            }catch(Throwable t){
                throw new RuntimeException(t);
            }
        }

        //clear processed futures
        futures.clear();
    }"""
new = """    private void complete(){
        // Browser target is deterministic/synchronous; no worker futures exist.
    }"""
if old not in text and new not in text:
    raise SystemExit("error: expected AsyncCore complete block was not found")
text = text.replace(old, new, 1)
async_core.write_text(text, encoding="utf-8")
print("patched: AsyncCore single-thread browser simulation")


# Pathfinder: preserve Mindustry's flow-field algorithm, but advance it once per
# game update rather than from a daemon thread that sleeps.
path = repo / "core/src/mindustry/ai/Pathfinder.java"
text = path.read_text(encoding="utf-8")
text = text.replace("public class Pathfinder implements Runnable{", "public class Pathfinder{")
text = text.replace("    /** Current pathfinding thread */\n    @Nullable Thread thread;\n", "")
old = """    /** Starts or restarts the pathfinding thread. */
    private void start(){
        stop();
        if(net.client()) return;

        thread = new Thread(this, \"Pathfinder\");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the pathfinding thread. */
    private void stop(){
        if(thread != null){
            thread.interrupt();
            thread = null;
        }
        queue.clear();
        needsRefresh = false;
    }"""
new = """    /** Browser target advances pathfinding from the game update loop. */
    private void start(){
        if(net.client()) return;
    }

    private void stop(){
        queue.clear();
        needsRefresh = false;
    }"""
if old not in text and new not in text:
    raise SystemExit("error: expected Pathfinder start/stop block was not found")
text = text.replace(old, new, 1)
old = """        Events.run(Trigger.afterGameUpdate, () -> {
            if(!needsRefresh) return;"""
new = """        Events.run(Trigger.afterGameUpdate, () -> {
            updateBrowserStep();
            if(!needsRefresh) return;"""
if old not in text and new not in text:
    raise SystemExit("error: expected Pathfinder afterGameUpdate hook was not found")
text = text.replace(old, new, 1)
start = text.find("    /** Thread implementation. */\n    @Override\n    public void run(){")
end = text.find("\n    public Flowfield getField", start)
if start != -1 and end != -1:
    replacement = """    /** Advances the original pathfinding worker algorithm without a JVM thread. */
    private void updateBrowserStep(){
        if(net.client() || !state.isPlaying()) return;

        queue.run();
        for(Flowfield data : threadList){
            if(data.dirty && data.frontier.size == 0){
                updateTargets(data);
                data.dirty = false;
            }
            updateFrontier(data, maxUpdate);
        }
    }
"""
    text = text[:start] + replacement + text[end:]
elif "private void updateBrowserStep()" not in text:
    raise SystemExit("error: expected Pathfinder run method was not found")
path.write_text(text, encoding="utf-8")
print("patched: Pathfinder event-loop stepping")


# ControlPathfinder: preserve current hierarchical pathfinding, but run one bounded
# worker iteration on Trigger.update. This is slower than native threading, but is
# deterministic and keeps the real AI/path data structures and algorithms.
path = repo / "core/src/mindustry/ai/ControlPathfinder.java"
text = path.read_text(encoding="utf-8")
text = text.replace("public class ControlPathfinder implements Runnable{", "public class ControlPathfinder{")
text = text.replace("    /** Current pathfinding thread */\n    @Nullable Thread thread;\n\n", "")
text = text.replace(
    "    volatile boolean invalidated;",
    "    boolean invalidated;\n    private long lastInvalidCheck = Time.millis() + invalidateCheckInterval;"
)
old = """        Events.run(Trigger.update, () -> {
            for(var req : controlPath.unitRequests.values()){"""
new = """        Events.run(Trigger.update, () -> {
            controlPath.updateBrowserStep();
            for(var req : controlPath.unitRequests.values()){"""
if old not in text and new not in text:
    raise SystemExit("error: expected ControlPathfinder update hook was not found")
text = text.replace(old, new, 1)
old = """    /** Starts or restarts the pathfinding thread. */
    private void start(){
        if(net.client() || thread != null) return;

        thread = new Thread(this, \"Control Pathfinder\");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the pathfinding thread. */
    private void stop(){
        if(thread != null){
            thread.interrupt();
            thread = null;
        }
        invalidated = true;
        queue.clear();
    }"""
new = """    /** Browser target advances pathfinding from Trigger.update. */
    private void start(){
        if(net.client()) return;
        invalidated = false;
    }

    private void stop(){
        invalidated = true;
        queue.clear();
    }"""
if old not in text and new not in text:
    raise SystemExit("error: expected ControlPathfinder start/stop block was not found")
text = text.replace(old, new, 1)
start = text.find("    @Override\n    public void run(){")
end = text.find("\n    @Struct\n    static class FieldIndexStruct", start)
if start != -1 and end != -1:
    replacement = """    /** One bounded iteration of the original pathfinding worker. */
    private void updateBrowserStep(){
        if(net.client() || invalidated || !state.isPlaying()) return;

        try{
            queue.run();

            clustersToUpdate.each(cluster -> {
                updateClustersComplete(cluster);
                clustersToInnerUpdate.remove(cluster);
            });
            clustersToInnerUpdate.each(this::updateClustersInner);
            clustersToInnerUpdate.clear();
            clustersToUpdate.clear();

            if(Time.timeSinceMillis(lastInvalidCheck) > invalidateCheckInterval){
                lastInvalidCheck = Time.millis();
                var it = invalidRequests.iterator();
                while(it.hasNext()){
                    var request = it.next();
                    if(request.invalidated){
                        it.remove();
                        continue;
                    }

                    var field = fields.get(FieldIndex.get(request.destination, request.costId, request.team));
                    if(field != null){
                        if(field.frontier.isEmpty()){
                            fields.remove(field.mapKey);
                            Core.app.post(() -> fieldList.remove(field));
                            for(var otherRequest : threadPathRequests){
                                if(otherRequest.destination == request.destination){
                                    otherRequest.oldCache = field;
                                    if(otherRequest != request) queue.post(() -> recalculatePath(otherRequest));
                                }
                            }
                            queue.post(() -> recalculatePath(request));
                            it.remove();
                        }
                    }else{
                        queue.post(() -> recalculatePath(request));
                        it.remove();
                    }
                }
            }

            fields.eachValue(cache -> {
                if(cache != null) updateFields(cache, maxUpdate);
            });
        }catch(Throwable e){
            if(!invalidated) Log.err(e);
        }
    }
"""
    text = text[:start] + replacement + text[end:]
elif "private void updateBrowserStep()" not in text:
    raise SystemExit("error: expected ControlPathfinder run method was not found")
path.write_text(text, encoding="utf-8")
print("patched: ControlPathfinder event-loop stepping")


# FogControl: keep the exact current fog bitfield operations/data format while doing
# the work immediately on the browser update thread instead of wait/notify threads.
path = repo / "core/src/mindustry/game/FogControl.java"
text = path.read_text(encoding="utf-8")
text = text.replace("    private static final Object notifyStatic = new Object(), notifyDynamic = new Object();\n", "")
text = text.replace("    private @Nullable Thread staticFogThread;\n    private @Nullable Thread dynamicFogThread;\n\n", "")
old = """        if(staticFogThread != null){
            staticFogThread.interrupt();
            staticFogThread = null;
        }

        dynamicEvents.clear();
        if(dynamicFogThread != null){
            dynamicFogThread.interrupt();
            dynamicFogThread = null;
        }"""
new = """        dynamicEvents.clear();"""
if old not in text and new not in text:
    raise SystemExit("error: expected FogControl stop-worker block was not found")
text = text.replace(old, new, 1)
old = """        if(staticFogThread == null){
            staticFogThread = new StaticFogThread();
            staticFogThread.setPriority(Thread.NORM_PRIORITY - 1);
            staticFogThread.setDaemon(true);
            staticFogThread.start();
        }

        if(dynamicFogThread == null){
            dynamicFogThread = new DynamicFogThread();
            dynamicFogThread.setPriority(Thread.NORM_PRIORITY - 1);
            dynamicFogThread.setDaemon(true);
            dynamicFogThread.start();
        }

"""
if old not in text and "staticFogThread == null" in text:
    raise SystemExit("error: expected FogControl worker-start block was not found")
text = text.replace(old, "", 1)
old = """            //notify that it's time for rendering
            //TODO this WILL block until it is done rendering, which is inherently problematic.
            synchronized(notifyDynamic){
                notifyDynamic.notify();
            }"""
new = """            // Browser target computes the visibility buffer immediately.
            updateDynamic(new Bits(256));"""
if old not in text and new not in text:
    raise SystemExit("error: expected FogControl dynamic notify block was not found")
text = text.replace(old, new, 1)
old = """        //wake up, it's time to draw some circles
        if(state.rules.staticFog && staticEvents.size > 0 && staticFogThread != null){
            synchronized(notifyStatic){
                notifyStatic.notify();
            }
        }"""
new = """        if(state.rules.staticFog && staticEvents.size > 0){
            updateStatic();
        }"""
if old not in text and new not in text:
    raise SystemExit("error: expected FogControl static notify block was not found")
text = text.replace(old, new, 1)

# Remove now-unreferenced Thread subclasses so GWT never has to translate wait/sleep.
start = text.find("    class StaticFogThread extends Thread{")
end = text.find("    void updateStatic(){", start)
if start != -1 and end != -1:
    text = text[:start] + text[end:]
start = text.find("    class DynamicFogThread extends Thread{")
end = text.find("    void updateDynamic(Bits cleared){", start)
if start != -1 and end != -1:
    text = text[:start] + text[end:]
if "StaticFogThread extends Thread" in text or "DynamicFogThread extends Thread" in text:
    raise SystemExit("error: FogControl thread classes remain after patch")
path.write_text(text, encoding="utf-8")
print("patched: FogControl synchronous browser update")

# MapPreviewLoader setup is a reflective preview compatibility workaround.
preview_path = repo / "core/src/mindustry/maps/MapPreviewLoader.java"
preview = preview_path.read_text(encoding="utf-8")
preview = preview.replace("import java.lang.reflect.*;\n\n", "")
start = preview.find("    public static void setupLoaders(){")
end = preview.find("    public static void checkPreviews(){", start)
if start != -1 and end != -1:
    preview = preview[:start] + "    public static void setupLoaders(){\n        // Browser target: no reflective preview-state workaround.\n        check = null;\n    }\n\n" + preview[end:]
elif "no reflective preview-state workaround" not in preview:
    raise SystemExit("error: expected MapPreviewLoader.setupLoaders implementation was not found")
preview_path.write_text(preview, encoding="utf-8")
print("patched: MapPreviewLoader reflection hook disabled")

# Mods: current desktop mod loading exposes JVM-only ClassLoader/Rhino paths.
# The first browser milestone is vanilla current Mindustry, so keep the Mods
# manager/API but remove executable Java/script mod loading from this web-only
# checkout. This also prevents JVM class-loading code from entering GWT's graph.
mods_path = repo / "core/src/mindustry/mod/Mods.java"
mods = mods_path.read_text(encoding="utf-8")

mods = mods.replace(
    '    private ContentParser parser = new ContentParser();',
    '    private ContentParser parser; // browser build: executable/content mods are disabled'
)
mods = mods.replace(
    '    private ModClassLoader mainLoader = new ModClassLoader(getClass().getClassLoader());',
    '    private ClassLoader mainLoader = getClass().getClassLoader(); // browser: preserve ABI, no dynamic child loader'
)

# Browser import UI may still call this method; fail explicitly rather than
# touching ZIP/JAR/class-loader paths.
pat = re.compile(r'''    /\*\* Imports an external mod file\. Folders are not supported here\. \*/\n    public LoadedMod importMod\(Fi file, boolean forceEnable\) throws IOException\{.*?\n    \}\n\n    /\*\* Repacks all in-game sprites\. \*/''', re.S)
repl = '''    /** Imports are intentionally unavailable in the first browser milestone. */
    public LoadedMod importMod(Fi file, boolean forceEnable) throws IOException{
        throw new IOException("Mod importing is not supported by the browser build yet.");
    }

    /** Repacks all in-game sprites. */'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.importMod implementation was not found")

# The normal implementation is a parallel sprite-repacking pipeline using
# Future/Executor/CountDownLatch. With the browser milestone's empty mod set,
# preserve the Loadable ABI but remove the worker pipeline from TeaVM's graph.
pat = re.compile(r'''    /\*\* Repacks all in-game sprites\. \*/\n    @Override\n    public void loadAsync\(\)\{.*?\n    \}\n\n    private void loadIcons\(\)\{''', re.S)
repl = '''    /** Browser milestone has no mod sprites to repack. */
    @Override
    public void loadAsync(){
    }

    private void loadIcons(){'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.loadAsync implementation was not found")

# The browser starts with no mods, so synchronous icon loading is also a no-op.
pat = re.compile(r'''    @Override\n    public void loadSync\(\)\{.*?\n    \}\n\n    private PageType getPage''', re.S)
repl = '''    @Override
    public void loadSync(){
    }

    private PageType getPage'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.loadSync implementation was not found")

# No executable/content mods are scanned in the browser build. buildFiles() is
# still called so the normal FileTreeInitEvent startup contract is preserved.
pat = re.compile(r'''    /\*\* Loads all mods from the folder, but does not call any methods on them\.\*/\n    public void load\(\)\{.*?\n    \}\n\n    private void sortMods\(\)\{''', re.S)
repl = '''    /** Browser build starts with an empty mod set. */
    public void load(){
        mods.clear();
        lastOrderedMods = new Seq<>();
        buildFiles();
    }

    private void sortMods(){'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.load implementation was not found")

# Skip Rhino/script traversal at the source level so TeaVM never has to translate
# that runtime path just to discover the early skipModCode guard.
pat = re.compile(r'''    /\*\* This must be run on the main thread! \*/\n    public void loadScripts\(\)\{.*?\n    \}\n\n    /\*\* Creates all the content found in mod files\. \*/''', re.S)
repl = '''    /** Browser milestone: executable mod scripts are disabled. */
    public void loadScripts(){
    }

    /** Creates all the content found in mod files. */'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.loadScripts implementation was not found")

# With an empty mod set there is no ContentParser work to do. Avoid eagerly
# constructing the reflection-heavy parser at all.
pat = re.compile(r'''    /\*\* Creates all the content found in mod files\. \*/\n    public void loadContent\(\)\{.*?\n    \}\n\n    public void handleContentError\(Content content, Throwable error\)\{.*?\n    \}\n\n    /\*\* Adds a listener for parsed JSON objects\. \*/\n    public void addParseListener\(ParseListener hook\)\{.*?\n    \}''', re.S)
repl = '''    /** Browser milestone: vanilla content only. */
    public void loadContent(){
    }

    public void handleContentError(Content content, Throwable error){
        Log.err(error);
    }

    /** Browser milestone has no mod JSON parser. */
    public void addParseListener(ParseListener hook){
    }'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.loadContent/parser methods were not found")

# Remove class-loader cleanup paths from remove/overwrite. There can be no Java
# mod loader in this browser checkout.
pat = re.compile(r'''\n        if\(mod\.loader != null\)\{.*?\n        \}\n\n        if\(mod\.root instanceof ZipFi\)''', re.S)
mods, count = pat.subn('\n\n        if(mod.root instanceof ZipFi)', mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods.removeMod class-loader cleanup was not found")

pat = re.compile(r'''\n                    //close the classloader for jar mods\n                    if\(!android\)\{.*?\n                    \}\n\n                    //close zip file''', re.S)
mods, count = pat.subn('\n\n                    //close zip file', mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods overwrite class-loader cleanup was not found")

# Keep metadata/content ZIP parsing source-compilable, but Java main classes can
# never be instantiated. Replace the complete dynamic loading branch.
pat = re.compile(r'''            //make sure the main class exists before loading it; if it doesn't just don't put it there\n            //if the mod is explicitly marked as java, try loading it anyway\n            if\(.*?\n            \}else\{\n                mainMod = null;\n            \}''', re.S)
repl = '''            // Browser build cannot dynamically load JVM classes. Content-only
            // archives are represented with a null main class.
            mainMod = null;'''
mods, count = pat.subn(repl, mods, count=1)
if count != 1:
    raise SystemExit("error: expected Mods Java class-loading branch was not found")


mods_path.write_text(mods, encoding="utf-8")
print("patched: browser vanilla-only mod boundary (no JAR/Rhino execution)")

# ContentParser has one fallback that asks Mods for a dynamic class loader.
parser_path = repo / "core/src/mindustry/mod/ContentParser.java"
parser = parser_path.read_text(encoding="utf-8")
old = """            try{
                return (Class<T>)Class.forName(base);
            }catch(Exception ignored){
                //try to use mod class loader
                try{
                    return (Class<T>)Class.forName(base, true, mods.mainLoader());
                }catch(Exception ignore){}
            }"""
new = """            try{
                return (Class<T>)Class.forName(base);
            }catch(Exception ignored){
                // Browser build has no dynamic mod class loader.
            }"""
if old not in parser and new not in parser:
    raise SystemExit("error: expected ContentParser mod class-loader fallback was not found")
parser = parser.replace(old, new, 1)
parser_path.write_text(parser, encoding="utf-8")
print("patched: ContentParser dynamic mod class-loader fallback removed")

# The first browser milestone constructs Core.audio as Audio(false). In that
# state Arc's newSound/newMusic are intentionally inert, so do not probe every
# generated audio path and emit missing-file warnings after the browser pack
# omits raw music/sound payloads. This is a web-only optimization; when WebAudio
# is enabled, remove this short-circuit together with the staging exclusions.
filetree_path = repo / "core/src/mindustry/core/FileTree.java"
filetree = filetree_path.read_text(encoding="utf-8")
old_sound = '        if(Vars.headless) return Sounds.none;'
new_sound = '        if(Vars.headless || Core.audio == null || !Core.audio.initialized()) return Sounds.none; // browser muted milestone'
if old_sound not in filetree and new_sound not in filetree:
    raise SystemExit("error: expected FileTree sound early-return was not found")
filetree = filetree.replace(old_sound, new_sound, 1)
old_music = '        if(Vars.headless) return new Music();'
new_music = '        if(Vars.headless || Core.audio == null || !Core.audio.initialized()) return new Music(); // browser muted milestone'
if old_music not in filetree and new_music not in filetree:
    raise SystemExit("error: expected FileTree music early-return was not found")
filetree = filetree.replace(old_music, new_music, 1)
filetree_path.write_text(filetree, encoding="utf-8")
print("patched: muted browser audio skips raw audio asset probes")

# The web patch (patch_arc.py) makes arc.Core.executor null so the browser backend
# installs its own; JVM entry points get theirs from their Arc backend. But the
# :tools:pack sprite generator (ImagePacker) runs headless with no Arc backend, so
# Vars.mainExecutor (= Core.executor) stays null and Generators.run() NPEs. Install
# the shared executor at the start of the tool, since this JVM build step can create
# real threads. Without this the whole browser asset pipeline cannot be staged.
imagepacker_path = repo / "tools/src/mindustry/tools/ImagePacker.java"
if imagepacker_path.exists():
    exact_replace(
        imagepacker_path,
        "    public static void main(String[] args) throws Exception{\n"
        "        Vars.headless = true;\n"
        "        //makes PNG loading slightly faster\n"
        "        ArcNativesLoader.load();",
        "    public static void main(String[] args) throws Exception{\n"
        "        Vars.headless = true;\n"
        "        //this JVM sprite tool runs without an Arc backend, so it must install the\n"
        "        //shared executor itself; the web patch leaves Core.executor null for the\n"
        "        //browser backend to fill in, which would otherwise NPE in Generators.run().\n"
        "        Core.executor = Threads.executor(\"Main Executor\", OS.cores);\n"
        "        Vars.mainExecutor = Core.executor;\n"
        "        //makes PNG loading slightly faster\n"
        "        ArcNativesLoader.load();",
        "ImagePacker installs the shared executor for headless sprite generation",
    )

# TeaVM's class library lacks java.util.concurrent.ExecutorService and
# java.lang.Class.isAnonymousClass(). With enough compile memory TeaVM ran the whole
# program and flagged these as unresolved on reachable (but not first-boot-critical) paths.
# Dispatch the async submits on the app loop (Core.app.post) and treat the anonymous-class
# check as false, so the whole-program compile resolves and emits the browser bundle.
maps_path = repo / "core/src/mindustry/maps/Maps.java"
maps_text = maps_path.read_text(encoding="utf-8")
if "mainExecutor.submit(() -> {" in maps_text:
    # Maps has two: createAllPreviews and createNewPreview.
    maps_path.write_text(maps_text.replace("mainExecutor.submit(() -> {", "Core.app.post(() -> {"), encoding="utf-8")
    print("patched: Maps preview writes dispatched on app loop (no ExecutorService)")
elif "Core.app.post(() -> {" not in maps_text:
    raise SystemExit("error: expected Maps mainExecutor.submit was not found")

exact_replace(
    repo / "core/src/mindustry/ui/dialogs/ModBrowserDialog.java",
    "mainExecutor.submit(() -> {", "Core.app.post(() -> {",
    "ModBrowserDialog cached-icon load on app loop (no ExecutorService)")
exact_replace(
    repo / "core/src/mindustry/entities/abilities/Ability.java",
    "type.isAnonymousClass()", "false",
    "Ability.getBundle avoids Class.isAnonymousClass (absent in TeaVM)")
exact_replace(
    repo / "core/src/mindustry/ui/dialogs/ContentInfoDialog.java",
    "contentClass.isAnonymousClass()", "false",
    "ContentInfoDialog avoids Class.isAnonymousClass (absent in TeaVM)")
