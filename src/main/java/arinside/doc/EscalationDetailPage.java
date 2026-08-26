package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.AppMembershipIndex;
import arinside.scan.ContainerReferenceIndex;
import arinside.scan.FieldReferenceIndex;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.MissingFieldReferenceIndex;
import arinside.scan.SchemaReferenceIndex;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.EscalationInterval;
import com.bmc.arsys.api.EscalationTime;
import com.bmc.arsys.api.Value;

import java.util.List;
import java.util.Set;

/** Java port of doc/DocEscalationDetails.cpp, scoped per ActionSummaryTable's javadoc. */
public final class EscalationDetailPage {
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

    public EscalationDetailPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, GlobalFieldIndex fieldIndex, FieldReferenceIndex fieldRefs, MissingFieldReferenceIndex missingFieldRefs, Set<String> knownUserNames, AppMembershipIndex appIndex, ContainerReferenceIndex containerRefs, SchemaReferenceIndex schemaRefs) {
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
    }

    /** An escalation's app = the app of any form it's attached to (first match), matching DocApplicationDetails.cpp's SearchEscalations. */
    private String ownerApp(List<String> formList) {
        if (formList == null) return null;
        for (String form : formList) {
            String app = appIndex.formApp(form);
            if (app != null) return app;
        }
        return null;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public Escalation fetch(WorkflowSource repo, String name) throws ARException {
        return repo.getEscalation(name);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, Escalation esc) throws ARException {
        PagePath page = Naming.escalationDetail(name, OverlaySupport.isOverlaidForNaming(esc.getProperties(), serverOverlayMode));

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String head = URLLink.to("Escalations", Naming.escalationOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Escalation, page.rootLevel()).toHtml() + WebUtil.objName(name)
            + ApplicationHeaderLink.suffix(ownerApp(esc.getFormList()), page.rootLevel());
        webPage.addContentHead(head);

        PagePath escLink = Naming.escalationDetail(name, false);
        QualificationRenderer.FieldReferenceSink sink = (formName, fieldId, fieldExists, detail) -> {
            // enabled populated (order/executeOn stay null - RefItem::GetObjectOrder only supports
            // Active Link/Filter, defaulting -1/unset for everything else including Escalation) -
            // Escalation is one of the three types RefItem::GetObjectEnabled sets supportsEnabled=
            // true for, so the real tool's general Workflow Reference table shows an Enabled cell
            // for Escalation rows too - previously left null here (a real gap, confirmed via live
            // comparison against DocFieldDetails.cpp's field showing a blank Enabled column).
            FieldReferenceIndex.Ref ref = new FieldReferenceIndex.Ref(name, "Escalation", ImageTag.Id.Escalation, escLink, detail, null, esc.isEnable(), null);
            fieldRefs.add(formName, fieldId, ref);
            if (!fieldExists) missingFieldRefs.add(formName, fieldId, ref);
        };

        webPage.addContent(generalInfo(esc, name, page.rootLevel(), sink));
        // Java port of Documentation()'s props.UnusedPropertiesToHTML(rootLevel) - previously
        // missing entirely. No exclude-set: GetPoolStr()/GetPool() read AR_OPROP_POOL_NUMBER via
        // plain GetValue (not GetAndUseValue), so the real C++ never marks it "claimed" either -
        // it genuinely shows Pool Number a second time here too when set, confirmed by reading
        // AREscalation.cpp directly rather than assumed; matching that exactly, not "cleaning it up".
        webPage.addContent(ObjectPropertiesTable.render(esc.getProperties()));
        webPage.addContent(ServerObjectHistoryWidget.render(esc, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());
    }

    /**
     * Java port of DocEscalationDetails.cpp's Documentation()/CreateSpecific() - a single flat
     * properties table (no tabs; the real C++ has no separate "Actions" tab for escalations), with
     * one "[schema link] -&gt; Run If / Actions" row PER form the escalation is attached to, matching
     * the same per-form re-resolution ActiveLinkDetailPage/FilterDetailPage already do. There is no
     * real "Execute On" concept for escalations (the earlier row here showing the raw form list under
     * that label didn't correspond to anything in the real C++'s Documentation(), which has no such
     * row at all - CAREscalation::GetExecuteOn() exists but is never called from the doc page).
     */
    private String generalInfo(Escalation esc, String name, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        Table tbl = new Table("escalationGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Enabled", AREnumLabels.objectEnable(esc.isEnable())));

        String pool = poolNumber(esc);
        if (!pool.isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Pool Number", pool));
        }
        tbl.addRow(new TableRow().addCellList("Time Criteria", timeCriteria(esc)));

        List<String> forms = esc.getFormList();
        if (forms != null && !forms.isEmpty()) {
            for (String schemaName : forms) {
                tbl.addRow(new TableRow().addCellList(schemaLink(schemaName, rootLevel), createSpecific(esc, name, schemaName, rootLevel, sink)));
            }
        } else {
            tbl.addRow(new TableRow().addCellList("No schema specified", createSpecific(esc, name, "", rootLevel, sink)));
        }

        tbl.addRow(new TableRow().addCellList("Container References", ContainerReferencesTable.render(containerRefs.escalationContainers(name), rootLevel)));
        return tbl.toXHtml();
    }

    private String schemaLink(String schemaName, int rootLevel) {
        boolean isOverlaid = fieldIndex.isOverlaid(schemaName);
        return URLLink.to(schemaName, Naming.schemaDetail(schemaName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    /** Java port of DocEscalationDetails.cpp's CreateSpecific() - Run If (rendered against schemaName) and the If/Else action list (rendered against schemaName, via the same CDocFilterActionStruct/ActionSummaryTable.filterTypeOf() reuse the real C++ itself uses for escalation actions). */
    private String createSpecific(Escalation esc, String name, String schemaName, int rootLevel, QualificationRenderer.FieldReferenceSink sink) {
        StringBuilder sb = new StringBuilder();
        QualificationRenderer.SchemaReferenceSink schemaSink = (targetForm, reason) -> {
            SchemaReferenceIndex.Caller caller = new SchemaReferenceIndex.Caller(name, "Escalation", esc.isEnable());
            switch (reason) {
                case PUSH_FIELDS_TARGET -> schemaRefs.addPushFieldTarget(targetForm, caller);
                case SERVICE_CALL -> schemaRefs.addServiceCaller(targetForm, caller);
                case DELETE_ENTRY -> schemaRefs.addDeleteEntryCaller(targetForm, caller);
                case OPEN_WINDOW_TARGET -> { /* OpenWindowAction is Active-Link-only; unreachable for Escalations */ }
            }
        };
        QualificationRenderer renderer = new QualificationRenderer(schemaName, rootLevel, fieldIndex, sink, schemaSink);

        String qual = esc.getQualifier() != null && esc.getQualifier().getOperation() != com.bmc.arsys.api.QualifierInfo.AR_COND_OP_NONE
            ? renderer.render(esc.getQualifier())
            : "No qualification specified";
        sb.append("Run If Qualification: <br/>").append(qual);

        sb.append(ActionSummaryTable.render(esc.getActionList(), esc.getElseList(),
            ActionSummaryTable.filterTypeOf(), ActionSummaryTable.filterLabel(), schemaName, renderer, appConfig.serverName));
        return sb.toString();
    }

    /**
     * Java port of CAREscalation::GetPoolStr() - AR_OPROP_POOL_NUMBER object property, server 7.1+
     * only, "" (no row) when unset/zero. Value.getIntValue() NPEs when the property exists but its
     * underlying value is null (confirmed via a real escalation on the live test server,
     * SRM:SRV:TriggerSurveyNotification) - getValue() itself is checked for null first instead of
     * relying on getIntValue()'s internal unboxing.
     */
    private String poolNumber(Escalation esc) {
        if (esc.getProperties() == null) return "";
        Value v = esc.getProperties().get(Constants.AR_OPROP_POOL_NUMBER);
        if (v == null || v.getValue() == null) return "";
        int pool = v.getIntValue();
        return pool > 0 ? Integer.toString(pool) : "";
    }

    /**
     * Java port of CAREscalation::GetTimeCriteria() - interval type always renders all three units
     * unconditionally ("{d} Days {h} Hours {m} Minutes", matching the real C++'s unconditional
     * stream write, not a zero-suppressed "Every Xd Yh Zm" summary); calendar/date type delegates to
     * ScheduleFormat.calendar (CARDayStructHelper::DayStructToHTMLString).
     */
    private String timeCriteria(Escalation esc) {
        Object tm = esc.getEscalationTm();
        if (tm instanceof EscalationInterval interval) {
            return interval.getDays() + " Days " + interval.getHours() + " Hours " + interval.getMinutes() + " Minutes";
        }
        if (tm instanceof EscalationTime cal) {
            return ScheduleFormat.calendar(cal);
        }
        return "Unknown Escalation type";
    }
}
