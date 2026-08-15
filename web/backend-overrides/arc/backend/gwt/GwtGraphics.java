package arc.backend.gwt;

import arc.*;
import arc.Application.ApplicationType;
import arc.Graphics.Cursor;
import arc.Graphics.Cursor.SystemCursor;
import arc.graphics.Pixmap;
import arc.graphics.GL20;
import arc.graphics.GL30;
import arc.graphics.gl.GLVersion;
import arc.util.ArcRuntimeException;
import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.webgl.client.WebGLContextAttributes;
import com.google.gwt.webgl.client.WebGLRenderingContext;

/** Current-Arc implementation of the historical WebGL graphics backend. */
public class GwtGraphics extends Graphics{
    CanvasElement canvas;
    WebGLRenderingContext context;
    GLVersion glVersion;
    GL20 gl;
    float fps;
    long lastTimeStamp = System.currentTimeMillis();
    long frameId = -1;
    float deltaTime;
    float elapsed;
    int frames;
    final GwtApplicationConfiguration config;

    public GwtGraphics(Panel root, GwtApplicationConfiguration config){
        Canvas canvasWidget = Canvas.createIfSupported();
        if(canvasWidget == null) throw new ArcRuntimeException("Canvas not supported");

        canvas = canvasWidget.getCanvasElement();
        root.add(canvasWidget);
        this.config = config;
        resizeCanvas(Math.max(1, Window.getClientWidth()), Math.max(1, Window.getClientHeight()));

        WebGLContextAttributes attributes = WebGLContextAttributes.create();
        attributes.setAntialias(config.antialiasing);
        attributes.setStencil(config.stencil);
        attributes.setAlpha(config.alpha);
        attributes.setPremultipliedAlpha(config.premultipliedAlpha);
        attributes.setPreserveDrawingBuffer(config.preserveDrawingBuffer);

        context = WebGLRenderingContext.getContext(canvas, attributes);
        if(context == null) throw new ArcRuntimeException("Unable to create WebGL context");
        context.viewport(0, 0, canvas.getWidth(), canvas.getHeight());

        gl = config.useDebugGL ? new GwtGL20Debug(context) : new GwtGL20(context);
        glVersion = new GLVersion(
            ApplicationType.web,
            gl.glGetString(GL20.GL_VERSION),
            gl.glGetString(GL20.GL_VENDOR),
            gl.glGetString(GL20.GL_RENDERER)
        );
    }

    private void resizeCanvas(int width, int height){
        canvas.setWidth(Math.max(1, width));
        canvas.setHeight(Math.max(1, height));
        canvas.getStyle().setProperty("width", Math.max(1, width) + "px");
        canvas.getStyle().setProperty("height", Math.max(1, height) + "px");
    }

    public WebGLRenderingContext getContext(){
        return context;
    }

    @Override
    public GL20 getGL20(){
        return gl;
    }

    @Override
    public void setGL20(GL20 gl20){
        gl = gl20;
        Core.gl = gl20;
        Core.gl20 = gl20;
    }

    @Override
    public GL30 getGL30(){
        return null;
    }

    @Override
    public void setGL30(GL30 gl30){
        Core.gl30 = gl30;
    }

    @Override
    public int getWidth(){
        return canvas.getWidth();
    }

    @Override
    public int getHeight(){
        return canvas.getHeight();
    }

    @Override
    public int getBackBufferWidth(){
        return canvas.getWidth();
    }

    @Override
    public int getBackBufferHeight(){
        return canvas.getHeight();
    }

    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond(){
        return (int)fps;
    }

    @Override
    public GLVersion getGLVersion(){
        return glVersion;
    }

    @Override
    public float getPpiX(){
        return 96f * devicePixelRatio();
    }

    @Override
    public float getPpiY(){
        return 96f * devicePixelRatio();
    }

    @Override
    public float getPpcX(){
        return getPpiX() / 2.54f;
    }

    @Override
    public float getPpcY(){
        return getPpiY() / 2.54f;
    }

    @Override
    public float getDensity(){
        return getPpiX() / 160f;
    }

    private static native float devicePixelRatio() /*-{
        return $wnd.devicePixelRatio || 1;
    }-*/;

    @Override
    public void setTitle(String title){
        setDocumentTitle(title == null ? "Mindustry" : title);
    }

    private static native void setDocumentTitle(String title) /*-{
        $doc.title = title;
    }-*/;

    @Override
    public void setVSync(boolean vsync){
        // requestAnimationFrame is synchronized to browser presentation.
    }

    @Override
    public BufferFormat getBufferFormat(){
        return new BufferFormat(8, 8, 8, 8, 16, config.stencil ? 8 : 0, 0, false);
    }

    @Override
    public boolean supportsExtension(String extensionName){
        return context.getExtension(extensionName) != null;
    }

    @Override
    public boolean isContinuousRendering(){
        return true;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous){
        // Browser backend currently renders continuously via requestAnimationFrame.
    }

    @Override
    public void requestRendering(){
        // No-op while continuous rendering is enabled.
    }

    @Override
    public boolean isFullscreen(){
        return isFullscreenJSNI();
    }

    @Override
    public boolean setFullscreen(boolean fullscreen){
        if(fullscreen == isFullscreen()) return true;
        if(fullscreen) return requestFullscreen(canvas);
        exitFullscreen();
        return true;
    }

    @Override
    public void setWindowSize(int width, int height){
        if(!isFullscreen()) resizeCanvas(width, height);
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot){
        return new GwtCursor(pixmap, xHotspot, yHotspot);
    }

    @Override
    protected void setCursor(Cursor cursor){
        if(cursor instanceof GwtCursor){
            canvas.getStyle().setProperty("cursor", ((GwtCursor)cursor).cssCursorProperty);
        }
    }

    @Override
    protected void setSystemCursor(SystemCursor systemCursor){
        canvas.getStyle().setProperty("cursor", GwtCursor.getNameForSystemCursor(systemCursor));
    }

    public void update(){
        int width = Math.max(1, Window.getClientWidth());
        int height = Math.max(1, Window.getClientHeight());
        if(width != canvas.getWidth() || height != canvas.getHeight()) resizeCanvas(width, height);

        long now = System.currentTimeMillis();
        deltaTime = Math.max(0f, Math.min((now - lastTimeStamp) / 1000f, 0.25f));
        lastTimeStamp = now;
        elapsed += deltaTime;
        frames++;
        if(elapsed >= 1f){
            fps = frames / elapsed;
            elapsed = 0f;
            frames = 0;
        }
    }

    private static native boolean isFullscreenJSNI() /*-{
        return !!($doc.fullscreenElement || $doc.webkitFullscreenElement || $doc.mozFullScreenElement || $doc.msFullscreenElement);
    }-*/;

    private static native boolean requestFullscreen(CanvasElement element) /*-{
        try {
            var fn = element.requestFullscreen || element.webkitRequestFullscreen || element.mozRequestFullScreen || element.msRequestFullscreen;
            if (!fn) return false;
            fn.call(element);
            return true;
        } catch (e) {
            return false;
        }
    }-*/;

    private static native void exitFullscreen() /*-{
        var fn = $doc.exitFullscreen || $doc.webkitExitFullscreen || $doc.mozCancelFullScreen || $doc.msExitFullscreen;
        if (fn) fn.call($doc);
    }-*/;
}
