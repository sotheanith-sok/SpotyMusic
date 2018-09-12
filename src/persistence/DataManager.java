package persistence;

import connect.Library;
import persistence.loaders.LibraryLoader;
import persistence.loaders.UserLoader;
import persistence.writers.UserWriter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

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

    /** A {@link File} representing the library directory. */
    public static final File libRoot = new File("SpotyMusic/Libraries/");

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
    private void init(){
        if (userFile.exists()) {
            Thread t = new Thread(new Runnable(){
                @Override
                public void run() {
                    try {
                        DataManager.getDataManager().loadUsers();

                    } catch (InterruptedException e) {
                        e.printStackTrace();

                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setName("User Loader");
            t.start();

        } else {
            try {
                // create stub user file
                if (!(rootDirectory.exists() && rootDirectory.isDirectory())) rootDirectory.mkdir();
                userFile.createNewFile();
                this.saveUsers();

            } catch (IOException e) {
                e.printStackTrace();
                // handle exception?
            }
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
                this.currentLib = new FutureTask<>(new LibraryLoader(new File("/SpotyMusic/Libraries/" + username + ".json")));
                Thread t = new Thread((Runnable) this.currentLib);
                t.setName("Library Loader");
                t.start();
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
        List<User> users = new LinkedList<>();
        users.addAll(this.users.values());
        Thread t = new Thread(new UserWriter(userFile, users));
        t.setName("User Writer");
        t.start();
    }

    /**
     * Loads {@link User}s from the default user file. This method reads from the file system synchronously, and thus
     * should be called in the context of a worker thread.
     *
     * @throws InterruptedException if the thread is interrupted while reading
     * @throws ExecutionException If there is an exception while loading users
     */
    private void loadUsers() throws InterruptedException, ExecutionException {
        FutureTask<List<User>> loadTask = new FutureTask(new UserLoader(userFile));
        loadTask.run();
        List<User> users = loadTask.get();
        for (User u : users) {
            this.users.put(u.getUsername(), u);
        }

        System.out.println("Loaded " + users.size() + " from users.json");
    }
}
