package arc.backend.gwt;

import com.google.gwt.storage.client.Storage;
import java.io.*;
import java.util.*;

/** Synchronous localStorage-backed byte VFS used by writable browser Fi handles. */
final class GwtVfs{
    static final String filePrefix = "mindustry:vfs:file:";
    static final String dirPrefix = "mindustry:vfs:dir:";

    private GwtVfs(){}

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
        Storage s = storage();
        return s.getItem(filePrefix + path) != null || s.getItem(dirPrefix + path) != null || hasChildren(path);
    }

    static boolean isDirectory(String path){
        path = normalize(path);
        return path.isEmpty() || storage().getItem(dirPrefix + path) != null || hasChildren(path);
    }

    static void mkdirs(String path){
        path = normalize(path);
        if(path.isEmpty()) return;
        String[] parts = path.split("/");
        String current = "";
        Storage s = storage();
        for(String part : parts){
            current = current.isEmpty() ? part : current + "/" + part;
            s.setItem(dirPrefix + current, "1");
        }
    }

    static byte[] read(String path){
        String value = storage().getItem(filePrefix + normalize(path));
        return value == null ? null : decodeBytes(value);
    }

    static void write(String path, byte[] data){
        path = normalize(path);
        mkdirs(parent(path));
        storage().setItem(filePrefix + path, encodeBytes(data));
    }

    static boolean delete(String path){
        path = normalize(path);
        Storage s = storage();
        if(s.getItem(filePrefix + path) != null){
            s.removeItem(filePrefix + path);
            return true;
        }
        if(isDirectory(path) && list(path).length == 0){
            s.removeItem(dirPrefix + path);
            return true;
        }
        return false;
    }

    static void deleteDirectory(String path){
        path = normalize(path);
        Storage s = storage();
        String childPrefix = path.isEmpty() ? "" : path + "/";
        for(int i = s.getLength() - 1; i >= 0; i--){
            String key = s.key(i);
            if(key == null) continue;
            if(key.startsWith(filePrefix)){
                String p = key.substring(filePrefix.length());
                if(p.equals(path) || p.startsWith(childPrefix)) s.removeItem(key);
            }else if(key.startsWith(dirPrefix)){
                String p = key.substring(dirPrefix.length());
                if(p.equals(path) || p.startsWith(childPrefix)) s.removeItem(key);
            }
        }
    }

    static String[] list(String path){
        path = normalize(path);
        String childPrefix = path.isEmpty() ? "" : path + "/";
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Storage s = storage();
        for(int i = 0; i < s.getLength(); i++){
            String key = s.key(i);
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
        Storage s = storage();
        for(int i = 0; i < s.getLength(); i++){
            String key = s.key(i);
            if(key == null) continue;
            if(key.startsWith(filePrefix) && key.substring(filePrefix.length()).startsWith(prefix)) return true;
            if(key.startsWith(dirPrefix) && key.substring(dirPrefix.length()).startsWith(prefix)) return true;
        }
        return false;
    }

    static Storage storage(){
        Storage s = GwtFiles.LocalStorage;
        if(s == null) throw new IllegalStateException("localStorage unavailable");
        return s;
    }

    // Hex is deliberately used instead of java.util.Base64 because it is tiny,
    // deterministic and fully GWT-translatable.
    static String encodeBytes(byte[] bytes){
        char[] out = new char[bytes.length * 2];
        final char[] hex = "0123456789abcdef".toCharArray();
        for(int i = 0; i < bytes.length; i++){
            int v = bytes[i] & 0xff;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 15];
        }
        return new String(out);
    }

    static byte[] decodeBytes(String text){
        if(text == null || text.length() == 0) return new byte[0];
        int length = text.length() / 2;
        byte[] out = new byte[length];
        for(int i = 0; i < length; i++){
            int hi = Character.digit(text.charAt(i * 2), 16);
            int lo = Character.digit(text.charAt(i * 2 + 1), 16);
            out[i] = (byte)((hi << 4) | lo);
        }
        return out;
    }
}
