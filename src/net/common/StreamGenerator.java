package net.common;

import net.connect.Session;
import utils.CompletableRunnable;

import java.io.IOException;
import java.io.OutputStream;

public abstract class StreamGenerator implements CompletableRunnable {

    protected Session session;

    private GeneratorState state;

    protected OutputStream dest;

    public StreamGenerator(Session session) {
        this.session = session;
        this.state = GeneratorState.NEW;
    }

    @Override
    public boolean run() throws Exception {
        if (this.state == GeneratorState.NEW) {
            try {
                this.initialize();

            } catch (IOException e) {
                this.finished();
                this.state = GeneratorState.ERROR;
                System.err.println("[StreamGenerator][update] IOException when initializing generator");
                e.printStackTrace();
                throw e;
            }
            this.state = GeneratorState.READY;
        }
/*
        if (!this.session.isConnected() || !this.session.isOutputOpened()) {
            this.state = GeneratorState.CLOSED;
        }
*/
        if (this.state == GeneratorState.READY) {
            // transfer data if there is space
            if (this.session.outputBufferSpace() > 0) {
                try {
                    this.transfer(this.session.outputBufferSpace());

                } catch (IOException e) {
                    this.finished();
                    this.state = GeneratorState.ERROR;
                    System.err.println("[StreamGenerator][update] IOException while transferring data");
                    e.printStackTrace();
                    throw e;

                } catch (Exception e) {
                    this.finished();
                    this.state = GeneratorState.ERROR;
                    System.err.println("[StreamGenerator][update] Non-IO related Exception while transferring data");
                    e.printStackTrace();
                    throw e;
                }

            } else {
                // if no space, switch to waiting
                this.state = GeneratorState.WAITING;
            }

        } else if (this.state == GeneratorState.WAITING) {
            if (this.session.outputBufferSpace() > 0) {
                this.state = GeneratorState.READY;
            }
        }

        return !this.state.isAlive();
    }

    protected void initialize() throws IOException {
        this.dest = this.session.outputStream();
    }

    protected abstract void transfer(int maxSize) throws Exception;

    public GeneratorState getState() {
        return this.state;
    }

    protected void waitingForSource() {
        this.state = GeneratorState.WAITING;
    }

    protected void finished() {
        this.state = GeneratorState.COMPLETE;
        try {
            this.session.closeSend();
        } catch (IOException e) {
            System.err.println("[StreamGenerator][finished] IOException while trying to close session");
            e.printStackTrace();
        }
    }

    public enum GeneratorState {
        NEW     (true),
        READY   (true),
        WAITING (true),
        ERROR   (false),
        CLOSED  (false),
        COMPLETE(false);

        private final boolean alive;

        GeneratorState(boolean alive) {
            this.alive = alive;
        }

        public boolean isAlive() {
            return this.alive;
        }
    }
}
