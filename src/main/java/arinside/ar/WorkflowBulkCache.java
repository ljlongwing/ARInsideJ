package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.EscalationCriteria;
import com.bmc.arsys.api.Filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of the real C++'s default "fast" object loading (lists/ARActiveLinkList.cpp's
 * LoadFromServer(), etc.) - one bulk ARGetMultipleActiveLinks/Filters/Escalations-equivalent RPC per
 * type (getListActiveLinkObjects()/getListFilterObjects()/getListEscalationObjects()), instead of
 * this port's original one-getActiveLink(name)-call-per-object pattern (which matched the C++'s
 * "--slow" fallback mode, not its default). Active link and filter bulk fetches return
 * fully-populated objects with no extra criteria tuning needed; escalations need an explicit
 * {@code criteria.setRetrieveAll(true)} (see below).
 *
 * <p>One instance is built once (see Main.java) right after the live connection opens and shared
 * (read-only, thread-safe by construction - never mutated after {@link #load}) across every
 * WorkflowRepository this run creates, including the per-pooled-connection instances the various
 * scan indexes build via their reads.submit(c -&gt; ...) factories - the cache data itself doesn't
 * depend on which connection built it. If a bulk call fails for any one type (matches the C++'s own
 * documented fallback trigger: "this could be necessary if there is a corrupt actlink that keeps us
 * from getting all activelinks at once"), that type's cache stays null and WorkflowRepository falls
 * back to its original per-name fetch for that type only - the other types keep their bulk speedup
 * independently.
 *
 * <p>{@code EscalationCriteria} inherits {@code setRetrieveAll(boolean)} from {@code
 * ObjectBaseCriteria}/{@code CriteriaFlags} without redeclaring it, so a bare {@code new
 * EscalationCriteria()} (retrieveAll unset) makes {@code getListEscalationObjects} return nothing;
 * {@code criteria.setRetrieveAll(true)} must be called explicitly to get real results - same fix
 * shape as {@code MenuCriteria} elsewhere in this port, just inherited rather than redeclared.
 */
public final class WorkflowBulkCache {
    private final Map<String, ActiveLink> activeLinks;
    private final Map<String, Filter> filters;
    private final Map<String, Escalation> escalations;

    private WorkflowBulkCache(Map<String, ActiveLink> activeLinks, Map<String, Filter> filters, Map<String, Escalation> escalations) {
        this.activeLinks = activeLinks;
        this.filters = filters;
        this.escalations = escalations;
    }

    public static WorkflowBulkCache load(ArClient client) {
        return new WorkflowBulkCache(loadActiveLinks(client), loadFilters(client), loadEscalations(client));
    }

    private static Map<String, ActiveLink> loadActiveLinks(ArClient client) {
        try {
            long t0 = System.currentTimeMillis();
            List<ActiveLink> all = client.raw().getListActiveLinkObjects();
            Map<String, ActiveLink> map = new HashMap<>(all.size() * 2);
            for (ActiveLink al : all) map.put(al.getName(), al);
            System.out.println("Bulk-loaded " + map.size() + " active links in " + (System.currentTimeMillis() - t0) + "ms");
            return map;
        } catch (ARException e) {
            System.out.println("WARN: bulk active link load failed (" + e.getMessage() + ") - falling back to per-object loading");
            return null;
        }
    }

    private static Map<String, Filter> loadFilters(ArClient client) {
        try {
            long t0 = System.currentTimeMillis();
            List<Filter> all = client.raw().getListFilterObjects();
            Map<String, Filter> map = new HashMap<>(all.size() * 2);
            for (Filter f : all) map.put(f.getName(), f);
            System.out.println("Bulk-loaded " + map.size() + " filters in " + (System.currentTimeMillis() - t0) + "ms");
            return map;
        } catch (ARException e) {
            System.out.println("WARN: bulk filter load failed (" + e.getMessage() + ") - falling back to per-object loading");
            return null;
        }
    }

    private static Map<String, Escalation> loadEscalations(ArClient client) {
        try {
            long t0 = System.currentTimeMillis();
            EscalationCriteria criteria = new EscalationCriteria();
            criteria.setRetrieveAll(true);
            List<Escalation> all = client.raw().getListEscalationObjects((String) null, 0L, criteria);
            Map<String, Escalation> map = new HashMap<>(all.size() * 2);
            for (Escalation e : all) map.put(e.getName(), e);
            System.out.println("Bulk-loaded " + map.size() + " escalations in " + (System.currentTimeMillis() - t0) + "ms");
            return map;
        } catch (ARException e) {
            System.out.println("WARN: bulk escalation load failed (" + e.getMessage() + ") - falling back to per-object loading");
            return null;
        }
    }

    boolean hasActiveLinks() { return activeLinks != null; }
    boolean hasFilters() { return filters != null; }
    boolean hasEscalations() { return escalations != null; }

    ActiveLink activeLink(String name) { return activeLinks == null ? null : activeLinks.get(name); }
    Filter filter(String name) { return filters == null ? null : filters.get(name); }
    Escalation escalation(String name) { return escalations == null ? null : escalations.get(name); }

    java.util.Set<String> activeLinkNames() { return activeLinks.keySet(); }
    java.util.Set<String> filterNames() { return filters.keySet(); }
    java.util.Set<String> escalationNames() { return escalations.keySet(); }
}
