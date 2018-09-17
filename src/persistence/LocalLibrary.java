package persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ObservableList;
import utils.ObservableListImpl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * An implementation of {@link Library} for libraries located on the local system.
 */
public class LocalLibrary implements Library {

    protected ObservableList<LocalSong> songs;
    private ObservableList<LocalAlbum> albums;
    private Map<String, LocalAlbum> album_map;
    private ObservableList<String> artists;
    private ObservableList<LocalPlaylist> playlists;

    private HashMap<String, ObservableList<LocalSong>> artistSongs;
    private HashMap<String, ObservableList<LocalAlbum>> artistAlbums;

    /**
     * Creates a new LocalLibrary with the given songs and playlists.
     *
     * @param songs songs in the library
     * @param playlists playlists in the library
     */
    public LocalLibrary(List<LocalSong> songs, Map<String, List<LocalSong>> playlists) {
        this.songs = new ObservableListImpl<>(songs);
        this.artists = new ObservableListImpl<>();
        this.playlists = new ObservableListImpl<>();
        HashMap<String, LocalAlbum> albums = new HashMap<>();

        for (LocalSong s : this.songs) {
            // assign LocalSong instances to this library
            s.setLibrary(this);
            // build list of albums
            if (!albums.containsKey(s.getAlbumTitle())) albums.put(s.getAlbumTitle(), new LocalAlbum(s.getAlbumTitle(), s.getArtist(), this));
            // build list of artists
            if (!this.artists.contains(s.getArtist())) this.artists.add(s.getArtist());
        }
        this.albums = new ObservableListImpl<>(albums.values());
        this.album_map = albums;

        // build playlists and add them to the library
        for (Map.Entry<String, List<LocalSong>> e : playlists.entrySet()) {
            this.playlists.add(new LocalPlaylist(e.getKey(), e.getValue(), this));
        }

        // these are used to cache the FilteredLists that back *byArtist retrieval
        this.artistSongs = new HashMap<>();
        this.artistAlbums = new HashMap<>();

        System.out.println("[LocalLibrary] LocalLibrary created");
        System.out.print('\t');
        System.out.print(this.songs.size());
        System.out.println(" songs");
        System.out.print('\t');
        System.out.print(this.albums.size());
        System.out.println(" albums");
        System.out.print('\t');
        System.out.print(this.artists.size());
        System.out.println(" artists");
        System.out.print('\t');
        System.out.print(this.playlists.size());
        System.out.println(" playlists");
    }

    protected void addSong(LocalSong song) {
        this.songs.add(song);
        song.setLibrary(this);
        if (!this.album_map.containsKey(song.getAlbumTitle())) {
            LocalAlbum newAlbum = new LocalAlbum(song.getAlbumTitle(), song.getArtist(), this);
            this.album_map.put(newAlbum.getTitle(), newAlbum);
            this.albums.add(newAlbum);
        }
        if (!this.artists.contains(song.getArtist())) {
            this.artists.add(song.getArtist());
        }
        DataManager.getDataManager().saveLibrary();
    }

    @Override
    public ObservableList<? extends Album> getAlbums() {
        return this.albums;
    }

    @Override
    public ObservableList<String> getArtists() {
        return this.artists;
    }

    @Override
    public ObservableList<? extends Album> getAlbumsByArtist(String artist) {
        if (this.artistAlbums.containsKey(artist)) {
            return this.artistAlbums.get(artist);

        } else {
            ObservableList<LocalAlbum> artistAlbums = this.albums.filtered(album -> album.getArtist().equals(artist));
            this.artistAlbums.put(artist, artistAlbums);
            return artistAlbums;
        }
    }

    @Override
    public ObservableList<? extends Song> getSongsByArtist(String artist) {
        if (this.artistSongs.containsKey(artist)) {
            return this.artistSongs.get(artist);

        } else {
            ObservableList<LocalSong> artistSongs = this.songs.filtered(song -> song.getArtist().equals(artist));
            this.artistSongs.put(artist, artistSongs);
            return artistSongs;
        }
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.songs;
    }

    @Override
    public ObservableList<? extends Playlist> getPlaylists() {
        return this.playlists;
    }

    @Override
    public Future<Boolean> importSong(File song) throws SecurityException {
        return DataManager.getDataManager().importFile(song);
    }

    @Override
    public Future<Boolean> deleteSong(Song song) throws SecurityException {
        this.songs.remove(song);
        return DataManager.getDataManager().saveLibrary();
    }

    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        this.playlists.add(new LocalPlaylist(name, new LinkedList<>(), this));
        return DataManager.getDataManager().saveLibrary();
    }

    /**
     * Writes the contents of this library to the gievn {@link JsonGenerator}.
     * Used internally by {@link persistence.writers.LibraryWriter}.
     *
     * @param gen a JsonGenerator to use as output
     * @throws IOException if there is an exception while writing
     */
    public void saveLibrary(JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeArrayFieldStart("songs");
        for (LocalSong s : this.songs) {
            gen.writeNumber(s.getId());
        }
        gen.writeEndArray();
        gen.writeArrayFieldStart("playlists");
        for (LocalPlaylist pl : this.playlists) {
            pl.savePlaylist(gen);
        }
        gen.writeEndArray();

        gen.writeEndObject();
    }
}
