package util;

@FunctionalInterface
public interface ProgressReporter {
    /**
     * Reports progress.
     * @param progress value between 0 and 1
     * @param status descriptive status text
     */
    void report(float progress, String status);
}
