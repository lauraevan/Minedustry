package mindustry.core;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.serialization.*;
import mindustry.mod.*;
import mindustry.net.Net.*;
import mindustry.type.*;
import mindustry.ui.FileChooser.*;

/** Browser Platform API with JVM dynamic-loading/Rhino hooks removed. */
public interface Platform{
    default void updateLobby(){}
    default void inviteFriends(){}
    default void publish(Publishable pub){}
    default void viewListing(Publishable pub){}
    default void viewListingID(String mapid){}
    default Seq<Fi> getWorkshopContent(Class<? extends Publishable> type){ return new Seq<>(0); }
    default void openWorkshop(){}
    default NetProvider getNet(){ throw new UnsupportedOperationException("Raw JVM networking is unavailable in browsers."); }
    default Scripts createScripts(){ return new Scripts(); }
    default void updateRPC(){}
    default String getUUID(){
        String uuid = Core.settings.getString("uuid", "");
        if(uuid.isEmpty()){
            byte[] result = new byte[8];
            new Rand().nextBytes(result);
            uuid = new String(Base64Coder.encode(result));
            Core.settings.put("uuid", uuid);
        }
        return uuid;
    }
    default void shareFile(Fi file){}
    default void showFileChooser(FileChooserParams params){
        throw new IllegalArgumentException("File chooser is not implemented by this browser platform.");
    }
    default void hide(){}
    default void beginForceLandscape(){}
    default void endForceLandscape(){}
    interface FileWriter{ void write(Fi file) throws Throwable; }
}
