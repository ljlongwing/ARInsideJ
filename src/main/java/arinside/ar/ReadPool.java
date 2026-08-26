package arinside.ar;

import arinside.config.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bounds how many AR System connections are open and in use at once for object fetching. Each
 * pool worker thread gets exactly one dedicated {@link ArClient} connection (opened lazily on
 * that thread's first task, reused for the rest of that thread's lifetime) rather than sharing
 * one connection across threads - {@code ARServerUser} isn't documented as thread-safe for
 * concurrent calls, and the user's own direction for this feature was "create multiple
 * connections" rather than risk sharing one.
 *
 * <p>{@code concurrency > 0}: a fixed pool of exactly that many worker threads/connections.
 * {@code concurrency <= 0} ("unlimited"): a {@link Executors#newCachedThreadPool()} with no
 * hard-coded cap - it grows a new thread (and therefore opens a new connection, via the same
 * lazy-per-thread mechanism) only as fast as work arrives and reuses idle threads/connections for
 * new work, so total connections opened tracks peak concurrent in-flight fetches, not total task
 * count. A literal one-connection-per-submitted-task reading of "unlimited" isn't practical (a
 * 59,283-item phase would try to open 59,283 simultaneous server logins) and isn't what this
 * setting is for - this is the intended, documented interpretation.
 *
 * <p>No internal "drain" bookkeeping here - callers that need a barrier (see
 * Main.documentEachParallel) join directly on the CompletableFuture chain each submit() call
 * returns, which has unambiguous JDK-guaranteed completion semantics, rather than relying on this
 * pool tracking submitted work itself.
 */
public final class ReadPool implements AutoCloseable {

    @FunctionalInterface
    public interface FetchTask<T> {
        T run(ArClient client) throws Exception;
    }

    private final ExecutorService executor;
    private final ThreadLocal<ArClient> connection;
    private final List<ArClient> opened = Collections.synchronizedList(new ArrayList<>());

    private ReadPool(AppConfig cfg, ExecutorService executor) {
        this.executor = executor;
        this.connection = ThreadLocal.withInitial(() -> {
            try {
                ArClient c = ArClient.connect(cfg);
                opened.add(c);
                return c;
            } catch (Exception e) {
                throw new RuntimeException("Failed opening pooled read connection: " + e.getMessage(), e);
            }
        });
    }

    public static ReadPool open(AppConfig cfg, int concurrency) {
        ExecutorService ex = concurrency > 0 ? Executors.newFixedThreadPool(concurrency) : Executors.newCachedThreadPool();
        return new ReadPool(cfg, ex);
    }

    /** Runs task on a pooled connection, on whichever pool thread picks it up. */
    public <T> CompletableFuture<T> submit(FetchTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                future.complete(task.run(connection.get()));
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
        for (ArClient c : opened) {
            c.close();
        }
    }
}
