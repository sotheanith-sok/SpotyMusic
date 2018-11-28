package net;

public class Constants {
    // connect/lib package
    public static final int PACKET_SIZE = 1024 * 17;
    public static final int HEADER_OVERHEAD = 2 * 4;
    public static final int FOOTER_OVERHEAD = 8;
    public static final int PACKET_BUFFER_SIZE = PACKET_SIZE + HEADER_OVERHEAD + FOOTER_OVERHEAD;
    public static final long RESEND_DELAY = 2048;
    public static final long TIMEOUT_DELAY = 1024 * 6;
    public static final int BUFFER_SIZE = 1024 * 200;

    // client package
    public static final int MIN_BUFFERED_DATA = 1024 * 16;

    // client and server packages
    public static final String REQUEST_TYPE_PROPERTY = "REQUEST_TYPE";
    public static final String REQUEST_LIST_ARTISTS = "REQUEST_LIST_ARTISTS";
    public static final String REQUEST_LIST_ALBUMS = "REQUEST_LIST_ALBUMS";
    public static final String REQUEST_LIST_SONGS = "REQUEST_LIST_SONGS";
    public static final String REQUEST_STREAM_SONG = "REQUEST_STREAM";
    public static final String REQUEST_SUBSCRIBE = "REQUEST_SUBSCRIBE";
    // library change subscription event types
    public static final String EVENT_TYPE_PROPERTY = "EVENT_TYPE";
    public static final String EVENT_SONG_ADDED = "EVENT_SONG_ADDED";
    public static final String EVENT_SONG_REMOVED = "EVENT_SONG_REMOVED";
    public static final String EVENT_ARTIST_ADDED = "EVENT_ARTIST_ADDED";
    public static final String EVENT_ARTIST_REMOVED = "EVENT_ARTIST_REMOVED";
    public static final String EVENT_ALBUM_ADDED = "EVENT_ALBUM_ADDED";
    public static final String EVENT_ALBUM_REMOVED = "EVENT_ALBUM_REMOVED";

    // logging levels
    public static final int SEVERE = 100;
    public static final int ERROR = 90;
    public static final int WARN = 80;
    public static final int INFO = 70;
    public static final int LOG = 60;
    public static final int FINE = 50;
    public static final int FINER = 40;
    public static final int FINEST  = 30;
    public static final int DEBUG = 20;
    public static final int TRACE = 10;

    public static final long MAX_BLOCK_SIZE = 1024 * 1024 * 4;

    public static final long MAX_CHANNEL_WAIT = 1000;

    public static final String PROPERTY_RESPONSE_STATUS = "PROP_RESPONSE_STATUS";
    public static final String RESPONSE_STATUS_OK = "STATUS_OK";
    public static final String RESPONSE_STATUS_NOT_FOUND = "STATUS_NOT_FOUND";
    public static final String RESPONSE_STATUS_SERVER_ERROR = "STATUS_SERVER_ERROR";
}
