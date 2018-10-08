package persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

/**
 * The <code>User</code> class is a simple data construct that stores data about users and provides utility methods
 * for storing and loading user data.
 *
 * @author Nicholas Utz
 * @see User
 * @see DataManager
 */
public class User {

    private final String username;
    private final String password;
    private String theme = "Default";

    /**
     * Creates a new instance of User, with the given username, password, and theme preference.
     *
     * @param username the name of the user
     * @param password the user's password
     * @param theme    the name of the user's prefered theme
     */
    public User(String username, String password, String theme) {
        this.username = username;
        this.password = password;
        this.theme = theme;
    }

    /**
     * Creates a new instance of User, with the given username and password, and the default
     * theme.
     *
     * @param username the name of the new user
     * @param password the password of the new user
     */
    public User(String username, String password) {
        this(username, password, "Default");
    }

    /**
     * Reads data from the given {@link JsonParser} and returns a User based on the read data.
     *
     * @param in a source of JSON data
     * @return a User or null if insufficient data is found
     * @throws IOException if there is an exception while reading data
     */
    public static User load(JsonParser in) throws IOException {
        String username = null;
        String password = null;
        String theme = "Default";

        JsonToken token = in.currentToken();
        for (; ; ) {
            if (token == JsonToken.FIELD_NAME) {
                // check name of field
                String field = in.getText();

                if (field == "username") {
                    username = in.nextTextValue();

                } else if (field == "password") {
                    password = in.nextTextValue();

                } else if (field == "theme") {
                    theme = in.nextTextValue();

                }// else... um... that shouldn't be here

            } else if (token == JsonToken.END_OBJECT) {
                break;
            }

            token = in.nextToken();
        }

        // verify that we have all the data we need
        if (username == null || password == null) return null;
        return new User(username, password, theme);
    }

    /**
     * Returns the username of the user represented by this User instance.
     *
     * @return user's username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Sets the name of the user's preferred theme.
     *
     * @param name the name of the user's preferred theme
     */
    public void setTheme(String name) {
        this.theme = name;
        DataManager.getDataManager().saveUsers();
    }

    /**
     * Returns the name of the user's preferred theme.
     *
     * @return user's theme preference
     */
    public String getTheme() {
        return this.theme;
    }

    /**
     * Checks the given password against the password of this User.
     *
     * @param password the password to test
     * @return whether the given password matches that of this User
     */
    public boolean testPassword(String password) {
        return this.password.equals(password);
    }

    /**
     * Writes this User to the given {@link JsonGenerator}.
     *
     * @param out a destination for this User's data
     * @throws IOException if an exception is thrown when writing data
     */
    public void write(JsonGenerator out) throws IOException {
        out.writeStartObject();
        out.writeStringField("username", this.username);
        out.writeStringField("password", this.password);
        out.writeStringField("theme", this.theme);
        out.writeEndObject();
    }
}
