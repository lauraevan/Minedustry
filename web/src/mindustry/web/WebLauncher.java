package mindustry.web;

import arc.*;
import arc.backend.gwt.*;
import mindustry.*;
import mindustry.net.Net.NetProvider;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Browser entry point.
 *
 * The first compatibility milestone intentionally boots with browser-safe
 * platform services. Audio and normal TCP/UDP multiplayer are not faked.
 */
public final class WebLauncher extends GwtApplication{

    @Override
    public GwtApplicationConfiguration getConfig(){
        GwtApplicationConfiguration config = new GwtApplicationConfiguration(1280, 720);
        config.disableAudio = true;
        config.rootPanel = RootPanel.get("app");
        return config;
    }

    @Override
    public ApplicationListener createApplicationListener(){
        return new WebClient();
    }

    private static final class WebClient extends ClientLauncher{
        private final NetProvider webNet = new WebNetProvider();

        @Override
        public void setup(){
            // Must be set before ClientLauncher begins loading Mods/content.
            Vars.skipModCode = true;
            super.setup();
        }

        @Override
        public NetProvider getNet(){
            return webNet;
        }
    }
}
