package mesh.library;

public interface MeshLibraryActivityListener {
    void onSearch(String param);

    void onSongAdded(MeshClientSong song);

    void onAlbumAdded(MeshClientAlbum album);

    void onArtistAdded(String artist);

    void onAlbumAccessed(MeshClientAlbum album);

    void onArtistAccessed(String artist);

    void onSongPlayed(MeshClientSong song);

    void onSongImported(MeshClientSong song);
}
