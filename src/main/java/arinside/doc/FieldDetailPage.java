package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.ARKeywordLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.PropertyHelper;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.JoinFieldIndex;
import com.bmc.arsys.api.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of doc/DocFieldDetails.cpp - General info + type-specific Limits + Default Value +
 * Permissions + join/view Field Mapping + per-VUI Display Properties (full raw property dump, one
 * table per VUI the field appears on - not to be confused with VuiDetailPage's own curated
 * Position/Colors/Font summary columns, a real but separate/incomplete substitute previously
 * mistaken for this section) + Attached Workflow (Control Field/Focus Field active-link
 * attachments, Order/Enabled/Execute On) + Referenced By (every other kind of field reference,
 * sourced from the field-level FieldReferenceIndex).
 *
 * Since 4.5 this is no longer one HTML file per field (that was ~90% of the whole output tree on a
 * large server). {@link #renderFragment} returns just the section HTML; {@code SchemaDetailPage}
 * collects one fragment per field into a single {@code schema/<form>/fields.js} sidecar that
 * {@code schema.js} renders into an anchored panel on the form's own page.
 */
public final class FieldDetailPage {
    private final AppConfig appConfig;
    private final FieldReferenceIndex fieldRefs;
    private final GlobalFieldIndex globalFields;
    private final JoinFieldIndex joinFields;

    public FieldDetailPage(AppConfig appConfig, FieldReferenceIndex fieldRefs, GlobalFieldIndex globalFields, JoinFieldIndex joinFields) {
        this.appConfig = appConfig;
        this.fieldRefs = fieldRefs;
        this.globalFields = globalFields;
        this.joinFields = joinFields;
    }

    /**
     * The field's detail sections as one HTML fragment (no page shell), for embedding in the form
     * page's {@code fields.js} sidecar. {@code rootLevel} is the form page's own level (2), so every
     * link inside resolves the same as it did on the old standalone field page.
     */
    public String renderFragment(String schemaName, boolean schemaOverlaid, Form form, Field field, List<Field> allFields, List<View> vuis, int rootLevel) {
        StringBuilder sb = new StringBuilder();

        Table tbl = new Table("fieldGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Field ID", Integer.toString(field.getFieldID())));
        tbl.addRow(new TableRow().addCellList("Data Type", AREnumLabels.dataType(field.getDataType())));
        tbl.addRow(new TableRow().addCellList("Field Type", AREnumLabels.fieldType(field.getFieldType())));
        tbl.addRow(new TableRow().addCellList("Option", AREnumLabels.fieldOption(field.getFieldOption())));
        tbl.addRow(new TableRow().addCellList("Core Field", field.isCoreField() ? "Yes" : "No"));
        tbl.addRow(new TableRow().addCellList("Default Value", field.getDefaultValue() == null
            ? WebUtil.EMPTY_VALUE : defaultValueOf(field.getDefaultValue(), schemaName, rootLevel)));
        sb.append(tbl.toXHtml());

        sb.append(limits(field, schemaName, rootLevel, form, allFields, vuis));
        sb.append(fieldMapping(form, field, rootLevel));
        sb.append(joinFormReferences(schemaName, field.getFieldID(), rootLevel));
        sb.append(permissions(field, rootLevel));
        sb.append(displayPropertiesPerVui(schemaName, schemaOverlaid, field, vuis, rootLevel));
        sb.append(attachedWorkflow(schemaName, field.getFieldID(), rootLevel));
        sb.append(referencedBy(schemaName, field.getFieldID(), rootLevel));
        return sb.toString();
    }

    /**
     * Java port of DocFieldDetails.cpp's DefaultValue() - identical to ValueFormat.format() for
     * every data type except CHAR, where the real C++ runs the raw string through
     * pInside->TextFindFields() (field-substitution: bare "$fieldId$" tokens become hyperlinked
     * field names) before quoting it. This is deliberately NOT folded into the shared
     * ValueFormat.format() method, since ARQualification.cpp's AR_DATA_TYPE_CHAR qualification-
     * literal case does NOT do this substitution - it's unique to default-value display.
     */
    private String defaultValueOf(Value value, String schemaName, int rootLevel) {
        if (value != null && value.getDataType() == DataType.CHAR && value.getValue() != null) {
            QualificationRenderer noOpQr = new QualificationRenderer(schemaName, rootLevel, globalFields, (f, id, exists, detail) -> {});
            return "\"" + TextFieldSubstitution.substitute(value.getValue().toString(), schemaName, noOpQr, "Default Value") + "\"";
        }
        return ValueFormat.format(value);
    }

    /**
     * Java port of DocFieldDetails::FieldMapping - only meaningful on Join/View-type forms (see
     * JoinForm/ViewForm subtypes); every other field type's FieldMapping (Regular/Vendor) has
     * nothing worth showing here. Field ID 1 on a join form is the special "Request ID" field that
     * maps to *both* member forms' own field 1 at once, matching the C++'s own special case.
     */
    private String fieldMapping(Form form, Field field, int rootLevel) {
        FieldMapping map = field.getFieldMap();
        if (form instanceof JoinForm join && map instanceof JoinFieldMapping) {
            QualificationRenderer ref = new QualificationRenderer(join.getName(), rootLevel, globalFields, (f, id, exists, detail) -> {});
            Table tbl = new Table("fieldMapping", "TblObjectList");
            tbl.description = "Field Mapping";
            tbl.addColumn(30, "Property");
            tbl.addColumn(70, "Value");
            if (field.getFieldID() == 1) {
                tbl.addRow(new TableRow().addCellList(WebUtil.validate(join.getMemberA()), ref.fieldRef(join.getMemberA(), 1)));
                tbl.addRow(new TableRow().addCellList(WebUtil.validate(join.getMemberB()), ref.fieldRef(join.getMemberB(), 1)));
            } else {
                JoinFieldMapping jm = (JoinFieldMapping) map;
                String targetForm = jm.getIndex() == 0 ? join.getMemberA() : join.getMemberB();
                tbl.addRow(new TableRow().addCellList("Form Name", tableSchemaLink(targetForm, rootLevel)));
                tbl.addRow(new TableRow().addCellList("Field Name", ref.fieldRef(targetForm, jm.getFieldID())));
            }
            return tbl.toXHtml();
        }
        if (form instanceof ViewForm view && map instanceof ViewFieldMapping vm) {
            Table tbl = new Table("fieldMapping", "TblObjectList");
            tbl.description = "Field Mapping";
            tbl.addColumn(30, "Property");
            tbl.addColumn(70, "Value");
            tbl.addRow(new TableRow().addCellList("Column", WebUtil.validate(vm.getFieldName())));
            tbl.addRow(new TableRow().addCellList("Table", WebUtil.validate(view.getTableName())));
            return tbl.toXHtml();
        }
        return "";
    }

    /** Java port of DocFieldDetails::JoinFormReferences - the reverse direction of fieldMapping() above, sourced from JoinFieldIndex's one-time reverse scan instead of the C++'s per-field full rescan (see that index's javadoc for why). */
    private String joinFormReferences(String schemaName, int fieldId, int rootLevel) {
        List<JoinFieldIndex.Ref> refs = joinFields.forField(schemaName, fieldId);
        if (refs.isEmpty()) return "";
        Table tbl = new Table("fieldJoinRefs", "TblObjectList");
        tbl.description = "Used in Join Forms";
        tbl.addColumn(50, "Join Form");
        tbl.addColumn(50, "As");
        for (JoinFieldIndex.Ref ref : refs) {
            boolean isOverlaid = globalFields != null && globalFields.isOverlaid(ref.joinFormName());
            tbl.addRow(new TableRow().addCellList(
                URLLink.to(ref.joinFormName(), Naming.schemaDetail(ref.joinFormName(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml(),
                ref.isMemberA() ? "Primary Form" : "Secondary Form"));
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of DocFieldDetails::WorkflowReferences, sourced from FieldReferenceIndex (see its
     * javadoc on why it's only complete on the pipeline's final field-page re-render pass). Control
     * Field/Focus Field references are excluded here - they're shown in attachedWorkflow() instead,
     * matching the C++'s "if (iter->GetMessageId() == REFM_FOCUSFIELD || iter->GetMessageId() ==
     * REFM_CONTROLFIELD) continue;" in WorkflowReferences() (DocFieldDetails.cpp:191).
     */
    private String referencedBy(String schemaName, int fieldId, int rootLevel) {
        List<FieldReferenceIndex.Ref> refs = fieldRefs.forField(schemaName, fieldId);
        Table tbl = new Table("fieldReferences", "TblObjectList");
        tbl.description = "Referenced By";
        tbl.addColumn(10, "Type");
        tbl.addColumn(45, "Name");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(40, "Detail");
        int count = 0;
        for (FieldReferenceIndex.Ref ref : refs) {
            if (isAttachedWorkflowDetail(ref.detail())) continue;
            String link = URLLink.to(ref.name(), ref.link(), ref.icon(), rootLevel).toHtml() + WorkflowRefLabel.orderSuffix(ref.typeLabel(), ref.order());
            String enabled = ref.enabled() == null ? "" : AREnumLabels.objectEnable(ref.enabled());
            tbl.addRow(new TableRow().addCellList(ref.typeLabel(), link, enabled, ref.detail()));
            count++;
        }
        return count > 0 ? tbl.toXHtml() : "";
    }

    private static boolean isAttachedWorkflowDetail(String detail) {
        return "Control Field".equals(detail) || "Focus Field".equals(detail);
    }

    /**
     * Java port of DocFieldDetails::WorkflowAttached (DocFieldDetails.cpp:1106) - the Control
     * Field/Focus Field subset of this field's references, sorted by the referencing active link's
     * Order (SortRefItemByOrder), rendered with Order/Enabled/Execute On columns sourced from
     * FieldReferenceIndex.Ref's order/enabled/executeOn (see ActiveLinkDetailPage's sink). Matches
     * the C++'s own "only rendered when non-empty" behavior. The Server-object cell's link also
     * carries the redundant " (Order)" suffix WorkflowReferenceTable::LinkToAlRef appends to every
     * AL reference site-wide (see WorkflowRefLabel) - redundant with the Order column here, but
     * matching the real C++ output byte-for-byte, confirmed via a live comparison on this exact
     * table.
     */
    private String attachedWorkflow(String schemaName, int fieldId, int rootLevel) {
        List<FieldReferenceIndex.Ref> refs = new java.util.ArrayList<>();
        for (FieldReferenceIndex.Ref ref : fieldRefs.forField(schemaName, fieldId)) {
            if (isAttachedWorkflowDetail(ref.detail())) refs.add(ref);
        }
        if (refs.isEmpty()) return "";
        refs.sort(java.util.Comparator.comparingInt(r -> r.order() == null ? 0 : r.order()));

        Table tbl = new Table("attachedWorkflow", "TblObjectList");
        tbl.description = "Attached Workflow";
        tbl.addColumn(10, "Order");
        tbl.addColumn(45, "Server object");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(40, "Execute On");
        for (FieldReferenceIndex.Ref ref : refs) {
            tbl.addRow(new TableRow().addCellList(
                ref.order() == null ? "" : Integer.toString(ref.order()),
                URLLink.to(ref.name(), ref.link(), ref.icon(), rootLevel).toHtml() + WorkflowRefLabel.orderSuffix(ref.typeLabel(), ref.order()),
                ref.enabled() == null ? "" : AREnumLabels.objectEnable(ref.enabled()),
                ref.executeOn() == null ? "" : ref.executeOn()));
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of DocFieldDetails::DisplayProperties (DocFieldDetails.cpp:786) - one full raw
     * "Object Properties"-style table per VUI this field appears on, reusing the same
     * ObjectPropertiesTable machinery the Menu/Active Link/Filter/VUI Object Properties sections
     * use, with a heading embedding the schema link and the VUI's own link/Id/Label - matching the
     * C++'s "Display Properties in [schema], View: [vui] (Id: N, Label: X)" format.
     */
    private String displayPropertiesPerVui(String schemaName, boolean schemaOverlaid, Field field, List<View> vuis, int rootLevel) {
        DisplayInstanceMap instances = field.getDisplayInstance();
        if (instances == null || instances.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, DisplayPropertyMap> e : instances.entrySet()) {
            int vuiId = e.getKey();
            View vui = findVui(vuis, vuiId);
            StringBuilder heading = new StringBuilder("Display Properties in ")
                .append(URLLink.to("Schema", Naming.schemaDetail(schemaName, schemaOverlaid), ImageTag.Id.NoImage, rootLevel).toHtml())
                .append(", View: \n");
            if (vui != null) {
                heading.append(URLLink.to(vui.getName(), Naming.schemaVuiDetail(schemaName, schemaOverlaid, vuiId), ImageTag.Id.NoImage, rootLevel).toHtml());
            } else {
                heading.append("(null)");
            }
            heading.append(" (Id: ").append(vuiId).append(", Label: ");
            String label = vui == null ? null : PropertyHelper.stringProperty(vui.getDisplayProperties(), Constants.AR_DPROP_LABEL);
            heading.append(label != null && !label.isEmpty() ? WebUtil.validate(label) : Integer.toString(vuiId));
            heading.append(")");
            sb.append(ObjectPropertiesTable.render(e.getValue(), Set.of(), heading.toString()));
        }
        return sb.toString();
    }

    private static View findVui(List<View> vuis, int vuiId) {
        if (vuis == null) return null;
        for (View v : vuis) if (v.getVUIId() == vuiId) return v;
        return null;
    }

    private String permissions(Field field, int rootLevel) {
        List<PermissionInfo> perms = field.getAssignedGroup();
        if (perms == null || perms.isEmpty()) return "";
        Table tbl = new Table("fieldPermissions", "TblObjectList");
        tbl.description = "Permissions";
        tbl.addColumn(30, "Group ID");
        tbl.addColumn(70, "Permission");
        for (PermissionInfo p : perms) {
            tbl.addRow(new TableRow().addCellList(Integer.toString(p.getGroupID()), permissionLabel(p.getPermissionValue())));
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of CAREnum::FieldPermission - None/View/Change. Previously used the WRONG enum
     * (object-level Visible/Hidden wording) - AR_PERMISSIONS_VIEW and AR_PERMISSIONS_VISIBLE share
     * the same raw value (1, confirmed against the arapi jar), so the bug never affected which STATE
     * showed, only the word used for it. See GroupDetailPage.objectPermissionLabel's javadoc for the
     * full story (found via user report on the schema page's Group Permissions, whose "Hidden"
     * (value 2) really did mislabel as "Change" under the old shared function).
     */
    private static String permissionLabel(int permissionValue) {
        if (permissionValue == Constants.AR_PERMISSIONS_VIEW) return "View";
        if (permissionValue == Constants.AR_PERMISSIONS_CHANGE) return "Change";
        if (permissionValue == Constants.AR_PERMISSIONS_NONE) return "None";
        return "";
    }

    /**
     * Java port of the type-specific parts of DocFieldDetails::FieldLimits - the common data types
     * only (see class javadoc), plus Table field limits (Server/Form/Qualification/Max Rows/Num
     * Columns/Columns-table - added after a live C++-vs-Java comparison of workflow "@"
     * (current-server) resolution surfaced that Table fields have the exact same server-name
     * pattern as Set Fields/Push Fields/OpenWindow actions, and were entirely unhandled here) plus
     * Column field limits (Source Type/Source Field/In Schema/Length).
     */
    private String limits(Field field, String schemaName, int rootLevel, Form form, List<Field> allFields, List<View> vuis) {
        FieldLimit limit = field.getFieldLimit();
        if (limit == null) return "";

        Table tbl = new Table("fieldLimits", "TblObjectList");
        tbl.description = "Limits";
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        boolean any = true;

        if (limit instanceof TableFieldLimit l) {
            QualificationRenderer noOpQr = new QualificationRenderer(schemaName, rootLevel, globalFields, (f, id, exists, detail) -> {});
            String targetSchema = resolveTableFormRef(l.getForm(), l.getSampleForm(), schemaName);
            tbl.addRow(new TableRow().addCellList("Server", tableDollarFieldOrServerLink(l.getServer(), l.getSampleServer(), schemaName, noOpQr, rootLevel)));
            tbl.addRow(new TableRow().addCellList("Form", tableDollarFieldOrFormLink(l.getForm(), l.getSampleForm(), targetSchema, schemaName, noOpQr, rootLevel)));
            String qual = l.getQualifier() != null && l.getQualifier().getOperation() != QualifierInfo.AR_COND_OP_NONE
                ? new QualificationRenderer(schemaName, targetSchema, rootLevel, globalFields, (f, id, exists, detail) -> {}).render(l.getQualifier())
                : "No qualification specified";
            tbl.addRow(new TableRow().addCellList("Qualification", qual));
            tbl.addRow(new TableRow().addCellList("Max Rows", Integer.toString(l.getMaxRetrieve())));
            tbl.addRow(new TableRow().addCellList("Num Columns", Integer.toString(l.getColumnCount())));
            tbl.addRow(new TableRow().addCellList("Columns", columnsTableOf(field, schemaName, targetSchema, rootLevel, form, allFields, vuis)));
        } else if (limit instanceof ColumnFieldLimit l) {
            String colSourceForm = columnSourceForm(l, schemaName, allFields);
            QualificationRenderer noOpQr2 = new QualificationRenderer(schemaName, rootLevel, globalFields, (f, id, exists, detail) -> {});
            tbl.addRow(new TableRow().addCellList("Source Type", AREnumLabels.columnDataSourceType(l.getDataSource())));
            tbl.addRow(new TableRow().addCellList("Source Field", noOpQr2.fieldRef(colSourceForm == null ? schemaName : colSourceForm, l.getDataField())));
            tbl.addRow(new TableRow().addCellList("In Schema", tableSchemaLink(colSourceForm, rootLevel)));
            if (l.getDataSource() == Constants.AR_COLUMN_LIMIT_DATASOURCE_DATA_FIELD) {
                tbl.addRow(new TableRow().addCellList("Length", Integer.toString(l.getColumnLength())));
            }
        } else if (limit instanceof CharacterFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Max Length", Integer.toString(l.getMaxLength())));
            tbl.addRow(new TableRow().addCellList("QBE Match", AREnumLabels.qbeMatch(l.getQBEMatch())));
            if (l.getPattern() != null && !l.getPattern().isEmpty()) {
                tbl.addRow(new TableRow().addCellList("Pattern", WebUtil.validate(l.getPattern())));
            }
        } else if (limit instanceof IntegerFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Low Range", Integer.toString(l.getLowRange())));
            tbl.addRow(new TableRow().addCellList("High Range", Integer.toString(l.getHighRange())));
        } else if (limit instanceof RealFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Low Range", Double.toString(l.getLowRange())));
            tbl.addRow(new TableRow().addCellList("High Range", Double.toString(l.getHighRange())));
            tbl.addRow(new TableRow().addCellList("Precision", Integer.toString(l.getPrecision())));
        } else if (limit instanceof DecimalFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Low Range", String.valueOf(l.getLowRange())));
            tbl.addRow(new TableRow().addCellList("High Range", String.valueOf(l.getHighRange())));
            tbl.addRow(new TableRow().addCellList("Precision", Integer.toString(l.getPrecision())));
        } else if (limit instanceof SelectionFieldLimit l) {
            Table enumTbl = new Table("fieldEnumValues", "TblObjectList");
            enumTbl.addColumn(20, "Number");
            enumTbl.addColumn(80, "Name");
            for (EnumItem item : l.getValues()) {
                enumTbl.addRow(new TableRow().addCellList(Integer.toString(item.getEnumItemNumber()), item.getEnumItemName()));
            }
            tbl.addRow(new TableRow().addCellList("Values", enumTbl.toXHtml()));
        } else if (limit instanceof CurrencyFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Low Range", String.valueOf(l.getLowRange())));
            tbl.addRow(new TableRow().addCellList("High Range", String.valueOf(l.getHighRange())));
            tbl.addRow(new TableRow().addCellList("Precision", Integer.toString(l.getPrecision())));
            // Java port of DocFieldDetails.cpp's AR_DATA_TYPE_CURRENCY case's two extra sections
            // this port previously dropped entirely - Allowable Types (currency codes the field
            // accepts, or "all allowed" when the list is empty) and Functional Types (code +
            // precision pairs).
            List<CurrencyDetail> allowable = l.getAllowable();
            StringBuilder allowableSb = new StringBuilder();
            if (allowable == null || allowable.isEmpty()) {
                allowableSb.append("All Allowable Currency Types allowed");
            } else {
                for (CurrencyDetail d : allowable) allowableSb.append("Code: ").append(WebUtil.validate(d.getCurrencyCode())).append("<br/>");
            }
            tbl.addRow(new TableRow().addCellList("Allowable Types", allowableSb.toString()));

            List<CurrencyDetail> functional = l.getFunctional();
            StringBuilder functionalSb = new StringBuilder();
            if (functional != null) {
                for (CurrencyDetail d : functional) {
                    functionalSb.append("Code: ").append(WebUtil.validate(d.getCurrencyCode())).append(" (").append(d.getPrecision()).append(")<br/>");
                }
            }
            tbl.addRow(new TableRow().addCellList("Functional Types", functionalSb.toString()));
        } else if (limit instanceof AttachmentFieldLimit l) {
            tbl.addRow(new TableRow().addCellList("Max Size", l.getMaxSize() + " bytes"));
        } else if (limit instanceof DiaryFieldLimit) {
            any = false;
        } else {
            any = false;
        }

        return any ? tbl.toXHtml() : "";
    }

    /**
     * Java port of DocFieldDetails.cpp's Table-field column listing (Column Title/Field/Type/
     * Source) - finds every sibling Column-type field whose ColumnFieldLimit.getParent() matches
     * this table field's own ID, sorts by each column's default-VUI AR_DPROP_COLUMN_ORDER display
     * property (matching SortByColumnOrder), and resolves each column's source-field data type via
     * GetColumnSourceField's logic (columnSourceForm below). Previously entirely unrendered.
     */
    private String columnsTableOf(Field tableField, String schemaName, String targetSchema, int rootLevel, Form form, List<Field> allFields, List<View> vuis) {
        Integer defaultVuiId = defaultVuiId(form, vuis);
        List<Field> columns = new java.util.ArrayList<>();
        for (Field f : allFields) {
            if (f.getFieldLimit() instanceof ColumnFieldLimit cfl && cfl.getParent() == tableField.getFieldID()) {
                columns.add(f);
            }
        }
        columns.sort(java.util.Comparator.comparingInt(f -> columnOrder(f, defaultVuiId)));

        Table colTbl = new Table("tableColumnList", "TblObjectList");
        colTbl.addColumn(10, "Column Title");
        colTbl.addColumn(70, "Field");
        colTbl.addColumn(10, "Type");
        colTbl.addColumn(10, "Source");
        QualificationRenderer noOpQr = new QualificationRenderer(schemaName, rootLevel, globalFields, (f, id, exists, detail) -> {});
        for (Field colField : columns) {
            ColumnFieldLimit colLimit = (ColumnFieldLimit) colField.getFieldLimit();
            String colSourceForm = columnSourceForm(colLimit, schemaName, allFields);
            // Type resolves via allFields when the source is on this same schema (Display/Control
            // Field), or via GlobalFieldIndex's cross-schema field-data-type map when it's on the
            // table's own target schema (Data Field - the common case, confirmed missing via a live
            // C++-vs-Java comparison: the real C++ resolves "Selection"/"Character"/etc. here even
            // for Data Field columns, this port previously left Type blank for that entire case).
            Integer colSourceType = null;
            if (colSourceForm != null) {
                if (colSourceForm.equals(schemaName)) {
                    Field colSourceField = findField(allFields, colLimit.getDataField());
                    colSourceType = colSourceField == null ? null : colSourceField.getDataType();
                } else {
                    colSourceType = globalFields == null ? null : globalFields.fieldDataType(colSourceForm, colLimit.getDataField());
                }
            }
            String label = columnLabel(colField, defaultVuiId);
            String fieldLink = URLLink.to(colField.getName(), Naming.schemaFieldDetail(schemaName, false, colField.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml();
            String type = colSourceType != null ? AREnumLabels.dataType(colSourceType) : "";
            colTbl.addRow(new TableRow().addCellList(WebUtil.validate(label), fieldLink, type, AREnumLabels.columnDataSourceType(colLimit.getDataSource())));
        }
        return colTbl.toXHtml();
    }

    /** VUI id matching Form.getDefaultVUI()'s name - null if there's no default VUI or it can't be resolved from the fetched vuis list. */
    private static Integer defaultVuiId(Form form, List<View> vuis) {
        String defaultVuiName = form.getDefaultVUI();
        if (defaultVuiName == null || defaultVuiName.isEmpty() || vuis == null) return null;
        for (View v : vuis) {
            if (defaultVuiName.equals(v.getName())) return v.getVUIId();
        }
        return null;
    }

    private static int columnOrder(Field colField, Integer defaultVuiId) {
        if (defaultVuiId == null || colField.getDisplayInstance() == null) return 0;
        com.bmc.arsys.api.Value v = colField.getDisplayInstance().getProperty(defaultVuiId, Constants.AR_DPROP_COLUMN_ORDER);
        return v != null && v.getValue() instanceof Number n ? n.intValue() : 0;
    }

    private static String columnLabel(Field colField, Integer defaultVuiId) {
        if (defaultVuiId == null || colField.getDisplayInstance() == null) return "";
        com.bmc.arsys.api.Value v = colField.getDisplayInstance().getProperty(defaultVuiId, Constants.AR_DPROP_LABEL);
        return v != null && v.getValue() instanceof String s ? s : "";
    }

    private static Field findField(List<Field> fields, int fieldId) {
        for (Field f : fields) if (f.getFieldID() == fieldId) return f;
        return null;
    }

    /**
     * Java port of DocFieldDetails.cpp's GetColumnSourceField's form-resolution half - for
     * DISPLAY_FIELD/CONTROL_FIELD the source field is on the column's own schema; for DATA_FIELD
     * it's on the *parent table field's* own target schema (resolved the same way the table field's
     * own Form line already is); anything else (TRIM_FIELD/VIEW_FIELD) isn't modeled by the real
     * C++ either and returns null here too.
     */
    private String columnSourceForm(ColumnFieldLimit colLimit, String ownSchema, List<Field> allFields) {
        int dataSource = colLimit.getDataSource();
        if (dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_DISPLAY_FIELD || dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_CONTROL_FIELD) {
            return ownSchema;
        }
        if (dataSource == Constants.AR_COLUMN_LIMIT_DATASOURCE_DATA_FIELD) {
            Field parentTableField = findField(allFields, colLimit.getParent());
            if (parentTableField != null && parentTableField.getFieldLimit() instanceof TableFieldLimit tfl) {
                return resolveTableFormRef(tfl.getForm(), tfl.getSampleForm(), ownSchema);
            }
        }
        return null;
    }

    /** Java port of the "@fieldId (Sample Form: link)" vs "@"/plain-form-literal branch DocFieldDetails.cpp's Table-limit rendering uses - same shape as ActionSummaryTable's resolveFormRef, duplicated locally since that class's helpers are private and this is the only field-limit call site that needs it. */
    private static String resolveTableFormRef(String raw, String sampleForm, String ownForm) {
        if (raw == null || raw.isEmpty()) return ownForm;
        if (raw.charAt(0) != '$') return raw;
        Integer fieldId = tableParseSampleFieldId(raw);
        if (fieldId == null) return ownForm;
        if (fieldId == -Constants.AR_KEYWORD_SCHEMA) return ownForm;
        return sampleForm == null || sampleForm.isEmpty() || sampleForm.equals("@") ? ownForm : sampleForm;
    }

    private String tableDollarFieldOrServerLink(String raw, String sampleServer, String ownForm, QualificationRenderer qr, int rootLevel) {
        if (raw != null && raw.length() > 1 && raw.charAt(0) == '$') {
            Integer fieldId = tableParseSampleFieldId(raw);
            if (fieldId != null) {
                String inner = fieldId < 0 ? ARKeywordLabels.forFieldId(fieldId) : qr.fieldRef(ownForm, fieldId);
                return "$" + inner + "$ (Sample Server: " + tableServerLink(sampleServer, rootLevel) + ")";
            }
        }
        return tableServerLink(raw, rootLevel);
    }

    /** Empty or "@" (current server) both link to this run's actual connected server - see ActionSummaryTable.serverInfoLink's javadoc for why (confirmed via a live C++-vs-Java comparison that this port's "no ServerInfo hyperlink" behavior was a stale assumption, not deliberate). */
    private String tableServerLink(String serverName, int rootLevel) {
        if (serverName == null || serverName.isEmpty() || serverName.equals("@")) {
            if (appConfig.serverName == null || appConfig.serverName.isEmpty()) return "";
            return URLLink.to(appConfig.serverName, Naming.serverInfo(), ImageTag.Id.NoImage, rootLevel).toHtml();
        }
        return URLLink.to(serverName, Naming.serverInfo(), ImageTag.Id.NoImage, rootLevel).toHtml();
    }

    private String tableDollarFieldOrFormLink(String raw, String sampleForm, String resolvedTargetSchema, String ownForm, QualificationRenderer qr, int rootLevel) {
        if (raw != null && raw.length() > 1 && raw.charAt(0) == '$') {
            Integer fieldId = tableParseSampleFieldId(raw);
            if (fieldId != null) {
                String inner = fieldId < 0 ? ARKeywordLabels.forFieldId(fieldId) : qr.fieldRef(ownForm, fieldId);
                return "$" + inner + "$ (Sample Form: " + tableSchemaLink(resolvedTargetSchema, rootLevel) + ")";
            }
        }
        return tableSchemaLink(resolvedTargetSchema, rootLevel);
    }

    private String tableSchemaLink(String formName, int rootLevel) {
        if (formName == null || formName.isEmpty()) return "";
        boolean isOverlaid = globalFields != null && globalFields.isOverlaid(formName);
        return URLLink.to(formName, Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    private static Integer tableParseSampleFieldId(String raw) {
        try {
            return Integer.parseInt(raw.substring(1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
