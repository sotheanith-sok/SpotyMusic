package stub;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.ObservableList;
import persistence.FileImportTask;

import java.io.File;
import java.util.concurrent.Future;

public class LibraryStub implements Library {
    /**
     * Returns a {@link ObservableList} of {@link Album}s in the Library.
     *
     * @return list of Albums
     */
    @Override
    public ObservableList<? extends Album> getAlbums() {
        return null;
    }

    /**
     * Returns a {@link ObservableList} of the names of the artists of all of the songs in the Library.
     *
     * @return list of artist names
     */
    @Override
    public ObservableList<String> getArtists() {
        return null;
    }

    /**
     * Returns a {@link ObservableList} of {@link Album}s written by the artist with the given name.
     *
     * @param artist the name of an artist
     * @return list of albums by the named artist
     */
    @Override
    public ObservableList<? extends Album> getAlbumsByArtist(String artist) {
        return null;
    }

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s written by the named artist.
     *
     * @param artist name of an artist
     * @return list of songs by the named artist
     */
    @Override
    public ObservableList<? extends Song> getSongsByArtist(String artist) {
        return null;
    }

    /**
     * Returns a {@link ObservableList} containing all of the {@link Song}s in the library.
     *
     * @return list of all songs
     */
    @Override
    public ObservableList<? extends Song> getSongs() {
        return null;
    }

    /**
     * Returns a {@link ObservableList} containing all of the {@link Playlist}s in the library.
     *
     * @return list of playlists
     */
    @Override
    public ObservableList<? extends Playlist> getPlaylists() {
        return null;
    }

    /**
     * Adds a song to the library.
     *
     * @param song a File representing the song to add
     * @return Future that resolves to success
     * @throws SecurityException if the current user is not authorized to modify the library
     */
    @Override
    public Future<Boolean> importSong(File song, String title, String artist, String album) throws SecurityException {
        return null;
    }

    /**
     * Adds a song to the library.
     *
     * @param song     a File that stores the song
     * @param title    the title of the song to import
     * @param artist   the artist who wrote the song
     * @param album    the album in which the song was released
     * @param listener a progress listener
     * @return Future that resolves to success
     */
    @Override
    public Future<Boolean> importSong(File song, String title, String artist, String album, FileImportTask.FileImportProgressListener listener) {
        return null;
    }

    /**
     * Removes a song from the library.
     *
     * @param song the Song to remove from the library
     * @return Future that resolves to success
     * @throws SecurityException if the current uer is not authorized to modify the library
     */
    @Override
    public Future<Boolean> deleteSong(Song song) throws SecurityException {
        return null;
    }

    /**
     * Creates a new Playlist with the given name.
     *
     * @param name the name of the playlist to create
     * @return a Future that resolves to a boolean indicating success
     * @throws SecurityException if the current user is not authorized to modify the library
     */
    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        return null;
    }


}
