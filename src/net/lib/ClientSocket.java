package net.lib;

import net.Constants;

import java.io.IOException;
import java.net.*;

import static net.Constants.PACKET_BUFFER_SIZE;

public class ClientSocket extends Socket {

    private final Object socketLock;

    private DatagramSocket socket;

    public ClientSocket(InetAddress remote, int port) {
        super(remote, port);

        this.socketLock = new Object();
    }

    public ClientSocket(InetAddress remote, int port, int sendBuffer, int receiveBuffer) {
        super(remote, port, sendBuffer, receiveBuffer);
        this.socketLock = new Object();
    }

    public void connect() throws SocketException, SocketTimeoutException {
        this.socket = new DatagramSocket(new InetSocketAddress(0));
        this.socket.connect(this.remote, this.port);
        this.socket.setSoTimeout((int) Constants.TIMEOUT_DELAY / 2);
        this.receiver.start();
        this.sendSyn();
        this.state.set(SYN_SENT);
        //System.out.println("[ClientSocket] SYN acknowledged");
        this.sender.start();
    }

    protected DatagramPacket receivePacket() throws IOException {
        if (this.state.get() == CLOSED) return null;
        DatagramPacket packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);
        try {
            this.socket.receive(packet);
            return packet;

        } catch (SocketTimeoutException e) {
            return null;

        } catch (IOException e) {
            if (this.socket.isClosed()) return null;
            throw e;
        }
    }

    @Override
    protected Object getSocketLock() {
        return this.socketLock;
    }

    @Override
    protected DatagramSocket getSocket() {
        return this.socket;
    }

    @Override
    protected void onSyn(InetAddress remote, int port, int id) {
        if (this.state.compareAndSet(SYN_SENT, ESTABLISHED)) {
            this.sendAck(id);
            System.out.println("[ClientSocket][onSyn] Connection established");

        } else {
            System.err.println("[ClientSocket][onSyn] Received unexpected SYN packet");
        }
    }

    @Override
    public void onClose(int id) {
        //if (this.state.get() == ESTABLISHED) System.out.println("[ClientSocket][onClose] Server sent CLOSE packet");
        super.onClose(id);
    }

    @Override
    protected void onClosed() {
        this.socket.close();
        if (this.debug <= Constants.FINE) System.out.println("[ClientSocket][onClosed] ClientSocket closed successfully");
        //System.out.println("[ClientSocket][onClosed] receiveBuffer.isReadOpened() = " + this.sendBuffer.isReadOpened());
    }

    @Override
    protected void onTimeout() {

    }
}
