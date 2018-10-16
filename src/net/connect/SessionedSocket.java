package net.connect;

import net.common.Constants;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionedSocket {

    private final Object socketLock;
    private DatagramSocket sock;

    private final Object sessionsLock;
    private HashMap<Integer, Session> sessions;

    private int port;
    private DefaultPacketHandler defaultHandler;

    private final Object sendLock;
    private LinkedBlockingDeque<SessionPacket> sendQueue;

    private AtomicBoolean running;

    private Thread listener;
    private Thread sender;

    public SessionedSocket(int port, DefaultPacketHandler defaultHandler) {
        this.port = port;
        this.sessionsLock = new Object();
        this.sessions = new HashMap<>();
        this.defaultHandler = defaultHandler;
        this.socketLock = new Object();
        this.sendLock = new Object();
        this.sendQueue = new LinkedBlockingDeque<>();
        this.running = new AtomicBoolean(false);
        this.listener = new Thread(this::listen);
        this.listener.setName("[SessionedSocket][Listener]");
        this.sender = new Thread(this::sender);
        this.sender.setName("[SessionedSocket][Sender]");
    }

    public void init() throws SocketException {
        try {
            SocketAddress address = Utils.getSocketAddress(this.port);
            if (address == null) {
                System.err.println("[SessionedSocket][init] Unable to find address to bind");
                return;
            }

            this.sock = new DatagramSocket(address);

        } catch (SocketException e) {
            System.err.println("[SessionedSocket][init] SocketException while trying to bind socket");
            e.printStackTrace();
            return;
        }

        System.out.println("[SessionedSocket][init] Socket bound to local address and port: " + this.sock.getLocalAddress() + ":" + this.sock.getLocalPort());
        this.running.set(true);
        this.listener.start();
        this.sender.start();
    }

    public void shutdown() {
        this.running.set(false);
        this.sock.close();
        synchronized (this.sessionsLock) {
            for (Session session : this.sessions.values()) {
                try {
                    session.close();

                } catch (IOException e) {
                    System.err.println("[SessionedSocket][shutdown] IOException while closing session");
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean isShutdown() {
        synchronized (this.sessionsLock) {
            return !this.running.get() && this.sessions.isEmpty();
        }
    }

    public Session createSession(InetAddress remote, int port) {
        if (!this.running.get()) throw new IllegalStateException("SessionedSocket is not opened");
        int id = (this.sock.getLocalAddress().hashCode() ^ remote.hashCode() << 4) +
                (this.sock.getLocalPort() ^ port << 8) + (int) System.currentTimeMillis() + ((int) System.nanoTime());
        Session session = new Session(this, id, remote, port);
        this.sessions.put(id, session);
        session.open();
        session.sendSessionInit();
        return session;
    }

    public Session createSession(SessionPacket init) {
        if (!this.running.get()) throw new IllegalStateException("SessionedSocket is not opened");
        Session session = new Session(this, init.getSessionID(), init.getRemote(), init.getPort());
        this.sessions.put(init.getSessionID(), session);
        session.open();
        session.sendInitResponse();
        return session;
    }

    public InetAddress getAddress() {
        return this.sock.getLocalAddress();
    }

    public int getPort() {
        return this.sock.getLocalPort();
    }

    protected synchronized void send(SessionPacket p) throws IOException {
        if (p.getPayloadSize() > Constants.PACKET_SIZE) {
            throw new IllegalArgumentException("Packet length too big");
        }

       try {
          this.sendQueue.putLast(p);
       } catch (InterruptedException e) {
          System.err.println("[SessionedSocket][send] Interrupted while trying to queue packet");
          e.printStackTrace();
       }

       // uncomment for debug
        //printPacketDetails(p, false);
    }

    private void sender() {
       while (this.running.get()) {
          try {
             SessionPacket p = this.sendQueue.takeFirst();

             try {
                synchronized (this.socketLock) {
                   DatagramPacket packet = new DatagramPacket(p.getPacket(), 0, p.getPayloadSize() + Constants.HEADER_OVERHEAD, p.getRemote(), p.getPort());
                   this.sock.send(packet);
                }
                //System.out.println("[SessionedSocket][send][" + p.getSessionID() + "] Sent " + p.getType() + " packet");

             } catch (BindException e) {
                System.err.println("[SessionedSocket][send] BindException while trying to send packet");
                System.err.println("\tLocalAddress: " + this.sock.getLocalAddress());
                System.err.println("\tLocalPort: " + this.sock.getLocalPort());
                System.err.println("\tRemoteAddress: " + p.getRemote());
                System.err.println("\tRemotePort: " + p.getPort());
             } catch (IOException e) {
                System.err.println("[SessionedSocket][sender] IOException while sending packet");
                e.printStackTrace();
             }

          } catch (InterruptedException e) {
             e.printStackTrace();
          }
       }
    }

    public void listen() {
        DatagramPacket packet = new DatagramPacket(new byte[Constants.PACKET_SIZE], Constants.PACKET_SIZE);

        while (this.running.get()) {
            try {
                this.sock.receive(packet);
                byte[] data = packet.getData();
                SessionPacket sessPack = new SessionPacket(data, packet.getAddress(), packet.getPort());

                // uncomment for debug
                //printPacketDetails(sessPack, true);
                //System.out.println("[SessionedSocket][listen][" + this.port + "][" + sessPack.getSessionID() + "] Received " + sessPack.getType() + " packet");

                synchronized (this.sessionsLock) {
                    if (this.sessions.containsKey(sessPack.getSessionID())) {
                        try {
                            this.sessions.get(sessPack.getSessionID()).onPacket(sessPack);
                        } catch (InterruptedException e) {
                            System.err.println("[SessionedSocket][listen] Interrupted while passing packet to Session");
                            e.printStackTrace();
                        }

                    } else {
                        System.out.println("[SessionedSocket][listen] Passing packet to default handler");
                        if (this.defaultHandler != null) this.defaultHandler.handlePacket(sessPack);
                    }
                }

            } catch (IOException e) {
                System.err.println("[SessionedSocket][listen] IOException while receiving packet");
                e.printStackTrace();
            }
        }

        System.out.println("[SessionedSocket][Listener] SessionedSocket Listener thread terminating");
    }

    protected void sessionClosed(int id) {
        synchronized (this.sessionsLock) {
            //System.out.print("[SessionedSocket][sessionClosed] Session ");
            //System.out.print(id);
            //System.out.println(" closed");
            this.sessions.remove(id);

            if (!this.running.get() && this.sessions.isEmpty()) System.out.println("[SessionedSocket] SessionedSocket shutdown");
        }
    }

    public static void printPacketDetails(SessionPacket packet, boolean received) {
        System.out.print("[");
        System.out.print(System.currentTimeMillis());
        if (received) System.out.println("][SessionedSocket] Received Packet:");
        else System.out.println("][SessionedSocket] Sending Packet:");
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
