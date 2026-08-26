package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.PermissionIndex;
import arinside.scan.WorkflowReferenceIndex;
import arinside.util.DateTimeFormat;

/**
 * Java port of doc/DocCustomWorkflow.cpp - forms/fields/VUIs (via PermissionIndex's existing form
 * pass, which already visits every field and now also every VUI of every form) plus active
 * links/filters/escalations/menus/containers/images (all via
 * WorkflowReferenceIndex.overlayOrCustom(), fed as a side effect of each type's own detail-page
 * rendering - see WorkflowReferenceIndex.addIfOverlayOrCustom()). Every object type this port
 * documents is now covered.
 *
 * Every row also carries the C++'s Type (Overlay/Custom)/Enabled/Changed/By columns (Enabled is
 * blank for object types with no enable concept - Forms/Fields/VUIs/Menus/Containers/Images -
 * matching DocCustomWorkflow.cpp's AddTableRow overloads exactly). Kept as four separate,
 * per-type tables (Forms/Fields/VUIs/Workflow) rather than the C++'s single flat table, since the
 * table grouping+description headers already convey what the C++'s "ObjType" column spells out
 * per row, and PermissionIndex/WorkflowReferenceIndex are already organized this way.
 */
public final class CustomWorkflowPage {
    private final AppConfig appConfig;
    private final PermissionIndex permIndex;
    private final WorkflowReferenceIndex workflowIndex;
    private final GlobalFieldIndex globalFields;
    private final java.util.Set<String> knownUserNames;

    public CustomWorkflowPage(AppConfig appConfig, PermissionIndex permIndex, WorkflowReferenceIndex workflowIndex, GlobalFieldIndex globalFields, java.util.Set<String> knownUserNames) {
        this.appConfig = appConfig;
        this.permIndex = permIndex;
        this.workflowIndex = workflowIndex;
        this.globalFields = globalFields;
        this.knownUserNames = knownUserNames;
    }

    public void render() {
        PagePath page = Naming.customWorkflow();
        WebPage webPage = new WebPage(page.fileName(), "Custom Workflow", page.rootLevel(), appConfig);
        webPage.addContentHead("Overlay and custom objects:");

        Table formTbl = new Table("customForms", "TblObjectList");
        formTbl.description = "Forms";
        formTbl.addColumn(40, "Form Name");
        formTbl.addColumn(15, "Type");
        formTbl.addColumn(20, "Changed");
        formTbl.addColumn(25, "By");
        for (var f : permIndex.overlayOrCustomForms()) {
            formTbl.addRow(new TableRow().addCellList(
                URLLink.to(f.name(), Naming.schemaDetail(f.name(), globalFields.isOverlaid(f.name())), ImageTag.Id.Schema, page.rootLevel()).toHtml(),
                OverlaySupport.overlayTypeLabel(f.overlayType()), changed(f.lastUpdateTime()), userLink(f.lastChangedBy(), page.rootLevel())));
        }
        webPage.addContent(formTbl.toXHtml());

        Table fieldTbl = new Table("customFields", "TblObjectList");
        fieldTbl.description = "Fields";
        fieldTbl.addColumn(25, "Form");
        fieldTbl.addColumn(30, "Field");
        fieldTbl.addColumn(15, "Type");
        fieldTbl.addColumn(15, "Changed");
        fieldTbl.addColumn(15, "By");
        for (var f : permIndex.overlayOrCustomFields()) {
            fieldTbl.addRow(new TableRow().addCellList(WebUtil.validate(f.formName()),
                URLLink.to(f.fieldName(), Naming.schemaFieldDetail(f.formName(), globalFields.isOverlaid(f.formName()), f.fieldId()), ImageTag.Id.Document, page.rootLevel()).toHtml(),
                OverlaySupport.overlayTypeLabel(f.overlayType()), changed(f.lastUpdateTime()), userLink(f.lastChangedBy(), page.rootLevel())));
        }
        webPage.addContent(fieldTbl.toXHtml());

        Table vuiTbl = new Table("customViews", "TblObjectList");
        vuiTbl.description = "VUIs";
        vuiTbl.addColumn(25, "Form");
        vuiTbl.addColumn(30, "VUI");
        vuiTbl.addColumn(15, "Type");
        vuiTbl.addColumn(15, "Changed");
        vuiTbl.addColumn(15, "By");
        for (var v : permIndex.overlayOrCustomViews()) {
            vuiTbl.addRow(new TableRow().addCellList(WebUtil.validate(v.formName()),
                URLLink.to("VUI " + v.vuiId(), Naming.schemaVuiDetail(v.formName(), globalFields.isOverlaid(v.formName()), v.vuiId()), ImageTag.Id.Document, page.rootLevel()).toHtml(),
                OverlaySupport.overlayTypeLabel(v.overlayType()), changed(v.lastUpdateTime()), userLink(v.lastChangedBy(), page.rootLevel())));
        }
        webPage.addContent(vuiTbl.toXHtml());

        Table wfTbl = new Table("customWorkflow", "TblObjectList");
        wfTbl.description = "Active Links / Filters / Escalations / Menus / Containers / Images";
        wfTbl.addColumn(15, "ObjType");
        wfTbl.addColumn(30, "Name");
        wfTbl.addColumn(15, "Type");
        wfTbl.addColumn(10, "Enabled");
        wfTbl.addColumn(15, "Changed");
        wfTbl.addColumn(15, "By");
        for (var cw : workflowIndex.overlayOrCustom()) {
            var ref = cw.ref();
            TableRow row = new TableRow();
            row.addCell(ref.typeLabel());
            row.addCell(URLLink.to(ref.name(), ref.link(), ref.icon(), page.rootLevel()).toHtml());
            row.addCell(OverlaySupport.overlayTypeLabel(cw.overlayType()));
            row.addCell(cw.enabled() == null ? new TableCell("") : new TableCell(AREnumLabels.objectEnable(cw.enabled()), cw.enabled() ? "" : "objStatusDisabled"));
            row.addCell(changed(cw.lastUpdateTime()));
            row.addCell(userLink(cw.lastChangedBy(), page.rootLevel()));
            wfTbl.addRow(row);
        }
        webPage.addContent(wfTbl.toXHtml());

        webPage.saveInFolder(page.path());
    }

    private String changed(com.bmc.arsys.api.Timestamp ts) {
        return ts == null ? "" : DateTimeFormat.toHtmlString(ts.getValue());
    }

    private String userLink(String userName, int rootLevel) {
        if (userName == null || userName.isEmpty()) return "";
        if (!knownUserNames.contains(userName)) return WebUtil.validate(userName);
        return URLLink.to(userName, Naming.userDetail(userName), ImageTag.Id.User, rootLevel).toHtml();
    }
}
