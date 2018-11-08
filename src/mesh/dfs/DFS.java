package mesh.dfs;

import mesh.impl.MeshNode;
import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.lib.Socket;
import net.reqres.Socketplexer;
import utils.RingBuffer;

import java.io.*;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.*;

// TODO: distributed file metadata file?
//          owner of first block(s) maintains meta data of file?

public class DFS {

    public static final File rootDirectory = new File("SpotyMusic/");
    public static final File blockDirectory = new File("SpotyMusic/dfs/");

    protected ExecutorService executor;

    private MeshNode mesh;

    protected ConcurrentHashMap<String, BlockDescriptor> blocks;

    public DFS(MeshNode mesh, ExecutorService executor) {
        this.mesh = mesh;
        this.executor = executor;

        this.blocks = new ConcurrentHashMap<>();

        this.executor.submit(this::init);
    }

    private void init() {
        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!blockDirectory.exists()) blockDirectory.mkdirs();

        // enumerate existing blocks
        this.executor.submit(this::enumerateBlocks);

        // register request handlers
        this.mesh.registerRequestHandler(REQUEST_READ_BLOCK, this::readBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_WRITE_BLOCK, this::writeBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_BLOCK_STATS, this::blockStatsHandler);
    }

    private void enumerateBlocks() {
        for (File f : blockDirectory.listFiles()) {
            try {
                BlockDescriptor descriptor = new BlockDescriptor(f);
                this.blocks.put(descriptor.getBlockName(), descriptor);

            } catch (Exception e) {
                System.err.println("[DFS][init] Exception while creating BlockDescriptor for block file: " + f.getName());
                System.err.println(e.getMessage());
            }
        }
    }

    private int getBestId(int block_id) {
        Set<Integer> nodes = this.mesh.getAvailableNodes();

        // find node with best matching id number
        int node_id = 0;
        for (int node : nodes) if (node > node_id && node < block_id) node_id = node;

        return node_id;
    }

    private void readBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            executor.submit(new ReadBlockRequestHandler(socketplexer, this.blocks.get(blockName), this));

        } else {
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen)-> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_READ_BLOCK);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    private void writeBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        this.executor.submit(new WriteBlockRequestHandler(socketplexer, block, this));

    }

    private void blockStatsHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            BlockDescriptor block = this.blocks.get(blockName);
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_SIZE, block.blockSize());
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_MODIFIED, block.lastModified());
                gen.writeEndObject();
            }));

        } else {
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    public Future<InputStream> requestFileBlock(BlockDescriptor block) {
        int node_id = getBestId(block.getBlockId());

        CompletableFuture<InputStream> future = new CompletableFuture<>();

        // if block is on this node, then just read it directly
        if (this.blocks.containsKey(block.getBlockName())) {
            BlockDescriptor localBlock = this.blocks.get(block.getBlockName());
            try {
                InputStream in = new BufferedInputStream(new FileInputStream(localBlock.getFile()));
                future.complete(in);

            } catch (FileNotFoundException e) {
                future.completeExceptionally(e);
            }

            return future;
        }

        Socket connection;

        try {
            connection = mesh.tryConnect(node_id);

        } catch (Exception e) {
            future.completeExceptionally(e);
            e.printStackTrace();
            return future;
        }

        Socketplexer plexer = new Socketplexer(connection, this.executor);

        // send request header
        this.executor.submit(new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(net.Constants.REQUEST_TYPE_PROPERTY, REQUEST_READ_BLOCK);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
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

    public Future<InputStream> getFileBlock(BlockDescriptor block, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            for (int i = 0; i < replication; i++) {
                block.setReplicaNumber(i);
                Future<InputStream> request = requestFileBlock(block);

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

    public Future<OutputStream> writeBlock(BlockDescriptor block) {
        int node_id = getBestId(block.getBlockId());

        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        if (this.blocks.containsKey(block.getBlockName())) {
            BlockDescriptor localBlock = this.blocks.get(block.getBlockName());
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(localBlock.getFile()));
                future.complete(out);

            } catch (FileNotFoundException e) {
                future.completeExceptionally(e);
            }

            return future;
        }

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
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
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

    public Future<JsonField.ObjectField> getBlockStats(BlockDescriptor block) {
        if (this.blocks.containsKey(block.getBlockName())) {
            BlockDescriptor localBlock = this.blocks.get(block.getBlockName());
            JsonField.ObjectField response = JsonField.emptyObject();
            response.setProperty(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE, localBlock.blockSize());
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_MODIFIED, localBlock.lastModified());
            return CompletableFuture.completedFuture(response);
        }

        CompletableFuture<JsonField.ObjectField> future = new CompletableFuture<>();

        int node_id = this.getBestId(block.getBlockId());

        Socket connection;

        try {
            connection = this.mesh.tryConnect(node_id);

        } catch (SocketException | NodeUnavailableException | SocketTimeoutException e) {
            System.err.println("[DFS][getBlockStats] Unable to connect to node " + node_id);
            e.printStackTrace();
            future.completeExceptionally(e);
            return future;
        }

        Socketplexer socketplexer = new Socketplexer(connection, this.executor);

        this.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_BLOCK_STATS);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
            gen.writeEndObject();
        }));

        this.executor.submit(() -> {
            try {
                InputStream in = socketplexer.waitInputChannel(1).get(2500, TimeUnit.MILLISECONDS);

                this.executor.submit(new JsonStreamParser(in, true, (JsonField field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField response = (JsonField.ObjectField) field;

                    if (!response.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                            !response.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_BLOCK_STATS)) {
                        future.completeExceptionally(new Exception("Bad response"));
                        return;
                    }

                    future.complete(response);
                }));

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                System.err.println("[DFS][getBlockSize] There was a problem opening the response channel");
                e.printStackTrace();
                future.completeExceptionally(e);
            }

        });

        return future;
    }

    public Future<InputStream> readFile(String fileName, int blockCount, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            RingBuffer buffer = new RingBuffer(Constants.MIN_BUFFERED_DATA);
            byte[] trx = new byte[1024 * 8];

            OutputStream buffer_out = buffer.getOutputStream();
            for (int i = 0; i < blockCount; i++) {
                if (future.isCancelled() || !buffer.isWriteOpened()) return;

                Future<InputStream> blockStream = getFileBlock(new BlockDescriptor(fileName, i), replication);

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
            for (int i = 0; i < replication; i++) outputs[i] = writeBlock(new BlockDescriptor(fileName, 0, i));

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
            int blockNumber = 0;
            int trx;
            try {
                while ((trx = in.read(trx_buffer, 0, (int) Math.min(trx_buffer.length, Constants.MAX_BLOCK_SIZE - written_block))) != -1) {
                    for (int i = 0; i < replication; i++) {
                        outputs[i].get().write(trx_buffer, 0, trx);
                    }
                    written_block += trx;

                    if (written_block >= Constants.MAX_BLOCK_SIZE) {
                        blockNumber++;
                        BlockDescriptor block = new BlockDescriptor(fileName, blockNumber);
                        for (int i = 0; i < replication; i++) {
                            outputs[i].get().close();
                            block.setReplicaNumber(i);
                            outputs[i] = writeBlock(block);
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

    public static final String REQUEST_BLOCK_STATS = "REQUEST_BLOCK_STATS";
    public static final String RESPONSE_BLOCK_STATS = "RESPONSE_BLOCK_STATS";
}
