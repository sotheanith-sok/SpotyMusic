package net.client;

import connect.Album;
import connect.Library;
import connect.Song;
import javafx.collections.ObservableList;

public class RemoteAlbum implements Album {

    private final RemoteLibrary library;

    private String title;
    private String artist;

    public RemoteAlbum(RemoteLibrary library, String title, String artist) {
        this.library = library;
        this.title = title;
        this.artist = artist;
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public String getArtist() {
        return this.artist;
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.library.getSongs().filtered((song) -> song.getAlbumTitle().equals(this.title));
    }

    @Override
    public Library getLibrary() {
        return this.library;
    }
}
