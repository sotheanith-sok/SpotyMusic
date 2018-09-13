package persistence;

import connect.Library;
import persistence.loaders.LibraryLoader;
import persistence.loaders.MediaLoader;
import persistence.loaders.UserLoader;
import persistence.writers.MediaWriter;
import persistence.writers.UserWriter;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The <code>DataManager</code> class is the primary interface for accessing persistent storage.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 */
public class DataManager {
    /** A {@link File} that represents the root directory for all SpotyMusic data. */
    public static final File rootDirectory = new File("SpotyMusic/");

    /** A {@link File} that represents the users.json file. */
    public static final File userFile = new File("SpotyMusic/users.json");

    /** A {@link File} representing the root of the media directory. */
    public static final File mediaRoot = new File("SpotyMusic/Media/");

    /** A {@link File} representing the media index file. */
    public static final File mediaIndex = new File("SpotyMusic/Media/index.json");

    /** A {@link File} representing the library directory. */
    public static final File libRoot = new File("SpotyMusic/Libraries/");

    /**
     * An {@link ExecutorService} to handle asynchronous operations.
     */
    protected ExecutorService executor;

    /** A {@link Map} containing all of ths songs in the local library, irrespective of users. */
    private Map<Integer, LocalSong> songs;

    private int largestId = 0;

    /** Stores all loaded users. */
    private Map<String, User> users;

    /** The current {@link User}. */
    private User currentUser = null;

    /** A {@link Future} that resolves to the {@link Library} of the current {@link User}. */
    private Future<Library> currentLib = null;

    /**
     * Private constructor.
     */
    private DataManager() {
        this.users = new HashMap<>();
    }

    /**
     * Initializes the DataManager.
     */
    public void init(){
        this.executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));

        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!mediaRoot.exists()) mediaRoot.mkdir();
        if (!libRoot.exists()) libRoot.mkdir();

        this.users = new ConcurrentHashMap<>();
        if (userFile.exists()) {
            this.loadUsers();

        } else {
            this.saveUsers();
        }

        this.songs = new ConcurrentHashMap<>();
        if (mediaIndex.exists()) {
            this.loadMedia();

        } else {
            this.saveMedia();
        }
    }

    /**
     * Instance of DataManager. There is only and always a single instance of DataManager.
     */
    private static DataManager instance = new DataManager();

    /**
     * Returns the singleton instance of DataManager.
     *
     * @return an instance of DataManager
     */
    public static DataManager getDataManager() {
        return instance;
    }

    /**
     * Attempts to authenticate a user with the given username and password. Returns a boolean indicating success.
     * Once a user is authenticated, the {@link #getCurrentUser()} and {@link #getCurrentLibrary()} methods can be
     * used to retrieve the name of the current user, and that user's library.
     *
     * @param username the username of a user
     * @param password the password of the user with the given username
     * @return true on successful authentication
     */
    public boolean tryAuth(String username, String password) {
        if (this.users.containsKey(username)) {
            User u = this.users.get(username);
            if (u.testPassword(password)) {
                this.currentUser = u;
                // start loading the user's library
                this.currentLib = this.executor.submit(new LibraryLoader(new File("/SpotyMusic/Libraries/" + username + ".json"), this.songs));
                return true;
            }
        }

        return false;
    }

    /**
     * Creates and saves a new {@link User} with the given username and password.
     *
     * @param username the username of the new user to create
     * @param password the password of the new user
     */
    public void registerUser(String username, String password) {
        if (this.users.containsKey(username)) throw new IllegalArgumentException("User exists");
        this.users.put(username, new User(username, password));
        this.saveUsers();
    }

    /**
     * Returns the current user, or null if there is no authorized user.
     *
     * @return current user or null
     */
    public User getCurrentUser() {
        return this.currentUser;
    }

    /**
     * Returns a {@link Future} which resolves to the {@link Library} of the current user.
     * Returns null if there is no current user.
     *
     * @return future of user's library
     */
    public Future<Library> getCurrentLibrary() {
        return this.currentLib;
    }

    /**
     * Saves the current list of {@link User}s to this user file.
     */
    private void saveUsers() {
        List<User> users = new LinkedList<>(this.users.values());
        this.executor.submit(new UserWriter(userFile, users));
    }

    /**
     * Loads {@link User}s from the default user file.
     */
    private void loadUsers() {
        this.executor.submit(new UserLoader(userFile, this.new OnUserLoaded()));
    }

    /**
     * Loads the {@link #mediaIndex} file.
     */
    private void loadMedia() {
        this.executor.submit(new MediaLoader(mediaIndex, this.new OnSongLoaded()));
    }

    /**
     * Writes the {@link #mediaIndex} file.
     */
    private void saveMedia() {
        this.executor.submit(new MediaWriter(mediaIndex, new LinkedList<LocalSong>(this.songs.values())));
    }

    /**
     * Callback handler for {@link MediaLoader}.
     */
    private class OnSongLoaded implements MediaLoader.SongLoadedHandler {
       @Override
        public void onSongLoaded(LocalSong song) {
            int id = song.getId();
            songs.put(id, song);
            if (id > largestId) largestId = id;
        }
    }

    /**
     * Callback handler for {@link UserLoader}.
     */
    private class OnUserLoaded implements UserLoader.UserLoadedHandler {
        @Override
        public void onUserLoaded(User user) {
            synchronized (DataManager.this) {
                users.put(user.getUsername(), user);
            }
        }
    }
}
