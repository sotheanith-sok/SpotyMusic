package net.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import net.Constants;
import utils.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class JsonStreamParser implements Runnable {

    private static JsonFactory factory = new JsonFactory();

    private InputStream source;

    private Handler handler;

    private JsonParser parser;

    private Deque<ParserContext> contextStack;

    private boolean autoCloseSocket;

    private boolean finished = false;

    private boolean globalArrayAsStream = false;

    private Logger logger;

    public JsonStreamParser(InputStream source, boolean autoCloseSocket, Handler handler) {
        this.source = source;
        this.autoCloseSocket = autoCloseSocket;
        this.handler = handler;
        this.contextStack = new LinkedList<>();
        this.logger = new Logger("JsonStreamParser", Constants.WARN);
    }

    public JsonStreamParser(InputStream source, boolean autoCloseSocket, Handler handler, boolean globalArrayAsStream) {
        this(source, autoCloseSocket, handler);
        this.globalArrayAsStream = globalArrayAsStream;
    }

    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public void run() {
        try {
            this.logger.trace(" Initializing parser");
            this.initialize();

            this.logger.debug(" Parser initialized");

            while (!finished && this.parser.nextToken() != null) {
                this.processToken(this.parser.getCurrentToken(), this.parser);
            }

            if (!finished) this.finished();

        } catch (IOException e) {
            this.finished();
            if (this.parser.isClosed()) return;
            this.logger.warn(" IOException while parsing JSON input stream");
            //e.printStackTrace();
        }
    }

    protected void initialize() throws IOException {
        this.parser = factory.createParser(this.source);
    }

    protected void finished() {
        if (this.finished) return;
        this.logger.log("[finished] JsonStreamParser finished");
        finished = true;

        if (this.autoCloseSocket) {
            this.logger.log("[finished] JsonStream finished, closing source");
            try {
                this.source.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void abort() {
        this.finished = true;
        try {
            this.source.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void processToken(JsonToken token, JsonParser parser) throws IOException {
        this.logger.trace("[processToken] Token: " + token);

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
                    this.handler.handle(new JsonField.BooleanField(parser.getBooleanValue()));

                } else if (token == JsonToken.VALUE_NUMBER_INT) {
                    this.handler.handle(new JsonField.IntegerField(parser.getLongValue()));

                } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                    this.handler.handle(new JsonField.FloatField(parser.getFloatValue()));

                } else if (token == JsonToken.VALUE_STRING) {
                    this.handler.handle(new JsonField.StringField(parser.getText()));
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
                //System.out.println("[SocketJsonParser][processToken] ValueString: \"" + parser.getText() + "\"");
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
                        this.handler.handle(array);
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
                    this.handler.handle(obj);
                    this.finished();

                } else {
                    context = contextStack.peek();
                    if (!context.isObject && this.globalArrayAsStream) {
                        // if now in global array, which is considered a stream
                        this.handler.handle(obj);

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
        void handle(JsonField field);
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
}
