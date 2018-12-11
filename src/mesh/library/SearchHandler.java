package mesh.library;

import mesh.dfs.BlockDescriptor;
import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.*;
import net.lib.Socket;
import net.reqres.Socketplexer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchHandler implements Runnable {

    private final String searchParam;

    private MeshLibrary library;

    public SearchHandler(String param, MeshLibrary library) {
        this.searchParam = param.toLowerCase();
        this.library = library;
    }

    @Override
    public void run() {
        Set<Integer> nodes = this.library.mesh.getAvailableNodes();

        final Object connectionsLock = new Object();
        LinkedList<Socketplexer> connections = new LinkedList<>();

        // connect to other nodes
        for (Integer node : nodes) {
            if (node == this.library.mesh.getNodeId()) continue;
            try {
                Socket socket = this.library.mesh.tryConnect(node);
                Socketplexer socketplexer = new Socketplexer(socket, this.library.executor);
                connections.add(socketplexer);

            } catch (NodeUnavailableException | SocketException | SocketTimeoutException e) {
                System.err.println("[SearchHandler][run] Unable to connect to node " + node);
                e.printStackTrace();
            }
        }

        // send search request to other nodes
        for (Socketplexer socketplexer : connections) {
            this.library.executor.submit(() -> {
                try {
                    (new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), false, (gen) -> {
                        gen.writeStartObject();
                        gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.REQUEST_SEARCH_MESH);
                        gen.writeStringField(MeshLibrary.PROPERTY_SEARCH_PARAMETER, searchParam);
                        gen.writeEndObject();
                    })).run();
                } catch (IOException e) {
                    synchronized (connectionsLock) {
                        connections.remove(socketplexer);
                    }
                    return;
                }

                AtomicBoolean ok = new AtomicBoolean(false);

                try {
                    (new JsonStreamParser(socketplexer.waitInputChannel(1).get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS), true, (field) -> {
                        if (!field.isObject()) return;
                        JsonField.ObjectField header = (JsonField.ObjectField) field;
                        ok.set(header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK));
                    })).run();

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[SearchHandler] Unable send search query");
                    System.err.println("[SearchHandler] \t" + e.getMessage());
                    socketplexer.terminate();
                    synchronized (connectionsLock) { connections.remove(socketplexer); }
                    return;
                }

                if (!ok.get()) {
                    socketplexer.terminate();
                    synchronized (connectionsLock) { connections.remove(socketplexer); }
                    return;
                }

                try {
                    (new JsonStreamParser(socketplexer.waitInputChannel(2).get(Constants.MAX_CHANNEL_WAIT, TimeUnit.MILLISECONDS), true, (field1) -> {
                        if (!field1.isObject()) return;
                        JsonField.ObjectField song = (JsonField.ObjectField) field1;

                        //System.out.println("[SearchHandler][parse] Getting title");
                        String title = song.getStringProperty(MeshLibrary.PROPERTY_SONG_TITLE);
                        //System.out.println("[SearchHandler][parse] Getting artist");
                        String artist = song.getStringProperty(MeshLibrary.PROPERTY_SONG_ARTIST);
                        //System.out.println("[SearchHandler][parse] Getting album");
                        String album = song.getStringProperty(MeshLibrary.PROPERTY_SONG_ALBUM);
                        //System.out.println("[SearchHandler][parse] Getting duration");
                        long duration = Long.parseLong(song.getStringProperty(MeshLibrary.PROPERTY_SONG_DURATION));
                        //System.out.println("[SearchHandler][parse] Getting file name");
                        String file = song.getStringProperty(MeshLibrary.PROPERTY_SONG_FILE_NAME);

                        MeshClientSong mcs = new MeshClientSong(title, artist, album, duration, file, this.library
                        );
                        this.library.addSong(mcs);
                        //System.out.println("[SearchHandler] Received response from remote: " + song.getStringProperty(MeshLibrary.PROPERTY_SONG_TITLE));
                    }, true)).run();
                    System.out.println("[SearchHandler] End of response data");

                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    System.err.println("[SearchHandler] Unable to parse search query response");
                    System.err.println("[SearchHandler] \t" + e.getMessage());

                } finally {
                    socketplexer.terminate();
                    synchronized (connectionsLock) { connections.remove(socketplexer); }
                }
            });
        }

        long entries = 0;
        long matches = 0;

        // search local index
        for (BlockDescriptor block : this.library.dfs.getLocalBlocks(MeshLibrary.INDEX_FILE_NAME)) {
            Future<InputStream> fin = this.library.dfs.getFileBlock(block, MeshLibrary.INDEX_FILE_REPLICAS);
            InputStream in;

            try {
                in = fin.get(2, TimeUnit.SECONDS);

            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("[SearchHandler][run] Unable to read local index file block");
                e.printStackTrace();
                continue;
            }

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    entries++;
                    String[] fields = line.split(";");
                    for (String field : fields) {
                        if (field.trim().toLowerCase().contains(searchParam)) {
                            matches++;
                            this.library.addSong(new MeshClientSong(fields[2], fields[0], fields[1], Long.parseLong(fields[3]), fields[4], this.library));
                            break;
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("[SearchHandler][run] IOException while reading inverted index file");
                e.printStackTrace();

                try {
                    in.close();
                } catch (IOException e1) {
                }
                continue;
            }
        }

        System.out.println("[SearchHandler][run] Scanned " + entries + " entries in local index blocks, " + matches + " matches");

        synchronized (connectionsLock) {
            while (!connections.isEmpty()) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                }

                for (Socketplexer socketplexer : connections) {
                    if (!socketplexer.isOpened()) connections.remove(socketplexer);
                }
            }
        }

        System.out.println("[SearchHandler][run] Search for \"" + searchParam + "\" completed");
    }
}
