package net.common;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Runnable class that can be used to run {@link DeferredJsonGenerator}s in a dedicated thread, allowing for
 * asynchronous generation of a JSON stream.
 */
public class AsyncJsonStreamGenerator implements Runnable {

    private static JsonFactory factory = new JsonFactory();

    private OutputStream out;

    private final Object queueLock;
    private LinkedList<DeferredJsonGenerator> queue;

    private AtomicBoolean running;

    /**
     * Creates a new AsyncJsonStreamGenerator that will write JSON to the provided OutputStream.
     *
     * @param out an OutputStream to write to
     */
    public AsyncJsonStreamGenerator(OutputStream out) {
        this.out = out;
        this.queueLock = new Object();
        this.queue = new LinkedList<>();
        this.running = new AtomicBoolean(true);
    }

    /**
     * Enqueues the given {@link DeferredJsonGenerator} to be run asynchronously.
     *
     * @param gen the deferred generator to enqueue
     */
    public void enqueue(DeferredJsonGenerator gen) {
        synchronized (this.queueLock) {
            this.queue.addLast(gen);
            this.queueLock.notifyAll();
        }
    }

    /**
     * Closes the AsyncJsonStreamGenerator. Any DeferredJsonGenerators that are currently being executed will be
     * completed before the AsyncJsonStreamGenerator closes, the underlying OutputStream is closed, and the thread
     * {@link #run()} function returns.
     */
    public void close() {
        this.running.set(false);
        synchronized (this.queueLock) {
            this.queueLock.notifyAll();
        }
    }

    @Override
    public void run() {
        JsonGenerator gen = null;

        try {
            gen = factory.createGenerator(this.out);

        } catch (IOException e) {
            System.err.println("[AsyncJsonStreamGenerator][run] IOException while creating JsonGenerator");
            e.printStackTrace();
            return;
        }

        while (this.running.get()) {
            synchronized (this.queueLock) {
                try {
                    while (!this.queue.isEmpty()) {
                        this.queue.removeFirst().generate(gen);
                    }

                } catch (IOException e) {
                    System.err.println("[AsyncJsonStreamGenerator][run] IOException while invoking deferred json generators");
                    e.printStackTrace();
                }

                try {
                    this.queueLock.wait();
                } catch (InterruptedException e) {}
            }
        }

        try {
            this.out.close();
        } catch (IOException e) {
            System.err.println("[AsyncJsonStreamGenerator][run] IOException while closing output stream");
            e.printStackTrace();
        }
    }
}
