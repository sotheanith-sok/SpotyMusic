package net.lib;

import net.Constants;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerSocket {

    private final Object socketLock;
    private DatagramSocket socket;

    private final Object socketsLock;
    private HashMap<Integer, SlaveSocket> sockets;

    private SocketHandler handler;

    private Thread listenerThread;

    private AtomicBoolean running;

    public ServerSocket(int port, SocketHandler handler) throws SocketException {
        this(Utils.getNonLoopback(), port, handler);
    }

    public ServerSocket(NetworkInterface iface, int port, SocketHandler handler) throws SocketException {
        this(Utils.getSocketAddress(iface, port), handler);
    }

    public ServerSocket(SocketAddress address, SocketHandler handler) throws SocketException {
        this.socket = new DatagramSocket(address);
        this.handler = handler;
        this.socketLock = new Object();

        this.socketsLock = new Object();
        this.sockets = new HashMap<>();

        this.listenerThread = new Thread(this::listener);
        this.listenerThread.setName("[ServerSocket][listener]");

        this.running = new AtomicBoolean(false);
    }

    public void open() {
        this.running.set(true);
        this.listenerThread.start();
    }

    public InetAddress localAddress() {
        return this.socket.getLocalAddress();
    }

    public int getPort() {
        return this.socket.getLocalPort();
    }

    private void listener() {
        while (this.running.get()) {
            DatagramPacket packet = new DatagramPacket(new byte[Constants.PACKET_SIZE + Constants.HEADER_OVERHEAD], Constants.PACKET_SIZE + Constants.HEADER_OVERHEAD);
            try {
                this.socket.receive(packet);

                int key = packet.getAddress().hashCode() ^ packet.getPort();

                //System.out.println("[ServerSocket][listener] Received packet from " + packet.getAddress() + ":" + packet.getPort());

                synchronized (this.socketsLock) {
                    if (this.sockets.containsKey(key)) {
                        this.sockets.get(key).transferPacket(packet);

                    } else {

                        ByteArrayInputStream in = new ByteArrayInputStream(packet.getData());
                        DataInputStream reader = new DataInputStream(in);

                        PacketType type = PacketType.fromValue(reader.readInt());
                        reader.close();

                        if (type == PacketType.SYN) {
                            //System.out.println("[ServerSocket][listener] Received SYN packet, creating new SlaveSocket");
                            SlaveSocket newSocket = new SlaveSocket(packet.getAddress(), packet.getPort());
                            newSocket.transferPacket(packet);
                            this.sockets.put(key, newSocket);
                            this.handler.handleSocket(newSocket);

                        } // do something with non-SYN packets?
                    }


                }

            } catch (IOException e) {
                System.err.println("[ServerSocket][listener] IOException while waiting to receive packets");
                e.printStackTrace();
            }
        }
    }

    protected void socketClosed(SlaveSocket socket) {
        synchronized (this.socketsLock) {
            //System.out.println("[ServerSocket][socketClosed] Socket to " + socket.remote + ":" + socket.port + " closed");
            this.sockets.remove(socket.remote.hashCode() ^ socket.port);
        }
    }

    @FunctionalInterface
    public interface SocketHandler {
        void handleSocket(Socket socket);
    }

    private class SlaveSocket extends Socket {

        private SynchronousQueue<DatagramPacket> transfer;

        public SlaveSocket(InetAddress remote, int port) {
            super(remote, port);

            this.transfer = new SynchronousQueue<>();
            this.state.set(LISTEN);
            this.receiver.start();
        }

        @Override
        protected DatagramPacket receivePacket() {
            try {
                //System.out.println("[SlaveSocket][receivePacket] Attempting to receive packet...");
                return this.transfer.poll(Constants.TIMEOUT_DELAY / 2, TimeUnit.MILLISECONDS);

            } catch (InterruptedException e) {
                return null;
            }
        }

        @Override
        public void sender() {
            if (this.state.get() == SYN_RECEIVED) {
                try {
                    this.sendSyn();
                    this.state.compareAndSet(SYN_RECEIVED, ESTABLISHED);

                } catch (SocketTimeoutException e) {
                    System.err.println("[SlaveSocket][sender] Timed out sending response SYN");
                    e.printStackTrace();
                }
            }

            super.sender();
        }

        @Override
        protected Object getSocketLock() {
            return socketLock;
        }

        @Override
        protected DatagramSocket getSocket() {
            return socket;
        }

        protected void transferPacket(DatagramPacket packet) {
            if (this.state.get() == CLOSED) return;
            try {
                this.transfer.offer(packet, Constants.TIMEOUT_DELAY / 4, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (this.state.get() == CLOSED) return;
                System.err.println("[SlaveSocket][transferPacket] Interrupted while trying to transfer packet to receiver thread");
                e.printStackTrace();
            }
        }

        @Override
        protected void onSyn(InetAddress remote, int port, int id) {
            if (this.enforceOrdering(id)) {
                this.state.compareAndSet(LISTEN, SYN_RECEIVED);
                this.sender.start();
            }
        }

        @Override
        public void onClose(int id) {
            //if (this.state.get() == ESTABLISHED) System.out.println("[SlaveSocket][onClose] Client sent CLOSE packet");
            super.onClose(id);
        }

        @Override
        protected void onClosed() {
            //System.out.println("[SlaveSocket][onClosed] SlaveSocket closed.");
            //System.out.println("[SlaveSocket][onClosed] receiveBuffer.isReadOpened() = " + this.sendBuffer.isReadOpened());
            //System.out.println("[SlaveSocket][onClosed] this.isReceiveClosed() = " + this.isReceiveClosed());
            socketClosed(this);
        }

        @Override
        protected void onTimeout() {

        }
    }
}
