package persistence.writers;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import persistence.LocalLibrary;

import java.io.File;
import java.io.IOException;

/**
 * LibraryWriter is a simple {@link Runnable} that writes the contents of a {@link LocalLibrary} to a {@link File}.
 */
public class LibraryWriter implements Runnable {

    private final File dest;
    private final LocalLibrary lib;

    /**
     * Creates a new LibraryWriter that will write the given {@link LocalLibrary} to the given {@link File}.
     *
     * @param dest the destination file to write to
     * @param lib the library to write
     */
    public LibraryWriter(File dest, LocalLibrary lib) {
        this.dest = dest;
        this.lib = lib;
    }

    @Override
    public void run() {
        try {
            JsonFactory factory = new JsonFactory();
            JsonGenerator gen = factory.createGenerator(this.dest, JsonEncoding.UTF8);

            this.lib.saveLibrary(gen);

            gen.close();
            System.out.println("[LibraryWriter] Saved local library to file");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
