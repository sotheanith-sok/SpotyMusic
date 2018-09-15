package utils;

import java.util.concurrent.*;

/**
 * Debounces invocations of a Runnable. When the runnable is run, a timer is started. The task actually runs at the end
 * when the timer runs out. If {@link #run()} is invoked again before the timer runs out, then the invocation is
 * either ignored, as the task is already scheduled to run, or the timer is restarted.
 */
public class DebouncedRunnable {

    private final Runnable command;
    private final long delay;
    private final TimeUnit unit;
    private final boolean extend;
    private ScheduledExecutorService executor;

    private CompletableFuture<Boolean> completableFuture = null;
    private ScheduledFuture<?> scheduledFuture = null;

    /**
     * Creates a new DebouncedRunnable that runs the given command.
     *
     * @param command the command to run
     * @param delay how long to debounce invocations
     * @param unit the time unit of the delay parameter
     * @param extend whether debounced invocations reset the delay or are simply ignored
     * @param executor a ScheduledExecutorService to run the debounced task
     */
    public DebouncedRunnable(Runnable command, long delay, TimeUnit unit, boolean extend, ScheduledExecutorService executor) {
        this.command = command;
        this.delay = delay;
        this.unit = unit;
        this.extend = extend;
        this.executor = executor;
    }

    /**
     * Creates a new DebouncedRunnable that runs the given command.
     * Extend defaults to false.
     *
     * @param command the command to run
     * @param delay how long to debounce invocations
     * @param unit the time unit of delay
     * @param executor a ScheduledExecutorService to run the debounced task
     */
    public DebouncedRunnable(Runnable command, long delay, TimeUnit unit, ScheduledExecutorService executor) {
        this(command, delay, unit, false, executor);
    }

    /**
     * Debouncedly runs the command.
     */
    public Future<Boolean> run() {
        if (this.scheduledFuture == null) {
            // if no future, schedule task
            this.completableFuture = new CompletableFuture<>();
            this.scheduledFuture = this.executor.schedule(this.new CompletableRunnable(), this.delay, this.unit);
            return this.completableFuture;

        } else {
            if (this.scheduledFuture.isDone()) {
                // if done, schedule again
                this.completableFuture = new CompletableFuture<>();
                this.scheduledFuture = this.executor.schedule(this.new CompletableRunnable(), this.delay, this.unit);
                return this.completableFuture;

            } else {
                if (this.extend) {
                    // cancel task and reschedule with more delay
                    this.scheduledFuture.cancel(false);
                    this.completableFuture = new CompletableFuture<>();
                    this.scheduledFuture = this.executor.schedule(this.new CompletableRunnable(), this.delay, this.unit);
                    return this.completableFuture;
                }
                // if not extend, do nothing
                return this.completableFuture;
            }
        }
    }

    class CompletableRunnable implements Runnable {
        @Override
        public void run() {
            try {
                command.run();
                completableFuture.complete(true);

            } catch (Exception e) {
                completableFuture.complete(false);
            }
        }
    }
}
