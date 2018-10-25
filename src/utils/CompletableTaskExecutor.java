package utils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CompletableTaskExecutor extends ScheduledThreadPoolExecutor {

    private long delay;

    public CompletableTaskExecutor(int threadPoolSize, long delay) {
        super(threadPoolSize);
        this.delay = delay;
    }

    public Future<?> submit(CompletableRunnable task) {
        return this.new CompletableFutureTask(task);
    }

    class CompletableFutureTask implements RunnableFuture {

        private CompletableRunnable task;

        private Future<?> executorHandle;

        private AtomicInteger state;

        private Exception thrown;

        public CompletableFutureTask(CompletableRunnable task) {
            this.task = task;
            this.state = new AtomicInteger(NEW);
            this.executorHandle = CompletableTaskExecutor.this.submit(this);
        }

        @Override
        public void run() {
            this.state.set(RUNNING);
            boolean finished = false;
            try {
                finished = this.task.run();

            } catch (Exception e) {
                System.err.println("[CompletableFutureTask][run] Task threw an exception");
                e.printStackTrace();
                this.state.set(ERROR);
                this.thrown = e;
                return;
            }

            if (finished) {
                this.state.set(DONE);
                //System.out.println("[CompletableFutureTask][run] Task finished");

            } else {
                //System.out.println("[CompletableFutureTask][run] Task not finished");
                CompletableTaskExecutor.this.schedule(this, delay, TimeUnit.MILLISECONDS);
                this.state.set(WAITING);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (this.state.get() >= DONE) {
                return false;

            } else {
                boolean ret = this.executorHandle.cancel(mayInterruptIfRunning);
                if (ret) {
                    this.state.set(CANCELED);
                    return true;

                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return this.state.get() == CANCELED;
        }

        @Override
        public boolean isDone() {
            return this.state.get() >= DONE;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            switch (this.state.get()) {
                case ERROR : throw new ExecutionException(this.thrown);
                case CANCELED : throw new CancellationException();
                default : return this.executorHandle.get();
            }
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            switch (this.state.get()) {
                case ERROR : throw new ExecutionException(this.thrown);
                case CANCELED : throw new CancellationException();
                default : return this.executorHandle.get(timeout, unit);
            }
        }

        public static final int NEW = 1;
        public static final int WAITING = 2;
        public static final int RUNNING = 3;
        public static final int DONE = 4;
        public static final int ERROR = 5;
        public static final int CANCELED = 6;
    }
}
