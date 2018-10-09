package net;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;

public class SessionedSocket {

    public static final int PACKET_LENGTH = 1024;

    private DatagramSocket sock;

    private HashMap<Integer, Session> sessions;

    private int port;
    DefaultPacketHandler defaultHandler;

    private boolean running = false;

    private Thread listener;

    public SessionedSocket(int port, DefaultPacketHandler defaultHandler) {
        this.port = port;
        this.sessions = new HashMap<>();
        this.defaultHandler = defaultHandler;
        this.listener = new Thread(this::listen);
    }

    public void init() throws SocketException {
        this.sock = new DatagramSocket(this.port);
        this.running = true;
        this.listener.start();
    }

    protected void send(InetAddress address, int port, SessionPacket packet) throws IOException {
        this.send(address, port, packet, 0, packet.getPayloadSize());
    }

    protected void send(InetAddress address, int port, SessionPacket p, int offset, int length) throws IOException {
        if (length > PACKET_LENGTH) {
            throw new IllegalArgumentException("Packet length too big");
        }

        DatagramPacket packet = new DatagramPacket(p.getPayload(), offset, length, address, port);
        this.sock.send(packet);
    }

    public void listen() {
        DatagramPacket packet = new DatagramPacket(new byte[PACKET_LENGTH], PACKET_LENGTH);

        while (this.running) {
            try {
                this.sock.receive(packet);
                byte[] data = packet.getData();
                SessionPacket sessPack = new SessionPacket(data);
                if (this.sessions.containsKey(sessPack.getSessionID())) {
                    try {
                        this.sessions.get(sessPack.getSessionID()).onPacket(sessPack);
                    } catch (InterruptedException e) {
                        System.err.println("[SessionedSocket][listen] Interrupted while passing packet to Session");
                        e.printStackTrace();
                    }

                } else {
                    if (this.defaultHandler != null) this.defaultHandler.handlePacket(sessPack);
                }

            } catch (IOException e) {
                System.err.println("[SessionedSocket][listen] IOException while receiving packet");
                e.printStackTrace();
            }
        }
    }

    public interface DefaultPacketHandler {
        void handlePacket(SessionPacket packet);
    }
}
