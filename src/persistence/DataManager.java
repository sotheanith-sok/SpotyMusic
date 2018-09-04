package persistence;

import connect.Library;

/**
 * The <code>DataManager</code> class is the primary interface for accessing persistent storage.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 */
public class DataManager {
    /**
     * Private constructor.
     */
    private DataManager() {

    }

    /**
     * Instance of DataManager. There is only and always a single instance of DataManager.
     */
    private static DataManager instance = new DataManager();

    public DataManager getDataManager() {
        return instance;
    }

    /**
     * Returns a {@link Library} representing the music collection of the user with the provided username and
     * password. If there is no user with the given username and password combination, an exception is thrown.
     *
     * @param username the username of a user
     * @param password the password of the user with the given username
     * @return the named user's library
     * @throws NoSuchUserException if there is no user with the given username and password
     */
    public Library tryAuth(String username, String password) throws NoSuchUserException {
        return null;
    }

    /**
     * Returns the username of the current user, or null if there is no authorized user.
     *
     * @return current user's name or null
     */
    public String getCurrentUser() {
        return null;
    }

    /**
     * Returns the {@link Library} of the current user, or null if there is no current user.
     *
     * @return user's library
     */
    public Library getCurrentLibrary() {
        return null;
    }


}
