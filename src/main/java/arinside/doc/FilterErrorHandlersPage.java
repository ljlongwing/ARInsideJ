package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.FilterErrorHandlerIndex;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Filter;

/** Java port of CDocMain::FilterErrorHandlers - the filters actually named as another filter's error handler, sourced from FilterErrorHandlerIndex's scan pass. Row shape matches this port's own FilterOverviewPage. */
public final class FilterErrorHandlersPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final FilterErrorHandlerIndex errorHandlers;

    public FilterErrorHandlersPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, FilterErrorHandlerIndex errorHandlers) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.errorHandlers = errorHandlers;
    }

    public void render() throws ARException {
        PagePath page = Naming.filterErrorHandlers();

        Table tbl = new Table("filterList", "TblObjectList");
        tbl.addColumn(25, "Filter Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Order");
        tbl.addColumn(24, "Execute On");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(10, "By");

        int rows = 0;
        for (String name : repo.listFilterNames()) {
            try {
                Filter flt = repo.getFilter(name);
                if (!OverlaySupport.isVisible(flt.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                if (!errorHandlers.isUsedAsErrorHandler(name)) continue;

                PagePath detail = Naming.filterDetail(name, OverlaySupport.isOverlaidForNaming(flt.getProperties(), serverOverlayMode));
                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, ImageTag.Id.Filter, page.rootLevel()).toHtml());
                row.addCell(new TableCell(AREnumLabels.objectEnable(flt.isEnable()), flt.isEnable() ? "" : "objStatusDisabled"));
                row.addCell(new TableCell(flt.getOrder()));
                row.addCell(String.join(", ", flt.getFormList()));
                row.addCell(new TableCell(flt.getActionList() == null ? 0 : flt.getActionList().size()));
                row.addCell(new TableCell(flt.getElseList() == null ? 0 : flt.getElseList().size()));
                row.addCell(DateTimeFormat.toHtmlString(flt.getLastUpdateTime().getValue()));
                row.addCell(flt.getLastChangedBy());
                tbl.addRow(row);
                rows++;
            } catch (ARException e) {
                System.out.println("EXCEPTION FilterErrorHandlers '" + name + "': " + e.getMessage());
            }
        }
        if (rows > 0) tbl.removeEmptyMessageRow();
        tbl.description = rows + " " + URLLink.to("Filters", Naming.overviewFilters(), ImageTag.Id.Filter, page.rootLevel()).toHtml() + " used as Error Handler";

        WebPage webPage = new WebPage(page.fileName(), "Filter Error Handlers", page.rootLevel(), appConfig);
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }
}
