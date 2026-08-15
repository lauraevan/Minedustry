package arc.backend.gwt;

import arc.Files;
import arc.Files.FileType;
import arc.backend.gwt.preloader.Preloader;
import arc.files.Fi;
import com.google.gwt.storage.client.Storage;

/** Browser Files implementation: packaged assets + a synchronous localStorage VFS. */
public class GwtFiles implements Files{
    public static final Storage LocalStorage = Storage.getLocalStorageIfSupported();
    final Preloader preloader;

    public GwtFiles(Preloader preloader){
        this.preloader = preloader;
    }

    @Override
    public Fi get(String path, FileType type){
        return new GwtFileHandle(preloader, path, type);
    }

    @Override
    public String getExternalStoragePath(){
        return "browser/";
    }

    @Override
    public boolean isExternalStorageAvailable(){
        return LocalStorage != null;
    }

    @Override
    public String getLocalStoragePath(){
        return "browser/";
    }

    @Override
    public boolean isLocalStorageAvailable(){
        return LocalStorage != null;
    }

    @Override
    public String getCachePath(){
        return "browser/cache";
    }
}
