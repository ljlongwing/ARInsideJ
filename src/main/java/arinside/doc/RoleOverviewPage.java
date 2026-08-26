package arinside.doc;

import arinside.ar.IdentityRepository;
import arinside.ar.RoleRecord;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;

import java.util.List;

/**
 * Java port of CDocMain::RoleList + output/RoleTable.{h,cpp} - real columns are Role Name/RoleID/
 * Application/Modified/By. Includes the jQuery letter-filter/JSON-search widget (see
 * ActiveLinkOverviewPage's javadoc) - roleList.js's JSON row layout is [roleId, name, appName,
 * modifiedDateStr, lastChangedBy, link, appLink], matching createRoleRowHtml().
 * Unlike the C++ (CARContainer app(role.GetApplicationName()); if (app.Exists()) ...), the app link
 * is emitted whenever an application name is present without a separate existence check - this port
 * doesn't have a container lookup wired into this page and every other "link to an application by
 * name" call site in this codebase makes the same simplification (see e.g. SchemaDetailPage's
 * schemaLink()), so this isn't a new gap.
 */
public final class RoleOverviewPage {
    private final IdentityRepository repo;
    private final AppConfig appConfig;

    public RoleOverviewPage(IdentityRepository repo, AppConfig appConfig) {
        this.repo = repo;
        this.appConfig = appConfig;
    }

    public List<RoleRecord> render() throws ARException {
        PagePath page = Naming.roleOverview();

        Table tbl = new Table("roleList", "TblObjectList");
        tbl.addColumn(25, "Role Name");
        tbl.addColumn(5, "RoleID");
        tbl.addColumn(30, "Application");
        tbl.addColumn(20, "Modified");
        tbl.addColumn(20, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar roleList = [");

        List<RoleRecord> roles = repo.listRoles();
        int count = 0;
        for (RoleRecord r : roles) {
            PagePath detail = Naming.roleDetail(r.requestId);
            letterFilter.incStartLetterOf(r.name);

            boolean hasApp = r.applicationName != null && !r.applicationName.isEmpty();
            String appLink = hasApp ? URLLink.relativeUrl(page.rootLevel(), Naming.containerDetail(Constants.ARCON_APP, r.applicationName, false)) : "";
            String appCell = hasApp
                ? URLLink.to(r.applicationName, Naming.containerDetail(Constants.ARCON_APP, r.applicationName, false), ImageTag.Id.Application, page.rootLevel()).toHtml()
                : "";
            String modified = r.modified == null ? "" : DateTimeFormat.toHtmlString(r.modified.getValue());
            String modifiedPlain = r.modified == null ? "" : DateTimeFormat.toPlainString(r.modified.getValue());
            String link = URLLink.relativeUrl(page.rootLevel(), detail);

            TableRow row = new TableRow();
            row.addCell(URLLink.to(r.name, detail, ImageTag.Id.Role, page.rootLevel()).toHtml());
            row.addCell(new TableCell(r.roleId));
            row.addCell(appCell);
            row.addCell(modified);
            row.addCell(r.modifiedBy == null ? "" : r.modifiedBy);
            tbl.addRow(row);

            if (count > 0) json.append(',');
            json.append('[').append(r.roleId).append(",\"")
                .append(WebUtil.jsString(r.name)).append("\",\"")
                .append(WebUtil.jsString(r.applicationName == null ? "" : r.applicationName)).append("\",\"")
                .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                .append(WebUtil.jsString(r.modifiedBy == null ? "" : r.modifiedBy)).append("\",\"")
                .append(WebUtil.jsString(link)).append("\",\"")
                .append(WebUtil.jsString(appLink)).append("\"]");
            count++;
        }
        if (!roles.isEmpty()) tbl.removeEmptyMessageRow();
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(roles.size()).append(" Roles\n");
        content.append("<span id='roleListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("roleFilter", "search by name or id")).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Role List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/object_list.js").addScriptReference("img/roleList.js")
            .addScriptReference("img/jquery.timers.js").addScriptReference("img/jquery.address.min.js");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());
        return roles;
    }
}
