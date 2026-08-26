package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.ContainerSource;
import arinside.ar.GroupRecord;
import arinside.ar.OverlaySupport;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.AppMembershipIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.GuideCallIndex;
import arinside.scan.ImageReferenceIndex;
import arinside.scan.RoleIndex;
import arinside.scan.SchemaWorkflowIndex;
import arinside.scan.WorkflowReferenceIndex;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.ContainerOwner;
import com.bmc.arsys.api.PermissionInfo;
import com.bmc.arsys.api.Reference;
import com.bmc.arsys.api.ReferenceType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the shared parts of doc/DocContainerHelper.cpp (BaseInfo/PermissionList/
 * SubadminList/ContainerForms/GuideContent) plus doc/DocApplicationDetails.cpp /
 * DocPacklistDetails.cpp / DocWebserviceDetails.cpp / DocAlGuideDetails.cpp /
 * DocFilterGuideDetails.cpp's shared base, collapsed into one General-info-plus-raw-member-list
 * page. Renders every Reference (name + raw ReferenceType) in one flat "Members" table instead of
 * the C++'s per-subtype grouped rendering (e.g. Application groups by owning schema, shows entry
 * points, help/about-box config) - that grouping remains a deliberate, documented scope cut. The
 * General tab's Permissions/Subadministrator Permissions/Owner Object List/Guide Content sections
 * (previously entirely missing - only Label+Description were shown) are now ported in full; none
 * of them needed a new fetch pass, since Container.getAssignedGroup()/getAdminGroupList()/
 * getContainerOwner()/getReferences() are all already-loaded fields on the object this page
 * already fetches.
 */
public final class ContainerDetailPage {
    private final ContainerSource repo;
    private final AppConfig appConfig;
    private final int containerType;
    private final String overviewTitle;
    private final ImageTag.Id icon;
    private final int serverOverlayMode;
    private final Set<String> knownUserNames;
    private final WorkflowReferenceIndex workflowIndex;
    private final ImageReferenceIndex imageRefs;
    private final GlobalFieldIndex globalFields;
    private final AppMembershipIndex appIndex;
    private final SchemaWorkflowIndex schemaWorkflow;
    private final GuideCallIndex guideCalls;
    private final ContainerReferenceIndex containerRefs;
    private final RoleIndex roleIndex;
    private final Map<Integer, GroupRecord> groupsById;

    public ContainerDetailPage(ContainerSource repo, AppConfig appConfig, int containerType, String overviewTitle, ImageTag.Id icon, int serverOverlayMode, Set<String> knownUserNames, WorkflowReferenceIndex workflowIndex, ImageReferenceIndex imageRefs, GlobalFieldIndex globalFields, AppMembershipIndex appIndex, SchemaWorkflowIndex schemaWorkflow, GuideCallIndex guideCalls, ContainerReferenceIndex containerRefs, RoleIndex roleIndex, Map<Integer, GroupRecord> groupsById) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.containerType = containerType;
        this.overviewTitle = overviewTitle;
        this.icon = icon;
        this.serverOverlayMode = serverOverlayMode;
        this.knownUserNames = knownUserNames;
        this.workflowIndex = workflowIndex;
        this.imageRefs = imageRefs;
        this.globalFields = globalFields;
        this.appIndex = appIndex;
        this.schemaWorkflow = schemaWorkflow;
        this.guideCalls = guideCalls;
        this.containerRefs = containerRefs;
        this.roleIndex = roleIndex;
        this.groupsById = groupsById;
    }

    /**
     * Matches DocApplicationDetails.cpp's SearchContainer: PACK containers get their app directly
     * (member of the app's own content); GUIDE/FILTER_GUIDE derive it from their owner form's app;
     * APP/WEBSERVICE never get an app assigned (a genuine gap in the original tool, see
     * AppMembershipIndex's javadoc).
     */
    private String ownerApp(String name, Container c) {
        if (containerType == Constants.ARCON_PACK) return appIndex.packApp(name);
        if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE) return appIndex.guideApp(c.getContainerOwner());
        return null;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public Container fetch(ContainerSource repo, String name) throws ARException {
        return repo.getContainer(name);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, Container c) throws ARException {
        PagePath page = Naming.containerDetail(containerType, name, OverlaySupport.isOverlaidForNaming(c.getProperties(), serverOverlayMode));

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String appRefName = ownerApp(name, c);
        String head = URLLink.to(overviewTitle, Naming.containerOverview(containerType), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(icon, page.rootLevel()).toHtml() + WebUtil.objName(name)
            + ApplicationHeaderLink.suffix(appRefName, page.rootLevel());
        webPage.addContentHead(head);

        TabControl tabs = new TabControl();
        tabs.addTab("General", generalInfo(c, appRefName, page.rootLevel()));
        tabs.addTab("Members", members(name, c, page.rootLevel()));
        webPage.addContent(tabs.toXHtml());
        // The C++ has no tabbed container page to port a tab-init script from - see container_page.js's own comment.
        webPage.addScriptReference("img/container_page.js");
        if (containerType == Constants.ARCON_APP) {
            webPage.addContent(applicationContent(name, c, page.rootLevel()));
        }
        if (containerType == Constants.ARCON_WEBSERVICE) {
            webPage.addContent(webserviceContent(c, page.rootLevel()));
        }
        if (containerType == Constants.ARCON_GUIDE) {
            webPage.addContent(guideCallers("Active Links calling this guide", "Active Link", guideCalls.alCallers(name), page.rootLevel(),
                n -> URLLink.to(n, Naming.activeLinkDetail(n, false), ImageTag.Id.ActiveLink, page.rootLevel()).toHtml()));
        }
        if (containerType == Constants.ARCON_FILTER_GUIDE) {
            webPage.addContent(guideCallers("Filters calling this guide", "Filter", guideCalls.filterCallers(name), page.rootLevel(),
                n -> URLLink.to(n, Naming.filterDetail(n, false), ImageTag.Id.Filter, page.rootLevel()).toHtml()));
        }
        if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_APP
            || containerType == Constants.ARCON_FILTER_GUIDE || containerType == Constants.ARCON_PACK) {
            webPage.addContent(workflowReferences(name, page.rootLevel()));
        }
        webPage.addContent(ServerObjectHistoryWidget.render(c, knownUserNames, page.rootLevel()));

        workflowIndex.addIfOverlayOrCustom(c.getProperties(),
            new WorkflowReferenceIndex.Ref(name, overviewTitle, icon, page), null, c.getLastUpdateTime(), c.getLastChangedBy());

        webPage.saveInFolder(page.path());
    }

    /** Java port of DocContainerHelper.cpp's BaseInfo. */
    private String generalInfo(Container c, String appRefName, int rootLevel) {
        Table tbl = new Table("containerGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Label", c.getLabel() == null ? WebUtil.EMPTY_VALUE : WebUtil.validate(c.getLabel())));
        tbl.addRow(new TableRow().addCellList("Description", c.getDescription() == null ? WebUtil.EMPTY_VALUE : WebUtil.validate(c.getDescription())));
        tbl.addRow(new TableRow().addCellList("Permissions", permissionList(c, appRefName, rootLevel)));

        if (containerType == Constants.ARCON_PACK || containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_APP) {
            tbl.addRow(new TableRow().addCellList("Subadministrator Permissions", subadminList(c, appRefName, rootLevel)));
        }
        if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE || containerType == Constants.ARCON_WEBSERVICE) {
            tbl.addRow(new TableRow().addCellList("Owner Object List", ownerObjectList(c, rootLevel)));
        }
        if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE) {
            tbl.addRow(new TableRow().addCellList("Guide Content", guideContent(c, rootLevel)));
        }
        return tbl.toXHtml();
    }

    /** Java port of DocContainerHelper.cpp's PermissionList. */
    private String permissionList(Container c, String appRefName, int rootLevel) {
        Table tbl = new Table("permissionList", "TblObjectList");
        tbl.addColumn(5, "Permission");
        tbl.addColumn(10, "Description");
        tbl.addColumn(75, "Name");
        tbl.addColumn(10, "Id");
        List<PermissionInfo> perms = c.getAssignedGroup();
        int count = 0;
        if (perms != null) {
            for (PermissionInfo p : perms) {
                ImageTag.Id visIcon = p.getPermissionValue() == Constants.AR_PERMISSIONS_HIDDEN ? ImageTag.Id.Hidden : ImageTag.Id.Visible;
                tbl.addRow(new TableRow().addCellList(new ImageTag(visIcon, rootLevel).toHtml(),
                    GroupDetailPage.objectPermissionLabel(p.getPermissionValue()), groupRef(p.getGroupID(), appRefName, rootLevel), Integer.toString(p.getGroupID())));
                count++;
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /**
     * Java port of DocContainerHelper.cpp's SubadminList - PACK/GUIDE/APP only. Full CGroupTable
     * rendering (Name/ID/Type/Category/Modified/By) - see GroupPermissionTable's javadoc for the
     * full C++ shape.
     */
    private String subadminList(Container c, String appRefName, int rootLevel) {
        // Table id is "groupList" (matching PermissionList's own table above) - the C++'s
        // CGroupTable always hardcodes that id regardless of which caller uses it, same pattern as
        // "indexTbl"/"referenceList" being reused elsewhere in this codebase.
        return GroupPermissionTable.render("groupList", c.getAdminGroupList(), appRefName, roleIndex, groupsById, knownUserNames, rootLevel).toXHtml();
    }

    /**
     * Java port of DocContainerHelper.cpp's ContainerForms - GUIDE/FILTER_GUIDE/WEBSERVICE only.
     * ContainerOwner.getType()==SCHEMA links to that specific form (matching the C++'s
     * LinkToSchema(ownerName)); ==ALL renders as plain "All Forms" text rather than attempting to
     * link a non-schema placeholder name as if it were one - a small, deliberate improvement over
     * blindly porting the C++'s loop, which would produce a broken link for that case.
     */
    private String ownerObjectList(Container c, int rootLevel) {
        Table tbl = new Table("formList", "TblObjectList");
        tbl.addColumn(100, "Form Name");
        List<ContainerOwner> owners = c.getContainerOwner();
        int count = 0;
        if (owners != null) {
            for (ContainerOwner o : owners) {
                if (o.getType() == ContainerOwner.ALL) {
                    tbl.addRow(new TableRow().addCellList("All Forms"));
                } else if (o.getType() == ContainerOwner.SCHEMA && o.getName() != null) {
                    boolean isOverlaid = globalFields != null && globalFields.isOverlaid(o.getName());
                    tbl.addRow(new TableRow().addCellList(URLLink.to(o.getName(), Naming.schemaDetail(o.getName(), isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml()));
                } else {
                    continue;
                }
                count++;
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /**
     * Java port of DocContainerHelper.cpp's GuideContent - GUIDE/FILTER_GUIDE only. Uses the same
     * already-loaded Container.getReferences() list the Members tab renders (the Java API doesn't
     * split "members" and "ordered guide content" into two separate fetches the way the C++'s
     * GetReferences()/GetContent() do) - ARREF_ACTLINK/ARREF_FILTER link to the referenced object,
     * ARREF_NULL_STRING shows the reference's own label text as a plain guide-step label.
     */
    private String guideContent(Container c, int rootLevel) {
        Table tbl = new Table("guideContent", "TblObjectList");
        tbl.addColumn(20, "Label");
        tbl.addColumn(80, "Object in Guide");
        List<Reference> refs = c.getReferences();
        int count = 0;
        if (refs != null) {
            for (Reference ref : refs) {
                ReferenceType type = ref.getReferenceType();
                String label = "";
                String object = "";
                if (type != null && type.toInt() == ReferenceType.ACTIVELINK.toInt() && ref.getName() != null) {
                    object = URLLink.to(ref.getName(), Naming.activeLinkDetail(ref.getName(), false), ImageTag.Id.ActiveLink, rootLevel).toHtml();
                } else if (type != null && type.toInt() == ReferenceType.FILTER.toInt() && ref.getName() != null) {
                    object = URLLink.to(ref.getName(), Naming.filterDetail(ref.getName(), false), ImageTag.Id.Filter, rootLevel).toHtml();
                } else if (type != null && type.toInt() == ReferenceType.NULL_STRING.toInt()) {
                    label = ref.getLabel() == null ? "" : WebUtil.validate(ref.getLabel());
                } else {
                    continue;
                }
                tbl.addRow(new TableRow().addCellList(label, object));
                count++;
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /**
     * Java port of DocApplicationDetails.cpp's ApplicationInformation() - Primary Form, then a count
     * + hyperlinked-list row for every object type an Application container "owns": member forms
     * (direct content), active links/filters/escalations/al-guides/filter-guides attached to those
     * forms (via SchemaWorkflowIndex - the reverse of what CARSchemaList's GetActiveLinks/GetFilters/
     * GetEscalations/GetActLinkGuides/GetFilterGuides already gave the C++ for free), member packing
     * lists (direct content), Web Services, and menus (via a member form's charMenu limit). The Web
     * Services row is always empty - the original C++ tool's SearchContainer has no ARCON_WEBSERVICE
     * case at all despite the type being in the loop range; this is a genuine, permanent gap in the
     * original tool, replicated here rather than "fixed".
     */
    private String applicationContent(String appName, Container app, int rootLevel) {
        Table tbl = new Table("specificPropList", "TblObjectList");
        tbl.description = "Application Content";
        tbl.addColumn(20, "Type");
        tbl.addColumn(80, "Server Object");

        String primaryForm = primaryForm(app);
        tbl.addRow(new TableRow().addCellList("Primary Form", primaryForm.isEmpty() ? "(null)" : schemaLinkFor(primaryForm, rootLevel)));

        List<String> forms = appIndex.formsOf(appName);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(forms.size(), "Form", "Forms", Naming.schemaOverview(), ImageTag.Id.Schema, rootLevel),
            linkListOfSchemas(forms, rootLevel)));

        List<String> als = union(forms, schemaWorkflow::activeLinksOf);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(als.size(), "Active Link", "Active Links", Naming.activeLinkOverview(), ImageTag.Id.ActiveLink, rootLevel),
            linkList(als, n -> URLLink.to(n, Naming.activeLinkDetail(n, false), ImageTag.Id.ActiveLink, rootLevel).toHtml())));

        List<String> filters = union(forms, schemaWorkflow::filtersOf);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(filters.size(), "Filter", "Filters", Naming.filterOverview(), ImageTag.Id.Filter, rootLevel),
            linkList(filters, n -> URLLink.to(n, Naming.filterDetail(n, false), ImageTag.Id.Filter, rootLevel).toHtml())));

        List<String> escals = union(forms, schemaWorkflow::escalationsOf);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(escals.size(), "Escalation", "Escalations", Naming.escalationOverview(), ImageTag.Id.Escalation, rootLevel),
            linkList(escals, n -> URLLink.to(n, Naming.escalationDetail(n, false), ImageTag.Id.Escalation, rootLevel).toHtml())));

        List<String> alGuides = union(forms, schemaWorkflow::alGuidesOf);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(alGuides.size(), "Active Link Guide", "Active Link Guides", Naming.overviewContainer(Constants.ARCON_GUIDE), ImageTag.Id.ActiveLinkGuide, rootLevel),
            linkList(alGuides, n -> URLLink.to(n, Naming.containerDetail(Constants.ARCON_GUIDE, n, false), ImageTag.Id.ActiveLinkGuide, rootLevel).toHtml())));

        List<String> packs = appIndex.packsOf(appName);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(packs.size(), "Packing List", "Packing Lists", Naming.overviewContainer(Constants.ARCON_PACK), ImageTag.Id.PackingList, rootLevel),
            linkList(packs, n -> URLLink.to(n, Naming.containerDetail(Constants.ARCON_PACK, n, false), ImageTag.Id.PackingList, rootLevel).toHtml())));

        List<String> filterGuides = union(forms, schemaWorkflow::filterGuidesOf);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(filterGuides.size(), "Filter Guide", "Filter Guides", Naming.overviewContainer(Constants.ARCON_FILTER_GUIDE), ImageTag.Id.FilterGuide, rootLevel),
            linkList(filterGuides, n -> URLLink.to(n, Naming.containerDetail(Constants.ARCON_FILTER_GUIDE, n, false), ImageTag.Id.FilterGuide, rootLevel).toHtml())));

        // Web Services: always empty - see javadoc above.
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(0, "Web Service", "Web Services", Naming.overviewContainer(Constants.ARCON_WEBSERVICE), ImageTag.Id.Webservice, rootLevel), "(null)"));

        List<String> menus = appIndex.menusOf(appName);
        tbl.addRow(new TableRow().addCellList(
            CountLink.render(menus.size(), "Menu", "Menus", Naming.overviewMenus(), ImageTag.Id.Menu, rootLevel),
            linkList(menus, n -> URLLink.to(n, Naming.menuDetail(n, false), ImageTag.Id.Menu, rootLevel).toHtml())));

        return tbl.toXHtml();
    }

    private String primaryForm(Container app) {
        List<Reference> refs = app.getReferences();
        if (refs == null) return "";
        for (Reference ref : refs) {
            if (ref.getReferenceType() != null && ref.getReferenceType().toInt() == ReferenceType.SCHEMA.toInt() && ref.getName() != null) {
                return ref.getName();
            }
        }
        return "";
    }

    private String schemaLinkFor(String formName, int rootLevel) {
        boolean isOverlaid = globalFields != null && globalFields.isOverlaid(formName);
        return URLLink.to(formName, Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    private String linkListOfSchemas(List<String> forms, int rootLevel) {
        return linkList(forms, n -> schemaLinkFor(n, rootLevel));
    }

    private String linkList(List<String> names, java.util.function.Function<String, String> linkOf) {
        if (names.isEmpty()) return "(null)";
        StringBuilder sb = new StringBuilder();
        for (String n : names) sb.append(linkOf.apply(n)).append("<br/>\n");
        return sb.toString();
    }

    /** Union of schemaWorkflow lookups across every app-owned form, deduplicated, preserving first-seen order. */
    private List<String> union(List<String> forms, java.util.function.Function<String, List<String>> lookup) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String form : forms) set.addAll(lookup.apply(form));
        return new ArrayList<>(set);
    }

    /**
     * Java port of DocWebserviceDetails.cpp's WSInformation() - Label/Description, then one row per
     * WS_PROPERTIES/WS_OPERATION/WS_ARXML_MAPPING/WS_WSDL/WS_PUBLISHING_LOC/WS_XML_SCHEMA_LOC content
     * reference. The Java API's {@code Reference} base class has no extRef payload at all (unlike the
     * C++'s ARReferenceStruct union) - the raw XML/text content only exists on the
     * {@link com.bmc.arsys.api.ExternalReference} subtype's getValue().
     */
    private String webserviceContent(Container ws, int rootLevel) {
        Table tbl = new Table("specificPropList", "TblObjectList");
        tbl.addColumn(20, "Description");
        tbl.addColumn(80, "Value");

        if (ws.getLabel() != null && !ws.getLabel().isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Label", ws.getLabel()));
        }
        if (ws.getDescription() != null && !ws.getDescription().isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Description", WebUtil.validate(ws.getDescription())));
        }

        String ownerForm = firstSchemaOwner(ws.getContainerOwner());

        List<Reference> refs = ws.getReferences();
        if (refs != null) {
            for (Reference ref : refs) {
                ReferenceType type = ref.getReferenceType();
                if (type == null) continue;
                String label;
                boolean withFieldLinks;
                if (type.toInt() == ReferenceType.WS_PROPERTIES.toInt()) { label = "Property"; withFieldLinks = true; }
                else if (type.toInt() == ReferenceType.WS_OPERATION.toInt()) { label = "Operation"; withFieldLinks = true; }
                else if (type.toInt() == ReferenceType.WS_ARXML_MAPPING.toInt()) { label = "Mapping"; withFieldLinks = true; }
                else if (type.toInt() == ReferenceType.WS_WSDL.toInt()) { label = "WSDL"; withFieldLinks = false; }
                else if (type.toInt() == ReferenceType.WS_PUBLISHING_LOC.toInt()) { label = "Publishing Location"; withFieldLinks = false; }
                else if (type.toInt() == ReferenceType.WS_XML_SCHEMA_LOC.toInt()) { label = "XML Schema"; withFieldLinks = false; }
                else continue;

                Object rawValue = ref instanceof com.bmc.arsys.api.ExternalReference ext && ext.getValue() != null
                    ? ext.getValue().getValue() : null;
                String escaped = WebUtil.validate(rawValue == null ? "" : String.valueOf(rawValue));
                String content = withFieldLinks && ownerForm != null ? xmlFindFields(escaped, ownerForm, rootLevel) : escaped;
                tbl.addRow(new TableRow().addCellList(label, "<pre class=\"preWsInfo\">" + content + "</pre>"));
            }
        }
        return tbl.toXHtml();
    }

    private String firstSchemaOwner(List<ContainerOwner> owners) {
        if (owners == null) return null;
        for (ContainerOwner o : owners) {
            if (o.getType() == ContainerOwner.SCHEMA && o.getName() != null) return o.getName();
        }
        return null;
    }

    private static final java.util.regex.Pattern FIELD_ID_MARKER = java.util.regex.Pattern.compile("arFieldId=&quot;(\\d+)&quot;");

    /** Java port of CARInside::XMLFindFields() - literal-text substitution of {@code arFieldId=&quot;N&quot;} markers (already-HTML-escaped) with a hyperlink to that field, for every field ID actually present in the text that the owner form has (via GlobalFieldIndex.fieldName(), which covers every field regardless of ID range - not byFieldId(), which is deliberately scoped to only the 1,000,000-1,999,999 "global" ID range for a different purpose, see its javadoc). */
    private String xmlFindFields(String escapedText, String formName, int rootLevel) {
        if (escapedText.isEmpty() || globalFields == null) return escapedText;
        boolean isOverlaid = globalFields.isOverlaid(formName);
        java.util.regex.Matcher m = FIELD_ID_MARKER.matcher(escapedText);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int fieldId = Integer.parseInt(m.group(1));
            String fieldName = globalFields.fieldName(formName, fieldId);
            sb.append(escapedText, last, m.start());
            if (fieldName != null) {
                sb.append("arFieldId=&quot;").append(URLLink.to(fieldName, Naming.schemaFieldDetail(formName, isOverlaid, fieldId), ImageTag.Id.Document, rootLevel).toHtml()).append("&quot;");
            } else {
                sb.append(m.group());
            }
            last = m.end();
        }
        sb.append(escapedText, last, escapedText.length());
        return sb.toString();
    }

    /**
     * Java port of DocAlGuideDetails.cpp's ActiveLinkActions() / DocFilterGuideDetails.cpp's
     * FilterActions() - one row per Call Guide action found anywhere that names this guide, via
     * {@link GuideCallIndex}.
     */
    private String guideCallers(String description, String objectColumnLabel, List<GuideCallIndex.Caller> callers, int rootLevel, java.util.function.Function<String, String> linkOf) {
        Table tbl = new Table("exuteInfoList", "TblObjectList");
        tbl.description = description;
        tbl.addColumn(20, "Action");
        tbl.addColumn(80, objectColumnLabel);
        for (GuideCallIndex.Caller caller : callers) {
            tbl.addRow(new TableRow().addCellList(caller.actionLabel(), linkOf.apply(caller.objectName())));
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of output/WorkflowReferenceTable.cpp's ToString() as used on Application/AL-Guide/
     * Filter-Guide/Packing-List detail pages - Type/Server object/Enabled/Description columns. The
     * only real data source feeding this table in the whole C++ codebase is scan/ScanContainers.cpp's
     * ARCON_PACK case (see ContainerReferenceIndex's javadoc), so Enabled is always blank (containers
     * don't support enabled/disabled) and Description is always the fixed "Contained in packing list"
     * text (REFM_PACKINGLIST's wording in util/RefItem.cpp) - not a simplification, that's genuinely
     * the only case this table ever renders in the original tool.
     */
    private String workflowReferences(String name, int rootLevel) {
        List<arinside.scan.ContainerReferenceIndex.ContainerRef> refs = containerRefs.packingLists(name);
        Table tbl = new Table("referenceList", "TblObjectList");
        tbl.description = "Workflow Reference";
        tbl.addColumn(10, "Type");
        tbl.addColumn(45, "Server object");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(40, "Description");
        for (arinside.scan.ContainerReferenceIndex.ContainerRef ref : refs) {
            tbl.addRow(new TableRow().addCellList("Container",
                URLLink.to(ref.name(), Naming.containerDetail(Constants.ARCON_PACK, ref.name(), false), ImageTag.Id.PackingList, rootLevel).toHtml(),
                "", "Contained in packing list"));
        }
        return tbl.toXHtml();
    }

    /** Java port of CARInside::LinkToGroup - see SchemaDetailPage.groupRef's identical javadoc for why this resolves the real name/link instead of a generic "Group N"/"Role N" literal. */
    private String groupRef(int groupId, String appRefName, int rootLevel) {
        if (groupId < 0) {
            arinside.ar.RoleRecord role = roleIndex == null ? null : roleIndex.find(groupId, appRefName);
            if (role != null) return URLLink.to(role.name, Naming.roleDetail(role.requestId), ImageTag.Id.Role, rootLevel).toHtml();
            return Integer.toString(groupId);
        }
        GroupRecord group = groupsById == null ? null : groupsById.get(groupId);
        if (group != null) {
            return URLLink.to(group.name, Naming.groupDetail(groupId), ImageTag.Id.Group, rootLevel).toHtml();
        }
        return Integer.toString(groupId);
    }

    private String members(String containerName, Container c, int rootLevel) {
        Table tbl = new Table("containerMembers", "TblObjectList");
        tbl.addColumn(30, "Type");
        tbl.addColumn(50, "Name");
        tbl.addColumn(20, "Label");

        int count = 0;
        if (c.getReferences() != null) {
            for (Reference ref : c.getReferences()) {
                TableRow row = new TableRow();
                row.addCell(AREnumLabels.referenceType(ref.getReferenceType()));
                row.addCell(ref.getName() == null ? "" : ref.getName());
                row.addCell(ref.getLabel() == null ? "" : ref.getLabel());
                tbl.addRow(row);
                count++;

                // Java port of DocPacklistDetails.cpp's ARREF_IMAGE case - feeds ImageDetailPage's
                // "Workflow Reference" section, same side-effect pattern as workflowIndex above.
                if (ref.getReferenceType() != null && ref.getReferenceType().toInt() == ReferenceType.IMAGE.toInt() && ref.getName() != null) {
                    imageRefs.add(ref.getName(), new ImageReferenceIndex.Ref(containerName, overviewTitle, icon,
                        Naming.containerDetail(containerType, containerName, OverlaySupport.isOverlaidForNaming(c.getProperties(), serverOverlayMode)), "Member"));
                }
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }
}
