package mindustry.web.teavm;

import arc.*;
import arc.Files.FileType;

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
public class WebFi extends Fi{
        private final String path;
    private final FileType browserType;

    public WebFi(String path, FileType type){
        super(WebVfs.normalize(path), type);
        this.path = WebVfs.normalize(path);
        this.browserType = type;
    }


    private boolean packaged(){
        return browserType == FileType.internal || browserType == FileType.classpath;
    }

    private void requireWritable(){
        if(packaged()) throw new ArcRuntimeException("Cannot write packaged browser asset: " + path);
        if(!WebVfs.available()) throw new ArcRuntimeException("Browser localStorage is unavailable");
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
            byte[] data = WebAssets.read(path);
            if(data == null) throw new ArcRuntimeException("Packaged asset not found: " + path);
            return new ByteArrayInputStream(data);
        }
        byte[] data = WebVfs.read(path);
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
        final byte[] initial = append ? WebVfs.read(path) : null;
        return new ByteArrayOutputStream(initial == null ? 256 : initial.length + 256){
            {
                if(initial != null) write(initial, 0, initial.length);
            }

            private void store(){
                WebVfs.write(path, toByteArray());
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
        if(packaged()){ String[] names=WebAssets.list(path); Fi[] out=new Fi[names.length]; for(int i=0;i<names.length;i++)out[i]=child(names[i]); return out; }
        String[] names = WebVfs.list(path);
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
        return packaged() ? WebAssets.isDirectory(path) : WebVfs.isDirectory(path);
    }

    @Override
    public Fi child(String name){
        String child = path.isEmpty() ? name : path + "/" + name;
        return new WebFi(child, browserType);
    }

    @Override
    public Fi sibling(String name){
        if(path.isEmpty()) throw new ArcRuntimeException("Cannot get sibling of browser VFS root");
        return parent().child(name);
    }

    @Override
    public Fi parent(){
        return new WebFi(WebVfs.parent(path), browserType);
    }

    @Override
    public boolean mkdirs(){
        requireWritable();
        WebVfs.mkdirs(path);
        return true;
    }

    @Override
    public boolean exists(){
        return packaged() ? WebAssets.exists(path) : WebVfs.exists(path);
    }

    @Override
    public boolean delete(){
        requireWritable();
        return WebVfs.delete(path);
    }

    @Override
    public boolean deleteDirectory(){
        requireWritable();
        if(!WebVfs.exists(path)) return false;
        WebVfs.deleteDirectory(path);
        return true;
    }

    @Override
    public void moveTo(Fi dest){
        requireWritable();
        if(dest.type() == FileType.classpath || dest.type() == FileType.internal){
            throw new ArcRuntimeException("Cannot move browser file into packaged storage: " + dest);
        }
        copyTo(dest);
        if(isDirectory()) deleteDirectory();
        else delete();
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
        return packaged() ? WebAssets.length(path) : WebVfs.length(path);
    }

    @Override
    public long lastModified(){
        return 0L;
    }
}
