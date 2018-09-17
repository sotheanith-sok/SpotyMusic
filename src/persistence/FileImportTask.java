package persistence;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.Map;

/**
 * The FileImportTask handles importing an audio file from the file system into SpotyMusic's file structure.
 *
 * @author Nicholas Utz
 */
public class FileImportTask implements Runnable {

    private final File src;
    private File dest;

    private FileImportedHandler handler;

    /**
     * Creates a new FileImportTask that imports te given {@link File}, and invokes the given
     * {@link FileImportedHandler} when finished.
     *
     * @param file the file to import
     * @param handler a handler to invoke when finished
     */
    public FileImportTask(File file, FileImportedHandler handler) {
        this.src = file;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(this.src);
            Map<String, Object> properties = format.properties();
            System.out.println("Properties of AudioFile: " + this.src.getName());
            for (String s : properties.keySet()) {
                System.out.print("\t");
                System.out.println(s);
            }
            String title = (String) properties.get("title");
            String artist = (String) properties.getOrDefault("author", "Unknown");
            String album = (String) properties.getOrDefault("album", "Unknown");
            long duration = (Long) properties.getOrDefault("duration", 0);
            this.dest = new File("SpotyMusic/Media/Artists/" + artist + "/" + album + "/" + this.src.getName());

            this.dest.createNewFile();

            BufferedInputStream in = new BufferedInputStream(new FileInputStream(this.src));
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(this.dest));
            byte[] buffer = new byte[4000];

            int bytes = 0;
            while ((bytes = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytes);
            }
            in.close();
            out.close();

            this.handler.onFileImported(title, artist, album, duration, this.dest);

        } catch (IOException e) {
            e.printStackTrace();

        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        }
    }

    /**
     * FileImportHandler is a simple FunctionalInterface that is invoked when a {@link FileImportTask} finishes
     * importing a file.
     */
    @FunctionalInterface
    public interface FileImportedHandler {
        void onFileImported(String title, String artist, String album, long duration, File path);
    }
}
