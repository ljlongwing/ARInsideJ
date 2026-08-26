package arinside.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bounds how many worker threads render+write pages concurrently - pure local work (build an
 * HTML string, write it to disk), no AR System connection involved, so unlike {@link
 * arinside.ar.ReadPool} this is just a plain thread pool. {@code concurrency > 0}: fixed pool of
 * that size. {@code concurrency <= 0} ("unlimited"): {@link Executors#newCachedThreadPool()}, no
 * hard-coded cap.
 *
 * <p>No internal "drain" bookkeeping - see ReadPool's javadoc for why callers join directly on
 * the futures submit() returns instead.
 */
public final class WritePool implements AutoCloseable {

    @FunctionalInterface
    public interface WriteTask {
        void run() throws Exception;
    }

    private final ExecutorService executor;

    private WritePool(ExecutorService executor) {
        this.executor = executor;
    }

    public static WritePool open(int concurrency) {
        return new WritePool(concurrency > 0 ? Executors.newFixedThreadPool(concurrency) : Executors.newCachedThreadPool());
    }

    public CompletableFuture<Void> submit(WriteTask task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
