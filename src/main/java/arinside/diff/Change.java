package arinside.diff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One object-level difference between two snapshots. {@code before}/{@code after} are the raw
 * {@code com.bmc.arsys.api.*} objects (one is null for ADDED/REMOVED); {@link arinside.doc.DiffReportPage}
 * re-derives the detailed diff from them via {@link arinside.doc.OverlayDiff}.
 */
public final class Change {
    public enum Kind { ADDED, REMOVED, MODIFIED }

    public final String typeLabel;   // "Form", "Active Link", ...
    public final String typeSlug;    // "form", "active_link", ... (also the diff/<slug>/ directory)
    public final String name;
    public final Kind kind;
    public final Object before;      // snapshot A object, null when ADDED
    public final Object after;       // snapshot B object, null when REMOVED

    /** Short human-readable change lines (MODIFIED only) - shown on the index list and in diff.json. */
    public final List<String> summary = new ArrayList<>();
    /** Machine-readable detail for diff.json. */
    public final Map<String, Object> json = new LinkedHashMap<>();

    public Change(String typeLabel, String typeSlug, String name, Kind kind, Object before, Object after) {
        this.typeLabel = typeLabel;
        this.typeSlug = typeSlug;
        this.name = name;
        this.kind = kind;
        this.before = before;
        this.after = after;
    }

    public String kindLabel() {
        return switch (kind) { case ADDED -> "Added"; case REMOVED -> "Removed"; case MODIFIED -> "Modified"; };
    }
}
