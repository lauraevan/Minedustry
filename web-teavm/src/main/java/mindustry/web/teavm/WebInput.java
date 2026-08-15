package mindustry.web.teavm;

import arc.*;
import arc.input.*;
import org.teavm.jso.*;
import org.teavm.jso.dom.html.HTMLCanvasElement;

public final class WebInput extends Input{
    private static final int MAX=20;
    @JSFunctor interface PointerCb extends JSObject{ void call(int x,int y,int id,int button,int buttons,String type); }
    @JSFunctor interface WheelCb extends JSObject{ void call(float dx,float dy); }
    @JSFunctor interface KeyCb extends JSObject{ void call(int keyCode,int charCode); }
    @JSFunctor interface VoidCb extends JSObject{ void call(); }
    @JSFunctor interface TextCb extends JSObject{ void call(String value); }

    private final InputEventQueue queue=new InputEventQueue();
    private final boolean[] touched=new boolean[MAX];
    private final int[] ids=new int[MAX], x=new int[MAX], y=new int[MAX], dx=new int[MAX], dy=new int[MAX];
    private int mousePressed;
    private boolean showingTextInput;

    public WebInput(HTMLCanvasElement canvas){
        for(int i=0;i<MAX;i++) ids[i]=-1;
        hook(canvas,this::down,this::move,this::up,(sx,sy)->queue.scrolled(sx,sy),
            (code,ch)->keyDown0(code),(code,ch)->keyUp0(code),(code,ch)->{ if(ch!=0) queue.keyTyped((char)ch); },this::blur);
    }
    void update(){ queue.setProcessor(inputMultiplexer); queue.drain(); }
    void postUpdate(){ keyboard.postUpdate(); for(int i=0;i<MAX;i++){dx[i]=dy[i]=0;} }
    @Override public int mouseX(){return x[0];} @Override public int mouseX(int p){return valid(p)?x[p]:0;}
    @Override public int deltaX(){return dx[0];} @Override public int deltaX(int p){return valid(p)?dx[p]:0;}
    @Override public int mouseY(){return y[0];} @Override public int mouseY(int p){return valid(p)?y[p]:0;}
    @Override public int deltaY(){return dy[0];} @Override public int deltaY(int p){return valid(p)?dy[p]:0;}
    @Override public boolean isTouched(){ if(mousePressed>0)return true; for(boolean b:touched)if(b)return true; return false; }
    @Override public boolean justTouched(){return keyTap(KeyCode.mouseLeft)||keyTap(KeyCode.mouseRight)||keyTap(KeyCode.mouseMiddle);}
    @Override public boolean isTouched(int p){return valid(p)&&touched[p];}
    @Override public long getCurrentEventTime(){return queue.getCurrentEventTime();}
    @Override public boolean isPeripheralAvailable(Peripheral p){
        if(p==Peripheral.multitouchScreen)return true;
        if(p==Peripheral.onscreenKeyboard)return Core.app!=null && Core.app.isMobile();
        if(p==Peripheral.hardwareKeyboard)return Core.app==null || !Core.app.isMobile();
        return false;
    }
    @Override public void getTextInput(TextInput input){
        if(input==null)return;
        showingTextInput=true;
        browserPrompt(input.title,input.message,input.text,input.maxLength,input.numeric,
            value->{showingTextInput=false;input.accepted.get(value);},
            ()->{showingTextInput=false;input.canceled.run();});
    }
    @Override public boolean isShowingTextInput(){return showingTextInput;}
    private boolean valid(int p){return p>=0&&p<MAX;}
    private int find(int id){for(int i=0;i<MAX;i++)if(ids[i]==id)return i;return -1;}
    private int alloc(int id){int p=find(id);if(p>=0)return p;for(int i=0;i<MAX;i++)if(ids[i]<0){ids[i]=id;return i;}return -1;}
    private void setPos(int p,int nx,int ny){dx[p]=nx-x[p];dy[p]=ny-y[p];x[p]=nx;y[p]=ny;}
    private void down(int nx,int ny,int id,int button,int buttons,String type){int p="mouse".equals(type)?0:alloc(id);if(p<0)return;setPos(p,nx,ny);touched[p]=true;if(p==0)mousePressed++;queue.touchDown(nx,ny,p,button(button));}
    private void move(int nx,int ny,int id,int button,int buttons,String type){int p="mouse".equals(type)?0:find(id);if(p<0)return;setPos(p,nx,ny);if((p==0&&buttons!=0)||touched[p])queue.touchDragged(nx,ny,p);else queue.mouseMoved(nx,ny);}
    private void up(int nx,int ny,int id,int button,int buttons,String type){int p="mouse".equals(type)?0:find(id);if(p<0)return;setPos(p,nx,ny);queue.touchUp(nx,ny,p,button(button));touched[p]=false;if(p==0)mousePressed=Math.max(0,mousePressed-1);else ids[p]=-1;}
    private void blur(){for(int i=0;i<MAX;i++){touched[i]=false;ids[i]=-1;}mousePressed=0;}
    private KeyCode button(int b){return b==2?KeyCode.mouseRight:b==1?KeyCode.mouseMiddle:KeyCode.mouseLeft;}
    private void keyDown0(int code){KeyCode k=key(code);if(k!=KeyCode.unknown&&!keyDown(k))queue.keyDown(k);}
    private void keyUp0(int code){KeyCode k=key(code);if(k!=KeyCode.unknown)queue.keyUp(k);}
    private static KeyCode key(int c){
        switch(c){
            case 8:return KeyCode.backspace; case 9:return KeyCode.tab; case 13:return KeyCode.enter;
            case 16:return KeyCode.shiftLeft; case 17:return KeyCode.controlLeft; case 18:return KeyCode.altLeft;
            case 27:return KeyCode.escape; case 32:return KeyCode.space;
            case 33:return KeyCode.pageUp; case 34:return KeyCode.pageDown; case 35:return KeyCode.end; case 36:return KeyCode.home;
            case 37:return KeyCode.left; case 38:return KeyCode.up; case 39:return KeyCode.right; case 40:return KeyCode.down;
            case 45:return KeyCode.insert; case 46:return KeyCode.forwardDel;
            case 48:return KeyCode.num0; case 49:return KeyCode.num1; case 50:return KeyCode.num2; case 51:return KeyCode.num3; case 52:return KeyCode.num4;
            case 53:return KeyCode.num5; case 54:return KeyCode.num6; case 55:return KeyCode.num7; case 56:return KeyCode.num8; case 57:return KeyCode.num9;
            case 65:return KeyCode.a; case 66:return KeyCode.b; case 67:return KeyCode.c; case 68:return KeyCode.d; case 69:return KeyCode.e;
            case 70:return KeyCode.f; case 71:return KeyCode.g; case 72:return KeyCode.h; case 73:return KeyCode.i; case 74:return KeyCode.j;
            case 75:return KeyCode.k; case 76:return KeyCode.l; case 77:return KeyCode.m; case 78:return KeyCode.n; case 79:return KeyCode.o;
            case 80:return KeyCode.p; case 81:return KeyCode.q; case 82:return KeyCode.r; case 83:return KeyCode.s; case 84:return KeyCode.t;
            case 85:return KeyCode.u; case 86:return KeyCode.v; case 87:return KeyCode.w; case 88:return KeyCode.x; case 89:return KeyCode.y; case 90:return KeyCode.z;
            case 96:return KeyCode.numpad0; case 97:return KeyCode.numpad1; case 98:return KeyCode.numpad2; case 99:return KeyCode.numpad3; case 100:return KeyCode.numpad4;
            case 101:return KeyCode.numpad5; case 102:return KeyCode.numpad6; case 103:return KeyCode.numpad7; case 104:return KeyCode.numpad8; case 105:return KeyCode.numpad9;
            case 106:return KeyCode.asterisk; case 107:return KeyCode.plus; case 109:return KeyCode.minus; case 110:return KeyCode.period; case 111:return KeyCode.slash;
            case 112:return KeyCode.f1; case 113:return KeyCode.f2; case 114:return KeyCode.f3; case 115:return KeyCode.f4; case 116:return KeyCode.f5; case 117:return KeyCode.f6;
            case 118:return KeyCode.f7; case 119:return KeyCode.f8; case 120:return KeyCode.f9; case 121:return KeyCode.f10; case 122:return KeyCode.f11; case 123:return KeyCode.f12;
            case 186:return KeyCode.semicolon; case 187:return KeyCode.equals; case 188:return KeyCode.comma; case 189:return KeyCode.minus;
            case 190:return KeyCode.period; case 191:return KeyCode.slash; case 192:return KeyCode.backtick; case 219:return KeyCode.leftBracket;
            case 220:return KeyCode.backslash; case 221:return KeyCode.rightBracket; case 222:return KeyCode.apostrophe;
            default:return KeyCode.unknown;
        }
    }

    @JSBody(params={"title","message","text","maxLength","numeric","accepted","canceled"},script=""+
        "try{var label=title||'';if(message){if(label)label+='\n\n';label+=message;}var value=window.prompt(label,text||'');if(value===null){canceled();return;}if(maxLength>=0&&value.length>maxLength)value=value.substring(0,maxLength);if(numeric&&value&&!/^-?(?:\\d+\\.?\\d*|\\.\\d+)$/.test(value)){value=value.replace(/[^0-9+\\-.]/g,'');}accepted(value);}catch(e){canceled();}")
    private static native void browserPrompt(String title,String message,String text,int maxLength,boolean numeric,TextCb accepted,VoidCb canceled);

    @JSBody(params={"canvas","down","move","up","wheel","keydown","keyup","typed","blur"},script=""+
        "canvas.tabIndex=0;canvas.style.touchAction='none';"+
        "function xy(e){var r=canvas.getBoundingClientRect();return [Math.round(e.clientX-r.left),Math.round(r.height-(e.clientY-r.top))];}"+
        "canvas.addEventListener('pointerdown',function(e){var p=xy(e);canvas.focus();try{canvas.setPointerCapture(e.pointerId);}catch(_){} down(p[0],p[1],e.pointerId,e.button,e.buttons,e.pointerType||'mouse');e.preventDefault();},{passive:false});"+
        "canvas.addEventListener('pointermove',function(e){var p=xy(e);move(p[0],p[1],e.pointerId,e.button,e.buttons,e.pointerType||'mouse');e.preventDefault();},{passive:false});"+
        "function u(e){var p=xy(e);up(p[0],p[1],e.pointerId,e.button,e.buttons,e.pointerType||'mouse');e.preventDefault();}"+
        "canvas.addEventListener('pointerup',u,{passive:false});canvas.addEventListener('pointercancel',u,{passive:false});"+
        "canvas.addEventListener('wheel',function(e){wheel(e.deltaX/100,e.deltaY/100);e.preventDefault();},{passive:false});canvas.addEventListener('contextmenu',function(e){e.preventDefault();});"+
        "window.addEventListener('keydown',function(e){if(document.activeElement!==canvas)return;keydown(e.keyCode||e.which||0,0);if(e.key&&e.key.length===1)typed(0,e.key.charCodeAt(0));e.preventDefault();});"+
        "window.addEventListener('keyup',function(e){if(document.activeElement!==canvas)return;keyup(e.keyCode||e.which||0,0);e.preventDefault();});window.addEventListener('blur',blur);")
    private static native void hook(HTMLCanvasElement canvas,PointerCb down,PointerCb move,PointerCb up,WheelCb wheel,KeyCb keydown,KeyCb keyup,KeyCb typed,VoidCb blur);
}
