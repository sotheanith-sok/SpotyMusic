package mesh.dfs;

import mesh.impl.MeshNode;
import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.reqres.Socketplexer;
import utils.RingBuffer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class DFS {

    private ExecutorService executor;

    private MeshNode mesh;

    public DFS(MeshNode mesh, ExecutorService executor) {
        this.mesh = mesh;
        this.executor = executor;


    }

    private int getBestId(int block_id) {
        Set<Integer> nodes = this.mesh.getAvailableNodes();

        // find node with best matching id number
        int node_id = 0;
        for (int node : nodes) if (node > node_id && node < block_id) node_id = node;

        return node_id;
    }

    public Future<InputStream> requestFileBlock(String fileName, int block_number, int replica) {
        String blockName = (fileName + "." + block_number + "." + replica);
        int block_id = blockName.hashCode();
        int node_id = getBestId(block_id);

        CompletableFuture<InputStream> future = new CompletableFuture<>();

        Socketplexer plexer0;
        try {
            plexer0 = new Socketplexer(mesh.tryConnect(node_id), this.executor);

        } catch (Exception e) {
            future.completeExceptionally(e);
            e.printStackTrace();
            return future;
        }

        final Socketplexer plexer = plexer0;

        // send request header
        this.executor.submit(new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(net.Constants.REQUEST_TYPE_PROPERTY, REQUEST_READ_BLOCK);
            gen.writeStringField(PROPERTY_BLOCK_NAME, blockName);
            gen.writeEndObject();
        }));

        this.executor.submit(new JsonStreamParser(plexer.getInputChannel(1), true, (field) -> {
            if (!field.isObject()) return;

            JsonField.ObjectField header = (JsonField.ObjectField) field;
            if (header.containsKey(net.Constants.REQUEST_TYPE_PROPERTY) &&
                    header.getStringProperty(net.Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_READ_BLOCK)) {

                if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                    if (future.isCancelled()) plexer.terminate();
                    else future.complete(plexer.getInputChannel(2));

                } else if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_NOT_FOUND)) {
                    future.completeExceptionally(new FileNotFoundException("Remote node said was file block not found"));
                }

            } else {
                System.err.println("[ReadBlockRequest] Malformed response header");
                plexer.terminate();
                future.completeExceptionally(new Exception("Malformed response header"));
            }
        }));

        return future;
    }

    public Future<InputStream> requestFileBlock(String fileName, int block_number) {
        return requestFileBlock(fileName, block_number, 0);
    }

    public Future<InputStream> getFileBlock(String fileName, int block_number, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            for (int i = 0; i < replication; i++) {
                Future<InputStream> request = requestFileBlock(fileName, block_number, i);

                try {
                    future.complete(request.get());

                } catch (InterruptedException e) {
                    System.err.println("[DFS][getFileBlock] Interrupted while waiting for file block request");
                    e.printStackTrace();
                    future.completeExceptionally(e);
                    return;

                } catch (ExecutionException e) {
                    // this should happen if the block isn't found or the node isn't connected
                }

                if (future.isCancelled()) return;
            }

            future.completeExceptionally(new Exception("Unable to locate file block"));
        });

        return future;
    }

    public Future<OutputStream> writeBlock(String fileName, int block_number, int replica) {
        String block_name = fileName + "." + block_number + "." + replica;
        int block_id = block_name.hashCode();
        int node_id = getBestId(block_id);

        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        Socketplexer plexer0;
        try {
            plexer0 = new Socketplexer(mesh.tryConnect(node_id), this.executor);

        } catch (SocketTimeoutException | SocketException | NodeUnavailableException e) {
            future.completeExceptionally(e);
            e.printStackTrace();
            return future;
        }

        final Socketplexer plexer = plexer0;

        this.executor.submit(new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_WRITE_BLOCK);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block_name);
            gen.writeEndObject();
        }));

        this.executor.submit(new JsonStreamParser(plexer.getInputChannel(1), true, (field) -> {
            if (!field.isObject()) return;

            JsonField.ObjectField packet = (JsonField.ObjectField) field;

            if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                    !packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_WRITE_BLOCK)) {
                System.err.println("[DFS][writeBlock] Received bad response type");
                future.completeExceptionally(new Exception("Bad response type"));
            }

            if (packet.containsKey(Constants.PROPERTY_RESPONSE_STATUS) &&
                    packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                if (future.isCancelled()) plexer.terminate();
                else future.complete(plexer.openOutputChannel(2));

            } else {
                System.err.println("[DFS][writeBlock] Received response code: " +
                        packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS));
                future.completeExceptionally(new Exception("Received response code: " +
                        packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS)));
            }
        }));

        return future;
    }

    public Future<OutputStream> writeBlock(String fileName, int block_number) {
        return this.writeBlock(fileName, block_number, 0);
    }

    public Future<InputStream> readFile(String fileName, int blockCount, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            RingBuffer buffer = new RingBuffer(Constants.MIN_BUFFERED_DATA);
            byte[] trx = new byte[1024 * 8];

            OutputStream buffer_out = buffer.getOutputStream();
            for (int i = 0; i < blockCount; i++) {
                if (future.isCancelled() || !buffer.isWriteOpened()) return;

                Future<InputStream> blockStream = getFileBlock(fileName, i, replication);

                try {
                    InputStream block = blockStream.get();
                    if (!future.isDone() && !future.isCancelled()) future.complete(buffer.getInputStream());

                    int read;
                    while ((read = block.read(trx, 0, trx.length)) != -1) buffer_out.write(trx, 0, read);

                    block.close();

                } catch (InterruptedException e) {
                    System.err.println("[DFS][readFile] Interrupted while waiting for file block");
                    if (!future.isDone()) future.completeExceptionally(e);
                    try {
                        buffer.getOutputStream().close();
                    } catch (IOException e1) {}

                } catch (ExecutionException e) {
                    e.printStackTrace();
                    if (!future.isDone()) future.completeExceptionally(e);
                    try {
                        buffer.getOutputStream().close();
                    } catch (IOException e1) {}

                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        buffer.getOutputStream().close();
                    } catch (IOException e1) {}
                }
            }

            try {
                buffer_out.close();
            } catch (IOException e) {}
        });

        return future;
    }

    public Future<OutputStream> writeStream(String fileName, int replication) {
        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            RingBuffer buffer = new RingBuffer(1024 * 8);
            byte[] trx_buffer = new byte[1024 * 4];

            Future<OutputStream>[] outputs = new Future[replication];
            for (int i = 0; i < replication; i++) outputs[i] = writeBlock(fileName, 0, i);

            try {
                for (int i = 0; i < replication; i++) outputs[i].get();

            } catch (InterruptedException | ExecutionException e) {
                for (int i = 0; i < replication; i++) outputs[i].cancel(false);
                future.completeExceptionally(e);
                return;

            }

            future.complete(buffer.getOutputStream());

            InputStream in = buffer.getInputStream();
            long written_block = 0;
            int trx;
            try {
                while ((trx = in.read(trx_buffer, 0, (int) Math.min(trx_buffer.length, Constants.MAX_BLOCK_SIZE - written_block))) != -1) {
                    for (int i = 0; i < replication; i++) {
                        outputs[i].get().write(trx_buffer, 0, trx);
                    }
                    written_block += trx;

                    if (written_block >= Constants.MAX_BLOCK_SIZE) {
                        for (int i = 0; i < replication; i++) {
                            outputs[i].get().close();
                            outputs[i] = writeBlock(fileName, 0, i);
                        }
                        written_block = 0;
                    }
                }

                for (int i = 0; i < replication; i++) outputs[i].get().close();

            } catch (IOException | InterruptedException | ExecutionException e) {
                for (int i = 0; i < replication; i++) {
                    if (outputs[i].isDone()) {
                        try {
                            outputs[i].get().close();
                        } catch (IOException | ExecutionException | InterruptedException e1) {
                            System.err.println("[DFS][writeStream] Exception while closing output");
                            e1.printStackTrace();
                        }
                    }
                    else outputs[i].cancel(false);
                }
                try {
                    in.close();
                } catch (IOException e1) {}
            }
        });

        return future;
    }

    public static final String PROPERTY_BLOCK_NAME = "PROP_BLOCK_NAME";
    public static final String PROPERTY_VERSION_NUMBER = "PROP_VERSION_NUMBER";

    public static final String REQUEST_READ_BLOCK = "REQUEST_READ_BLOCK";
    public static final String RESPONSE_READ_BLOCK = "RESPONSE_READ_BLOCK";

    public static final String REQUEST_WRITE_BLOCK = "REQUEST_WRITE_BLOCK";
    public static final String RESPONSE_WRITE_BLOCK = "RESPONSE_WRITE_BLOCK";

}
