package arc.backend.gwt;

import arc.*;
import arc.input.*;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.KeyCodes;

/**
 * Browser input backend updated for current Arc's InputEventQueue/KeyboardDevice model.
 * Mouse, keyboard, wheel and multi-touch events are translated into the same event
 * queue used by Arc's current SDL backend.
 */
public class GwtInput extends Input{
    private static final int MAX_TOUCHES = 20;

    private final CanvasElement canvas;
    private final InputEventQueue queue = new InputEventQueue();
    private final boolean[] touched = new boolean[MAX_TOUCHES];
    private final int[] touchIds = new int[MAX_TOUCHES];
    private final int[] touchX = new int[MAX_TOUCHES];
    private final int[] touchY = new int[MAX_TOUCHES];
    private final int[] deltaX = new int[MAX_TOUCHES];
    private final int[] deltaY = new int[MAX_TOUCHES];
    private boolean hasFocus = true;
    private int mousePressed;

    public GwtInput(CanvasElement canvas){
        this.canvas = canvas;
        for(int i = 0; i < touchIds.length; i++) touchIds[i] = -1;
        canvas.setPropertyInt("tabIndex", 0);
        hookEvents();
    }

    /** Called before ApplicationListener.update(). */
    void update(){
        queue.setProcessor(inputMultiplexer);
        queue.drain();
    }

    /** Called after ApplicationListener.update(). */
    void postUpdate(){
        keyboard.postUpdate();
        for(int i = 0; i < MAX_TOUCHES; i++){
            deltaX[i] = 0;
            deltaY[i] = 0;
        }
    }

    @Override
    public int mouseX(){
        return touchX[0];
    }

    @Override
    public int mouseX(int pointer){
        return valid(pointer) ? touchX[pointer] : 0;
    }

    @Override
    public int deltaX(){
        return deltaX[0];
    }

    @Override
    public int deltaX(int pointer){
        return valid(pointer) ? deltaX[pointer] : 0;
    }

    @Override
    public int mouseY(){
        return touchY[0];
    }

    @Override
    public int mouseY(int pointer){
        return valid(pointer) ? touchY[pointer] : 0;
    }

    @Override
    public int deltaY(){
        return deltaY[0];
    }

    @Override
    public int deltaY(int pointer){
        return valid(pointer) ? deltaY[pointer] : 0;
    }

    @Override
    public boolean isTouched(){
        if(mousePressed > 0) return true;
        for(boolean value : touched) if(value) return true;
        return false;
    }

    @Override
    public boolean justTouched(){
        return keyTap(KeyCode.mouseLeft) || keyTap(KeyCode.mouseRight) || keyTap(KeyCode.mouseMiddle);
    }

    @Override
    public boolean isTouched(int pointer){
        return valid(pointer) && touched[pointer];
    }

    @Override
    public long getCurrentEventTime(){
        return queue.getCurrentEventTime();
    }

    @Override
    public boolean isPeripheralAvailable(Peripheral peripheral){
        return peripheral == Peripheral.hardwareKeyboard ||
            (peripheral == Peripheral.multitouchScreen && isTouchScreen());
    }

    private boolean valid(int pointer){
        return pointer >= 0 && pointer < MAX_TOUCHES;
    }

    private static native boolean isTouchScreen() /*-{
        return ('ontouchstart' in $wnd) || (($wnd.navigator.maxTouchPoints || 0) > 0) || (($wnd.navigator.msMaxTouchPoints || 0) > 0);
    }-*/;

    static native void addEventListener(JavaScriptObject target, String name, GwtInput handler, boolean capture) /*-{
        target.addEventListener(name, function(e) {
            handler.@arc.backend.gwt.GwtInput::handleEvent(Lcom/google/gwt/dom/client/NativeEvent;)(e);
        }, capture);
    }-*/;

    private static native JavaScriptObject getWindow() /*-{
        return $wnd;
    }-*/;

    private static native void focusCanvas(CanvasElement canvas) /*-{
        if(canvas && canvas.focus) canvas.focus();
    }-*/;

    private static native float getMouseWheelVelocity(NativeEvent event) /*-{
        if (typeof event.deltaY === 'number' && event.deltaY !== 0) return event.deltaY / 100.0;
        if (typeof event.wheelDelta === 'number' && event.wheelDelta !== 0) return -event.wheelDelta / 120.0;
        if (typeof event.detail === 'number') return event.detail / 3.0;
        return 0.0;
    }-*/;

    private void hookEvents(){
        addEventListener(canvas, "mousedown", this, true);
        addEventListener(Document.get(), "mouseup", this, true);
        addEventListener(Document.get(), "mousemove", this, true);
        addEventListener(canvas, "wheel", this, true);
        addEventListener(Document.get(), "keydown", this, false);
        addEventListener(Document.get(), "keyup", this, false);
        addEventListener(Document.get(), "keypress", this, false);
        addEventListener(getWindow(), "blur", this, false);
        addEventListener(canvas, "touchstart", this, true);
        addEventListener(canvas, "touchmove", this, true);
        addEventListener(canvas, "touchcancel", this, true);
        addEventListener(canvas, "touchend", this, true);
        addEventListener(canvas, "contextmenu", this, true);
    }

    private int relativeX(NativeEvent event){
        float scale = canvas.getClientWidth() == 0 ? 1f : canvas.getWidth() * 1f / canvas.getClientWidth();
        return Math.round(scale * (event.getClientX() - canvas.getAbsoluteLeft() + canvas.getScrollLeft() + canvas.getOwnerDocument().getScrollLeft()));
    }

    private int relativeY(NativeEvent event){
        float scale = canvas.getClientHeight() == 0 ? 1f : canvas.getHeight() * 1f / canvas.getClientHeight();
        int top = Math.round(scale * (event.getClientY() - canvas.getAbsoluteTop() + canvas.getScrollTop() + canvas.getOwnerDocument().getScrollTop()));
        return canvas.getHeight() - top;
    }

    private int relativeX(Touch touch){
        float scale = canvas.getClientWidth() == 0 ? 1f : canvas.getWidth() * 1f / canvas.getClientWidth();
        return Math.round(scale * touch.getRelativeX(canvas));
    }

    private int relativeY(Touch touch){
        float scale = canvas.getClientHeight() == 0 ? 1f : canvas.getHeight() * 1f / canvas.getClientHeight();
        int top = Math.round(scale * touch.getRelativeY(canvas));
        return canvas.getHeight() - top;
    }

    private KeyCode mouseButton(int button){
        if(button == NativeEvent.BUTTON_RIGHT) return KeyCode.mouseRight;
        if(button == NativeEvent.BUTTON_MIDDLE) return KeyCode.mouseMiddle;
        return KeyCode.mouseLeft;
    }

    private static KeyCode keyForCode(int code){
        switch(code){
            case KeyCodes.KEY_ALT: return KeyCode.altLeft;
            case KeyCodes.KEY_BACKSPACE: return KeyCode.backspace;
            case KeyCodes.KEY_CTRL: return KeyCode.controlLeft;
            case KeyCodes.KEY_DELETE: return KeyCode.forwardDel;
            case KeyCodes.KEY_DOWN: return KeyCode.down;
            case KeyCodes.KEY_END: return KeyCode.end;
            case KeyCodes.KEY_ENTER: return KeyCode.enter;
            case KeyCodes.KEY_ESCAPE: return KeyCode.escape;
            case KeyCodes.KEY_HOME: return KeyCode.home;
            case KeyCodes.KEY_LEFT: return KeyCode.left;
            case KeyCodes.KEY_PAGEDOWN: return KeyCode.pageDown;
            case KeyCodes.KEY_PAGEUP: return KeyCode.pageUp;
            case KeyCodes.KEY_RIGHT: return KeyCode.right;
            case KeyCodes.KEY_SHIFT: return KeyCode.shiftLeft;
            case KeyCodes.KEY_TAB: return KeyCode.tab;
            case KeyCodes.KEY_UP: return KeyCode.up;
            case 19: return KeyCode.pause;
            case 20: return KeyCode.capsLock;
            case 32: return KeyCode.space;
            case 45: return KeyCode.insert;
            case 48: return KeyCode.num0;
            case 49: return KeyCode.num1;
            case 50: return KeyCode.num2;
            case 51: return KeyCode.num3;
            case 52: return KeyCode.num4;
            case 53: return KeyCode.num5;
            case 54: return KeyCode.num6;
            case 55: return KeyCode.num7;
            case 56: return KeyCode.num8;
            case 57: return KeyCode.num9;
            case 65: return KeyCode.a;
            case 66: return KeyCode.b;
            case 67: return KeyCode.c;
            case 68: return KeyCode.d;
            case 69: return KeyCode.e;
            case 70: return KeyCode.f;
            case 71: return KeyCode.g;
            case 72: return KeyCode.h;
            case 73: return KeyCode.i;
            case 74: return KeyCode.j;
            case 75: return KeyCode.k;
            case 76: return KeyCode.l;
            case 77: return KeyCode.m;
            case 78: return KeyCode.n;
            case 79: return KeyCode.o;
            case 80: return KeyCode.p;
            case 81: return KeyCode.q;
            case 82: return KeyCode.r;
            case 83: return KeyCode.s;
            case 84: return KeyCode.t;
            case 85: return KeyCode.u;
            case 86: return KeyCode.v;
            case 87: return KeyCode.w;
            case 88: return KeyCode.x;
            case 89: return KeyCode.y;
            case 90: return KeyCode.z;
            case 96: return KeyCode.numpad0;
            case 97: return KeyCode.numpad1;
            case 98: return KeyCode.numpad2;
            case 99: return KeyCode.numpad3;
            case 100: return KeyCode.numpad4;
            case 101: return KeyCode.numpad5;
            case 102: return KeyCode.numpad6;
            case 103: return KeyCode.numpad7;
            case 104: return KeyCode.numpad8;
            case 105: return KeyCode.numpad9;
            case 106: return KeyCode.asterisk;
            case 107: return KeyCode.plus;
            case 109: return KeyCode.minus;
            case 110: return KeyCode.period;
            case 111: return KeyCode.slash;
            case 112: return KeyCode.f1;
            case 113: return KeyCode.f2;
            case 114: return KeyCode.f3;
            case 115: return KeyCode.f4;
            case 116: return KeyCode.f5;
            case 117: return KeyCode.f6;
            case 118: return KeyCode.f7;
            case 119: return KeyCode.f8;
            case 120: return KeyCode.f9;
            case 121: return KeyCode.f10;
            case 122: return KeyCode.f11;
            case 123: return KeyCode.f12;
            case 144: return KeyCode.num;
            case 145: return KeyCode.scrollLock;
            case 186: return KeyCode.semicolon;
            case 187: return KeyCode.equals;
            case 188: return KeyCode.comma;
            case 189: return KeyCode.minus;
            case 190: return KeyCode.period;
            case 191: return KeyCode.slash;
            case 192: return KeyCode.backtick;
            case 219: return KeyCode.leftBracket;
            case 220: return KeyCode.backslash;
            case 221: return KeyCode.rightBracket;
            case 222: return KeyCode.apostrophe;
            default: return KeyCode.unknown;
        }
    }

    private void handleEvent(NativeEvent event){
        String type = event.getType();

        if(type.equals("contextmenu")){
            event.preventDefault();
            return;
        }

        if(type.equals("mousedown")){
            hasFocus = true;
            focusCanvas(canvas);
            int x = relativeX(event), y = relativeY(event);
            deltaX[0] = x - touchX[0];
            deltaY[0] = y - touchY[0];
            touchX[0] = x;
            touchY[0] = y;
            mousePressed++;
            queue.touchDown(x, y, 0, mouseButton(event.getButton()));
            event.preventDefault();
            return;
        }

        if(type.equals("mouseup")){
            int x = relativeX(event), y = relativeY(event);
            deltaX[0] = x - touchX[0];
            deltaY[0] = y - touchY[0];
            touchX[0] = x;
            touchY[0] = y;
            mousePressed = Math.max(0, mousePressed - 1);
            queue.touchUp(x, y, 0, mouseButton(event.getButton()));
            return;
        }

        if(type.equals("mousemove")){
            int x = relativeX(event), y = relativeY(event);
            deltaX[0] = x - touchX[0];
            deltaY[0] = y - touchY[0];
            touchX[0] = x;
            touchY[0] = y;
            if(mousePressed > 0) queue.touchDragged(x, y, 0);
            else queue.mouseMoved(x, y);
            return;
        }

        if(type.equals("wheel")){
            queue.scrolled(0f, getMouseWheelVelocity(event));
            event.preventDefault();
            return;
        }

        if(type.equals("blur")){
            hasFocus = false;
            return;
        }

        if(type.equals("keydown") && hasFocus){
            KeyCode code = keyForCode(event.getKeyCode());
            if(!keyDown(code)) queue.keyDown(code);
            if(code == KeyCode.backspace) queue.keyTyped('\b');
            else if(code == KeyCode.tab) queue.keyTyped('\t');
            else if(code == KeyCode.enter) queue.keyTyped((char)13);
            else if(code == KeyCode.forwardDel || code == KeyCode.del) queue.keyTyped((char)127);
            if(code != KeyCode.unknown) event.preventDefault();
            return;
        }

        if(type.equals("keyup") && hasFocus){
            KeyCode code = keyForCode(event.getKeyCode());
            queue.keyUp(code);
            if(code != KeyCode.unknown) event.preventDefault();
            return;
        }

        if(type.equals("keypress") && hasFocus){
            int charCode = event.getCharCode();
            if(charCode != 0) queue.keyTyped((char)charCode);
            return;
        }

        if(type.equals("touchstart")){
            hasFocus = true;
            JsArray<Touch> touches = event.getChangedTouches();
            for(int i = 0; i < touches.length(); i++){
                Touch touch = touches.get(i);
                int pointer = allocatePointer(touch.getIdentifier());
                if(pointer < 0) continue;
                int x = relativeX(touch), y = relativeY(touch);
                touched[pointer] = true;
                touchX[pointer] = x;
                touchY[pointer] = y;
                deltaX[pointer] = deltaY[pointer] = 0;
                queue.touchDown(x, y, pointer, KeyCode.mouseLeft);
            }
            event.preventDefault();
            return;
        }

        if(type.equals("touchmove")){
            JsArray<Touch> touches = event.getChangedTouches();
            for(int i = 0; i < touches.length(); i++){
                Touch touch = touches.get(i);
                int pointer = findPointer(touch.getIdentifier());
                if(pointer < 0) continue;
                int x = relativeX(touch), y = relativeY(touch);
                deltaX[pointer] = x - touchX[pointer];
                deltaY[pointer] = y - touchY[pointer];
                touchX[pointer] = x;
                touchY[pointer] = y;
                queue.touchDragged(x, y, pointer);
            }
            event.preventDefault();
            return;
        }

        if(type.equals("touchend") || type.equals("touchcancel")){
            JsArray<Touch> touches = event.getChangedTouches();
            for(int i = 0; i < touches.length(); i++){
                Touch touch = touches.get(i);
                int pointer = findPointer(touch.getIdentifier());
                if(pointer < 0) continue;
                int x = relativeX(touch), y = relativeY(touch);
                deltaX[pointer] = x - touchX[pointer];
                deltaY[pointer] = y - touchY[pointer];
                touchX[pointer] = x;
                touchY[pointer] = y;
                touched[pointer] = false;
                touchIds[pointer] = -1;
                queue.touchUp(x, y, pointer, KeyCode.mouseLeft);
            }
            event.preventDefault();
        }
    }

    private int allocatePointer(int id){
        int existing = findPointer(id);
        if(existing >= 0) return existing;
        for(int i = 0; i < MAX_TOUCHES; i++){
            if(touchIds[i] == -1){
                touchIds[i] = id;
                return i;
            }
        }
        return -1;
    }

    private int findPointer(int id){
        for(int i = 0; i < MAX_TOUCHES; i++){
            if(touchIds[i] == id) return i;
        }
        return -1;
    }
}
