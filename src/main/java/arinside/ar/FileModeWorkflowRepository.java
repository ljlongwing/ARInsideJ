package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.Menu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WorkflowSource backed by an AR System Administrator XML export file - see
 * FileModeSchemaRepository's javadoc for the live-connection caveat this shares. All four object
 * types are pre-loaded once in the constructor (one *FromDef RPC per type), unlike the live
 * WorkflowRepository which fetches per-object on demand - a def file is expected to hold a small,
 * hand-picked object set, so loading everything up front is cheap and simpler than lazy per-name
 * lookups against a file-backed source. No live per-object calls remain after construction (the
 * former expandMenu() live call - see MenuDetailPage's javadoc for why it was removed entirely -
 * was the last one), so unlike an earlier round of this port, file mode's write pool no longer
 * needs any connection-sharing synchronization at all.
 */
public final class FileModeWorkflowRepository implements WorkflowSource {
    private final Map<String, ActiveLink> activeLinksByName = new HashMap<>();
    private final Map<String, Filter> filtersByName = new HashMap<>();
    private final Map<String, Escalation> escalationsByName = new HashMap<>();
    private final Map<String, Menu> menusByName = new HashMap<>();

    public FileModeWorkflowRepository(ArClient client, String defFile) {
        try {
            loadFirst(client.raw().getListActiveLinksFromDef(defFile, null, 0, false), activeLinksByName);
            loadFirst(client.raw().getListFiltersFromDef(defFile, null, 0, false), filtersByName);
            loadFirst(client.raw().getListEscalationsFromDef(defFile, null, 0, false), escalationsByName);
            loadFirst(client.raw().getListMenusFromDef(defFile, null, 0, false), menusByName);
        } catch (ARException | IOException e) {
            throw new RuntimeException("Failed reading workflow objects from def file '" + defFile + "': " + e.getMessage(), e);
        }
    }

    private static <T> void loadFirst(Map<String, List<T>> raw, Map<String, T> target) {
        if (raw == null) return;
        for (var entry : raw.entrySet()) {
            if (!entry.getValue().isEmpty() && entry.getValue().get(0) != null) {
                target.put(entry.getKey(), entry.getValue().get(0));
            }
        }
    }

    private static List<String> sortedKeys(Map<String, ?> map) {
        List<String> names = new ArrayList<>(map.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override public List<String> listActiveLinkNames() { return sortedKeys(activeLinksByName); }
    @Override public ActiveLink getActiveLink(String name) { return activeLinksByName.get(name); }

    @Override public List<String> listFilterNames() { return sortedKeys(filtersByName); }
    @Override public Filter getFilter(String name) { return filtersByName.get(name); }

    @Override public List<String> listEscalationNames() { return sortedKeys(escalationsByName); }
    @Override public Escalation getEscalation(String name) { return escalationsByName.get(name); }

    @Override public List<String> listMenuNames() { return sortedKeys(menusByName); }
    @Override public Menu getMenu(String name) { return menusByName.get(name); }
}
