package persistence.loaders;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import persistence.DataManager;
import persistence.User;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * The <code>UserLoader</code> class is a {@link Callable} that loads user information from a provided file.
 *
 * UserLoader is used internally by {@link DataManager} to load user data.
 *
 * @since 0.0.1
 * @author Nicholas Utz
 * @see DataManager
 */
public class UserLoader implements Callable<List<User>> {

    private final File userFile;

    /**
     * Creates a new <code>UserLoader</code> that will load users from the given {@link File}.
     *
     * @param userFile the file from which to load user data
     */
    public UserLoader(File userFile) {
        this.userFile = userFile;
    }

    @Override
    public List<User> call() throws Exception {
        List<User> result = new LinkedList<>();

        // instantiate a Jackson JSON parser
        JsonFactory jsonFactory = new JsonFactory();
        JsonParser parser = jsonFactory.createParser(this.userFile);

        // iterate over JSON tokens
        JsonToken token = parser.currentToken();
        for (;;) {
            if (token == JsonToken.START_ARRAY) {
                // expected to be the first token.

            } else if (token == JsonToken.START_OBJECT) {
                // beginning of a user entry
                User entry = User.load(parser);
                if (entry != null) result.add(entry);

            } else if (token == JsonToken.END_ARRAY) {
                // end of user list
                break;
            }

            // iterate to next token
            token = parser.nextToken();
        }

        // close parser, and return parsed data
        parser.close();
        return result;
    }
}
