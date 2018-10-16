package persistence;

import connect.Library;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import persistence.loaders.LibraryLoader;
import persistence.loaders.MediaLoader;
import persistence.loaders.UserLoader;
import persistence.writers.LibraryWriter;
import persistence.writers.MediaWriter;
import persistence.writers.UserWriter;
import utils.DebouncedRunnable;
import utils.ObservableListImpl;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The <code>DataManager</code> class is the primary interface for accessing persistent storage.
 *
 * @author Nicholas Utz
 * @since 0.0.1
 */
public class DataManager {
    /**
     * A {@link File} that represents the root directory for all SpotyMusic data.
     */
    public static final File rootDirectory = new File("SpotyMusic/");

    /**
     * A {@link File} that represents the users.json file.
     */
    public static final File userFile = new File("SpotyMusic/users.json");

    /**
     * A {@link File} representing the root of the media directory.
     */
    public static final File mediaRoot = new File("SpotyMusic/Media/");

    /**
     * A {@link File} representing the media index file.
     */
    public static final File mediaIndex = new File("SpotyMusic/Media/index.json");

    /**
     * A {@link File} representing the library directory.
     */
    public static final File libRoot = new File("SpotyMusic/Libraries/");

    /**
     * Instance of DataManager. There is only and always a single instance of DataManager.
     */
    private static DataManager instance = new DataManager();

    /**
     * An {@link ExecutorService} to handle asynchronous operations.
     */
    protected ScheduledExecutorService executor;

    /**
     * An {@link ObservableList} containing all of ths songs in the local library, irrespective of users.
     */
    private ObservableList<LocalSong> songs;

    /**
     * Stores all loaded users.
     */
    private Map<String, User> users;
    private DebouncedRunnable saveUsersTask;
    private DebouncedRunnable saveMediaTask;
    private DebouncedRunnable saveLibraryTask;

    /**
     * The current {@link User}.
     */
    private User currentUser = null;

    /**
     * A {@link Future} that resolves to the {@link Library} of the current {@link User}.
     */
    private Future<Library> currentLib = null;

    /**
     * Private constructor.
     */
    private DataManager() {
        this.users = new ConcurrentHashMap<>();
        this.songs = FXCollections.observableList(new LinkedList<>());
    }

    /**
     * Returns the singleton instance of DataManager.
     *
     * @return an instance of DataManager
     */
    public static DataManager getDataManager() {
        return instance;
    }

    /**
     * Initializes the DataManager.
     */
    public void init() {
        this.executor = new ScheduledThreadPoolExecutor(4);

        this.saveUsersTask = new DebouncedRunnable(() -> {
            List<User> users = new LinkedList<>(this.users.values());
            UserWriter writer = new UserWriter(userFile, users);
            writer.run();
        }, 5, TimeUnit.SECONDS, this.executor);
        this.saveMediaTask = new DebouncedRunnable(() -> {
            MediaWriter writer = new MediaWriter(mediaIndex, this.songs);
            writer.run();
        }, 5, TimeUnit.SECONDS, this.executor);

        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!mediaRoot.exists()) mediaRoot.mkdir();
        if (!libRoot.exists()) libRoot.mkdir();

        if (userFile.exists()) {
            this.loadUsers();

        } else {
            this.saveUsers();
        }

        if (mediaIndex.exists()) {
            this.loadMedia();

        } else {
            this.saveMedia();
        }
    }

    /**
     * Attempts to authenticate a user with the given username and password. Returns a boolean indicating success.
     * Once a user is authenticated, {@link #getCurrentUser()} can be used to retrieve the name of the current user.
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
                // TODO: reimplement this
                //this.currentLib = this.executor.submit(new LibraryLoader(new File("SpotyMusic/Libraries/" + username + ".json"), this.songs));
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

    public ObservableList<LocalSong> getSongs() {
        return this.songs;
    }

    /**
     * Starts a {@link FileImportTask} that imports the given file into SpotyMusic's file system.
     * The resulting {@link LocalSong} is automatically added to the library of the current user.
     *
     * @param file the file to import
     * @param title the title of the song stored in the file
     * @param artist the artist who wrote the song
     * @param album the album in which the song was released
     * @return Future resolving to success
     */
    public Future<Boolean> importFile(File file, String title, String artist, String album) {
        return this.executor.submit(new FileImportTask(file, this::onFileImported, title, artist, album), true);
    }

    /**
     * Starts a {@link FileImportTask} that imports the given file into the SpotyMusic file system.
     * The resulting {@link LocalSong} is automatically added to the current user's library.
     *
     * @param file the file to import
     * @param title the title of the song stored in the file
     * @param artist the artist who wrote the song
     * @param album the album in which the song was released
     * @param listener a progress listener for the import task
     * @return Future resolving to success
     */
    public Future<Boolean> importFile(File file, String title, String artist, String album, FileImportTask.FileImportProgressListener listener) {
        return this.executor.submit(new FileImportTask(file, this::onFileImported, title, artist, album, listener), true);
    }

    /**
     * Saves the current list of {@link User}s to this user file.
     */
    public void saveUsers() {
        this.saveUsersTask.run();
    }

    /**
     * Loads {@link User}s from the default user file.
     */
    private void loadUsers() {
        this.executor.submit(new UserLoader(userFile, this::onUserLoaded));
    }

    /**
     * Loads the {@link #mediaIndex} file.
     */
    private void loadMedia() {
        this.executor.submit(new MediaLoader(mediaIndex, this::onSongLoaded));
    }

    /**
     * Writes the {@link #mediaIndex} file.
     */
    public void saveMedia() {
        this.saveMediaTask.run();
    }

    /**
     * Saves the library of the current user.
     */
    public Future<Boolean> saveLibrary() {
        return this.saveLibraryTask.run();
    }

    /**
     * Callback handler for {@link MediaLoader}.
     */
    private void onSongLoaded(LocalSong song) {
        this.songs.add(song);
        System.out.print("[DataManager][OnSongLoaded] Song loaded: ");
        System.out.print(song.getTitle());
        System.out.print(" (");
        System.out.print(song.getId());
        System.out.println(")");
    }

    /**
     * Callback handler for {@link UserLoader}.
     */
    private void onUserLoaded(User user) {
        synchronized (DataManager.this) {
            users.put(user.getUsername(), user);
        }
    }

    /**
     * A callback used when a {@link FileImportTask} completes.
     *
     * @param title the title of the song imported
     * @param artist the artist who wrote the song
     * @param album the album in which the song was published
     * @param duration the length of the song
     * @param path the path to the song's file
     * @param id the unique ID number of the song
     */
    private void onFileImported(String title, String artist, String album, long duration, File path, long id) {
        LocalSong newSong = new LocalSong(title, artist, album, duration, path, id);
        System.out.format("[DataManager][onFileImported] Song \"%s\" imported\n", title);
        this.songs.add(newSong);
        this.saveMedia();
    }
}
