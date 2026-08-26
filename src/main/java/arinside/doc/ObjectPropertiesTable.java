package arinside.doc;

import arinside.ar.ARPropertyLabels;
import arinside.output.Table;
import arinside.output.TableRow;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.DataType;
import com.bmc.arsys.api.PropertyMap;
import com.bmc.arsys.api.Value;

import java.util.Map;
import java.util.Set;

/**
 * Java port of core/ARProplistHelper.cpp's CARProplistHelper::GetList - a raw "Object Properties"
 * table dumping every property on an object (label via {@link ARPropertyLabels}, value via
 * {@link ValueFormat} with a special case for AR_DPROP_ENUM_LABELS' packed backslash-delimited
 * format). Real C++ call sites confirmed by reading every doc/*.cpp file (not assumed): only 4
 * object types actually show this - Menu (DocCharMenuDetails.cpp), Active Link (DocAlDetails.cpp),
 * Filter (DocFilterDetails.cpp), VUI (DocVuiDetails.cpp) - schema/escalation/container don't call
 * it (they extract known properties into typed sections instead, a different C++ mechanism -
 * GetAndUseValue+UnusedPropertiesToHTML - not this one, and not ported here).
 *
 * <p>Deliberately NOT ported: CAREnum::FieldPropertiesValue, a 111+-case nested switch giving
 * per-property enum/bitmask value labels (e.g. property X's value 0 means "Do Not Fire Workflow").
 * This always falls through to the raw numeric/string value instead - the exact same fallback path
 * the C++ itself takes whenever a specific property/value pair isn't in that table, just taken
 * unconditionally rather than attempting the lookup first. A real, bounded scope cut: less
 * information than the C++ for properties whose value happens to be an enum/bitmask, but never
 * wrong (the underlying value is always shown, just not decoded to its label).
 *
 * <p>Active Link's C++ call site (DocAlDetails.cpp) doesn't actually call GetList at all (it's
 * commented out there) - it uses the OTHER, instance-based CARProplistHelper::GetAndUseValue/
 * UnusedPropertiesToHTML mechanism instead: AR_OPROP_INTERVAL_VALUE is "claimed" by
 * CARActiveLink::GetExecuteOn (shown inline as "Interval: N" there - see
 * ActiveLinkDetailPage.generalInfo) before the remaining, unclaimed properties are dumped here -
 * confirmed by an exhaustive grep of every doc/*.cpp GetAndUseValue call site (only DocSchemaDetails.cpp
 * and, via CARActiveLink::GetExecuteOn, DocAlDetails.cpp use this exclusion mechanism at all; Menu/
 * Filter/VUI genuinely call plain GetList with no exclusions, confirmed the same way). The
 * {@code excludeIds} param below is this table's port of that "already claimed elsewhere" exclusion -
 * used only by ActiveLinkDetailPage. DocSchemaDetails.cpp's own much larger GetAndUseValue block
 * (Next ID Block Size/Cache/Core Fields/Form Allow Delete/Allow Drill Down/localization/Form Tag
 * Name/entry-point-order/full-text-search settings) is ported too, but as SchemaDetailPage's own
 * typed sections (basicPropertiesInfo/entryPointsInfo/fullTextSearchInfo) rather than through this
 * generic table - Schema pages never call GetList/render() at all in the real C++, so there's
 * nothing here to exclude from for that object type.
 */
final class ObjectPropertiesTable {
    private ObjectPropertiesTable() {}

    static String render(PropertyMap props) {
        return render(props, Set.of());
    }

    /** excludeIds: property ids already shown elsewhere on the page (see class javadoc) - skipped here so they aren't shown twice. */
    static String render(PropertyMap props, Set<Integer> excludeIds) {
        return render(props, excludeIds, "Object Properties:");
    }

    /** Same as {@link #render(PropertyMap, Set)}, but with a caller-supplied heading instead of the generic "Object Properties:" - used by FieldDetailPage's per-VUI DisplayProperties section, whose real C++ heading (DocFieldDetails.cpp's DisplayProperties()) embeds the schema/VUI links and isn't a fixed string. */
    static String render(PropertyMap props, Set<Integer> excludeIds, String heading) {
        Table tbl = new Table("displayPropList", "TblObjectList");
        tbl.description = heading;
        tbl.addColumn(20, "Description");
        tbl.addColumn(80, "Values");
        if (props == null) return tbl.toXHtml();

        int count = 0;
        for (Map.Entry<Integer, Value> e : props.entrySet()) {
            int propId = e.getKey();
            if (excludeIds.contains(propId)) continue;
            tbl.addRow(new TableRow().addCellList(ARPropertyLabels.label(propId), valueText(propId, e.getValue())));
            count++;
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    private static String valueText(int propId, Value value) {
        if (value == null) return "";
        if (propId == Constants.AR_DPROP_ENUM_LABELS && value.getDataType() == DataType.CHAR
            && value.getValue() instanceof String packed) {
            return enumLabelsText(packed);
        }
        return rawValueText(propId, value);
    }

    /**
     * Java port of core/ARProplistHelper.cpp's CARProplistHelper::GetValue - deliberately NOT the
     * same formatter as {@link ValueFormat} (that one renders AR_VALUE qualification literals,
     * which quote CHAR/TIME values the way qualification syntax requires - e.g. `'Some Text'`).
     * Raw object-property values are never quoted in the original tool (GetValue's
     * AR_DATA_TYPE_CHAR case is plain {@code CWebUtil::Validate(arV.u.charVal)}, no quotes).
     *
     * <p>AR_DATA_TYPE_ULONG has one hardcoded special case matching the original GetValue -
     * AR_OPROP_SCC_TIMESTAMP is epoch-seconds converted via CUtil::DateTimeToHTMLString.
     *
     * <p>CAREnum::FieldPropertiesValue (111+ per-property enum/bitmask value tables) is still
     * deliberately not fully ported (see class javadoc) - EXCEPT AR_SMOPROP_OVERLAY_PROPERTY
     * (Original/Overlay/Custom), which is common enough on overlay-capable objects to be worth the
     * small, targeted port. Everything else still falls through to the raw value - INTEGER/ENUM/
     * ULONG show the plain number (matching the C++'s own fallback for an unmatched property id) -
     * EXCEPT AR_OPROP_OVERLAY_EXTEND_MASK/AR_OPROP_OVERLAY_INHERIT_MASK, hardcoded to always render
     * "Unknown" (EnumDefault in the C++, `if (strValue.empty()) strValue = EnumDefault;`): these two
     * are AR_DATA_TYPE_BITMASK server-side with no per-value case in the original decode table
     * either (BMC's own FieldPropertiesValue switch has no case for either property id), so the
     * original tool always shows "Unknown" for them, never the raw mask number - hardcoded by
     * property id rather than gated on {@code DataType.BITMASK} because the Java AR API surfaces
     * these two specifically as a different DataType client-side.
     *
     * <p>Deliberate departure from strict C++ parity: every "Unknown" fallback (both the hardcoded
     * pair above and the generic BITMASK/decode-miss case below) appends the raw underlying value
     * in parens - e.g. "Unknown (12)" - so a reader can look the number up manually even though this
     * table can't decode it to a label. The original tool discards the number entirely in this
     * case; this is intentionally more useful than a byte-exact match would be.
     */
    private static final Set<Integer> ALWAYS_UNKNOWN = Set.of(
        Constants.AR_OPROP_OVERLAY_EXTEND_MASK, Constants.AR_OPROP_OVERLAY_INHERIT_MASK);

    private static String rawValueText(int propId, Value value) {
        DataType type = value.getDataType();
        Object v = value.getValue();
        if (ALWAYS_UNKNOWN.contains(propId)) return unknownWithValue(v);
        if (type == DataType.NULL || v == null) return "NULL";
        if (type == DataType.CHAR || type == DataType.DIARY) return arinside.output.WebUtil.validate(v.toString());
        if (type == DataType.BITMASK) {
            String decoded = decodedValue(propId, v);
            return decoded != null ? decoded : unknownWithValue(v);
        }
        if (type == DataType.ULONG && propId == Constants.AR_OPROP_SCC_TIMESTAMP) {
            return arinside.util.DateTimeFormat.toHtmlString(((Number) v).longValue());
        }
        if (type == DataType.INTEGER || type == DataType.ENUM || type == DataType.ULONG) {
            String decoded = decodedValue(propId, v);
            return decoded != null ? decoded : v.toString();
        }
        return v.toString();
    }

    private static String unknownWithValue(Object v) {
        return v == null ? "Unknown" : "Unknown (" + v + ")";
    }

    /** Java port of the AR_SMOPROP_OVERLAY_PROPERTY case in core/AREnum.cpp's CAREnum::FieldPropertiesValue (CAREnum::GetOverlayType) - the only per-property value decode this table ports (see rawValueText's javadoc). */
    private static String decodedValue(int propId, Object v) {
        if (propId == Constants.AR_SMOPROP_OVERLAY_PROPERTY && v instanceof Number n) {
            return switch (n.intValue()) {
                case Constants.AR_OVERLAID_OBJECT -> "Original";
                case Constants.AR_OVERLAY_OBJECT -> "Overlay";
                case Constants.AR_CUSTOM_OBJECT -> "Custom";
                default -> null;
            };
        }
        return null;
    }

    /**
     * Java port of the AR_DPROP_ENUM_LABELS special case in ARProplistHelper.cpp's GetValue -
     * "<count>\<num1>\<label1>\<num2>\<label2>\..." packed into one string, rendered as one
     * "<num> - <label>" line per entry.
     */
    private static String enumLabelsText(String packed) {
        StringBuilder out = new StringBuilder();
        int pos = packed.indexOf('\\');
        if (pos < 0) return "";
        int numItems;
        try {
            numItems = Integer.parseInt(packed.substring(0, pos));
        } catch (NumberFormatException e) {
            return "";
        }
        String rest = packed.substring(pos + 1);
        for (int i = 0; i < numItems; i++) {
            int sep1 = rest.indexOf('\\');
            if (sep1 < 0) break;
            String num = rest.substring(0, sep1);
            rest = rest.substring(sep1 + 1);
            int sep2 = rest.indexOf('\\');
            if (sep2 < 0) break;
            String label = rest.substring(0, sep2);
            rest = rest.substring(sep2 + 1);
            out.append(num).append(" - ").append(label).append("<br/>");
        }
        return out.toString();
    }
}
