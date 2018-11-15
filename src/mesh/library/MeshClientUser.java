package mesh.library;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
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

public class MeshClientUser implements MeshLibraryActivityListener {

    private static final JsonFactory _factory = new JsonFactory();

    private final String username;

    private final String password;

    private HashMap<String, String> attributes;

    private Set<String> favorites;

    private MeshLibrary library;

    private int lastAction;

    private String actionTarget;

    private DebouncedRunnable save_task;

    private MeshClientUser(String username, String password, List<String> favs, Map<String, String> attrs, MeshLibrary library) {
        this.username = username;
        this.password = password;
        this.favorites = new HashSet<>(favs);
        this.attributes = new HashMap<>(attrs);
        this.library = library;

        this.save_task = new DebouncedRunnable(this::do_save, 5, TimeUnit.SECONDS, true, library.executor);

        for (String fav : favs) {
            library.doSearch(fav);
        }
    }

    public static Future<MeshClientUser> tryAuth(String user, String password, MeshLibrary library) {
        CompletableFuture<MeshClientUser> future = new CompletableFuture<>();

        library.executor.submit(() -> {
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
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(password.getBytes(), "AES"));

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

            JsonStreamParser parser = new JsonStreamParser(cin, true, (field) -> {
                if (field.isObject()) {
                    JsonField.ObjectField root = (JsonField.ObjectField) field;

                    if (root.containsKey("favs")) {
                        JsonField fav_field = root.getProperty("favs");
                        if (fav_field.isArray()) {
                            JsonField.ArrayField fav_array = (JsonField.ArrayField) fav_field;
                            for (JsonField fav : fav_array.getElements()) {
                                if (fav.isString()) favs.add(fav.getStringValue());
                            }
                        }
                    }

                    if (root.containsKey("attrs")) {
                        JsonField attr_field = root.getProperty("attrs");
                        if (attr_field.isObject()) {
                            for (Map.Entry<String, JsonField> entry : attr_field.getProperties().entrySet()) {
                                attrs.put(entry.getKey(), entry.getValue().getStringValue());
                            }
                        }
                    }
                }
            });
            parser.run();

            future.complete(new MeshClientUser(user, password, favs, attrs, library));
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
                    future.complete(new MeshClientUser(user, password, new LinkedList<>(), new HashMap<>(), library));
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
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(password.getBytes(), "AES"));

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

            gen.writeFieldName("favs");
            gen.writeStartArray();
            for (String f : this.favorites) {
                gen.writeString(f);
            }
            gen.writeEndArray();

            gen.writeEndObject();

            gen.writeFieldName("attrs");
            gen.writeStartObject();
            for (Map.Entry<String, String> attribute : this.attributes.entrySet()) {
                gen.writeStringField(attribute.getKey(), attribute.getValue());
            }
            gen.writeEndObject();

            gen.close();
            cout.close();

        } catch (IOException e) {
            System.err.println("[MeshClientUser][do_save] IOException while saving user file!");
            e.printStackTrace();

        } finally {
            try { cout.close(); } catch (IOException e1) {}
        }
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
    }

    @Override
    public void onSearch(String param) {

    }

    @Override
    public void onSongAdded(MeshClientSong song) {

    }

    @Override
    public void onAlbumAdded(MeshClientAlbum album) {

    }

    @Override
    public void onArtistAdded(String artist) {

    }

    @Override
    public void onAlbumAccessed(MeshClientAlbum album) {
        this.lastAction = ACTION_ALBUM;
        this.actionTarget = album.getTitle();
    }

    @Override
    public void onArtistAccessed(String artist) {
        this.lastAction = ACTION_ARTIST;
        this.actionTarget = artist;
    }

    @Override
    public void onSongPlayed(MeshClientSong song) {
        switch (this.lastAction) {
            case ACTION_SEARCH : if (this.favorites.add(song.getAlbumTitle())) this.save(); break;
            case ACTION_ALBUM :
            case ACTION_ARTIST : if (this.favorites.add(this.actionTarget)) this.save(); break;
        }

        this.lastAction = ACTION_SONG;
        this.actionTarget = song.getGUID();
    }

    private static final int ACTION_SEARCH = 1;
    private static final int ACTION_ARTIST = 2;
    private static final int ACTION_ALBUM = 3;
    private static final int ACTION_SONG = 4;

    public static final int USER_FILE_REPLICATION = 3;
}
