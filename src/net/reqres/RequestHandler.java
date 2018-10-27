package net.reqres;

import net.common.JsonField;

import java.util.concurrent.ExecutorService;

/**
 * A simple FunctionalInterface used by {@link RequestServer} to represent a request handler.
 *
 * RequestHandlers are registered with a RequestServer to handle requests of a specific type
 * when the server receives one.
 *
 * The RequestHandler is provided with an ExecutorService, which comes from the RequestServer
 * and is intended to be used for any long-running tasks involved with handling the request.
 * <em>Requests should not be handled synchronously</em> in the RequestHandler.
 */
@FunctionalInterface
public interface RequestHandler {
    /**
     * Invoked when the RequestServer receives a request with request type matching that which this
     * RequestHandler was registered with. The RequestServer passes the Socketplexer that it created to
     * process the request. This is provided so that request handlers can create and use sub-channels of
     * the request connection as necessary for the type of request being handled. The parsed request header
     * is also passed to the handler. An ExecutorService is also provided to be used for any long-running
     * or potentially blocking tasks involved with handling the request. The request handler should return
     * as quickly as possible, leaving the bulk of processing to be handled by the ExecutorService.
     *
     * @param plexer the Socketplexer representing the request/response session
     * @param request the parsed request header
     * @param executorService an ExecutorService to handle any parallel, long-running, or blocking tasks
     */
    void handle(Socketplexer plexer, JsonField.ObjectField request, ExecutorService executorService);
}
