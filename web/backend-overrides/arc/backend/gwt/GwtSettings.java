package arc.backend.gwt;

import arc.Settings;
import arc.util.ArcRuntimeException;
import com.google.gwt.storage.client.Storage;
import java.util.Map;

/** Current-Arc settings persisted synchronously in browser localStorage. */
public class GwtSettings extends Settings{
    private String prefix = "app:";

    @Override
    public void setAppName(String name){
        super.setAppName(name);
        prefix = name + ":setting:";
    }

    @Override
    public synchronized void saveValues(){
        Storage storage = storage();
        try{
            // Removing backwards avoids skipping entries as localStorage compacts.
            for(int i = storage.getLength() - 1; i >= 0; i--){
                String key = storage.key(i);
                if(key != null && key.startsWith(prefix)) storage.removeItem(key);
            }

            for(Map.Entry<String, Object> entry : values.entrySet()){
                Object value = entry.getValue();
                String type;
                String encoded;
                if(value instanceof Boolean){
                    type = "b"; encoded = value.toString();
                }else if(value instanceof Integer){
                    type = "i"; encoded = value.toString();
                }else if(value instanceof Long){
                    type = "l"; encoded = value.toString();
                }else if(value instanceof Float){
                    type = "f"; encoded = value.toString();
                }else if(value instanceof byte[]){
                    type = "x"; encoded = GwtVfs.encodeBytes((byte[])value);
                }else{
                    type = "s"; encoded = String.valueOf(value);
                }
                storage.setItem(prefix + entry.getKey() + ":" + type, encoded);
            }
        }catch(Throwable error){
            throw new ArcRuntimeException("Could not flush browser settings", error);
        }
    }

    @Override
    public synchronized void loadValues(){
        values.clear();
        Storage storage = storage();
        try{
            for(int i = 0; i < storage.getLength(); i++){
                String rawKey = storage.key(i);
                if(rawKey == null || !rawKey.startsWith(prefix)) continue;
                int split = rawKey.lastIndexOf(':');
                if(split < prefix.length()) continue;
                String key = rawKey.substring(prefix.length(), split);
                String type = rawKey.substring(split + 1);
                String value = storage.getItem(rawKey);
                if(value == null) continue;
                Object decoded;
                if(type.equals("b")) decoded = Boolean.parseBoolean(value);
                else if(type.equals("i")) decoded = Integer.parseInt(value);
                else if(type.equals("l")) decoded = Long.parseLong(value);
                else if(type.equals("f")) decoded = Float.parseFloat(value);
                else if(type.equals("x")) decoded = GwtVfs.decodeBytes(value);
                else decoded = value;
                values.put(key, decoded);
            }
        }catch(Throwable error){
            values.clear();
            throw new ArcRuntimeException("Could not load browser settings", error);
        }
    }

    private Storage storage(){
        Storage storage = GwtFiles.LocalStorage;
        if(storage == null) throw new ArcRuntimeException("localStorage is unavailable; browser persistence cannot start");
        return storage;
    }
}
