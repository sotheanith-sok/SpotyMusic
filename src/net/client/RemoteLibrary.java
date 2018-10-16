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
import net.common.SimpleJsonWriter;
import net.connect.Session;
import net.connect.SessionPacket;
import net.connect.SessionedSocket;
import utils.CompletableTaskExecutor;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.Future;

public class RemoteLibrary implements Library {

    private InetAddress address;
    private int port;

    private SessionedSocket socket;

    protected CompletableTaskExecutor taskManager;

    protected ObservableList<RemoteSong> songs;
    protected ObservableList<RemoteAlbum> albums;
    protected ObservableList<String> artists;

    public RemoteLibrary(InetAddress address, int port) {
        this.address = address;
        this.port = port;

        this.socket = new SessionedSocket(12322, this::noop);

        this.taskManager = new CompletableTaskExecutor(Runtime.getRuntime().availableProcessors(), 10);

        this.songs = FXCollections.observableList(new LinkedList<>());
        this.albums = FXCollections.observableList(new LinkedList<>());
        this.artists = FXCollections.observableList(new LinkedList<>());

        try {
            this.socket.init();

        } catch (SocketException e) {
            System.err.println("[RemoteLibrary] SocketException while opening SessionedSocket");
            e.printStackTrace();
        }

        System.out.println("[RemoteLibrary] RemoteLibrary instantiated");
    }

    public void connect() {
        // send request to get all artists
        Session session = this.getSession();
        SimpleJsonWriter request = new SimpleJsonWriter(session);
        JsonField.ObjectField packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_ARTISTS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(session, (sess, art) -> {
            if (art.isString()) this.artists.add(art.getStringValue());
        }, true));

        // send request to get all albums
        session = this.getSession();
        request = new SimpleJsonWriter(session);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_ALBUMS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(session, (sess, alb) -> {
            if (alb.isObject() && alb.containsKey("title") && alb.containsKey("artist")){
                this.albums.add(new RemoteAlbum(this, alb.getProperty("title").getStringValue(), alb.getProperty("artist").getStringValue()));
                System.out.println("[RemoteLibrary][albumParseHandler] New album: " + alb.getProperty("title").getStringValue());
            }

        }, true));

        // send request to get all songs
        session = this.getSession();
        request = new SimpleJsonWriter(session);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_LIST_SONGS);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(session, (sess, song) -> {
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

        session = this.getSession();
        //session.debug = true;
        request = new SimpleJsonWriter(session);
        packet = JsonField.emptyObject();
        packet.setProperty(Constants.REQUEST_TYPE_PROPERTY, Constants.REQUEST_SUBSCRIBE);
        request.que(packet);
        request.complete();
        this.taskManager.submit(request);
        this.taskManager.submit(new JsonStreamParser(session, new ChangeStreamParser(this)));
        session.addDisconnectListener(() -> System.out.println("[RemoteLibrary] Change subscription disconnected"));

    }

    public void disconnect() {
        this.socket.shutdown();
        this.taskManager.shutdown();
    }

    protected Session getSession() {
        return this.socket.createSession(this.address, this.port);
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
