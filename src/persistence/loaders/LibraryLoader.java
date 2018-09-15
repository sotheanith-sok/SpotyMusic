package persistence.loaders;

import com.fasterxml.jackson.core.*;
import connect.Library;
import persistence.LocalLibrary;
import persistence.LocalSong;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * The <code>LibraryLoader</code> is used to load library JSON files to create {@link persistence.LocalLibrary}
 * instances.
 *
 * @author Nicholas Utz
 * @see persistence.DataManager
 * @see persistence.LocalLibrary
 */
public class LibraryLoader implements Callable<Library> {

    private final File descriptor;
    private final Map<Integer, LocalSong> songs;

    /**
     * Creates a new LibraryLoader that will load library data from the given {@link File}.
     *
     * @param desc the file describing the library to load
     */
    public LibraryLoader(File desc, Map<Integer, LocalSong> songs) {
        this.descriptor = desc;
        this.songs = songs;
    }

    @Override
    public Library call() throws IOException {
        if (!this.descriptor.exists()) return new LocalLibrary(new LinkedList<>(), new HashMap<>());

        LinkedList<LocalSong> songs = new LinkedList<>();

        HashMap<String, List<LocalSong>> playlists = new HashMap<>();

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(this.descriptor);

        JsonToken token = parser.currentToken();
        while (token != null) {
            if (token == JsonToken.FIELD_NAME) {
                String name = parser.getText();
                if (name == "songs") {
                    while ((token = parser.nextToken()) != JsonToken.START_ARRAY);
                    while (token != JsonToken.END_ARRAY) {
                        if (token == JsonToken.VALUE_NUMBER_INT) songs.add(this.songs.get(parser.getIntValue()));
                        token = parser.nextToken();
                    }

                } else if (name == "playlists") {
                    String listName = null;
                    LinkedList<LocalSong> listSongs = new LinkedList<>();
                    while (token != JsonToken.END_ARRAY) {
                        while (token != JsonToken.END_OBJECT) {
                            if (token == JsonToken.FIELD_NAME) {
                                if (parser.getText().equals("name")) listName = parser.nextTextValue();

                            } else if (token == JsonToken.VALUE_NUMBER_INT) {
                                listSongs.add(this.songs.get(parser.nextIntValue(0)));
                            }
                            token = parser.nextToken();
                        }

                        playlists.put(listName, listSongs);
                        listSongs = new LinkedList<>();
                        token = parser.nextToken();
                    }
                }
            }
        }
        parser.close();
        System.out.println("[LibraryLoader] Read library contents from file");

        LocalLibrary lib = new LocalLibrary(songs, playlists);
        System.out.println("[LibraryLoader] Constructed LocalLibrary instance");

        return lib;
    }
}
