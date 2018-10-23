package net.server;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import net.common.*;
import net.common.JsonField;
import net.lib.Utils;
import net.lib.ServerSocket;
import net.lib.Socket;
import persistence.DataManager;
import persistence.LocalSong;
import utils.CompletableTaskExecutor;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class LibraryServer {

    private static LibraryServer instance;

    private final ServerSocket socket;

    protected ObservableList<LocalSong> songs;

    protected ObservableSet<String> artists;

    protected ObservableMap<String, String> albums;

    private CompletableTaskExecutor taskManager;

    private LibraryServer() throws SocketException {
        this.socket = new ServerSocket(Utils.getSocketAddress(12321), this::handleSocket);
        this.songs = DataManager.getDataManager().getSongs();
        this.songs.addListener(this::onChanged);

        this.artists = FXCollections.observableSet(new LinkedHashSet<>());
        this.albums = FXCollections.observableMap(new LinkedHashMap<>());

        for (LocalSong song : this.songs) {
            this.artists.add(song.getArtist());
            this.albums.put(song.getAlbumTitle(), song.getArtist());
        }

        this.taskManager = new CompletableTaskExecutor(Runtime.getRuntime().availableProcessors(), 10);

        this.socket.open();

        System.out.println("[LibraryServer] LibraryServer initialized");
    }

    public static LibraryServer getInstance() {
        if (instance == null) {
            try {
                instance = new LibraryServer();

            } catch (SocketException e) {
                System.err.println("[LibraryServer][getInstance] SocketException while creating LibraryServer!");
                e.printStackTrace();
            }
        }
        return instance;
    }

    public InetAddress getAddress() {
        return this.socket.localAddress();
    }

    public int getPort() {
        return this.socket.getPort();
    }

    private void handleSocket(Socket socket) {
        System.out.println("[LibraryServer][handleSocket] New connection");
        this.taskManager.submit(new JsonStreamParser(socket, false, this::handleRequest));
    }

    private void handleRequest(Socket socket, JsonField request) {
        if (!request.isObject()) {
            System.err.println("[LibraryServer][handleRequest] Received malformed request");
            return;
        }

        if (!request.containsKey(Constants.REQUEST_TYPE_PROPERTY)) {
            System.err.println("[LibraryServer][handleRequest] Request Packet does not contain REQUEST_TYPE property");
            socket.close();
            return;
        }
        String type = request.getProperty(Constants.REQUEST_TYPE_PROPERTY).getStringValue();
        System.out.println("[LibraryServer][handleRequest] Received request of type \"" + type + "\"");
        switch (type) {
            case Constants.REQUEST_LIST_ARTISTS :
                this.taskManager.submit(new IterativeStreamingJsonSerializer<>(socket, true, this.artists.iterator(), (artist, gen) -> {
                    gen.writeString(artist);
                }));
                break;
            case Constants.REQUEST_LIST_ALBUMS :
                this.taskManager.submit(new IterativeStreamingJsonSerializer<>(socket, true, this.albums.entrySet().iterator(), (album, gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField("title", album.getKey());
                    gen.writeStringField("artist", album.getValue());
                    gen.writeEndObject();
                }));
                break;
            case Constants.REQUEST_LIST_SONGS :
                this.taskManager.submit(new IterativeStreamingJsonSerializer<>(socket, true, this.songs.iterator(), (song, gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField("title", song.getTitle());
                    gen.writeStringField("artist", song.getArtist());
                    gen.writeStringField("album", song.getAlbumTitle());
                    gen.writeNumberField("duration", song.getDuration());
                    gen.writeNumberField("id", song.getId());
                    gen.writeEndObject();
                }));
                break;
            case Constants.REQUEST_STREAM_SONG :
                this.handleStreamSong(socket, request.getProperty("id").getLongValue());
                break;
            case Constants.REQUEST_SUBSCRIBE :
                //socket.debug = true;
                this.taskManager.submit(new ChangeSubscriptionHandler(socket, this));
                System.out.println("[LibraryServer][handleRequest] New subscription");
                //socket.addDisconnectListener(() -> System.out.println("[LibraryServer] ChangeSubscription disconnected"));
                break;
            case "test-file" :
                this.taskManager.submit(new TestFileStreamer(socket));
                break;
            default : System.err.println("[LibraryServer][handleRequest] Unrecognized request type: " +
                    request.getProperty(Constants.REQUEST_TYPE_PROPERTY).getStringValue());
        }
    }

    private void handleStreamSong(Socket socket, long id) {
        LocalSong song = null;
        for (LocalSong s : this.songs) {
            if (s.getId() == id){
                song = s;
                break;
            }
        }

        if (song == null) {
            System.err.println("[LibraryServer][handleStreamSong] Unable to process request. Song ID not found");
            socket.close();

        } else {
            this.taskManager.submit(new SongStreamHandler(socket, song));
        }
    }

    private void onChanged(Change<? extends LocalSong> c) {
        while (c.next()) {
            if (c.wasRemoved()) {
                List<? extends LocalSong> removed = c.getRemoved();
                for (LocalSong song : removed) {
                    boolean artist = false;
                    boolean album = false;
                    for (LocalSong s2 : this.songs) {
                        artist |= s2.getArtist().equals(song.getArtist());
                        album |= s2.getAlbumTitle().equals(song.getAlbumTitle());
                        if (artist && album) break;
                    }

                    if (!artist) this.artists.remove(song.getArtist());
                    if (!album) this.albums.remove(song.getAlbumTitle());
                    System.out.format("[LibraryServer][onChanged] Song removed \"%s\"\n", song.getTitle());
                }
            } else if (c.wasAdded()) {
                List<? extends LocalSong> added = c.getAddedSubList();
                for (LocalSong song : added) {
                    this.albums.put(song.getAlbumTitle(), song.getArtist());
                    this.artists.add(song.getArtist());
                    System.out.format("[LibraryServer][onChanged] Song added \"%s\"\n", song.getTitle());
                }
            }
        }
    }
}
