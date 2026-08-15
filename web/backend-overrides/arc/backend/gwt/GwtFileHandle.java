package arc.backend.gwt;

import arc.*;
import arc.Files.FileType;
import arc.backend.gwt.preloader.Preloader;
import arc.files.Fi;
import arc.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/**
 * Current Arc Fi implementation for browsers.
 *
 * classpath/internal => official GWT asset preloader (read only)
 * local/absolute/external => synchronous localStorage VFS (read/write)
 */
public class GwtFileHandle extends Fi{
    public final Preloader preloader;
    private final String path;
    private final FileType browserType;

    public GwtFileHandle(Preloader preloader, String path, FileType type){
        super(GwtVfs.normalize(path), type);
        this.preloader = preloader;
        this.path = GwtVfs.normalize(path);
        this.browserType = type;
    }

    public GwtFileHandle(String path){
        this(((GwtApplication)Core.app).getPreloader(), path, FileType.internal);
    }

    private boolean packaged(){
        return browserType == FileType.internal || browserType == FileType.classpath;
    }

    private void requireWritable(){
        if(packaged()) throw new ArcRuntimeException("Cannot write packaged browser asset: " + path);
        if(GwtFiles.LocalStorage == null) throw new ArcRuntimeException("Browser localStorage is unavailable");
    }

    @Override
    public String path(){
        return path;
    }

    @Override
    public String absolutePath(){
        return "browser://" + path;
    }

    @Override
    public String name(){
        int index = path.lastIndexOf('/');
        return index < 0 ? path : path.substring(index + 1);
    }

    @Override
    public String extension(){
        String name = name();
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1);
    }

    @Override
    public String nameWithoutExtension(){
        String name = name();
        int index = name.lastIndexOf('.');
        return index < 0 ? name : name.substring(0, index);
    }

    @Override
    public String pathWithoutExtension(){
        int index = path.lastIndexOf('.');
        return index < 0 ? path : path.substring(0, index);
    }

    @Override
    public FileType type(){
        return browserType;
    }

    @Override
    public File file(){
        // java.io.File has no meaningful browser backing.  Returning a path object
        // is useful for filters/name inspection, but callers must use Fi streams for IO.
        return new File(path);
    }

    @Override
    public InputStream read(){
        if(packaged()){
            InputStream in = preloader.read(path);
            if(in == null) throw new ArcRuntimeException("Packaged asset not found: " + path);
            return in;
        }
        byte[] data = GwtVfs.read(path);
        if(data == null) throw new ArcRuntimeException("Browser file not found: " + path);
        return new ByteArrayInputStream(data);
    }

    @Override
    public ByteBuffer map(FileChannel.MapMode mode){
        throw new ArcRuntimeException("Memory-mapped files are unavailable in browsers: " + path);
    }

    @Override
    public OutputStream write(boolean append){
        requireWritable();
        final byte[] initial = append ? GwtVfs.read(path) : null;
        return new ByteArrayOutputStream(initial == null ? 256 : initial.length + 256){
            private boolean stored;
            {
                if(initial != null) write(initial, 0, initial.length);
            }

            private void store(){
                if(!stored){
                    stored = true;
                    GwtVfs.write(path, toByteArray());
                }
            }

            @Override
            public void flush(){
                store();
            }

            @Override
            public void close(){
                store();
            }
        };
    }

    @Override
    public Writer writer(boolean append, String charset){
        try{
            OutputStream out = write(append);
            return charset == null ? new OutputStreamWriter(out) : new OutputStreamWriter(out, charset);
        }catch(UnsupportedEncodingException error){
            throw new ArcRuntimeException("Unsupported charset: " + charset, error);
        }
    }

    @Override
    public Fi[] list(){
        if(packaged()) return preloader.list(path);
        String[] names = GwtVfs.list(path);
        Fi[] out = new Fi[names.length];
        for(int i = 0; i < names.length; i++) out[i] = child(names[i]);
        return out;
    }

    @Override
    public Fi[] list(FileFilter filter){
        ArrayList<Fi> out = new ArrayList<>();
        for(Fi child : list()) if(filter.accept(child.file())) out.add(child);
        return out.toArray(new Fi[out.size()]);
    }

    @Override
    public Fi[] list(FilenameFilter filter){
        ArrayList<Fi> out = new ArrayList<>();
        File self = file();
        for(Fi child : list()) if(filter.accept(self, child.name())) out.add(child);
        return out.toArray(new Fi[out.size()]);
    }

    @Override
    public Fi[] list(String suffix){
        ArrayList<Fi> out = new ArrayList<>();
        for(Fi child : list()) if(child.name().endsWith(suffix)) out.add(child);
        return out.toArray(new Fi[out.size()]);
    }

    @Override
    public boolean isDirectory(){
        return packaged() ? preloader.isDirectory(path) : GwtVfs.isDirectory(path);
    }

    @Override
    public Fi child(String name){
        String child = path.isEmpty() ? name : path + "/" + name;
        return new GwtFileHandle(preloader, child, browserType);
    }

    @Override
    public Fi sibling(String name){
        if(path.isEmpty()) throw new ArcRuntimeException("Cannot get sibling of browser VFS root");
        return parent().child(name);
    }

    @Override
    public Fi parent(){
        return new GwtFileHandle(preloader, GwtVfs.parent(path), browserType);
    }

    @Override
    public boolean mkdirs(){
        requireWritable();
        GwtVfs.mkdirs(path);
        return true;
    }

    @Override
    public boolean exists(){
        return packaged() ? preloader.contains(path) : GwtVfs.exists(path);
    }

    @Override
    public boolean delete(){
        requireWritable();
        return GwtVfs.delete(path);
    }

    @Override
    public boolean deleteDirectory(){
        requireWritable();
        if(!GwtVfs.exists(path)) return false;
        GwtVfs.deleteDirectory(path);
        return true;
    }

    @Override
    public void emptyDirectory(boolean preserveTree){
        requireWritable();
        for(Fi child : list()){
            if(child.isDirectory()){
                if(preserveTree) child.emptyDirectory(true);
                else child.deleteDirectory();
            }else{
                child.delete();
            }
        }
    }

    @Override
    public long length(){
        return packaged() ? preloader.length(path) : GwtVfs.length(path);
    }

    @Override
    public long lastModified(){
        return 0L;
    }
}
