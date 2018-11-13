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

public class SearchSongRequestHandler implements Runnable {

    private Socketplexer socketplexer;

    private JsonField.ObjectField request;

    private MeshLibrary library;

    public SearchSongRequestHandler(Socketplexer socketplexer, JsonField.ObjectField request, MeshLibrary library) {
        this.socketplexer = socketplexer;
        this.request = request;
        this.library = library;
    }

    @Override
    public void run() {
        String searchParam = this.request.getStringProperty(MeshLibrary.PROPERTY_SEARCH_PARAMETER);

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
                System.err.println("[SearchSongRequestHandler][run] Unable to connect to node " + node);
                e.printStackTrace();
            }
        }

        this.library.executor.submit(new DeferredStreamJsonGenerator(this.socketplexer.openOutputChannel(1), true, (gen) -> {
            gen.writeStartObject();
            gen.writeStringField(Constants.REQUEST_TYPE_PROPERTY, MeshLibrary.RESPONSE_SEARCH_SONG);
            gen.writeStringField(Constants.PROPERTY_RESPONSE_STATUS, Constants.RESPONSE_STATUS_OK);
            gen.writeEndObject();
        }));

        AsyncJsonStreamGenerator generator = new AsyncJsonStreamGenerator(socketplexer.openOutputChannel(2));
        this.library.executor.submit(generator);
        generator.enqueue((gen) -> gen.writeStartArray());

        for (Socketplexer socketplexer : connections) {
            try {
                this.library.executor.submit(new JsonStreamParser(socketplexer.waitInputChannel(1).get(), true, (field) -> {
                    if (!field.isObject()) return;
                    JsonField.ObjectField header = (JsonField.ObjectField) field;
                    if (header.getStringProperty(Constants.PROPERTY_RESPONSE_STATUS).equals(Constants.RESPONSE_STATUS_OK)) {
                        this.library.executor.submit(new JsonStreamParser(socketplexer.getInputChannel(2), true, (field1) -> {
                            generator.enqueue((gen) -> field.write(gen));

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

        generator.enqueue((gen) -> gen.writeEndObject());
        generator.close();
    }
}
