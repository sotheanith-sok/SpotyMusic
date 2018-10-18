package net.common;

import net.lib.Socket;

import java.io.IOException;
import java.util.Iterator;

public class IterativeStreamingJsonSerializer<T> extends JsonStreamGenerator {

    private Iterator<T> source;
    private JsonSerializer<T> serializer;

    public IterativeStreamingJsonSerializer(Socket socket, boolean autoClose, Iterator<T> source, JsonSerializer<T> serializer) {
        super(socket, autoClose);
        this.source = source;
        this.serializer = serializer;
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.gen.writeStartArray();
    }

    @Override
    protected void transfer(int maxSize) throws IOException {
        this.counter.reset();
        while (this.counter.getCount() < maxSize && this.source.hasNext()) {
            this.serializer.serialize(this.source.next(), this.gen);
        }

        if (!source.hasNext()) {
            this.gen.writeEndArray();
            this.finished();
        }
    }
}
