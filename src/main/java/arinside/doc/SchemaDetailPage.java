package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.ARPropertyLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.SchemaSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.ImageReferenceIndex;
import arinside.scan.JoinFieldIndex;
import arinside.scan.SchemaTypeIndex;
import arinside.scan.WorkflowReferenceIndex;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ArchiveInfo;
import com.bmc.arsys.api.AuditInfo;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.EntryListFieldInfo;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.FieldMapping;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.IndexInfo;
import com.bmc.arsys.api.JoinFieldMapping;
import com.bmc.arsys.api.JoinForm;
import com.bmc.arsys.api.VendorFieldMapping;
import com.bmc.arsys.api.ViewFieldMapping;
import com.bmc.arsys.api.ViewForm;
import com.bmc.arsys.api.PermissionInfo;
import com.bmc.arsys.api.QualifierInfo;
import com.bmc.arsys.api.SortInfo;
import com.bmc.arsys.api.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of doc/DocSchemaDetails.cpp. Covers "General", "Fields", "Properties" (permissions/
 * indexes/sort-list/archive/audit/result-list - see propertiesInfo()) and "Workflow" (via
 * {@link WorkflowReferenceIndex} - form-level "executes on" only, not field-level). Unlike the
 * C++, which decodes these from a raw AR_OPROP_* property list (ShowPermissionProperties etc. in
 * DocSchemaDetails.cpp - ~900 lines of tag-by-tag parsing), the Java API exposes all of this as
 * clean typed accessors directly on Form (getAssignedGroup/getIndexInfo/getSortInfo/getArchiveInfo/
 * getAuditInfo/getEntryListFieldInfo) - a genuine simplification, not a scope cut. Join/view-schema
 * field mapping is rendered per-field on FieldDetailPage instead of a dedicated "References" tab
 * here (see its javadoc); per-field DisplayProperties (position/font/color per VUI) are rendered on
 * each VUI's own page (see VuiDetailPage).
 */
public final class SchemaDetailPage {
    private final SchemaSource repo;
    private final AppConfig appConfig;
    private final WorkflowReferenceIndex workflowIndex;
    private final int serverOverlayMode;
    private final FieldReferenceIndex fieldRefs;
    private final arinside.scan.MissingFieldReferenceIndex missingFieldRefs;
    private final GlobalFieldIndex globalFields;
    private final JoinFieldIndex joinFields;
    private final SchemaTypeIndex schemaTypes;
    private final ImageReferenceIndex imageRefs;
    private final Set<String> knownUserNames;
    private final arinside.scan.SchemaReferenceIndex schemaRefs;
    private final arinside.scan.ContainerReferenceIndex containerRefs;
    private final arinside.scan.AppMembershipIndex appIndex;
    private final arinside.scan.RoleIndex roleIndex;
    private final Map<Integer, arinside.ar.GroupRecord> groupsById;
    private final arinside.scan.SchemaDbInfoIndex schemaDbInfo;

    public SchemaDetailPage(SchemaSource repo, AppConfig appConfig, WorkflowReferenceIndex workflowIndex, int serverOverlayMode,
                             FieldReferenceIndex fieldRefs, arinside.scan.MissingFieldReferenceIndex missingFieldRefs, GlobalFieldIndex globalFields, JoinFieldIndex joinFields, SchemaTypeIndex schemaTypes,
                             ImageReferenceIndex imageRefs, Set<String> knownUserNames, arinside.scan.SchemaReferenceIndex schemaRefs,
                             arinside.scan.ContainerReferenceIndex containerRefs, arinside.scan.AppMembershipIndex appIndex,
                             arinside.scan.RoleIndex roleIndex, Map<Integer, arinside.ar.GroupRecord> groupsById,
                             arinside.scan.SchemaDbInfoIndex schemaDbInfo) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.workflowIndex = workflowIndex;
        this.serverOverlayMode = serverOverlayMode;
        this.fieldRefs = fieldRefs;
        this.missingFieldRefs = missingFieldRefs;
        this.globalFields = globalFields;
        this.joinFields = joinFields;
        this.schemaTypes = schemaTypes;
        this.imageRefs = imageRefs;
        this.knownUserNames = knownUserNames;
        this.schemaRefs = schemaRefs;
        this.containerRefs = containerRefs;
        this.appIndex = appIndex;
        this.roleIndex = roleIndex;
        this.groupsById = groupsById;
        this.schemaDbInfo = schemaDbInfo;
    }

    /** Everything render() needs that comes from AR System - fetched once, up front, so the render/write half can run independently of the fetch (see ReadPool/WritePool). */
    public record SchemaData(String formName, Form form, boolean isOverlaid, List<Field> fields, List<View> vuis) {}

    /** The fetch half - safe to run on a pooled read connection. */
    public SchemaData fetch(SchemaSource repo, String formName) throws ARException {
        Form form = repo.getForm(formName);
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(form.getProperties(), serverOverlayMode);
        List<Field> fields = repo.getFields(formName);
        fields.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        // Read from GlobalFieldIndex's own pass (built up front, before forms render) instead of a
        // redundant re-fetch - see GlobalFieldIndex.viewsByForm's javadoc.
        List<View> vuis = globalFields.views(formName);
        return new SchemaData(formName, form, isOverlaid, fields, vuis);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String formName) throws ARException {
        render(fetch(repo, formName));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(SchemaData data) throws ARException {
        String formName = data.formName();
        Form form = data.form();
        boolean isOverlaid = data.isOverlaid();
        List<Field> fields = data.fields();

        PagePath page = Naming.schemaDetail(formName, isOverlaid);

        WebPage webPage = new WebPage(page.fileName(), formName, page.rootLevel(), appConfig);

        StringBuilder head = new StringBuilder();
        head.append(URLLink.to("Forms", Naming.schemaOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml());
        head.append(" &gt; ");
        head.append(new ImageTag(AREnumLabels.schemaImage(form.getFormType()), page.rootLevel()).toHtml());
        head.append(WebUtil.objName(formName));
        webPage.addContentHead(head.toString());

        boolean isSpecialForm = form.getFormType() == Constants.AR_SCHEMA_JOIN
            || form.getFormType() == Constants.AR_SCHEMA_VIEW
            || form.getFormType() == Constants.AR_SCHEMA_VENDOR;
        Table fieldsTable = isSpecialForm
            ? allFieldsSpecialTable(formName, isOverlaid, form, fields, page.rootLevel())
            : allFieldsTable(formName, isOverlaid, fields, page.rootLevel());
        String fieldsTab = fieldsFilterHeader(formName, isOverlaid, form, fields, isSpecialForm, page.rootLevel()) + fieldsTable.toXHtml();

        Map<Integer, String> fieldNames = new HashMap<>();
        for (Field f : fields) fieldNames.put(f.getFieldID(), f.getName());

        // Matches DocSchemaDetails.cpp's real tab layout (5 tabs: General/Fields/Views/Workflow/
        // References) - ShowGeneralInfo()'s Name/Type/Default View table, an <hr/>, then
        // ShowProperties()'s whole Basic..Change-History accordion all live under tab-1 "General"
        // together; there is no separate "Properties" tab in the real tool.
        String generalTab = generalInfo(formName, isOverlaid, form, data.vuis(), page.rootLevel())
            + "<hr/>\n" + propertiesInfo(formName, form, isOverlaid, fields, fieldNames, page.rootLevel()) + "<hr/>\n";

        TabControl tabs = new TabControl();
        tabs.addTab("General", generalTab);
        tabs.addTab("Fields", fieldsTab);
        tabs.addTab("Views", vuiListTable(formName, isOverlaid, data.vuis(), page.rootLevel()));
        tabs.addTab("Workflow", workflowRefs(formName, page.rootLevel()));
        tabs.addTab("References", referencesInfo(formName, page.rootLevel()));
        webPage.addContent(tabs.toXHtml());

        // Real jQuery UI tabs()/accordion() init for #MainObjectTabCtrl/#schemaProperties/
        // #schemaPermissions lives in schema_page.js - without referencing it (plus its 3 sibling
        // scripts) the tab markup above renders as a flat stacked list, never becoming real tabs.
        webPage.addScriptReference("img/object_list.js");
        webPage.addScriptReference("img/schema_page.js");
        webPage.addScriptReference("img/jquery.timers.js");
        webPage.addScriptReference("img/jquery.address.min.js");

        webPage.saveInFolder(page.path());

        // Java port of DocSchemaDetails::AllFieldsCsv - same table, exported alongside the page.
        new CsvPage(Naming.schemaFieldsCsv(formName, isOverlaid).fileName(), appConfig)
            .saveInFolder(page.path(), fieldsTable.toCsv());

        FieldDetailPage fieldDetail = new FieldDetailPage(appConfig, fieldRefs, globalFields, joinFields);
        for (Field field : fields) fieldDetail.render(formName, isOverlaid, form, field, fields, data.vuis());

        renderVuis(formName, isOverlaid, fields, data.vuis());
    }

    /** Java port of doc/DocVUIDetails.cpp's list pass - one vui_*.htm page per VUI, with the fields shown on it (reverse-indexed from each field's DisplayInstanceMap, keyed by VUI ID). */
    private void renderVuis(String formName, boolean isOverlaid, List<Field> fields, List<View> vuis) {
        if (vuis.isEmpty()) return;

        Map<Integer, List<Field>> fieldsByVui = new HashMap<>();
        for (Field field : fields) {
            if (field.getDisplayInstance() == null) continue;
            for (Integer vuiId : field.getDisplayInstance().keySet()) {
                fieldsByVui.computeIfAbsent(vuiId, k -> new ArrayList<>()).add(field);
            }
        }

        VuiDetailPage vuiDetail = new VuiDetailPage(appConfig, imageRefs);
        for (View vui : vuis) {
            vuiDetail.render(formName, isOverlaid, vui, fieldsByVui.getOrDefault(vui.getVUIId(), List.of()));
        }
    }

    /** Java port of DocSchemaDetails.cpp's ShowVuiList() - VUI Name/Label/Web Alias/Type/Modified/By, one row per view. Label and Web Alias come from the VUI's own DisplayProperties (AR_DPROP_LABEL / AR_OPROP_VIEW_LABEL_WEB_ALIAS - CARVui::Label()/webAlias()), not its ObjectProperties. */
    private String vuiListTable(String formName, boolean isOverlaid, List<View> vuis, int rootLevel) {
        Table tbl = new Table("vuiList", "TblObjectList");
        tbl.addColumn(30, "Vui Name");
        tbl.addColumn(20, "Label");
        tbl.addColumn(10, "Web Alias");
        tbl.addColumn(10, "Type");
        tbl.addColumn(15, "Modified");
        tbl.addColumn(15, "By");
        for (View v : vuis) {
            String name = v.getName() == null ? "" : v.getName();
            com.bmc.arsys.api.ViewDisplayPropertyMap dprops = v.getDisplayProperties();
            String label = displayProp(dprops, Constants.AR_DPROP_LABEL);
            String webAlias = displayProp(dprops, Constants.AR_OPROP_VIEW_LABEL_WEB_ALIAS);
            String modified = v.getLastUpdateTime() == null ? "" : DateTimeFormat.toHtmlString(v.getLastUpdateTime().getValue());
            tbl.addRow(new TableRow().addCellList(
                URLLink.to(name, Naming.schemaVuiDetail(formName, isOverlaid, v.getVUIId()), ImageTag.Id.NoImage, rootLevel).toHtml(),
                WebUtil.validate(label), WebUtil.validate(webAlias), AREnumLabels.vuiType(v.getVUIType()),
                modified, userLink(v.getLastChangedBy(), rootLevel)));
        }
        if (!vuis.isEmpty()) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    private String displayProp(com.bmc.arsys.api.ViewDisplayPropertyMap props, int propertyId) {
        if (props == null) return "";
        com.bmc.arsys.api.Value v = props.get(propertyId);
        if (v == null || v.getValue() == null) return "";
        return String.valueOf(v.getValue());
    }

    private String userLink(String userName, int rootLevel) {
        if (userName == null || userName.isEmpty()) return "";
        if (!knownUserNames.contains(userName)) return WebUtil.validate(userName);
        return URLLink.to(userName, Naming.userDetail(userName), ImageTag.Id.User, rootLevel).toHtml();
    }

    /**
     * Java port of DocSchemaDetails.cpp's GenerateReferencesTable() - the "References" tab. All
     * sub-tables are real, including AlWindowOpenReferences() (the AL "Open Window Action" row,
     * REFM_OPENWINDOW_FORM, see openWindowRefsCell()) - see SchemaReferenceIndex's javadoc for
     * the history of "writing"/"deleting"/"executing services" having been wrongly believed dead -
     * a scan/-only grep missed that doc/ also populates schema.AddReference() as AL/Filter/
     * Escalation pages render.
     */
    private String referencesInfo(String formName, int rootLevel) {
        StringBuilder sb = new StringBuilder();

        sb.append(workflowReferenceSubTable("Workflow reading data from this form", schemaRefs.setFieldsReaders(formName), "Set Fields", rootLevel));
        sb.append(workflowReferenceSubTable("Workflow writing data to this form", schemaRefs.pushFieldTargets(formName), "Push Fields", rootLevel));
        sb.append(workflowReferenceSubTable("Workflow deleting data on this form", schemaRefs.deleteEntryCallers(formName), "Delete Entry", rootLevel));
        sb.append(workflowReferenceSubTable("Workflow executing services on this form", schemaRefs.serviceCallers(formName), "Service", rootLevel));

        Table tbl = new Table("schemaReferences", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");

        tbl.addRow(new TableRow().addCellList("Container References", containerRefsCell(formName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("Table Fields datasource form", tableFieldRefsCell(formName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("Active Link \"Open Window Action\"", openWindowRefsCell(formName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("Join Form References", joinRefsCell(formName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("Search Menu References", searchMenuRefsCell(formName, rootLevel)));

        sb.append(tbl.toXHtml());
        return sb.toString();
    }

    /**
     * rowDescription is a short, fixed label per sub-table (e.g. "Push Fields") standing in for the
     * C++'s much more detailed per-row CRefItem::GetDescription() ("Target in 'Push Fields'
     * If-Action 3") - this port doesn't carry the If/Else-branch/action-index provenance through
     * SchemaReferenceIndex.Caller, matching the level of detail this table already accepted for the
     * pre-existing "Set Fields" row before this method had real data for the other three.
     */
    private String workflowReferenceSubTable(String description, List<arinside.scan.SchemaReferenceIndex.Caller> callers, String rowDescription, int rootLevel) {
        Table tbl = new Table("schemaWfRef" + description.hashCode(), "TblObjectList");
        tbl.description = description;
        tbl.addColumn(10, "Type");
        tbl.addColumn(45, "Server object");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(40, "Description");
        for (arinside.scan.SchemaReferenceIndex.Caller c : callers) {
            tbl.addRow(new TableRow().addCellList(c.typeLabel(), callerLink(c, rootLevel), AREnumLabels.objectEnable(c.enabled()), rowDescription));
        }
        return tbl.toXHtml();
    }

    private String callerLink(arinside.scan.SchemaReferenceIndex.Caller c, int rootLevel) {
        String link = switch (c.typeLabel()) {
            case "Active Link" -> URLLink.to(c.objectName(), Naming.activeLinkDetail(c.objectName(), false), ImageTag.Id.ActiveLink, rootLevel).toHtml();
            case "Filter" -> URLLink.to(c.objectName(), Naming.filterDetail(c.objectName(), false), ImageTag.Id.Filter, rootLevel).toHtml();
            case "Escalation" -> URLLink.to(c.objectName(), Naming.escalationDetail(c.objectName(), false), ImageTag.Id.Escalation, rootLevel).toHtml();
            default -> WebUtil.validate(c.objectName());
        };
        return link + WorkflowRefLabel.orderSuffix(c.typeLabel(), c.order());
    }

    private String containerRefsCell(String formName, int rootLevel) {
        List<arinside.scan.ContainerReferenceIndex.ContainerRef> refs = containerRefs.schemaPackingLists(formName);
        if (refs.isEmpty()) return WebUtil.EMPTY_VALUE;
        StringBuilder sb = new StringBuilder();
        for (arinside.scan.ContainerReferenceIndex.ContainerRef ref : refs) {
            sb.append(URLLink.to(ref.name(), Naming.containerDetail(Constants.ARCON_PACK, ref.name(), false), ImageTag.Id.PackingList, rootLevel).toHtml()).append("<br/>\n");
        }
        return sb.toString();
    }

    private String tableFieldRefsCell(String formName, int rootLevel) {
        List<GlobalFieldIndex.TableFieldRef> refs = globalFields.tableFieldSources(formName);
        if (refs.isEmpty()) return WebUtil.EMPTY_VALUE;
        StringBuilder sb = new StringBuilder();
        for (GlobalFieldIndex.TableFieldRef ref : refs) {
            boolean isOverlaid = globalFields.isOverlaid(ref.ownerForm());
            String fieldName = globalFields.fieldName(ref.ownerForm(), ref.fieldId());
            sb.append("Table: ").append(URLLink.to(fieldName != null ? fieldName : String.valueOf(ref.fieldId()), Naming.schemaFieldDetail(ref.ownerForm(), isOverlaid, ref.fieldId()), ImageTag.Id.Document, rootLevel).toHtml())
                .append(" in form: ").append(URLLink.to(ref.ownerForm(), Naming.schemaDetail(ref.ownerForm(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml()).append("<br/>\n");
        }
        return sb.toString();
    }

    private String joinRefsCell(String formName, int rootLevel) {
        List<arinside.scan.SchemaReferenceIndex.Caller> refs = schemaRefs.joinReferences(formName);
        if (refs.isEmpty()) return WebUtil.EMPTY_VALUE;
        StringBuilder sb = new StringBuilder();
        for (arinside.scan.SchemaReferenceIndex.Caller c : refs) {
            boolean isOverlaid = globalFields.isOverlaid(c.objectName());
            sb.append(c.typeLabel()).append(": ").append(URLLink.to(c.objectName(), Naming.schemaDetail(c.objectName(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml()).append("<br/>\n");
        }
        return sb.toString();
    }

    /** Java port of DocSchemaDetails.cpp's AlWindowOpenReferences() - every Active Link whose OpenWindowAction targets this form, matching REFM_OPENWINDOW_FORM. The real C++ renders these via CAlTable (the AL overview table's own Name/Order/etc. columns); this port reuses the same simple link-list rendering as the other single-cell reference rows here instead. */
    private String openWindowRefsCell(String formName, int rootLevel) {
        List<arinside.scan.SchemaReferenceIndex.Caller> refs = schemaRefs.openWindowTargets(formName);
        if (refs.isEmpty()) return WebUtil.EMPTY_VALUE;
        StringBuilder sb = new StringBuilder();
        for (arinside.scan.SchemaReferenceIndex.Caller c : refs) {
            sb.append(callerLink(c, rootLevel)).append("<br/>\n");
        }
        return sb.toString();
    }

    private String searchMenuRefsCell(String formName, int rootLevel) {
        List<String> menus = schemaRefs.queryMenus(formName);
        if (menus.isEmpty()) return WebUtil.EMPTY_VALUE;
        StringBuilder sb = new StringBuilder();
        for (String menuName : menus) {
            sb.append(URLLink.to(menuName, Naming.menuDetail(menuName, false), ImageTag.Id.Menu, rootLevel).toHtml()).append("<br/>\n");
        }
        return sb.toString();
    }

    /**
     * Java port of DocSchemaDetails.cpp's WorkflowDoc() - the "Workflow" tab's instant-filter list
     * of every Active Link/Filter/Escalation/AL-Guide/Filter-Guide/Webservice attached to this form.
     * Previously a bare static 2-column Type/Name table with no filtering at all; schema_page.js's
     * initWorkflowList()/FilterableTable mechanism (byte-identical to the C++'s own copy) was
     * already shipping unused, exactly like the Fields-tab filter fix earlier this session - it just
     * needed the "var referenceList = [...]" JSON blob, the type-checkbox filter control, and a
     * matching empty table shell (real rows are built entirely client-side from the JSON, on tab
     * select - confirmed by checking the real C++ output: the server-rendered table is always just
     * the empty "Table contains no data" placeholder, matching this port's own Table class's default
     * empty-table behavior with no extra code needed).
     */
    private String workflowRefs(String formName, int rootLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"text/javascript\">var schemaWFLInit = false;\n");
        sb.append(workflowJson(formName, rootLevel));
        sb.append("var rootLevel = ").append(rootLevel).append("\n</script>\n");
        sb.append(SCHEMA_REFERENCE_FILTER_CONTROL);

        Table tbl = new Table("referenceList", "TblObjectList");
        tbl.addColumn(35, "Server object");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(5, "Order");
        tbl.addColumn(15, "Execute On");
        tbl.addColumn(5, "If");
        tbl.addColumn(5, "Else");
        tbl.addColumn(15, "Changed");
        tbl.addColumn(15, "By");
        sb.append(tbl.toXHtml());
        return sb.toString();
    }

    /**
     * Java port of DocSchemaDetails.cpp's AddJsonRow(CARActiveLink/CARFilter/CAREscalation/
     * CARContainer, ...) - row shape [objType, name, enabled-or-containerType, order, executeOn,
     * ifCount, elseCount, modified, changedBy, link], objType = the real C++'s
     * GetServerObjectTypeXML()-AR_STRUCT_XML_OFFSET values (6=Active Link/5=Filter/9=Escalation/
     * 12=Container - confirmed against both schema_page.js's own hardcoded checks and the real C++
     * output's actual JSON). Escalation's Order slot is the empty string "" (it has no
     * Order concept), matching the C++ exactly.
     */
    private String workflowJson(String formName, int rootLevel) {
        StringBuilder json = new StringBuilder("var referenceList = [");
        int count = 0;
        for (WorkflowReferenceIndex.Ref ref : workflowIndex.forForm(formName)) {
            Integer objType = switch (ref.typeLabel()) {
                case "Active Link" -> 6;
                case "Filter" -> 5;
                case "Escalation" -> 9;
                default -> null;
            };
            if (objType == null) continue;
            if (count > 0) json.append(',');
            String order = ref.order() == null ? "\"\"" : String.valueOf(ref.order());
            json.append('[').append(objType).append(",\"").append(WebUtil.jsString(ref.name())).append("\",")
                .append(ref.enabled()).append(',').append(order).append(',').append(ref.executeOn()).append(',')
                .append(ref.ifCount()).append(',').append(ref.elseCount()).append(",\"")
                .append(WebUtil.jsString(ref.modified() == null ? "" : DateTimeFormat.toPlainString(ref.modified().getValue()))).append("\",\"")
                .append(WebUtil.jsString(ref.changedBy())).append("\",\"")
                .append(WebUtil.jsString(URLLink.relativeUrl(rootLevel, ref.link()))).append("\"]");
            count++;
        }
        for (com.bmc.arsys.api.Container c : containerRefs.schemaGuidesAndWebservices(formName)) {
            if (count > 0) json.append(',');
            // isOverlaid=false is a pre-existing simplification for container links elsewhere in
            // this file too (see FilterDetailPage.errorHandlerCell's identical comment) - not new.
            PagePath link = Naming.containerDetail(c.getType(), c.getName(), false);
            json.append("[12,\"").append(WebUtil.jsString(c.getName())).append("\",").append(c.getType())
                .append(",\"\",\"\",\"\",\"\",\"")
                .append(WebUtil.jsString(c.getLastUpdateTime() == null ? "" : DateTimeFormat.toPlainString(c.getLastUpdateTime().getValue()))).append("\",\"")
                .append(WebUtil.jsString(c.getLastChangedBy())).append("\",\"")
                .append(WebUtil.jsString(URLLink.relativeUrl(rootLevel, link))).append("\"]");
            count++;
        }
        json.append("];\n");
        return json.toString();
    }

    /** Java port of CDocMain::CreateSchemaReferenceFilterControl() - static markup, byte-matched against the real C++ output (including its "search by name" placeholder, distinct wording from the Fields-tab filter's "search by name or id"). */
    private static final String SCHEMA_REFERENCE_FILTER_CONTROL =
        "<div><div id='search'><span class='clearable'><label for='workflowFilter'>Filter: </label>"
        + "<input id='workflowFilter' class='data_field' type='text' placeholder='search by name'/></span></div>"
        + "<div class='restrictions' style='max-width: 1230px;'><div id='clearButton'><button id='typeFilterNone'>Clear All</button></div>"
        + "<div id='referenceMultiFilter'><div class='left ptop'>Restrict results to: </div><div class='filterOpts'>"
        + "<div class='checkbox-sref'><input id='typeFilterActiveLink' type='checkbox' value='1'/><label for='typeFilterActiveLink'>&nbsp;ActiveLink</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterFilter' type='checkbox' value='2'/><label for='typeFilterFilter'>&nbsp;Filter</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterEscalation' type='checkbox' value='3'/><label for='typeFilterEscalation'>&nbsp;Escalation</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterALGuide' type='checkbox' value='4'/><label for='typeFilterALGuide'>&nbsp;ActiveLinkGuide</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterFilterGuide' type='checkbox' value='5'/><label for='typeFilterFilterGuide'>&nbsp;FilterGuide</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterApplication' type='checkbox' value='6'/><label for='typeFilterApplication'>&nbsp;Application</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterPackList' type='checkbox' value='7'/><label for='typeFilterPackList'>&nbsp;PackingList</label></div>"
        + "<div class='checkbox-sref'><input id='typeFilterWebservice' type='checkbox' value='8'/><label for='typeFilterWebservice'>&nbsp;Webservice</label></div>"
        + "</div></div></div></div>";

    /**
     * Java port of DocSchemaDetails.cpp's ShowProperties() - Basic/EntryPoints/ResultList/SortList/
     * Archive/Audit/Indexes/FullTextSearch/Permissions/ChangeHistory, all wrapped in the single
     * &lt;div id='schemaProperties'&gt; jQuery UI accordion schema_page.js initializes (see the
     * addScriptReference calls in render()). ShowPermissionProperties/ShowIndexProperties/
     * ShowResultListProperties/ShowSortListProperties/ShowArchiveProperties/ShowAuditProperties read
     * typed Form accessors instead of a raw property list - see class javadoc for why.
     */
    private String propertiesInfo(String formName, Form form, boolean isOverlaid, List<Field> fields, Map<Integer, String> fieldNames, int rootLevel) {
        StringBuilder sb = new StringBuilder("<div id='schemaProperties'>\n");

        sb.append(basicPropertiesInfo(form.getProperties(), rootLevel));
        sb.append(entryPointsInfo(form.getProperties(), rootLevel));

        Map<Integer, Field> fieldsById = new HashMap<>();
        for (Field f : fields) fieldsById.put(f.getFieldID(), f);

        List<EntryListFieldInfo> resultList = form.getEntryListFieldInfo();
        if (resultList != null && !resultList.isEmpty()) {
            Table tbl = new Table("schemaResultList", "TblObjectList");
            tbl.addColumn(25, "Field");
            tbl.addColumn(10, "Field Id");
            tbl.addColumn(10, "Datatype");
            tbl.addColumn(10, "Width");
            tbl.addColumn(15, "Separator");
            tbl.addColumn(15, "Modified");
            tbl.addColumn(15, "By");
            for (EntryListFieldInfo f : resultList) {
                Field field = fieldsById.get(f.getFieldId());
                if (field == null) continue;
                tbl.addRow(new TableRow().addCellList(
                    URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml(),
                    Integer.toString(field.getFieldID()),
                    AREnumLabels.dataType(field.getDataType()),
                    Integer.toString(f.getColumnWidth()),
                    WebUtil.validate(nullToEmpty(f.getSeparator())),
                    field.getLastUpdateTime() == null ? "" : DateTimeFormat.toHtmlString(field.getLastUpdateTime().getValue()),
                    userLink(field.getLastChangedBy(), rootLevel)));
                addSchemaFieldReference(formName, isOverlaid, field.getFieldID(),
                    "Field in " + URLLink.to("ResultList", Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.NoImage, rootLevel).toHtml());
            }
            sb.append(accordionItem("Result List Fields", rootLevel, tbl));
        }

        List<SortInfo> sortList = form.getSortInfo();
        if (sortList != null && !sortList.isEmpty()) {
            Table tbl = new Table("schemaSortList", "TblObjectList");
            tbl.addColumn(10, "Sort Order");
            tbl.addColumn(30, "Field");
            tbl.addColumn(10, "Field Id");
            tbl.addColumn(10, "Datatype");
            tbl.addColumn(20, "Modified");
            tbl.addColumn(20, "By");
            for (SortInfo s : sortList) {
                Field field = fieldsById.get(s.getFieldID());
                if (field == null) continue;
                String order = s.getSortOrder() == Constants.AR_SORT_DESCENDING ? "Descending" : "Ascending";
                tbl.addRow(new TableRow().addCellList(
                    order,
                    URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml(),
                    Integer.toString(field.getFieldID()),
                    AREnumLabels.dataType(field.getDataType()),
                    field.getLastUpdateTime() == null ? "" : DateTimeFormat.toHtmlString(field.getLastUpdateTime().getValue()),
                    userLink(field.getLastChangedBy(), rootLevel)));
                addSchemaFieldReference(formName, isOverlaid, field.getFieldID(),
                    "Field in " + URLLink.to("SortList", Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.NoImage, rootLevel).toHtml());
            }
            sb.append(accordionItem("Sort", rootLevel, tbl));
        }

        // Order from here down matches DocSchemaDetails.cpp's ShowProperties() call sequence
        // exactly (Archive, then Audit, then Indexes, then Full Text Search) - an earlier version
        // of this method rendered Indexes before Archive/Audit, a divergence from the real
        // accordion order that went unnoticed since nothing before today's fix actually turned
        // this content into a real jQuery UI accordion widget.
        ArchiveInfo archive = form.getArchiveInfo();
        if (archive != null && form.getFormType() != Constants.AR_SCHEMA_DIALOG) {
            sb.append(archiveInfo(formName, isOverlaid, archive, fieldNames, rootLevel));
        }

        AuditInfo audit = form.getAuditInfo();
        if (audit != null && form.getFormType() != Constants.AR_SCHEMA_DIALOG) {
            sb.append(auditInfo(formName, isOverlaid, audit, rootLevel));
        }

        List<IndexInfo> indexes = form.getIndexInfo();
        if (indexes != null && !indexes.isEmpty()) {
            StringBuilder indexesHtml = new StringBuilder();
            for (IndexInfo ix : indexes) {
                indexesHtml.append(indexTable(formName, isOverlaid, ix, fieldsById, rootLevel).toXHtml());
            }
            sb.append("<h2>").append(new ImageTag(ImageTag.Id.Document, rootLevel).toHtml()).append("Indexes</h2>\n<div>\n")
                .append(indexesHtml).append("</div>\n");
        }

        if (form.getFormType() != Constants.AR_SCHEMA_DIALOG) {
            sb.append(fullTextSearchInfo(form, formName, rootLevel));
        }

        sb.append(permissionsInfo(formName, isOverlaid, form, fields, rootLevel));

        sb.append(objectPropertiesInfo(form.getProperties(), rootLevel));

        sb.append(ServerObjectHistoryWidget.render(form, knownUserNames, rootLevel));

        sb.append("</div>\n");
        return sb.toString();
    }

    /**
     * Java port of the "&lt;h2&gt;&lt;icon/&gt;Title&lt;/h2&gt;&lt;div&gt;{table}&lt;/div&gt;" wrapping every
     * individual Show*Properties() call in DocSchemaDetails.cpp builds by hand around its own bare
     * CTable output (confirmed by reading ShowBasicProperties/ShowEntryPointProperties/etc.
     * directly - CTable's own description field, the mechanism {@link Table#toXHtml()} normally
     * uses for a plain heading, is deliberately left unset for every one of these accordion items;
     * the div wrapper is what actually makes each one a distinct, independently-collapsible jQuery
     * UI accordion panel once schema_page.js's accordion() call runs). tbl's own description must
     * be left unset (empty) by the caller - this method supplies the heading instead.
     */
    private String accordionItem(String title, int rootLevel, Table tbl) {
        return "<h2>" + new ImageTag(ImageTag.Id.Document, rootLevel).toHtml() + title + "</h2>\n<div>\n" + tbl.toXHtml() + "</div>\n";
    }

    /**
     * Java port of DocSchemaDetails.cpp's trailing propIdx.UnusedPropertiesToHTML(strm, rootLevel)
     * call (ShowProperties(), right after ShowPermissionProperties and before ShowChangeHistory) -
     * previously entirely missing, so a form's Object Properties were never shown at all (found via
     * user report). The real C++ tracks a "claimed" bit per property as each Show*Properties()
     * method reads one via GetAndUseValue, then dumps whatever's left unclaimed at the end; this
     * port has no equivalent claimed-bit bookkeeping (each typed section here reads Form's own typed
     * accessors, or a raw property directly via intProp/PropertyHelper), so the same result is
     * reached the same way {@link ObjectPropertiesTable} already does for Menu/AL/Filter/VUI pages -
     * an explicit exclude-set naming every property id one of THIS page's own typed sections already
     * shows (enumerated by reading every GetAndUseValue(AR_OPROP_...) call between ShowBasicProperties
     * and ShowFTSMTSProperties in the real source - confirmed complete). One extra id
     * beyond the C++'s own exclude set: AR_OPROP_DRILL_DOWN_IN_WEB_REPORTS - the real C++ never
     * claims it due to a confirmed copy-paste bug (basicPropertiesInfo's javadoc has the full story),
     * so the real tool actually shows it twice; this port shows it once, correctly, and excludes it
     * here on purpose rather than reproducing that redundancy.
     */
    private String objectPropertiesInfo(com.bmc.arsys.api.ObjectPropertyMap props, int rootLevel) {
        Set<Integer> exclude = Set.of(
            Constants.AR_OPROP_NEXT_ID_BLOCK_SIZE, Constants.AR_OPROP_CACHE_DISP_PROP,
            Constants.AR_OPROP_CORE_FIELDS_OPTION_MASK, Constants.AR_OPROP_FORM_ALLOW_DELETE,
            Constants.AR_OPROP_DRILL_DOWN_IN_WEB_REPORTS, Constants.AR_OPROP_LOCALIZE_FORM_VIEWS,
            Constants.AR_OPROP_LOCALIZE_FORM_DATA, Constants.AR_OPROP_FORM_TAG_NAME,
            // AR_SMOPROP_* (a different property-id namespace than AR_OPROP_*, easy to miss on a
            // AR_OPROP_-only grep - found and fixed the same day this whole section was added, via
            // re-checking ShowEntryPointProperties specifically) - entryPointsInfo() already shows
            // both of these; omitting them here duplicated them into this leftover dump too.
            Constants.AR_SMOPROP_ENTRYPOINT_DEFAULT_NEW_ORDER, Constants.AR_SMOPROP_ENTRYPOINT_DEFAULT_SEARCH_ORDER,
            Constants.AR_OPROP_MFS_OPTION_MASK, Constants.AR_OPROP_MFS_WEIGHTED_RELEVANCY_FIELDS,
            Constants.AR_OPROP_FT_SCAN_TIME_MONTH_MASK, Constants.AR_OPROP_FT_SCAN_TIME_WEEKDAY_MASK,
            Constants.AR_OPROP_FT_SCAN_TIME_HOUR_MASK, Constants.AR_OPROP_FT_SCAN_TIME_MINUTE,
            Constants.AR_OPROP_FT_SCAN_TIME_INTERVAL);
        return ObjectPropertiesTable.render(props, exclude);
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowPermissionProperties() - Group/Subadministrator/Field
     * Permissions, nested inside &lt;div id='schemaPermissions'&gt; as a second, inner accordion
     * (schema_page.js initializes both #schemaProperties and #schemaPermissions). Always rendered
     * (matching the C++'s unconditional call, not gated on emptiness - "Table contains no data" is
     * itself the useful signal for a form with no configured permissions). Field Permissions is a
     * genuinely new section here (didn't exist before this fix): one row per field, each with its
     * own group/permission list nested inside a header-less "TblHidden" sub-table, matching
     * DocSchemaDetails.cpp:940-999 exactly - Field.getAssignedGroup() is the same accessor already
     * used for the form-level Group Permissions table above, just called per-field instead.
     */
    private String permissionsInfo(String formName, boolean isOverlaid, Form form, List<Field> fields, int rootLevel) {
        String appRefName = appIndex == null ? null : appIndex.formApp(formName);

        // Java port of ValidateGroup(appRefName, groupId) (ARInside.cpp:1158-1175) - unlike
        // LinkToGroup below, this is a ROW-INCLUSION filter, not a link-vs-text fallback: a positive
        // group ID is NEVER skipped here (ValidateGroup always returns true for those, regardless of
        // whether the group actually exists - that existence check only affects the link/text choice
        // inside groupRef()), only a negative (role) ID whose role doesn't exist for this schema's
        // own app gets its row omitted entirely.
        Table groupTbl = new Table("permissionList", "TblObjectList");
        groupTbl.description = "Group Permissions";
        groupTbl.addColumn(60, "Group Name");
        groupTbl.addColumn(20, "Group Id");
        groupTbl.addColumn(20, "Permission");
        List<PermissionInfo> perms = form.getAssignedGroup();
        int groupRows = 0;
        if (perms != null) {
            for (PermissionInfo p : perms) {
                int gid = p.getGroupID();
                if (gid < 0 && roleIndex.find(gid, appRefName) == null) continue;
                String icon = new ImageTag(p.getPermissionValue() == Constants.AR_PERMISSIONS_HIDDEN ? ImageTag.Id.Hidden : ImageTag.Id.Visible, rootLevel).toHtml();
                groupTbl.addRow(new TableRow().addCellList(groupRef(gid, appRefName, rootLevel), Integer.toString(gid), icon + GroupDetailPage.objectPermissionLabel(p.getPermissionValue())));
                groupRows++;
            }
        }
        if (groupRows > 0) groupTbl.removeEmptyMessageRow();

        // Subadministrator/Field Permissions have no row-inclusion filter at all in the C++ (no
        // ValidateGroup call in either ShowPermissionProperties block) - every row shows, with
        // groupRef()'s own link-vs-plain-text fallback (matching LinkToGroup) as the only variation.
        Table subadminTbl = new Table("subadminPerms", "TblObjectList");
        subadminTbl.description = "Subadministrator Permissions";
        subadminTbl.addColumn(90, "Group Name");
        subadminTbl.addColumn(10, "Group Id");
        List<Integer> admin = form.getAdminGrpList();
        if (admin != null) {
            for (Integer gid : admin) subadminTbl.addRow(new TableRow().addCellList(groupRef(gid, appRefName, rootLevel), Integer.toString(gid)));
        }
        if (admin != null && !admin.isEmpty()) subadminTbl.removeEmptyMessageRow();

        Table fieldPermTbl = new Table("fieldPerms", "TblObjectList");
        fieldPermTbl.description = "Field Permissions";
        fieldPermTbl.addColumn(40, "Field Name");
        fieldPermTbl.addColumn(10, "Field ID");
        fieldPermTbl.addColumn(10, "Datatype");
        fieldPermTbl.addColumn(40, "Permissions");
        for (Field field : fields) {
            fieldPermTbl.addRow(new TableRow().addCellList(
                URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml(),
                Integer.toString(field.getFieldID()),
                AREnumLabels.dataType(field.getDataType()),
                fieldPermissionCell(field.getAssignedGroup(), appRefName, rootLevel)));
        }
        if (!fields.isEmpty()) fieldPermTbl.removeEmptyMessageRow();

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(new ImageTag(ImageTag.Id.Document, rootLevel).toHtml()).append("Permissions</h2>\n");
        sb.append("<div id='schemaPermissions'>\n");
        sb.append(groupTbl.toXHtml());
        sb.append(subadminTbl.toXHtml());
        sb.append(fieldPermTbl.toXHtml());
        sb.append("</div>\n");
        return sb.toString();
    }

    private String fieldPermissionCell(List<PermissionInfo> fldPerms, String appRefName, int rootLevel) {
        if (fldPerms == null || fldPerms.isEmpty()) return "(null)";
        Table nested = new Table("PermissionFieldList", "TblHidden").disableHeader();
        nested.addColumn(50, "Group Name");
        nested.addColumn(50, "Permission");
        for (PermissionInfo p : fldPerms) {
            String icon = new ImageTag(p.getPermissionValue() == Constants.AR_PERMISSIONS_CHANGE ? ImageTag.Id.Edit : ImageTag.Id.Visible, rootLevel).toHtml();
            nested.addRow(new TableRow().addCellList(groupRef(p.getGroupID(), appRefName, rootLevel), icon + GroupDetailPage.fieldPermissionLabel(p.getPermissionValue())));
        }
        return nested.toXHtml();
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowBasicProperties() - Next Request ID Block, Display
     * Property Caching (VUI/Field caching overrides), Disable Status History, Allow Delete,
     * Localize Form Views/Data, and Form Tag Name. Server-version-gated in the C++ (7.1.0+ for
     * caching/status-history, 7.6.3+ for allow-delete/localization, 8.0.0+ for the tag name) - no
     * explicit gate needed here, matching this file's established pattern elsewhere (see
     * fullTextSearchInfo's javadoc): the Java API simply won't return these properties on an older
     * server, so checking for their presence is equivalent.
     *
     * <p>"Allow Drill Down in Web Reports" reads AR_OPROP_DRILL_DOWN_IN_WEB_REPORTS (60062, "boolean,
     * Chart drill down" per ar.h; compared against AR_ALLOW_DRILL_DOWN_IN_WEB_REPORTS), NOT
     * AR_OPROP_FORM_ALLOW_DELETE - unlike every other row here, this one deliberately does NOT
     * match DocSchemaDetails.cpp's real source (line 1611 there reads AR_OPROP_FORM_ALLOW_DELETE a
     * second time, an unambiguous copy/paste bug: ar.h defines AR_OPROP_DRILL_DOWN_IN_WEB_REPORTS
     * for exactly this purpose, and AREnum.cpp's own FieldPropertiesLabel table separately maps it
     * to the label "Drill Down in Web Reports" - real, unused ground truth sitting right in the
     * same codebase the buggy line lives in). Corrected here rather than ported as-is, per explicit
     * user instruction to make this one row actually correct instead of faithfully wrong.
     */
    private String basicPropertiesInfo(com.bmc.arsys.api.ObjectPropertyMap props, int rootLevel) {
        Table tbl = new Table("basicProps", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");

        Integer nextIdBlockSize = intProp(props, Constants.AR_OPROP_NEXT_ID_BLOCK_SIZE);
        boolean blockEnabled = nextIdBlockSize != null && nextIdBlockSize > 0;
        tbl.addRow(new TableRow().addCellList("Next Request ID Block", (blockEnabled ? "Enabled" : "Disabled") + "<br/>" + (blockEnabled ? "Size: " + nextIdBlockSize : "")));

        Integer cacheDisplayProps = intProp(props, Constants.AR_OPROP_CACHE_DISP_PROP);
        boolean cacheOverride = cacheDisplayProps != null && cacheDisplayProps >= 0;
        StringBuilder cache = new StringBuilder("Override Server Settings: ").append(cacheOverride ? "Enabled" : "Disabled");
        if (cacheOverride) {
            cache.append("<br/>").append((cacheDisplayProps & Constants.AR_CACHE_DPROP_VUI) != 0 ? "Yes" : "No").append(" - Disable VUI Display Property Caching");
            cache.append("<br/>").append((cacheDisplayProps & Constants.AR_CACHE_DPROP_FIELD) != 0 ? "Yes" : "No").append(" - Disable Field Display Property Caching");
        }
        tbl.addRow(new TableRow().addCellList("Display Property Caching", cache.toString()));

        Integer coreFieldsMask = intProp(props, Constants.AR_OPROP_CORE_FIELDS_OPTION_MASK);
        boolean disableStatusHistory = coreFieldsMask != null && (coreFieldsMask & Constants.AR_CORE_FIELDS_OPTION_DISABLE_STATUS_HISTORY) != 0;
        tbl.addRow(new TableRow().addCellList("Disable Status History", disableStatusHistory ? "Yes" : "No"));

        Integer allowDelete = intProp(props, Constants.AR_OPROP_FORM_ALLOW_DELETE);
        tbl.addRow(new TableRow().addCellList("Allow Delete", allowDelete != null && allowDelete == 1 ? "Yes" : "No"));

        Integer allowDrillDown = intProp(props, Constants.AR_OPROP_DRILL_DOWN_IN_WEB_REPORTS);
        tbl.addRow(new TableRow().addCellList("Allow Drill Down in Web Reports", allowDrillDown != null && allowDrillDown == Constants.AR_ALLOW_DRILL_DOWN_IN_WEB_REPORTS ? "Yes" : "No"));

        Integer localizeViews = intProp(props, Constants.AR_OPROP_LOCALIZE_FORM_VIEWS);
        int viewsValue = localizeViews != null ? localizeViews : Constants.AR_LOCALIZE_FORM_VIEWS_ALL;
        String viewsState = viewsValue == Constants.AR_LOCALIZE_FORM_VIEWS_ALL ? "All"
            : viewsValue == Constants.AR_LOCALIZE_FORM_VIEWS_ALIASES ? "Only for selection field aliases" : "Disabled";
        tbl.addRow(new TableRow().addCellList(ARPropertyLabels.label(Constants.AR_OPROP_LOCALIZE_FORM_VIEWS), viewsState));

        Integer localizeData = intProp(props, Constants.AR_OPROP_LOCALIZE_FORM_DATA);
        int dataValue = localizeData != null ? localizeData : Constants.AR_LOCALIZE_FORM_DATA_ALL;
        tbl.addRow(new TableRow().addCellList(ARPropertyLabels.label(Constants.AR_OPROP_LOCALIZE_FORM_DATA), dataValue == 1 ? "Yes" : "No"));

        String formTagName = arinside.ar.PropertyHelper.stringProperty(props, Constants.AR_OPROP_FORM_TAG_NAME);
        tbl.addRow(new TableRow().addCellList(ARPropertyLabels.label(Constants.AR_OPROP_FORM_TAG_NAME), WebUtil.validate(formTagName)));

        return accordionItem("Basic", rootLevel, tbl);
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowEntryPointProperties() - New/Search mode entry point
     * enablement + display order, read from AR_SMOPROP_ENTRYPOINT_DEFAULT_NEW_ORDER/
     * AR_SMOPROP_ENTRYPOINT_DEFAULT_SEARCH_ORDER (a negative/absent value means "not an entry
     * point", matching the real C++'s entryPointNewOrder/entryPointSearchOrder defaulting to -1).
     */
    private String entryPointsInfo(com.bmc.arsys.api.ObjectPropertyMap props, int rootLevel) {
        Integer newOrder = intProp(props, Constants.AR_SMOPROP_ENTRYPOINT_DEFAULT_NEW_ORDER);
        Integer searchOrder = intProp(props, Constants.AR_SMOPROP_ENTRYPOINT_DEFAULT_SEARCH_ORDER);

        Table tbl = new Table("entryPoints", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("New Mode", entryPointCell(newOrder)));
        tbl.addRow(new TableRow().addCellList("Search Mode", entryPointCell(searchOrder)));
        return accordionItem("EntryPoints", rootLevel, tbl);
    }

    private String entryPointCell(Integer order) {
        boolean enabled = order != null && order >= 0;
        StringBuilder sb = new StringBuilder(enabled ? "Enabled" : "Disabled");
        if (enabled) sb.append(" (Application List Display Order: ").append(order).append(")");
        return sb.toString();
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowFTSMTSProperties() - multi-form-search exclusion flag,
     * weighted relevancy field mapping (a custom "&lt;count&gt;;&lt;tag&gt;;&lt;fieldId&gt;;..."
     * packed string in AR_OPROP_MFS_WEIGHTED_RELEVANCY_FIELDS), and the FT-indexed-field update
     * schedule (either a calendar day-struct or a plain interval, matching Escalation's own
     * GetTimeCriteria() shape/wording - see EscalationDetailPage). No server-version gate (the real
     * C++ requires 7.6.3+): the Java API simply won't return these properties on an older server, so
     * checking for their presence is equivalent and doesn't need a separate live version check.
     */
    private String fullTextSearchInfo(Form form, String formName, int rootLevel) {
        com.bmc.arsys.api.ObjectPropertyMap props = form.getProperties();
        Table tbl = new Table("schemaFts", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");

        Integer mfsOptions = intProp(props, Constants.AR_OPROP_MFS_OPTION_MASK);
        boolean excluded = mfsOptions != null && (mfsOptions & Constants.AR_MULTI_FORM_SEARCH_OPTION_EXCLUDE) != 0;
        tbl.addRow(new TableRow().addCellList("Exclude from multi-form search", excluded ? "Yes" : "No"));

        tbl.addRow(new TableRow().addCellList("Weighted relevancy fields", weightedRelevancyFields(props, formName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("FT-indexed field updates", ftScanSchedule(props)));

        return accordionItem("Full Text Search", rootLevel, tbl);
    }

    /** Decodes AR_OPROP_MFS_WEIGHTED_RELEVANCY_FIELDS' packed "&lt;count&gt;;&lt;tag&gt;;&lt;fieldId&gt;;..." text into Title/Environment/Keywords field links, matching DocSchemaDetails.cpp's manual strchr/atoi parse exactly. */
    private String weightedRelevancyFields(com.bmc.arsys.api.ObjectPropertyMap props, String formName, int rootLevel) {
        com.bmc.arsys.api.Value v = props == null ? null : props.get(Constants.AR_OPROP_MFS_WEIGHTED_RELEVANCY_FIELDS);
        int[] fieldByTag = new int[4]; // index 0 unused, matching the C++'s 1-based tag numbering
        if (v != null && v.getValue() instanceof String s && !s.isEmpty()) {
            String[] parts = s.split(";");
            try {
                int count = Integer.parseInt(parts[0]);
                for (int i = 0; i < count; i++) {
                    int tagIdx = 1 + i * 2;
                    int tag = Integer.parseInt(parts[tagIdx]);
                    int fieldId = Integer.parseInt(parts[tagIdx + 1]);
                    if (tag > 0 && tag <= 3) fieldByTag[tag] = fieldId;
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // matches the C++'s own tolerant strchr/atoi parse - a malformed string just leaves fields unmapped
            }
        }

        String[] tagLabels = {null, "Title", "Environment", "Keywords"};
        boolean isOverlaid = globalFields.isOverlaid(formName);
        StringBuilder sb = new StringBuilder();
        for (int tag = 1; tag <= 3; tag++) {
            sb.append(tagLabels[tag]).append(": ");
            if (fieldByTag[tag] > 0) {
                String fieldName = globalFields.fieldName(formName, fieldByTag[tag]);
                sb.append(URLLink.to(fieldName != null ? fieldName : String.valueOf(fieldByTag[tag]), Naming.schemaFieldDetail(formName, isOverlaid, fieldByTag[tag]), ImageTag.Id.Document, rootLevel).toHtml());
            }
            sb.append("<br/>\n");
        }
        return sb.toString();
    }

    /** Java port of the ShowFTSMTSProperties day-struct/interval branch - identical wording to EscalationDetailPage's GetTimeCriteria() interval case. */
    private String ftScanSchedule(com.bmc.arsys.api.ObjectPropertyMap props) {
        Integer month = intProp(props, Constants.AR_OPROP_FT_SCAN_TIME_MONTH_MASK);
        Integer week = intProp(props, Constants.AR_OPROP_FT_SCAN_TIME_WEEKDAY_MASK);
        Integer hour = intProp(props, Constants.AR_OPROP_FT_SCAN_TIME_HOUR_MASK);
        Integer minute = intProp(props, Constants.AR_OPROP_FT_SCAN_TIME_MINUTE);
        Integer interval = intProp(props, Constants.AR_OPROP_FT_SCAN_TIME_INTERVAL);

        boolean hasDayStruct = (month != null && month != 0) || (week != null && week != 0) || (hour != null && hour != 0) || (minute != null && minute != 0);
        if (hasDayStruct) {
            com.bmc.arsys.api.EscalationTime cal = new com.bmc.arsys.api.EscalationTime(
                hour == null ? 0 : hour, week == null ? 0 : week, month == null ? 0 : month, minute == null ? 0 : minute);
            return ScheduleFormat.calendar(cal);
        }
        if (interval != null && interval != 0) {
            long days = interval / 86400L;
            long hours = (interval % 86400L) / 3600L;
            long minutes = (interval % 3600L) / 60L;
            return days + " Days " + hours + " Hours " + minutes + " Minutes";
        }
        return "No Time specified";
    }

    private Integer intProp(com.bmc.arsys.api.ObjectPropertyMap props, int propertyId) {
        if (props == null) return null;
        com.bmc.arsys.api.Value v = props.get(propertyId);
        if (v == null || v.getValue() == null) return null;
        return v.getIntValue();
    }

    private static String auditStyleLabel(int style) {
        if (style == Constants.AR_AUDIT_COPY) return "Copy";
        if (style == Constants.AR_AUDIT_LOG) return "Log";
        if (style == Constants.AR_AUDIT_LOG_SHADOW) return "Log Shadow";
        return "None";
    }

    private static String auditChangedFieldsLabel(int mask) {
        if (mask == Constants.AR_AUDIT_ONLY_CHNG_FLDS_YES) return "Yes";
        if (mask == Constants.AR_AUDIT_ONLY_CHNG_FLDS_NO) return "No";
        return "Default";
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowAuditProperties - previously gated on audit.isEnable()
     * (hiding the whole section when audit was off, where the real C++ always shows it - "Audit
     * Enabled: No" is itself the useful signal), missing the "Audit Enabled"/"Audit Only Changed
     * Fields" rows entirely, always labeling the target-form row "Audit Form" as plain unlinked text
     * regardless of style (the real C++ picks "Audited From Form"/"Audit Form"/"Audit Log Form"/
     * "Audited From Forms" depending on AR_AUDIT_NONE/COPY/LOG/LOG_SHADOW, and always links to the
     * target schema), and never showing "Qualification" when the audit qualifier was empty (the C++
     * always shows this row, "No qualification specified" included). LOG_SHADOW's "Audited From
     * Forms" is sourced from {@link arinside.scan.SchemaReferenceIndex#auditSources} - the reverse
     * side of every OTHER form's own audit-target reference, a new scan-phase pass (see that index's
     * javadoc) since REFM_SCHEMA_AUDIT_SOURCE was entirely unported before this fix.
     */
    private String auditInfo(String formName, boolean isOverlaid, AuditInfo audit, int rootLevel) {
        Table tbl = new Table("schemaAudit", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Audit Style", auditStyleLabel(audit.getAuditStyle())));
        tbl.addRow(new TableRow().addCellList("Audit Enabled", audit.isEnable() ? "Yes" : "No"));
        tbl.addRow(new TableRow().addCellList("Audit Only Changed Fields", auditChangedFieldsLabel(audit.getAuditMask())));

        if (audit.getAuditStyle() == Constants.AR_AUDIT_NONE) {
            if (audit.getAuditForm() != null && !audit.getAuditForm().isEmpty()) {
                tbl.addRow(new TableRow().addCellList("Audited From Form", schemaLink(audit.getAuditForm(), rootLevel)));
            }
        } else if (audit.getAuditStyle() == Constants.AR_AUDIT_COPY) {
            tbl.addRow(new TableRow().addCellList("Audit Form", schemaLink(audit.getAuditForm(), rootLevel)));
        } else if (audit.getAuditStyle() == Constants.AR_AUDIT_LOG) {
            tbl.addRow(new TableRow().addCellList("Audit Log Form", schemaLink(audit.getAuditForm(), rootLevel)));
        } else if (audit.getAuditStyle() == Constants.AR_AUDIT_LOG_SHADOW) {
            tbl.addRow(new TableRow().addCellList("Audited From Forms", auditSourceLinks(formName, rootLevel)));
        }

        QualifierInfo qual = audit.getQualifier();
        String qualText = qual != null && qual.getOperation() != QualifierInfo.AR_COND_OP_NONE
            ? qualification(formName, isOverlaid, qual, "Audit Qualification", rootLevel) : "No qualification specified";
        tbl.addRow(new TableRow().addCellList("Qualification", qualText));

        return accordionItem("Audit Settings", rootLevel, tbl);
    }

    private String auditSourceLinks(String formName, int rootLevel) {
        List<String> sources = schemaRefs.auditSources(formName);
        if (sources.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            if (i > 0) sb.append("<br/>");
            sb.append(schemaLink(sources.get(i), rootLevel));
        }
        return sb.toString();
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowArchiveProperties - archiveType is a real bitmask
     * (AR_ARCHIVE_FORM=1/DELETE=2/FILE_XML=4/FILE_ARX=8 are independent destination bits,
     * combinable with the AR_ARCHIVE_NO_ATTACHMENTS=32/NO_DIARY=64 flag bits - confirmed against
     * thirdparty/arapi/include/ar.h's #defines, all distinct powers of two), not a
     * mutually-exclusive enum - an earlier version of this method used plain `==` comparisons,
     * silently showing "None" for any form with a combined-flag archiveType and omitting the
     * Archive State/Archive From Form/Times rows entirely. Always rendered for every non-Dialog
     * schema (matching the C++, which calls this unconditionally, not gated on isEnable() - see
     * this method's caller), since "Archive State: Disabled" is itself the useful signal.
     * getArchiveDest() covers both the C++'s archive.u.formName and archive.u.dirPath union members
     * (this jar consolidates that C union into one string field) - used for whichever destination
     * type is actually set. No-Attachments/No-Diary render as plain Yes/No text rather than the
     * C++'s checkbox widget (CWebUtil::ChkBoxInput has no port in this codebase) - a deliberate,
     * minor cosmetic simplification matching how every other boolean flag in this file already
     * renders. getAgeQualifierInDays()/getAgeQualifierFieldId() have no equivalent anywhere in
     * DocSchemaDetails.cpp - a genuinely newer AR System feature this jar exposes beyond what the
     * original C++ tool could show, kept as a clearly-labeled extra row rather than dropped.
     */
    private String archiveInfo(String formName, boolean isOverlaid, ArchiveInfo archive, Map<Integer, String> fieldNames, int rootLevel) {
        int type = archive.getArchiveType();
        boolean archiveToForm = (type & ArchiveInfo.AR_ARCHIVE_FORM) != 0;
        boolean deleteSource = (type & ArchiveInfo.AR_ARCHIVE_DELETE) != 0;
        boolean archiveToFile = (type & (ArchiveInfo.AR_ARCHIVE_FILE_ARX | ArchiveInfo.AR_ARCHIVE_FILE_XML)) != 0;
        boolean noAttachments = (type & ArchiveInfo.AR_ARCHIVE_NO_ATTACHMENTS) != 0;
        boolean noDiary = (type & ArchiveInfo.AR_ARCHIVE_NO_DIARY) != 0;

        Table tbl = new Table("schemaArchive", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");

        StringBuilder typeText = new StringBuilder();
        if (!archiveToForm && !archiveToFile) {
            typeText.append("None");
        } else if (archiveToForm || deleteSource) {
            if (archiveToForm) typeText.append("Copy to Archive");
            if (deleteSource && archiveToForm) typeText.append(" and ");
            if (deleteSource) typeText.append("Delete From Source");
        }
        tbl.addRow(new TableRow().addCellList("Archive Type", typeText.toString()));
        tbl.addRow(new TableRow().addCellList("Archive State", archive.isEnable() ? "Enabled" : "Disabled"));

        if (archiveToFile) {
            tbl.addRow(new TableRow().addCellList("Archive to File", WebUtil.validate(nullToEmpty(archive.getArchiveDest()))));
        } else if (archiveToForm) {
            String dest = archive.getArchiveDest();
            boolean destOverlaid = dest != null && !dest.isEmpty() && globalFields != null && globalFields.isOverlaid(dest);
            String destLink = dest == null || dest.isEmpty() ? "" : URLLink.to(dest, Naming.schemaDetail(dest, destOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
            tbl.addRow(new TableRow().addCellList("Archive to Form",
                destLink + "<br/>No Attachments: " + (noAttachments ? "Yes" : "No") + "&nbsp;&nbsp;No Diary: " + (noDiary ? "Yes" : "No")));
        }

        if (archive.getArchiveFrom() != null && !archive.getArchiveFrom().isEmpty()) {
            String from = archive.getArchiveFrom();
            boolean fromOverlaid = globalFields != null && globalFields.isOverlaid(from);
            tbl.addRow(new TableRow().addCellList("Archive From Form", URLLink.to(from, Naming.schemaDetail(from, fromOverlaid), ImageTag.Id.Schema, rootLevel).toHtml()));
        }

        if (archive.getAgeQualifierInDays() > 0) {
            tbl.addRow(new TableRow().addCellList("Age Qualifier", archive.getAgeQualifierInDays() + " days after " + fieldRef(fieldNames, archive.getAgeQualifierFieldId())));
        }

        tbl.addRow(new TableRow().addCellList("Times", ScheduleFormat.calendar(archive.getArchiveTmInfo())));

        String qual = archive.getQualifier() != null && archive.getQualifier().getOperation() != QualifierInfo.AR_COND_OP_NONE
            ? qualification(formName, isOverlaid, archive.getQualifier(), "Archive Qualification", rootLevel) : "";
        tbl.addRow(new TableRow().addCellList("Qualification", qual));

        if (archive.getDescription() != null && !archive.getDescription().isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Description", WebUtil.validate(archive.getDescription())));
        }
        return accordionItem("Archive Settings", rootLevel, tbl);
    }

    /**
     * Java port of CARInside::LinkToGroup (ARInside.cpp:1177-1203) - links to the real Group/Role
     * page using its real name (e.g. "Public" for group 0) when it resolves, falls back to the raw
     * numeric ID as plain text otherwise (matches every one of {@link #permissionsInfo}'s three
     * permission tables' cell content). This is NOT a row-inclusion filter - permissionsInfo's own
     * Group Permissions loop applies the separate ValidateGroup row-skip before ever calling this
     * for a negative ID; this method itself never omits anything, only chooses link-vs-text. Fixed
     * via user report to resolve the real name (groupsById, from a cheap early identity.
     * listGroups() call - see Main.java) instead of the generic literal "Group N" this used to show
     * regardless of the group's actual name.
     */
    private String groupRef(int groupId, String appRefName, int rootLevel) {
        if (groupId < 0) {
            arinside.ar.RoleRecord role = roleIndex.find(groupId, appRefName);
            if (role != null) return URLLink.to(role.name, Naming.roleDetail(role.requestId), ImageTag.Id.Role, rootLevel).toHtml();
            return Integer.toString(groupId);
        }
        arinside.ar.GroupRecord group = groupsById.get(groupId);
        if (group != null) {
            return URLLink.to(group.name, Naming.groupDetail(groupId), ImageTag.Id.Group, rootLevel).toHtml();
        }
        return Integer.toString(groupId);
    }

    private String fieldRef(Map<Integer, String> fieldNames, int fieldId) {
        String name = fieldNames.get(fieldId);
        return name == null ? "Field " + fieldId : WebUtil.validate(name) + " (" + fieldId + ")";
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowResultListProperties/ShowSortListProperties/
     * ShowIndexProperties each calling pInside->AddFieldReference(schema, field, CRefItem(schema,
     * REFM_SCHEMA_RESULTLIST/REFM_SCHEMA_SORTLIST/REFM_SCHEMA_INDEX)) - previously entirely missing
     * here, so a field used in its own schema's Result List/Sort List/an Index never showed up in
     * that field's own "Referenced By" table. detail matches RefItem.cpp's "Field in ResultList"/
     * "Field in SortList"/"Field in {indexName}" text, with the "ResultList"/"SortList"/index-name
     * portion itself wrapped in a link too, matching CRefItem::LinkToSchemaResultList/SortList/
     * Index exactly - confirmed by reading FileNaming.cpp's SchemaResultList/SchemaSortList/
     * SchemaIndexes classes: all three resolve to the exact same URL as the schema's own index.htm
     * (there's no per-section anchor fragment in the real tool either, despite the "took you to that
     * section of the page" framing this looked like at first - it's a same-target, at-first-glance-
     * redundant link with the "Server object" column's own link, but the real tool always renders
     * it, so this does too).
     */
    private void addSchemaFieldReference(String formName, boolean isOverlaid, int fieldId, String detail) {
        FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(formName, "Schema", ImageTag.Id.Schema,
            Naming.schemaDetail(formName, isOverlaid), detail);
        fieldRefs.add(formName, fieldId, ref);
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowIndexProperties - one separate table per index (not a
     * single summary table with a comma-joined, unlinked field list, which this port previously
     * simplified it down to), each field a real link to its own detail page plus Field ID/Datatype/
     * Modified/By columns matching the C++ exactly. Reuses the same table id "indexTbl" across every
     * index on the page - a real, harmless C++ quirk (duplicate ids across sibling tables), not
     * something this port needs to work around.
     */
    private Table indexTable(String formName, boolean isOverlaid, IndexInfo ix, Map<Integer, Field> fieldsById, int rootLevel) {
        Table tbl = new Table("indexTbl", "TblObjectList");
        tbl.description = (ix.isUnique() ? "Unique Index :" : "Index: ") + WebUtil.objName(nullToEmpty(ix.getIndexName()));
        tbl.addColumn(30, "Field Name");
        tbl.addColumn(10, "Field ID");
        tbl.addColumn(15, "Datatype");
        tbl.addColumn(20, "Modified");
        tbl.addColumn(25, "By");

        List<Integer> ids = ix.getIndexFields();
        boolean any = false;
        if (ids != null) {
            for (int fieldId : ids) {
                Field field = fieldsById.get(fieldId);
                if (field == null) continue;
                tbl.addRow(new TableRow().addCellList(
                    URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml(),
                    Integer.toString(field.getFieldID()),
                    AREnumLabels.dataType(field.getDataType()),
                    DateTimeFormat.toHtmlString(field.getLastUpdateTime().getValue()),
                    userLink(field.getLastChangedBy(), rootLevel)));
                addSchemaFieldReference(formName, isOverlaid, field.getFieldID(),
                    "Field in " + URLLink.to(nullToEmpty(ix.getIndexName()), Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.NoImage, rootLevel).toHtml());
                any = true;
            }
        }
        if (any) tbl.removeEmptyMessageRow();
        return tbl;
    }

    /**
     * Java port of DocSchemaDetails.cpp's ShowAuditProperties/ShowArchiveProperties feeding their
     * qualification through CARQualification with a CRefItem tagged REFM_SCHEMA_AUDIT_QUALIFICATION/
     * REFM_SCHEMA_ARCHIVE_QUALIFICATION (RefItem.cpp:638-643 for the "Audit Qualification"/"Archive
     * Qualification" detail text) - previously a no-op sink here, so a field referenced only by its
     * own form's Archive/Audit qualifier never showed up in that field's own "Referenced By" table,
     * same bug shape as the field-reference-sink gap fixed earlier for menus.
     */
    private String qualification(String formName, boolean isOverlaid, com.bmc.arsys.api.QualifierInfo q, String detail, int rootLevel) {
        QualificationRenderer.FieldReferenceSink sink = (f, fieldId, fieldExists, qualDetail) -> {
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(formName, "Schema", ImageTag.Id.Schema, Naming.schemaDetail(formName, isOverlaid), qualDetail);
            fieldRefs.add(f, fieldId, ref);
            if (!fieldExists) missingFieldRefs.add(f, fieldId, ref);
        };
        QualificationRenderer renderer = new QualificationRenderer(formName, rootLevel, globalFields, sink);
        return renderer.render(q, detail);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Java port of DocSchemaDetails::ShowGeneralInfo's Name/Type/Default View rows - Type uses the shared SchemaTypeIndex override (see SchemaOverviewPage's javadoc) so this page agrees with the overview list; Default View links to the real VUI page when the form's defaultVui (a numeric VUI ID as a string - Views have no separate display name, see VuiDetailPage's javadoc) actually resolves to one of this form's own VUIs, matching the C++'s CheckedURLLink fallback-to-plain-text behavior otherwise. */
    private String generalInfo(String formName, boolean isOverlaid, Form form, List<View> vuis, int rootLevel) {
        Table tbl = new Table("schemaGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Name", WebUtil.validate(formName)));
        String type = AREnumLabels.internalSchemaType(schemaTypes.internalSchemaType(formName, form.getFormType()));
        String details = typeDetails(formName, isOverlaid, form, rootLevel);
        if (!details.isEmpty()) type += " " + details;
        tbl.addRow(new TableRow().addCellList("Type", type));
        tbl.addRow(new TableRow().addCellList("Default View", defaultViewCell(formName, isOverlaid, form.getDefaultVUI(), vuis, rootLevel)));

        // Java port of DocSchemaDetails.cpp:2176-2198 - DB Table ID/View/SH View rows, sourced from
        // a raw SQL passthrough query (see SchemaDbInfoIndex's javadoc for why this isn't a jar
        // limitation after all). Server mode only - null in file/XML mode, same as the C++'s own
        // data source being unavailable there. The C++ also gates this block on
        // CompareServerVersion(7,1) - this port doesn't thread server-version context into doc
        // pages elsewhere (an existing, accepted simplification), so a pre-7.1 server just means
        // schemaDbInfo has no rows for anything, which renders identically to the version-gated skip.
        arinside.scan.SchemaDbInfoIndex.SchemaDbInfo dbInfo = schemaDbInfo != null ? schemaDbInfo.find(formName) : null;
        if (dbInfo != null) {
            tbl.addRow(new TableRow().addCellList("DB Table ID", dbInfo.schemaId() > 0 ? String.valueOf(dbInfo.schemaId()) : ""));
            if (form.getFormType() != Constants.AR_SCHEMA_DIALOG && form.getFormType() != Constants.AR_SCHEMA_VENDOR) {
                tbl.addRow(new TableRow().addCellList("DB Table View", WebUtil.validate(dbInfo.viewName())));
                tbl.addRow(new TableRow().addCellList("DB Table SH Views", WebUtil.validate(dbInfo.shViewName())));
            }
        }

        return tbl.toXHtml();
    }

    /**
     * Java port of DocSchemaDetails.cpp's TypeDetails() - previously scoped to just the Join
     * branch's "(memberA &lt;-&gt; memberB)" member-forms line (a hard dependency for the Fields-tab
     * filter's "Real Field" rendering on Join forms, via schema_page.js's join-left/join-right
     * clone-source spans - see the JS's own javadoc history), never the Join qualification line, nor
     * View's "(Table Name: X   Key Field: Y)" or Vendor's "(Plugin: X   Table: Y)" sub-info - all
     * three now ported. Join qualification uses a two-schema QualificationRenderer (memberA/memberB)
     * matching CARQualification's own two-form constructor there (`TypeDetails()` at
     * DocSchemaDetails.cpp:1236), and is only shown when non-empty (unlike Archive/Audit's
     * qualification rows, which always render even when empty). Field references inside it feed
     * FieldReferenceIndex/MissingFieldReferenceIndex with a real "Join Qualification" detail label
     * (REFM_SCHEMA_JOIN_QUALIFICATION, RefItem.cpp:635-636) - previously a no-op sink here, same bug
     * shape as the Archive/Audit field-reference gap fixed the same session. Note this is NOT the
     * same as {@link #allFieldsSpecialTable}'s own no-op QualificationRenderer sink (used only for
     * its fieldRef() link-rendering convenience) - that one correctly stays a no-op, since the real
     * C++'s AllFieldsSpecial() renders those Real-Field links via plain LinkToField(), never
     * CRefItem/AddFieldReference (confirmed by reading DocSchemaDetails.cpp:426-518 in full).
     */
    private String typeDetails(String formName, boolean isOverlaid, Form form, int rootLevel) {
        if (form instanceof JoinForm join) {
            StringBuilder sb = new StringBuilder("(<span id='join-left'>").append(schemaLink(join.getMemberA(), rootLevel))
                .append("</span> &lt;-&gt; <span id='join-right'>").append(schemaLink(join.getMemberB(), rootLevel)).append("</span>)");
            QualifierInfo joinQual = join.getJoinQualification();
            if (joinQual != null && joinQual.getOperation() != QualifierInfo.AR_COND_OP_NONE) {
                QualificationRenderer.FieldReferenceSink sink = (f, fieldId, fieldExists, detail) -> {
                    FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(formName, "Schema", ImageTag.Id.Schema, Naming.schemaDetail(formName, isOverlaid), detail);
                    fieldRefs.add(f, fieldId, ref);
                    if (!fieldExists) missingFieldRefs.add(f, fieldId, ref);
                };
                QualificationRenderer qr = new QualificationRenderer(join.getMemberA(), join.getMemberB(), rootLevel, globalFields, sink);
                sb.append("<br/>Qualification: ").append(qr.render(joinQual, "Join Qualification"));
            }
            return sb.toString();
        }
        if (form instanceof ViewForm view) {
            return "<span class='additionalInfo'>(Table&nbsp;Name: " + WebUtil.validate(nullToEmpty(view.getTableName()))
                + " &nbsp;&nbsp;&nbsp; Key&nbsp;Field: " + WebUtil.validate(nullToEmpty(view.getKeyField())) + ")</span>";
        }
        if (form instanceof com.bmc.arsys.api.VendorForm vendor) {
            return "<span class='additionalInfo'>(Plugin:&nbsp;" + WebUtil.validate(nullToEmpty(vendor.getVendorName()))
                + " &nbsp;&nbsp;&nbsp; Table: " + WebUtil.validate(nullToEmpty(vendor.getTableName())) + ")</span>";
        }
        return "";
    }

    /**
     * Java port of DocSchemaDetails.cpp's `CARVui defaultVUI(schema.GetInsideId(),
     * schema.GetDefaultVUI());` + `CheckedURLLink(defaultVUI, schema.GetDefaultVUI(), rootLevel)`.
     * Form.getDefaultVUI() (both the C and Java AR APIs) returns the default view's LABEL text (e.g.
     * "Best Practice View"), not its internal VUI name/id - confirmed by comparing live C++ output
     * (which shows a hyperlink captioned with the matched VUI's raw internal name, e.g.
     * "399990344") against its own Views-tab table, where that same VUI's Label column reads "Best
     * Practice View". CARVui's (schemaInsideId, const string& vuiLabel) constructor resolves this by
     * scanning every VUI's own AR_DPROP_LABEL display property for a match (first match wins) - the
     * same lookup this method now performs. Caption stays the human-readable label (an improvement
     * over C++'s raw-name caption), but is now wrapped in the same hyperlink C++ produces when a
     * matching VUI is found.
     *
     * <p>Labels aren't unique - e.g. every locale variant of a "Best Practice View" VUI (399990344,
     * 399990344_de, 399990344_fr, ...) shares the identical Label text - so "first match wins" is
     * only deterministic if scanned in the SAME order the real tool scans it. CARVUIList sorts its
     * own vuiList by name (`std::sort(sortedList..., SortByName(...))` in ARVUIList.cpp) before
     * CARVui's label-search loop walks it in that order, so the real tool always resolves the
     * alphabetically-first name among same-labeled VUIs (confirmed via live comparison: C++ links to
     * "399990344", the alphabetically-first of the ten "Best Practice View"-labeled VUIs on
     * HPD:Help Desk). {@code vuis} isn't guaranteed to already be in that order, so it's sorted here
     * by name to match.
     */
    private String defaultViewCell(String formName, boolean isOverlaid, String defaultVui, List<View> vuis, int rootLevel) {
        if (defaultVui == null || defaultVui.isEmpty()) return "";
        List<View> sorted = new java.util.ArrayList<>(vuis);
        sorted.sort(java.util.Comparator.comparing(v -> v.getName() == null ? "" : v.getName()));
        for (View v : sorted) {
            String label = displayProp(v.getDisplayProperties(), Constants.AR_DPROP_LABEL);
            if (defaultVui.equals(label)) {
                return URLLink.to(defaultVui, Naming.schemaVuiDetail(formName, isOverlaid, v.getVUIId()), ImageTag.Id.Document, rootLevel).toHtml();
            }
        }
        return WebUtil.validate(defaultVui);
    }

    /**
     * Java port of DocSchemaDetails.cpp's AllFieldsJson + GenerateFieldTableDescription - the
     * client-side instant search box above the Fields tab's table (schema_page.js's
     * execFieldFilter/#fieldNameFilter handler, byte-identical to the C++'s own copy - confirmed
     * via diff), previously entirely missing here even though the JS to drive it already shipped.
     * Emits the "var schemaFieldList = [...]" JSON array the JS reads (row shape: fieldId, name,
     * numeric dataType, view count, plain-text modified, changed-by, relative link, then - only for
     * Join/View/Vendor forms - the same "Real Field" data allFieldsSpecialTable's HTML rendering
     * uses, packed into extra array slots schemaFieldManager's per-type renderer expects), the
     * filter input/button/result-count markup, and the CSV download link (also previously missing -
     * the CSV file itself was already being generated and saved, just never linked to).
     */
    private String fieldsFilterHeader(String formName, boolean isOverlaid, Form form, List<Field> fields, boolean isSpecialForm, int rootLevel) {
        StringBuilder json = new StringBuilder("\nvar schemaFieldList = [");
        JoinForm join = form instanceof JoinForm jf ? jf : null;

        int count = 0;
        for (Field field : fields) {
            int viewCount = field.getDisplayInstance() == null ? 0 : field.getDisplayInstance().size();
            String link = URLLink.relativeUrl(rootLevel, Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()));

            if (count > 0) json.append(',');
            json.append('[').append(field.getFieldID()).append(",\"")
                .append(WebUtil.jsString(field.getName())).append("\",")
                .append(field.getDataType()).append(',')
                .append(viewCount).append(",\"")
                .append(WebUtil.jsString(DateTimeFormat.toPlainString(field.getLastUpdateTime().getValue()))).append("\",\"")
                .append(WebUtil.jsString(field.getLastChangedBy())).append("\",\"")
                .append(WebUtil.jsString(link)).append('"');
            if (isSpecialForm) {
                for (String item : realFieldJsonItems(join, field, rootLevel)) json.append(',').append(item);
            }
            json.append(']');
            count++;
        }
        json.append("];\n");
        if (isSpecialForm) {
            String type = form.getFormType() == Constants.AR_SCHEMA_VIEW ? "view"
                : form.getFormType() == Constants.AR_SCHEMA_VENDOR ? "vendor"
                : form.getFormType() == Constants.AR_SCHEMA_JOIN ? "join" : null;
            if (type != null) json.append("schemaFieldManager.setRenderer(\"").append(type).append("\");\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        sb.append("<div><label for=\"fieldNameFilter\">Filter: </label><span class='clearable'><input type=\"text\" class='data_field' id=\"fieldNameFilter\" placeholder=\"search by name or id\"/></span><button id=\"execFieldFilter\">Filter</button></div>\n");
        sb.append(new ImageTag(ImageTag.Id.Document, rootLevel).toHtml())
            .append("<span id='fieldListFilterResultCount'></span>").append(fields.size()).append(" fields (")
            .append(URLLink.to("data", Naming.schemaFieldsCsv(formName, isOverlaid), ImageTag.Id.NoImage, rootLevel).toHtml())
            .append(")\n");
        sb.append("<div id=\"result\"></div>");
        return sb.toString();
    }

    /** Java port of AllFieldsJson's per-mapping-type extra JSON slots - same DISPLAY_FIELD/JoinFieldMapping/ViewFieldMapping/VendorFieldMapping branching as realFieldCell(), just packed as raw JSON items instead of HTML (schema_page.js's schemaFieldManager unpacks these client-side). */
    private List<String> realFieldJsonItems(JoinForm join, Field field, int rootLevel) {
        FieldMapping map = field.getFieldMap();
        if (join != null && map instanceof JoinFieldMapping jm) {
            if (field.getFieldID() == 1) {
                List<String> items = new ArrayList<>(joinMemberFieldJson(join.getMemberA(), 1, rootLevel));
                items.addAll(joinMemberFieldJson(join.getMemberB(), 1, rootLevel));
                return items;
            }
            if (jm.getFieldID() > 0) {
                String targetForm = jm.getIndex() > 0 ? join.getMemberB() : join.getMemberA();
                List<String> items = new ArrayList<>(joinMemberFieldJson(targetForm, jm.getFieldID(), rootLevel));
                items.add(Integer.toString(jm.getIndex() > 0 ? 1 : 0));
                return items;
            }
            return List.of();
        }
        if (map instanceof ViewFieldMapping vm) return List.of("\"" + WebUtil.jsString(vm.getFieldName()) + "\"");
        if (map instanceof VendorFieldMapping vem) return List.of("\"" + WebUtil.jsString(vem.getFieldName()) + "\"");
        return List.of();
    }

    private List<String> joinMemberFieldJson(String targetForm, int fieldId, int rootLevel) {
        String name = globalFields == null ? null : globalFields.fieldName(targetForm, fieldId);
        boolean targetOverlaid = globalFields != null && globalFields.isOverlaid(targetForm);
        String link = name != null ? URLLink.relativeUrl(rootLevel, Naming.schemaFieldDetail(targetForm, targetOverlaid, fieldId)) : "";
        if (name == null) name = Integer.toString(fieldId);
        return List.of("\"" + WebUtil.jsString(name) + "\"", "\"" + WebUtil.jsString(link) + "\"");
    }

    /**
     * Builds the field list table shared by the HTML "Fields" tab and the CSV export
     * (AllFields/AllFieldsCsv in the C++). Note: unlike the C++ (which formats the Modified
     * column differently per output - DateTimeToHTMLString's "&nbsp;" for HTML vs. plain
     * DateTimeToString for CSV), this shares one plain-space-formatted value for both, since the
     * "&nbsp;" is only a no-line-wrap hint for the HTML table, not a correctness requirement, and
     * embedding "&nbsp;" literally in the CSV export would be a real data-quality bug.
     */
    private Table allFieldsTable(String formName, boolean isOverlaid, List<Field> fields, int rootLevel) {
        Table tbl = new Table("fieldListAll", "TblObjectList");
        tbl.addColumn(40, "Field Name");
        tbl.addColumn(10, "Field ID");
        tbl.addColumn(10, "Datatype");
        tbl.addColumn(10, "In Views");
        tbl.addColumn(10, "Modified");
        tbl.addColumn(20, "By");

        for (Field field : fields) {
            int viewCount = field.getDisplayInstance() == null ? 0 : field.getDisplayInstance().size();
            String viewsClass = (viewCount == 0 && field.getFieldID() != 15) ? "fieldInNoView" : "";

            TableRow row = new TableRow();
            row.addCell(URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml());
            row.addCell(new TableCell(field.getFieldID()));
            row.addCell(AREnumLabels.dataType(field.getDataType()));
            row.addCell(new TableCell(viewCount, viewsClass));
            row.addCell(DateTimeFormat.toPlainString(field.getLastUpdateTime().getValue()));
            row.addCell(field.getLastChangedBy());
            tbl.addRow(row);
        }
        if (!fields.isEmpty()) tbl.removeEmptyMessageRow();

        return tbl;
    }

    /**
     * Java port of DocSchemaDetails.cpp's AllFieldsSpecial()/AllFieldsSpecialCsv() - the Fields tab
     * for Join/View/Vendor forms (IsJoinViewOrVendorForm()), same columns as {@link #allFieldsTable}
     * plus a "Real Field" column showing each field's underlying data source: for a join form, a
     * link to the real field on whichever member form it maps to (field ID 1 is the special
     * "Request ID" case that maps to *both* member forms' own field 1 at once); for view/vendor
     * forms, the plain (unlinked) external column/field name, since that's not an AR System field.
     */
    private Table allFieldsSpecialTable(String formName, boolean isOverlaid, Form form, List<Field> fields, int rootLevel) {
        Table tbl = new Table("fieldListAll", "TblObjectList");
        tbl.addColumn(20, "Field Name");
        tbl.addColumn(10, "Field ID");
        tbl.addColumn(10, "Datatype");
        tbl.addColumn(30, "Real Field");
        tbl.addColumn(10, "In Views");
        tbl.addColumn(10, "Modified");
        tbl.addColumn(20, "By");

        // Deliberately a no-op sink, NOT the same gap as typeDetails()'s Join Qualification one -
        // this QualificationRenderer is only reused here for fieldRef()'s link-rendering
        // convenience (the "Real Field" column below), and the real C++'s AllFieldsSpecial()
        // (DocSchemaDetails.cpp:426-518) renders those same links via plain LinkToField(), never
        // CRefItem/AddFieldReference - confirmed by reading it in full, not assumed.
        QualificationRenderer ref = new QualificationRenderer(formName, rootLevel, globalFields, (f, id, exists, detail) -> {});
        JoinForm join = form instanceof JoinForm jf ? jf : null;

        for (Field field : fields) {
            int viewCount = field.getDisplayInstance() == null ? 0 : field.getDisplayInstance().size();
            String viewsClass = (viewCount == 0 && field.getFieldID() != 15) ? "fieldInNoView" : "";

            TableRow row = new TableRow();
            row.addCell(URLLink.to(field.getName(), Naming.schemaFieldDetail(formName, isOverlaid, field.getFieldID()), ImageTag.Id.Document, rootLevel).toHtml());
            row.addCell(new TableCell(field.getFieldID()));
            row.addCell(AREnumLabels.dataType(field.getDataType()));
            row.addCell(realFieldCell(join, field, ref, rootLevel));
            row.addCell(new TableCell(viewCount, viewsClass));
            row.addCell(DateTimeFormat.toPlainString(field.getLastUpdateTime().getValue()));
            row.addCell(field.getLastChangedBy());
            tbl.addRow(row);
        }
        if (!fields.isEmpty()) tbl.removeEmptyMessageRow();

        return tbl;
    }

    private String realFieldCell(JoinForm join, Field field, QualificationRenderer ref, int rootLevel) {
        FieldMapping map = field.getFieldMap();
        if (join != null && map instanceof JoinFieldMapping jm) {
            if (field.getFieldID() == 1) {
                return ref.fieldRef(join.getMemberA(), 1) + " -&gt; " + schemaLink(join.getMemberA(), rootLevel)
                    + "<br/>\n" + ref.fieldRef(join.getMemberB(), 1) + " -&gt; " + schemaLink(join.getMemberB(), rootLevel);
            }
            if (jm.getFieldID() > 0) {
                String targetForm = jm.getIndex() > 0 ? join.getMemberB() : join.getMemberA();
                return ref.fieldRef(targetForm, jm.getFieldID()) + " -&gt; " + schemaLink(targetForm, rootLevel);
            }
            return "";
        }
        if (map instanceof ViewFieldMapping vm) return WebUtil.validate(vm.getFieldName());
        if (map instanceof VendorFieldMapping vem) return WebUtil.validate(vem.getFieldName());
        return "";
    }

    private String schemaLink(String schemaName, int rootLevel) {
        if (schemaName == null || schemaName.isEmpty()) return "";
        boolean isOverlaid = globalFields != null && globalFields.isOverlaid(schemaName);
        return URLLink.to(schemaName, Naming.schemaDetail(schemaName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }
}
