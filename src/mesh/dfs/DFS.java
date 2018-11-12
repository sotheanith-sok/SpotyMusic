package mesh.dfs;

import jdk.nashorn.internal.ir.Block;
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
import utils.RingBuffer;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DFS {

    public static final File rootDirectory = new File("SpotyMusic/");
    public static final File blockDirectory = new File("SpotyMusic/dfs/");
    public static final File fileIndex = new File("SpotyMusic/files.json");

    protected ScheduledExecutorService executor;

    private MeshNode mesh;

    protected ConcurrentHashMap<String, BlockDescriptor> blocks;

    protected ObservableMap<String, FileDescriptor> files;

    private AtomicBoolean blockOrganizerRunning;

    public DFS(MeshNode mesh, ScheduledExecutorService executor) {
        this.mesh = mesh;
        this.executor = executor;

        this.blocks = new ConcurrentHashMap<>();
        this.files = new ObservableMap<>();

        this.blockOrganizerRunning = new AtomicBoolean(false);

    }

    public void init() {
        if (!rootDirectory.exists()) rootDirectory.mkdir();
        if (!blockDirectory.exists()) blockDirectory.mkdirs();

        // enumerate existing blocks
        this.executor.submit(this::enumerateBlocks);
        // enumerate existing files
        this.executor.submit(this::enumerateFiles);

        // register request handlers
        this.mesh.registerRequestHandler(REQUEST_READ_BLOCK, this::readBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_WRITE_BLOCK, this::writeBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_BLOCK_STATS, this::blockStatsHandler);
        this.mesh.registerRequestHandler(REQUEST_FILE_METADATA, this::fileQueryHandler);
        this.mesh.registerRequestHandler(REQUEST_DELETE_BLOCK, this::deleteBlockHandler);
        this.mesh.registerRequestHandler(REQUEST_PUT_FILE_METADATA, this::filePutHandler);
        this.mesh.registerRequestHandler(REQUEST_APPEND_BLOCK, this::appendBlockHandler);

        this.mesh.registerPacketHandler(COMMAND_DELETE_FILE, this::deleteFileHandler);
    }

    private void enumerateBlocks() {
        File[] files = blockDirectory.listFiles();
        if (files == null) {
            System.out.println("[DFS][enumerateBlocks] No blocks to enumerate");
            return;
        }

        int count = 0;

        for (File f : files) {
            try {
                BlockDescriptor descriptor = new BlockDescriptor(f);
                this.blocks.put(descriptor.getBlockName(), descriptor);
                count++;

            } catch (Exception e) {
                System.err.println("[DFS][init] Exception while creating BlockDescriptor for block file: " + f.getName());
                System.err.println(e.getMessage());
            }
        }

        System.out.println("[DFS][enumerateBlocks] Enumerated " + count + " of " + files.length + " detected blocks");

        this.organizeBlocks();
        this.mesh.addNodeConnectListener(id -> this.organizeBlocks());
    }

    private void organizeBlocks() {
        if (this.blockOrganizerRunning.compareAndSet(false, true)) {
            this.executor.submit(() -> {
                byte[] trx = new byte[1024 * 8];
                for (BlockDescriptor block : this.blocks.values()) {
                    int bestId = 0;
                    if ((bestId = this.getBestId(block.getBlockId())) != this.mesh.getNodeId()) {
                        System.out.println("[DFS][organizeBlocks] Block " + block.getBlockName() + " does not belong on this node");

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

                            if (block.getBlockNumber() == 0) {
                                FileDescriptor descriptor = this.files.get(block.getFileName());
                                if (descriptor != null) {
                                    this.putFileMetadata(descriptor, bestId);
                                    this.files.remove(block.getFileName());
                                }
                            }

                        } catch (TimeoutException e) {
                            System.out.println("[DFS][enumerateBlocks] Timed out while trying to move block to another node");
                            e.printStackTrace();

                            if (in != null) {
                                try { in.close(); } catch (IOException e1) {}
                            }

                        } catch (Exception e) {
                            System.out.println("[DFS][enumerateBlocks] Exception while trying to transfer block");
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    private void enumerateFiles() {
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

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

        } else {
            try {
                fileIndex.createNewFile();

            } catch (IOException e) {
                System.err.println("[DFS][enumerateFiles] Unable to create file index file");
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
        this.executor.submit(new WriteBlockRequestHandler(socketplexer, block,this));
    }

    private void appendBlockHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
        String blockName = request.getStringProperty(PROPERTY_BLOCK_NAME);
        BlockDescriptor block = null;
        if (this.blocks.containsKey(blockName)) block = this.blocks.get(blockName);
        else block = new BlockDescriptor(blockName);
        this.executor.submit(new WriteBlockRequestHandler(socketplexer, block, this, true));
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
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_NUMBER, block.getBlockNumber());
                gen.writeNumberField(BlockDescriptor.PROPERTY_BLOCK_REPLICA, block.getReplicaNumber());
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

    private void fileQueryHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
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
                executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                    gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                    gen.writeEndObject();
                }));
            }

        } catch (Exception e) {
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_FILE_METADATA);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_SERVER_ERROR);
                gen.writeEndObject();
            }));
        }

    }

    private void filePutHandler(Socketplexer socketplexer, JsonField.ObjectField request, ExecutorService executor) {
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
            executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                gen.writeStartObject();
                gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, RESPONSE_DELETE_BLOCK);
                gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_NOT_FOUND);
                gen.writeEndObject();
            }));
        }
    }

    private void deleteFileHandler(JsonField.ObjectField packet, InetAddress source) {
        this.executor.submit(() -> {
            String fileName = packet.getStringProperty(FileDescriptor.PROPERTY_FILE_NAME);
            this.files.remove(fileName);

            for (BlockDescriptor block : this.blocks.values()) {
                if (block.getFileName().equals(fileName)) {
                    File file = block.getFile();
                    if (!file.delete()) System.err.println("[DFS][deleteFileHandler] There was a problem deleting block " + block.getBlockName());
                    this.blocks.remove(block.getBlockName());
                }
            }
        });
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
        return this.writeBlock(block, false);
    }

    public Future<OutputStream> writeBlock(BlockDescriptor block, boolean append) {
        int node_id = getBestId(block.getBlockId());

        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        if (node_id == this.mesh.getNodeId() && this.blocks.containsKey(block.getBlockName())) {
            BlockDescriptor localBlock = this.blocks.get(block.getBlockName());
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(localBlock.getFile(), append));
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
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, append ? REQUEST_APPEND_BLOCK : REQUEST_WRITE_BLOCK);
            gen.writeStringField(PROPERTY_BLOCK_NAME, block.getBlockName());
            gen.writeEndObject();
        }));

        this.executor.submit(new JsonStreamParser(plexer.getInputChannel(1), true, (field) -> {
            if (!field.isObject()) return;

            JsonField.ObjectField packet = (JsonField.ObjectField) field;

            if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY) ||
                    !packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY).equals(append ? RESPONSE_APPEND_BLOCK : RESPONSE_WRITE_BLOCK)) {
                System.err.println("[DFS][writeBlock] Received bad response type");
                future.completeExceptionally(new Exception("Bad response type"));
            }

            if (packet.containsKey(Constants.PROPERTY_RESPONSE_STATUS) &&
                    packet.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                if (future.isCancelled()) plexer.terminate();
                else {
                    future.complete(plexer.openOutputChannel(2));
                }

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
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER, localBlock.getBlockNumber());
            response.setProperty(BlockDescriptor.PROPERTY_BLOCK_REPLICA, localBlock.getReplicaNumber());
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

    public Future<FileDescriptor> getFileStats(String fileName) {
        if (this.files.containsKey(fileName)) {
            return CompletableFuture.completedFuture(this.files.get(fileName));
        }

        CompletableFuture<FileDescriptor> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            BlockDescriptor firstBlock = new BlockDescriptor(fileName, 0, 0);

            Exception exc = null;

            for (int i = 0; i < this.mesh.getAvailableNodes().size(); i++) {
                firstBlock.setReplicaNumber(i);

                int node_id = this.getBestId(firstBlock.getBlockId());
                Socket connection;

                try {
                    connection = this.mesh.tryConnect(node_id);
                    exc = null;

                } catch (SocketException | SocketTimeoutException | NodeUnavailableException e) {
                    System.err.println("[DFS][getFileStats] Unable to connect to node");
                    e.printStackTrace();
                    exc = e;
                    continue;
                }

                Socketplexer socketplexer = new Socketplexer(connection, this.executor);

                this.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, REQUEST_FILE_METADATA);
                    gen.writeStringField(FileDescriptor.PROPERTY_FILE_NAME, fileName);
                    gen.writeEndObject();
                }));

                AtomicReference<JsonField.ObjectField> aresponse = new AtomicReference<>();

                try {
                    JsonStreamParser parser = new JsonStreamParser(socketplexer.waitInputChannel(1).get(2500, TimeUnit.MILLISECONDS), true, (field) -> {
                        if (field.isObject()) {
                            aresponse.set((JsonField.ObjectField) field);
                        }
                    });
                    parser.run();

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[DFS][getFileStats] Exception while processing response data");
                    e.printStackTrace();
                    exc = e;
                    socketplexer.terminate();
                    continue;
                }

                JsonField.ObjectField response = aresponse.get();
                if (response.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_NOT_FOUND)) {
                    exc = new FileNotFoundException("Remote reported no such file");
                    continue;

                } else if (response.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_SERVER_ERROR)) {
                    exc = new Exception("Remote had a problem processing the request");
                    continue;
                }

                future.complete(FileDescriptor.fromJson(response));
                break;
            }

            if (exc != null && !future.isDone()) {
                future.completeExceptionally(exc);
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

    public Future<?> putFileMetadata(FileDescriptor fileDescriptor, int node_id) {
        CompletableFuture<String> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            Socket connection;

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

    public Future<OutputStream> writeFile(String fileName, int replicas, boolean append) {
        CompletableFuture<OutputStream> future = new CompletableFuture<>();

        this.executor.submit(() -> {
            int replication = replicas;

            FileDescriptor descriptor = null;
            JsonField.ObjectField lastBlock = null;
            if (append) {
                Future<FileDescriptor> file = getFileStats(fileName);
                try {
                    descriptor = file.get(10, TimeUnit.SECONDS);
                    replication = descriptor.getReplicas();

                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    System.err.println("[DFS][writeFile][append] Exception while fetching file metadata");
                    e.printStackTrace();
                    future.completeExceptionally(e);
                    return;
                }

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

            Future<OutputStream>[] outputs = new Future[replication];
            for (int i = 0; i < replication; i++) outputs[i] = writeBlock(new BlockDescriptor(fileName, 0, i), append);

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
            int blockNumber = append ? (int) lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER) : 0;
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

    public void appendFile(String fileName, byte[] data) {
        this.executor.submit(() -> {
            FileDescriptor descriptor;
            JsonField.ObjectField lastBlock = null;
            Future<FileDescriptor> file = getFileStats(fileName);
            try {
                descriptor = file.get(10, TimeUnit.SECONDS);

            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                System.err.println("[DFS][writeFile][append] Exception while fetching file metadata");
                e.printStackTrace();
                return;
            }

            Future<JsonField.ObjectField> block = getBlockStats(new BlockDescriptor(fileName, descriptor.getBlockCount() - 1));
            try {
                lastBlock = block.get(10, TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[DFS][writeFile][append] Exception while fetching last block metadata");
                e.printStackTrace();
                return;
            }

            int blockNumber = (int) lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_NUMBER);
            int replicas = descriptor.getReplicas();

            Future<OutputStream>[] outputs = new Future[replicas];
            if (lastBlock.getLongProperty(BlockDescriptor.PROPERTY_BLOCK_SIZE) + data.length > Constants.MAX_BLOCK_SIZE) {
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

            FileDescriptor metadata = new FileDescriptor(fileName, descriptor.getTotalSize() + data.length, blockNumber + 1, replicas);
            for (int i = 0; i < replicas; i++) {
                BlockDescriptor blockDescriptor= new BlockDescriptor(fileName, 0, i);
                int id = this.getBestId(blockDescriptor.getBlockId());
                this.putFileMetadata(metadata, id);
            }
        });
    }

    public void deleteFile(String fileName) {
        this.executor.submit(() -> {
            Future<FileDescriptor> descriptorFuture = this.getFileStats(fileName);
            try {
                descriptorFuture.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[DFS][deleteFile] Unable to get metadata of file to delete");
                e.printStackTrace();
            }
        });
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
