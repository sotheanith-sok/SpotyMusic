package net.connect;

import net.Constants;

import java.io.*;
import java.net.InetAddress;

/**
 * Represents a packet of data that is a part of a communication Socket.
 *
 * @deprecated
 */
public class SessionPacket {

    private int sessionID;

    private int messageID;

    private PacketType type;

    private int ack;

    private int window;

    private int payloadSize;

    private byte[] payload;

    private InetAddress remote;

    private int port;

    public SessionPacket(int sessionID, int messageID, PacketType type, int ack, int window, byte[] data, InetAddress remote, int port) {
        this.sessionID = sessionID;
        this.messageID = messageID;
        this.type = type;
        this.ack = ack;
        this.window = window;
        this.payloadSize = data.length;
        this.payload = data;
        this.remote = remote;
        this.port = port;
    }

    public SessionPacket(int sessionID, int messageID, PacketType type, int ack, int window, byte[] data, InetAddress remote, int port, int size) {
        this(sessionID, messageID, type, ack, window, data, remote, port);
        this.payloadSize = size;
    }

    public SessionPacket(byte[] packet, InetAddress remote, int port) throws IOException {
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(packet));
        this.sessionID = din.readInt();
        this.messageID = din.readInt();
        this.type = PacketType.fromInt(din.readInt());
        this.ack = din.readInt();
        this.window = din.readInt();
        this.payloadSize = din.readInt();
        this.payload = new byte[this.payloadSize];
        this.remote = remote;
        this.port = port;

        din.read(this.payload);
    }

    public int getSessionID() {
        return this.sessionID;
    }

    public int getMessageID() {
        return this.messageID;
    }

    public void setMessaegID(int id) {
        this.messageID = id;
    }

    public PacketType getType() {
        return this.type;
    }

    public int getAck() {
        return this.ack;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public int getWindow() {
        return this.window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public int getPayloadSize() {
        return this.payloadSize;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public byte[] getPacket() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Constants.HEADER_OVERHEAD + this.payload.length);
        DataOutputStream dout = new DataOutputStream(out);
        try {
            dout.writeInt(sessionID);
            dout.writeInt(messageID);
            dout.writeInt(type.value);
            dout.writeInt(ack);
            dout.writeInt(window);
            dout.writeInt(payloadSize);
            dout.write(payload, 0, payloadSize);

        } catch (IOException e) {
            // this really should be impossible
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    public InetAddress getRemote() {
        return this.remote;
    }

    public int getPort() {
        return this.port;
    }

    public enum PacketType {
        UNRECOGNIZED    (0x00),
        SESSION_INIT    (0x01),
        INIT_RESPONSE   (0x02),
        MESSAGE         (0x03),
        KEEP_ALIVE      (0x04),
        CLOSE           (0x05),
        CLOSE_ACK       (0x06),
        CLOSE_SEND      (0x07)
        ;

        private final int value;

        PacketType(int val) {
            this.value = val;
        }

        public static PacketType fromInt(int val) {
            switch (val) {
                case 0x01 : return SESSION_INIT;
                case 0x02 : return INIT_RESPONSE;
                case 0x03 : return MESSAGE;
                case 0x04 : return KEEP_ALIVE;
                case 0x05 : return CLOSE;
                case 0x06 : return CLOSE_ACK;
                case 0x07 : return CLOSE_SEND;
                default : return UNRECOGNIZED;
            }
        }
    }
}
