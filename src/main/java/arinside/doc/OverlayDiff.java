package arinside.doc;

import arinside.ar.ARPropertyLabels;
import arinside.output.ImageTag;
import arinside.output.Table;
import arinside.output.TableRow;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.PropertyMap;
import com.bmc.arsys.api.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Generic base-vs-overlay comparison helpers behind the "Changes from Base Layer" feature - a
 * capability the C++ original never had at all (confirmed with the user), so there's no port to
 * match here, purely a from-scratch design. Compares two full fetches of the same object (the
 * active/overlay layer, fetched normally, vs. the hidden base layer, fetched with the session's
 * overlay group set to "-2" - see {@link arinside.ar.OverlaySupport}'s class javadoc) rather than
 * trying to use AR System's own "granular overlay" server-side bookkeeping
 * ({@code AR_OPROP_OVERLAY_EXTEND_MASK}/{@code INHERIT_MASK}, {@code setGranularMode(ONLY_MODE)}):
 * a live spike against a real overlaid form ({@code HPD:Help Desk}) found {@code ONLY_MODE}
 * behaves inconsistently across collection types on that same object (returned the full unchanged
 * permission list, but an empty index list, even though neither category was actually customized
 * and both masks were 0) - not reliable enough to build correctness on top of. A full base-vs-
 * overlay comparison is slower but always correct: it shows exactly what a person would see
 * eyeballing both pages by hand, which is the literal feature being asked for.
 */
public final class OverlayDiff {
    private OverlayDiff() {}

    public enum Status { ADDED, REMOVED, CHANGED }

    /** One item's diff outcome. Only ADDED/REMOVED/CHANGED items are ever produced - equal items are omitted (absence = unchanged). */
    public record Item<T>(Status status, T base, T overlay) {
        public T current() { return overlay != null ? overlay : base; }
    }

    /**
     * Keyed-collection differ: base-only items are REMOVED, overlay-only are ADDED, present in both
     * but not equal (per {@code equalsFn}) are CHANGED, present in both and equal are omitted
     * entirely. Order: overlay's own order first, then any REMOVED (base-only) items appended last.
     */
    public static <T, K> List<Item<T>> diffKeyed(List<T> base, List<T> overlay, Function<T, K> keyFn, BiPredicate<T, T> equalsFn) {
        Map<K, T> baseByKey = new LinkedHashMap<>();
        if (base != null) for (T b : base) baseByKey.put(keyFn.apply(b), b);
        Set<K> matched = new HashSet<>();
        List<Item<T>> result = new ArrayList<>();
        if (overlay != null) {
            for (T o : overlay) {
                K key = keyFn.apply(o);
                T b = baseByKey.get(key);
                if (b == null) {
                    result.add(new Item<>(Status.ADDED, null, o));
                } else {
                    matched.add(key);
                    if (!equalsFn.test(b, o)) result.add(new Item<>(Status.CHANGED, b, o));
                }
            }
        }
        for (Map.Entry<K, T> e : baseByKey.entrySet()) {
            if (!matched.contains(e.getKey())) result.add(new Item<>(Status.REMOVED, e.getValue(), null));
        }
        return result;
    }

    /** One differing property, formatted with the same label/value text {@link ObjectPropertiesTable} itself uses. */
    public record PropChange(int propId, String label, String baseValue, String overlayValue) {}

    /**
     * Excludes AR_SMOPROP_OVERLAY_PROPERTY (the base/overlay marker itself - definitionally always
     * different, not a real customization) and the two overlay-bookkeeping masks
     * {@link ObjectPropertiesTable} itself always renders as "Unknown" for. Also excludes
     * AR_SMOPROP_OVERLAY_GROUP (90016, "Overlay Group") - confirmed live (via a throwaway property
     * dump against HPD:Help Desk) to be the id behind the "Overlay Group" row every overlay object
     * shows: same definitional-marker situation as AR_SMOPROP_OVERLAY_PROPERTY - a base layer never
     * has it set and an overlay/custom layer always does, so it always "differs" without being a
     * real customization. Excluding it means an overlay whose only difference was this bookkeeping
     * field now correctly shows the plain "no differences found" message instead of a one-row table.
     */
    private static final Set<Integer> EXCLUDED_PROP_IDS = Set.of(
        Constants.AR_SMOPROP_OVERLAY_PROPERTY,
        Constants.AR_SMOPROP_OVERLAY_GROUP,
        Constants.AR_OPROP_OVERLAY_EXTEND_MASK,
        Constants.AR_OPROP_OVERLAY_INHERIT_MASK);

    public static List<PropChange> diffProperties(PropertyMap base, PropertyMap overlay) {
        List<PropChange> changes = new ArrayList<>();
        if (base == null && overlay == null) return changes;
        Set<Integer> allIds = new TreeSet<>();
        if (base != null) for (Map.Entry<Integer, Value> e : base.entrySet()) allIds.add(e.getKey());
        if (overlay != null) for (Map.Entry<Integer, Value> e : overlay.entrySet()) allIds.add(e.getKey());
        for (int propId : allIds) {
            if (EXCLUDED_PROP_IDS.contains(propId)) continue;
            Value bv = base == null ? null : base.get(propId);
            Value ov = overlay == null ? null : overlay.get(propId);
            String baseText = bv == null ? null : ObjectPropertiesTable.valueText(propId, bv);
            String overlayText = ov == null ? null : ObjectPropertiesTable.valueText(propId, ov);
            if (Objects.equals(baseText, overlayText)) continue;
            changes.add(new PropChange(propId, ARPropertyLabels.label(propId), baseText, overlayText));
        }
        return changes;
    }

    /** CSS class for a diff-flagged row/block, matching the existing {@code .fieldNotFound}/{@code .fieldInNoView} convention in style.css. */
    public static String cssClass(Status status) {
        return switch (status) {
            case ADDED -> "overlayAdded";
            case CHANGED -> "overlayChanged";
            case REMOVED -> "overlayRemoved";
        };
    }

    /** Short inline text badge, belt-and-suspenders with the CSS class since CSV export/non-visual contexts need a plain-text signal too. */
    public static String badge(Status status) {
        return switch (status) {
            case ADDED -> " (Added by Overlay)";
            case CHANGED -> " (Changed by Overlay)";
            case REMOVED -> " (Removed by Overlay)";
        };
    }

    /**
     * Generic "Changes from Base Layer" summary for object types with no inline structural diff yet
     * (this pass covers ActiveLink/Filter/Escalation/Menu/Container/Image with just this
     * property-level summary - see Main.java's documentOverlayBaseLayers and this project's overlay-
     * diff plan for why Form/Field gets a deeper, inline-annotated treatment instead). Explicitly
     * renders a plain "no differences found" line rather than an empty table when the overlay
     * exists but isn't actually customized - see this class's own javadoc on why that's a real,
     * expected case, not a bug.
     */
    public static String renderSummary(List<PropChange> propertyDiff, int rootLevel) {
        if (propertyDiff.isEmpty()) {
            return "<div class=\"overlaySummary\"><p><b>This object is an overlay, but no differences from its base layer were found.</b></p></div>\n";
        }
        return "<div class=\"overlaySummary\">\n<h2>" + new ImageTag(ImageTag.Id.Document, rootLevel).toHtml() + "Changes from Base Layer</h2>\n<div>\n"
            + renderPropertyTable(propertyDiff) + "</div>\n</div>\n";
    }

    /**
     * Bare property-diff table, no "Changes from Base Layer" wrapper - for callers that already
     * opened their own summary section (e.g. because they also have structural content above the
     * property table, like Menu's item diff or AL/Filter/Escalation's Qualifier/Action diff).
     * Calling {@link #renderSummary} a second time inside an already-open section was a real bug
     * (produced two "Changes from Base Layer" headings on one page) fixed by this split - always use
     * this bare variant, never renderSummary, when appending inside an existing summary block.
     */
    public static String renderPropertyTable(List<PropChange> propertyDiff) {
        if (propertyDiff.isEmpty()) return "";
        Table tbl = new Table("overlayPropDiff", "TblObjectList");
        tbl.description = "Other Property Changes";
        tbl.addColumn(30, "Property");
        tbl.addColumn(35, "Base Value");
        tbl.addColumn(35, "Overlay Value");
        for (PropChange c : propertyDiff) {
            tbl.addRow(new TableRow().addCellList(c.label(), c.baseValue() == null ? "" : c.baseValue(), c.overlayValue() == null ? "" : c.overlayValue()));
        }
        tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /** Reciprocal note for a hidden base-layer page, pointing back to the overlay page instead of duplicating its diff - {@code overlayPageLinkHtml} is a caller-built {@code URLLink} (each object type names its own detail page differently, see each Doc*DetailPage's Naming.*Detail call). */
    public static String renderBaseLayerNote(String overlayPageLinkHtml) {
        return "<div class=\"overlaySummary\"><p>This is the <b>base layer</b> of an overlaid object - see the "
            + overlayPageLinkHtml + " for what the overlay changes.</p></div>\n";
    }

    /**
     * Structural diff bundle for ActiveLink/Filter/Escalation. Qualifier and Action/Else-List are
     * real functional content living entirely outside {@code getProperties()} (confirmed via the
     * Java API - {@code getQualifier()}/{@code getActionList()}/{@code getElseList()} are separate
     * typed accessors, same situation as {@code ListMenu.getItems()}) - a plain property diff never
     * sees them at all. Tracked here as simple "did this change" flags (via each type's own
     * QualifierInfo/List.equals()) rather than a structural item-by-item diff: actions have no
     * stable identity to key by (unlike Fields/Indexes/MenuItems), so a real item-level diff would
     * risk a confidently wrong ADDED/REMOVED pairing where a simple "changed, here's both versions"
     * is honest and always correct - each page renders its own before/after HTML (needs page-
     * specific QualificationRenderer/ActionSummaryTable wiring, with a no-op sink so the diff-only
     * rendering doesn't double-register field references into the live index) and passes it to
     * {@link #renderWorkflowSummary}.
     */
    public record WorkflowDiff(List<PropChange> propertyDiff, boolean qualifierChanged, boolean actionsChanged, List<Item<String>> formListDiff) {
        public boolean hasChanges() { return !propertyDiff.isEmpty() || qualifierChanged || actionsChanged || !formListDiff.isEmpty(); }
    }

    public static String renderWorkflowSummary(WorkflowDiff diff, String baseQualifierHtml, String overlayQualifierHtml,
                                         String baseActionsHtml, String overlayActionsHtml, int rootLevel) {
        if (!diff.hasChanges()) {
            return "<div class=\"overlaySummary\"><p><b>This object is an overlay, but no differences from its base layer were found.</b></p></div>\n";
        }
        StringBuilder sb = new StringBuilder("<div class=\"overlaySummary\">\n<h2>");
        sb.append(new ImageTag(ImageTag.Id.Document, rootLevel).toHtml()).append("Changes from Base Layer</h2>\n<div>\n");
        if (diff.qualifierChanged()) sb.append(renderChangedBlock("Run If Qualification Changed", baseQualifierHtml, overlayQualifierHtml));
        if (diff.actionsChanged()) sb.append(renderChangedBlock("Action List Changed", baseActionsHtml, overlayActionsHtml));
        if (!diff.formListDiff().isEmpty()) sb.append(renderListDiff("Form List Changes", diff.formListDiff()));
        sb.append(renderPropertyTable(diff.propertyDiff()));
        sb.append("</div>\n</div>\n");
        return sb.toString();
    }

    private static String renderChangedBlock(String title, String baseHtml, String overlayHtml) {
        return "<h3>" + title + "</h3>\n<p><b>Base:</b></p>\n" + baseHtml + "\n<p><b>Overlay:</b></p>\n" + overlayHtml + "\n";
    }

    private static String renderListDiff(String title, List<Item<String>> diff) {
        return renderItemListDiff(title, diff, Function.identity());
    }

    /** Generic keyed-item-list diff renderer (a plain ADDED/CHANGED/REMOVED name list, not a structural per-item comparison) - used where a caller-specific type-aware rendering isn't worth building without real data to verify it against, see e.g. ContainerDetailPage's Reference list. */
    public static <T> String renderItemListDiff(String title, List<Item<T>> diff, Function<T, String> labelFn) {
        if (diff.isEmpty()) return "";
        Table tbl = new Table("overlayListDiff", "TblObjectList");
        tbl.description = title;
        tbl.addColumn(100, "Value");
        for (Item<T> item : diff) {
            TableRow row = new TableRow(cssClass(item.status()));
            row.addCell(labelFn.apply(item.current()) + badge(item.status()));
            tbl.addRow(row);
        }
        tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }
}
