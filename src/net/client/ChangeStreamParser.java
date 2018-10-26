package net.client;

import net.common.Constants;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.connect.Session;
import net.lib.Socket;

public class ChangeStreamParser implements JsonStreamParser.Handler {

    private RemoteLibrary library;

    public ChangeStreamParser(RemoteLibrary library) {
        this.library = library;
    }

    @Override
    public void handle(Socket sessisocketon, JsonField field) {
        String type = field.getProperty(Constants.EVENT_TYPE_PROPERTY).getStringValue();
        System.out.println("[ChangeStreamParser][handle] Received \"" + type + "\" event");
        switch (type) {
            case Constants.EVENT_ARTIST_ADDED :
                this.library.artists.add(field.getProperty("name").getStringValue());
                System.out.format("[ChangeStreamParser][handle] New artist \"%s\" added", field.getProperty("name").getStringValue());
                break;
            case Constants.EVENT_ARTIST_REMOVED :
                this.library.artists.remove(field.getProperty("name").getStringValue());
                System.out.format("[ChangeStreamParser][handle] Artist \"%s\" removed", field.getProperty("name").getStringValue());
                break;
            case Constants.EVENT_ALBUM_ADDED :
                if (!(field.containsKey("title") && field.containsKey("artist"))) {
                    System.err.println("[ChangeStreamParser][handle] Received incomplete packet");
                    return;
                }
                RemoteAlbum newAlbum = new RemoteAlbum(this.library,
                        field.getProperty("title").getStringValue(),
                        field.getProperty("artist").getStringValue());
                this.library.albums.add(newAlbum);
                System.out.format("[ChangeStreamParser][handle] New album \"%s\" added", newAlbum.getTitle());
                break;

            case Constants.EVENT_ALBUM_REMOVED :
                if (!field.containsKey("title")) {
                    System.err.println("[ChangeStreamParser][handle] Received incomplete packet");
                    return;
                }
                String title = field.getProperty("title").getStringValue();
                this.library.albums.removeIf((album) -> album.getTitle().equals(title));
                System.out.format("[ChangeStreamParser][handle] Album \"%s\" removed", field.getProperty("title").getStringValue());
                break;

            case Constants.EVENT_SONG_ADDED :
                if (!(field.containsKey("title") && field.containsKey("artist") && field.containsKey("album") && field.containsKey("duration") && field.containsKey("id"))) {
                    System.err.println("[ChangeStreamParser][handle] Received incomplete packet");
                    return;
                }
                RemoteSong newSong = new RemoteSong(
                        this.library,
                        field.getProperty("title").getStringValue(),
                        field.getProperty("artist").getStringValue(),
                        field.getProperty("album").getStringValue(),
                        field.getProperty("duration").getLongValue(),
                        field.getProperty("id").getLongValue());
                this.library.songs.add(newSong);
                System.out.format("[ChangeStreamParser][handle] New song \"%s\" added", newSong.getTitle());
                break;

            case Constants.EVENT_SONG_REMOVED :
                if (!field.containsKey("id")) {
                    System.err.println("[ChangeStreamParser][handle] Received incomplete packet");
                    return;
                }
                long id = field.getProperty("id").getLongValue();
                this.library.songs.removeIf((song) -> song.getId() == id);
                System.out.format("[ChangeStreamParser][handle] Song %d removed", field.getProperty("id").getLongValue());
                return;

            default :
                System.err.println("[ChangeStreamParser][handler] Unrecognized event type");
        }
    }
}
