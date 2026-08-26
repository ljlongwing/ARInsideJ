package arinside.doc;

import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;
import arinside.scan.MissingMenuReferenceIndex;
import arinside.scan.PermissionIndex;
import com.bmc.arsys.api.Constants;

import java.util.List;

/**
 * Java port of doc/DocValidator.cpp - the four "objects with no or unknown access group" checks
 * (FormGroupValidator/FieldGroupValidator/AlGroupValidator/ContainerGroupValidator, sourced from
 * PermissionIndex's existing full pass) plus the two "missing reference" checks:
 * FieldReferenceValidator (sourced from MissingFieldReferenceIndex, populated as a side effect of
 * QualificationRenderer's field-reference callback while AL/filter/escalation Run If qualifiers
 * are rendered - see FieldReferenceIndex's javadoc for the same pattern) and MenuReferenceValidator
 * (sourced from PermissionIndex.missingMenuRefs(), piggybacked on the same form/field pass since
 * it already visits every CharacterFieldLimit). Neither needs its own fetch pass.
 */
public final class ValidatorPage {
    private final AppConfig appConfig;
    private final PermissionIndex permIndex;
    private final MissingFieldReferenceIndex missingFieldRefs;
    private final GlobalFieldIndex globalFields;

    public ValidatorPage(AppConfig appConfig, PermissionIndex permIndex, MissingFieldReferenceIndex missingFieldRefs, GlobalFieldIndex globalFields) {
        this.appConfig = appConfig;
        this.permIndex = permIndex;
        this.missingFieldRefs = missingFieldRefs;
        this.globalFields = globalFields;
    }

    public void render() {
        PagePath page = Naming.validatorMain();
        WebPage webPage = new WebPage(page.fileName(), "Validator", page.rootLevel(), appConfig);
        webPage.addContentHead("Server object validation:");

        StringBuilder content = new StringBuilder();
        content.append("Missing Objects referenced by Workflow:<br/>\n");
        content.append(link("Fields", Naming.validatorFieldReferences(), page.rootLevel())).append("<br/>\n");
        content.append(link("Menus", Naming.validatorMenuReferences(), page.rootLevel())).append("<br/><br/>\n");
        content.append("List of server objects with no specified access groups:<br/>\n");
        content.append(link("Forms", Naming.validatorFormGroups(), page.rootLevel())).append("<br/>\n");
        content.append(link("Fields", Naming.validatorFieldGroups(), page.rootLevel())).append("<br/>\n");
        content.append(link("Active Links", Naming.validatorActiveLinkGroups(), page.rootLevel())).append("<br/>\n");
        content.append(link("Container", Naming.validatorContainerGroups(), page.rootLevel()));
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        fieldReferences();
        menuReferences();
        formGroups();
        fieldGroups();
        activeLinkGroups();
        containerGroups();
    }

    private void fieldReferences() {
        PagePath page = Naming.validatorFieldReferences();
        WebPage webPage = new WebPage(page.fileName(), "Field validator", page.rootLevel(), appConfig);
        webPage.addContentHead(head("List of missing fields that are referenced in ARSystem workflow:", page.rootLevel()));

        List<MissingFieldReferenceIndex.Entry> entries = missingFieldRefs.entries();
        if (entries.isEmpty()) {
            webPage.addContent("Integrity check found no missing fields in workflow.");
        } else {
            Table tbl = new Table("fieldListAll", "TblObjectList");
            tbl.addColumn(10, "Missing Field");
            tbl.addColumn(30, "Form");
            tbl.addColumn(60, "Workflow Item");
            for (var e : entries) {
                tbl.addRow(new TableRow().addCellList(Integer.toString(e.fieldId()),
                    e.formName(),
                    URLLink.to(e.owner().name(), e.owner().link(), e.owner().icon(), page.rootLevel()).toHtml() + " (" + e.owner().detail() + ")"));
            }
            webPage.addContent(tbl.toXHtml());
        }
        webPage.saveInFolder(page.path());
    }

    private void menuReferences() {
        PagePath page = Naming.validatorMenuReferences();
        WebPage webPage = new WebPage(page.fileName(), "Menu validator", page.rootLevel(), appConfig);
        webPage.addContentHead(head("List of missing menus that are referenced in ARSystem workflow:", page.rootLevel()));

        List<MissingMenuReferenceIndex.Entry> entries = permIndex.missingMenuRefs().entries();
        if (entries.isEmpty()) {
            webPage.addContent("Integrity check found no missing menus in workflow.");
        } else {
            Table tbl = new Table("missingMenus", "TblObjectList");
            tbl.addColumn(25, "Menu Name");
            tbl.addColumn(15, "Object Type");
            tbl.addColumn(30, "Server object");
            tbl.addColumn(30, "Details");
            for (var e : entries) {
                tbl.addRow(missingMenuRow(e, page.rootLevel()));
            }
            webPage.addContent(tbl.toXHtml());
        }
        webPage.saveInFolder(page.path());
    }

    /** Java port of DocValidator.cpp's MenuReferenceValidator row rendering - dispatches on the entry's real source type (field/active link/packing list, see MissingMenuReferenceIndex's javadoc) instead of the C++'s generic CRefItem/WorkflowReferenceTable::LinkToObjByRefItem polymorphism, which this port doesn't have an equivalent of. */
    private TableRow missingMenuRow(MissingMenuReferenceIndex.Entry e, int rootLevel) {
        if (e instanceof MissingMenuReferenceIndex.FieldEntry f) {
            boolean isOverlaid = globalFields.isOverlaid(f.formName());
            return new TableRow().addCellList(f.menuName(), "Field",
                URLLink.to(f.formName(), Naming.schemaDetail(f.formName(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml(),
                URLLink.to(f.fieldName(), Naming.schemaFieldDetail(f.formName(), isOverlaid, f.fieldId()), ImageTag.Id.Document, rootLevel).toHtml());
        }
        if (e instanceof MissingMenuReferenceIndex.ActiveLinkEntry al) {
            boolean isOverlaid = permIndex.isOverlaidActiveLink(al.activeLinkName());
            return new TableRow().addCellList(al.menuName(), "Active Link",
                URLLink.to(al.activeLinkName(), Naming.activeLinkDetail(al.activeLinkName(), isOverlaid), ImageTag.Id.ActiveLink, rootLevel).toHtml(),
                al.branch() + "-Action " + al.actionIndex());
        }
        MissingMenuReferenceIndex.PackingListEntry pl = (MissingMenuReferenceIndex.PackingListEntry) e;
        boolean isOverlaid = permIndex.isOverlaidContainer(Constants.ARCON_PACK, pl.packingListName());
        return new TableRow().addCellList(pl.menuName(), "Packing List",
            URLLink.to(pl.packingListName(), Naming.containerDetail(Constants.ARCON_PACK, pl.packingListName(), isOverlaid), ImageTag.Id.PackingList, rootLevel).toHtml(),
            "");
    }

    private static String link(String caption, PagePath target, int rootLevel) {
        return URLLink.to(caption, target, ImageTag.Id.Document, rootLevel).toHtml();
    }

    private static String head(String title, int rootLevel) {
        return URLLink.to("Validation", Naming.validatorMain(), ImageTag.Id.NoImage, rootLevel).toHtml() + " &gt; " + title;
    }

    private void formGroups() {
        PagePath page = Naming.validatorFormGroups();
        WebPage webPage = new WebPage(page.fileName(), "Form access group validation", page.rootLevel(), appConfig);
        webPage.addContentHead(head("Forms with no or unknown group permission:", page.rootLevel()));

        Table tbl = new Table("FormNoPermission", "TblObjectList");
        tbl.addColumn(100, "Form Name");
        for (String name : permIndex.formsNoPermission()) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.schemaDetail(name, globalFields.isOverlaid(name)), ImageTag.Id.Schema, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private void fieldGroups() {
        PagePath page = Naming.validatorFieldGroups();
        WebPage webPage = new WebPage(page.fileName(), "Field access group validation", page.rootLevel(), appConfig);
        webPage.addContentHead(head("Fields with no or unknown group permission:", page.rootLevel()));

        Table tbl = new Table("FieldsNoPermission", "TblObjectList");
        tbl.addColumn(15, "Num. Fields");
        tbl.addColumn(85, "Form Name");
        for (var entry : permIndex.fieldsNoPermissionByForm().entrySet()) {
            tbl.addRow(new TableRow().addCellList(Integer.toString(entry.getValue().size()),
                URLLink.to(entry.getKey(), Naming.validatorFieldGroupDetails(entry.getKey()), ImageTag.Id.Document, page.rootLevel()).toHtml()));
            fieldGroupDetails(entry.getKey(), entry.getValue());
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private void fieldGroupDetails(String schemaName, List<String> fieldNames) {
        PagePath page = Naming.validatorFieldGroupDetails(schemaName);
        WebPage webPage = new WebPage(page.fileName(), "Field access group validation", page.rootLevel(), appConfig);
        webPage.addContentHead(head("Fields with no or unknown group permission:", page.rootLevel()));

        Table tbl = new Table("FieldsNoPermission", "TblObjectList");
        tbl.description = URLLink.to(schemaName, Naming.schemaDetail(schemaName, globalFields.isOverlaid(schemaName)), ImageTag.Id.Schema, page.rootLevel()).toHtml();
        tbl.addColumn(100, "Field Name");
        for (String fieldName : fieldNames) {
            tbl.addRow(new TableRow().addCellList(fieldName));
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private void activeLinkGroups() {
        PagePath page = Naming.validatorActiveLinkGroups();
        WebPage webPage = new WebPage(page.fileName(), "Active Link access group validation", page.rootLevel(), appConfig);
        webPage.addContentHead(head("Active Links with no or unknown group permission:", page.rootLevel()));

        Table tbl = new Table("AlNoPermission", "TblObjectList");
        tbl.addColumn(100, "Active Link");
        for (String name : permIndex.activeLinksNoPermission()) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.activeLinkDetail(name, permIndex.isOverlaidActiveLink(name)), ImageTag.Id.ActiveLink, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private void containerGroups() {
        PagePath page = Naming.validatorContainerGroups();
        WebPage webPage = new WebPage(page.fileName(), "Container access group validation", page.rootLevel(), appConfig);
        webPage.addContentHead(head("Container with no or unknown group permission:", page.rootLevel()));

        Table tbl = new Table("ContainerNoPermission", "TblObjectList");
        tbl.addColumn(15, "Type");
        tbl.addColumn(85, "Container");
        for (var c : permIndex.containersNoPermission()) {
            tbl.addRow(new TableRow().addCellList(containerTypeLabel(c.containerType()),
                URLLink.to(c.name(), Naming.containerDetail(c.containerType(), c.name(), permIndex.isOverlaidContainer(c.containerType(), c.name())), GroupDetailPage.containerIcon(c.containerType()), page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private static String containerTypeLabel(int containerType) {
        return switch (containerType) {
            case 1 -> "Active Link Guide";
            case 2 -> "Application";
            case 5 -> "Web Service";
            default -> "";
        };
    }
}
