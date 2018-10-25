package utils;

/**
 * Represents a task that may yield without being finished.
 */
public interface CompletableRunnable {
    /**
     * Runs the task. Returns true if the task is finished, false otherwise.
     *
     * @return finished
     */
    boolean run() throws Exception;
}
