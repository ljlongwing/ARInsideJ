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

/**
 * Java port of CDocMain::ActiveLinkActionList/ActiveLinkActionDetails (doc/DocMain.cpp) - one
 * landing page (overview/actlinks_action.htm) linking to a per-action-type breakdown page (one of
 * overview/actlinks_action_1.htm..18.htm), each listing every Active Link that uses that action
 * type anywhere in its If or Else list, plus how many total if/else hits that action type has
 * across every Active Link on the server.
 *
 * Row shape matches this port's own ActiveLinkOverviewPage (not the C++'s wider CAlTable, which
 * additionally has a Groups column - see ActiveLinkOverviewPage's own javadoc for that pre-existing,
 * unrelated scope note; this stays self-consistent with the rest of the port instead of chasing 1:1
 * C++ column parity nothing else here has either).
 *
 * Every Active Link is fetched once up front and reused across all 18 action-type passes, rather
 * than the C++'s approach of re-scanning the object list once per action type - same "getForm()
 * over one full pass" tradeoff already accepted elsewhere in this port (GlobalFieldIndex etc.),
 * just amortized instead of repeated.
 */
public final class ActiveLinkActionPage {
    private static final int FIRST_ACTION = Constants.AR_ACTIVE_LINK_ACTION_MACRO;
    private static final int LAST_ACTION = Constants.AR_ACTIVE_LINK_ACTION_SERVICE;

    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public ActiveLinkActionPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public void render() throws ARException {
        List<ActiveLink> links = new ArrayList<>();
        for (String name : repo.listActiveLinkNames()) {
            try {
                ActiveLink al = repo.getActiveLink(name);
                if (OverlaySupport.isVisible(al.getProperties(), serverOverlayMode, appConfig.overlaySupport)) {
                    links.add(al);
                }
            } catch (ARException e) {
                System.out.println("EXCEPTION ActiveLinkActionList '" + name + "': " + e.getMessage());
            }
        }

        PagePath page = Naming.activeLinkActionOverview();
        Table tbl = new Table("alList", "TblObjectList");
        tbl.description = URLLink.to("Active Links", Naming.overviewActiveLinks(), ImageTag.Id.ActiveLink, page.rootLevel()).toHtml()
            + " with a specified action in If/Else list:";
        tbl.addColumn(100, "Active Link Action (Items count if/else)");

        for (int actionType = FIRST_ACTION; actionType <= LAST_ACTION; actionType++) {
            int[] counts = detail(actionType, links, page.rootLevel());
            StringBuilder cell = new StringBuilder(URLLink.to(AREnumLabels.activeLinkActionType(actionType), Naming.activeLinkActionDetail(actionType), ImageTag.Id.Document, page.rootLevel()).toHtml()
                + " (" + counts[0] + "/" + counts[1] + ")");
            if (actionType == Constants.AR_ACTIVE_LINK_ACTION_FIELDS) {
                for (SetFieldsSubtype st : SetFieldsSubtype.values()) {
                    int[] sub = detailSubtype(actionType, st, links, page.rootLevel());
                    if (sub[0] + sub[1] == 0) continue;
                    cell.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;&#8627; ")
                        .append(URLLink.to(st.label(), Naming.activeLinkActionSubtypeDetail(actionType, st.key()), ImageTag.Id.Document, page.rootLevel()).toHtml())
                        .append(" (").append(sub[0]).append('/').append(sub[1]).append(')');
                }
            }
            TableRow row = new TableRow();
            row.addCell(cell.toString());
            tbl.addRow(row);
        }

        WebPage webPage = new WebPage(page.fileName(), "Active Link Actions", page.rootLevel(), appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    /** Writes the per-action-type detail page, returns {ifCount, elseCount} tallied across every Active Link. */
    private int[] detail(int actionType, List<ActiveLink> links, int rootLevel) {
        PagePath page = Naming.activeLinkActionDetail(actionType);

        Table tbl = new Table("alList", "TblObjectList");
        tbl.description = URLLink.to("Active Links", Naming.activeLinkActionOverview(), ImageTag.Id.ActiveLink, rootLevel).toHtml()
            + " with " + AREnumLabels.activeLinkActionType(actionType) + " action";
        tbl.addColumn(25, "Active Link Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Order");
        tbl.addColumn(24, "Execute On");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(10, "By");

        int ifCount = 0, elseCount = 0, rows = 0;
        for (ActiveLink al : links) {
            int ifHits = count(al.getActionList(), actionType);
            int elseHits = count(al.getElseList(), actionType);
            ifCount += ifHits;
            elseCount += elseHits;
            if (ifHits + elseHits == 0) continue;

            boolean isOverlaid = OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode);
            TableRow row = new TableRow();
            row.addCell(URLLink.to(al.getName(), Naming.activeLinkDetail(al.getName(), isOverlaid), ImageTag.Id.ActiveLink, rootLevel).toHtml());
            row.addCell(new TableCell(AREnumLabels.objectEnable(al.isEnable()), al.isEnable() ? "" : "objStatusDisabled"));
            row.addCell(new TableCell(al.getOrder()));
            row.addCell(String.join(", ", al.getFormList()));
            row.addCell(new TableCell(al.getActionList() == null ? 0 : al.getActionList().size()));
            row.addCell(new TableCell(al.getElseList() == null ? 0 : al.getElseList().size()));
            row.addCell(DateTimeFormat.toHtmlString(al.getLastUpdateTime().getValue()));
            row.addCell(al.getLastChangedBy());
            tbl.addRow(row);
            rows++;
        }
        if (rows > 0) tbl.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Active Link Actions", rootLevel, appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());

        return new int[]{ifCount, elseCount};
    }

    /** Like {@link #detail} but restricted to Set Fields actions of one {@link SetFieldsSubtype}. The page is written only when at least one active link matches, since the overview links to it only then. */
    private int[] detailSubtype(int actionType, SetFieldsSubtype st, List<ActiveLink> links, int rootLevel) {
        PagePath page = Naming.activeLinkActionSubtypeDetail(actionType, st.key());

        Table tbl = new Table("alList", "TblObjectList");
        tbl.description = URLLink.to("Active Links", Naming.activeLinkActionOverview(), ImageTag.Id.ActiveLink, rootLevel).toHtml()
            + " with " + AREnumLabels.activeLinkActionType(actionType) + " action (" + st.label() + ")";
        tbl.addColumn(25, "Active Link Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Order");
        tbl.addColumn(24, "Execute On");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(10, "By");

        int ifCount = 0, elseCount = 0, rows = 0;
        for (ActiveLink al : links) {
            int ifHits = countSubtype(al.getActionList(), st);
            int elseHits = countSubtype(al.getElseList(), st);
            ifCount += ifHits;
            elseCount += elseHits;
            if (ifHits + elseHits == 0) continue;

            boolean isOverlaid = OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode);
            TableRow row = new TableRow();
            row.addCell(URLLink.to(al.getName(), Naming.activeLinkDetail(al.getName(), isOverlaid), ImageTag.Id.ActiveLink, rootLevel).toHtml());
            row.addCell(new TableCell(AREnumLabels.objectEnable(al.isEnable()), al.isEnable() ? "" : "objStatusDisabled"));
            row.addCell(new TableCell(al.getOrder()));
            row.addCell(String.join(", ", al.getFormList()));
            row.addCell(new TableCell(al.getActionList() == null ? 0 : al.getActionList().size()));
            row.addCell(new TableCell(al.getElseList() == null ? 0 : al.getElseList().size()));
            row.addCell(DateTimeFormat.toHtmlString(al.getLastUpdateTime().getValue()));
            row.addCell(al.getLastChangedBy());
            tbl.addRow(row);
            rows++;
        }
        if (rows == 0) return new int[]{0, 0};
        tbl.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Active Link Actions", rootLevel, appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());

        return new int[]{ifCount, elseCount};
    }

    private static int count(List<ActiveLinkAction> actions, int actionType) {
        if (actions == null) return 0;
        int n = 0;
        for (ActiveLinkAction a : actions) {
            if (Action.getActionType((Action) a, true) == actionType) n++;
        }
        return n;
    }

    private static int countSubtype(List<ActiveLinkAction> actions, SetFieldsSubtype st) {
        if (actions == null) return 0;
        int n = 0;
        for (ActiveLinkAction a : actions) {
            if (a instanceof SetFieldsAction sf && SetFieldsSubtype.of(sf) == st) n++;
        }
        return n;
    }
}
