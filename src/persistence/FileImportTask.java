package persistence;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * The FileImportTask handles importing an audio file from the file system into SpotyMusic's file structure.
 *
 * @author Nicholas Utz
 */
public class FileImportTask implements Runnable {

    private final File src;
    private File dest;

    private FileImportedHandler handler;

    private String title;
    private String artist;
    private String album;
    private FileImportProgressListener listener = null;

    /**
     * Creates a new FileImportTask that imports te given {@link File}, and invokes the given
     * {@link FileImportedHandler} when finished.
     *
     * @param file    the file to import
     * @param handler a handler to invoke when finished
     * @param title the title of the song stored in the file
     * @param artist the artist who wrote the song
     * @param album the album that the song was published in
     */
    public FileImportTask(File file, FileImportedHandler handler, String title, String artist, String album) {
        this.src = file;
        this.handler = handler;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    /**
     * Creates a new FileImportTask that imports the given {@link File}, that updates the given
     * {@link FileImportProgressListener} with progress updates, and invokes the given {@link FileImportedHandler}
     * when finished.
     *
     * @param file the file to import
     * @param handler a handler to notify when finished
     * @param title the title of the song stored in the file
     * @param artist the artist who wrote the song
     * @param album the album that the song was published in
     * @param listener a progress listener
     */
    public FileImportTask(File file, FileImportedHandler handler, String title, String artist, String album, FileImportProgressListener listener) {
        this(file, handler, title, artist, album);
        this.listener = listener;
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

            long duration = Math.round(format.getFrameLength() / format.getFormat().getFrameRate());

            this.dest = new File("SpotyMusic/Media/Artists/" + this.artist + "/" + this.album + "/" + this.title);

            this.dest.createNewFile();

            CheckedInputStream check = new CheckedInputStream(new FileInputStream(this.src), new CRC32());
            BufferedInputStream in = new BufferedInputStream(check);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(this.dest));
            byte[] buffer = new byte[4000];

            long toTransfer = this.src.length();
            if (this.listener != null) this.listener.onProgress(0, toTransfer);

            int bytes = 0;
            long total = 0;

            while ((bytes = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytes);
                total += bytes;
                if (this.listener != null) this.listener.onProgress(total, toTransfer);
            }
            in.close();
            out.close();

            this.handler.onFileImported(title, artist, album, duration, this.dest, check.getChecksum().getValue());

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
        void onFileImported(String title, String artist, String album, long duration, File path, long checksum);
    }

    /**
     * FileImportProgressListener is a FunctionalInterface that is sued to listed to progress updates on a
     * {@link FileImportTask}.
     */
    @FunctionalInterface
    public interface FileImportProgressListener {
        /**
         * Called when progress is made on a file import task.
         *
         * @param done number of bytes transferred
         * @param total total number of bytes to transfer
         */
        void onProgress(long done, long total);
    }
}
