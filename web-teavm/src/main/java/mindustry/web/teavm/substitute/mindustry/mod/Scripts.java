package mindustry.web.teavm.substitute.mindustry.mod;

import mindustry.mod.*;

import arc.files.*;
import arc.util.*;
import arc.util.Log.*;
import mindustry.mod.Mods.*;

/** Browser replacement for the Rhino-backed scripting engine. */
public class Scripts implements Disposable{
    public Scripts(){}
    public boolean hasErrored(){ return false; }
    public String runConsole(String text){
        return "JavaScript/Rhino console execution is unavailable in the browser build.";
    }
    public void log(String source, String message){ log(LogLevel.info, source, message); }
    public void log(LogLevel level, String source, String message){ Log.log(level, "[@]: @", source, message); }
    public float[] newFloats(int capacity){ return new float[capacity]; }
    public Class<?> getClass(Object object){ return object == null ? null : object.getClass(); }
    public void run(LoadedMod mod, Fi file){}
    @Override public void dispose(){}
}
