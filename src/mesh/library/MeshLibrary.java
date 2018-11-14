package mesh.library;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import mesh.dfs.DFS;
import mesh.impl.MeshNode;
import net.common.JsonField;
import net.reqres.Socketplexer;
import utils.Utils;

import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.*;

public class MeshLibrary implements Library {

    protected MeshNode mesh;

    protected DFS dfs;

    protected ScheduledExecutorService executor;

    protected MeshClientUser user;

    protected ObservableList<MeshClientSong> songs;

    protected ObservableList<String> artists;

    protected ObservableList<MeshClientAlbum> albums;

    public MeshLibrary(MeshNode mesh, DFS dfs, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.dfs = dfs;
        this.executor = executor;

        this.songs = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));
        this.artists = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));
        this.albums = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));
    }

    public void init() {
        this.mesh.registerRequestHandler(REQUEST_SEARCH_MESH, this::handleSearchMesh);
    }

    private void handleSearchMesh(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        executor.submit(new MeshSearchRequestHandler(socketplexer, request, this));
    }

    public Future<Boolean> tryAuth(String user, String password) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Future<MeshClientUser> fuser = MeshClientUser.tryAuth(user, password, this);
            try {
                this.user = fuser.get(10, TimeUnit.SECONDS);
                future.complete(true);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                future.complete(false);
            }
        });

        return future;
    }

    public Future<Boolean> register(String user, String password) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Future<MeshClientUser> fuser = MeshClientUser.register(user, password, this);

            try {
                this.user = fuser.get(6, TimeUnit.SECONDS);
                future.complete(true);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                future.complete(false);
            }
        });

        return future;
    }

    public void doSearch(String searchParam) {
        this.executor.submit(new SearchHandler(searchParam, this));
    }

    public void importSong(File file, String title, String artist, String album) {
        this.executor.submit(new ImportSongHandler(file, title, artist, album, this));
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
        if (this.user != null) this.user.onArtistAccessed(artist);
        return this.albums.filtered((album) -> album.getArtist().equals(artist));
    }

    @Override
    public ObservableList<? extends Song> getSongsByArtist(String artist) {
        if (this.user != null) this.user.onArtistAccessed(artist);
        return this.songs.filtered((song) -> song.getArtist().equals(artist));
    }

    public ObservableList<? extends Song> getSongsByAlbum(MeshClientAlbum album) {
        if (this.user != null) this.user.onAlbumAccessed(album);
        return this.songs.filtered((song) -> song.getAlbumTitle().equals(album.getTitle()));
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.songs;
    }

    @Override
    public ObservableList<? extends Playlist> getPlaylists() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public ObservableList<? extends Song> search(String searchParam) {
        String param = searchParam.toLowerCase().trim();
        // TODO: send search query to mesh
        if (this.user != null) this.user.onSearch(searchParam);
        return this.songs.filtered((song) -> song.getTitle().toLowerCase().contains(param) ||
                                                song.getAlbumTitle().toLowerCase().contains(param) ||
                                                song.getArtist().toLowerCase().contains(param));
    }

    protected void onSongPlayed(MeshClientSong song) {
        if (this.user != null) this.user.onSongPlayed(song);
    }

    public static final String REQUEST_IMPORT_SONG = "REQUEST_IMPORT_SONG";
    public static final String RESPONSE_IMPORT_SONG = "RESPONSE_IMPORT_SONG";

    public static final String REQUEST_STREAM_SONG = "REQUEST_STREAM_SONG";
    public static final String RESPONSE_STREAM_SONG = "RESPONSE_STREAM_SONG";

    public static final String REQUEST_SEARCH_MESH = "REQUEST_SEARCH_MESH";
    public static final String RESPONSE_SEARCH_MESH = "RESPONSE_SEARCH_MESH";
    public static final String REQUEST_SEARCH_SONG = "REQUEST_SEARCH_SONG";
    public static final String RESPONSE_SEARCH_SONG = "RESPONSE_SEARCH_SONG";
    public static final String PROPERTY_SEARCH_PARAMETER = "PROP_SEARCH_PARAM";

    public static final String PROPERTY_SONG_TITLE = "PROP_SONG_TITLE";
    public static final String PROPERTY_SONG_ARTIST = "PROP_SONG_ARTIST";
    public static final String PROPERTY_SONG_ALBUM = "PROP_SONG_ALBUM";
    public static final String PROPERTY_SONG_DURATION = "PROP_SONG_DURATION";
    public static final String PROPERTY_SONG_FILE_NAME = "PROP_SONG_FILE_NAME";

    public static final String INDEX_FILE_NAME = Utils.hash("MusicIndexFile", "MD5");
    public static final int INDEX_FILE_REPLICAS = 3;
    public static final int SONG_FILE_REPLICAS = 1;

}
