package net.lib;

public enum PacketType {
    UNKNOWN     (0),
    SYN         (1),
    MESSAGE     (2),
    ACK         (3),
    CLOSE       (4),
    POKE        (5);

    final int value;

    PacketType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static PacketType fromValue(int val) {
        switch (val) {
            case 1 : return SYN;
            case 2 : return MESSAGE;
            case 3 : return ACK;
            case 4 : return CLOSE;
            case 5 : return POKE;
            default : return UNKNOWN;
        }
    }
}
