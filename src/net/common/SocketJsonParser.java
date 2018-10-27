package net.common;

import net.lib.Socket;

public class SocketJsonParser implements Runnable {

    private Socket socket;

    private Handler handler;

    private boolean autoCloseSocket = false;

    private boolean globalArrayAsStream = false;

    public boolean debug = false;

    public SocketJsonParser(Socket socket, boolean autoCloseSocket, Handler handler) {
        this.socket = socket;
        this.autoCloseSocket = autoCloseSocket;
        this.handler = handler;
    }

    public SocketJsonParser(Socket socket, boolean autoCloseSocket, Handler handler, boolean globalArrayAsStream) {
        this(socket, autoCloseSocket, handler);
        this.globalArrayAsStream = globalArrayAsStream;
    }

    public void run() {
        JsonStreamParser parser = new JsonStreamParser(this.socket.inputStream(), this.autoCloseSocket, (field) -> this.handler.handle(this.socket, field), this.globalArrayAsStream);
        parser.run();
    }

    @FunctionalInterface
    public interface Handler {
        void handle(Socket socket, JsonField field);
    }

}
