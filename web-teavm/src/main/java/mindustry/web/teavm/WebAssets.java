package mindustry.web.teavm;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;
import java.util.*;

final class WebAssets{
    private WebAssets(){}
    static byte[] read(String path){
        path=WebVfs.normalize(path); Uint8Array a=get(path); if(a==null)return null;
        int n=a.getLength(); byte[] out=new byte[n]; for(int i=0;i<n;i++)out[i]=(byte)a.get(i); return out;
    }
    static boolean exists(String path){
        String p=WebVfs.normalize(path);
        if(p.isEmpty())return true;
        return has(p)||hasPrefix(p+"/");
    }
    static long length(String path){Uint8Array a=get(WebVfs.normalize(path));return a==null?0:a.getLength();}
    static boolean isDirectory(String path){String p=WebVfs.normalize(path);if(p.length()>0)p+="/";return hasPrefix(p);}
    static String[] list(String path){
        String p=WebVfs.normalize(path);String pref=p.isEmpty()?"":p+"/";JSArray<JSString> all=keys();LinkedHashSet<String> out=new LinkedHashSet<>();
        for(int i=0;i<all.getLength();i++){String k=all.get(i).stringValue();if(!k.startsWith(pref)||k.equals(p))continue;String r=k.substring(pref.length());int s=r.indexOf('/');out.add(s<0?r:r.substring(0,s));}
        return out.toArray(new String[0]);
    }
    @JSBody(params="path",script="return window.__mindustryAssets && window.__mindustryAssets[path] || null;") private static native Uint8Array get(String path);
    @JSBody(params="path",script="return !!(window.__mindustryAssets && window.__mindustryAssets[path]);") private static native boolean has(String path);
    @JSBody(params="prefix",script="if(!window.__mindustryAssets)return false;for(var k in window.__mindustryAssets)if(k.indexOf(prefix)===0)return true;return false;") private static native boolean hasPrefix(String prefix);
    @JSBody(script="return window.__mindustryAssets ? Object.keys(window.__mindustryAssets) : [];") private static native JSArray<JSString> keys();
}
