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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DFS {

    public static final File rootDirectory = new File("SpotyMusic/");
    public static final File blockDirectory = new File("SpotyMusic/dfs/");
    public static final File fileIndex = new File("SpotyMusic/files.json");

    private Logger serverLog;

    private Logger clientLog;

    private Logger blockOrganizerLog;

    protected ScheduledExecutorService executor;

    private MeshNode mesh;

    protected ConcurrentHashMap<String, BlockDescriptor> blocks;

    protected ObservableMap<String, FileDescriptor> files;

    private AtomicBoolean blockOrganizerRunning;

    public DFS(MeshNode mesh, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.executor = executor;

        this.serverLog = new Logger("DFS][server", Constants.LOG);
        this.clientLog = new Logger("DFS][client", Constants.LOG);
        this.blockOrganizerLog = new Logger("DFS][organizeBlocks", Constants.LOG);

        this.blocks = new ConcurrentHashMap<>();
        this.files = new ObservableMap<>();

        this.blockOrganizerRunning = new AtomicBoolean(false);
    }

    public void init() {
        this.serverLog.debug("[init] Checking that directories exist");
        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!blockDirectory.exists()) blockDirectory.mkdirs();

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

        // enumerate existing blocks
        this.serverLog.debug("[init] Starting Block Enumerator");
        this.executor.submit(this::enumerateBlocks);
        // enumerate existing files
        this.serverLog.debug("[init] Starting File Enumerator");
        this.executor.submit(this::enumerateFiles);

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
            this.blockOrganizerLog.debug(" Submitting block organizer task");
            this.executor.submit(() -> {
                this.blockOrganizerLog.log(" Block Organizer starting");
                if (this.mesh.getAvailableNodes().size() < 2) {
                    this.blockOrganizerLog.log(" There are not enough connected nodes to reorganize blocks");
                    this.blockOrganizerRunning.set(false);
                    return;
                }

                byte[] trx = new byte[1024 * 8];
                for (BlockDescriptor block : Collections.unmodifiableCollection(this.blocks.values())) {
                    int blockId = block.getBlockId();
                    int bestId = this.getBestId(blockId);
                    this.blockOrganizerLog.finer(" Block " + blockId + " best matches node " + bestId);

                    if (bestId != this.mesh.getNodeId() && this.mesh.getAvailableNodes().size() >= 2) {
                        this.blockOrganizerLog.fine(" Block " + block.getBlockName() + " does not belong on this node");

                        this.blocks.remove(block.getBlockName());

                        this.blockOrganizerLog.finer(" Checking for block on remote");

                        Future<JsonField.ObjectField> statsFuture = this.getBlockStats(block);
                        JsonField.ObjectField stats;

                        try {
                            stats = statsFuture.get(3500, TimeUnit.MILLISECONDS);

                        } catch (InterruptedException | TimeoutException e) {
                            statsFuture.cancel(false);
                            this.blockOrganizerLog.warn(" Unable to retrieve block stats");
                            e.printStackTrace();
                            this.blocks.put(block.getBlockName(), block);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e1) {
                            }
                            //continue;
                            break;

                        } catch (ExecutionException e) {
                            this.blockOrganizerLog.error(" ExecutionException while trying to retrieve block stats");
                            e.printStackTrace();
                            this.blocks.put(block.getBlockName(), block);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e1) {
                            }
                            //continue;
                            break;
                        }

                        if (stats.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                            if (stats.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE) == block.blockSize()) {
                                this.blockOrganizerLog.log(" Remote has block, sizes match");
                                block.getFile().delete();
                                continue;

                            } else {
                                if (stats.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_MODIFIED) < block.lastModified()) {
                                    this.blockOrganizerLog.log(" Remote has block, size does not match, local is newer");

                                } else {
                                    this.blockOrganizerLog.log(" Remote has block, size does not match, remote is newer");
                                    // TODO: when working reliably, delete local copy of block
                                    continue;
                                }
                            }
                        } else {
                            this.blockOrganizerLog.log(" Remote reported block not found");
                        }

                        InputStream in = null;
                        Future<OutputStream> fout = this.writeBlock(block);
                        OutputStream out = null;
                        try {
                            this.blockOrganizerLog.finest(" Opening FileInputStream");
                            in = new BufferedInputStream(new FileInputStream(block.getFile()));
                            this.blockOrganizerLog.debug(" Opened FileInputStream");

                            this.blockOrganizerLog.finest(" Getting WriteBlock request upload stream");
                            out = fout.get(2500, TimeUnit.MILLISECONDS);
                            this.blockOrganizerLog.debug(" Got upload stream");

                            int trxd = 0;
                            long total = 0;
                            this.blockOrganizerLog.trace(" Reading from local file");
                            while ((trxd = in.read(trx, 0, trx.length)) != -1) {
                                this.blockOrganizerLog.trace(" Read " + trxd + " from local file");
                                out.write(trx, 0, trxd);
                                this.blockOrganizerLog.println(5, " Wrote " + trxd + " bytes to socket");
                                total += trxd;
                                this.blockOrganizerLog.trace(" Reading from local file");
                            }

                            this.blockOrganizerLog.debug(" Closing file and upload streams");
                            in.close();
                            out.close();

                            this.blockOrganizerLog.finer(" Uploaded " + total + " bytes of block data");

                            try {
                                // give the remote some time to process
                                Thread.sleep(250);
                            } catch (InterruptedException e) {}

                            this.blockOrganizerLog.finest(" Confirming existence of block on remote");

                            statsFuture = this.getBlockStats(block);
                            try {
                                stats = statsFuture.get(5000, TimeUnit.MILLISECONDS);
                                if (stats.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                                    long size = stats.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE);
                                    if (size != block.blockSize()) {
                                        this.blockOrganizerLog.info(" Uploaded block is not complete");
                                        //continue;
                                        break;

                                    } else {
                                        this.blockOrganizerLog.info(" Block transferred successfully");
                                    }

                                } else {
                                    this.blockOrganizerLog.info(" Remote reported block not found");
                                    //continue;
                                    break;
                                }

                            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                                statsFuture.cancel(false);
                                this.blockOrganizerLog.warn(" Unable to retrieve stats of uploaded block");
                                e.printStackTrace();
                                this.blocks.put(block.getBlockName(), block);
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e1) {
                                }
                                //continue;
                                break;
                            }

                            //this.blockOrganizerLog.finer(" Copied block " + block.getBlockName() + " data to node " + bestId);

                            if (block.getBlockNumber() == 0) {
                                this.blockOrganizerLog.finer(" Moved first block in file, copying file descriptor to destination node");
                                FileDescriptor descriptor = this.files.get(block.getFileName());
                                if (descriptor != null) {
                                    Future<?> future = this.putFileMetadata(descriptor, bestId);
                                    try {
                                        future.get(1500, TimeUnit.MILLISECONDS);
                                    } catch (TimeoutException | InterruptedException | ExecutionException e) {
                                        future.cancel(false);
                                        this.blockOrganizerLog.error(" Exception while sending file descriptor");
                                        e.printStackTrace();
                                    }
                                    this.files.remove(block.getFileName());
                                }
                            }

                            this.blockOrganizerLog.log(" Block " + block.getBlockName() + " transferred to " + bestId);

                            if (block.getFile().delete())
                                this.blockOrganizerLog.fine(" Local block deleted");
                            else
                                this.blockOrganizerLog.info(" Unable to delete local copy of block");

                        } catch (TimeoutException e) {
                            fout.cancel(false);
                            this.blockOrganizerLog.warn(" Timed out while trying to move block to another node");
                            e.printStackTrace();

                            try {
                                in.close();
                            } catch (IOException e1) {
                            }
                            break;

                        } catch (IOException e) {
                            this.blockOrganizerLog.error(" IOException while transferring block");
                            e.printStackTrace();

                            if (in != null) try {
                                in.close();
                            } catch (IOException e1) {
                            }
                            if (out != null) try {
                                out.close();
                            } catch (IOException e1) {
                            }
                            break;

                        } catch (Exception e) {
                            this.blockOrganizerLog.error(" Exception while trying to transfer block");
                            e.printStackTrace();
                            break;
                        }

                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                        }
                    }
                }

                this.blockOrganizerLog.log(" All local blocks belong on this node");
                this.blockOrganizerRunning.set(false);
            });

        } else {
            this.blockOrganizerLog.debug(" Block Organizer is already running");
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
        int node_id = nodes.iterator().next();
        for (int node : nodes) if (node > node_id && node < block_id) node_id = node;

        //this.clientLog.debug("[getBestId] Best node_id for block_id " + block_id + " is " + node_id);
        return node_id;
    }

    private void readBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[readBlockHandler] Handling " + REQUEST_READ_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
            (new ReadBlockRequestHandler(socketplexer, this.blocks.get(blockName), this)).run();

        } else {
            this.serverLog.warn("[readBlockHandler] Received request for non-existent block");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_READ_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e) {
                this.serverLog.warn("[readBlockHandler] Unable to obtain response header output stream");
                socketplexer.terminate();
            }
        }
    }

    private void writeBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[writeBlockHandler] Handling " + REQUEST_WRITE_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        (new WriteBlockRequestHandler(socketplexer, block, this)).run();
    }

    private void appendBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[appendBlockHandler] Handling " + REQUEST_APPEND_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        (new WriteBlockRequestHandler(socketplexer, block, this, true)).run();
    }

    private void blockStatsHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        this.serverLog.log("[blockStatsHandler] Request for stats of block " + blockName);
        if (this.blocks.containsKey(blockName)) {
            BlockDescriptor block = this.blocks.get(blockName);
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_SIZE, block.blockSize());
                    gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_MODIFIED, block.lastModified());
                    gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_NUMBER, block.getBlockNumber());
                    gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_REPLICA, block.getReplicaNumber());
                    gen.writeEndObject();
                })).run();
                this.serverLog.log("[blockStatsHandler] Request handled");

            } catch (IOException e) {
                this.serverLog.warn("[blockStatsHandler] Unable to obtain response stream");
                socketplexer.terminate();
            }

        } else {
            this.serverLog.info("[blockStatsHandler] Received request for stats on non-existent block");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_BLOCK_STATS);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                })).run();
                this.serverLog.log("[blockStatsHandler] Request handled");

            } catch (IOException e) {
                this.serverLog.warn("[blockStatsHandler] Unable to obtain response stream");
                socketplexer.terminate();
            }
        }
    }

    private void fileQueryHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[fileQueryHandler] Handling " + REQUEST_FILE_METADATA + " request");
        try {
            String fileName = request.getStringProperty(FileDescriptor.PROPERTY_FILE_NAME);
            if (this.files.containsKey(fileName)) {
                FileDescriptor file = this.files.get(fileName);
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    file.serialize(gen, true);
                    gen.writeEndObject();
                })).run();

            } else {
                this.serverLog.warn("[fileQueryHandler] Received query for non-existent file");
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                })).run();
            }

        } catch (Exception e) {
            this.serverLog.warn("[fileQueryHandler] Exception thrown while handling file query request");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e1) {
                this.serverLog.warn("[fileQueryHandler] Unable to obtain response stream");
                socketplexer.terminate();
            }
        }

    }

    private void filePutHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[filePutHandler] Handling " + REQUEST_PUT_FILE_METADATA + " request");
        try {
            FileDescriptor descriptor = FileDescriptor.fromJson(request);
            this.files.put(descriptor.getFileName(), descriptor);

            (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_PUT_FILE_METADATA);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                gen.writeEndObject();
            })).run();

        } catch (Exception e) {
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_PUT_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e1) {
                this.serverLog.warn("[filePutHandler] Unable to obtain response stream");
            }
        }
    }

    private void deleteBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        this.serverLog.finer("[deleteBlockHandler] Handling " + REQUEST_DELETE_BLOCK + " request");
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        if (this.blocks.containsKey(blockName)) {
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

            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_DELETE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e) {
                this.serverLog.warn("[deleteBlockHandler] Unable to obtain response stream");
                socketplexer.terminate();
            }

        } else {
            this.serverLog.info("[deleteBlockHandler] Received request to delete non-existent block");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_DELETE_BLOCK);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e) {
                this.serverLog.warn("[deleteBlockHandler] Unable to obtain response stream");
                socketplexer.terminate();
            }
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
                if (!file.delete())
                    this.serverLog.warn("[deleteFileHandler] There was a problem deleting block " + block.getBlockName());
                this.blocks.remove(block.getBlockName());
            }
        });
    }

    public Future<InputStream> requestFileBlock(BlockDescriptor block) {
        this.clientLog.log("[requestFileBlock] Request for block " + block.getBlockName());
        int node_id = getBestId(block.getBlockId());
        this.clientLog.debug("[requestFileBlock] Block should be located on node " + node_id);

        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
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
                return;
            }

            Socket connection;

            this.clientLog.debug("[requestFileBlock] Connecting to node " + node_id + "...");
            try {
                connection = mesh.tryConnect(node_id);

            } catch (Exception e) {
                future.completeExceptionally(e);
                this.clientLog.error("[requestFileBlock] Unable to connect to node " + node_id);
                e.printStackTrace();
                return;
            }

            Socketplexer plexer = new Socketplexer(connection, this.executor);

            // send request header
            this.clientLog.finer("[requestFileBlock] Sending request header");
            try {
                (new DeferredStreamJsonGenerator(plexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_READ_BLOCK);
                    gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
                    gen.writeEndObject();
                })).run();
            } catch (IOException e) {
                this.clientLog.warn("[requestFileBLock] Unable to obtain request header stream");
                plexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            InputStream headerStream;
            Future<InputStream> headerStreamFuture = plexer.waitInputChannel(1);

            try {
                this.clientLog.trace("[requestFileBlock] Obtaining response header stream");
                headerStream = headerStreamFuture.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);
                this.clientLog.debug("[requestFileBlock] Got response header stream");

            } catch (InterruptedException | TimeoutException e) {
                this.clientLog.error("[requestFileBLock] Unable to obtain response header stream");
                e.printStackTrace();
                plexer.terminate();
                future.completeExceptionally(e);
                return;

            } catch (ExecutionException e) {
                this.clientLog.error("[requestFileBLock] ExecutionException while obtaining response header stream");
                e.printStackTrace();
                plexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            this.clientLog.finer("[requestFileBlock] Parsing response header");
            (new JsonStreamParser(headerStream, true, (field) -> {
                if (!field.isObject()) return;

                JsonField.ObjectField header = (JsonField.ObjectField) field;
                if (header.containsKey(net.Constants.REQUEST_TYPE_PROPERTY) &&
                        header.getStringProperty(net.Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_READ_BLOCK)) {
                    if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                        if (future.isCancelled()) plexer.terminate();
                        else {
                            Future<InputStream> dataStreamFuture = plexer.waitInputChannel(2);
                            try {
                                this.clientLog.trace("[requestFileBlock] Obtaining response body stream");
                                InputStream dataStream = dataStreamFuture.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);
                                if (future.isCancelled()) {
                                    this.clientLog.fine("[requestFileBlock] Got response body stream, but request was canceled");
                                    try {
                                        dataStream.close();
                                    } catch (IOException e) { }
                                    plexer.terminate();
                                    return;
                                }

                                future.complete(dataStream);
                                this.clientLog.debug("[requestFileBlock] Successfully retrieved file block");

                            } catch (InterruptedException | TimeoutException e) {
                                this.clientLog.error("[requestFileBlock] Unable to obtain response body stream");
                                e.printStackTrace();
                                plexer.terminate();
                                future.completeExceptionally(e);

                            } catch (ExecutionException e) {
                                this.clientLog.error("[requestFileBlock] ExecutionException while trying to obtain response body");
                                e.printStackTrace();
                                plexer.terminate();
                                future.completeExceptionally(e);
                            }
                        }

                    } else if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_NOT_FOUND)) {
                        this.clientLog.log("[requestFileBlock] Remote node reported block not found");
                        future.completeExceptionally(new FileNotFoundException("Remote node said was file block not found"));
                    }

                } else {
                    this.clientLog.warn("[requestFileBlock] Malformed response header");
                    plexer.terminate();
                    future.completeExceptionally(new Exception("Malformed response header"));
                }
            })).run();
        });

        return future;
    }

    // add logging
    public Future<InputStream> getFileBlock(BlockDescriptor block, int replication) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            for (int i = 0; i < replication; i++) {
                block.setReplicaNumber(i);
                Future<InputStream> request = requestFileBlock(block);

                if (future.isCancelled()) return;

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

            }

            future.completeExceptionally(new Exception("Unable to locate file block"));
        });

        return future;
    }

    public Future<OutputStream> writeBlock(BlockDescriptor block) {
        return this.writeBlock(block, false);
    }

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
            //plexer.setLogFilter(Constants.DEBUG);

            this.clientLog.finest("[writeBlock] Sending request headers");
            OutputStream headersOut = null;
            try {
                (new DeferredStreamJsonGenerator(headersOut = plexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, append ? REQUEST_APPEND_BLOCK : REQUEST_WRITE_BLOCK);
                    gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
                    gen.writeEndObject();
                    this.clientLog.finest("[writeBlock] Request headers sent");
                })).run();
            } catch (IOException e) {
                this.clientLog.warn("[writeBlock] Unable to obtain request header stream");
                plexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            InputStream headersIn;
            Future<InputStream> headerStreamFuture = plexer.waitInputChannel(1);

            try {
                this.clientLog.trace("[writeBlock] Obtaining response header stream");
                headersIn = headerStreamFuture.get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS);

            } catch (InterruptedException | TimeoutException e) {
                this.clientLog.error("[writeBlock] Unable to obtain response header channel");
                e.printStackTrace();
                plexer.terminate();
                future.completeExceptionally(e);
                return;

            } catch (ExecutionException e) {
                this.clientLog.error("[writeBlock] ExecutionException while trying to obtain response header channel");
                e.printStackTrace();
                plexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            this.clientLog.finest("[writeBlock] Parsing response headers");
            (new JsonStreamParser(headersIn, false, (field) -> {
                if (!field.isObject()) return;

                JsonField.ObjectField packet = (JsonField.ObjectField) field;
                this.clientLog.finest("[writeBlock] Received response headers");

                if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                        !packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(append ? RESPONSE_APPEND_BLOCK : RESPONSE_WRITE_BLOCK)) {
                    this.clientLog.warn("[writeBlock] Received bad response type");
                    plexer.terminate();
                    future.completeExceptionally(new Exception("Bad response type"));
                }

                if (packet.containsKey(Constants.PROPERTY_RESPONSE_STATUS) &&
                        packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                    if (future.isCancelled()) {
                        plexer.terminate();
                        this.clientLog.fine("[writeBlock] Ready to write block, but request was canceled.");

                    } else {
                        try {
                            future.complete(plexer.openOutputChannel(2, 1024 * 10));
                            this.clientLog.fine("[writeBlock] Ready to write block to remote node");

                        } catch (IOException e) {
                            this.clientLog.warn("[writeBlock] Unable to obtain upload stream");
                            plexer.terminate();
                        }
                    }

                } else {
                    this.clientLog.warn("[writeBlock] Received response code: " +
                            packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS));
                    plexer.terminate();
                    future.completeExceptionally(new Exception("Received response code: " +
                            packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS)));
                }
            })).run();
            try {
                // give socketplexer time to establish channel
                Thread.sleep(Constants.MAX_CHANNEL_WAIT);
            } catch (InterruptedException e) {}

            try { headersIn.close(); } catch (IOException e) {}
            try { headersOut.close(); } catch (IOException e) {}
        });

        return future;
    }

    public Future<JsonField.ObjectField> getBlockStats(BlockDescriptor block) {
        this.clientLog.log("[getBlockStats] Request for block statistics for block " + block.getBlockName());

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

        this.executor.submit(() -> {

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
                return;
            }

            Socketplexer socketplexer = new Socketplexer(connection, this.executor);
            //socketplexer.setLogFilter(Constants.DEBUG);

            Future<InputStream> responseFuture = socketplexer.waitInputChannel(1);

            this.clientLog.fine("[getBlockStats] Sending request header");
            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_BLOCK_STATS);
                    gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
                    gen.writeEndObject();
                    this.clientLog.trace("[getBlockStats] Request headers sent");
                })).run();
            } catch (IOException e) {
                this.clientLog.warn("[getBlockStats] Unable to obtain request header stream");
                socketplexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            this.clientLog.fine("[getBlockStats] Parsing response header");
            try {
                InputStream in = responseFuture.get(5000, TimeUnit.MILLISECONDS);

                (new JsonStreamParser(in, true, (JsonField field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField response = (JsonField.ObjectField) field;

                    if (!response.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                            !response.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(RESPONSE_BLOCK_STATS)) {
                        future.completeExceptionally(new Exception("Bad response"));
                        this.clientLog.warn("[getBlockStats] Malformed response");
                        return;
                    }

                    this.clientLog.fine("[getBlockStats] Got remote stats of block " + block.getBlockName());

                    future.complete(response);
                    try {
                        in.close();
                        socketplexer.terminate();
                        this.clientLog.debug("[getBlockStats] Socketplexer terminated");
                    } catch (IOException e) {
                        this.clientLog.warn("[getBlockStats] Unable to close request session");
                        e.printStackTrace();
                    }
                })).run();
                socketplexer.terminate();

                if (!future.isDone()) {
                    future.completeExceptionally(new Exception("An unknown problem occurred"));
                }

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                this.clientLog.warn("[getBlockStats] There was a problem opening the response channel");
                e.printStackTrace();
                socketplexer.terminate();
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
                try {
                    (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                        gen.writeStartObject();
                        gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_FILE_METADATA);
                        gen.writeStringField(FileDescriptor.PROPERTY_FILE_NAME, fileName);
                        gen.writeEndObject();
                    })).run();
                } catch (IOException e) {
                    this.clientLog.warn("[getFileStats] Unable to obtain request header stream");
                    socketplexer.terminate();
                    future.completeExceptionally(e);
                    return;
                }

                AtomicReference<JsonField.ObjectField> aresponse = new AtomicReference<>();

                try {
                    this.clientLog.finest("[getFileStats] Parsing response headers...");
                    (new JsonStreamParser(socketplexer.waitInputChannel(1).get(2500, TimeUnit.MILLISECONDS), true, (field) -> {
                        if (field.isObject()) {
                            aresponse.set((JsonField.ObjectField) field);
                        }
                    })).run();

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
                    socketplexer.terminate();
                    continue;

                } else if (response.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_SERVER_ERROR)) {
                    this.clientLog.fine("[getFileStats] Remote node encountered an unexpected error");
                    exc = new Exception("Remote had a problem processing the request");
                    socketplexer.terminate();
                    continue;
                }

                this.clientLog.fine("[getFileStats] Successfully got file stats");
                future.complete(FileDescriptor.fromJson(response));
                socketplexer.terminate();
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
        this.clientLog.log("[readFile] Request to read file from DFS");
        CompletableFuture<InputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            RingBuffer buffer = new RingBuffer(1024 * 150);
            byte[] trx = new byte[1024 * 16];

            OutputStream buffer_out = buffer.getOutputStream();
            Future<InputStream> blockStream = getFileBlock(new BlockDescriptor(fileName, 0), replication);

            for (int i = 0; i < blockCount && buffer.isWriteOpened(); i++) {
                if (future.isCancelled() || !buffer.isWriteOpened()) {
                    blockStream.cancel(false);
                    return;
                }

                try {
                    this.clientLog.fine("[readFile] Retrieving block " + i);

                    InputStream block = blockStream.get(5, TimeUnit.SECONDS);

                    if (future.isCancelled() || !buffer.isReadOpened()) {
                        block.close();
                        break;
                    }

                    if (!future.isDone()) {
                        future.complete(buffer.getInputStream());
                    }

                    if (i + 1 < blockCount)
                        blockStream = getFileBlock(new BlockDescriptor(fileName, i + 1), replication);

                    this.clientLog.finer("[readFile] Retrieved block " + i);

                    int read;
                    while ((read = block.read(trx, 0, trx.length)) != -1) {
                        if (buffer.isWriteOpened()) buffer_out.write(trx, 0, read);
                        else {
                            block.close();
                            if (blockStream.isDone()) blockStream.get().close(); else blockStream.cancel(false);
                            break;
                        }
                        this.clientLog.trace("[readFile] Read " + read + " bytes from DFS");
                    }

                    block.close();

                } catch (TimeoutException | InterruptedException | ExecutionException | IOException e) {
                    this.clientLog.info("[readFile] Timed out while trying to retrieve block " + i);
                    e.printStackTrace();
                    if (!future.isDone()) future.completeExceptionally(e);

                    try {
                        if (blockStream.isDone()) blockStream.get().close();
                        else blockStream.cancel(false);

                    } catch (IOException | InterruptedException | ExecutionException e1) {}

                    try {
                        buffer.getOutputStream().close();
                    } catch (IOException e1) {}

                    break;
                }
            }

            try {
                buffer_out.close();
            } catch (IOException e) {
            }
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
                file.cancel(false);
                future.completeExceptionally(e);
                return;
            }

            int blockCount = descriptor.getBlockCount();
            int replicas = descriptor.getReplicas();

            Future<InputStream> in = this.readFile(fileName, blockCount, replicas);
            try {
                future.complete(in.get(10000, TimeUnit.MILLISECONDS));

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[DFS][readFile] There was a problem getting the requested file");
                e.printStackTrace();
                in.cancel(false);
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

            try {
                (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_PUT_FILE_METADATA);
                    fileDescriptor.serialize(gen, true);
                    gen.writeEndObject();
                })).run();
            } catch (IOException e) {
                this.clientLog.warn("[getBlockStats] Unable to obtain request header stream");
                socketplexer.terminate();
                future.completeExceptionally(e);
                return;
            }

            try {
                (new JsonStreamParser(socketplexer.waitInputChannel(1).get(5000, TimeUnit.MILLISECONDS), true, (field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField packet = (JsonField.ObjectField) field;
                    String status = packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS);
                    if (status.equals(Constants.RESPONSE_STATUS_OK)) {
                        future.complete(status);

                    } else {
                        future.completeExceptionally(new Exception("Server returned status code " + status));
                    }
                })).run();
                socketplexer.terminate();

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

        this.clientLog.log("[writeFile] Request to write file " + fileName);

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
            for (int i = 0; i < replication; i++)
                outputs[i] = writeBlock(new BlockDescriptor(fileName, blockNumber, i), append);

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
                    } else outputs[i].cancel(false);
                }
                try {
                    in.close();
                } catch (IOException e1) {
                }
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

            for (int i = 0; i < replicas; i++)
                outputs[i] = writeBlock(new BlockDescriptor(fileName, blockNumber, i), true);

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
                BlockDescriptor blockDescriptor = new BlockDescriptor(fileName, 0, i);
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
            if (!file.delete())
                this.clientLog.warn("[deleteFile] There was a problem deleting block " + block.getBlockName());
            this.blocks.remove(block.getBlockName());
        }
    }

    public List<BlockDescriptor> getLocalBlocks(String fileName) {
        ArrayList<BlockDescriptor> blocks = new ArrayList<>();

        blocks:
        for (BlockDescriptor block : this.blocks.values()) {
            if (block.getFileName().equals(fileName)) {
                for (BlockDescriptor block1 : blocks)
                    if (block1.getBlockNumber() == block.getBlockNumber()) continue blocks;
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
