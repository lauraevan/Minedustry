package mindustry.web.teavm;

import arc.*;
import arc.graphics.*;
import arc.graphics.gl.*;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.webgl.WebGLRenderingContext;

public final class WebGraphics extends Graphics{
    final HTMLCanvasElement canvas;
    private final WebGLRenderingContext context;
    private GL20 gl20;
    private final GLVersion version;
    private final BufferFormat format = new BufferFormat(8,8,8,8,24,8,0,false);
    private boolean continuous=true;
    private int width,height,backWidth,backHeight,fps,frames;
    private long frameId;
    private double lastTime, fpsTime;
    private float delta=1f/60f;

    public WebGraphics(HTMLCanvasElement canvas){
        this.canvas=canvas;
        this.context=createContext(canvas);
        if(context==null) throw new IllegalStateException("WebGL 1 is unavailable");
        this.gl20 = new TeaVMGL20(context);
        this.version = new GLVersion(Application.ApplicationType.web,
            safeString(context.getParameterString(WebGLRenderingContext.VERSION),"WebGL 1.0"),
            safeString(context.getParameterString(WebGLRenderingContext.VENDOR),"browser"),
            safeString(context.getParameterString(WebGLRenderingContext.RENDERER),"WebGL"));
        resizeBackingStore();
    }

    void update(double now){
        resizeBackingStore();
        if(lastTime!=0) delta=(float)Math.min(0.25,Math.max(0.0001,(now-lastTime)/1000.0));
        lastTime=now; frameId++; frames++;
        if(fpsTime==0) fpsTime=now;
        if(now-fpsTime>=1000){ fps=frames; frames=0; fpsTime=now; }
    }

    private void resizeBackingStore(){
        int logicalW=Math.max(1,clientWidth(canvas)), logicalH=Math.max(1,clientHeight(canvas));
        float d=Math.max(1f,devicePixelRatio());
        int physicalW=Math.max(1,Math.round(logicalW*d)), physicalH=Math.max(1,Math.round(logicalH*d));
        boolean backingChanged=canvas.getWidth()!=physicalW || canvas.getHeight()!=physicalH;
        if(canvas.getWidth()!=physicalW) canvas.setWidth(physicalW);
        if(canvas.getHeight()!=physicalH) canvas.setHeight(physicalH);
        width=logicalW; height=logicalH; backWidth=physicalW; backHeight=physicalH;
        if(backingChanged && gl20!=null) gl20.glViewport(0,0,physicalW,physicalH);
    }

    private static String safeString(String s,String fallback){ return s==null?fallback:s; }

    @Override public GL20 getGL20(){ return gl20; }
    @Override public void setGL20(GL20 gl20){ this.gl20=gl20; Core.gl=gl20; Core.gl20=gl20; }
    @Override public GL30 getGL30(){ return null; }
    @Override public void setGL30(GL30 gl30){}
    @Override public int getWidth(){ return width; }
    @Override public int getHeight(){ return height; }
    @Override public int getBackBufferWidth(){ return backWidth; }
    @Override public int getBackBufferHeight(){ return backHeight; }
    @Override public long getFrameId(){ return frameId; }
    @Override public float getDeltaTime(){ return delta; }
    @Override public int getFramesPerSecond(){ return fps; }
    @Override public GLVersion getGLVersion(){ return version; }
    @Override public float getPpiX(){ return 96f*devicePixelRatio(); }
    @Override public float getPpiY(){ return 96f*devicePixelRatio(); }
    @Override public float getPpcX(){ return getPpiX()/2.54f; }
    @Override public float getPpcY(){ return getPpiY()/2.54f; }
    @Override public float getDensity(){ return devicePixelRatio(); }
    @Override public void setTitle(String title){ setDocumentTitle(title); }
    @Override public void setVSync(boolean vsync){}
    @Override public BufferFormat getBufferFormat(){ return format; }
    @Override public boolean supportsExtension(String extension){ return hasExtension(context,extension); }
    @Override public boolean isContinuousRendering(){ return continuous; }
    @Override public void setContinuousRendering(boolean value){ continuous=value; }
    @Override public void requestRendering(){}
    @Override public boolean isFullscreen(){ return fullscreen(); }
    @Override public boolean setFullscreen(boolean value){ return setFullscreen0(canvas,value); }
    @Override public Cursor newCursor(Pixmap pixmap,int xHotspot,int yHotspot){ return Cursor.SystemCursor.arrow; }
    @Override protected void setCursor(Cursor cursor){}
    @Override protected void setSystemCursor(Cursor.SystemCursor cursor){ setCursorName(canvas,cursor.name()); }

    @JSBody(params="canvas", script="return canvas.getContext('webgl',{alpha:false,antialias:true,stencil:true,premultipliedAlpha:false,preserveDrawingBuffer:false});")
    private static native WebGLRenderingContext createContext(HTMLCanvasElement canvas);
    @JSBody(params="canvas", script="return Math.max(1,canvas.clientWidth|0);") private static native int clientWidth(HTMLCanvasElement canvas);
    @JSBody(params="canvas", script="return Math.max(1,canvas.clientHeight|0);") private static native int clientHeight(HTMLCanvasElement canvas);
    @JSBody(script="return window.devicePixelRatio||1;") private static native float devicePixelRatio();
    @JSBody(params="title", script="document.title=title;") private static native void setDocumentTitle(String title);
    @JSBody(script="return !!document.fullscreenElement;") private static native boolean fullscreen();
    @JSBody(params={"canvas","value"}, script="try{if(value){if(canvas.requestFullscreen)canvas.requestFullscreen();}else if(document.exitFullscreen){document.exitFullscreen();}return true;}catch(e){return false;}") private static native boolean setFullscreen0(HTMLCanvasElement canvas,boolean value);
    @JSBody(params={"gl","ext"}, script="try{if(gl.getExtension(ext))return true;if(ext.indexOf('GL_')===0&&gl.getExtension(ext.substring(3)))return true;return false;}catch(e){return false;}") private static native boolean hasExtension(WebGLRenderingContext gl,String ext);
    @JSBody(params={"canvas","name"}, script="var m={arrow:'default',ibeam:'text',crosshair:'crosshair',hand:'pointer',horizontalResize:'ew-resize',verticalResize:'ns-resize'};canvas.style.cursor=m[name]||'default';") private static native void setCursorName(HTMLCanvasElement canvas,String name);
}
