package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.OverlaySupport;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import arinside.doc.QualificationRenderer;
import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.PagePath;
import arinside.output.URLLink;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ColumnFieldLimit;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.EnumItem;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.FieldLimit;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.QualifierInfo;
import com.bmc.arsys.api.SelectionFieldLimit;
import com.bmc.arsys.api.TableFieldLimit;
import com.bmc.arsys.api.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the global-field-list part of scan/ScanFields.cpp (the "global field list
 * generation" block in CScanFields::Start) + CDocMain::GlobalFieldList. AR System's convention:
 * field IDs 1,000,000-1,999,999 are "global"/shared field IDs, meant to carry the same meaning
 * across every form that defines one - this indexes every (schema, field) pair whose field ID
 * falls in that range, grouped by field ID, so GlobalFieldsPage can show "this shared ID is used
 * on these forms."
 *
 * Also builds a general fieldId-&gt;name lookup per form (fieldName()) as a second side effect of
 * this same pass, piggybacked here rather than as a separate index - this class already visits
 * every (form, field) pair at exactly the point in Main.java's pipeline (right after forms, right
 * before active links/filters/escalations) where the qualification renderer needs it to turn a
 * bare field ID into a readable, linkable name when rendering "Run If" qualifiers.
 *
 * Costs one more full pass over every form's fields (on top of the passes SchemaOverviewPage and
 * SchemaDetailPage already do) - same known, accepted tradeoff as WorkflowReferenceIndex.
 *
 * Also records each form's overlay-naming status ({@link #isOverlaid(String)}) as a third side
 * effect of this same pass (one extra getForm() per form, same tradeoff as above) - this is what
 * lets {@link arinside.doc.QualificationRenderer#fieldRef} and this class's own byFieldId links
 * point at the correct "__o"-suffixed schema directory for forms that exist only as a hidden
 * overlay base layer (e.g. the stock "User" form on some servers/exports), instead of always
 * guessing isOverlaid=false and producing a 404. See OverlaySupport's javadoc for the "__o" naming
 * convention itself.
 */
public final class GlobalFieldIndex {
    private static final int GLOBAL_FIELD_MIN = 1_000_000;
    private static final int GLOBAL_FIELD_MAX = 2_000_000; // exclusive

    public record Entry(String schemaName, PagePath schemaLink, String fieldName) {}
    /** Java port of DocSchemaDetails.cpp's TableFieldReferences() data source - "which Table field, on which form, pulls its rows FROM this form". */
    public record TableFieldRef(String ownerForm, int fieldId) {}

    /** Thread-safe: Collections.synchronizedSortedMap wrapping a TreeMap (needs to stay sorted for GlobalFieldsPage's iteration order) + a synchronized list per bucket (multiple forms can share the same global field ID). */
    private final SortedMap<Integer, List<Entry>> byFieldId = Collections.synchronizedSortedMap(new TreeMap<>());
    private final Map<String, Map<Integer, String>> fieldNamesByForm = new ConcurrentHashMap<>();
    /** Cross-schema field data type, for a Table field's "Data Field"-sourced Column whose source lives on the table's own target schema (a different schema than the column itself) - see FieldDetailPage.columnsTableOf. Piggybacked on the same full-field scan every other per-form map here already does, no extra fetch. */
    private final Map<String, Map<Integer, Integer>> fieldDataTypesByForm = new ConcurrentHashMap<>();
    /** VUI name -&gt; VUI id per form, for resolving an OpenWindowAction's plain (non-"$field$") View Name to a real hyperlink - see DocOpenWindowAction.cpp's View Name block and ActionSummaryTable.viewNameOf. Derived from {@link #viewsByForm} below, not a separate fetch. */
    private final Map<String, Map<String, Integer>> vuiIdsByForm = new ConcurrentHashMap<>();
    /**
     * The full View list per form, from the SAME repo.getViews(formName) call vuiIdsByForm derives
     * from - not itself a piggyback on the fields fetch (a genuinely separate RPC, same accepted
     * cost as WorkflowReferenceIndex/PermissionIndex's own per-form fetches), but caching the full
     * objects here (not just a name-&gt;id summary) lets PermissionIndex and SchemaDetailPage read
     * from this ONE pass instead of each doing their own redundant full-server getViews() scan -
     * confirmed as real, not theoretical: a live run's timing summary showed ~14052 VUI-fetch calls
     * across ~4667 forms (3 independent full passes), not the ~4667 a single pass would cost.
     */
    private final Map<String, List<View>> viewsByForm = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Map<Integer, String>>> enumLabelsByForm = new ConcurrentHashMap<>();
    private final Map<String, Boolean> overlaidByForm = new ConcurrentHashMap<>();
    private final Map<String, List<TableFieldRef>> tableFieldSources = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static GlobalFieldIndex build(SchemaSource repo, int serverOverlayMode, boolean overlaySupportEnabled,
                                          FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs) throws ARException {
        return build(repo, null, null, serverOverlayMode, overlaySupportEnabled, fieldRefs, missingFieldRefs);
    }

    /** Server mode: each form's fetch+index-accumulate runs as one task on the read pool - see WorkflowReferenceIndex.build's javadoc for why no separate write-pool hop is needed here. */
    public static GlobalFieldIndex build(SchemaSource repo, ReadPool reads, Function<ArClient, SchemaSource> repoFactory,
                                          int serverOverlayMode, boolean overlaySupportEnabled,
                                          FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs) throws ARException {
        GlobalFieldIndex idx = new GlobalFieldIndex();

        if (reads == null) {
            for (String formName : repo.listFormNames()) {
                try {
                    idx.indexForm(repo, formName, serverOverlayMode, overlaySupportEnabled, fieldRefs, missingFieldRefs);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing global fields for '" + formName + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String formName : repo.listFormNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexForm(repoFactory.apply(c), formName, serverOverlayMode, overlaySupportEnabled, fieldRefs, missingFieldRefs); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing global fields for '" + formName + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexForm(SchemaSource repo, String formName, int serverOverlayMode, boolean overlaySupportEnabled,
                            FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs) throws ARException {
        Form form = repo.getForm(formName);
        boolean overlaid = overlaySupportEnabled && OverlaySupport.isOverlaidForNaming(form.getProperties(), serverOverlayMode);
        overlaidByForm.put(formName, overlaid);

        List<Field> fields = repo.getFields(formName);
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Map<Integer, String>> enumLabels = new HashMap<>();
        Map<Integer, Integer> dataTypes = new HashMap<>();
        Map<Integer, Field> byId = new HashMap<>();
        for (Field field : fields) {
            int id = field.getFieldID();
            names.put(id, field.getName());
            dataTypes.put(id, field.getDataType());
            byId.put(id, field);
            if (id >= GLOBAL_FIELD_MIN && id < GLOBAL_FIELD_MAX) {
                byFieldId.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new Entry(formName, Naming.schemaDetail(formName, overlaid), field.getName()));
            }
            FieldLimit limit = field.getFieldLimit();
            if (limit instanceof SelectionFieldLimit sel && sel.getValues() != null) {
                Map<Integer, String> labels = new HashMap<>();
                for (EnumItem item : sel.getValues()) {
                    labels.put(item.getEnumItemNumber(), item.getEnumItemName());
                }
                enumLabels.put(id, labels);
            }
            if (limit instanceof TableFieldLimit tbl) {
                String dataSourceForm = tbl.getForm();
                if (dataSourceForm != null && dataSourceForm.startsWith("$")) dataSourceForm = tbl.getSampleForm();
                if (dataSourceForm != null && !dataSourceForm.isEmpty() && !dataSourceForm.equals("@")) {
                    tableFieldSources.computeIfAbsent(dataSourceForm, k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(new TableFieldRef(formName, id));
                }
            }
        }
        fieldNamesByForm.put(formName, names);
        enumLabelsByForm.put(formName, enumLabels);
        fieldDataTypesByForm.put(formName, dataTypes);

        List<View> views = repo.getViews(formName);
        Map<String, Integer> vuis = new HashMap<>();
        for (View v : views) {
            vuis.put(v.getName(), v.getVUIId());
        }
        vuiIdsByForm.put(formName, vuis);
        viewsByForm.put(formName, views);

        // Java port of scan/ScanFields.cpp's AR_DATA_TYPE_COLUMN/AR_DATA_TYPE_TABLE cases - piggybacked
        // on this same full-field scan (see class javadoc for the established "amortize" pattern), run
        // as a second pass over the same field list since Column fields need their *parent* Table
        // field's own FieldLimit already looked up by id (byId), which the first pass builds.
        for (Field field : fields) {
            FieldLimit limit = field.getFieldLimit();
            if (limit instanceof ColumnFieldLimit col) {
                indexColumnReference(formName, overlaid, field, col, byId, fieldRefs);
            } else if (limit instanceof TableFieldLimit tbl) {
                indexTableQualificationReferences(formName, tbl, field, fieldRefs, missingFieldRefs);
            }
        }
    }

    /**
     * Java port of scan/ScanFields.cpp's AR_DATA_TYPE_COLUMN case (REFM_TABLEFIELD_COLUMN) - adds a
     * reverse "Column in Table X of Form Y" reference onto the field this Column field actually
     * reads its data from, so that field's own "Referenced By" page shows every Column mirroring
     * it. Source-form resolution matches CDocFieldDetails::GetColumnSourceField exactly (Display/
     * Control Field -&gt; this column's own schema; Data Field -&gt; the parent Table field's own
     * target schema). rootLevel is hardcoded to 2, matching the C++'s own hardcoding in
     * CScanFields::Start - every field detail page lives at the same folder depth (see
     * Naming.schemaFieldDetail), so the relative-link prefix baked into detail here is always valid
     * regardless of which field's page it's later rendered on.
     */
    private void indexColumnReference(String formName, boolean overlaid, Field colField, ColumnFieldLimit col,
                                       Map<Integer, Field> byId, FieldReferenceIndex fieldRefs) {
        Field parentTableField = byId.get(col.getParent());
        if (parentTableField == null) return;
        String sourceForm = columnSourceForm(col, formName, parentTableField);
        if (sourceForm == null || sourceForm.isEmpty()) return;

        int rootLevel = 2;
        String detail = "Column in Table "
            + URLLink.to(parentTableField.getName(), Naming.schemaFieldDetail(formName, overlaid, parentTableField.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml()
            + " of Form "
            + URLLink.to(formName, Naming.schemaDetail(formName, overlaid), ImageTag.Id.Schema, rootLevel).toHtml();
        FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(colField.getName(), "Field", ImageTag.Id.Document,
            Naming.schemaFieldDetail(formName, overlaid, colField.getFieldID()), detail);
        fieldRefs.add(sourceForm, col.getDataField(), ref);
    }

    /** Same DISPLAY_FIELD/CONTROL_FIELD/DATA_FIELD branching as FieldDetailPage.columnSourceForm - duplicated here since that method is private and this index has no access to FieldDetailPage's helpers (same tradeoff already accepted elsewhere in this port). */
    private static String columnSourceForm(ColumnFieldLimit col, String ownForm, Field parentTableField) {
        int dataSource = col.getDataSource();
        if (dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_DISPLAY_FIELD || dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_CONTROL_FIELD) {
            return ownForm;
        }
        if (dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_DATA_FIELD && parentTableField.getFieldLimit() instanceof TableFieldLimit tfl) {
            return resolveTableFormRef(tfl.getForm(), tfl.getSampleForm(), ownForm);
        }
        return null;
    }

    /**
     * Java port of scan/ScanFields.cpp's AR_DATA_TYPE_TABLE case (REFM_TABLEFIELD_QUALIFICATION) -
     * walks a Table field's own qualification purely for the FieldReferenceSink side effect (the
     * rendered qualification text itself is discarded - this pass never displays it), tagging every
     * field the qualification touches with the fixed "Table Qualification" label (RefItem.cpp's own
     * label has no per-clause breakdown, unlike Run If/Set Fields quals). Unlike the C++, this
     * doesn't gate on the target schema's existence first (CARSchema::Exists() before walking) -
     * this index's own forms are indexed in parallel with no fixed order, so "not yet indexed" and
     * "doesn't exist" aren't distinguishable mid-build; skipping that gate matches how
     * FieldDetailPage's own (display-only) rendering of this same qualifier already renders
     * unconditionally with no existence check.
     */
    private void indexTableQualificationReferences(String formName, TableFieldLimit tbl, Field tableField,
                                                     FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs) {
        QualifierInfo qual = tbl.getQualifier();
        if (qual == null || qual.getOperation() == QualifierInfo.AR_COND_OP_NONE) return;
        String targetSchema = resolveTableFormRef(tbl.getForm(), tbl.getSampleForm(), formName);
        if (targetSchema == null || targetSchema.isEmpty()) return;

        PagePath tableFieldLink = Naming.schemaFieldDetail(formName, isOverlaid(formName), tableField.getFieldID());
        QualificationRenderer.FieldReferenceSink sink = (targetForm, targetFieldId, fieldExists, detail) -> {
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(tableField.getName(), "Field", ImageTag.Id.Document, tableFieldLink, "Table Qualification");
            fieldRefs.add(targetForm, targetFieldId, ref);
            if (!fieldExists) missingFieldRefs.add(targetForm, targetFieldId, ref);
        };
        new QualificationRenderer(formName, targetSchema, 2, this, sink).render(qual);
    }

    /** Java port of DocFieldDetails.cpp's Table-limit "@"/"$fieldId"/plain-form-literal branch, duplicated from FieldDetailPage.resolveTableFormRef for the same "private helper, no cross-package access" reason as columnSourceForm above. */
    private static String resolveTableFormRef(String raw, String sampleForm, String ownForm) {
        if (raw == null || raw.isEmpty()) return ownForm;
        if (raw.charAt(0) != '$') return raw;
        try {
            int fieldId = Integer.parseInt(raw.substring(1));
            if (fieldId == -Constants.AR_KEYWORD_SCHEMA) return ownForm;
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return ownForm;
        }
        return sampleForm == null || sampleForm.isEmpty() || sampleForm.equals("@") ? ownForm : sampleForm;
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    public SortedMap<Integer, List<Entry>> byFieldId() {
        return byFieldId;
    }

    /** Every Table field (on any form) whose data source is this form - matches CDocSchemaDetails::TableFieldReferences(). */
    public List<TableFieldRef> tableFieldSources(String formName) {
        return tableFieldSources.getOrDefault(formName, List.of());
    }

    /** Whether formName is a real, known form - Java port of CARSchema::Exists() for callers (e.g. TextFieldSubstitution's "Application-*" / "PERFORM-ACTION-*" form-name echo) that need to distinguish a real schema link from a plain literal echo. */
    public boolean formExists(String formName) {
        return formName != null && fieldNamesByForm.containsKey(formName);
    }

    /** The full View list for a form, from this index's own pass - see viewsByForm's javadoc for why callers should read from here instead of re-fetching. Returns an empty list for an unknown form. */
    public List<View> views(String formName) {
        return viewsByForm.getOrDefault(formName, List.of());
    }

    /** Returns null if formName is unknown or the field ID doesn't exist on it (e.g. a stale/removed reference). */
    public String fieldName(String formName, int fieldId) {
        Map<Integer, String> names = fieldNamesByForm.get(formName);
        return names == null ? null : names.get(fieldId);
    }

    /** A field's AR_DATA_TYPE_* on a given form, or null if the form/field isn't known - see fieldDataTypesByForm's javadoc. */
    public Integer fieldDataType(String formName, int fieldId) {
        Map<Integer, Integer> types = fieldDataTypesByForm.get(formName);
        return types == null ? null : types.get(fieldId);
    }

    /**
     * The label text for an enum/selection field's numeric value (e.g. fieldId=7, value=1 -&gt;
     * "Enabled") - see core/ARQualification.cpp's GetFieldEnumValue, used by
     * {@link arinside.doc.QualificationRenderer}'s enum-value resolution. Returns null if formName
     * is unknown, the field isn't a selection/enum field, or no item matches this value (falls back
     * to the raw numeric rendering in that case, matching the C++'s behavior).
     */
    public String enumLabel(String formName, int fieldId, int value) {
        Map<Integer, Map<Integer, String>> byField = enumLabelsByForm.get(formName);
        if (byField == null) return null;
        Map<Integer, String> labels = byField.get(fieldId);
        return labels == null ? null : labels.get(value);
    }

    /** A VUI's id by (form, name), or null if the form/VUI isn't known - see vuiIdsByForm's javadoc. */
    public Integer vuiId(String formName, String vuiName) {
        Map<String, Integer> vuis = vuiIdsByForm.get(formName);
        return vuis == null ? null : vuis.get(vuiName);
    }

    /** Whether formName's detail page lives at the "__o"-suffixed path - see class javadoc. Defaults to false for an unknown form name (e.g. a stale/removed reference), matching this port's prior always-false behavior for that case. */
    public boolean isOverlaid(String formName) {
        return overlaidByForm.getOrDefault(formName, false);
    }

    /** Sum of every form's field count - free to compute, this index already visited every field of every form as a side effect of indexing field names for Run If hyperlinks. Used by the index/summary page's "N fields loaded" stat, matching the C++'s DocSummaryInfo (which re-fetches every schema's field list just for that count - this port avoids the redundant fetch). */
    public int totalFieldCount() {
        return fieldNamesByForm.values().stream().mapToInt(Map::size).sum();
    }
}
