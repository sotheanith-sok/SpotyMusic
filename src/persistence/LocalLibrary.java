package persistence;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;

import java.io.File;
import java.util.List;

public class LocalLibrary implements Library {


    @Override
    public List<Album> getAlbums() {
        return null;
    }

    @Override
    public List<String> getArtists() {
        return null;
    }

    @Override
    public List<Album> getAlbumsByArtist(String artist) {
        return null;
    }

    @Override
    public List<Song> getSongsByArtist(String artist) {
        return null;
    }

    @Override
    public List<Song> getSongs() {
        return null;
    }

    @Override
    public List<Playlist> getPlaylists() {
        return null;
    }

    @Override
    public void importSong(File song) throws SecurityException {

    }

    @Override
    public void deleteSong(Song song) throws SecurityException {

    }
}
