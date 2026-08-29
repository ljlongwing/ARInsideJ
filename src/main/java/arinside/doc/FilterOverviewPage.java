package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Filter;

import java.util.List;

/**
 * Java port of CDocMain::FilterList/FilterListJson + output/FilterTable.{h,cpp} - includes the
 * jQuery letter-filter/JSON-search widget (see ActiveLinkOverviewPage's javadoc) plus the
 * "Restrict results to" execute-on-bitmask checkboxes (filterList.js ANDs each checked value
 * against row[9], the filter's real opSet bitmask, already present in the JSON row below).
 */
public final class FilterOverviewPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public FilterOverviewPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public int render() throws ARException {
        PagePath page = Naming.filterOverview();

        Table tbl = new Table("filterList", "TblObjectList");
        tbl.addColumn(25, "Filter Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Order");
        tbl.addColumn(24, "Execute On");
        tbl.addColumn(7, "Shared");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(10, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar filterList = [");

        List<String> names = repo.listFilterNames();
        int count = 0;
        for (String name : names) {
            try {
                Filter filter = repo.getFilter(name);
                if (!OverlaySupport.isVisible(filter.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                boolean isOverlaid = OverlaySupport.isOverlaidForNaming(filter.getProperties(), serverOverlayMode);
                PagePath detail = Naming.filterDetail(name, isOverlaid);
                letterFilter.incStartLetterOf(name);
                SearchIndex.add(name, "filter", detail);
                if (appConfig.jsonOutput) JsonExport.addFilter(name, filter, OverlaySupport.overlayType(filter.getProperties()));

                int ifCount = filter.getActionList() == null ? 0 : filter.getActionList().size();
                int elseCount = filter.getElseList() == null ? 0 : filter.getElseList().size();
                boolean shared = filter.getFormList() != null && filter.getFormList().size() > 1;
                String executeOn = String.join(", ", filter.getFormList());
                String modified = DateTimeFormat.toHtmlString(filter.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(filter.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, new ImageTag(ImageTag.Id.Filter, page.rootLevel(), OverlaySupport.overlayType(filter.getProperties())), page.rootLevel()).toHtml());
                row.addCell(new TableCell(AREnumLabels.objectEnable(filter.isEnable()), filter.isEnable() ? "" : "objStatusDisabled"));
                row.addCell(new TableCell(filter.getOrder()));
                row.addCell(executeOn);
                row.addCell(shared ? "Yes" : "");
                row.addCell(new TableCell(ifCount));
                row.addCell(new TableCell(elseCount));
                row.addCell(modified);
                row.addCell(filter.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",")
                    .append(filter.isEnable() ? 1 : 0).append(',')
                    .append(filter.getOrder()).append(",\"")
                    .append(WebUtil.jsString(executeOn)).append("\",")
                    .append(ifCount).append(',').append(elseCount).append(",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(filter.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(filter.getOpSet()).append(',')
                    .append(OverlaySupport.overlayType(filter.getProperties())).append(',')
                    .append(shared ? 1 : 0).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION FilterList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        tbl.maxRenderedRows(0); // rows come from lists.js (see Table.maxRenderedRows javadoc)
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(count).append(" Filters\n");
        content.append("<span id='filterListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("filterFilter", null));
        content.append("<span class='multiFilter' id='multiFilter'>Restrict results to: ")
            .append("<input id='typeFilterOnlyNone' type='checkbox' value='N'/><label for='typeFilterOnlyNone'>&nbsp;None</label>")
            .append("<input id='typeFilterModify' type='checkbox' value='2'/><label for='typeFilterModify'>&nbsp;Modify</label>")
            .append("<input id='typeFilterSubmit' type='checkbox' value='4'/><label for='typeFilterSubmit'>&nbsp;Submit</label>")
            .append("<input id='typeFilterDelete' type='checkbox' value='8'/><label for='typeFilterDelete'>&nbsp;Delete</label>")
            .append("<input id='typeFilterGetEntry' type='checkbox' value='1'/><label for='typeFilterGetEntry'>&nbsp;Get&nbsp;Entry</label>")
            .append("<input id='typeFilterMerge' type='checkbox' value='16'/><label for='typeFilterMerge'>&nbsp;Merge</label>")
            .append("<input id='typeFilterService' type='checkbox' value='64'/><label for='typeFilterService'>&nbsp;Service</label>")
            .append(" <button id='typeFilterNone'>Clear All</button></span>");
        content.append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Filter List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/lists.js").bodyClass("list-page");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        PagePath overviewPage = Naming.overviewFilters();
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), "Filter List", overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/lists.js").bodyClass("list-page");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
