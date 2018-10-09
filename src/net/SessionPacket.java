package net;

import java.io.*;

/**
 * Represents a packet of data that is a part of a communication Session.
 */
public class SessionPacket {

    public static final int HEADER_OVERHEAD = 6 * 4;

    private int sessionID;

    private int messageID;

    private PacketType type;

    private int ack;

    private int window;

    private int payloadSize;

    private byte[] payload;

    public SessionPacket(int sessionID, int messageID, PacketType type, int ack, int window, int payloadSize, byte[] data) {
        this.sessionID = sessionID;
        this.messageID = messageID;
        this.type = type;
        this.ack = ack;
        this.window = window;
        this.payloadSize = payloadSize;

        ByteArrayOutputStream out = new ByteArrayOutputStream(HEADER_OVERHEAD + data.length);
        DataOutputStream dout = new DataOutputStream(out);
        try {
            dout.writeInt(sessionID);
            dout.writeInt(messageID);
            dout.writeInt(type.value);
            dout.writeInt(ack);
            dout.writeInt(window);
            dout.writeInt(payloadSize);
            dout.write(data);
            this.payload = out.toByteArray();

        } catch (IOException e) {
            // this really should be impossible
            e.printStackTrace();
        }
    }

    public SessionPacket(byte[] packet) throws IOException {
        DataInputStream din = new DataInputStream(new ByteArrayInputStream(packet));
        this.sessionID = din.readInt();
        this.messageID = din.readInt();
        this.type = PacketType.fromInt(din.readInt());
        this.ack = din.readInt();
        this.window = din.readInt();
        this.payloadSize = din.readInt();
        this.payload = new byte[this.payloadSize];

        din.read(this.payload);
    }

    public int getSessionID() {
        return this.sessionID;
    }

    public int getMessageID() {
        return this.messageID;
    }

    public PacketType getType() {
        return this.type;
    }

    public int getAck() {
        return this.ack;
    }

    public int getWindow() {
        return this.window;
    }

    public int getPayloadSize() {
        return this.payloadSize;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public enum PacketType {
        UNRECOGNIZED    (0x00),
        SESSION_INIT    (0x01),
        INIT_RESPONSE   (0x02),
        MESSAGE         (0x03)
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
                default : return UNRECOGNIZED;
            }
        }
    }
}
