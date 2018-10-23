package persistence.writers;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import persistence.LocalSong;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * MediaWriter is a simple {@link Runnable} that writes a list of {@link LocalSong}s to a JSON file.
 */
public class MediaWriter implements Runnable {

    private final File index;
    private final List<LocalSong> songs;

    /**
     * Creates a new MediaWriter that will write the given song list to the given file.
     *
     * @param index the file to write song metadata to
     * @param songs the list of songs to write to the file
     */
    public MediaWriter(File index, List<LocalSong> songs) {
        this.index = index;
        this.songs = songs;
    }

    @Override
    public void run() {
        try {
            JsonFactory factory = new JsonFactory();
            JsonGenerator gen = factory.createGenerator(this.index, JsonEncoding.UTF8);

            gen.writeStartArray();
            for (LocalSong s : this.songs) {
                s.saveSong(gen);
            }

            gen.writeEndArray();
            gen.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[MediaWriter][run] Saved media index");
    }

}
