package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.WorkflowSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.MenuAttachmentIndex;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Menu;

import java.util.List;

/**
 * Java port of CDocMain::CharMenuList/MenuListJson + output/MenuTable.{h,cpp}, including the jQuery
 * letter-filter/JSON-search widget (see ActiveLinkOverviewPage's javadoc) and its type-filter
 * checkboxes (menuList.js's onCheckTypeFilterForRow reads row[1], the real menu type, already in
 * the JSON row below). The C++'s "(!)" not-used-in-workflow marker (CARCharMenu::IsUsedInWorkflow,
 * core/ARCharMenu.cpp) now fully ported via {@link MenuAttachmentIndex} - both a real character
 * field's charMenu limit pointing at this menu AND an active link's Change Field action setting it.
 */
public final class MenuOverviewPage {
    private final WorkflowSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final MenuAttachmentIndex attachments;

    public MenuOverviewPage(WorkflowSource repo, AppConfig appConfig, int serverOverlayMode, MenuAttachmentIndex attachments) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.attachments = attachments;
    }

    public int render() throws ARException {
        PagePath page = Naming.menuOverview();

        Table tbl = new Table("menuList", "TblObjectList");
        tbl.addColumn(40, "CharMenu Name");
        tbl.addColumn(10, "Type");
        tbl.addColumn(10, "Refresh On");
        tbl.addColumn(20, "Changed");
        tbl.addColumn(20, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar menuList = [");

        List<String> names = repo.listMenuNames();
        int count = 0;
        for (String name : names) {
            try {
                Menu menu = repo.getMenu(name);
                if (!OverlaySupport.isVisible(menu.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                boolean isOverlaid = OverlaySupport.isOverlaidForNaming(menu.getProperties(), serverOverlayMode);
                PagePath detail = Naming.menuDetail(name, isOverlaid);
                letterFilter.incStartLetterOf(name);
                SearchIndex.add(name, "menu", detail);
                if (appConfig.jsonOutput) JsonExport.addMenu(name, menu, OverlaySupport.overlayType(menu.getProperties()));

                boolean usedInWorkflow = attachments.isUsed(name);
                String modified = DateTimeFormat.toHtmlString(menu.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(menu.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                String nameCell = URLLink.to(name, detail, new ImageTag(ImageTag.Id.Menu, page.rootLevel(), OverlaySupport.overlayType(menu.getProperties())), page.rootLevel()).toHtml();
                if (!usedInWorkflow) nameCell += " (<b>!</b>)";

                TableRow row = new TableRow();
                row.addCell(nameCell);
                row.addCell(AREnumLabels.menuType(menu.getMenuType()));
                row.addCell(AREnumLabels.menuRefresh(menu.getRefreshCode()));
                row.addCell(modified);
                row.addCell(menu.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",")
                    .append(menu.getMenuType()).append(',')
                    .append(menu.getRefreshCode()).append(",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(menu.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(OverlaySupport.overlayType(menu.getProperties())).append(',')
                    .append(usedInWorkflow ? 1 : 0).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION CharMenuList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        tbl.maxRenderedRows(0); // rows come from lists.js (see Table.maxRenderedRows javadoc)
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(count).append(" Menus\n");
        content.append("<span id='menuListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("menuFilter", null));
        content.append("<span class='multiFilter' id='multiFilter'>Restrict results to: ")
            .append("<input id='typeFilterCharacter' type='checkbox' value='1'/><label for='typeFilterCharacter'>&nbsp;Character</label>")
            .append("<input id='typeFilterSearch' type='checkbox' value='2'/><label for='typeFilterSearch'>&nbsp;Search</label>")
            .append("<input id='typeFilterFile' type='checkbox' value='3'/><label for='typeFilterFile'>&nbsp;File</label>")
            .append("<input id='typeFilterSQL' type='checkbox' value='4'/><label for='typeFilterSQL'>&nbsp;SQL</label>")
            .append("<input id='typeFilterDD' type='checkbox' value='6'/><label for='typeFilterDD'>&nbsp;Data&nbsp;Dictionary</label>")
            .append(" <button id='typeFilterNone'>Clear All</button></span>");
        content.append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());
        content.append("(!) Menu is not attached to a character field and no Active Link \"Change Field\" Action sets the menu to a field.");

        WebPage webPage = new WebPage(page.fileName(), "Menu List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/lists.js").bodyClass("list-page");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        PagePath overviewPage = Naming.overviewMenus();
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), "Menu List", overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/lists.js").bodyClass("list-page");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
