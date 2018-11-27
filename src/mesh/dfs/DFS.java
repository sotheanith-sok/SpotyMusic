package mesh.dfs;

import mesh.impl.MeshNode;
import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.DeferredStreamJsonGenerator;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.lib.Socket;
import net.reqres.Socketplexer;
import persistence.ObservableMap;
import persistence.ObservableMapSerializer;
import utils.Logger;
import utils.RingBuffer;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DFS {

    public static final File rootDirectory = new File("SpotyMusic/");
    public static final File blockDirectory = new File("SpotyMusic/dfs/");
    public static final File fileIndex = new File("SpotyMusic/files.json");

    private Logger serverLog;

    private Logger clientLog;

    protected ScheduledExecutorService executor;

    private MeshNode mesh;

    protected ConcurrentHashMap<String, BlockDescriptor> blocks;

    protected ObservableMap<String, FileDescriptor> files;

    private AtomicBoolean blockOrganizerRunning;

    public DFS(MeshNode mesh, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.executor = executor;

        this.serverLog = new Logger("DFS][server", Constants.TRACE);
        this.clientLog = new Logger("DFS][client", Constants.DEBUG);

        this.blocks = new ConcurrentHashMap<>();
        this.files = new ObservableMap<>();

        this.blockOrganizerRunning = new AtomicBoolean(false);
    }

    public void init() {
        this.serverLog.debug("[init] Checking that directories exist");
        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!blockDirectory.exists()) blockDirectory.mkdirs();

        // enumerate existing blocks
        this.serverLog.debug("[init] Starting Block Enumerator");
        this.executor.submit(this::enumerateBlocks);
        // enumerate existing files
        this.serverLog.debug("[init] Starting File Enumerator");
        this.executor.submit(this::enumerateFiles);

        // register request handlers
        this.serverLog.debug("[init] Registering request handlers");
        this.mesh.registerRequestHandler(REQUEST_READ_BLOCK, this::readBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_WRITE_BLOCK, this::writeBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_BLOCK_STATS, this::blockStatsHandler);
        this.mesh.registerRequestHandler(REQUEST_FILE_METADATA, this::fileQueryHandler);
        this.mesh.registerRequestHandler(REQUEST_DELETE_BLOCK, this::deleteBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_PUT_FILE_METADATA, this::filePutHandler);
        this.mesh.registerRequestHandler(REQUEST_APPEND_BLOCK, this::appendBlockHandler);

        this.serverLog.debug("[init] Registering packet handlers");
        this.mesh.registerPacketHandler(COMMAND_DELETE_FILE, this::deleteFileHandler);
    }

    private void enumerateBlocks() {
        this.serverLog.finest("[enumerateBlocks] Enumerating blocks...");

        File[] files = blockDirectory.listFiles();
        if (files == null) {
            this.serverLog.finer("[enumerateBlocks] No blocks found");
            return;
        }

        this.serverLog.finer("[enumerateBlocks] Found " + files.length + " blocks");

        int count = 0;

        for (File f : files) {
            try {
                BlockDescriptor descriptor = new BlockDescriptor(f);
                this.blocks.put(descriptor.getBlockName(), descriptor);
                count++;

            } catch (Exception e) {
                this.serverLog.warn("[enumerateBlocks] Exception while creating BlockDescriptor for block file: " + f.getName());
                e.printStackTrace();
            }
        }

        this.serverLog.log("[enumerateBlocks] Enumerated " + count + " of " + files.length + " detected blocks");

        this.serverLog.debug("[enumerateBlocks] Starting block organizer");
        this.organizeBlocks();
        this.mesh.addNodeConnectListener(id -> this.organizeBlocks());
    }

    private void organizeBlocks() {
        if (this.blockOrganizerRunning.compareAndSet(false, true)) {
            this.serverLog.debug("[organizeBlocks] Submitting block organizer task");
            this.executor.submit(() -> {
                this.serverLog.log("[organiseBlocks] Block Organizer starting");
                if (this.mesh.getAvailableNodes().size() < 2) {
                    this.serverLog.log("[organizeBlocks] There are not enough connected nodes to reorganize blocks");
                    this.blockOrganizerRunning.set(false);
                    return;
                }

                byte[] trx = new byte[1024 * 8];
                for (BlockDescriptor block : this.blocks.values()) {
                    int blockId = block.getBlockId();
                    int bestId = this.getBestId(blockId);
                    this.serverLog.trace("[organizeBlocks] Block " + blockId + " best matches node " + bestId);

                    if (bestId != this.mesh.getNodeId() && this.mesh.getAvailableNodes().size() >= 2) {
                        this.serverLog.fine("[organizeBlocks] Block " + block.getBlockName() + " does not belong on this node");

                        InputStream in = null;
                        try {
                            Future<OutputStream> fout = this.writeBlock(block);
                            in = new FileInputStream(block.getFile());

                            OutputStream out = fout.get(5, TimeUnit.SECONDS);

                            int trxd = 0;
                            while ((trxd = in.read(trx, 0, trx.length)) != -1) {
                                out.write(trx, 0, trxd);
                            }

                            in.close();
                            out.close();

                            this.serverLog.finer("[organizeBlocks] Moved block " + block.getBlockName() + " to node " + bestId);

                            if (block.getBlockNumber() == 0) {
                                this.serverLog.finest("[organiseBlocks] Moved first block in file, copying file descriptor to destination node");
                                FileDescriptor descriptor = this.files.get(block.getFileName());
                                if (descriptor != null) {
                                    this.putFileMetadata(descriptor, bestId);
                                    this.files.remove(block.getFileName());
                                }
                            }

                        } catch (TimeoutException e) {
                            this.serverLog.warn("[enumerateBlocks] Timed out while trying to move block to another node");
                            e.printStackTrace();

                            if (in != null) {
                                try { in.close(); } catch (IOException e1) {}
                            }
                            break;

                        } catch (Exception e) {
                            this.serverLog.warn("[enumerateBlocks] Exception while trying to transfer block");
                            e.printStackTrace();
                            break;
                        }
                    }
                }

                this.serverLog.log("[organizeBlocks] All local blocks belong on this node");
                this.blockOrganizerRunning.set(false);
            });

        } else {
            this.serverLog.debug("[organizeBlocks] Block Organizer is already running");
        }
    }

    private void enumerateFiles() {
        this.serverLog.debug("[enumerateFiles] File Enumerator starting");
        if (fileIndex.exists()) {
            try {
                InputStream in = new BufferedInputStream(new FileInputStream(fileIndex));
                JsonStreamParser parser = new JsonStreamParser(in, true, (JsonField field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField object = (JsonField.ObjectField) field;
                    for (Map.Entry<String, JsonField> entry : object.getProperties().entrySet()) {
                        this.files.put(entry.getKey(), FileDescriptor.fromJson((JsonField.ObjectField) entry.getValue()));
                    }
                });
                parser.run();
                this.serverLog.log("[enumerateFiles] Enumerated " + this.files.size() + " files");

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        } else {
            this.serverLog.finest("[enumerateFiles] File index not found, creating file index");
            try {
                fileIndex.createNewFile();

            } catch (IOException e) {
                this.serverLog.warn("[enumerateFiles] Unable to create file index file");
                e.printStackTrace();
            }
        }

        new ObservableMapSerializer<>(this.files, fileIndex, (file, gen) -> file.serialize(gen), this.executor, 2500, TimeUnit.MILLISECONDS);
    }

    private int getBestId(int block_id) {
        Set<Integer> nodes = this.mesh.getAvailableNodes();

        // find node with best matching id number
        int node_id = 0;
        for (int node : nodes) if (node > node_id && node < block_id) node_id = node;

        //this.clientLog.debug("[getBestId] Best node_id for block_id " + block_id + " is " + node_id);
        return node_id;
    }

    private void readBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[readBlockHandler] Handling " + REQUEST_READ_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            executor.submit(new ReadBlockRequestHandler(socketplexer, this.blocks.get(blockName), this));

        } else {
            this.serverLog.warn("[readBlockHandler] Received request for non-existent block");
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen)-> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_READ_BLOCK);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    private void writeBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[writeBlockHandler] Handling " + REQUEST_WRITE_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        this.executor.submit(new WriteBlockRequestHandler(socketplexer, block,this));
    }

    private void appendBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[appendBlockHandler] Handling " + REQUEST_APPEND_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        this.executor.submit(new WriteBlockRequestHandler(socketplexer, block, this, true));
    }

    private void blockStatsHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[blockStatsHandler] Handling " + REQUEST_BLOCK_STATS + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            BlockDescriptor block = this.blocks.get(blockName);
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_SIZE, block.blockSize());
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_MODIFIED, block.lastModified());
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_NUMBER, block.getBlockNumber());
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_REPLICA, block.getReplicaNumber());
                gen.writeEndObject();
            }));

        } else {
            this.serverLog.warn("[blockStatsHandler] Received request for stats on non-existent block");
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    private void fileQueryHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[fileQueryHandler] Handling " + REQUEST_FILE_METADATA + " request");
        try {
            String fileName = request.getStringProperty(FileDescriptor.PROPERTY_FILE_NAME);
            if (this.files.containsKey(fileName)) {
                FileDescriptor file = this.files.get(fileName);
                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    file.serialize(gen, true);
                    gen.writeEndObject();
                }));

            } else {
                this.serverLog.warn("[fileQueryHandler] Received query for non-existent file");
                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                }));
            }

        } catch (Exception e) {
            this.serverLog.warn("[fileQueryHandler] Exception thrown while handling file query request");
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                gen.writeEndObject();
            }));
        }

    }

    private void filePutHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[filePutHandler] Handling " + REQUEST_PUT_FILE_METADATA + " request");
        executor.submit(() -> {
            try {
                FileDescriptor descriptor = FileDescriptor.fromJson(request);
                this.files.put(descriptor.getFileName(), descriptor);

                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_PUT_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeEndObject();
                }));

            } catch (Exception e) {
                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_PUT_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                }));
            }
        });
    }

    private void deleteBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[deleteBlockHandler] Handling " + REQUEST_DELETE_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            executor.submit(() -> {
                BlockDescriptor block = this.blocks.get(blockName);
                File file = block.getFile();
                if (!file.delete()) {
                    System.err.println("[DFS][deleteBlockHandler] There was a problem deleting the file block: " + blockName);
                }
                this.blocks.remove(blockName);

                // if first block in file, delete file metadata
                if (block.getBlockNumber() == 0) {
                    this.files.remove(block.getFileName());
                }

                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_DELETE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeEndObject();
                }));
            });

        } else {
            this.serverLog.info("[deleteBlockHandler] Received request to delete non-existent block");
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_DELETE_BLOCK);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    private void deleteFileHandler(JsonField.ObjectField packet, InetAddress source) {
        this.serverLog.finer("[deleteFileHandler] Handling " + COMMAND_DELETE_FILE + " command");
        this.executor.submit(() -> {
            String fileName = packet.getStringProperty(FileDescriptor.PROPERTY_FILE_NAME);
            this.files.remove(fileName);

            for (BlockDescriptor block : this.getLocalBlocks(fileName)) {
                this.serverLog.finest("[deleteFileHandler] Deleting block " + block.getBlockName());
                File file = block.getFile();
                if (!file.delete()) this.serverLog.warn("[deleteFileHandler] There was a problem deleting block " + block.getBlockName());
                this.blocks.remove(block.getBlockName());
            }
        });
    }

    public Future<InputStream> requestFileBlock(BlockDescriptor block) {
        this.clientLog.log("[requestFileBlock] Request for block " + block.getBlockName());
        int node_id = getBestId(block.getBlockId());
        this.clientLog.debug("[requestFileBlock] Block should be located on node " + node_id);

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

            this.clientLog.finer("[requestFileBlock] Request for block handled locally");
            return future;
        }

        Socket connection;

        this.clientLog.debug("[requestFileBlock] Connecting to node " + node_id + "...");
        try {
            connection = mesh.tryConnect(node_id);

        } catch (Exception e) {
            future.completeExceptionally(e);
            this.clientLog.warn("[requestFileBlock] Unable to connect to node " + node_id);
            e.printStackTrace();
            return future;
        }

        Socketplexer plexer = new Socketplexer(connection, this.executor);

        // send request header
        this.clientLog.finer("[requestFileBlock] Sending request header");
        this.executor.submit(new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(net.Constants.REQUEST_TYPE_PROPERTY, REQUEST_READ_BLOCK);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
            gen.writeEndObject();
        }));

        this.clientLog.finer("[requestFileBlock] Parsing response header");
        this.executor.submit(new JsonStreamParser(plexer.getInputChannel(1), true, (field) -> {
            if (!field.isObject()) return;

            JsonField.ObjectField header = (JsonField.ObjectField) field;
            if (header.containsKey(net.Constants.REQUEST_TYPE_PROPERTY) &&
                    header.getStringProperty(net.Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_READ_BLOCK)) {

                if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                    if (future.isCancelled()) plexer.terminate();
                    else future.complete(plexer.getInputChannel(2));
                    this.clientLog.log("[requestFileBlock] Downloading block from remote");

                } else if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_NOT_FOUND)) {
                    this.clientLog.log("[requestFileBlock] Remote node reported block not found");
                    future.completeExceptionally(new FileNotFoundException("Remote node said was file block not found"));
                }

            } else {
                this.clientLog.warn("[requestFileBlock] Malformed response header");
                plexer.terminate();
                future.completeExceptionally(new Exception("Malformed response header"));
            }
        }));

        return future;
    }

    // add logging
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
        return this.writeBlock(block, false);
    }

    // add logging
    public Future<OutputStream> writeBlock(BlockDescriptor block, boolean append) {
        this.clientLog.log("[writeBlock] Request to write block " + block.getBlockName() + " append=" + append);
        int node_id = getBestId(block.getBlockId());
        this.clientLog.debug("[writeBlock] Best node_id for block: " + node_id);

        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            if (node_id == this.mesh.getNodeId()) {
                this.clientLog.finer("[writeBlock] Block is located on local node");

                BlockDescriptor localBlock = this.blocks.getOrDefault(block.getBlockName(), block);
                this.blocks.putIfAbsent(localBlock.getBlockName(), localBlock);
                try {
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(localBlock.getFile(), append));
                    future.complete(out);
                    this.clientLog.finer("[writeBlock] Request handled locally");

                } catch (FileNotFoundException e) {
                    this.clientLog.warn("[writeBlock] There was a problem opening a local FileOutputStream");
                    e.printStackTrace();
                    future.completeExceptionally(e);
                }

                return;
            }

            Socket connection;

            this.clientLog.finer("[writeBlock] Attempting to connect to node " + node_id);
            try {
                connection = mesh.tryConnect(node_id);
                this.clientLog.finer("[writeBlock] Connected to remote node successfully");

            } catch (SocketTimeoutException | SocketException | NodeUnavailableException e) {
                future.completeExceptionally(e);
                this.clientLog.warn("[writeBlock] Unable to connect to remote node");
                e.printStackTrace();
                return;
            }

            this.clientLog.trace("[writeBlock] Creating Socketplexer");
            final Socketplexer plexer = new Socketplexer(connection, this.executor);

            this.clientLog.finest("[writeBlock] Sending request headers");
            this.executor.submit(new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, append ? REQUEST_APPEND_BLOCK : REQUEST_WRITE_BLOCK);
                gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
                gen.writeEndObject();
            }));

            this.clientLog.finest("[writeBlock] Parsing response headers");
            this.executor.submit(new JsonStreamParser(plexer.getInputChannel(1), true, (field) -> {
                if (!field.isObject()) return;

                JsonField.ObjectField packet = (JsonField.ObjectField) field;

                if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                        !packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(append ? RESPONSE_APPEND_BLOCK : RESPONSE_WRITE_BLOCK)) {
                    this.clientLog.warn("[writeBlock] Received bad response type");
                    future.completeExceptionally(new Exception("Bad response type"));
                }

                if (packet.containsKey(Constants.PROPERTY_RESPONSE_STATUS) &&
                        packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                    if (future.isCancelled()) {
                        plexer.terminate();
                        this.clientLog.fine("[writeBlock] Ready to write block, but request was canceled.");

                    } else {
                        future.complete(plexer.openOutputChannel(2));
                        this.clientLog.fine("[writeBlock] Ready to write block to remote node");
                    }

                } else {
                    this.clientLog.warn("[writeBlock] Received response code: " +
                            packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS));
                    future.completeExceptionally(new Exception("Received response code: " +
                            packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS)));
                }
            }));

        });

        return future;
    }

    public Future<JsonField.ObjectField> getBlockStats(BlockDescriptor block) {
        this.clientLog.log("[getBlockStats] Request for block statistics");

        if (this.blocks.containsKey(block.getBlockName())) {
            BlockDescriptor localBlock = this.blocks.get(block.getBlockName());
            JsonField.ObjectField response = JsonField.emptyObject();
            response.setProperty(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER, localBlock.getBlockNumber());
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_REPLICA, localBlock.getReplicaNumber());
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE, localBlock.blockSize());
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_MODIFIED, localBlock.lastModified());
            this.clientLog.fine("[getBlockStats] Request for block stats handled locally");
            return CompletableFuture.completedFuture(response);
        }

        CompletableFuture<JsonField.ObjectField> future = new CompletableFuture<>();

        int node_id = this.getBestId(block.getBlockId());
        this.clientLog.debug("[getBlockStats] Best node_id for block " + block.getBlockName() + " is " + node_id);

        Socket connection;

        this.clientLog.finer("[getBlockStats] Connecting to node " + node_id);
        try {
            connection = this.mesh.tryConnect(node_id);
            this.clientLog.finest("[getBlockStats] Connected to remote node successfully");

        } catch (SocketException | NodeUnavailableException | SocketTimeoutException e) {
            this.clientLog.warn("[getBlockStats] Unable to connect to node " + node_id);
            e.printStackTrace();
            future.completeExceptionally(e);
            return future;
        }

        Socketplexer socketplexer = new Socketplexer(connection, this.executor);

        this.clientLog.fine("[getBlockStats] Sending request header");
        this.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_BLOCK_STATS);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
            gen.writeEndObject();
        }));

        this.clientLog.fine("[getBlockStats] Parsing response header");
        this.executor.submit(() -> {
            try {
                InputStream in = socketplexer.waitInputChannel(1).get(2500, TimeUnit.MILLISECONDS);

                this.executor.submit(new JsonStreamParser(in, true, (JsonField field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField response = (JsonField.ObjectField) field;

                    if (!response.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                            !response.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_BLOCK_STATS)) {
                        future.completeExceptionally(new Exception("Bad response"));
                        this.clientLog.warn("[getBlockStats] Malformed response");
                        return;
                    }

                    future.complete(response);
                }));

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                this.clientLog.warn("[getBlockSize] There was a problem opening the response channel");
                e.printStackTrace();
                future.completeExceptionally(e);
            }

        });

        return future;
    }

    public Future<FileDescriptor> getFileStats(String fileName) {
        this.clientLog.log("[getFileStats] Request for file stats of file " + fileName);

        if (this.files.containsKey(fileName)) {
            this.clientLog.fine("[getFileStats] Request for file stats handled locally");
            return CompletableFuture.completedFuture(this.files.get(fileName));
        }

        CompletableFuture<FileDescriptor> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            BlockDescriptor firstBlock = new BlockDescriptor(fileName, 0, 0);
            this.clientLog.fine("[getFileStats] Searching for first block of file");

            Exception exc = null;

            for (int i = 0; i < this.mesh.getAvailableNodes().size(); i++) {
                firstBlock.setReplicaNumber(i);

                int node_id = this.getBestId(firstBlock.getBlockId());
                this.clientLog.debug("[getFileStats] best node_id for block is " + node_id);

                if (node_id == this.mesh.getNodeId()) {
                    if (this.files.containsKey(fileName)) {
                        future.complete(this.files.get(fileName));
                        this.clientLog.fine("[getFileStats] Request for file stats handled locally");
                        return;

                    } else {
                        this.clientLog.fine("[getFileStats] First block of file should have been here, but its not...");
                        continue;
                    }
                }

                Socket connection;

                this.clientLog.finest("[getFileStats] Connecting to node " + node_id);
                try {
                    connection = this.mesh.tryConnect(node_id);
                    this.clientLog.finest("[getFileStats] COnnected to remote node successfully");
                    exc = null;

                } catch (SocketException | SocketTimeoutException | NodeUnavailableException e) {
                    this.clientLog.warn("[getFileStats] Unable to connect to remote node");
                    e.printStackTrace();
                    exc = e;
                    continue;
                }

                Socketplexer socketplexer = new Socketplexer(connection, this.executor);

                this.clientLog.finest("[getFileStats] Sending request headers");
                this.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_FILE_METADATA);
                    gen.writeStringField(FileDescriptor.PROPERTY_FILE_NAME, fileName);
                    gen.writeEndObject();
                }));

                AtomicReference<JsonField.ObjectField> aresponse = new AtomicReference<>();

                try {
                    this.clientLog.finest("[getFileStats] Parsing response headers...");
                    JsonStreamParser parser = new JsonStreamParser(socketplexer.waitInputChannel(1).get(2500, TimeUnit.MILLISECONDS), true, (field) -> {
                        if (field.isObject()) {
                            aresponse.set((JsonField.ObjectField) field);
                        }
                    });
                    parser.run();

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    this.clientLog.warn("[getFileStats] Exception while processing response data");
                    e.printStackTrace();
                    exc = e;
                    socketplexer.terminate();
                    continue;
                }

                JsonField.ObjectField response = aresponse.get();
                if (response.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_NOT_FOUND)) {
                    this.clientLog.fine("[getFileStats] Remote node reported file not found");
                    exc = new FileNotFoundException("Remote reported no such file");
                    continue;

                } else if (response.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_SERVER_ERROR)) {
                    this.clientLog.fine("[getFileStats] Remote node encountered an unexpected error");
                    exc = new Exception("Remote had a problem processing the request");
                    continue;
                }

                this.clientLog.fine("[getFileStats] Successfully got file stats");
                future.complete(FileDescriptor.fromJson(response));
                break;
            }

            if (exc != null && !future.isDone()) {
                this.clientLog.warn("[getFileStats] There was a problem while retrieving file stats");
                exc.printStackTrace();
                future.completeExceptionally(exc);

            } else {
                this.clientLog.fine("[getFileStats] The requested file was not found");
                future.completeExceptionally(new FileNotFoundException("The requested file was not found"));
            }
        });

        return future;
    }

    // add logging
    public Future<InputStream> readFile(String fileName, int blockCount, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            RingBuffer buffer = new RingBuffer(1024 * 24);
            byte[] trx = new byte[1024 * 8];

            OutputStream buffer_out = buffer.getOutputStream();
            Future<InputStream> blockStream = getFileBlock(new BlockDescriptor(fileName, 0), replication);

            for (int i = 0; i < blockCount && buffer.isWriteOpened(); i++) {
                if (future.isCancelled() || !buffer.isWriteOpened()) return;

                try {
                    System.out.println("[DFS][readFile] Retrieving block " + i);

                    InputStream block = blockStream.get(2, TimeUnit.SECONDS);
                    if (!future.isDone() && !future.isCancelled()) future.complete(buffer.getInputStream());
                    if (i + 1 < blockCount) blockStream = getFileBlock(new BlockDescriptor(fileName, i + 1), replication);

                    //System.out.println("[DFS][readFile] Retrieved block " + i);

                    int read;
                    while ((read = block.read(trx, 0, trx.length)) != -1) {
                        if (buffer.isWriteOpened()) buffer_out.write(trx, 0, read);
                        else {
                            block.close();
                            blockStream.cancel(false);
                            break;
                        }
                        //System.out.println("[DFS][readFile] Read " + read + " bytes from DFS");
                    }

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

                } catch (TimeoutException e) {
                    System.err.println("[DFS][readFile] Timed out while trying to retrieve block " + i);
                    e.printStackTrace();
                    try { buffer_out.close(); } catch (IOException e1) {}
                    future.completeExceptionally(e);
                    break;
                }
            }

            try {
                buffer_out.close();
            } catch (IOException e) {}
        });

        return future;
    }

    // add logging
    public Future<InputStream> readFile(String fileName) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Future<FileDescriptor> file = getFileStats(fileName);

            FileDescriptor descriptor = null;

            try {
                descriptor = file.get(10, TimeUnit.SECONDS);

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                System.err.println("[DFS][readFile] Exception while fetching file metadata");
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            int blockCount = descriptor.getBlockCount();
            int replicas = descriptor.getReplicas();

            Future<InputStream> in = this.readFile(fileName, blockCount, replicas);
            try {
                future.complete(in.get(7500, TimeUnit.MILLISECONDS));

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[DFS][readFile] There was a problem getting the requested file");
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // add logging
    public Future<?> putFileMetadata(FileDescriptor fileDescriptor, int node_id) {
        CompletableFuture<String> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Socket connection;

            if (node_id == this.mesh.getNodeId()) {
                this.files.put(fileDescriptor.getFileName(), fileDescriptor);
                future.complete("Success");
                return;
            }

            try {
                connection = this.mesh.tryConnect(node_id);

            } catch (SocketException | NodeUnavailableException | SocketTimeoutException e) {
                System.err.println("[DFS][getBlockStats] Unable to connect to node " + node_id);
                e.printStackTrace();
                future.completeExceptionally(e);
                return;
            }

            Socketplexer socketplexer = new Socketplexer(connection, this.executor);

            this.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_PUT_FILE_METADATA);
                fileDescriptor.serialize(gen, true);
                gen.writeEndObject();
            }));

            try {
                this.executor.submit(new JsonStreamParser(socketplexer.waitInputChannel(1).get(5000, TimeUnit.MILLISECONDS), true, (field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField packet = (JsonField.ObjectField) field;
                    String status = packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS);
                    if (status.equals(Constants.RESPONSE_STATUS_OK)) {
                        future.complete(status);

                    } else {
                        future.completeExceptionally(new Exception("Server returned status code " + status));
                    }
                }));

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[DFS][putFileMetadata] Exception while parsing response header");
                e.printStackTrace();
            }
        });

        return future;
    }

    public Future<OutputStream> writeFile(String fileName, int replicas) {
        return this.writeFile(fileName, replicas, false);
    }

    // add logging
    public Future<OutputStream> writeFile(String fileName, int replicas, boolean tryAppend) {
        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        System.out.println("[DFS][writeFile][FINER] Request to write file " + fileName);

        this.executor.submit(() -> {
            int replication = replicas;
            boolean append = tryAppend;

            FileDescriptor descriptor = null;
            JsonField.ObjectField lastBlock = null;
            if (append) {
                Future<FileDescriptor> file = getFileStats(fileName);
                try {
                    descriptor = file.get(10, TimeUnit.SECONDS);
                    replication = descriptor.getReplicas();

                } catch (InterruptedException | TimeoutException e) {
                    System.err.println("[DFS][writeFile][append] Future related exception while fetching file metadata");
                    e.printStackTrace();
                    future.completeExceptionally(e);
                    return;

                } catch (ExecutionException e) {
                    if (e.getCause() instanceof FileNotFoundException) {
                        // cannot append, must create new file
                        append = false;

                    } else {
                        System.err.println("[DFS][writeFile][append] ExecutionException while fetching metadata");
                        e.printStackTrace();
                        future.completeExceptionally(e);
                        return;
                    }
                }
            }

            if (append) {
                Future<JsonField.ObjectField> block = getBlockStats(new BlockDescriptor(fileName, descriptor.getBlockCount() - 1));
                try {
                    lastBlock = block.get(10, TimeUnit.SECONDS);

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[DFS][writeFile][append] Exception while fetching last block metadata");
                    e.printStackTrace();
                    future.completeExceptionally(e);
                    return;
                }
            }

            RingBuffer buffer = new RingBuffer(1024 * 8);
            byte[] trx_buffer = new byte[1024 * 4];

            int blockNumber = append ? (int) lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER) : 0;

            Future<OutputStream>[] outputs = new Future[replication];
            for (int i = 0; i < replication; i++) outputs[i] = writeBlock(new BlockDescriptor(fileName, blockNumber, i), append);

            try {
                for (int i = 0; i < replication; i++) outputs[i].get();

            } catch (InterruptedException | ExecutionException e) {
                for (int i = 0; i < replication; i++) outputs[i].cancel(false);
                future.completeExceptionally(e);
                return;
            }

            future.complete(buffer.getOutputStream());

            InputStream in = buffer.getInputStream();
            long total_written = append ? descriptor.getTotalSize() : 0;
            long written_block = append ? lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE) : 0;
            int trx;
            try {
                while ((trx = in.read(trx_buffer, 0, (int) Math.min(trx_buffer.length, Constants.MAX_BLOCK_SIZE - written_block))) != -1) {
                    for (int i = 0; i < replication; i++) {
                        outputs[i].get().write(trx_buffer, 0, trx);
                    }
                    written_block += trx;
                    total_written += trx;

                    if (written_block >= Constants.MAX_BLOCK_SIZE) {
                        blockNumber++;
                        BlockDescriptor block = new BlockDescriptor(fileName, blockNumber);
                        for (int i = 0; i < replication; i++) {
                            outputs[i].get().close();
                            block.setReplicaNumber(i);
                            outputs[i] = writeBlock(block, append);
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
                e.printStackTrace();
            }

            FileDescriptor metadata = new FileDescriptor(fileName, total_written, blockNumber + 1, replication);
            for (int i = 0; i < replication; i++) {
                BlockDescriptor block = new BlockDescriptor(fileName, 0, i);
                int id = this.getBestId(block.getBlockId());
                this.putFileMetadata(metadata, id);
            }

        });

        return future;
    }

    public Future<OutputStream> appendFile(String fileName) {
        return this.writeFile(fileName, 0, true);
    }

    // add logging
    public void appendFile(String fileName, byte[] data, int replication) {
        this.executor.submit(() -> {
            boolean create = false;
            FileDescriptor descriptor = null;
            JsonField.ObjectField lastBlock = null;
            Future<FileDescriptor> file = getFileStats(fileName);
            try {
                descriptor = file.get(10, TimeUnit.SECONDS);

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                if (e.getCause() instanceof FileNotFoundException) {
                    create = true;

                } else {
                    System.err.println("[DFS][writeFile][append] Exception while fetching file metadata");
                    e.printStackTrace();
                    return;
                }
            }

            if (!create) {
                Future<JsonField.ObjectField> block = getBlockStats(new BlockDescriptor(fileName, descriptor.getBlockCount() - 1));
                try {
                    lastBlock = block.get(10, TimeUnit.SECONDS);

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[DFS][writeFile][append] Exception while fetching last block metadata");
                    e.printStackTrace();
                    return;
                }
            }

            int blockNumber = create ? 0 : (int) lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER);
            int replicas = create ? replication : descriptor.getReplicas();

            Future<OutputStream>[] outputs = new Future[replicas];
            if (!create && lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE) + data.length > Constants.MAX_BLOCK_SIZE) {
                blockNumber++;
            }

            for (int i = 0; i < replicas; i++) outputs[i] = writeBlock(new BlockDescriptor(fileName, blockNumber, i), true);

            for (int i = 0; i < replicas; i++) {
                try {
                    OutputStream out = outputs[i].get(1500, TimeUnit.MILLISECONDS);
                    out.write(data);
                    out.close();

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[DFS][appendFile][data] Exception while trying to get output stream to replica " + i);
                    e.printStackTrace();

                } catch (IOException e) {
                    System.err.println("[DFS][appendFile][data] IOException while writing data to replica " + i);
                    e.printStackTrace();
                }
            }

            FileDescriptor metadata = new FileDescriptor(fileName, (create ? 0 : descriptor.getTotalSize()) + data.length, blockNumber + 1, replicas);
            for (int i = 0; i < replicas; i++) {
                BlockDescriptor blockDescriptor= new BlockDescriptor(fileName, 0, i);
                int id = this.getBestId(blockDescriptor.getBlockId());
                this.putFileMetadata(metadata, id);
            }
        });
    }

    public void deleteFile(String fileName) {
        this.clientLog.log("[deleteFile] Request to delete file " + fileName);
        this.clientLog.finer("[deleteFile] Sending command to delete file");
        this.mesh.broadcastPacket((gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, COMMAND_DELETE_FILE);
            gen.writeStringField(FileDescriptor.PROPERTY_FILE_NAME, fileName);
            gen.writeEndObject();
        });

        this.clientLog.finer("[deleteFile] Deleting local parts of file");
        this.files.remove(fileName);
        for (BlockDescriptor block : this.getLocalBlocks(fileName)) {
            this.clientLog.finest("[deleteFile] Deleting block " + block.getBlockName());
            File file = block.getFile();
            if (!file.delete()) this.clientLog.warn("[deleteFile] There was a problem deleting block " + block.getBlockName());
            this.blocks.remove(block.getBlockName());
        }
    }

    public List<BlockDescriptor> getLocalBlocks(String fileName) {
        ArrayList<BlockDescriptor> blocks = new ArrayList<>();

        blocks:
        for (BlockDescriptor block : this.blocks.values()) {
            if (block.getFileName().equals(fileName)) {
                for (BlockDescriptor block1 : blocks) if (block1.getBlockNumber() == block.getBlockNumber()) continue blocks;
                blocks.add(block);
            }
        }

        return blocks;
    }

    public Future<Boolean> fileExists(String fileName) {
        this.clientLog.log("[fileExists] Request to check for existence of file " + fileName);
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Future<FileDescriptor> desc = this.getFileStats(fileName);

            this.clientLog.finer("[fileExists] Requesting file stats...");
            try {
                desc.get(9, TimeUnit.SECONDS);
                this.clientLog.fine("[fileExists] Received file stats, file exists");
                future.complete(true);

            } catch (InterruptedException | TimeoutException e) {
                this.clientLog.warn("[fileExists] There was a problem while retrieving file stats");
                e.printStackTrace();
                future.completeExceptionally(e);

            } catch (ExecutionException e) {
                this.clientLog.fine("[fileExists] ExecutionException while retrieving file stats, file does not exist");
                future.complete(false);
            }
        });

        return future;
    }

    public static final String PROPERTY_BLOCK_NAME = "PROP_BLOCK_NAME";

    public static final String REQUEST_READ_BLOCK = "REQUEST_READ_BLOCK";
    public static final String RESPONSE_READ_BLOCK = "RESPONSE_READ_BLOCK";

    public static final String REQUEST_WRITE_BLOCK = "REQUEST_WRITE_BLOCK";
    public static final String RESPONSE_WRITE_BLOCK = "RESPONSE_WRITE_BLOCK";
    public static final String REQUEST_APPEND_BLOCK = "REQUEST_APPEND_BLOCK";
    public static final String RESPONSE_APPEND_BLOCK = "RESPONSE_APPEND_BLOCK";

    public static final String REQUEST_BLOCK_STATS = "REQUEST_BLOCK_STATS";
    public static final String RESPONSE_BLOCK_STATS = "RESPONSE_BLOCK_STATS";

    public static final String REQUEST_PUT_FILE_METADATA = "REQUEST_PUT_FILE_METADATA";
    public static final String RESPONSE_PUT_FILE_METADATA = "RESPONSE_PUT_FILE_METADATA";
    public static final String REQUEST_FILE_METADATA = "REQUEST_FILE_METADATA";
    public static final String RESPONSE_FILE_METADATA = "RESPONSE_FILE_METADATA";

    public static final String REQUEST_DELETE_BLOCK = "REQUEST_DELETE_BLOCK";
    public static final String RESPONSE_DELETE_BLOCK = "RESPONSE_DELETE_BLOCK";

    public static final String COMMAND_DELETE_FILE = "CMD_DELETE_FILE";
}
