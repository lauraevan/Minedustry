package mindustry.web.teavm;

import arc.*;
import arc.Application.ApplicationType;
import arc.audio.Audio;
import arc.struct.Seq;
import arc.util.Log;
import org.teavm.jso.*;
import org.teavm.jso.dom.html.HTMLCanvasElement;

import java.util.function.Supplier;

public final class WebApplication implements Application{
    @JSFunctor interface FrameCallback extends JSObject{ void run(double time); }

    private final Supplier<ApplicationListener> listenerFactory;
    private final Seq<ApplicationListener> listeners = new Seq<>();
    private final Seq<Runnable> pending = new Seq<>(), running = new Seq<>();
    private WebGraphics graphics;
    private WebInput input;
    private boolean exiting;
    private int lastWidth, lastHeight;

    public WebApplication(Supplier<ApplicationListener> listenerFactory){
        this.listenerFactory = listenerFactory;
        // Critical ordering: current Mindustry snapshots platform/executor state in static initializers.
        Core.app = this;
        Core.executor = new ImmediateExecutorService();
    }

    public void start(){
        try{
            HTMLCanvasElement canvas = canvas();
            graphics = new WebGraphics(canvas);
            Core.graphics = graphics;
            Core.gl20 = graphics.getGL20();
            Core.gl30 = null;
            Core.gl = Core.gl20;
            Core.audio = new Audio(false);
            Core.files = new WebFiles();
            Core.settings = new WebSettings();
            input = new WebInput(canvas);
            Core.input = input;

            ApplicationListener listener = listenerFactory.get();
            addListener(listener);
            lastWidth = graphics.getWidth(); lastHeight = graphics.getHeight();
            listener.init();
            listener.resize(lastWidth, lastHeight);
            requestFrame(this::frame);
        }catch(Throwable t){
            Log.err("[TeaVM] Mindustry initialization failed", t);
            throw t instanceof RuntimeException ? (RuntimeException)t : new RuntimeException(t);
        }
    }

    private void frame(double timestamp){
        if(exiting) return;
        try{
            graphics.update(timestamp);
            int width=graphics.getWidth(), height=graphics.getHeight();
            if(width!=lastWidth || height!=lastHeight){
                lastWidth=width; lastHeight=height;
                for(ApplicationListener l:listeners) l.resize(width,height);
            }
            running.addAll(pending); pending.clear();
            for(Runnable r:running) r.run(); running.clear();
            input.update();
            defaultUpdate();
            for(ApplicationListener l:listeners) l.update();
            input.postUpdate();
        }catch(Throwable t){
            Log.err("[TeaVM] frame failed", t);
            throw t instanceof RuntimeException ? (RuntimeException)t : new RuntimeException(t);
        }
        requestFrame(this::frame);
    }

    @Override public Seq<ApplicationListener> getListeners(){ return listeners; }
    @Override public ApplicationType getType(){ return ApplicationType.web; }
    /** Preserve the web application type while selecting Mindustry's touch/mobile UI on real mobile browsers. */
    @Override public boolean isMobile(){ return mobileBrowser(); }
    @Override public long getJavaHeap(){ return 0L; }
    @Override public String getClipboardText(){ return clipboardRead(); }
    @Override public void setClipboardText(String text){ clipboardWrite(text == null ? "" : text); }
    @Override public boolean openURI(String uri){ return open(uri); }
    @Override public void post(Runnable runnable){ pending.add(runnable); }
    @Override public void exit(){ exiting=true; for(ApplicationListener l:listeners) l.dispose(); dispose(); }

    @JSBody(script="try{var ua=navigator.userAgent||'';return /Android|iPhone|iPad|iPod/i.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);}catch(e){return false;}")
    private static native boolean mobileBrowser();
    @JSBody(script="return document.getElementById('game');")
    private static native HTMLCanvasElement canvas();
    @JSBody(params="cb", script="requestAnimationFrame(cb);")
    private static native void requestFrame(FrameCallback cb);
    @JSBody(script="try{return localStorage.getItem('mindustry:clipboard')||'';}catch(e){return '';}")
    private static native String clipboardRead();
    @JSBody(params="text", script="try{localStorage.setItem('mindustry:clipboard',text); if(navigator.clipboard) navigator.clipboard.writeText(text).catch(()=>{});}catch(e){}")
    private static native void clipboardWrite(String text);
    @JSBody(params="uri", script="try{window.open(uri,'_blank','noopener');return true;}catch(e){return false;}")
    private static native boolean open(String uri);
}
