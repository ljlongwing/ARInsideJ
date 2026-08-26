package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.List;

/** Java port of CDocMain::EscalationActionList/EscalationActionDetails - Escalation actions reuse the Filter action type range/struct (see the C++'s DocMain.cpp, which types esc.GetIfActions() as ARFilterActionList) - see ActiveLinkActionPage's javadoc for the shared design notes that apply here too. */
public final class EscalationActionPage {
    private static final int FIRST_ACTION = Constants.AR_FILTER_ACTION_NOTIFY;
    private static final int LAST_ACTION = Constants.AR_FILTER_ACTION_SERVICE;

    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public EscalationActionPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public void render() throws ARException {
        List<Escalation> escalations = new ArrayList<>();
        for (String name : repo.listEscalationNames()) {
            try {
                Escalation esc = repo.getEscalation(name);
                if (OverlaySupport.isVisible(esc.getProperties(), serverOverlayMode, appConfig.overlaySupport)) {
                    escalations.add(esc);
                }
            } catch (ARException e) {
                System.out.println("EXCEPTION EscalationActionList '" + name + "': " + e.getMessage());
            }
        }

        PagePath page = Naming.escalationActionOverview();
        Table tbl = new Table("escalationList", "TblObjectList");
        tbl.description = URLLink.to("Escalations", Naming.overviewEscalations(), ImageTag.Id.Escalation, page.rootLevel()).toHtml()
            + " with a specified action in If/Else list:";
        tbl.addColumn(100, "Escalation Action (Items count if/else)");

        for (int actionType = FIRST_ACTION; actionType <= LAST_ACTION; actionType++) {
            int[] counts = detail(actionType, escalations, page.rootLevel());
            TableRow row = new TableRow();
            row.addCell(URLLink.to(AREnumLabels.filterActionType(actionType), Naming.escalationActionDetail(actionType), ImageTag.Id.Document, page.rootLevel()).toHtml()
                + " (" + counts[0] + "/" + counts[1] + ")");
            tbl.addRow(row);
        }

        WebPage webPage = new WebPage(page.fileName(), "Escalation Actions", page.rootLevel(), appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private int[] detail(int actionType, List<Escalation> escalations, int rootLevel) {
        PagePath page = Naming.escalationActionDetail(actionType);

        Table tbl = new Table("escalationList", "TblObjectList");
        tbl.description = URLLink.to("Escalations", Naming.escalationActionOverview(), ImageTag.Id.Escalation, rootLevel).toHtml()
            + " with " + AREnumLabels.filterActionType(actionType) + " action";
        tbl.addColumn(25, "Escalation Name");
        tbl.addColumn(10, "Enabled");
        tbl.addColumn(25, "Execute On");
        tbl.addColumn(7, "If");
        tbl.addColumn(7, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(13, "By");

        int ifCount = 0, elseCount = 0, rows = 0;
        for (Escalation esc : escalations) {
            int ifHits = count(esc.getActionList(), actionType);
            int elseHits = count(esc.getElseList(), actionType);
            ifCount += ifHits;
            elseCount += elseHits;
            if (ifHits + elseHits == 0) continue;

            boolean isOverlaid = OverlaySupport.isOverlaidForNaming(esc.getProperties(), serverOverlayMode);
            TableRow row = new TableRow();
            row.addCell(URLLink.to(esc.getName(), Naming.escalationDetail(esc.getName(), isOverlaid), ImageTag.Id.Escalation, rootLevel).toHtml());
            row.addCell(new TableCell(AREnumLabels.objectEnable(esc.isEnable()), esc.isEnable() ? "" : "objStatusDisabled"));
            row.addCell(String.join(", ", esc.getFormList()));
            row.addCell(new TableCell(esc.getActionList() == null ? 0 : esc.getActionList().size()));
            row.addCell(new TableCell(esc.getElseList() == null ? 0 : esc.getElseList().size()));
            row.addCell(DateTimeFormat.toHtmlString(esc.getLastUpdateTime().getValue()));
            row.addCell(esc.getLastChangedBy());
            tbl.addRow(row);
            rows++;
        }
        if (rows > 0) tbl.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Escalation Actions", rootLevel, appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());

        return new int[]{ifCount, elseCount};
    }

    private static int count(List<FilterAction> actions, int actionType) {
        if (actions == null) return 0;
        int n = 0;
        for (FilterAction a : actions) {
            if (Action.getActionType((Action) a, false) == actionType) n++;
        }
        return n;
    }
}
