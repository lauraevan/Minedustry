package mindustry.mod;

import arc.files.*;
import arc.func.*;
import arc.util.*;
import arc.util.serialization.Json.*;
import mindustry.ctype.*;
import mindustry.mod.Mods.*;

/**
 * Minimal browser API shell. Executable/content mods and map data patches are
 * intentionally outside the first playable milestone, so reflective parsing is
 * never entered. Keeping the type preserves current public signatures without
 * pulling java.lang.reflect/Class.forName into GWT.
 */
public class ContentParser{
    public interface ParseListener{
        void parsed(Class<?> type, JsonValue jsonData, Object result);
    }

    public boolean allowClassResolution, allowAssetLoading, allowPatching;

    void warnContext(@Nullable Content currentContent, @Nullable Fi currentFile, String string, Object... format){
    }

    public void finishParsing(){
    }

    public void markError(Content content, LoadedMod mod, Fi file, Throwable error){
        if(content != null && content.minfo != null){
            content.minfo.error = Strings.getFinalMessage(error);
            content.minfo.baseError = error;
        }
    }
}
