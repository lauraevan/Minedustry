package mindustry.web.teavm;
import arc.Files; import arc.files.Fi;
public final class WebFiles implements Files{
    @Override public Fi get(String path,FileType type){return new WebFi(path,type);}
    @Override public String getExternalStoragePath(){return "browser/";}
    @Override public boolean isExternalStorageAvailable(){return WebVfs.available();}
    @Override public String getLocalStoragePath(){return "browser/";}
    @Override public boolean isLocalStorageAvailable(){return WebVfs.available();}
    @Override public String getCachePath(){return "browser/cache";}
}
