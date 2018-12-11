package mesh.library;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import connect.Song;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import net.common.JsonField;
import net.common.JsonStreamParser;
import utils.DebouncedRunnable;
import utils.Utils;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

public class MeshClientUser implements MeshLibraryActivityListener, MeshClientPlaylist.PlaylistChangeListener {

    private static final JsonFactory _factory = new JsonFactory();

    private final String username;

    private final String password;

    private ObservableList<MeshClientPlaylist> playlists;

    private HashMap<String, String> attributes;

    private Set<String> favorites;
    private Set<MeshClientSong> songs;
    private Set<MeshClientAlbum> albums;
    private Set<String> artists;

    private MeshLibrary library;

    private DebouncedRunnable save_task;

    private MeshClientUser(String username, String password, List<String> favs, Map<String, String> attrs, Map<String, List<Long>> playlists, MeshLibrary library) {
        this.username = username;
        this.password = password;
        this.favorites = new HashSet<>(favs);
        this.attributes = new HashMap<>(attrs);
        this.library = library;

        this.songs = new HashSet<>();
        this.albums = new HashSet<>();
        this.artists = new HashSet<>();

        this.playlists = FXCollections.observableArrayList();
        for (Map.Entry<String, List<Long>> entry : playlists.entrySet()) {
            MeshClientPlaylist playList = new MeshClientPlaylist(entry.getKey(), entry.getValue(), library);
            playList.setChangeListener(this);
            this.playlists.add(playList);
        }

        this.save_task = new DebouncedRunnable(this::do_save, 5, TimeUnit.SECONDS, true, library.executor);

        for (String fav : favs) {
            library.doSearch(fav);
        }
    }

    public static Future<MeshClientUser> tryAuth(String user, String password, MeshLibrary library) {
        CompletableFuture<MeshClientUser> future = new CompletableFuture<>();

        library.executor.submit(() -> {
            System.out.println("[MeshClientUser][tryAuth] Attempting to load user: " + user);

            String fileName = Utils.hash(user + password, "SHA-256");

            Future<Boolean> fexists = library.dfs.fileExists(fileName);

            try {
                boolean exists = fexists.get(3, TimeUnit.SECONDS);
                if (!exists) {
                    future.completeExceptionally(new IllegalArgumentException("There is no user with the given username and password"));
                }

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println();
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            Future<InputStream> fin = library.dfs.readFile(fileName);
            InputStream in;

            try {
                in = fin.get(3, TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[MeshClientUser][tryAuth] Unable to read user file from DFS");
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            Cipher cipher;
            try {
                cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                byte[] key = new byte[16];
                (new Random(password.hashCode())).nextBytes(key);
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));

            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                System.err.println("[MeshClientUser][tryAuth] Unable to create cipher!");
                e.printStackTrace();
                future.completeExceptionally(e);
                return;

            } catch (InvalidKeyException e) {
                System.err.println("[MeshClientUser][tryAuth] Unable to construct cipher key");
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            CipherInputStream cin = new CipherInputStream(in, cipher);

            List<String> favs = new LinkedList<>();
            Map<String, String> attrs = new HashMap<>();
            Map<String, List<Long>> playlists = new HashMap<>();

            JsonStreamParser parser = new JsonStreamParser(cin, true, (field) -> {
                System.out.println(field.toString());
                if (field.isObject()) {
                    JsonField.ObjectField root = (JsonField.ObjectField) field;

                    if (root.containsKey("favs")) {
                        JsonField fav_field = root.getProperty("favs");
                        System.out.println("[MeshClientUser][tryAuth] Found Favs field");
                        if (fav_field.isArray()) {
                            JsonField.ArrayField fav_array = (JsonField.ArrayField) fav_field;
                            System.out.println("[MeshClientUser][tryAuth] Reading " + fav_array.getElements().size() + " favorites");
                            for (JsonField fav : fav_array.getElements()) {
                                if (fav.isString()) {
                                    favs.add(fav.getStringValue());
                                    System.out.println("[MeshClientUser][tryAuth] Loaded fav: \"" + fav.getStringValue() + "\"");
                                }
                            }
                        }
                    }

                    if (root.containsKey("attrs")) {
                        JsonField attr_field = root.getProperty("attrs");
                        System.out.println("[MeshClientUser][tryAuth] Found attributes field");
                        if (attr_field.isObject()) {
                            System.out.println("[MeshClientUser][tryAuth] Reading " + attr_field.getProperties().size() + " attributes");
                            for (Map.Entry<String, JsonField> entry : attr_field.getProperties().entrySet()) {
                                attrs.put(entry.getKey(), entry.getValue().getStringValue());
                                System.out.println("[MeshClientUser][tryAuth] Loaded user attribute: \"" + entry.getKey() + "\"=\"" + entry.getValue().getStringValue() + "\"");
                            }
                        }
                    }

                    if (root.containsKey("playlists")) {
                        JsonField playlist_field = root.getProperty("playlists");
                        if (playlist_field.isObject()) {
                            for (Map.Entry<String, JsonField> entry : playlist_field.getProperties().entrySet()) {
                                if (entry.getValue().isArray()) {
                                    String name = entry.getKey();
                                    List<Long> songs = new LinkedList<>();

                                    for (JsonField item : entry.getValue().getElements()) {
                                        songs.add(item.getLongValue());
                                    }

                                    playlists.put(name, songs);
                                }
                            }
                        }
                    }
                }
            });
            parser.run();
            /*
            byte[] trx = new byte[1024 * 8];
            int read = 0;
            try {
                while ((read = cin.read(trx, 0, trx.length)) != -1) System.out.write(trx, 0, read);
            } catch (IOException e) {}
            */

            future.complete(new MeshClientUser(user, password, favs, attrs, playlists, library));
            System.out.println("[MeshClientUser][tryAuth] Loaded user " + user);
        });

        return future;
    }

    public static Future<MeshClientUser> register(String user, String password, MeshLibrary library) {
        CompletableFuture<MeshClientUser> future = new CompletableFuture<>();

        library.executor.submit(() -> {
            String fileName = Utils.hash(user + password, "SHA-256");

            Future<Boolean> exists = library.dfs.fileExists(fileName);

            try {
                if (exists.get(10, TimeUnit.SECONDS)) {
                    future.completeExceptionally(new IllegalArgumentException("Username taken"));

                } else {
                    future.complete(new MeshClientUser(user, password, new LinkedList<>(), new HashMap<>(), new HashMap<>(), library));
                }

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[MeshClientUser][register] Exception while checking if user exists!");
                e.printStackTrace();
                future.completeExceptionally(e);
            }

        });

        return future;
    }

    private void save() {
        this.save_task.run();
    }

    private void do_save() {
        System.out.println("[MeshClientUser][do_save] Saving user: " + this.username);
        String fileName = Utils.hash(this.username + this.password, "SHA-256");

        Future<OutputStream> fout = this.library.dfs.writeFile(fileName, USER_FILE_REPLICATION);
        OutputStream out;

        try {
            out = fout.get(5, TimeUnit.SECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("[MeshClientUser][do_save] Unable to get outputstream for user file");
            e.printStackTrace();
            return;
        }

        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] key = new byte[16];
            (new Random(password.hashCode())).nextBytes(key);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            System.err.println("[MeshClientUser][tryAuth] Unable to create cipher!");
            e.printStackTrace();
            try { out.close(); } catch (IOException e1) {}
            return;

        } catch (InvalidKeyException e) {
            System.err.println("[MeshClientUser][tryAuth] Unable to construct cipher key");
            e.printStackTrace();
            try { out.close(); } catch (IOException e1) {}
            return;
        }

        CipherOutputStream cout = new CipherOutputStream(out, cipher);

        try {
            JsonGenerator gen = _factory.createGenerator(cout, JsonEncoding.UTF8);
            gen.writeStartObject();

            int favs = 0;
            int attrs = 0;
            int lists = 0;

            {   // favorites array
                gen.writeFieldName("favs");
                gen.writeStartArray();
                for (MeshClientSong song : this.songs) {
                    gen.writeString(song.getTitle());
                    favs++;
                }
                for (MeshClientAlbum album : this.albums) {
                    gen.writeString(album.getTitle());
                    favs++;
                }
                for (String artist : this.artists) {
                    gen.writeString(artist);
                    favs++;
                }
                gen.writeEndArray();
            }

            {   // attributes object
                gen.writeFieldName("attrs");
                gen.writeStartObject();
                for (Map.Entry<String, String> attribute : this.attributes.entrySet()) {
                    gen.writeStringField(attribute.getKey(), attribute.getValue());
                    attrs++;
                }
                gen.writeEndObject();
            }

            {   // playlists object
                gen.writeFieldName("playlists");
                gen.writeStartObject();

                for (MeshClientPlaylist playlist : this.playlists) {
                    gen.writeArrayFieldStart(playlist.getName());

                    for (Song song : playlist.getSongs()) {
                        gen.writeNumber(song.getId());
                    }

                    gen.writeEndArray();
                    lists++;
                }

                gen.writeEndObject();
            }

            System.out.println("[MeshClientUser][do_save] Wrote " + attrs + " attributes, " +
                    favs + " favorites, and " +
                    lists + " playlists");

            gen.writeEndObject();
            gen.close();
            cout.close();

        } catch (IOException e) {
            System.err.println("[MeshClientUser][do_save] IOException while saving user file!");
            e.printStackTrace();

        } finally {
            try { cout.close(); } catch (IOException e1) {}
        }

        System.out.println("[MeshClientUser][do_save] Saved user: " + this.username);
    }

    public String getUsername() {
        return this.username;
    }

    public String getAttribute(String key) {
        return this.attributes.get(key);
    }

    public String getAttributeOrDefault(String key, String defaultValue) {
        return this.attributes.getOrDefault(key, defaultValue);
    }

    public void setAttribute(String key, String value) {
        this.attributes.put(key, value);
        this.save();
    }

    private void addArtistFavorite(String artist) {
        this.artists.add(artist);

        this.save();
    }

    private void addAlbumFavorite(MeshClientAlbum album) {
        int sameArtist = 0;
        for (MeshClientAlbum album1 : this.albums) {
            if (album1.getArtist().equals(album.getArtist())) sameArtist++;
        }

        if (sameArtist >= 1) {
            this.albums.removeIf((album2) -> album2.getArtist().equals(album.getArtist()));
            this.addArtistFavorite(album.getArtist());

        } else {
            this.albums.add(album);
        }

        this.save();
    }

    private void addSongFavorite(MeshClientSong song) {
        if (this.artists.contains(song.getArtist())) return;
        if (this.albums.contains(this.library.getAlbumByTitle(song.getAlbumTitle()))) return;
        if (this.songs.contains(song)) return;

        int sameAlbum = 0;
        for (MeshClientSong song1 : this.songs) {
            if (song1.getAlbumTitle().equals(song.getAlbumTitle())) sameAlbum++;
        }

        if (sameAlbum > 3) {
            this.songs.removeIf((song2) -> song2.getAlbumTitle().equals(song.getAlbumTitle()));
            this.addAlbumFavorite(this.library.getAlbumByTitle(song.getAlbumTitle()));

        } else {
            this.songs.add(song);
        }

        this.save();
    }

    public ObservableList<MeshClientPlaylist> getPlaylists() {
        return FXCollections.unmodifiableObservableList(this.playlists);
    }

    public MeshClientPlaylist createPlaylist(String title) {
        MeshClientPlaylist newList = new MeshClientPlaylist(title, this.library);
        newList.setChangeListener(this);
        this.playlists.add(newList);
        this.save();
        return newList;
    }

    @Override
    public void onSearch(String param) {

    }

    @Override
    public void onSongAdded(MeshClientSong song) {
        if (this.favorites.contains(song.getTitle())) {
            this.songs.add(song);
        }
    }

    @Override
    public void onAlbumAdded(MeshClientAlbum album) {
        if (this.favorites.contains(album.getTitle())) {
            this.albums.add(album);
        }
    }

    @Override
    public void onArtistAdded(String artist) {
        if (this.favorites.contains(artist)) {
            this.artists.add(artist);
        }
    }

    @Override
    public void onAlbumAccessed(MeshClientAlbum album) {

    }

    @Override
    public void onArtistAccessed(String artist) {

    }

    @Override
    public void onSongPlayed(MeshClientSong song) {
        this.addSongFavorite(song);
    }

    @Override
    public void onSongImported(MeshClientSong song) {
        this.addSongFavorite(song);
    }

    public static final int USER_FILE_REPLICATION = 3;

    @Override
    public void onSongAdded(Song added) {
        try {
            this.save();

        } catch (ClassCastException e) {
            System.err.println("[MeshClientUser][onSongAdded] Non-mesh song added to mesh playlist?");
        }
    }

    @Override
    public void onSongRemoved(Song removed) {
        this.save();
    }
}
