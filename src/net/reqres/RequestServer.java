package net.reqres;

import net.Constants;
import net.common.JsonField;
import net.common.JsonStreamParser;
import net.lib.ServerSocket;
import net.lib.Socket;
import utils.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.*;

/**
 * RequestServer wraps around a {@link ServerSocket}, where connections are expected to follow a request/response
 * pattern. Request/Response connections use {@link Socketplexer}s to separate request/response headers from body data.
 * Request headers take the form of a Json object. The header object must contain a property with a field name
 * equivalent to {@link Constants#REQUEST_TYPE_PROPERTY}. The value of the request type field in the header is used to
 * resolve the {@link RequestHandler} which will handle the request. If the header is malformed, or there is no handler
 * for the parsed request type, then the connection is terminated. RequestHandler instances must be registered with the
 * RequestServer in order for the server to be able to handle requests of a given type.
 *
 * RequestServer puts a heavy emphasis on organized concurrency. Thus, all RequestServer related classes have direct
 * integration with {@link ExecutorService}s. The RequestServer itself uses an ExecutorService to manage its internal
 * request handling system. The RequestServer's internal executor service is also used by the Socketplexers that it
 * creates, and is passed to RequestHandlers that are registered to it.
 */
public class RequestServer {

    protected ExecutorService executor;

    private ServerSocket socket;

    private ConcurrentHashMap<String, RequestHandler> requestHandlers;

    private Logger logger;

    /**
     * Creates a new RequestServer with its own ExecutorService and listening on the given port number.
     *
     * @param port the port number to listen to
     * @throws SocketException if there is a problem opening the server socket
     */
    public RequestServer(int port) throws SocketException {
        this(new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 64, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>()), port);
    }

    /**
     * Creates a new RequestServer with the provided ExecutorService, listening on the given port number.
     *
     * @param executor an ExecutorService to use for parallel task processing
     * @param port the port number to listen on
     * @throws SocketException if there is aa problem opening the server socket
     */
    public RequestServer(ExecutorService executor, int port) throws SocketException {
        this.executor = executor;
        this.socket = new ServerSocket(port, this::onSocket);
        this.requestHandlers = new ConcurrentHashMap<>();
        this.logger = new Logger("RequestServer", Constants.TRACE);
        this.socket.open();
    }

    /**
     * Creates a new RequestServer using the provided ExecutorService, listening on the given SocketAddress.
     *
     * @param executor an ExecutorService to use for parallel task management
     * @param address a SocketAddress to listen on
     * @throws SocketException if there is a problem opening the server socket
     */
    public RequestServer(ExecutorService executor, SocketAddress address) throws SocketException {
        this.executor = executor;
        this.socket = new ServerSocket(address, this::onSocket);
        this.requestHandlers = new ConcurrentHashMap<>();
        this.logger = new Logger("RequestServer", Constants.TRACE);
        this.socket.open();
    }

    /**
     * Called by backing {@link ServerSocket} when a new connection is opened.
     * Used internally.
     *
     * @param sock the newly opened socket
     */
    private void onSocket(Socket sock) {
        this.logger.log("[onSocket] New socket connection");
        executor.submit(this.new ConnectionHandler(sock));
    }

    /**
     * Called when a request header is parsed.
     * Used internally.
     *
     * @param plexer the Socketplexer representing the request session
     * @param request the parsed request header
     */
    protected void onRequest(Socketplexer plexer, JsonField request) {
        if (!request.isObject()) {
            this.logger.error("[onRequest] Request header parsed as non-object value");
            plexer.terminate();
        }

        JsonField.ObjectField packet = (JsonField.ObjectField) request;

        if (!packet.containsKey(Constants.REQUEST_TYPE_PROPERTY)) {
            this.logger.error("[onRequest] Request header does not include request type");
            plexer.terminate();
        }

        String requestType = packet.getStringProperty(Constants.REQUEST_TYPE_PROPERTY);
        if (this.requestHandlers.containsKey(requestType)) {
            this.logger.log("[onRequest] Found handler for request type");
            try {
                this.requestHandlers.get(requestType).handle(plexer, packet, this.executor);

            } catch (Exception e) {
                this.logger.warn("[onRequest] Exception thrown by request handler");
                e.printStackTrace();
                plexer.terminate();
            }

        } else {
            this.logger.warn("[onRequest] No registered handler for request type: " + requestType);
            plexer.terminate();
        }
    }

    public ServerSocket getServerSocket() {
        this.logger.trace("[getServerSocket] ServerSocket retrieved");
        return this.socket;
    }

    /**
     * Registers a {@link RequestHandler} to handle requests of the given type.
     *
     * @param requestType the type of request that the handler can handle
     * @param handler the handler to handle the named request type
     */
    public void registerHandler(String requestType, RequestHandler handler) {
        this.requestHandlers.put(requestType, handler);
        this.logger.finer("[registerHandler] Handler for request type " + requestType + " registered");
    }

    /**
     * A {@link Runnable} class used to asynchronously create a Socketplexer, and parse the request header
     * when a new connection is opened.
     * Used internally.
     */
    private class ConnectionHandler implements Runnable {

        private Socket sock;

        /**
         * Creates a new ConnectionHandler to handle the connection represented by the given Socket.
         *
         * @param sock the socket to handle
         */
        public ConnectionHandler(Socket sock) {
            this.sock = sock;
        }

        @Override
        public void run() {
            logger.finer("[ConnectionHandler] Handling connection");

            logger.trace("[ConnectionHandler] Creating socketplexer");
            Socketplexer plexer = new Socketplexer(this.sock, executor);

            logger.trace("[ConnectionHandler] Getting header channel");
            Future<InputStream> requestChannel = plexer.waitInputChannel(1);
            try {
                InputStream requestStream = requestChannel.get(Constants.TIMEOUT_DELAY, TimeUnit.MILLISECONDS);
                logger.debug("[ConnectionHandler] Parsing request header");
                JsonStreamParser requestParser = new JsonStreamParser(requestStream, true, (header) -> {
                    onRequest(plexer, header);
                });
                executor.submit(requestParser);

            } catch (InterruptedException e) {
                logger.warn("[ConnectionHandler] Interrupted while waiting for request input channel");
                e.printStackTrace();

            } catch (ExecutionException e) {
                logger.error("[ConnectionHandler] Input channel resolution encountered an exception");
                e.printStackTrace();

            } catch (TimeoutException e) {
                logger.error("ConnectionHandler] Timed out waiting for request header channel");
                e.printStackTrace();
            }
        }
    }
}
