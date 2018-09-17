package persistence.loaders;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import persistence.LocalSong;

import java.io.File;
import java.io.IOException;

/**
 * The MediaLoader class loads data stored in the {@link persistence.DataManager#mediaIndex} file.
 */
public class MediaLoader implements Runnable {

    private final File index;
    private final SongLoadedHandler handler;

    /**
     * Creates a new MediaLoader, that will load the media index found in the given {@link File}.
     *
     * @param index the index file to load
     * @param handler a {@link SongLoadedHandler} to invoke when a song is loaded
     */
    public MediaLoader(File index, SongLoadedHandler handler) {
        this.index = index;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            if (!this.index.exists()) return;
            JsonFactory jsonFactory = new JsonFactory();
            JsonParser parser = jsonFactory.createParser(this.index);
            JsonToken token = parser.currentToken();
            while (token != JsonToken.END_ARRAY) {
                if (token == JsonToken.START_OBJECT) {
                    LocalSong song = LocalSong.loadSong(parser);
                    if (song == null) {
                        token = parser.nextToken();
                        continue;

                    } else {
                        this.handler.onSongLoaded(song);
                    }
                }
            }

            parser.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * The SongLoadedHandler interface is a FunctionalInterface that defines a handler which is invoked
     * when a song is loaded by the {@link MediaLoader}.
     */
    @FunctionalInterface
    public interface SongLoadedHandler {
        void onSongLoaded(LocalSong song);
    }
}
