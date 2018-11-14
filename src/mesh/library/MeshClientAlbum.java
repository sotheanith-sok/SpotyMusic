package mesh.library;

import connect.Album;
import connect.Library;
import connect.Song;
import javafx.collections.ObservableList;

public class MeshClientAlbum implements Album {

    private final String title;

    private final String artist;

    private MeshLibrary library;

    public MeshClientAlbum(String title, String artist, MeshLibrary library) {
        this.title = title;
        this.artist = artist;
        this.library = library;
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
        return this.library.getSongsByAlbum(this);
    }

    @Override
    public Library getLibrary() {
        return this.library;
    }
}
