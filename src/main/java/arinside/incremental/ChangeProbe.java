package arinside.incremental;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import arinside.ar.ArClient;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.ObjectPropertyMap;

/**
 * Cheap "did anything change on the server since the last run" check for incremental mode
 * ({@code IncrementalRuns} / {@code --incremental}) - names only, no full object fetch. For each workflow-object type it asks the server twice:
 * {@code getListX(sinceEpoch)} (anything added or modified in the window) and {@code getListX(0L)}
 * (every current name, to spot deletions against the recorded set). Any hit anywhere means the run
 * proceeds in full. Users/groups/roles are not probed - they are not part of a snapshot the way the
 * workflow object graph is, and are cheap to re-document anyway.
 */
public final class ChangeProbe {
    private ChangeProbe() {}

    /** schema / activelink / filter / escalation / menu / container / image - the manifest keys. */
    public static final List<String> TYPES =
        List.of("schema", "activelink", "filter", "escalation", "menu", "container", "image");

    private static final int[] CONTAINER_TYPES = {
        Constants.ARCON_GUIDE, Constants.ARCON_APP, Constants.ARCON_PACK,
        Constants.ARCON_FILTER_GUIDE, Constants.ARCON_WEBSERVICE
    };

    /** Every current object name per type - written into the manifest at the end of a full run. */
    public static void snapshotInto(ArClient client, RunState state) throws ARException {
        for (String type : TYPES) {
            state.names.put(type, new LinkedHashSet<>(allNames(client, type, 0L)));
        }
    }

    /**
     * The first concrete change found versus {@code prev}, or empty when the output is still current.
     * The string is for the console line only.
     */
    public static Optional<String> firstChange(ArClient client, RunState prev) throws ARException {
        long since = prev.probeTime;
        for (String type : TYPES) {
            List<String> touched = allNames(client, type, since);
            if (!touched.isEmpty()) {
                return Optional.of(touched.size() + " " + type + " object(s) added/modified (e.g. \"" + touched.get(0) + "\")");
            }
            Set<String> current = new LinkedHashSet<>(allNames(client, type, 0L));
            Set<String> recorded = prev.names.getOrDefault(type, Set.of());
            if (!current.equals(recorded)) {
                Set<String> removed = new LinkedHashSet<>(recorded);
                removed.removeAll(current);
                Set<String> added = new LinkedHashSet<>(current);
                added.removeAll(recorded);
                String detail = !removed.isEmpty() ? removed.size() + " removed (e.g. \"" + removed.iterator().next() + "\")"
                    : added.size() + " added (e.g. \"" + added.iterator().next() + "\")";
                return Optional.of(type + " list changed: " + detail);
            }
        }
        return Optional.empty();
    }

    private static List<String> allNames(ArClient client, String type, long since) throws ARException {
        var raw = client.raw();
        return switch (type) {
            case "schema" -> raw.getListForm(since);
            case "activelink" -> raw.getListActiveLink(since);
            case "filter" -> raw.getListFilter(since);
            case "escalation" -> raw.getListEscalation(since);
            case "menu" -> raw.getListMenu(since, null, null);
            case "container" -> raw.getListContainer(since, CONTAINER_TYPES, true, null, (ObjectPropertyMap) null);
            case "image" -> raw.getListImage(since);
            default -> List.of();
        };
    }
}
