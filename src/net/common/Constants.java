package net.common;

public class Constants {
    // connect package
    public static final int PACKET_SIZE = 1024;
    public static final int HEADER_OVERHEAD = 2 * 4;
    public static final int PACKET_BUFFER_SIZE = PACKET_SIZE + HEADER_OVERHEAD;
    public static final long RESEND_DELAY = 4096;
    public static final long TIMEOUT_DELAY = 16384;
    public static final int BUFFER_SIZE = 1024 * 10;

    // client package
    public static final int MIN_BUFFERED_DATA = 2048;

    // client and server packages
    public static final String REQUEST_TYPE_PROPERTY = "type";
    public static final String REQUEST_LIST_ARTISTS = "list-artists";
    public static final String REQUEST_LIST_ALBUMS = "list-albums";
    public static final String REQUEST_LIST_SONGS = "list-songs";
    public static final String REQUEST_STREAM_SONG = "stream-song";
    public static final String REQUEST_SUBSCRIBE = "subscribe-changes";
    // library change subscription event types
    public static final String EVENT_TYPE_PROPERTY = "type";
    public static final String EVENT_SONG_ADDED = "song-added";
    public static final String EVENT_SONG_REMOVED = "song-removed";
    public static final String EVENT_ARTIST_ADDED = "artist-added";
    public static final String EVENT_ARTIST_REMOVED = "artist-removed";
    public static final String EVENT_ALBUM_ADDED = "album-added";
    public static final String EVENT_ALBUM_REMOVED = "album-removed";

    public static final int SEVERE = 100;
    public static final int ERROR = 90;
    public static final int WARN = 80;
    public static final int INFO = 70;
    public static final int LOG = 60;
    public static final int FINE = 50;
    public static final int FINER = 40;
    public static final int FINEST  = 30;
    public static final int DEBUG = 20;

}
