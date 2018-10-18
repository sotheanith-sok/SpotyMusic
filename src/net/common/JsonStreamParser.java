package net.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import net.lib.Socket;
import utils.CompletableRunnable;

import java.io.*;
import java.util.*;

public class JsonStreamParser implements CompletableRunnable {

    private static JsonFactory factory = new JsonFactory();

    private Socket socket;

    private Handler handler;

    private JsonParser parser;

    private ParserState state;

    private Deque<ParserContext> contextStack;

    private boolean autoCloseSocket = false;

    private boolean globalArrayAsStream = false;

    public JsonStreamParser(Socket socket, boolean autoCloseSocket, Handler handler) {
        this.socket = socket;
        this.autoCloseSocket = autoCloseSocket;
        this.handler = handler;
        this.state = ParserState.NEW;
        this.contextStack = new LinkedList<>();
        //this.socket.addDisconnectListener(() -> this.state = ParserState.CLOSED);
    }

    public JsonStreamParser(Socket socket, boolean autoCloseSocket, Handler handler, boolean globalArrayAsStream) {
        this(socket, autoCloseSocket, handler);
        this.globalArrayAsStream = globalArrayAsStream;
    }

    public boolean run() throws Exception {
        if (this.state == ParserState.NEW) {
            try {
                this.initialize();
                this.state = ParserState.READY;
                //System.out.println("[JsonStreamParser][run] JsonSteamParser initialized");

            } catch (IOException e) {
                this.finished();
                this.state = ParserState.ERROR;
                System.err.println("[JsonStreamParser][update] IOException while creating JsonParser");
                e.printStackTrace();
                throw e;
            }
        }

        if (this.socket.isReceiveClosed()) {
            System.out.println("[JsonStreamParser][run][" + this.state + "] Socket not connected or input is closed");
            this.state = ParserState.CLOSED;
        }

        if (this.state == ParserState.READY) {
            try {
                InputStream in = this.socket.inputStream();

                for (int i = 0; this.state == ParserState.READY && i < 10; i++) {
                    if (parser.hasCurrentToken()) {
                        //System.out.println("[JsonStreamParser][update] processing token");
                        try {
                            this.processToken(parser.getCurrentToken(), parser);

                        } catch (IOException e) {
                            this.finished();
                            this.state = ParserState.ERROR;
                            System.err.println("[JsonStreamParser][update] IOException while processing token");
                            e.printStackTrace();
                            throw e;
                        }

                    }
                    //System.out.println("[JsonSteamParser] Trying to get next token");
                    if (parser.nextToken() == null) {
                        this.state = ParserState.WAITING;
                        break;
                    }

                }

            } catch (IOException e) {
                this.finished();
                this.state = ParserState.ERROR;
                System.err.println("[JsonStreamParser][update] IOException while reading input stream");
                e.printStackTrace();
                throw e;
            }

        } else if (this.state == ParserState.WAITING) {
            if (this.socket.isReceiveClosed()) {
                this.state = ParserState.CLOSED;

            } else {
                this.state = ParserState.READY;
            }
        }

        return !this.state.isAlive();
    }

    public ParserState getState() {
        return this.state;
    }

    protected void initialize() throws IOException {
        this.parser = factory.createParser(this.socket.inputStream());
    }

    protected void finished() {
        this.state = ParserState.COMPLETE;
        //System.out.println("[JsonStreamParser][finished] JsonStreamParser finished");

        if (this.autoCloseSocket) {
            System.out.println("[JsonStreamParser][finished] JsonStream finished, closing socket");
            this.socket.close();
        }
    }

    public void abort() {
        this.state = ParserState.ABORTED;
        this.socket.close();
    }

    protected void processToken(JsonToken token, JsonParser parser) throws IOException {
        //System.out.println("[JsonStreamParser][processToken] Token: " + token);

        if (token == JsonToken.START_OBJECT) {
            this.contextStack.push(new ParserContext(true));
            return;

        } else if (token == JsonToken.START_ARRAY) {
            this.contextStack.push(new ParserContext(false));
            return;
        }

        if (contextStack.isEmpty()) {
            // global context

            // there really shouldn't be any tokens other than START_ARRAY and START_OBJECT

        } else {
            // not global context
            ParserContext context = contextStack.peek();

            if (!context.isObject() && globalArrayAsStream) {
                if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
                    this.handler.handle(this.socket, new JsonField.BooleanField(parser.getBooleanValue()));

                } else if (token == JsonToken.VALUE_NUMBER_INT) {
                    this.handler.handle(this.socket, new JsonField.IntegerField(parser.getLongValue()));

                } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                    this.handler.handle(this.socket, new JsonField.FloatField(parser.getFloatValue()));

                } else if (token == JsonToken.VALUE_STRING) {
                    this.handler.handle(this.socket, new JsonField.StringField(parser.getText()));
                }

            } else if (token == JsonToken.FIELD_NAME) {
                //System.out.println("[JsonStreamParser][processToken] FieldName: \"" + parser.getText() + "\"");
                context.currentName = parser.getText();

            } else if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
                context.pushValue(new JsonField.BooleanField(parser.getBooleanValue()));

            } else if (token == JsonToken.VALUE_NUMBER_INT) {
                context.pushValue(new JsonField.IntegerField(parser.getLongValue()));

            } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                context.pushValue(new JsonField.FloatField(parser.getFloatValue()));

            } else if (token == JsonToken.VALUE_STRING) {
                //System.out.println("[JsonStreamParser][processToken] ValueString: \"" + parser.getText() + "\"");
                context.pushValue(new JsonField.StringField(parser.getText()));

            } else if (token == JsonToken.END_ARRAY) {
                if (contextStack.size() == 1 && this.globalArrayAsStream) {
                    // if in global array, and global arrays are considered streams
                    this.finished();

                } else {
                    JsonField array = new JsonField.ArrayField(context.getValues());
                    this.contextStack.pop();

                    if (contextStack.isEmpty()) {
                        // was in global array, not considering global arrays streams
                        this.handler.handle(this.socket, array);
                        this.finished();

                    } else {
                        // was not in global array
                        context = this.contextStack.peek();
                        context.pushValue(array);
                    }
                }

            } else if (token == JsonToken.END_OBJECT) {
                JsonField obj = new JsonField.ObjectField(context.getFields());
                this.contextStack.pop();
                if (this.contextStack.isEmpty()) {
                    // was in global scope
                    this.handler.handle(this.socket, obj);
                    this.finished();

                } else {
                    context = contextStack.peek();
                    if (!context.isObject && this.globalArrayAsStream) {
                        // if now in global array, which is considered a stream
                        this.handler.handle(this.socket, obj);

                    } else {
                        // not in array stream
                        context.pushValue(obj);
                    }
                }

            }
        }
    }

    @FunctionalInterface
    public interface Handler {
        void handle(Socket socket, JsonField field);
    }

    private class ParserContext {
        private boolean isObject;

        private Map<String, JsonField> fields;

        private List<JsonField> values;

        public String currentName;

        public ParserContext(boolean isObject) {
            this.isObject = isObject;
            if (this.isObject) {
                this.fields = new HashMap<>();

            } else {
                this.values = new LinkedList<>();
            }
        }

        public boolean isObject() {
            return this.isObject;
        }

        public Map<String, JsonField> getFields() {
            return this.fields;
        }

        public List<JsonField> getValues() {
            return this.values;
        }

        public void pushValue(JsonField value) {
            if (this.isObject) {
                this.fields.put(this.currentName, value);

            } else {
                this.values.add(value);
            }
        }
    }

    public enum ParserState {
        NEW(true),
        WAITING(true),
        READY(true),
        ERROR(false),
        CLOSED(false),
        COMPLETE(false),
        ABORTED(false);

        private final boolean alive;

        ParserState(boolean live) {
            this.alive = live;
        }

        public boolean isAlive() {
            return this.alive;
        }
    }
}
