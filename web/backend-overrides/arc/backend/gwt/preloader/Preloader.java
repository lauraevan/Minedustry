package arc.backend.gwt.preloader;

import arc.Files.FileType;
import arc.backend.gwt.GwtFileHandle;
import arc.backend.gwt.preloader.AssetDownloader.AssetLoaderListener;
import arc.backend.gwt.preloader.AssetFilter.AssetType;
import arc.files.Fi;
import arc.struct.*;
import arc.util.ArcRuntimeException;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ImageElement;
import java.io.*;

/**
 * Arc's final official GWT preloader, migrated to current Arc types.
 * Unlike the historical implementation, directory listing includes text,
 * binary, image, audio and directory assets; current Mindustry's built-in maps
 * and data are not text-only.
 */
public class Preloader{
    public final String baseUrl;
    public ObjectMap<String, Void> directories = new ObjectMap<>();
    public ObjectMap<String, ImageElement> images = new ObjectMap<>();
    public ObjectMap<String, Void> audio = new ObjectMap<>();
    public ObjectMap<String, String> texts = new ObjectMap<>();
    public ObjectMap<String, Blob> binaries = new ObjectMap<>();

    public Preloader(String newBaseURL){
        baseUrl = newBaseURL;
        GWT.create(PreloaderBundle.class);
    }

    public void preload(final String assetFileUrl, final PreloaderCallback callback){
        final AssetDownloader loader = new AssetDownloader();
        loader.loadText(baseUrl + assetFileUrl, new AssetLoaderListener<String>(){
            @Override public void onProgress(double amount){}

            @Override
            public void onFailure(){
                callback.error(assetFileUrl);
            }

            @Override
            public void onSuccess(String result){
                String[] lines = result.split("\\n");
                Seq<Asset> assets = new Seq<>(lines.length);
                for(String line : lines){
                    if(line.trim().isEmpty()) continue;
                    String[] tokens = line.split(":", 4);
                    if(tokens.length != 4) throw new ArcRuntimeException("Invalid assets description line: " + line);
                    AssetType type = AssetType.Text;
                    if(tokens[0].equals("i")) type = AssetType.Image;
                    else if(tokens[0].equals("b")) type = AssetType.Binary;
                    else if(tokens[0].equals("a")) type = AssetType.Audio;
                    else if(tokens[0].equals("d")) type = AssetType.Directory;
                    long size = Long.parseLong(tokens[2]);
                    if(type == AssetType.Audio && !loader.isUseBrowserCache()) size = 0;
                    assets.add(new Asset(tokens[1].trim(), type, size, tokens[3]));
                }

                final PreloaderState state = new PreloaderState(assets);
                if(assets.isEmpty()){
                    callback.update(state);
                    return;
                }

                for(int i = 0; i < assets.size; i++){
                    final Asset asset = assets.get(i);
                    if(contains(asset.url)){
                        asset.loaded = asset.size;
                        asset.succeed = true;
                        continue;
                    }
                    loader.load(baseUrl + asset.url, asset.type, asset.mimeType, new AssetLoaderListener<Object>(){
                        @Override
                        public void onProgress(double amount){
                            asset.loaded = (long)amount;
                            callback.update(state);
                        }

                        @Override
                        public void onFailure(){
                            asset.failed = true;
                            callback.error(asset.url);
                            callback.update(state);
                        }

                        @Override
                        public void onSuccess(Object loaded){
                            switch(asset.type){
                                case Text: texts.put(asset.url, (String)loaded); break;
                                case Image: images.put(asset.url, (ImageElement)loaded); break;
                                case Binary: binaries.put(asset.url, (Blob)loaded); break;
                                case Audio: audio.put(asset.url, null); break;
                                case Directory: directories.put(trimSlash(asset.url), null); break;
                            }
                            asset.succeed = true;
                            asset.loaded = asset.size;
                            callback.update(state);
                        }
                    });
                }
                callback.update(state);
            }
        });
    }

    public InputStream read(String url){
        url = trimSlash(url);
        if(texts.containsKey(url)){
            try{
                return new ByteArrayInputStream(texts.get(url).getBytes("UTF-8"));
            }catch(UnsupportedEncodingException ignored){
                return new ByteArrayInputStream(texts.get(url).getBytes());
            }
        }
        if(images.containsKey(url)) return new ByteArrayInputStream(new byte[1]);
        if(binaries.containsKey(url)) return binaries.get(url).read();
        if(audio.containsKey(url)) return new ByteArrayInputStream(new byte[1]);
        return null;
    }

    public boolean contains(String url){
        url = trimSlash(url);
        return texts.containsKey(url) || images.containsKey(url) || binaries.containsKey(url) ||
            audio.containsKey(url) || directories.containsKey(url) || hasChild(url);
    }

    public boolean isText(String url){ return texts.containsKey(trimSlash(url)); }
    public boolean isImage(String url){ return images.containsKey(trimSlash(url)); }
    public boolean isBinary(String url){ return binaries.containsKey(trimSlash(url)); }
    public boolean isAudio(String url){ return audio.containsKey(trimSlash(url)); }
    public boolean isDirectory(String url){
        url = trimSlash(url);
        return url.isEmpty() || directories.containsKey(url) || hasChild(url);
    }

    public Fi[] list(String url){
        url = trimSlash(url);
        ObjectSet<String> names = new ObjectSet<>();
        collectChildren(names, url, texts.keys());
        collectChildren(names, url, images.keys());
        collectChildren(names, url, binaries.keys());
        collectChildren(names, url, audio.keys());
        collectChildren(names, url, directories.keys());
        Fi[] out = new Fi[names.size];
        int i = 0;
        for(String name : names){
            String child = url.isEmpty() ? name : url + "/" + name;
            out[i++] = new GwtFileHandle(this, child, FileType.internal);
        }
        return out;
    }

    public Fi[] list(String url, FileFilter filter){
        Seq<Fi> out = new Seq<>();
        for(Fi child : list(url)) if(filter.accept(child.file())) out.add(child);
        Fi[] array = new Fi[out.size];
        for(int i = 0; i < out.size; i++) array[i] = out.get(i);
        return array;
    }

    public Fi[] list(String url, FilenameFilter filter){
        Seq<Fi> out = new Seq<>();
        File dir = new File(trimSlash(url));
        for(Fi child : list(url)) if(filter.accept(dir, child.name())) out.add(child);
        Fi[] array = new Fi[out.size];
        for(int i = 0; i < out.size; i++) array[i] = out.get(i);
        return array;
    }

    public Fi[] list(String url, String suffix){
        Seq<Fi> out = new Seq<>();
        for(Fi child : list(url)) if(child.name().endsWith(suffix)) out.add(child);
        Fi[] array = new Fi[out.size];
        for(int i = 0; i < out.size; i++) array[i] = out.get(i);
        return array;
    }

    public long length(String url){
        url = trimSlash(url);
        if(texts.containsKey(url)){
            try{
                return texts.get(url).getBytes("UTF-8").length;
            }catch(UnsupportedEncodingException ignored){
                return texts.get(url).length();
            }
        }
        if(images.containsKey(url)) return 1;
        if(binaries.containsKey(url)) return binaries.get(url).length();
        if(audio.containsKey(url)) return 1;
        return 0;
    }

    private boolean hasChild(String parent){
        ObjectSet<String> one = new ObjectSet<>();
        collectChildren(one, trimSlash(parent), texts.keys());
        if(one.size > 0) return true;
        collectChildren(one, trimSlash(parent), images.keys());
        if(one.size > 0) return true;
        collectChildren(one, trimSlash(parent), binaries.keys());
        if(one.size > 0) return true;
        collectChildren(one, trimSlash(parent), audio.keys());
        if(one.size > 0) return true;
        collectChildren(one, trimSlash(parent), directories.keys());
        return one.size > 0;
    }

    private static void collectChildren(ObjectSet<String> out, String parent, Iterable<String> paths){
        String prefix = parent.isEmpty() ? "" : parent + "/";
        for(String raw : paths){
            String path = trimSlash(raw);
            if(!path.startsWith(prefix) || path.equals(parent)) continue;
            String rest = path.substring(prefix.length());
            if(rest.isEmpty()) continue;
            int slash = rest.indexOf('/');
            out.add(slash < 0 ? rest : rest.substring(0, slash));
        }
    }

    private static String trimSlash(String path){
        if(path == null) return "";
        path = path.replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);
        while(path.endsWith("/") && !path.isEmpty()) path = path.substring(0, path.length() - 1);
        return path;
    }

    public interface PreloaderCallback{
        void update(PreloaderState state);
        void error(String file);
    }

    public static class Asset{
        public final String url;
        public final AssetType type;
        public final long size;
        public final String mimeType;
        public boolean succeed, failed;
        public long loaded;

        public Asset(String url, AssetType type, long size, String mimeType){
            this.url = url;
            this.type = type;
            this.size = size;
            this.mimeType = mimeType;
        }
    }

    public static class PreloaderState{
        public final Seq<Asset> assets;

        public PreloaderState(Seq<Asset> assets){
            this.assets = assets;
        }

        public long getDownloadedSize(){
            long size = 0;
            for(int i = 0; i < assets.size; i++){
                Asset asset = assets.get(i);
                size += (asset.succeed || asset.failed) ? asset.size : Math.min(asset.size, asset.loaded);
            }
            return size;
        }

        public long getTotalSize(){
            long size = 0;
            for(int i = 0; i < assets.size; i++) size += assets.get(i).size;
            return size;
        }

        public float getProgress(){
            long total = getTotalSize();
            return total == 0 ? 1f : getDownloadedSize() / (float)total;
        }

        public boolean hasEnded(){
            return getDownloadedSize() == getTotalSize();
        }
    }
}
