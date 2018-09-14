package persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import utils.ObservableListImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Implementation of {@link Playlist} for use with {@link LocalLibrary} instances.
 */
public class LocalPlaylist implements Playlist, ListChangeListener<LocalSong> {

    private String name;
    private LocalLibrary lib;

    private ObservableList<LocalSong> songs;

    /**
     * Creates a new LocalPlaylist with the given name and song list, associated with the given {@link LocalLibrary}.
     *
     * @param name the name of the playlist
     * @param songs the songs in the playlsit
     * @param lib the library that the playlist is part of
     */
    public LocalPlaylist(String name, List<LocalSong> songs, LocalLibrary lib) {
        this.name = name;
        this.lib = lib;
        this.songs = new ObservableListImpl<>(songs);
        lib.songs.addListener(this);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.songs;
    }

    @Override
    public Library getLibrary() {
        return this.lib;
    }

    @Override
    public Future<Boolean> addSong(Song song) throws SecurityException {
        return null;
    }

    @Override
    public Future<Boolean> removeSong(Song song) throws SecurityException {
        return null;
    }

    @Override
    public void onChanged(Change<? extends LocalSong> c) {
        if (c.wasRemoved()) {
            this.songs.removeAll(c.getRemoved());
        }
    }

    /**
     * Writes the contents of the playlist to the given {@link JsonGenerator}.
     * Used internally by {@link LocalLibrary#saveLibrary(JsonGenerator)}.
     *
     * @param gen a JsonGenerator to use as output
     * @throws IOException if an IOException occurs while writing
     */
    public void savePlaylist(JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("name", this.name);
        gen.writeArrayFieldStart("songs");
        for (LocalSong s : this.songs) {
            gen.writeNumber(s.getId());
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
