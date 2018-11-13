package mesh.library;

import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.reqres.Socketplexer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class StreamSongRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private JsonField.ObjectField request;

    private MeshLibrary library;

    public StreamSongRequestHandler(Socketplexer socketplexer, JsonField.ObjectField request, MeshLibrary library) {
        this.socketplexer = socketplexer;
        this.request = request;
        this.library = library;
    }

    @Override
    public void run() {
        String songFileName = this.request.getStringProperty(MeshLibrary.PROPERTY_SONG_FILE_NAME);

        Future<InputStream> fin = this.library.dfs.readFile(songFileName);
        InputStream in;

        try {
            in = fin.get(7500, TimeUnit.MILLISECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                System.err.println("[StreamSongRequestHandler][run] DFS reported song file does not exist");
                this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.RESPONSE_STREAM_SONG);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                })));
                return;

            } else {
                System.err.println("[StreamSongRequestHandler][run] Unable to get song from DFS");
                e.printStackTrace();
                this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.RESPONSE_STREAM_SONG);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })));
                return;
            }
        }

        this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.RESPONSE_STREAM_SONG);
            gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
            gen.writeEndObject();
        })));

        try {
            OutputStream out = this.socketplexer.openOutputChannel(2);

            byte[] trx = new byte[1024 * 8];
            int trxd = 0;
            while ((trxd = in.read(trx, 0, trx.length)) != -1) {
                out.write(trx, 0, trxd);
            }

            in.close();
            out.close();

        } catch (IOException e) {
            System.err.println("[StreamSongRequestHandler][run] IOException while streaming song data");
            e.printStackTrace();

            try { in.close(); } catch (IOException e1) {}
            this.socketplexer.terminate();
        }
    }
}
