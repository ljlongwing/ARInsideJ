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

/** Java port of CDocMain::FilterActionList/FilterActionDetails - see ActiveLinkActionPage's javadoc for the shared design notes (fetch-once, row shape, tallying) that apply here too. */
public final class FilterActionPage {
    private static final int FIRST_ACTION = Constants.AR_FILTER_ACTION_NOTIFY;
    private static final int LAST_ACTION = Constants.AR_FILTER_ACTION_SERVICE;

    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public FilterActionPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public void render() throws ARException {
        List<Filter> filters = new ArrayList<>();
        for (String name : repo.listFilterNames()) {
            try {
                Filter flt = repo.getFilter(name);
                if (OverlaySupport.isVisible(flt.getProperties(), serverOverlayMode, appConfig.overlaySupport)) {
                    filters.add(flt);
                }
            } catch (ARException e) {
                System.out.println("EXCEPTION FilterActionList '" + name + "': " + e.getMessage());
            }
        }

        PagePath page = Naming.filterActionOverview();
        Table tbl = new Table("filterList", "TblObjectList");
        tbl.description = URLLink.to("Filter", Naming.overviewFilters(), ImageTag.Id.Filter, page.rootLevel()).toHtml()
            + " with a specified action in If/Else list:";
        tbl.addColumn(100, "Filter Action (Items count if/else)");

        for (int actionType = FIRST_ACTION; actionType <= LAST_ACTION; actionType++) {
            int[] counts = detail(actionType, filters, page.rootLevel());
            TableRow row = new TableRow();
            row.addCell(URLLink.to(AREnumLabels.filterActionType(actionType), Naming.filterActionDetail(actionType), ImageTag.Id.Document, page.rootLevel()).toHtml()
                + " (" + counts[0] + "/" + counts[1] + ")");
            tbl.addRow(row);
        }

        WebPage webPage = new WebPage(page.fileName(), "Filter Actions", page.rootLevel(), appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }

    private int[] detail(int actionType, List<Filter> filters, int rootLevel) {
        PagePath page = Naming.filterActionDetail(actionType);

        Table tbl = new Table("filterList", "TblObjectList");
        tbl.description = URLLink.to("Filter", Naming.filterActionOverview(), ImageTag.Id.Filter, rootLevel).toHtml()
            + " with " + AREnumLabels.filterActionType(actionType) + " action";
        tbl.addColumn(25, "Filter Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Order");
        tbl.addColumn(24, "Execute On");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(10, "By");

        int ifCount = 0, elseCount = 0, rows = 0;
        for (Filter flt : filters) {
            int ifHits = count(flt.getActionList(), actionType);
            int elseHits = count(flt.getElseList(), actionType);
            ifCount += ifHits;
            elseCount += elseHits;
            if (ifHits + elseHits == 0) continue;

            boolean isOverlaid = OverlaySupport.isOverlaidForNaming(flt.getProperties(), serverOverlayMode);
            TableRow row = new TableRow();
            row.addCell(URLLink.to(flt.getName(), Naming.filterDetail(flt.getName(), isOverlaid), ImageTag.Id.Filter, rootLevel).toHtml());
            row.addCell(new TableCell(AREnumLabels.objectEnable(flt.isEnable()), flt.isEnable() ? "" : "objStatusDisabled"));
            row.addCell(new TableCell(flt.getOrder()));
            row.addCell(String.join(", ", flt.getFormList()));
            row.addCell(new TableCell(flt.getActionList() == null ? 0 : flt.getActionList().size()));
            row.addCell(new TableCell(flt.getElseList() == null ? 0 : flt.getElseList().size()));
            row.addCell(DateTimeFormat.toHtmlString(flt.getLastUpdateTime().getValue()));
            row.addCell(flt.getLastChangedBy());
            tbl.addRow(row);
            rows++;
        }
        if (rows > 0) tbl.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Filter Actions", rootLevel, appConfig);
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
