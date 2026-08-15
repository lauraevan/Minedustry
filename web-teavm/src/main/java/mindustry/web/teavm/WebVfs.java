package mindustry.web.teavm;

import org.teavm.jso.JSBody;
import java.io.*;
import java.util.*;

/** Synchronous localStorage-backed byte VFS used by writable browser Fi handles. */
final class WebVfs{
    static final String filePrefix = "mindustry:vfs:file:";
    static final String dirPrefix = "mindustry:vfs:dir:";

    private WebVfs(){}

    static String normalize(String path){
        if(path == null) return "";
        path = path.replace('\\', '/');
        while(path.startsWith("/")) path = path.substring(1);
        while(path.contains("//")) path = path.replace("//", "/");
        while(path.endsWith("/") && path.length() > 0) path = path.substring(0, path.length() - 1);
        if(path.equals(".")) return "";

        String[] split = path.split("/");
        ArrayList<String> clean = new ArrayList<>();
        for(String part : split){
            if(part.isEmpty() || part.equals(".")) continue;
            if(part.equals("..")){
                if(!clean.isEmpty()) clean.remove(clean.size() - 1);
            }else{
                clean.add(part);
            }
        }
        StringBuilder out = new StringBuilder();
        for(String part : clean){
            if(out.length() > 0) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    static boolean exists(String path){
        path = normalize(path);
        if(path.isEmpty()) return true;

        return get(filePrefix + path) != null || get(dirPrefix + path) != null || hasChildren(path);
    }

    static boolean isDirectory(String path){
        path = normalize(path);
        return path.isEmpty() || get(dirPrefix + path) != null || hasChildren(path);
    }

    static void mkdirs(String path){
        path = normalize(path);
        if(path.isEmpty()) return;
        String[] parts = path.split("/");
        String current = "";

        for(String part : parts){
            current = current.isEmpty() ? part : current + "/" + part;
            set(dirPrefix + current, "1");
        }
    }

    static byte[] read(String path){
        String value = get(filePrefix + normalize(path));
        return value == null ? null : decodeBytes(value);
    }

    static void write(String path, byte[] data){
        path = normalize(path);
        mkdirs(parent(path));
        set(filePrefix + path, encodeBytes(data));
    }

    static boolean delete(String path){
        path = normalize(path);

        if(get(filePrefix + path) != null){
            remove(filePrefix + path);
            return true;
        }
        if(isDirectory(path) && list(path).length == 0){
            remove(dirPrefix + path);
            return true;
        }
        return false;
    }

    static void deleteDirectory(String path){
        path = normalize(path);

        String childPrefix = path.isEmpty() ? "" : path + "/";
        for(int i = keyCount() - 1; i >= 0; i--){
            String key = key(i);
            if(key == null) continue;
            if(key.startsWith(filePrefix)){
                String p = key.substring(filePrefix.length());
                if(p.equals(path) || p.startsWith(childPrefix)) remove(key);
            }else if(key.startsWith(dirPrefix)){
                String p = key.substring(dirPrefix.length());
                if(p.equals(path) || p.startsWith(childPrefix)) remove(key);
            }
        }
    }

    static String[] list(String path){
        path = normalize(path);
        String childPrefix = path.isEmpty() ? "" : path + "/";
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for(int i = 0; i < keyCount(); i++){
            String key = key(i);
            if(key == null) continue;
            String p = null;
            if(key.startsWith(filePrefix)) p = key.substring(filePrefix.length());
            else if(key.startsWith(dirPrefix)) p = key.substring(dirPrefix.length());
            if(p == null || !p.startsWith(childPrefix) || p.equals(path)) continue;
            String rest = p.substring(childPrefix.length());
            int slash = rest.indexOf('/');
            out.add(slash == -1 ? rest : rest.substring(0, slash));
        }
        return out.toArray(new String[out.size()]);
    }

    static long length(String path){
        byte[] data = read(path);
        return data == null ? 0 : data.length;
    }

    static String parent(String path){
        path = normalize(path);
        int index = path.lastIndexOf('/');
        return index < 0 ? "" : path.substring(0, index);
    }

    static boolean hasChildren(String path){
        path = normalize(path);
        String prefix = path.isEmpty() ? "" : path + "/";

        for(int i = 0; i < keyCount(); i++){
            String key = key(i);
            if(key == null) continue;
            if(key.startsWith(filePrefix) && key.substring(filePrefix.length()).startsWith(prefix)) return true;
            if(key.startsWith(dirPrefix) && key.substring(dirPrefix.length()).startsWith(prefix)) return true;
        }
        return false;
    }

    static boolean available(){ return storageAvailable(); }

    @JSBody(script="try{return !!window.localStorage;}catch(e){return false;}") static native boolean storageAvailable();
    @JSBody(params="key",script="try{return localStorage.getItem(key);}catch(e){return null;}") static native String get(String key);
    @JSBody(params={"key","value"},script="localStorage.setItem(key,value);") static native void set(String key,String value);
    @JSBody(params="key",script="localStorage.removeItem(key);") static native void remove(String key);
    @JSBody(script="return localStorage.length;") static native int keyCount();
    @JSBody(params="index",script="return localStorage.key(index);") static native String key(int index);

    // TeaVM 0.15 provides java.util.Base64. Prefix the new representation so
    // older hex-encoded test saves remain readable after this storage upgrade.
    static String encodeBytes(byte[] bytes){
        return "b64:" + Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] decodeBytes(String text){
        if(text == null || text.length() == 0) return new byte[0];
        if(text.startsWith("b64:")){
            return Base64.getDecoder().decode(text.substring(4));
        }

        // Legacy pre-Base64 web-port format.
        int length = text.length() / 2;
        byte[] out = new byte[length];
        for(int i = 0; i < length; i++){
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            if(hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid browser VFS encoding");
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }

}
