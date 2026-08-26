package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.AppMembershipIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MenuAttachmentIndex;
import arinside.scan.WorkflowReferenceIndex;
import com.bmc.arsys.api.*;

import java.util.List;
import java.util.Set;

/**
 * Java port of doc/DocCharMenuDetails.cpp - renders each menu's real per-subtype definition
 * directly, matching the C++: {@link ListMenu} shows its static item tree, {@link QueryMenu}/
 * {@link SqlMenu} show their qualification/SQL command rendered once per form the menu is actually
 * attached to (see {@link MenuAttachmentIndex}), {@link FileMenu} shows its filename/location, and
 * {@link DataDictionaryMenu} shows its form/field/license lookup config - no live server call
 * beyond the one `getMenu()` fetch (with `MenuCriteria.setRetrieveAll(true)`, see
 * WorkflowRepository's javadoc) is needed for any of this.
 *
 * <p>Query/SQL menu qualifications are rendered structurally against each attached form via a
 * two-schema qualifier (schema1=attached/calling form, schema2=menu's own query form), matching
 * how the C++ resolves them, via {@link QualificationRenderer}'s two-form constructor - not
 * resolved by actually running the query against the server, since a `$fieldId$` token in the
 * qualification may refer to a field on the calling form rather than the menu's own query-source
 * form.
 */
public final class MenuDetailPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final Set<String> knownUserNames;
    private final WorkflowReferenceIndex workflowIndex;
    private final GlobalFieldIndex globalFields;
    private final MenuAttachmentIndex attachments;
    private final arinside.ar.ContainerSource containers;
    private final AppMembershipIndex appIndex;
    private final ContainerReferenceIndex containerRefs;

    public MenuDetailPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, Set<String> knownUserNames,
                           WorkflowReferenceIndex workflowIndex, GlobalFieldIndex globalFields, MenuAttachmentIndex attachments,
                           arinside.ar.ContainerSource containers, AppMembershipIndex appIndex, ContainerReferenceIndex containerRefs) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.knownUserNames = knownUserNames;
        this.workflowIndex = workflowIndex;
        this.globalFields = globalFields;
        this.attachments = attachments;
        this.containers = containers;
        this.appIndex = appIndex;
        this.containerRefs = containerRefs;
    }

    public record MenuData(String name, Menu menu) {}

    /** The fetch half - safe to run on a pooled read connection. */
    public MenuData fetch(WorkflowSource repo, String name) throws ARException {
        return new MenuData(name, repo.getMenu(name));
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(MenuData data) throws ARException {
        String name = data.name();
        Menu menu = data.menu();
        PagePath page = Naming.menuDetail(name, OverlaySupport.isOverlaidForNaming(menu.getProperties(), serverOverlayMode));

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String head = URLLink.to("Menus", Naming.menuOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Menu, page.rootLevel()).toHtml() + WebUtil.objName(name)
            + ApplicationHeaderLink.suffix(appIndex.menuApp(name), page.rootLevel());
        webPage.addContentHead(head);

        Table tbl = new Table("menuGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Type", AREnumLabels.menuType(menu.getMenuType())));
        tbl.addRow(new TableRow().addCellList("Refresh", AREnumLabels.menuRefresh(menu.getRefreshCode())));
        webPage.addContent(tbl.toXHtml());

        webPage.addContent(definition(name, menu, page.rootLevel()));
        webPage.addContent(relatedFields(name, page.rootLevel()));
        webPage.addContent(relatedActiveLinks(name, page.rootLevel()));
        webPage.addContent(containerReferences(name, page.rootLevel()));
        webPage.addContent(ObjectPropertiesTable.render(menu.getProperties()));
        webPage.addContent(ServerObjectHistoryWidget.render(menu, knownUserNames, page.rootLevel()));

        workflowIndex.addIfOverlayOrCustom(menu.getProperties(),
            new WorkflowReferenceIndex.Ref(name, "Menu", ImageTag.Id.Menu, page), null, menu.getLastUpdateTime(), menu.getLastChangedBy());

        webPage.saveInFolder(page.path());
    }

    /** Dispatches on the real menu subtype - matches DocCharMenuDetails.cpp's switch on menuType. */
    private String definition(String menuName, Menu menu, int rootLevel) {
        if (menu instanceof ListMenu lm) return listMenuDefinition(lm);
        if (menu instanceof QueryMenu qm) return perAttachedForm(menuName, rootLevel, form -> queryMenuRow(qm, form, rootLevel));
        if (menu instanceof SqlMenu sm) return perAttachedForm(menuName, rootLevel, form -> sqlMenuRow(sm, form, rootLevel));
        if (menu instanceof FileMenu fm) return fileMenuDefinition(fm);
        if (menu instanceof DataDictionaryMenu ddm) return perAttachedForm(menuName, rootLevel, form -> dataDictMenuRow(ddm, form, rootLevel));
        return "";
    }

    /**
     * Java port of DocCharMenuDetails.cpp's CharMenuDetails - a flat, non-recursive table of the
     * menu's top-level items only (Type/Label/Value columns), matching the C++.
     */
    private String listMenuDefinition(ListMenu lm) {
        Table tbl = new Table("menuItems", "TblObjectList");
        tbl.description = "Menu Definition";
        tbl.addColumn(20, "Type");
        tbl.addColumn(40, "Label");
        tbl.addColumn(40, "Value");
        List<MenuItem> items = lm.getItems();
        int count = 0;
        if (items != null) {
            for (MenuItem item : items) {
                String value = item.getType() == Constants.AR_MENU_TYPE_VALUE ? nullToEmpty(item.getValue()) : "";
                tbl.addRow(new TableRow().addCellList(AREnumLabels.menuItemType(item.getType()), WebUtil.validate(nullToEmpty(item.getLabel())), WebUtil.validate(value)));
                count++;
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /** Java port of DocCharMenuDetails.cpp's FileMenuDetails - filename/location are on the object directly, no attached-form context needed. */
    private String fileMenuDefinition(FileMenu fm) {
        Table tbl = new Table("menuFile", "TblObjectList");
        tbl.description = "Menu Definition";
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("File Name", WebUtil.validate(nullToEmpty(fm.getFileName()))));
        tbl.addRow(new TableRow().addCellList("Location", fileMenuLocation(fm.getLocation())));
        return tbl.toXHtml();
    }

    private static String fileMenuLocation(int location) {
        if (location == Constants.AR_MENU_FILE_SERVER) return "Server";
        if (location == Constants.AR_MENU_FILE_CLIENT) return "Client";
        return "Unknown";
    }

    /**
     * Runs the given per-form row renderer once for every form this menu is attached to (see
     * {@link MenuAttachmentIndex}), or once with an empty form name if it's attached to nothing -
     * matches DocCharMenuDetails.cpp's "add a dummy -1 schema so the definition still generates at
     * least once" fallback for an unattached menu.
     */
    private String perAttachedForm(String menuName, int rootLevel, java.util.function.Function<String, TableRow> rowFor) {
        Table tbl = new Table("menuDefinition", "TblObjectList");
        tbl.description = "Menu Definition";
        tbl.addColumn(30, "Attached Form");
        tbl.addColumn(70, "Value");
        Set<String> forms = attachments.attachedForms(menuName);
        if (forms.isEmpty()) {
            tbl.addRow(rowFor.apply(""));
        } else {
            for (String form : forms) {
                tbl.addRow(rowFor.apply(form));
            }
        }
        tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    private String formCell(String attachedForm, int rootLevel) {
        if (attachedForm.isEmpty()) return "Menu Definition";
        boolean isOverlaid = globalFields != null && globalFields.isOverlaid(attachedForm);
        return URLLink.to(attachedForm, Naming.schemaDetail(attachedForm, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    /** Java port of DocCharMenuDetails.cpp's SearchMenuDetails - two-schema qualifier (attachedForm=calling form, qm.getForm()=the menu's own query source form), matching CARQualification's schema1/schema2 resolution rule. */
    private TableRow queryMenuRow(QueryMenu qm, String attachedForm, int rootLevel) {
        String querySchema = nullToEmpty(qm.getForm());
        QualificationRenderer qr = new QualificationRenderer(attachedForm, querySchema, rootLevel, globalFields, (f, id, exists, detail) -> {});

        StringBuilder sb = new StringBuilder();
        sb.append("Server: ").append(WebUtil.validate(nullToEmpty(qm.getServer()))).append("<br/>\n");
        sb.append("Schema: ").append(URLLink.to(querySchema, Naming.schemaDetail(querySchema, globalFields != null && globalFields.isOverlaid(querySchema)), ImageTag.Id.Schema, rootLevel).toHtml()).append("<br/>\n");

        List<Integer> labelFields = qm.getLabelField();
        if (labelFields != null) {
            for (int i = 0; i < labelFields.size(); i++) {
                int fieldId = labelFields.get(i);
                if (fieldId == 0) continue;
                sb.append("Label Field (").append(i).append(") : ").append(qr.fieldRef(querySchema, fieldId))
                    .append(" (FieldId: ").append(fieldId).append(")<br/>\n");
            }
        }
        sb.append("Sort On Label: ").append(qm.isSortOnLabel() ? "Yes" : "No").append("<br/>\n");
        sb.append("Value Field: ").append(qr.fieldRef(querySchema, qm.getValueField())).append("<br/>\n");

        String qualText = qm.getQualification() != null ? qr.render(qm.getQualification()) : "";
        if (!qualText.isEmpty()) {
            sb.append("Qualification:<br/>\n").append(qualText).append("\n");
        } else {
            sb.append("Qualification: ").append(WebUtil.EMPTY_VALUE).append("<br/>\n");
        }

        return new TableRow().addCellList(formCell(attachedForm, rootLevel), sb.toString());
    }

    /** Java port of DocCharMenuDetails.cpp's SqlMenuDetails - the SQL command itself is free text potentially containing $fieldId$ tokens, resolved via TextFieldSubstitution against the attached (calling) form, same mechanism ActionSummaryTable's Run Process fix uses. */
    private TableRow sqlMenuRow(SqlMenu sm, String attachedForm, int rootLevel) {
        QualificationRenderer qr = new QualificationRenderer(attachedForm, rootLevel, globalFields, (f, id, exists, detail) -> {});

        StringBuilder sb = new StringBuilder();
        sb.append("Server: ").append(WebUtil.validate(nullToEmpty(sm.getServer()))).append("<br/>\n");
        sb.append("Label Index List: ").append(indexListOf(sm.getLabelIndex())).append("<br/>\n");
        sb.append("Value Index: ").append(sm.getValueIndex()).append("<br/><br/>\n");
        sb.append("SQL Command: ").append(TextFieldSubstitution.substitute(sm.getSQLCommand(), attachedForm, qr, "Field in Menus SQL-Command"));

        return new TableRow().addCellList(formCell(attachedForm, rootLevel), sb.toString());
    }

    private static String indexListOf(List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer i : indexes) {
            if (i == null || i == 0) break;
            if (sb.length() > 0) sb.append(",");
            sb.append(i);
        }
        return sb.toString();
    }

    /** Java port of DocCharMenuDetails.cpp's DataDictMenuDetails - dispatches on the real subtype (Form/Field/License) rather than a struct union tag, matching what FormDataDictionaryMenu/FieldDataDictionaryMenu/LicenseDataDictionaryMenu already give directly. */
    private TableRow dataDictMenuRow(DataDictionaryMenu ddm, String attachedForm, int rootLevel) {
        QualificationRenderer qr = new QualificationRenderer(attachedForm, rootLevel, globalFields, (f, id, exists, detail) -> {});
        StringBuilder sb = new StringBuilder();
        String server = ddm.getServer();
        String serverText = server != null && server.startsWith("$") ? TextFieldSubstitution.substitute(server, attachedForm, qr, "Server-Value in Menu") : WebUtil.validate(nullToEmpty(server));
        sb.append("Server: ").append(serverText).append("<br/>\n");
        sb.append("Label Format: ").append(AREnumLabels.menuDDLabelFormat(ddm.getNameType())).append("<br/>\n");
        sb.append("Value Format: ").append(AREnumLabels.menuDDValueFormat(ddm.getValueFormat())).append("<br/>\n");

        if (ddm instanceof FormDataDictionaryMenu form) {
            sb.append("Object Type: Form<br/>\n");
            String schemaType = form.getFormType() > 0 ? AREnumLabels.schemaType(form.getFormType()) : "All";
            sb.append("Form Type: ").append(schemaType).append("<br/>\n");
            sb.append("Show Hidden Forms: ").append(form.isIncludeHidden() ? "Yes" : "No").append("<br/>\n");
        } else if (ddm instanceof FieldDataDictionaryMenu field) {
            sb.append("Object Type: Field<br/>\n");
            sb.append("Form Name: ").append(TextFieldSubstitution.substitute(field.getForm(), attachedForm, qr, "Form-Value in Menu")).append("<br/><br/>\n");
            sb.append("Field Type:<br/>\n").append(AREnumLabels.menuDDFieldTypes(field.getFieldType())).append("<br/>\n");
        } else if (ddm instanceof LicenseDataDictionaryMenu license) {
            sb.append("Object Type: License<br/>\n");
            sb.append("License Type: ").append(license.getLicenseType()).append("<br/>\n");
        }

        return new TableRow().addCellList(formCell(attachedForm, rootLevel), sb.toString());
    }

    /** Java port of DocCharMenuDetails.cpp's RelatedFields. */
    private String relatedFields(String menuName, int rootLevel) {
        List<MenuAttachmentIndex.FieldRef> refs = attachments.relatedFields(menuName);
        if (refs.isEmpty()) return "";
        Table tbl = new Table("relatedFields", "TblObjectList");
        tbl.description = "Related Fields";
        tbl.addColumn(40, "Field Name");
        tbl.addColumn(20, "Field Id");
        tbl.addColumn(40, "Form");
        for (MenuAttachmentIndex.FieldRef ref : refs) {
            boolean isOverlaid = globalFields != null && globalFields.isOverlaid(ref.formName());
            tbl.addRow(new TableRow().addCellList(
                URLLink.to(ref.fieldName(), Naming.schemaFieldDetail(ref.formName(), isOverlaid, ref.fieldId()), ImageTag.Id.Document, rootLevel).toHtml(),
                Integer.toString(ref.fieldId()),
                URLLink.to(ref.formName(), Naming.schemaDetail(ref.formName(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml()));
        }
        return tbl.toXHtml();
    }

    /** Java port of DocCharMenuDetails.cpp's RelatedActiveLinks. */
    private String relatedActiveLinks(String menuName, int rootLevel) {
        List<MenuAttachmentIndex.ActiveLinkRef> refs = attachments.relatedActiveLinks(menuName);
        if (refs.isEmpty()) return "";
        Table tbl = new Table("relatedActiveLinks", "TblObjectList");
        tbl.description = "Related Active Links (Change Field)";
        tbl.addColumn(100, "Active Link");
        for (MenuAttachmentIndex.ActiveLinkRef ref : refs) {
            tbl.addRow(new TableRow().addCellList(ref.branch() + "-Action " + ref.actionIndex() + " "
                + URLLink.to(ref.activeLinkName(), Naming.activeLinkDetail(ref.activeLinkName(), false), ImageTag.Id.ActiveLink, rootLevel).toHtml()));
        }
        return tbl.toXHtml();
    }

    /** Java port of DocCharMenuDetails.cpp's ContainerReferences (496-536) - which non-Application containers reference this menu. */
    private String containerReferences(String menuName, int rootLevel) {
        List<arinside.scan.ContainerReferenceIndex.ContainerRef> refs = containerRefs.menuContainers(menuName);
        if (refs.isEmpty()) return "";
        Table tbl = new Table("menuContainerReferences", "TblObjectList");
        tbl.description = "Container References";
        tbl.addColumn(100, "Container");
        for (arinside.scan.ContainerReferenceIndex.ContainerRef ref : refs) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(ref.name(), Naming.containerDetail(ref.containerType(), ref.name(), false), GroupDetailPage.containerIcon(ref.containerType()), rootLevel).toHtml()));
        }
        return tbl.toXHtml();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
