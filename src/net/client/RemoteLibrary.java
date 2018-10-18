package net.client;

import connect.Album;
import connect.Library;
import connect.Playlist;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.common.Constants;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.common.JsonStreamParser.Handler;
import net.common.SimpleJsonWriter;
import net.connect.SessionPacket;
import net.lib.ClientSocket;
import net.lib.Socket;
import utils.CompletableTaskExecutor;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.concurrent.Future;

public class RemoteLibrary implements Library {

    private InetAddress address;
    private int port;

    protected CompletableTaskExecutor taskManager;

    protected ObservableList<RemoteSong> songs;
    protected ObservableList<RemoteAlbum> albums;
    protected ObservableList<String> artists;

    public RemoteLibrary(InetAddress address, int port) {
        this.address = address;
        this.port = port;

        this.taskManager = new CompletableTaskExecutor(Runtime.getRuntime().availableProcessors(), 10);

        this.songs = FXCollections.observableList(new LinkedList<>());
        this.albums = FXCollections.observableList(new LinkedList<>());
        this.artists = FXCollections.observableList(new LinkedList<>());

        System.out.println("[RemoteLibrary] RemoteLibrary instantiated");
    }

    public void connect() throws SocketException, SocketTimeoutException {
        // send request to get all artists
        Socket socket = this.getConnection();
        SimpleJsonWriter request = new SimpleJsonWriter(socket, false);
        JsonField.ObjectField packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_ARTISTS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(socket, true, (soc, art) -> {
            if (art.isString()) {
                this.artists.add(art.getStringValue());
                System.out.println("[RemoteLibrary][artistParseHandler] New artist: " + art.getStringValue());
            }
        }, true));

        // send request to get all albums
        socket = this.getConnection();
        //socket.debug = true;
        request = new SimpleJsonWriter(socket, false);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_ALBUMS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(socket, true, (sess, alb) -> {
            if (alb.isObject() && alb.containsKey("title") && alb.containsKey("artist")){
                this.albums.add(new RemoteAlbum(this, alb.getProperty("title").getStringValue(), alb.getProperty("artist").getStringValue()));
                System.out.println("[RemoteLibrary][albumParseHandler] New album: " + alb.getProperty("title").getStringValue());
            }

        }, true));

        // send request to get all songs
        socket = this.getConnection();
        //socket.debug = true;
        request = new SimpleJsonWriter(socket, false);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_SONGS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(socket, true, (sess, song) -> {
            if (song.isObject()) {
                if (song.containsKey("title") &&
                    song.containsKey("artist") &&
                    song.containsKey("album") &&
                    song.containsKey("duration") &&
                    song.containsKey("id")) {
                    this.songs.add(new RemoteSong(this,
                            song.getProperty("title").getStringValue(),
                            song.getProperty("artist").getStringValue(),
                            song.getProperty("album").getStringValue(),
                            song.getProperty("duration").getLongValue(),
                            song.getProperty("id").getLongValue()));
                    System.out.println("[RemoteLibrary][songParseHandler] New song added to library: " + song.getProperty("title").getStringValue());

                } else {
                    System.err.println("[RemoteLibrary][SongParseHandler] Received incomplete song data");
                }
            }

        }, true));

        socket = this.getConnection();
        //socket.debug = true;
        request = new SimpleJsonWriter(socket, false);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_SUBSCRIBE);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(socket, true, new ChangeStreamParser(this)));
        //socket.addDisconnectListener(() -> System.out.println("[RemoteLibrary] Change subscription disconnected"));

        /*
        socket = this.getConnection();
        request = new SimpleJsonWriter(socket, false);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, "test-file");
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new TestFileDownloader(socket));
*/
    }

    public void disconnect() {
        this.taskManager.shutdown();
    }

    protected Socket getConnection() throws SocketException, SocketTimeoutException {
        ClientSocket socket = new ClientSocket(this.address, this.port);
        socket.connect();
        return socket;
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
        return this.albums.filtered((album) -> album.getArtist().equals(artist));
    }

    @Override
    public ObservableList<? extends Song> getSongsByArtist(String artist) {
        return this.songs.filtered((song) -> song.getArtist().equals(artist));
    }

    @Override
    public ObservableList<? extends Song> getSongs() {
        return this.songs;
    }

    @Override
    public ObservableList<? extends Playlist> getPlaylists() {
        return null;
    }

    @Override
    public Future<Boolean> createPlaylist(String name) throws SecurityException {
        return null;
    }

    private void noop(SessionPacket packet) {
        // don't accept connections
    }
}
