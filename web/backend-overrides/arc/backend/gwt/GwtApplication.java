package arc.backend.gwt;

import arc.*;
import arc.Application.ApplicationType;
import arc.audio.Audio;
import arc.backend.gwt.preloader.Preloader;
import arc.backend.gwt.preloader.Preloader.PreloaderCallback;
import arc.backend.gwt.preloader.Preloader.PreloaderState;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Log.LogLevel;
import com.google.gwt.animation.client.AnimationScheduler;
import com.google.gwt.animation.client.AnimationScheduler.AnimationCallback;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.*;

/**
 * Modernized browser Application implementation for current Arc.
 *
 * Derived from Arc's final official GWT backend, but updated to the current
 * Application contract: ApplicationType.web, String clipboard API, no Core.net,
 * Core.audio is always a non-null disabled Audio object for the first milestone,
 * and Application.defaultUpdate() is run once per frame.
 */
public abstract class GwtApplication implements EntryPoint, Application{
    private static AgentInfo agentInfo;

    protected TextArea log;
    GwtApplicationConfiguration config;
    GwtGraphics graphics;
    Preloader preloader;
    LoadingListener loadingListener;

    private GwtInput input;
    private Panel root;
    private final Seq<Runnable> runnables = new Seq<>();
    private final Seq<Runnable> runnablesHelper = new Seq<>();
    private final Seq<ApplicationListener> listeners = new Seq<>();
    private int lastWidth;
    private int lastHeight;
    private String clipboardText = "";

    public static AgentInfo agentInfo(){
        return agentInfo;
    }

    private static native AgentInfo computeAgentInfo() /*-{
        var userAgent = navigator.userAgent.toLowerCase();
        return {
            isFirefox : userAgent.indexOf("firefox") !== -1,
            isChrome : userAgent.indexOf("chrome") !== -1,
            isSafari : userAgent.indexOf("safari") !== -1,
            isOpera : userAgent.indexOf("opera") !== -1,
            isIE : userAgent.indexOf("msie") !== -1 || userAgent.indexOf("trident") !== -1,
            isMacOS : userAgent.indexOf("mac") !== -1,
            isLinux : userAgent.indexOf("linux") !== -1,
            isWindows : userAgent.indexOf("win") !== -1
        };
    }-*/;

    private static native void removeHostBoot() /*-{
        var boot = $doc.getElementById("boot");
        if(boot && boot.parentNode) boot.parentNode.removeChild(boot);
    }-*/;

    public abstract GwtApplicationConfiguration getConfig();

    public abstract ApplicationListener createApplicationListener();

    @Override
    public Seq<ApplicationListener> getListeners(){
        return listeners;
    }

    public String getPreloaderBaseURL(){
        return GWT.getHostPageBaseURL() + "assets/";
    }

    @Override
    public void onModuleLoad(){
        agentInfo = computeAgentInfo();
        removeHostBoot();
        // Establish the platform identity before any Mindustry class is allowed to
        // initialize. Vars performs static platform decisions and snapshots
        // Core.executor, so constructing ClientLauncher before Core.app exists can
        // permanently select native-only behavior.
        Core.app = this;
        Core.executor = new GwtExecutorService();
        config = getConfig();
        addListener(createApplicationListener());

        Log.setLogger(new GwtApplicationLogger(config.log));
        Log.setLogLevel(LogLevel.info);

        if(config.rootPanel != null){
            root = config.rootPanel;
        }else{
            Element element = Document.get().getElementById("embed-" + GWT.getModuleName());
            VerticalPanel panel = new VerticalPanel();
            panel.setWidth(config.width + "px");
            panel.setHeight(config.height + "px");
            panel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
            panel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
            if(element == null){
                RootPanel.get().add(panel);
                RootPanel.get().setWidth(config.width + "px");
                RootPanel.get().setHeight(config.height + "px");
            }else{
                element.appendChild(panel.getElement());
            }
            root = panel;
        }

        preloadAssets();
    }

    void preloadAssets(){
        final PreloaderCallback callback = getPreloaderCallback();
        preloader = createPreloader();
        preloader.preload("assets.txt", new PreloaderCallback(){
            @Override
            public void error(String file){
                callback.error(file);
            }

            @Override
            public void update(PreloaderState state){
                callback.update(state);
                if(state.hasEnded()){
                    getRootPanel().clear();
                    if(loadingListener != null) loadingListener.beforeSetup();
                    setupLoop();
                    addEventListeners();
                    if(loadingListener != null) loadingListener.afterSetup();
                }
            }
        });
    }

    public Widget getNoWebGLSupportWidget(){
        return new Label("This browser does not provide a usable WebGL context.");
    }

    void setupLoop(){
        try{
            graphics = new GwtGraphics(root, config);
        }catch(Throwable error){
            root.clear();
            root.add(getNoWebGLSupportWidget());
            Log.err("Unable to initialize WebGL", error);
            return;
        }

        lastWidth = graphics.getWidth();
        lastHeight = graphics.getHeight();

        // Core.app must be assigned before Audio(false) and listeners are constructed.
        Core.app = this;
        Core.graphics = graphics;
        Core.gl20 = graphics.getGL20();
        Core.gl30 = graphics.getGL30();
        Core.gl = Core.gl20;

        // First real browser milestone is intentionally muted. A disabled Audio object
        // keeps current Arc/Mindustry null-safe without touching native SoLoud.
        Core.audio = new Audio(false);
        Core.settings = new GwtSettings();
        Core.files = new GwtFiles(preloader);
        input = new GwtInput(graphics.canvas);
        Core.input = input;

        updateLogLabelSize();

        try{
            for(ApplicationListener listener : listeners){
                listener.init();
                listener.resize(graphics.getWidth(), graphics.getHeight());
            }
        }catch(Throwable error){
            Log.err("[GwtApplication] init failed", error);
            throw new RuntimeException(error);
        }

        AnimationScheduler.get().requestAnimationFrame(new AnimationCallback(){
            @Override
            public void execute(double timestamp){
                try{
                    mainLoop();
                }catch(Throwable error){
                    Log.err("[GwtApplication] frame failed", error);
                    throw new RuntimeException(error);
                }
                AnimationScheduler.get().requestAnimationFrame(this, graphics.canvas);
            }
        }, graphics.canvas);
    }

    void mainLoop(){
        graphics.update();

        if(graphics.getWidth() != lastWidth || graphics.getHeight() != lastHeight){
            lastWidth = graphics.getWidth();
            lastHeight = graphics.getHeight();
            Core.gl.glViewport(0, 0, lastWidth, lastHeight);
            for(ApplicationListener listener : listeners){
                listener.resize(lastWidth, lastHeight);
            }
        }

        runnablesHelper.addAll(runnables);
        runnables.clear();
        for(int i = 0; i < runnablesHelper.size; i++){
            runnablesHelper.get(i).run();
        }
        runnablesHelper.clear();

        input.update();
        graphics.frameId++;
        defaultUpdate();
        for(ApplicationListener listener : listeners){
            listener.update();
        }
        input.postUpdate();
    }

    public Panel getRootPanel(){
        return root;
    }

    public Preloader createPreloader(){
        return new Preloader(getPreloaderBaseURL());
    }

    public PreloaderCallback getPreloaderCallback(){
        final Panel preloaderPanel = new VerticalPanel();
        preloaderPanel.setStyleName("gdx-preloader");
        final Image logo = new Image(GWT.getModuleBaseURL() + "logo.png");
        logo.setStyleName("logo");
        preloaderPanel.add(logo);
        final Panel meterPanel = new SimplePanel();
        meterPanel.setStyleName("gdx-meter");
        final InlineHTML meter = new InlineHTML();
        final Style meterStyle = meter.getElement().getStyle();
        meterStyle.setWidth(0, Unit.PCT);
        meterPanel.add(meter);
        preloaderPanel.add(meterPanel);
        getRootPanel().add(preloaderPanel);
        return new PreloaderCallback(){
            @Override
            public void error(String file){
                Log.err("Preload failed: @", file);
            }

            @Override
            public void update(PreloaderState state){
                meterStyle.setWidth(100f * state.getProgress(), Unit.PCT);
            }
        };
    }

    private void updateLogLabelSize(){
        if(log != null){
            log.setSize(graphics == null ? "400px" : graphics.getWidth() + "px", "200px");
        }
    }

    @Override
    public ApplicationType getType(){
        return ApplicationType.web;
    }

    @Override
    public long getJavaHeap(){
        // Browser runtimes do not expose a portable Java heap figure.
        return 0L;
    }

    @Override
    public String getClipboardText(){
        return clipboardText;
    }

    @Override
    public void setClipboardText(String text){
        clipboardText = text == null ? "" : text;
        writeSystemClipboard(clipboardText);
    }

    private static native void writeSystemClipboard(String text) /*-{
        try {
            if ($wnd.navigator && $wnd.navigator.clipboard && $wnd.navigator.clipboard.writeText) {
                $wnd.navigator.clipboard.writeText(text);
            }
        } catch (ignored) {}
    }-*/;

    @Override
    public boolean openURI(String uri){
        return openURI0(uri, config == null || config.openURLInNewWindow);
    }

    private static native boolean openURI0(String uri, boolean newWindow) /*-{
        try {
            if (newWindow) {
                var opened = $wnd.open(uri, '_blank', 'noopener,noreferrer');
                return opened != null;
            }
            $wnd.location.href = uri;
            return true;
        } catch (e) {
            return false;
        }
    }-*/;

    @Override
    public void post(Runnable runnable){
        if(runnable != null) runnables.add(runnable);
    }

    @Override
    public void exit(){
        // Browsers do not allow arbitrary scripts to close a normal tab.
    }

    public String getBaseUrl(){
        return preloader.baseUrl;
    }

    public Preloader getPreloader(){
        return preloader;
    }

    public CanvasElement getCanvasElement(){
        return graphics.canvas;
    }

    public LoadingListener getLoadingListener(){
        return loadingListener;
    }

    public void setLoadingListener(LoadingListener loadingListener){
        this.loadingListener = loadingListener;
    }

    private native void addEventListeners() /*-{
        var self = this;
        var eventName = null;
        if ("hidden" in $doc) eventName = "visibilitychange";
        else if ("webkitHidden" in $doc) eventName = "webkitvisibilitychange";
        else if ("mozHidden" in $doc) eventName = "mozvisibilitychange";
        else if ("msHidden" in $doc) eventName = "msvisibilitychange";

        if (eventName !== null) {
            $doc.addEventListener(eventName, function() {
                self.@arc.backend.gwt.GwtApplication::onVisibilityChange(Z)($doc['hidden'] !== true);
            });
        }
    }-*/;

    private void onVisibilityChange(boolean visible){
        for(ApplicationListener listener : listeners){
            if(visible) listener.resume();
            else listener.pause();
        }
    }

    @Override
    public void dispose(){
        for(ApplicationListener listener : listeners){
            try{
                listener.dispose();
            }catch(Throwable error){
                Log.err(error);
            }
        }
        Application.super.dispose();
    }

    public interface LoadingListener{
        void beforeSetup();
        void afterSetup();
    }

    public static class AgentInfo extends JavaScriptObject{
        protected AgentInfo(){}

        public final native boolean isFirefox() /*-{ return this.isFirefox; }-*/;
        public final native boolean isChrome() /*-{ return this.isChrome; }-*/;
        public final native boolean isSafari() /*-{ return this.isSafari; }-*/;
        public final native boolean isOpera() /*-{ return this.isOpera; }-*/;
        public final native boolean isIE() /*-{ return this.isIE; }-*/;
        public final native boolean isMacOS() /*-{ return this.isMacOS; }-*/;
        public final native boolean isLinux() /*-{ return this.isLinux; }-*/;
        public final native boolean isWindows() /*-{ return this.isWindows; }-*/;
    }
}
