package mesh.library;

import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.reqres.Socketplexer;
import utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ImportSongRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private JsonField.ObjectField request;

    private MeshLibrary library;

    public ImportSongRequestHandler(Socketplexer socketplexer, JsonField.ObjectField request, MeshLibrary library) {
        this.socketplexer = socketplexer;
        this.request = request;
        this.library = library;
    }

    @Override
    public void run() {
        String songTitle = this.request.getStringProperty(MeshLibrary.PROPERTY_SONG_TITLE);
        String songArtist = this.request.getStringProperty(MeshLibrary.PROPERTY_SONG_ARTIST);
        String songAlbum = this.request.getStringProperty(MeshLibrary.PROPERTY_SONG_ALBUM);
        long songDuration = this.request.getLongProperty(MeshLibrary.PROPERTY_SONG_DURATION);

        String songFileName = Utils.hash(String.join(".", new String[]{songArtist, songAlbum, songTitle}), "MD5");

        OutputStream out = null;
        InputStream in = null;
        try {
            Future<OutputStream> fout = library.dfs.writeFile(songFileName, MeshLibrary.SONG_FILE_REPLICAS);
            out = fout.get(10, TimeUnit.SECONDS);

            this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(net.Constants.PROPERTY_RESPONSE_STATUS, MeshLibrary.RESPONSE_IMPORT_SONG);
                gen.writeStringField(net.Constants.PROPERTY_RESPONSE_STATUS, net.Constants.RESPONSE_STATUS_OK);
                gen.writeEndObject();
            }));

            Future<InputStream> fin = socketplexer.waitInputChannel(2);
            in = fin.get(1000, TimeUnit.MILLISECONDS);

            byte[] trx = new byte[1024 * 8];
            int trxd = 0;
            while ((trxd = in.read(trx, 0, trx.length)) != -1) {
                out.write(trx, 0, trxd);
            }

            in.close();
            out.close();

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("[ImportSongRequestHandler][run] Exception while trying to import song");
            e.printStackTrace();

            this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(net.Constants.PROPERTY_RESPONSE_STATUS, MeshLibrary.RESPONSE_IMPORT_SONG);
                gen.writeStringField(net.Constants.PROPERTY_RESPONSE_STATUS, net.Constants.RESPONSE_STATUS_SERVER_ERROR);
                gen.writeEndObject();
            }));
            return;

        } catch (IOException e) {
            System.err.println("[ImportSongRequestHandler][run] IOException while uploading song to DFS");
            e.printStackTrace();
            socketplexer.terminate();

            if (in != null) {
                try { in.close(); } catch (IOException e1) {}
            }

            if (out != null) {
                try { out.close(); } catch (IOException e1) {}
            }
            return;
        }

        // append song metadata to inverted index file
        StringBuilder builder = new StringBuilder();
        builder.append(songArtist); builder.append(';');
        builder.append(songAlbum); builder.append(';');
        builder.append(songTitle); builder.append(';');
        builder.append(songDuration); builder.append(';');
        builder.append(songFileName); builder.append('\n');
        builder.trimToSize();

        this.library.dfs.appendFile(MeshLibrary.INDEX_FILE_NAME, builder.toString().getBytes(), MeshLibrary.INDEX_FILE_REPLICAS);
    }
}
