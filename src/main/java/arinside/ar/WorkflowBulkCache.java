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
 * "--slow" fallback mode, not its default). Confirmed via a live spike against the test server: a
 * bulk fetch of 59,283 active links took ~1.9s and 34,315 filters ~1.1s, versus multi-minute full
 * scans for the same object counts under the old per-object pattern - and every field on a
 * bulk-fetched object (formList, order, executeMask, controlField, focusField, qualifier,
 * actionList, elseList, groupList, properties, owner, helpText, lastChangedBy, lastUpdateTime,
 * diary) matched a direct getActiveLink(name) fetch of the same object exactly, so - unlike
 * MenuCriteria's default (which needs an explicit setRetrieveAll(true) to avoid returning
 * mostly-empty objects) - the no-arg bulk calls here already return fully-populated objects with no
 * extra criteria tuning needed.
 *
 * One instance is built once (see Main.java) right after the live connection opens and shared
 * (read-only, thread-safe by construction - never mutated after {@link #load}) across every
 * WorkflowRepository this run creates, including the per-pooled-connection instances the various
 * scan indexes build via their reads.submit(c -&gt; ...) factories - the cache data itself doesn't
 * depend on which connection built it. If a bulk call fails for any one type (matches the C++'s own
 * documented fallback trigger: "this could be necessary if there is a corrupt actlink that keeps us
 * from getting all activelinks at once"), that type's cache stays null and WorkflowRepository falls
 * back to its original per-name fetch for that type only - the other types keep their bulk speedup
 * independently.
 *
 * Escalations initially looked broken (getListEscalationObjects returned 0 results against the live
 * test server no matter which formName/names-list shape was tried) - that was an incomplete
 * investigation, not a real jar limitation: EscalationCriteria's own class file has no
 * setRetrieveAll method, and javap doesn't surface inherited methods when a class is inspected in
 * isolation, so the fact that EscalationCriteria extends ObjectBaseCriteria extends CriteriaFlags
 * (which DOES declare setRetrieveAll(boolean)) was missed on the first pass. With
 * `new EscalationCriteria()` left at its default (retrieveAll unset), the bulk call apparently
 * returns nothing; with `criteria.setRetrieveAll(true)` explicitly called, it returns every real
 * escalation with fields matching a direct getEscalation() fetch exactly (confirmed via spike) -
 * same fix shape as MenuCriteria elsewhere in this port, just inherited rather than redeclared.
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
