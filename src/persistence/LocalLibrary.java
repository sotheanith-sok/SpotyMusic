package persistence;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

public class LocalLibrary implements Library {


    @Override
    public ObservableList<Album> getAlbums() {
        return null;
    }

    @Override
    public ObservableList<String> getArtists() {
        return null;
    }

    @Override
    public ObservableList<Album> getAlbumsByArtist(String artist) {
        return null;
    }

    @Override
    public ObservableList<Song> getSongsByArtist(String artist) {
        return null;
    }

    @Override
    public ObservableList<Song> getSongs() {
        return null;
    }

    @Override
    public ObservableList<Playlist> getPlaylists() {
        return null;
    }

    @Override
    public Future<Boolean> importSong(File song) throws SecurityException {
        return null;
    }

    @Override
    public Future<Boolean> deleteSong(Song song) throws SecurityException {
        return null;
    }

    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        return null;
    }
}
