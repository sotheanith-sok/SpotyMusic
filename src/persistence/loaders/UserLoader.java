package persistence.loaders;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import persistence.DataManager;
import persistence.User;

import java.io.File;
import java.io.IOException;
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
public class UserLoader implements Runnable {

    private final File userFile;
    private final UserLoadedHandler handler;

    /**
     * Creates a new <code>UserLoader</code> that will load users from the given {@link File}.
     *
     * @param userFile the file from which to load user data
     */
    public UserLoader(File userFile, UserLoadedHandler handler) {
        this.userFile = userFile;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            if (!this.userFile.exists()) this.userFile.createNewFile();

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
                    if (entry != null) this.handler.onUserLoaded(entry);

                } else if (token == JsonToken.END_ARRAY) {
                    // end of user list
                    break;
                }

                // iterate to next token
                token = parser.nextToken();
            }

            // close parser, and return parsed data
            parser.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface UserLoadedHandler {
        void onUserLoaded(User user);
    }
}
