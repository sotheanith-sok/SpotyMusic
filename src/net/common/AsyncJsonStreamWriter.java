package net.common;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncJsonStreamWriter implements Runnable {

    private static JsonFactory factory = new JsonFactory();

    private OutputStream out;

    private SynchronousQueue<JsonField> queue;

    private AtomicBoolean running;

    public AsyncJsonStreamWriter(OutputStream stream) {
        this.out = stream;
        this.queue = new SynchronousQueue<>();
        this.running = new AtomicBoolean(true);
    }

    @Override
    public void run() {
        JsonGenerator gen;

        try {
            gen = factory.createGenerator(this.out, JsonEncoding.UTF8);

        } catch (IOException e) {
            System.err.println("[AsyncJsonStreamWriter][run] IOException while creating JsonGenerator");
            e.printStackTrace();
            return;
        }

        while (this.running.get()) {
            try {
                JsonField field = queue.take();
                field.write(gen);

            } catch (InterruptedException e) {
                System.err.println("[AsyncJsonStreamWriter][run] Interrupted while polling queue");
                e.printStackTrace();

            } catch (IOException e) {
                System.err.println("[AsyncJsonStreamWriter][run] IOException while writing JsonField");
                e.printStackTrace();
                break;
            }
        }

        try {
            gen.close();
        } catch (IOException e) {
            System.err.println("[AsyncJsonStreamWriter][run] IOException while closing JsonGenerator");
        }

        if (!this.running.get()) {
            try {
                this.out.close();

            } catch (IOException e) {
                System.err.println("[AsyncJsonStreamWriter][run] IOException while closing OutputStream");
                e.printStackTrace();
            }
        }
    }

    public void enqueue(JsonField field) {
        this.queue.offer(field);
    }

    public void close() {
        this.running.set(false);
    }
}
