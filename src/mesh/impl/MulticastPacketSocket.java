package mesh.impl;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.Constants;
import net.common.DeferredJsonGenerator;
import net.common.JsonField;
import net.common.JsonStreamParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class MulticastPacketSocket {

    private static JsonFactory factory = new JsonFactory();

    private ExecutorService executor;

    private MulticastSocket multicastSocket;

    private final Object lock;

    private ConcurrentHashMap<String, PacketHandler> handlers;

    private AtomicBoolean running;

    public MulticastPacketSocket(InetSocketAddress address, ExecutorService executor) throws IOException {
        this.executor = executor;
        this.multicastSocket = new MulticastSocket(address.getPort());
        this.multicastSocket.setSoTimeout((int) Constants.RESEND_DELAY / 2);
        this.multicastSocket.joinGroup(address.getAddress());
        this.lock = new Object();
        this.handlers = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(true);
        this.executor.submit(this::receiver);
    }

    private void receiver() {
        DatagramPacket temp = new DatagramPacket(new byte[Constants.PACKET_SIZE], Constants.PACKET_SIZE);

        while (this.running.get()) {
            try {
                this.multicastSocket.receive(temp);

                ByteArrayInputStream instrm = new ByteArrayInputStream(temp.getData(), temp.getOffset(), temp.getLength());
                JsonStreamParser parser = new JsonStreamParser(instrm, true, (field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField packet = (JsonField.ObjectField) field;
                    if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY)) {
                        System.err.println("[MulticastPacketSocket][receiver] Received packet without type");

                    } else {
                        this.executor.submit(() -> {
                            String type = packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY);

                            if (this.handlers.containsKey(type)) {
                                this.handlers.get(type).onPacket(packet, temp.getAddress());

                            } else {
                                System.err.println("[MulticastPacketSocket][receiver][handler] No handler for packet type: " + type);
                            }
                        });
                    }
                });
                parser.run();

            } catch (SocketTimeoutException e) {
                // if timed out, don't printStackTrace.
                // timing out occasionally prevents receiver thread from blocking indefinitely

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.multicastSocket.close();
    }

    public void send(JsonField.ObjectField packet) {
        try {
            ByteArrayOutputStream strm = new ByteArrayOutputStream();
            JsonGenerator gen = factory.createGenerator(strm, JsonEncoding.UTF8);
            packet.write(gen);
            gen.close();
            strm.close();
            this.send(strm.toByteArray(), 0, strm.size());

        } catch (IOException e) {
            System.err.println("[MulticastPacketSocket][send] IOException while sending packet");
            e.printStackTrace();
        }
    }

    public void send(JsonField.ObjectField packet, InetAddress dest) {
        try {
            ByteArrayOutputStream strm = new ByteArrayOutputStream();
            JsonGenerator gen = factory.createGenerator(strm, JsonEncoding.UTF8);
            packet.write(gen);
            gen.close();
            strm.close();
            this.send(strm.toByteArray(), 0, strm.size(), dest);

        } catch (IOException e) {
            System.err.println("[MulticastPacketSocket][send] IOException while sending unicast packet");
            e.printStackTrace();
        }
    }

    public void send(DeferredJsonGenerator packet) {
        try {
            ByteArrayOutputStream strm = new ByteArrayOutputStream();
            JsonGenerator gen = factory.createGenerator(strm, JsonEncoding.UTF8);
            packet.generate(gen);
            gen.close();
            strm.close();
            this.send(strm.toByteArray(), 0, strm.size());

        } catch (IOException e) {
            System.err.println("[MulticastPacketSocket][send] IOException while sending packet");
            e.printStackTrace();
        }
    }

    public void send(DeferredJsonGenerator packet, InetAddress dest) {
        try {
            ByteArrayOutputStream strm = new ByteArrayOutputStream();
            JsonGenerator gen = factory.createGenerator(strm, JsonEncoding.UTF8);
            packet.generate(gen);
            gen.close();
            strm.close();
            this.send(strm.toByteArray(), 0, strm.size(), dest);

        } catch (IOException e) {
            System.err.println("[MulticastPacketSocket][send] IOException while sending unicast packet");
            e.printStackTrace();
        }
    }

    private void send(byte[] data, int offset, int length) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, offset, length);
        synchronized (this.lock) {
            this.multicastSocket.send(packet);
        }
    }

    private void send(byte[] data, int offset, int length, InetAddress dest) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, offset, length);
        packet.setAddress(dest);
        packet.setPort(this.multicastSocket.getPort());
        synchronized (this.lock) {
            this.multicastSocket.send(packet);
        }
    }

    public void addHandler(String type, PacketHandler handler) {
        this.handlers.put(type, handler);
    }

    public void removeHandler(String type, PacketHandler handler) {
        this.handlers.remove(type, handler);
    }

    public void close() {
        this.running.set(false);
    }

    @FunctionalInterface
    public interface PacketHandler {
        void onPacket(JsonField.ObjectField packet, InetAddress source);
    }
}
