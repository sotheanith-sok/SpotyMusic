package persistence.loaders;

import connect.Library;

import java.io.File;
import java.io.IOException;
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

    /**
     * Creates a new LibraryLoader that will load library data from the given {@link File}.
     *
     * @param desc the file describing the library to load
     */
    public LibraryLoader(File desc) {
        this.descriptor = desc;
    }

    @Override
    public Library call() throws IOException {


        return null;
    }
}
