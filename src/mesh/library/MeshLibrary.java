package mesh.library;

import mesh.dfs.DFS;
import mesh.impl.MeshNode;
import net.common.JsonField;
import net.reqres.Socketplexer;
import utils.Utils;

import java.util.concurrent.*;

public class MeshLibrary {

    protected MeshNode mesh;

    protected DFS dfs;

    protected ScheduledExecutorService executor;

    public MeshLibrary(MeshNode mesh, DFS dfs, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.dfs = dfs;
        this.executor = executor;
    }

    public void init() {
        this.mesh.registerRequestHandler(REQUEST_IMPORT_SONG, this::handleImportSong);
        this.mesh.registerRequestHandler(REQUEST_STREAM_SONG, this::handleStreamSong);
        this.mesh.registerRequestHandler(REQUEST_SEARCH_MESH, this::handleSearchMesh);
        this.mesh.registerRequestHandler(REQUEST_SEARCH_SONG, this::handleSearchSong);
    }

    private void handleImportSong(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        executor.submit(new ImportSongRequestHandler(socketplexer, request, this));
    }

    private void handleStreamSong(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        executor.submit(new StreamSongRequestHandler(socketplexer, request, this));
    }

    private void handleSearchMesh(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        executor.submit(new MeshSearchRequestHandler(socketplexer, request, this));
    }

    private void handleSearchSong(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        executor.submit(new SearchSongRequestHandler(socketplexer, request, this));
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
