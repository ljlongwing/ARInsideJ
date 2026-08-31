package arinside.ar.is;

import arinside.util.JsonReader;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulls the Innovation Studio definition inventory over {@link IsClient}. One list call per
 * {@link IsDefType}; the DataPage responses already carry the full object (not a summary), so
 * there is no per-object detail round trip. Definitions keep their raw parsed JSON map -
 * {@code doc/is} renderers read type-specifics from it directly (same flat-map approach the
 * sibling {@code documentor} tool's {@code IsSerializer} takes).
 */
public final class IsRepository {

    private final IsClient client;
    private final List<IsBundle> bundles = new ArrayList<>();
    private final Map<IsDefType, List<IsDefinition>> byType = new LinkedHashMap<>();

    private IsRepository(IsClient client) { this.client = client; }

    public static IsRepository load(IsClient client) {
        IsRepository repo = new IsRepository(client);
        repo.client.login();
        repo.loadBundles();
        repo.loadDefinitions();
        return repo;
    }

    /** Build a repository from already-materialised data (tests / offline fixtures). */
    public static IsRepository of(List<IsBundle> bundles, Map<IsDefType, List<IsDefinition>> byType) {
        IsRepository repo = new IsRepository(null);
        if (bundles != null) repo.bundles.addAll(bundles);
        if (byType != null) repo.byType.putAll(byType);
        return repo;
    }

    private void loadBundles() {
        for (Object o : client.dataPage("com.bmc.arsys.rx.application.bundle.datapage.BundleDescriptorDataPageQuery")) {
            bundles.add(new IsBundle(
                JsonReader.str(o, "id"),
                JsonReader.str(o, "name"),
                JsonReader.str(o, "friendlyName"),
                JsonReader.str(o, "version"),
                JsonReader.str(o, "developerId"),
                JsonReader.str(o, "description"),
                JsonReader.bool(o, "isApplication"),
                JsonReader.str(o, "lastDeployedTime"),
                JsonReader.asMap(o)));
        }
        bundles.sort((a, b) -> String.valueOf(a.id()).compareToIgnoreCase(String.valueOf(b.id())));
    }

    private void loadDefinitions() {
        for (IsDefType type : IsDefType.values()) {
            List<IsDefinition> list = new ArrayList<>();
            try {
                for (Object o : client.dataPage(type.dataPageQuery)) {
                    list.add(toDefinition(type, o));
                }
            } catch (RuntimeException e) {
                // one flaky type (processes 500 intermittently) shouldn't lose the whole IS pull
                System.out.println("IS: could not list " + type.pluralLabel + " - " + e.getMessage());
            }
            list.sort((a, b) -> String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));
            byType.put(type, list);
        }
    }

    private static IsDefinition toDefinition(IsDefType type, Object o) {
        Map<String, Object> raw = JsonReader.asMap(o);
        Boolean enabled = raw.containsKey("isEnabled") ? JsonReader.bool(o, "isEnabled") : null;
        return new IsDefinition(
            type,
            JsonReader.str(o, "name"),
            JsonReader.str(o, "description"),
            enabled,
            JsonReader.str(o, "lastChangedBy"),
            epoch(JsonReader.str(o, "lastUpdateTime")),
            JsonReader.str(o, "owner"),
            JsonReader.str(o, "overlayGroupId"),
            JsonReader.str(o, "scope"),
            JsonReader.str(o, "guid"),
            raw);
    }

    /** IS timestamps are ISO-8601 with an offset, e.g. {@code 2026-08-31T13:29:03.000+0000}. */
    private static Long epoch(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(iso).toInstant().getEpochSecond();
        } catch (DateTimeParseException e) {
            try { return Instant.parse(iso).getEpochSecond(); } catch (DateTimeParseException e2) { return null; }
        }
    }

    /* ---------- accessors ---------- */

    public List<IsBundle> bundles() { return bundles; }

    public List<IsDefinition> of(IsDefType type) { return byType.getOrDefault(type, List.of()); }

    public int totalDefinitions() {
        return byType.values().stream().mapToInt(List::size).sum();
    }

    public boolean isEmpty() { return totalDefinitions() == 0 && bundles.isEmpty(); }
}
