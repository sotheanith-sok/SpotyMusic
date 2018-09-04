package persistence;

/**
 * A type of {@link Exception} that is thrown when the specified user does not exist.
 */
public class NoSuchUserException extends Exception {
    public NoSuchUserException() {
        super("There is no user with the given username");
    }

    public NoSuchUserException(String message) {
        super(message);
    }
}
