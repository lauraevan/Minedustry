package mindustry.web.teavm;

import arc.*;
import arc.Files.FileType;
import arc.files.*;
import arc.util.*;
import mindustry.ui.FileChooser.FileChooserParams;
import org.teavm.jso.*;
import org.teavm.jso.core.*;
import org.teavm.jso.typedarrays.*;

import java.io.*;

/** Browser implementation of Mindustry's Platform file chooser contract. */
public final class WebFileChooser{
    @JSFunctor interface FilesCb extends JSObject{ void call(JSArray<PickedFile> files); }

    interface PickedFile extends JSObject{
        @JSProperty String getName();
        @JSProperty Uint8Array getData();
    }

    private WebFileChooser(){}

    public static void show(FileChooserParams params){
        if(params.open){
            open(params);
        }else{
            String name = Strings.sanitizeFilename(params.fileName == null ? "file" : params.fileName);
            params.handleChooseResult(new DownloadFi(name));
        }
    }

    private static void open(FileChooserParams params){
        StringBuilder accept = new StringBuilder();
        if(params.extensions != null){
            for(String extension : params.extensions){
                if(extension == null || extension.isEmpty()) continue;
                if(accept.length() > 0) accept.append(',');
                accept.append('.').append(extension.startsWith(".") ? extension.substring(1) : extension);
            }
        }

        choose(accept.toString(), params.allowMultiple, files -> {
            if(files == null || files.getLength() == 0) return;
            Fi[] imported = new Fi[files.getLength()];
            long stamp = System.currentTimeMillis();
            for(int i = 0; i < files.getLength(); i++){
                PickedFile picked = files.get(i);
                String name = Strings.sanitizeFilename(picked.getName());
                Uint8Array input = picked.getData();
                byte[] bytes = new byte[input.getLength()];
                for(int n = 0; n < bytes.length; n++) bytes[n] = (byte)input.get(n);

                WebFi file = new WebFi("imports/" + stamp + "-" + i + "-" + name, FileType.local);
                WebVfs.write(file.path(), bytes);
                imported[i] = file;
            }
            params.handleChooseResult(imported);
        });
    }

    /** A writable Fi that also downloads its final bytes when the writer closes it. */
    private static final class DownloadFi extends WebFi{
        private final String downloadName;

        DownloadFi(String name){
            super("exports/" + name, FileType.local);
            this.downloadName = name;
        }

        @Override
        public OutputStream write(boolean append){
            if(!WebVfs.available()) throw new ArcRuntimeException("Browser localStorage is unavailable");
            byte[] old = append ? WebVfs.read(path()) : null;
            return new ByteArrayOutputStream(old == null ? 1024 : old.length + 1024){
                private boolean closed;
                {
                    if(old != null) write(old, 0, old.length);
                }

                @Override
                public void flush(){
                    WebVfs.write(path(), toByteArray());
                }

                @Override
                public void close(){
                    if(closed) return;
                    closed = true;
                    byte[] bytes = toByteArray();
                    WebVfs.write(path(), bytes);
                    Uint8Array jsBytes = new Uint8Array(bytes.length);
                    jsBytes.set(bytes);
                    download(downloadName, jsBytes);
                }
            };
        }
    }

    @JSBody(params={"accept","multiple","accepted"}, script=""+
        "var input=document.createElement('input');input.type='file';input.accept=accept||'';input.multiple=!!multiple;input.style.display='none';document.body.appendChild(input);"+
        "function cleanup(){try{input.remove();}catch(e){}}"+
        "input.addEventListener('change',function(){var list=Array.from(input.files||[]);if(!list.length){cleanup();return;}Promise.all(list.map(function(file){return file.arrayBuffer().then(function(buf){return {name:file.name,data:new Uint8Array(buf)};});})).then(function(files){cleanup();accepted(files);}).catch(function(){cleanup();});},{once:true});"+
        "input.click();")
    private static native void choose(String accept, boolean multiple, FilesCb accepted);

    @JSBody(params={"name","bytes"}, script=""+
        "try{var blob=new Blob([bytes],{type:'application/octet-stream'});var url=URL.createObjectURL(blob);var a=document.createElement('a');a.href=url;a.download=name||'mindustry.bin';a.style.display='none';document.body.appendChild(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(url);},1000);}catch(e){console.error('Mindustry web export failed',e);}")
    private static native void download(String name, Uint8Array bytes);
}
