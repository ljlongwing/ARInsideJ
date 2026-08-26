package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.CallGuideAction;
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
 * Java port of doc/DocAlGuideDetails.cpp's ActiveLinkActions() and doc/DocFilterGuideDetails.cpp's
 * FilterActions() - "which active links / filters call this guide via a Call Guide action", built
 * as a shared reverse index (same established pattern as FilterErrorHandlerIndex/
 * ContainerReferenceIndex) rather than a live per-guide scan of every AL/filter's action list. Active
 * link guides are only ever called from active link actions, filter guides only from filter actions
 * (matches the real C++, which scans alList for CDocAlGuideDetails and filterList for
 * CDocFilterGuideDetails - never the other way round) - kept as two separate maps rather than one to
 * preserve that distinction.
 */
public final class GuideCallIndex {
    public record Caller(String objectName, String actionLabel) {}

    private final Map<String, List<Caller>> byAlGuide = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> byFilterGuide = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static GuideCallIndex build(WorkflowSource repo) throws ARException {
        return build(repo, null, null);
    }

    public static GuideCallIndex build(WorkflowSource repo, ReadPool reads, Function<ArClient, WorkflowSource> repoFactory) throws ARException {
        GuideCallIndex idx = new GuideCallIndex();

        if (reads == null) {
            for (String name : repo.listActiveLinkNames()) {
                try {
                    idx.indexAl(repo.getActiveLink(name), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing guide calls for AL '" + name + "': " + e.getMessage());
                }
            }
            for (String name : repo.listFilterNames()) {
                try {
                    idx.indexFilter(repo.getFilter(name), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing guide calls for filter '" + name + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : repo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexAl(repoFactory.apply(c).getActiveLink(name), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing guide calls for AL '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : repo.listFilterNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexFilter(repoFactory.apply(c).getFilter(name), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing guide calls for filter '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexAl(ActiveLink al, String name) {
        indexActions(byAlGuide, al.getActionList(), name, "If-Action");
        indexActions(byAlGuide, al.getElseList(), name, "Else-Action");
    }

    private void indexFilter(Filter flt, String name) {
        indexActions(byFilterGuide, flt.getActionList(), name, "If-Action");
        indexActions(byFilterGuide, flt.getElseList(), name, "Else-Action");
    }

    private <A> void indexActions(Map<String, List<Caller>> byGuide, List<A> actions, String callerName, String branchLabel) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i) instanceof CallGuideAction cg) {
                String guideName = cg.getGuideName();
                if (guideName != null && !guideName.isEmpty()) {
                    byGuide.computeIfAbsent(guideName, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(new Caller(callerName, branchLabel + " " + i));
                }
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    /** Every active link whose action list calls this AL guide by name, matching DocAlGuideDetails.cpp's ActiveLinkActions(). */
    public List<Caller> alCallers(String guideName) { return byAlGuide.getOrDefault(guideName, List.of()); }

    /** Every filter whose action list calls this filter guide by name, matching DocFilterGuideDetails.cpp's FilterActions(). */
    public List<Caller> filterCallers(String guideName) { return byFilterGuide.getOrDefault(guideName, List.of()); }
}
