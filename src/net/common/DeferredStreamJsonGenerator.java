package net.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;

public class DeferredStreamJsonGenerator implements Runnable {

    private static JsonFactory factory = new JsonFactory();

    private boolean autoClose;

    private DeferredJsonGenerator generator;

    private OutputStream out;

    /**
     * Creates a new DeferredStreamJsonGenerator which invokes the given {@link DeferredJsonGenerator} to generate
     * JSON at a later time.
     *
     * @param output an OutputStream to write JSON to
     * @param autoClose whether the given OutputStream should be closed when the given generator is finished
     * @param gen a {@link DeferredJsonGenerator} to invoke
     */
    public DeferredStreamJsonGenerator(OutputStream output, boolean autoClose, DeferredJsonGenerator gen) {
        this.out = output;
        this.autoClose = autoClose;
        this.generator = gen;
    }

    @Override
    public void run() {
        JsonGenerator gen;
        try {
            gen = factory.createGenerator(out, JsonEncoding.UTF8);
        } catch (IOException e) {
            System.err.println("[DeferredStreamJsonGenerator][run] IOException while creating JsonGenerator");
            e.printStackTrace();
            try {
                this.out.close();
            } catch (IOException e1) {}
            return;
        }

        try {
            this.generator.generate(gen);
            gen.close();
            if (this.autoClose) this.out.close();

        } catch (IOException e) {
            if (gen.isClosed()) return;
            System.err.println("[DeferredStreamJsonGenerator][run] IOException while running deferred generator task");
            e.printStackTrace();
        }
    }

}
