package mesh.library;

import mesh.impl.NodeUnavailableException;
import net.Constants;
import net.common.*;
import net.lib.Socket;
import net.reqres.Socketplexer;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class SearchHandler implements Runnable {

    private final String searchParam;

    private MeshLibrary library;

    public SearchHandler(String param, MeshLibrary library) {
        this.searchParam = param;
        this.library = library;
    }

    @Override
    public void run() {
        Set<Integer> nodes = this.library.mesh.getAvailableNodes();

        LinkedList<Socketplexer> connections = new LinkedList<>();

        for (Integer node : nodes) {
            try {
                Socket socket = this.library.mesh.tryConnect(node);
                Socketplexer socketplexer = new Socketplexer(socket, this.library.executor);
                connections.add(socketplexer);
                this.library.executor.submit(new DeferredStreamJsonGenerator(socketplexer.openOutputChannel(1), true, (gen) -> {
                    gen.writeStartObject();
                    gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.REQUEST_SEARCH_MESH);
                    gen.writeStringField(MeshLibrary.PROPERTY_SEARCH_PARAMETER, searchParam);
                    gen.writeEndObject();
                }));

            } catch (NodeUnavailableException | SocketException | SocketTimeoutException e) {
                System.err.println("[SearchHandler][run] Unable to connect to node " + node);
                e.printStackTrace();
            }
        }

        for (Socketplexer socketplexer : connections) {
            try {
                this.library.executor.submit(new JsonStreamParser(socketplexer.waitInputChannel(1).get(), true, (field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField header = (JsonField.ObjectField) field;
                    if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                        this.library.executor.submit(new JsonStreamParser(socketplexer.getInputChannel(2), true, (field1) -> {
                            if (!field1.isObject()) return;
                            JsonField.ObjectField song = (JsonField.ObjectField) field1;
                            this.library.songs.add(new MeshClientSong(
                                    song.getStringProperty(MeshLibrary.PROPERTY_SONG_TITLE),
                                    song.getStringProperty(MeshLibrary.PROPERTY_SONG_ARTIST),
                                    song.getStringProperty(MeshLibrary.PROPERTY_SONG_ALBUM),
                                    song.getLongProperty(MeshLibrary.PROPERTY_SONG_DURATION),
                                    song.getStringProperty(MeshLibrary.PROPERTY_SONG_FILE_NAME),
                                    this.library
                            ));

                        }, true));

                    } else {
                        socketplexer.terminate();
                        connections.remove(socketplexer);
                    }
                }));

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        while (!connections.isEmpty()) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {}

            for (Socketplexer socketplexer : connections) {
                if (!socketplexer.isOpened()) connections.remove(socketplexer);
            }
        }

        System.out.println("[SearchHandler][run] Search for \"" + searchParam + "\" completed");
    }
}
