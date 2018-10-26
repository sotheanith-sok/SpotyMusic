package net.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import net.connect.Session;
import net.lib.Socket;
import utils.CountingOutputStream;

import java.io.IOException;

public abstract class JsonStreamGenerator extends StreamGenerator {

    protected static JsonFactory factory = new JsonFactory();

    protected JsonGenerator gen;

    protected CountingOutputStream counter;

    public JsonStreamGenerator(Socket socket, boolean autoClose) {
        super(socket, autoClose);
    }

    @Override
    protected void initialize() throws IOException {
        super.initialize();
        this.gen = factory.createGenerator((this.counter = new CountingOutputStream(this.dest)), JsonEncoding.UTF8);
    }

    @Override
    protected void finished() {
        try {
            //this.gen.close();
            this.gen.flush();

        } catch (IOException e) {
            System.err.println("[JsonStreamGenerator][finished] IOException while closing JsonGenerator");
        }

        super.finished();
    }
}
