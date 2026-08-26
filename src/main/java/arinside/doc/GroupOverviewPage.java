package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.GroupRecord;
import arinside.ar.IdentityRepository;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;

import java.util.List;

/**
 * Java port of CDocMain::GroupList + output/GroupTable.{h,cpp} - real columns are Name/ID/Type/
 * Category/Modified/By. Includes the jQuery letter-filter/JSON-search widget plus the
 * Regular/Dynamic/Computed category-restriction checkboxes (see ActiveLinkOverviewPage's javadoc
 * for the general pattern) - groupList.js's JSON row layout is [groupId, name, type, category,
 * modifiedDateStr, lastChangedBy, link], matching createGroupRowHtml(); category values (0/1/2)
 * match AR_GROUP_CATEGORY_*.
 */
public final class GroupOverviewPage {
    private final IdentityRepository repo;
    private final AppConfig appConfig;

    public GroupOverviewPage(IdentityRepository repo, AppConfig appConfig) {
        this.repo = repo;
        this.appConfig = appConfig;
    }

    public List<GroupRecord> render() throws ARException {
        PagePath page = Naming.groupOverview();

        Table tbl = new Table("groupList", "TblObjectList");
        tbl.addColumn(30, "Group Name");
        tbl.addColumn(10, "ID");
        tbl.addColumn(15, "Type");
        tbl.addColumn(15, "Category");
        tbl.addColumn(15, "Modified");
        tbl.addColumn(15, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar groupList = [");

        List<GroupRecord> groups = repo.listGroups();
        int count = 0;
        for (GroupRecord g : groups) {
            PagePath detail = Naming.groupDetail(g.groupId);
            letterFilter.incStartLetterOf(g.name);

            String modified = g.modified == null ? "" : DateTimeFormat.toHtmlString(g.modified.getValue());
            String modifiedPlain = g.modified == null ? "" : DateTimeFormat.toPlainString(g.modified.getValue());
            String link = URLLink.relativeUrl(page.rootLevel(), detail);

            TableRow row = new TableRow();
            row.addCell(URLLink.to(g.name, detail, ImageTag.Id.Group, page.rootLevel()).toHtml());
            row.addCell(new TableCell(g.groupId));
            row.addCell(AREnumLabels.groupType(g.groupType));
            row.addCell(AREnumLabels.groupCategory(g.category));
            row.addCell(modified);
            row.addCell(g.modifiedBy == null ? "" : g.modifiedBy);
            tbl.addRow(row);

            if (count > 0) json.append(',');
            json.append('[').append(g.groupId).append(",\"")
                .append(WebUtil.jsString(g.name)).append("\",")
                .append(g.groupType).append(',').append(g.category).append(",\"")
                .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                .append(WebUtil.jsString(g.modifiedBy == null ? "" : g.modifiedBy)).append("\",\"")
                .append(WebUtil.jsString(link)).append("\"]");
            count++;
        }
        if (!groups.isEmpty()) tbl.removeEmptyMessageRow();
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        String typeFilter = "<span class='multiFilter' id='multiFilter'>Restrict results to: "
            + "<input id='typeFilterRegular' type='checkbox' value='0'/><label for='typeFilterRegular'>&nbsp;Regular</label>"
            + "<input id='typeFilterDynamic' type='checkbox' value='1'/><label for='typeFilterDynamic'>&nbsp;Dynamic</label>"
            + "<input id='typeFilterComputed' type='checkbox' value='2'/><label for='typeFilterComputed'>&nbsp;Computed</label>"
            + " <button id='typeFilterNone'>Clear All</button></span>";

        StringBuilder content = new StringBuilder();
        content.append(groups.size()).append(" Groups\n");
        content.append("<span id='groupListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("groupFilter", "search by name or id"))
            .append(" &nbsp;&nbsp;&nbsp; ").append(typeFilter).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Group List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/object_list.js").addScriptReference("img/groupList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());
        return groups;
    }
}
