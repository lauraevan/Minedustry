package mindustry.web;

import arc.func.*;
import arc.struct.*;
import mindustry.net.*;
import mindustry.net.Net.NetProvider;

import java.io.*;

/**
 * Correct offline browser transport for the first boot milestone.
 *
 * Browsers cannot directly open the TCP/UDP sockets used by normal Mindustry
 * multiplayer. A later WebSocket relay can replace this provider without
 * contaminating game logic with browser-specific networking.
 */
public final class WebNetProvider implements NetProvider{
    private final Seq<NetConnection> none = new Seq<>();

    @Override
    public void connectClient(String ip, int port, Runnable success) throws IOException{
        throw new IOException("Direct TCP/UDP multiplayer is unavailable in browsers; use the WebSocket relay backend.");
    }

    @Override
    public void sendClient(Object object, boolean reliable){
        // No active browser multiplayer connection in the offline provider.
    }

    @Override
    public void disconnectClient(){
    }

    @Override
    public void discoverServers(Cons<Host> callback, Runnable done){
        done.run();
    }

    @Override
    public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed){
        failed.get(new IOException("Direct server pings are unavailable in a browser."));
    }

    @Override
    public void hostServer(int port) throws IOException{
        throw new IOException("A browser cannot host a normal Mindustry TCP/UDP server.");
    }

    @Override
    public Iterable<? extends NetConnection> getConnections(){
        return none;
    }

    @Override
    public void closeServer(){
    }
}
