package persistence.writers;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import persistence.User;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The <code>UserWriter</code> class is a utility class that is used to write {@link User}s to a JSON file.
 *
 * @author Nicholas Utz
 * @see User
 * @see persistence.loaders.UserLoader
 * @see persistence.DataManager
 */
public class UserWriter implements Runnable {

    private final File userFile;

    private final List<User> users;

    /**
     * Creates a new UserWriter that will write the given {@link List} of {@link User}s to the given {@link File}.
     *
     * @param userFile the file to write to
     * @param users the users to write
     */
    public UserWriter(File userFile, List<User> users) {
        this.userFile = userFile;
        this.users = users;
    }

    @Override
    public void run() {
        try {
            // instantiate a JSON generator
            JsonFactory jsonFactory = new JsonFactory();
            JsonGenerator gen = jsonFactory.createGenerator(this.userFile, JsonEncoding.UTF8);

            // write users
            gen.writeStartArray();
            for (User user : this.users) {
                user.write(gen);
            }

            // close file
            gen.writeEndArray();
            gen.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
