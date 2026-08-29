package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;

import java.util.List;

/**
 * Java port of CDocMain::ActiveLinkList/ActiveLinkListJson + output/AlTable.{h,cpp} - includes the
 * "Groups" column (count of GetGroupList()) and the jQuery letter-filter/JSON-search widget the
 * C++ adds to this page (object_list.js + actlinkList.js, already bundled as web-assets resources
 * but never wired up server-side until now - see LetterFilterControl's javadoc).
 */
public final class ActiveLinkOverviewPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public ActiveLinkOverviewPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public int render() throws ARException {
        PagePath page = Naming.activeLinkOverview();

        Table tbl = new Table("alList", "TblObjectList");
        tbl.addColumn(22, "Active Link Name");
        tbl.addColumn(8, "Enabled");
        tbl.addColumn(8, "Groups");
        tbl.addColumn(8, "Order");
        tbl.addColumn(21, "Execute On");
        tbl.addColumn(7, "Shared");
        tbl.addColumn(6, "If");
        tbl.addColumn(6, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(8, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar alList = [");

        List<String> names = repo.listActiveLinkNames();
        int count = 0;
        for (String name : names) {
            try {
                ActiveLink al = repo.getActiveLink(name);
                if (!OverlaySupport.isVisible(al.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                boolean isOverlaid = OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode);
                PagePath detail = Naming.activeLinkDetail(name, isOverlaid);
                letterFilter.incStartLetterOf(name);
                SearchIndex.add(name, "active-link", detail);
                if (appConfig.jsonOutput) JsonExport.addActiveLink(name, al, OverlaySupport.overlayType(al.getProperties()));

                int groupCount = al.getGroupList() == null ? 0 : al.getGroupList().size();
                int ifCount = al.getActionList() == null ? 0 : al.getActionList().size();
                int elseCount = al.getElseList() == null ? 0 : al.getElseList().size();
                int formCount = al.getFormList() == null ? 0 : al.getFormList().size();
                boolean shared = formCount > 1; // attached to more than one form (Dev Studio's "Shared" flag)
                String executeOn = String.join(", ", al.getFormList());
                String modified = DateTimeFormat.toHtmlString(al.getLastUpdateTime().getValue());
                // JSON rows go through jQuery's .text() client-side (not innerHTML), so they need
                // the plain-space date, not the HTML table's &nbsp; variant - matches the C++'s own
                // split between DateTimeToString (JSON/CSV) and DateTimeToHTMLString (static table).
                String modifiedPlain = DateTimeFormat.toPlainString(al.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, new ImageTag(ImageTag.Id.ActiveLink, page.rootLevel(), OverlaySupport.overlayType(al.getProperties())), page.rootLevel()).toHtml());
                row.addCell(new TableCell(AREnumLabels.objectEnable(al.isEnable()), al.isEnable() ? "" : "objStatusDisabled"));
                row.addCell(new TableCell(groupCount));
                row.addCell(new TableCell(al.getOrder()));
                row.addCell(executeOn);
                row.addCell(shared ? "Yes" : "");
                row.addCell(new TableCell(ifCount));
                row.addCell(new TableCell(elseCount));
                row.addCell(modified);
                row.addCell(al.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",")
                    .append(al.isEnable() ? 1 : 0).append(',')
                    .append(groupCount).append(',')
                    .append(al.getOrder()).append(",\"")
                    .append(WebUtil.jsString(executeOn)).append("\",")
                    .append(ifCount).append(',').append(elseCount).append(",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(al.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(OverlaySupport.overlayType(al.getProperties())).append(',')
                    .append(shared ? 1 : 0).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION ActiveLinkList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        tbl.maxRenderedRows(0); // rows come from lists.js (see Table.maxRenderedRows javadoc)
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(count).append(" Active Links\n");
        content.append("<span id='actlinkListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("actlinkFilter", null)).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Active Link List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/lists.js").bodyClass("list-page");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        // Java port of ObjectNameActiveLinkOverview (output/FileNaming.cpp) - the overview/ nav
        // landing page, same content as above under a different path - see Naming's javadoc.
        PagePath overviewPage = Naming.overviewActiveLinks();
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), "Active Link List", overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/lists.js").bodyClass("list-page");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
