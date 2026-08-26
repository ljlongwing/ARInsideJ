package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.GroupRecord;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.AppMembershipIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;
import arinside.scan.RoleIndex;
import arinside.scan.SchemaReferenceIndex;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.DataType;
import com.bmc.arsys.api.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java port of doc/DocAlDetails.cpp, scoped per ActionSummaryTable's javadoc (action list rendered as a type summary, not fully pretty-printed). */
public final class ActiveLinkDetailPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final GlobalFieldIndex fieldIndex;
    private final FieldReferenceIndex fieldRefs;
    private final MissingFieldReferenceIndex missingFieldRefs;
    private final Set<String> knownUserNames;
    private final AppMembershipIndex appIndex;
    private final ContainerReferenceIndex containerRefs;
    private final SchemaReferenceIndex schemaRefs;
    private final RoleIndex roleIndex;
    private final Map<Integer, GroupRecord> groupsById;

    public ActiveLinkDetailPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, GlobalFieldIndex fieldIndex, FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs, Set<String> knownUserNames, AppMembershipIndex appIndex, ContainerReferenceIndex containerRefs, SchemaReferenceIndex schemaRefs, RoleIndex roleIndex, Map<Integer, GroupRecord> groupsById) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.fieldIndex = fieldIndex;
        this.fieldRefs = fieldRefs;
        this.missingFieldRefs = missingFieldRefs;
        this.knownUserNames = knownUserNames;
        this.appIndex = appIndex;
        this.containerRefs = containerRefs;
        this.schemaRefs = schemaRefs;
        this.roleIndex = roleIndex;
        this.groupsById = groupsById;
    }

    /** An AL's app = the app of any form it's attached to (first match), matching DocApplicationDetails.cpp's SearchActiveLinks. */
    private String ownerApp(List<String> formList) {
        if (formList == null) return null;
        for (String form : formList) {
            String app = appIndex.formApp(form);
            if (app != null) return app;
        }
        return null;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public ActiveLink fetch(WorkflowSource repo, String name) throws ARException {
        return repo.getActiveLink(name);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, ActiveLink al) throws ARException {
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode);
        PagePath page = Naming.activeLinkDetail(name, isOverlaid);

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String appRefName = ownerApp(al.getFormList());
        String head = URLLink.to("Active Links", Naming.activeLinkOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.ActiveLink, page.rootLevel()).toHtml() + WebUtil.objName(name)
            + ApplicationHeaderLink.suffix(appRefName, page.rootLevel());
        webPage.addContentHead(head);

        PagePath alLink = Naming.activeLinkDetail(name, false);
        QualificationRenderer.FieldReferenceSink sink = (formName, fieldId, fieldExists, detail) -> {
            // order/enabled/executeOn are populated for every Active Link reference (not just
            // Control Field/Focus Field ones) - CRefItem::GetObjectOrder/Enabled/ExecuteOn are
            // generic to the referencing object, not to the reference reason (RefItem.cpp:342-382),
            // and WorkflowReferenceTable::LinkToAlRef appends " (Order)" to every AL reference link
            // site-wide, not just the Attached Workflow table (see WorkflowRefLabel).
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(name, "Active Link", ImageTag.Id.ActiveLink, alLink, detail, al.getOrder(), al.isEnable(), executeOnSingleLine(al));
            fieldRefs.add(formName, fieldId, ref);
            if (!fieldExists) missingFieldRefs.add(formName, fieldId, ref);
        };

        webPage.addContent(generalInfo(al, name, appRefName, page.rootLevel(), sink));
        webPage.addContent(ObjectPropertiesTable.render(al.getProperties(), Set.of(Constants.AR_OPROP_INTERVAL_VALUE)));
        webPage.addContent(ServerObjectHistoryWidget.render(al, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());
    }

    /**
     * Java port of CARActiveLink::GetExecuteOn(singleLine=false, props) - the mask-derived label
     * list plus, when present, an "Interval: N" line sourced from the AR_OPROP_INTERVAL_VALUE
     * object property (AR_EXECUTE_ON_INTERVAL itself is obsolete since server 7.5+, which always
     * sets this property instead - see AREnumLabels.activeLinkExecuteOn's javadoc). This property
     * is excluded from the page's generic Object Properties table (see the ObjectPropertiesTable.render
     * call in render() above) since it's already shown here, matching the C++'s GetAndUseValue/
     * UnusedPropertiesToHTML "claim" semantics.
     */
    private String executeOn(ActiveLink al) {
        String base = AREnumLabels.activeLinkExecuteOn(al.getExecuteMask());
        if (al.getProperties() == null) return base;
        Value v = al.getProperties().get(Constants.AR_OPROP_INTERVAL_VALUE);
        if (v == null || v.getValue() == null || v.getDataType() != DataType.INTEGER) return base;
        String interval = "Interval: " + v.getIntValue();
        return "None".equals(base) ? interval : base + "<br/>" + interval;
    }

    /**
     * Java port of CARActiveLink::GetExecuteOn(singleLine=true, props) - same mask-derived label
     * list as executeOn() above, but joined with ", " instead of "<br/>", and the interval property
     * (when present) contributes the bare word "Interval" with no ": N" value - matching the C++'s
     * own singleLine-vs-multiline branching in ARActiveLink.cpp:117-135. Used only by
     * WorkflowAttached's "Execute On" column (RefItem.cpp:368's GetObjectExecuteOn() call passes
     * singleLine=true there specifically), never by the AL's own general-info page.
     */
    private String executeOnSingleLine(ActiveLink al) {
        String base = AREnumLabels.activeLinkExecuteOn(al.getExecuteMask()).replace("<br/>", ", ");
        if (al.getProperties() == null) return base;
        Value v = al.getProperties().get(Constants.AR_OPROP_INTERVAL_VALUE);
        if (v == null || v.getValue() == null || v.getDataType() != DataType.INTEGER) return base;
        return "None".equals(base) ? "Interval" : base + ", Interval";
    }

    /**
     * Java port of DocAlDetails.cpp's Documentation()/CreateSpecific() - a single flat properties
     * table (no tabs; the real C++ has no "Actions" tab at all for active links), with one
     * "[schema link] -&gt; Control Field / Focus Field / Run If / Actions" row PER form the AL is
     * attached to (an AL's IfActions/ElseActions/Qualifier/ControlField/FocusField are one shared
     * set of field IDs, but those IDs mean something different per attached form, so the C++
     * re-resolves and re-renders them once per form rather than once for a single "primary" form -
     * a real architectural difference from this port's earlier single-form rendering).
     */
    private String generalInfo(ActiveLink al, String name, String appRefName, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        Table tbl = new Table("alGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Enabled", AREnumLabels.objectEnable(al.isEnable())));
        tbl.addRow(new TableRow().addCellList("Order", Integer.toString(al.getOrder())));
        tbl.addRow(new TableRow().addCellList("Execute On", executeOn(al)));

        List<String> forms = al.getFormList();
        if (forms != null && !forms.isEmpty()) {
            for (String schemaName : forms) {
                tbl.addRow(new TableRow().addCellList(schemaLink(schemaName, rootLevel), createSpecific(al, name, schemaName, rootLevel, sink)));
            }
        } else {
            tbl.addRow(new TableRow().addCellList("No schema specified", createSpecific(al, name, "", rootLevel, sink)));
        }

        tbl.addRow(new TableRow().addCellList("Permissions", permissions(al.getGroupList(), appRefName, rootLevel)));
        tbl.addRow(new TableRow().addCellList("Container References", ContainerReferencesTable.render(containerRefs.activeLinkContainers(name), rootLevel)));
        return tbl.toXHtml();
    }

    private String schemaLink(String schemaName, int rootLevel) {
        boolean isOverlaid = fieldIndex.isOverlaid(schemaName);
        return URLLink.to(schemaName, Naming.schemaDetail(schemaName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    /** Java port of DocAlDetails.cpp's CreateSpecific() - Control Field/Focus Field links, Run If (rendered against schemaName), and the If/Else action list (rendered against schemaName). */
    private String createSpecific(ActiveLink al, String name, String schemaName, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        StringBuilder sb = new StringBuilder();
        QualificationRenderer.SchemaReferenceSink schemaSink = (targetForm, reason) -> {
            SchemaReferenceIndex.Caller caller = new SchemaReferenceIndex.Caller(name, "Active Link", al.isEnable(), al.getOrder());
            switch (reason) {
                case PUSH_FIELDS_TARGET -> schemaRefs.addPushFieldTarget(targetForm, caller);
                case SERVICE_CALL -> schemaRefs.addServiceCaller(targetForm, caller);
                case DELETE_ENTRY -> schemaRefs.addDeleteEntryCaller(targetForm, caller);
                case OPEN_WINDOW_TARGET -> schemaRefs.addOpenWindowTarget(targetForm, caller);
            }
        };
        QualificationRenderer renderer = new QualificationRenderer(schemaName, rootLevel, fieldIndex, sink, schemaSink);

        if (al.getControlField() > 0) {
            sb.append("Control Field: ").append(renderer.fieldRef(schemaName, al.getControlField(), "Control Field")).append("<br/><br/>\n");
        }
        if (al.getFocusField() > 0) {
            sb.append("Focus Field: ").append(renderer.fieldRef(schemaName, al.getFocusField(), "Focus Field")).append("<br/><br/>\n");
        }

        String qual = al.getQualifier() != null && al.getQualifier().getOperation() != com.bmc.arsys.api.QualifierInfo.AR_COND_OP_NONE
            ? renderer.render(al.getQualifier(), "Run If")
            : "No qualification specified";
        sb.append("Run If Qualification: <br/>").append(qual);

        sb.append(ActionSummaryTable.render(al.getActionList(), al.getElseList(),
            ActionSummaryTable.activeLinkTypeOf(), ActionSummaryTable.activeLinkLabel(), schemaName, renderer, appConfig.serverName));
        return sb.toString();
    }

    /**
     * Java port of DocAlDetails.cpp's Permissions() - the real CGroupTable rendering (Name/ID/Type/
     * Category/Modified/By, or the Role-row variant for negative ids), previously simplified here to
     * a bare `groupRef()` link list with a hardcoded "Group N"/"Role N" fallback text regardless of
     * the group/role's real name (see GroupPermissionTable's own javadoc for the full C++ shape).
     */
    private String permissions(List<Integer> groupIds, String appRefName, int rootLevel) {
        return GroupPermissionTable.render("groupList", groupIds, appRefName, roleIndex, groupsById, knownUserNames, rootLevel).toXHtml();
    }
}
