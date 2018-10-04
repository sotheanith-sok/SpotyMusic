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
        System.out.print("[LibraryLoader] Loading library from ");
        System.out.println(this.descriptor.getName());
        if (!this.descriptor.exists()) {
            System.out.println("[LibraryLoader] Library file does not exist");
            return new LocalLibrary(new LinkedList<>(), new HashMap<>());
        }

        LinkedList<LocalSong> songs = new LinkedList<>();

        HashMap<String, List<LocalSong>> playlists = new HashMap<>();

        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(this.descriptor);

        JsonToken token = parser.nextToken();
        while (token != null) {
            //System.out.print("[LibraryLoader] Token: ");
            //System.out.println(token);
            if (token == JsonToken.FIELD_NAME) {
                String name = parser.getText();
                //System.out.print("[LibraryLoader] Json field name: ");
                //System.out.println(name);
                if (name == "songs") {
                    while ((token = parser.nextToken()) != JsonToken.START_ARRAY);
                    while (token != JsonToken.END_ARRAY) {
                        if (token == JsonToken.VALUE_NUMBER_INT) {
                            int val = parser.getIntValue();
                            System.out.print("[LibraryLoader] Song ");
                            System.out.print(val);
                            System.out.println(" added to library");
                            songs.add(this.songs.get(val));
                        }
                        token = parser.nextToken();
                    }
                    System.out.println("[LibraryLoader] End of songs list");

                } else if (name == "playlists") {
                    String listName = null;
                    LinkedList<LocalSong> listSongs = new LinkedList<>();
                     System.out.println("[LibraryLoader] Start playlists list");
                    while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                       if (token == JsonToken.START_OBJECT) {
                          while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                             if (token == JsonToken.FIELD_NAME) {
                                if (parser.getText().equals("name")) listName = parser.nextTextValue();

                             } else if (token == JsonToken.VALUE_NUMBER_INT) {
                                listSongs.add(this.songs.get(parser.getIntValue()));
                             }
                          }
                          playlists.put(listName, listSongs);
                          listSongs = new LinkedList<>();
                       }
                    }
                }
            }
            token = parser.nextToken();
        }
        parser.close();
        System.out.println("[LibraryLoader] Read library contents from file");

        LocalLibrary lib = new LocalLibrary(songs, playlists);
        System.out.println("[LibraryLoader] Constructed LocalLibrary instance");

        return lib;
    }
}
