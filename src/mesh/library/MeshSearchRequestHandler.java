package mesh.library;

import mesh.dfs.BlockDescriptor;
import net.Constants;
import net.common.AsyncJsonStreamGenerator;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.reqres.Socketplexer;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MeshSearchRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private JsonField.ObjectField request;

    private MeshLibrary library;

    public MeshSearchRequestHandler(Socketplexer socketplexer, JsonField.ObjectField request, MeshLibrary library) {
        this.socketplexer = socketplexer;
        this.request = request;
        this.library = library;
    }

    @Override
    public void run() {
        String searchParam = this.request.getStringProperty(MeshLibrary.PROPERTY_SEARCH_PARAMETER).trim().toLowerCase();

        List<BlockDescriptor> blocks = this.library.dfs.getLocalBlocks(MeshLibrary.INDEX_FILE_NAME);

        this.library.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.RESPONSE_SEARCH_MESH);
            gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
            gen.writeEndObject();
        }));

        OutputStream out = this.socketplexer.openOutputChannel(2);
        AsyncJsonStreamGenerator generator = new AsyncJsonStreamGenerator(out);
        this.library.executor.submit(generator);
        generator.enqueue((gen) -> gen.writeStartArray());

        for (BlockDescriptor block : blocks) {
            Future<InputStream> fin = this.library.dfs.getFileBlock(block, MeshLibrary.INDEX_FILE_REPLICAS);
            InputStream in;

            try {
                in = fin.get(2, TimeUnit.SECONDS);

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                System.err.println("[MeshSearchRequestHandler][run] Unable to read local index file block");
                e.printStackTrace();
                continue;
            }

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split(";");
                    for (String field : fields) {
                        if (field.trim().toLowerCase().startsWith(searchParam)) {
                            generator.enqueue((gen) -> {
                                gen.writeStartObject();
                                gen.writeStringField(MeshLibrary.PROPERTY_SONG_ARTIST, fields[0]);
                                gen.writeStringField(MeshLibrary.PROPERTY_SONG_ALBUM, fields[1]);
                                gen.writeStringField(MeshLibrary.PROPERTY_SONG_TITLE, fields[2]);
                                gen.writeStringField(MeshLibrary.PROPERTY_SONG_DURATION, fields[3]);
                                gen.writeStringField(MeshLibrary.PROPERTY_SONG_FILE_NAME, fields[4]);
                                gen.writeEndObject();
                            });
                        }
                    }
                }

                reader.close();

            } catch (IOException e) {
                System.err.println("[MeshSearchRequestHandler][run] IOException while reading inverted index file");
                e.printStackTrace();

                try { in.close(); } catch (IOException e1) {}
                continue;
            }
        }

        generator.enqueue((gen) -> gen.writeEndArray());
        generator.close();
    }
}
