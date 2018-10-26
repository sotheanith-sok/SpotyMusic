package net.common;

import net.lib.Socket;

import java.io.IOException;
import java.io.OutputStream;

public abstract class StreamGenerator implements Runnable {

    protected Socket socket;

    private GeneratorState state;

    protected OutputStream dest;

    private boolean autoClose = false;

    public StreamGenerator(Socket socket, boolean autoClose) {
        this.socket = socket;
        this.state = GeneratorState.NEW;
        this.autoClose = autoClose;
    }

    @Override
    public void run() {
        try {
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

            while (this.state.isAlive()) {
                if (this.socket.isClosed()) {
                    this.state = GeneratorState.CLOSED;
                }

                if (this.state == GeneratorState.READY) {
                    // transfer data if there is space
                    if (this.socket.outputBufferSpace() > 0) {
                        try {
                            this.transfer(this.socket.outputBufferSpace());

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
                    if (this.socket.outputBufferSpace() > 0) {
                        Thread.sleep(250);
                        this.state = GeneratorState.READY;
                    }
                }
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    protected void initialize() throws IOException {
        this.dest = this.socket.outputStream();
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
        if (this.autoClose) {
            //System.out.println("[StreamGenerator][finished] StreamGenerator finished, closing socket");
            this.socket.close();
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
