package net.server;

import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.SetChangeListener;
import net.common.Constants;
import net.common.JsonField;
import net.common.SimpleJsonWriter;
import net.connect.Session;
import persistence.LocalSong;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class ChangeSubscriptionHandler extends SimpleJsonWriter {

    private LibraryServer server;

    public ChangeSubscriptionHandler(Session session, LibraryServer server) {
        super(session);
        this.server = server;
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.gen.writeStartArray();
        this.server.songs.addListener(this::onSongChange);
        this.server.albums.addListener(this::onAlbumChange);
        this.server.artists.addListener(this::onArtistChange);
    }

    @Override
    protected void finished() {
        try {
            this.gen.writeEndArray();
        } catch (IOException e) {}
        super.finished();
        this.server.songs.removeListener(this::onSongChange);
        this.server.albums.removeListener(this::onAlbumChange);
        this.server.artists.removeListener(this::onArtistChange);
        this.que.clear();
    }

    private void onSongChange(ListChangeListener.Change<? extends LocalSong> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                List<? extends LocalSong> added = c.getAddedSubList();
                for (LocalSong song : added) {
                    JsonField event = JsonField.fromObject(new HashMap<>());
                    event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_SONG_ADDED));
                    event.setProperty("title", JsonField.fromString(song.getTitle()));
                    event.setProperty("artist", JsonField.fromString(song.getArtist()));
                    event.setProperty("album", JsonField.fromString(song.getAlbumTitle()));
                    event.setProperty("duration", JsonField.fromInt(song.getDuration()));
                    event.setProperty("id", JsonField.fromInt(song.getId()));
                    this.que(event);
                }

            } else if (c.wasRemoved()) {
                List<? extends LocalSong> removed = c.getRemoved();
                for (LocalSong song : removed) {
                    JsonField event = JsonField.fromObject(new HashMap<>());
                    event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_SONG_REMOVED));
                    event.setProperty("id", JsonField.fromInt(song.getId()));
                    this.que(event);
                }
            }
        }
    }

    private void onAlbumChange(MapChangeListener.Change<? extends String, ? extends String> c) {
        if (c.wasAdded()) {
            JsonField event = JsonField.fromObject(new HashMap<>());
            event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_ALBUM_ADDED));
            event.setProperty("title", JsonField.fromString(c.getKey()));
            event.setProperty("artist", JsonField.fromString(c.getValueAdded()));
            this.que(event);

        } else {
            JsonField event = JsonField.fromObject(new HashMap<>());
            event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_ALBUM_REMOVED));
            event.setProperty("title", JsonField.fromString(c.getValueRemoved()));
            this.que(event);
        }
    }

    private void onArtistChange(SetChangeListener.Change<? extends String> c) {
        if (c.wasAdded()) {
            JsonField event = JsonField.fromObject(new HashMap<>());
            event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_ARTIST_ADDED));
            event.setProperty("name", JsonField.fromString(c.getElementAdded()));
            this.que(event);

        } else {
            JsonField event = JsonField.fromObject(new HashMap<>());
            event.setProperty(Constants.EVENT_TYPE_PROPERTY, JsonField.fromString(Constants.EVENT_ARTIST_REMOVED));
            event.setProperty("name", JsonField.fromString(c.getElementRemoved()));
            this.que(event);
        }
    }
}
