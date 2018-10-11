package net.connect;

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

    public Session createSession(InetAddress remote, int port) {
        int id = (this.sock.getLocalAddress().hashCode() ^ remote.hashCode() << 4) +
                (this.sock.getLocalPort() ^ port << 8) + (int) System.currentTimeMillis();
        Session session = new Session(this, id, remote, port);
        this.sessions.put(id, session);
        session.open();
        session.sendSessionInit();
        return session;
    }

    public Session createSession(SessionPacket init) {
        Session session = new Session(this, init.getSessionID(), init.getRemote(), init.getPort());
        this.sessions.put(init.getSessionID(), session);
        session.open();
        session.sendInitResponse();
        return session;
    }

    protected synchronized void send(SessionPacket p) throws IOException {
        if (p.getPayloadSize() > PACKET_LENGTH) {
            throw new IllegalArgumentException("Packet length too big");
        }

        DatagramPacket packet = new DatagramPacket(p.getPacket(), 0, p.getPayloadSize() + SessionPacket.HEADER_OVERHEAD, p.getRemote(), p.getPort());
        this.sock.send(packet);

        printPacketDetails(p, false);
    }

    public void listen() {
        DatagramPacket packet = new DatagramPacket(new byte[PACKET_LENGTH], PACKET_LENGTH);

        while (this.running) {
            try {
                this.sock.receive(packet);
                byte[] data = packet.getData();
                SessionPacket sessPack = new SessionPacket(data, packet.getAddress(), packet.getPort());

                printPacketDetails(sessPack, true);

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

    protected void sessionClosed(int id) {
        synchronized (this) {
            System.out.print("[SessionedSocket][sessionClosed] Session ");
            System.out.print(id);
            System.out.println(" closed");
            this.sessions.remove(id);
        }
    }

    public static void printPacketDetails(SessionPacket packet, boolean received) {
        // uncomment for debugging

        System.out.print("[");
        System.out.print(System.currentTimeMillis());
        if (received) System.out.println("][scratch] Received Packet:");
        else System.out.println("][scratch] Sending Packet:");
        System.out.print("\tRemote: ");
        System.out.println(packet.getRemote());
        System.out.print("\tPort: ");
        System.out.println(packet.getPort());
        System.out.print("\tSessionID: ");
        System.out.println(packet.getSessionID());
        System.out.print("\tMessageID: ");
        System.out.println(packet.getMessageID());
        System.out.print("\tPacketType: ");
        System.out.println(packet.getType().toString());
        System.out.print("\tAckID: ");
        System.out.println(packet.getAck());
        System.out.print("\tWindow: ");
        System.out.println(packet.getWindow());
        System.out.print("\tPayloadSize: ");
        System.out.println(packet.getPayloadSize());


    }

    public interface DefaultPacketHandler {
        void handlePacket(SessionPacket packet);
    }
}
