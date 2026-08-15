package mindustry.mod;

import arc.files.*;
import arc.struct.*;
import mindustry.ctype.*;
import mindustry.mod.Mods.*;
import mindustry.mod.data.*;

/**
 * Browser milestone replacement for Mindustry's reflection-heavy map data patcher.
 * Vanilla content and normal map simulation do not depend on applying these patches.
 */
public class DataPatcher{
    private static final ModMeta dpModMeta = new ModMeta();
    public static final LoadedMod dpMod;
    public static final int maxImageSize = 2000;
    public static final int patchFormatVersion = 2;

    static{
        dpModMeta.name = dpModMeta.internalName = "dp";
        dpMod = new LoadedMod(new Fi("dp"), new Fi(""), null, null, dpModMeta);
    }

    private final Seq<Content> added = new Seq<>();

    public boolean isPatched(Object object){
        return false;
    }

    public void apply(Seq<PatchAsset> patches, Seq<ContentAsset> content){
        apply(patches, content, true);
    }

    public void apply(Seq<PatchAsset> patches, Seq<ContentAsset> content, boolean reloadContentWorld){
        if((patches != null && !patches.isEmpty()) || (content != null && !content.isEmpty())){
            throw new UnsupportedOperationException("Custom map data/content patches are not supported by the browser build yet.");
        }
    }

    public void unapply(){
        unapply(true);
    }

    public void unapply(boolean reloadContentWorld){
        added.clear();
    }

    public Seq<Content> getAddedContent(){
        return added;
    }

    public static void fixContentArrays(){
        // No patch-created content exists in this browser milestone.
    }
}
