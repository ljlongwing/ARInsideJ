package arinside.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coarse-grained instrumentation for the "is the runtime dominated by AR API round trips or by
 * local file writes" question. Two choke points are timed: WebPage.saveInFolder (every file this
 * tool writes goes through it) and SchemaRepository.getViews' per-VUI getView() loop (the one
 * confirmed one-round-trip-per-object network cost, since the bulk getListViewObjects call is
 * broken in this jar - see the project notes). Everything else (the bulk
 * getListXObjects() calls, local HTML rendering) is left as the implied remainder in summary()
 * rather than instrumented at every call site - a full per-call breakdown would mean touching
 * every repository class for a one-off diagnostic.
 */
public final class Timing {
    public static final AtomicLong writeNanos = new AtomicLong();
    public static final AtomicInteger writeCount = new AtomicInteger();
    public static final AtomicLong vuiFetchNanos = new AtomicLong();
    public static final AtomicInteger vuiFetchCount = new AtomicInteger();

    private Timing() {}

    public static long start() {
        return System.nanoTime();
    }

    public static void addWrite(long startNanos) {
        writeNanos.addAndGet(System.nanoTime() - startNanos);
        writeCount.incrementAndGet();
    }

    public static void addVuiFetch(long startNanos) {
        vuiFetchNanos.addAndGet(System.nanoTime() - startNanos);
        vuiFetchCount.incrementAndGet();
    }

    /**
     * writeNanos/vuiFetchNanos are summed across every thread that did work (see ReadPool/
     * WritePool) - at ReadConcurrency=1/WriteConcurrency=1 that sum equals real wall-clock time
     * spent in each bucket, so "% of total" is meaningful and the remainder ("everything else") is
     * the implied non-write/non-VUI time. Once concurrency > 1, multiple threads accumulate write
     * time simultaneously, so the sum can legitimately exceed total wall-clock elapsed time - a
     * plain "% of total" would go over 100% (and the remainder negative), so above roughly 1.05x
     * concurrency this reports "effective average parallelism" (accumulated / wall-clock) instead,
     * which stays meaningful at any concurrency level.
     */
    public static String summary(long totalNanos) {
        double totalMs = totalNanos / 1_000_000.0;
        double writeMs = writeNanos.get() / 1_000_000.0;
        double vuiMs = vuiFetchNanos.get() / 1_000_000.0;

        boolean concurrent = (writeMs + vuiMs) > totalMs * 1.05;
        if (!concurrent) {
            double otherMs = totalMs - writeMs - vuiMs;
            return String.format(
                "Timing summary:%n" +
                "  Total elapsed:                 %,10.0f ms%n" +
                "  File writes:                   %,10.0f ms (%d files, %5.1f%% of total)%n" +
                "  VUI fetch (per-object getView): %,9.0f ms (%d calls, %5.1f%% of total)%n" +
                "  Everything else (bulk AR gather + local rendering): %,10.0f ms (%5.1f%% of total)%n",
                totalMs,
                writeMs, writeCount.get(), pct(writeMs, totalMs),
                vuiMs, vuiFetchCount.get(), pct(vuiMs, totalMs),
                otherMs, pct(otherMs, totalMs)
            );
        }
        return String.format(
            "Timing summary (concurrent run - accumulated thread-time, not wall-clock shares):%n" +
            "  Total elapsed (wall-clock):     %,10.0f ms%n" +
            "  File writes:  %,12.0f ms accumulated (%d files, avg parallelism %.1fx)%n" +
            "  VUI fetch:    %,12.0f ms accumulated (%d calls, avg parallelism %.1fx)%n",
            totalMs,
            writeMs, writeCount.get(), writeMs / Math.max(totalMs, 1),
            vuiMs, vuiFetchCount.get(), vuiMs / Math.max(totalMs, 1)
        );
    }

    private static double pct(double part, double total) {
        return total <= 0 ? 0 : 100.0 * part / total;
    }
}
