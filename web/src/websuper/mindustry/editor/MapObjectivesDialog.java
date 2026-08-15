package mindustry.editor;

import arc.func.*;
import arc.struct.*;
import mindustry.game.MapObjectives.*;
import mindustry.ui.dialogs.*;

/**
 * Browser-safe editor shell. The native dialog discovers/edit fields with Java
 * reflection. Generated field metadata will replace it later; keeping this API
 * lets the rest of the real map editor load without java.lang.reflect.
 */
public class MapObjectivesDialog extends BaseDialog{
    public MapObjectivesDialog(){
        super("@editor.objectives");
        addCloseButton();
    }

    public void show(Seq<MapObjective> objectives, Cons<Seq<MapObjective>> out){
        cont.clear();
        cont.add("Objective property editing is not available in the browser build yet.").wrap().width(520f).pad(24f);
        super.show();
    }
}
