package mesh.library;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import mesh.dfs.DFS;
import mesh.impl.MeshNode;
import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.*;
import net.lib.Socket;
import net.reqres.Socketplexer;
import utils.DebouncedRunnable;
import utils.Logger;
import utils.Utils;

import java.io.*;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshLibrary implements Library {

    protected MeshNode mesh;

    protected DFS dfs;

    protected ScheduledExecutorService executor;

    private Logger logger;

    protected MeshClientUser user;

    private final Object libraryLock;

    protected ObservableList<MeshClientSong> songs;

    protected ObservableList<String> artists;

    protected ObservableList<MeshClientAlbum> albums;

    private DebouncedRunnable doSortTask;

    protected DebouncedRunnable doSaveTask;

    private AtomicBoolean isSorting;

    protected ConcurrentHashMap<String, String[]> index;

    public MeshLibrary(MeshNode mesh, DFS dfs, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.dfs = dfs;
        this.executor = executor;
        this.libraryLock = new Object();

        this.logger = new Logger("MeshLibrary", Constants.DEBUG);

        this.index = new ConcurrentHashMap<String, String[]>();

        this.songs = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));
        this.artists = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));
        this.albums = FXCollections.synchronizedObservableList(FXCollections.observableList(new LinkedList<>()));

        this.doSaveTask = new DebouncedRunnable(this::doSave, 1000, TimeUnit.MILLISECONDS, true, this.executor);
        this.doSortTask = new DebouncedRunnable(this::doSortIfMaster, 750, TimeUnit.MILLISECONDS, true, this.executor);
        this.isSorting = new AtomicBoolean(false);
    }

    public void init() {
        this.mesh.registerRequestHandler(REQUEST_SEARCH_MESH, this::handleSearchMesh);
        this.mesh.registerRequestHandler(REQUEST_SORT_STATUS, this::sortStatusHandler);
        this.mesh.registerRequestHandler(REQUEST_SORT_EMIT, this::sortEmitHandler);
        this.mesh.registerPacketHandler(REQUEST_DO_SORT, (packet, source) -> {
            if (!this.isSorting.get()) this.doSortTask.run();
        });
        this.mesh.addNodeConnectListener((id) -> this.doSortTask.run());

        try {
            if (!INDEX_FILE.exists()) INDEX_FILE.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.executor.submit(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(INDEX_FILE)));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split(";");
                    this.index.put(fields[4], fields);
                }

            } catch (IOException e) {
                this.logger.error("[init] IOException while reading local auxiliary index file");
                e.printStackTrace();
            }
        });
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

    public Task<Song> importSong(File file, String title, String artist, String album) {
        ImportSongHandler task = new ImportSongHandler(file, title, artist, album, this);
        this.executor.submit(task);
        return task;
    }

    public void addSong(MeshClientSong song) {
        synchronized (this.libraryLock) {
            // check if song is already in library
            for (MeshClientSong song1 : this.songs) if (song1.getGUID().equals(song.getGUID())) return;
            // if song not already known, add to library
            this.songs.add(song);
            System.out.println("[MeshLibrary][addSong] Song \"" + song.getTitle() + "\" added to library");
            if (this.user != null) this.user.onSongAdded(song);

            // check if album is already in library
            for (MeshClientAlbum album : this.albums) if (album.getTitle().equals(song.getAlbumTitle())) return;
            // if new album, add to library
            MeshClientAlbum album = new MeshClientAlbum(song.getAlbumTitle(), song.getArtist(), this);
            this.albums.add(album);
            System.out.println("[MeshLibrary][addSong] Album \"" + album.getTitle() + "\" added to library");
            if (this.user != null) this.user.onAlbumAdded(album);

            // check if new artist
            for (String artist : this.artists) if (artist.equals(song.getArtist())) return;
            // if new artist, add to library
            this.artists.add(song.getArtist());
            System.out.println("[MeshLibrary][addSong] Artist \"" + song.getArtist() + "\" added to library");
            if (this.user != null) this.user.onArtistAdded(song.getArtist());
        }
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

    public MeshClientAlbum getAlbumByTitle(String title) {
        for (MeshClientAlbum album : this.albums) {
            if (album.getTitle().equals(title)) return album;
        }

        return null;
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
        if (this.user != null) {
            return this.user.getPlaylists();
        }

        return FXCollections.emptyObservableList();
    }

    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        if (this.user != null) {
            this.user.createPlaylist(name);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public ObservableList<? extends Song> search(String searchParam) {
        String param = searchParam.toLowerCase().trim();
        this.doSearch(searchParam);
        if (this.user != null) this.user.onSearch(searchParam);
        return this.songs.filtered((song) -> song.getTitle().toLowerCase().contains(param) ||
                                                song.getAlbumTitle().toLowerCase().contains(param) ||
                                                song.getArtist().toLowerCase().contains(param));
    }

    protected void onSongPlayed(MeshClientSong song) {
        if (this.user != null) this.user.onSongPlayed(song);
    }

    public MeshClientUser getCurrentUser() {
        return this.user;
    }

    public void signOut() {
        this.user = null;
        this.songs.clear();
        this.albums.clear();
        this.artists.clear();
    }

    private void doSortIfMaster() {
        if (this.mesh.getAvailableNodes().size() < 2) return;
        if (this.isSorting.get()) return;

        if (this.mesh.isMaster()) {
            // see if network is already sorting
            this.logger.fine("[doSortIfMaster] Getting network sort status");
            Future<Boolean> statusFuture= this.getSortStatus();
            boolean status = false;
            try {
                status = statusFuture.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);
                this.logger.finer("[doSortIfMaster] Network sort status is " + status);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                this.logger.warn("[doSortIfMaster] Unable to obtain network sorting status");
                e.printStackTrace();
                return;
            }

            if (status) return;

            // begin sort operation if not sorting
            this.mesh.broadcastPacket((gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_DO_SORT);
                gen.writeEndObject();
            });

            this.doSort();
        }
    }

    private void sortStatusHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.logger.log("[sortStatusHandler] Request for sort status");
        try {
            (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_SORT_STATUS);
                gen.writeBooleanField(PROPERTY_SORT_STATUS, this.isSorting.get());
                gen.writeEndObject();
            })).run();
            this.logger.log("[sortStatusHandler] Request handled");

        } catch (IOException e) {
            this.logger.warn("[sortStatusHandler] Unable to obtain response stream");
            e.printStackTrace();
        }
    }

    private void doSort() {
        this.isSorting.set(true);

        HashMap<Integer, Socketplexer> connections = new HashMap<>();
        HashMap<Integer, AsyncJsonStreamGenerator> generators = new HashMap<>();

        int localId = this.mesh.getNodeId();

        for (Map.Entry<String, String[]> entry : Collections.unmodifiableSet(this.index.entrySet())) {
            int remoteId;
            if ((remoteId = this.mesh.getBestId(entry.getKey().hashCode())) != localId) {
                AsyncJsonStreamGenerator generator;

                if (generators.containsKey(remoteId)) {
                    generator = generators.get(remoteId);

                } else {
                    Socket socket;

                    try {
                        socket = this.mesh.tryConnect(remoteId);

                    } catch (NodeUnavailableException | SocketException | SocketTimeoutException e) {
                        this.logger.warn("[doSort] Unable to connect to node " + remoteId);
                        e.printStackTrace();
                        continue;
                    }

                    Socketplexer socketplexer = new Socketplexer(socket, this.executor);

                    try {
                        (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                            gen.writeStartObject();
                            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_SORT_EMIT);
                            gen.writeEndObject();
                        })).run();

                    } catch (IOException e) {
                        this.logger.warn("[doSort] Unable to open request header channel");
                        e.printStackTrace();
                    }

                    AtomicBoolean ok = new AtomicBoolean(false);

                    try {
                        (new JsonStreamParser(socketplexer.waitInputChannel(1).get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS), false, (field) -> {
                            if (!field.isObject()) return;
                            JsonField.ObjectField header = (JsonField.ObjectField) field;

                            if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                                ok.set(true);
                            }
                        })).run();

                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        this.logger.warn("[doSort] Unable to open response header channel");
                        e.printStackTrace();
                    }

                    if (!ok.get()) continue;

                    OutputStream requestBody;

                    try {
                        requestBody = socketplexer.openOutputChannel(2);

                    } catch (IOException e) {
                        this.logger.warn("[doSort] Unable to open request body channel");
                        e.printStackTrace();
                        continue;
                    }

                    connections.put(remoteId, socketplexer);
                    generator = new AsyncJsonStreamGenerator(requestBody);
                    generator.enqueue((gen) -> gen.writeStartArray());
                    generators.put(remoteId, generator);
                }

                String[] fields = entry.getValue();
                generator.enqueue((gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(MeshLibrary.PROPERTY_SONG_ARTIST, fields[0]);
                    gen.writeStringField(MeshLibrary.PROPERTY_SONG_ALBUM, fields[1]);
                    gen.writeStringField(MeshLibrary.PROPERTY_SONG_TITLE, fields[2]);
                    gen.writeStringField(MeshLibrary.PROPERTY_SONG_DURATION, fields[3]);
                    gen.writeStringField(MeshLibrary.PROPERTY_SONG_FILE_NAME, fields[4]);
                    gen.writeEndObject();
                });

                this.index.remove(entry.getKey());
            }
        }

        this.doSaveTask.run();

        for (AsyncJsonStreamGenerator generator : generators.values()) {
            generator.enqueue((gen) -> gen.writeEndArray());
            generator.close();
        }

        for (Socketplexer socketplexer : connections.values()) {
            socketplexer.terminate();
        }
    }

    private void doSave() {
        this.logger.log("[doSave] Saving auxiliary index file...");
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(INDEX_FILE));

            for (String[] entry : this.index.values()) {
                for (int i = 0; i < entry.length; i++) {
                    writer.write(entry[i]);
                    if (i + 1 < entry.length) writer.write(';');
                }
                writer.newLine();
            }

            writer.close();

        } catch (IOException e) {
            this.logger.warn("[doSave] IOException while writing auxiliary index file");
            e.printStackTrace();
            return;
        }

        this.logger.debug("[doSave] Saved auxiliary index file");
    }

    private void sortEmitHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executorService) {
        if (!request.isObject()) return;

        OutputStream responseHeader;

        try {
            responseHeader = socketplexer.openOutputChannel(1);

        } catch (IOException e) {
            this.logger.warn("[sortEmitHandler] Unable to open response header channel");
            e.printStackTrace();
            socketplexer.terminate();
            return;
        }

        if (this.isSorting.get()) {
            (new DeferredStreamJsonGenerator(responseHeader, true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_SORT_EMIT);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                gen.writeEndObject();
            })).run();

        } else {
            (new DeferredStreamJsonGenerator(responseHeader, true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_SORT_EMIT);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, "NOT_SORTING");
                gen.writeEndObject();
            })).run();
            socketplexer.terminate();
            return;
        }

        InputStream bodyStream;

        try {
            bodyStream = socketplexer.waitInputChannel(2).get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            this.logger.warn("[sortEmitHandler] Unable to open emit channel");
            e.printStackTrace();
            socketplexer.terminate();
            return;
        }

        JsonStreamParser parser = new JsonStreamParser(bodyStream, true, (field) -> {
            JsonField.ObjectField song = (JsonField.ObjectField) field;

            //System.out.println("[SearchHandler][parse] Getting title");
            String title = song.getStringProperty(MeshLibrary.PROPERTY_SONG_TITLE);
            //System.out.println("[SearchHandler][parse] Getting artist");
            String artist = song.getStringProperty(MeshLibrary.PROPERTY_SONG_ARTIST);
            //System.out.println("[SearchHandler][parse] Getting album");
            String album = song.getStringProperty(MeshLibrary.PROPERTY_SONG_ALBUM);
            //System.out.println("[SearchHandler][parse] Getting duration");
            String duration = song.getStringProperty(MeshLibrary.PROPERTY_SONG_DURATION);
            //System.out.println("[SearchHandler][parse] Getting file name");
            String file = song.getStringProperty(MeshLibrary.PROPERTY_SONG_FILE_NAME);
            String[] entry = new String[]{artist, album, title, duration, file};
            if (this.index.putIfAbsent(file, entry) == null) this.doSaveTask.run();

        }, true);
    }

    public Future<Boolean> getSortStatus() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Socket connection;

            try {
                connection = this.mesh.tryConnect(this.mesh.getNextNode());

            } catch (SocketException | SocketTimeoutException | NodeUnavailableException e) {
                this.logger.warn("[getSortStatus] Unable to connect to node " + this.mesh.getNextNode());
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            Socketplexer socketplexer = new Socketplexer(connection, this.executor);

            this.logger.debug("[getSortStatus] Sending request headers");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_SORT_STATUS);
                    gen.writeEndObject();
                })).run();
                this.logger.trace("[getSortStatus] Request headers sent");

            } catch (IOException e) {
                this.logger.warn("[getSortStatus] Unable to open request header stream");
                e.printStackTrace();
                future.completeExceptionally(e);
                socketplexer.terminate();
                return;
            }

            this.logger.debug("[getSortStatus] Parsing response headers");
            try {
                (new JsonStreamParser(socketplexer.waitInputChannel(1).get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS), true, (field) -> {
                    if (field.isObject()) return;
                    JsonField.ObjectField headers = (JsonField.ObjectField) field;
                    boolean status = headers.getBooleanProperty(PROPERTY_SORT_STATUS);
                    future.complete(status);
                    this.logger.fine("[getSortStatus] Network sort status = " + status);
                })).run();
                this.logger.trace("[getSortStatus] Parsed response headers");

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                this.logger.warn("[getSortStatus] Unable to obtain response header stream");
                e.printStackTrace();
            }

            socketplexer.terminate();
        });

        return future;
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

    public static final String REQUEST_SORT_STATUS = "REQUEST_SORT_STATUS";
    public static final String RESPONSE_SORT_STATUS = "RESPONSE_SORT_STATUS";
    public static final String PROPERTY_SORT_STATUS = "PROP_SORT_STATUS";

    public static final String REQUEST_DO_SORT = "REQUEST_DO_SORT";
    public static final String REQUEST_SORT_EMIT = "REQUEST_SORT_EMIT";
    public static final String RESPONSE_SORT_EMIT = "RESPONSE_SORT_EMIT";

    public static final String PROPERTY_SONG_TITLE = "PROP_SONG_TITLE";
    public static final String PROPERTY_SONG_ARTIST = "PROP_SONG_ARTIST";
    public static final String PROPERTY_SONG_ALBUM = "PROP_SONG_ALBUM";
    public static final String PROPERTY_SONG_DURATION = "PROP_SONG_DURATION";
    public static final String PROPERTY_SONG_FILE_NAME = "PROP_SONG_FILE_NAME";

    public static final String INDEX_FILE_NAME = Utils.hash("MusicIndexFile", "MD5");
    public static final File INDEX_FILE = new File("SpotyMusic/songs.txt");
    public static final int INDEX_FILE_REPLICAS = 3;
    public static final int SONG_FILE_REPLICAS = 1;

}
