package arinside.doc;

import arinside.ar.ContainerSource;
import arinside.ar.OverlaySupport;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GuideCallIndex;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Container;

import java.util.List;

/**
 * Java port of CDocMain::ContainerList(nType, title) + output/ContainerTable.{h,cpp} - one page
 * class shared across all five ARCON_* subtypes, matching the C++'s own approach. Real columns are
 * Name(+"(!)" marker for an unreferenced guide)/Modified/By - no Label/Members columns (that was
 * this port's own invention, not in the real CContainerTable). Includes the jQuery letter-filter/
 * JSON-search widget (see ActiveLinkOverviewPage's javadoc) - containerList.js's JSON row layout is
 * [name, modifiedDateStr, lastChangedBy, link, overlayType, unusedFlag(guides only)], confirmed
 * against the real createContainerRowHtml().
 */
public final class ContainerOverviewPage {
    private final ContainerSource repo;
    private final AppConfig appConfig;
    private final int containerType;
    private final String title;
    private final ImageTag.Id icon;
    private final int serverOverlayMode;
    private final GuideCallIndex guideCalls;

    public ContainerOverviewPage(ContainerSource repo, AppConfig appConfig, int containerType, String title, ImageTag.Id icon, int serverOverlayMode, GuideCallIndex guideCalls) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.containerType = containerType;
        this.title = title;
        this.icon = icon;
        this.serverOverlayMode = serverOverlayMode;
        this.guideCalls = guideCalls;
    }

    /** Java port of CContainerTable::IsUnusedContainer - a Guide/Filter Guide no AL/Filter CallGuide action ever targets. */
    private boolean isUnused(String name) {
        if (containerType == Constants.ARCON_GUIDE) return guideCalls == null || guideCalls.alCallers(name).isEmpty();
        if (containerType == Constants.ARCON_FILTER_GUIDE) return guideCalls == null || guideCalls.filterCallers(name).isEmpty();
        return false;
    }

    public int render() throws ARException {
        PagePath page = Naming.containerOverview(containerType);

        Table tbl = new Table("containerList", "TblObjectList");
        tbl.addColumn(60, "Name");
        tbl.addColumn(20, "Modified");
        tbl.addColumn(20, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar containerList = [");

        List<String> names = repo.listContainerNames(containerType);
        int count = 0;
        for (String name : names) {
            try {
                Container c = repo.getContainer(name);
                if (!OverlaySupport.isVisible(c.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                PagePath detail = Naming.containerDetail(containerType, name, OverlaySupport.isOverlaidForNaming(c.getProperties(), serverOverlayMode));
                letterFilter.incStartLetterOf(name);
                boolean unused = isUnused(name);

                String modified = DateTimeFormat.toHtmlString(c.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(c.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, new ImageTag(icon, page.rootLevel(), OverlaySupport.overlayType(c.getProperties())), page.rootLevel()).toHtml() + (unused ? " (<b>!</b>)" : ""));
                row.addCell(modified);
                row.addCell(c.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(c.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(OverlaySupport.overlayType(c.getProperties()));
                if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE) {
                    json.append(',').append(unused ? 0 : 1);
                }
                json.append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION ContainerList(" + containerType + ") '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\nvar containerType = ").append(containerType).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append("<span id='containerListResultCount'></span>").append(count).append(" ").append(title).append("\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("containerFilter", null)).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());
        if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE) {
            content.append("(!) No Active Link / Filter \"CallGuide\" Action uses this Guide.");
        }

        WebPage webPage = new WebPage(page.fileName(), title, page.rootLevel(), appConfig);
        webPage.addScriptReference("img/object_list.js").addScriptReference("img/containerList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        PagePath overviewPage = Naming.overviewContainer(containerType);
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), title, overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/object_list.js").addScriptReference("img/containerList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
