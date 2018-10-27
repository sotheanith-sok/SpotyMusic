package net.common;

import net.lib.Socket;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketJsonWriter extends JsonStreamGenerator {

    protected final Object queueLock;
    protected LinkedList<JsonField> que;

    private AtomicBoolean running;

    public SocketJsonWriter(Socket socket, boolean autoClose) {
        super(socket, autoClose);

        this.queueLock = new Object();
        this.que = new LinkedList<>();
        this.running = new AtomicBoolean(true);
    }

    @Override
    protected void transfer(int maxSize) throws Exception {
        synchronized (this.queueLock) {
            if (this.que.isEmpty() && this.running.get()) {
                this.waitingForSource();

            } else if (!this.que.isEmpty()) {
                //System.out.println("[SocketJsonWriter] SocketJsonWriter writing json");
                this.counter.reset();
                JsonField next;
                while (this.counter.getCount() < maxSize) {
                    if ((next = this.que.pollFirst()) == null) break;
                    next.write(this.gen);
                    //System.out.println("[SocketJsonWriter][transfer] Wrote a JsonField");
                }
            } else {
                //System.out.println("[SocketJsonWriter] SocketJsonWriter finished");
                this.finished();
            }
        }
    }

    public void que(JsonField field) {
        if (this.running.get()) {
            synchronized (this.queueLock) {
                this.que.addLast(field);
                //System.out.println("[SimpletJsonWriter][que] Queued a JsonField");
            }
        } else {
            throw new IllegalStateException("SocketJsonWriter is closed");
        }
    }

    public void complete() {
        //System.out.println("[SocketJsonWriter] Completed with " + this.que.size() + " items in queue");
        this.running.set(false);
    }
}
