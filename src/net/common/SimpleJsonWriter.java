package net.common;

import net.connect.Session;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleJsonWriter extends JsonStreamGenerator {

    protected final Object queueLock;
    protected LinkedList<JsonField> que;

    private AtomicBoolean running;

    public SimpleJsonWriter(Session session) {
        super(session);

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
                this.counter.reset();
                JsonField next;
                while (this.counter.getCount() < maxSize) {
                    if ((next = this.que.pollFirst()) == null) break;
                    next.write(this.gen);
                }
            } else {
                this.finished();
            }
        }
    }

    public void que(JsonField field) {
        if (this.running.get()) {
            synchronized (this.queueLock) {
                this.que.addLast(field);
            }
        } else {
            throw new IllegalStateException("SimpleJsonWriter is closed");
        }
    }

    public void complete() {
        this.running.set(false);
    }
}
