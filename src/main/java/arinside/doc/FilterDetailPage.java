package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.AppMembershipIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.FilterErrorHandlerIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;
import arinside.scan.SchemaReferenceIndex;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Filter;

import java.util.List;
import java.util.Set;

/** Java port of doc/DocFilterDetails.cpp, scoped per ActionSummaryTable's javadoc. */
public final class FilterDetailPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final GlobalFieldIndex fieldIndex;
    private final FieldReferenceIndex fieldRefs;
    private final MissingFieldReferenceIndex missingFieldRefs;
    private final Set<String> knownUserNames;
    private final AppMembershipIndex appIndex;
    private final ContainerReferenceIndex containerRefs;
    private final FilterErrorHandlerIndex errorHandlers;
    private final SchemaReferenceIndex schemaRefs;

    public FilterDetailPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, GlobalFieldIndex fieldIndex, FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs, Set<String> knownUserNames, AppMembershipIndex appIndex, ContainerReferenceIndex containerRefs, FilterErrorHandlerIndex errorHandlers, SchemaReferenceIndex schemaRefs) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.fieldIndex = fieldIndex;
        this.fieldRefs = fieldRefs;
        this.missingFieldRefs = missingFieldRefs;
        this.knownUserNames = knownUserNames;
        this.appIndex = appIndex;
        this.containerRefs = containerRefs;
        this.errorHandlers = errorHandlers;
        this.schemaRefs = schemaRefs;
    }

    /** A filter's app = the app of any form it's attached to (first match), matching DocApplicationDetails.cpp's SearchFilters. */
    private String ownerApp(List<String> formList) {
        if (formList == null) return null;
        for (String form : formList) {
            String app = appIndex.formApp(form);
            if (app != null) return app;
        }
        return null;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public Filter fetch(WorkflowSource repo, String name) throws ARException {
        return repo.getFilter(name);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, Filter filter) throws ARException {
        PagePath page = Naming.filterDetail(name, OverlaySupport.isOverlaidForNaming(filter.getProperties(), serverOverlayMode));

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String head = URLLink.to("Filters", Naming.filterOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Filter, page.rootLevel()).toHtml() + WebUtil.objName(name)
            + ApplicationHeaderLink.suffix(ownerApp(filter.getFormList()), page.rootLevel());
        webPage.addContentHead(head);

        PagePath filterLink = Naming.filterDetail(name, false);
        QualificationRenderer.FieldReferenceSink sink = (formName, fieldId, fieldExists, detail) -> {
            // order populated so WorkflowRefLabel can append " (Order)" to this reference's link,
            // matching WorkflowReferenceTable::LinkToFilterRef. enabled is populated too - Filter is
            // one of the three types (Filter/Active Link/Escalation) RefItem::GetObjectEnabled sets
            // supportsEnabled=true for, so the general Workflow Reference table (not just the
            // Active-Link-only Attached Workflow one) shows an Enabled cell for Filter rows.
            // executeOn stays unset - unused by the general table (Execute On is
            // Attached-Workflow-only, and Filters never appear there).
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(name, "Filter", ImageTag.Id.Filter, filterLink, detail, filter.getOrder(), filter.isEnable(), null);
            fieldRefs.add(formName, fieldId, ref);
            if (!fieldExists) missingFieldRefs.add(formName, fieldId, ref);
        };

        webPage.addContent(generalInfo(filter, name, page.rootLevel(), sink));
        webPage.addContent(workflowReferences(name, page.rootLevel()));
        webPage.addContent(ObjectPropertiesTable.render(filter.getProperties()));
        webPage.addContent(ServerObjectHistoryWidget.render(filter, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());
    }

    /**
     * Java port of DocFilterDetails.cpp's Documentation()/CreateSpecific() - a single flat
     * properties table (no tabs; the real C++ has no separate "Actions" tab for filters), with one
     * "[schema link] -&gt; Run If / Actions" row PER form the filter is attached to, matching the
     * same per-form re-resolution ActiveLinkDetailPage now does (see its javadoc) - a filter's
     * IfActions/ElseActions/Qualifier are one shared set of field IDs that mean something different
     * per attached form.
     */
    private String generalInfo(Filter filter, String name, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        Table tbl = new Table("filterGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Enabled", AREnumLabels.objectEnable(filter.isEnable())));
        tbl.addRow(new TableRow().addCellList("Order", Integer.toString(filter.getOrder())));
        tbl.addRow(new TableRow().addCellList("Execute On", AREnumLabels.filterExecuteOn(filter.getOpSet())));
        tbl.addRow(new TableRow().addCellList("Error Handler", errorHandlerCell(filter)));

        List<String> forms = filter.getFormList();
        if (forms != null && !forms.isEmpty()) {
            for (String schemaName : forms) {
                tbl.addRow(new TableRow().addCellList(schemaLink(schemaName, rootLevel), createSpecific(filter, name, schemaName, rootLevel, sink)));
            }
        } else {
            tbl.addRow(new TableRow().addCellList("No schema specified", createSpecific(filter, name, "", rootLevel, sink)));
        }

        tbl.addRow(new TableRow().addCellList("Container References", ContainerReferencesTable.render(containerRefs.filterContainers(name), rootLevel)));
        return tbl.toXHtml();
    }

    private String schemaLink(String schemaName, int rootLevel) {
        boolean isOverlaid = fieldIndex.isOverlaid(schemaName);
        return URLLink.to(schemaName, Naming.schemaDetail(schemaName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    /** Java port of DocFilterDetails.cpp's CreateSpecific() - Run If (rendered against schemaName) and the If/Else action list (rendered against schemaName). */
    private String createSpecific(Filter filter, String name, String schemaName, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        StringBuilder sb = new StringBuilder();
        QualificationRenderer.SchemaReferenceSink schemaSink = (targetForm, reason) -> {
            SchemaReferenceIndex.Caller caller = new SchemaReferenceIndex.Caller(name, "Filter", filter.isEnable(), filter.getOrder());
            switch (reason) {
                case PUSH_FIELDS_TARGET -> schemaRefs.addPushFieldTarget(targetForm, caller);
                case SERVICE_CALL -> schemaRefs.addServiceCaller(targetForm, caller);
                case DELETE_ENTRY -> schemaRefs.addDeleteEntryCaller(targetForm, caller);
                case OPEN_WINDOW_TARGET -> { /* OpenWindowAction is Active-Link-only; unreachable for Filters, matching the C++'s AlWindowOpenReferences() being Active-Link-only too */ }
            }
        };
        QualificationRenderer renderer = new QualificationRenderer(schemaName, rootLevel, fieldIndex, sink, schemaSink);

        String qual = filter.getQualifier() != null && filter.getQualifier().getOperation() != com.bmc.arsys.api.QualifierInfo.AR_COND_OP_NONE
            ? renderer.render(filter.getQualifier())
            : "No qualification specified";
        sb.append("Run If Qualification: <br/>").append(qual);

        sb.append(ActionSummaryTable.render(filter.getActionList(), filter.getElseList(),
            ActionSummaryTable.filterTypeOf(), ActionSummaryTable.filterLabel(), schemaName, renderer, appConfig.serverName));
        return sb.toString();
    }

    /**
     * Java port of DocFilterDetails.cpp's WorkflowReferences() (227-280) - every OTHER filter that
     * selected this one as its Error Handler, sourced from FilterErrorHandlerIndex (a port of
     * CARFilter::ErrorCallers()/scan/ScanFilters.cpp; see FilterErrorHandlerIndex's own javadoc).
     * Link text carries the same " (Order)" suffix WorkflowReferenceTable::LinkToFilterRef appends
     * to every Filter reference (see WorkflowRefLabel).
     */
    private String workflowReferences(String filterName, int rootLevel) {
        List<FilterErrorHandlerIndex.Caller> callers = errorHandlers.callers(filterName);
        if (callers.isEmpty()) return "";
        Table tbl = new Table("referenceList", "TblObjectList");
        tbl.description = "Workflow Reference:";
        tbl.addColumn(10, "Type");
        tbl.addColumn(45, "Server object");
        tbl.addColumn(5, "Enabled");
        tbl.addColumn(40, "Description");
        for (FilterErrorHandlerIndex.Caller caller : callers) {
            tbl.addRow(new TableRow().addCellList("Filter",
                URLLink.to(caller.name(), Naming.filterDetail(caller.name(), false), ImageTag.Id.Filter, rootLevel).toHtml() + WorkflowRefLabel.orderSuffix("Filter", caller.order()),
                AREnumLabels.objectEnable(caller.enabled()),
                "Selected as Error Handler"));
        }
        return tbl.toXHtml();
    }

    /** Java port of DocFilterDetails.cpp's "Error Handler" row - links to the target filter (matching this file's existing self-referencing links, isOverlaid=false is a pre-existing simplification here, not new) when errorFilterOptions is enabled, "None" otherwise. */
    private String errorHandlerCell(Filter filter) {
        if (filter.getErrorFilterOptions() != com.bmc.arsys.api.Constants.AR_ERRHANDLER_ENABLE) return "None";
        String handlerName = filter.getErrorHandlingFilter();
        if (handlerName == null || handlerName.isEmpty()) return "None";
        int rootLevel = Naming.filterDetail(handlerName, false).rootLevel();
        return URLLink.to(handlerName, Naming.filterDetail(handlerName, false), ImageTag.Id.Filter, rootLevel).toHtml();
    }
}
