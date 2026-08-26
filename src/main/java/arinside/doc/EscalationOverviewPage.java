package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Escalation;

import java.util.List;

/**
 * Java port of CDocMain::EscalationList/EscalationListJson + output/EscalTable.{h,cpp} - includes
 * the jQuery letter-filter/JSON-search widget (see ActiveLinkOverviewPage's javadoc). The C++'s
 * optional DSO "Pool" column is conditionally shown by escalationList.js only when the static
 * table's own header says so (checked against the actual DOM at load time) - this port's static
 * table never has that column, so it stays correctly hidden with no extra code needed here; the
 * JSON row still reserves that slot (as 0) to keep the array shape identical to the shipped JS.
 */
public final class EscalationOverviewPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public EscalationOverviewPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public int render() throws ARException {
        PagePath page = Naming.escalationOverview();

        Table tbl = new Table("escalationList", "TblObjectList");
        tbl.addColumn(25, "Escalation Name");
        tbl.addColumn(10, "Enabled");
        tbl.addColumn(25, "Execute On");
        tbl.addColumn(7, "If");
        tbl.addColumn(7, "Else");
        tbl.addColumn(13, "Changed");
        tbl.addColumn(13, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar escalationList = [");

        List<String> names = repo.listEscalationNames();
        int count = 0;
        for (String name : names) {
            try {
                Escalation esc = repo.getEscalation(name);
                if (!OverlaySupport.isVisible(esc.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                boolean isOverlaid = OverlaySupport.isOverlaidForNaming(esc.getProperties(), serverOverlayMode);
                PagePath detail = Naming.escalationDetail(name, isOverlaid);
                letterFilter.incStartLetterOf(name);

                int ifCount = esc.getActionList() == null ? 0 : esc.getActionList().size();
                int elseCount = esc.getElseList() == null ? 0 : esc.getElseList().size();
                String executeOn = String.join(", ", esc.getFormList());
                String modified = DateTimeFormat.toHtmlString(esc.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(esc.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, ImageTag.Id.Escalation, page.rootLevel()).toHtml());
                row.addCell(new TableCell(AREnumLabels.objectEnable(esc.isEnable()), esc.isEnable() ? "" : "objStatusDisabled"));
                row.addCell(executeOn);
                row.addCell(new TableCell(ifCount));
                row.addCell(new TableCell(elseCount));
                row.addCell(modified);
                row.addCell(esc.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",")
                    .append(esc.isEnable() ? 1 : 0).append(",\"")
                    .append(WebUtil.jsString(executeOn)).append("\",")
                    .append(ifCount).append(',').append(elseCount).append(",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(esc.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",0,")
                    .append(OverlaySupport.overlayType(esc.getProperties())).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION EscalationList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(count).append(" Escalations\n");
        content.append("<span id='escalationListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("escalationFilter", null)).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Escalation List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/object_list.js").addScriptReference("img/escalationList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        PagePath overviewPage = Naming.overviewEscalations();
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), "Escalation List", overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/object_list.js").addScriptReference("img/escalationList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
