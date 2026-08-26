package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the error-handler half of scan/ScanFilters.cpp (CScanFilters::Start/Scan) - for
 * every filter with error handling enabled (errorFilterOptions == AR_ERRHANDLER_ENABLE) and a
 * named target filter that actually exists (CScanFilters::Scan's own "if (errFlt.Exists())" gate -
 * confirmed via a live re-check of scan/ScanFilters.cpp: CARFilter::ErrorCallers() genuinely IS
 * populated by the real tool, contrary to this class's own earlier javadoc, which incorrectly
 * claimed it was "confirmed dead" - see {@link #callers}), records the reverse mapping
 * target-filter-name -&gt; calling-filter-names, which is exactly what FilterErrorHandlersPage
 * needs to answer "which filters does the server actually use as an error handler". Same
 * fetch-once-per-object-then-index pattern as GlobalFieldIndex/WorkflowReferenceIndex.
 */
public final class FilterErrorHandlerIndex {
    public record Caller(String name, boolean enabled, int order) {}

    private final Map<String, List<Caller>> callersByHandler = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static FilterErrorHandlerIndex build(WorkflowSource repo) throws ARException {
        return build(repo, null, null);
    }

    public static FilterErrorHandlerIndex build(WorkflowSource repo, ReadPool reads, Function<ArClient, WorkflowSource> repoFactory) throws ARException {
        FilterErrorHandlerIndex idx = new FilterErrorHandlerIndex();
        java.util.Set<String> allFilterNames = new java.util.HashSet<>(repo.listFilterNames());

        if (reads == null) {
            for (String name : repo.listFilterNames()) {
                try {
                    idx.indexFilter(repo.getFilter(name), name, allFilterNames);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing filter error handlers for '" + name + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : repo.listFilterNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexFilter(repoFactory.apply(c).getFilter(name), name, allFilterNames); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing filter error handlers for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    /** allFilterNames: matches CScanFilters::Scan's "if (errFlt.Exists())" gate - a filter naming a nonexistent/typo'd error handler is silently skipped, not indexed under a dangling key. */
    private void indexFilter(Filter flt, String callerName, java.util.Set<String> allFilterNames) {
        if (flt.getErrorFilterOptions() != Constants.AR_ERRHANDLER_ENABLE) return;
        String handlerName = flt.getErrorHandlingFilter();
        if (handlerName == null || handlerName.isBlank() || !allFilterNames.contains(handlerName)) return;
        callersByHandler.computeIfAbsent(handlerName, k -> Collections.synchronizedList(new ArrayList<>())).add(new Caller(callerName, flt.isEnable(), flt.getOrder()));
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    /** True if at least one other filter names filterName as its error handler. */
    public boolean isUsedAsErrorHandler(String filterName) {
        List<Caller> callers = callersByHandler.get(filterName);
        return callers != null && !callers.isEmpty();
    }

    /**
     * Every other filter that selected filterName as its Error Handler - a genuine port of
     * DocFilterDetails.cpp's WorkflowReferences() (CARFilter::ErrorCallers(), populated by
     * scan/ScanFilters.cpp's CScanFilters::Start/Scan), not a new post-C++ capability. An earlier
     * version of this javadoc (and of FilterDetailPage's own comment on its caller) claimed this
     * mechanism was "confirmed dead" in the real tool - that was wrong: a second, closer read of
     * ScanFilters.cpp found the real population site (Start()'s errCalls map, built during the
     * scan phase and copied into each target filter's ErrorCallers() vector), missed by whatever
     * grep the original claim was based on. The real C++'s table id/columns/heading ("Workflow
     * Reference:", Type/Server object/Enabled/Description, "Selected as Error Handler") already
     * match what FilterDetailPage.workflowReferences() renders from this index.
     */
    public List<Caller> callers(String filterName) {
        return callersByHandler.getOrDefault(filterName, List.of());
    }
}
