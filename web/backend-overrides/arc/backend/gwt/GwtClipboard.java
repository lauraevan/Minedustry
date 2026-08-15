package arc.backend.gwt;

/**
 * Compatibility clipboard used by any legacy backend code that still expects a
 * stateful object. Current Arc exposes clipboard methods directly on Application.
 */
public class GwtClipboard{
    private String content = "";

    public String getContents(){
        return content;
    }

    public void setContents(String content){
        this.content = content == null ? "" : content;
    }
}
