package mindustry.web.teavm;

import arc.*;
import mindustry.*;
import mindustry.net.Net.NetProvider;
import mindustry.ui.FileChooser.FileChooserParams;

/** TeaVM/JavaScript entry point for the real current Mindustry ClientLauncher. */
public final class WebLauncher{
    private WebLauncher(){}

    public static void main(String[] args){
        new WebApplication(() -> new WebClient()).start();
    }

    private static final class WebClient extends ClientLauncher{
        private final NetProvider net = new WebNetProvider();
        @Override public void setup(){ Vars.skipModCode = true; super.setup(); }
        @Override public NetProvider getNet(){ return net; }
        @Override public void showFileChooser(FileChooserParams params){ WebFileChooser.show(params); }
    }
}
