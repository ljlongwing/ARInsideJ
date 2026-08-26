package arinside.ar;

import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of the active link / filter / escalation / menu loading portions of
 * CARInside::LoadActiveLinks/LoadFilters/LoadEscalations/LoadCharMenus (ARInside.cpp) and their
 * lists/AR*List.cpp counterparts - collapsed into one repository per the port plan (see
 * ArClient's javadoc).
 */
public final class WorkflowRepository implements WorkflowSource {
    private final ArClient client;
    private final BlackList blackList;
    private final WorkflowBulkCache cache;

    /** No bulk cache - always fetches live, one object per call (matches this class's original behavior). */
    public WorkflowRepository(ArClient client, BlackList blackList) {
        this(client, blackList, null);
    }

    /** cache may be null (no bulk cache built, or this repository intentionally shouldn't use one) - falls back to live per-name fetches exactly as before. See WorkflowBulkCache's javadoc. */
    public WorkflowRepository(ArClient client, BlackList blackList, WorkflowBulkCache cache) {
        this.client = client;
        this.blackList = blackList;
        this.cache = cache;
    }

    private static List<String> sorted(java.util.Collection<String> names, java.util.function.Predicate<String> excluded) {
        List<String> copy = new ArrayList<>(names);
        copy.removeIf(excluded);
        Collections.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    public List<String> listActiveLinkNames() throws ARException {
        if (cache != null && cache.hasActiveLinks()) return sorted(cache.activeLinkNames(), blackList::containsActiveLink);
        return sorted(client.raw().getListActiveLink(), blackList::containsActiveLink);
    }

    public ActiveLink getActiveLink(String name) throws ARException {
        if (cache != null) {
            ActiveLink al = cache.activeLink(name);
            if (al != null) return al;
        }
        return client.raw().getActiveLink(name);
    }

    public List<String> listFilterNames() throws ARException {
        if (cache != null && cache.hasFilters()) return sorted(cache.filterNames(), blackList::containsFilter);
        return sorted(client.raw().getListFilter(), blackList::containsFilter);
    }

    public Filter getFilter(String name) throws ARException {
        if (cache != null) {
            Filter f = cache.filter(name);
            if (f != null) return f;
        }
        return client.raw().getFilter(name);
    }

    public List<String> listEscalationNames() throws ARException {
        if (cache != null && cache.hasEscalations()) return sorted(cache.escalationNames(), blackList::containsEscalation);
        return sorted(client.raw().getListEscalation(), blackList::containsEscalation);
    }

    public Escalation getEscalation(String name) throws ARException {
        if (cache != null) {
            Escalation e = cache.escalation(name);
            if (e != null) return e;
        }
        return client.raw().getEscalation(name);
    }

    public List<String> listMenuNames() throws ARException { return sorted(client.raw().getListMenu(0L, null, null), blackList::containsMenu); }

    /**
     * `new MenuCriteria()` (default, no flags set) never populates the subtype-specific definition
     * data - `getMenu()` comes back as the base type with every QueryMenu/SqlMenu/ListMenu/FileMenu/
     * DataDictionaryMenu accessor empty, which an earlier round of this port mistook for a genuine
     * Java-API limitation and worked around via the separate live `expandMenu()` RPC instead (now
     * removed - see MenuDetailPage's javadoc for why that approach was architecturally wrong and
     * caused real ERROR 314 failures for query/SQL menus). `setRetrieveAll(true)` is the fix,
     * confirmed via spike: with it, `getMenu()` returns the real typed subtype with real
     * qualification/SQL/items/filename data populated, no live definition-resolving call needed.
     */
    public Menu getMenu(String name) throws ARException {
        MenuCriteria criteria = new MenuCriteria();
        criteria.setRetrieveAll(true);
        return client.raw().getMenu(name, criteria);
    }
}
