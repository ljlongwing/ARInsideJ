package arinside.doc;

import arinside.config.AppConfig;
import arinside.output.*;

/**
 * Java port of doc/DocSummaryInfo.cpp - the "index.htm" home page content (a summary table of
 * every object type + counts, linking to each overview page).
 *
 * Unlike the C++ (which threads count fields through CDocSummaryInfo as documentation proceeds,
 * then renders once at the very end of CARInside::Documentation()), this is a plain static render
 * call taking every count as a parameter, invoked once at the end of Main's pipeline once all the
 * real counts are known - same net effect, simpler given this port's structure.
 */
public final class DocSummaryInfo {
    private DocSummaryInfo() {}

    /**
     * loadSeconds/documentationSeconds mirror the C++'s nDurationLoad/nDurationDocumentation
     * (ARInside.cpp's CAppTimer around LoadServerObjects() vs Documentation()) - see the
     * loadSeconds capture in Main.java for how this port approximates that split given its
     * interleaved scan/doc pipeline.
     */
    public record Counts(
        int activeLinks, int webServices, int activeLinkGuides, int filterGuides, int packingLists,
        int applications, int escalations, int filters, int groups, int menus, int roles, int forms,
        int users, int images, int associations, int totalFields, long loadSeconds, long documentationSeconds, int filesCreated) {}

    public static void render(AppConfig appConfig, Counts c) {
        PagePath page = Naming.mainHome();
        WebPage webPage = new WebPage(page.fileName(), "Documentation Index", page.rootLevel(), appConfig);
        webPage.addContentHead("Documentation index:");

        Table tbl = new Table("tblDocSummary", "TblObjectList");
        tbl.addColumn(10, "Objects");
        tbl.addColumn(90, "Description");

        row(tbl, c.activeLinks(), "Active Links", Naming.overviewActiveLinks());
        row(tbl, c.webServices(), "Web Services", Naming.overviewContainer(com.bmc.arsys.api.Constants.ARCON_WEBSERVICE));
        row(tbl, c.activeLinkGuides(), "Active Link Guides", Naming.overviewContainer(com.bmc.arsys.api.Constants.ARCON_GUIDE));
        row(tbl, c.filterGuides(), "Filter Guides", Naming.overviewContainer(com.bmc.arsys.api.Constants.ARCON_FILTER_GUIDE));
        row(tbl, c.packingLists(), "Packing Lists", Naming.overviewContainer(com.bmc.arsys.api.Constants.ARCON_PACK));
        row(tbl, c.applications(), "Applications", Naming.overviewContainer(com.bmc.arsys.api.Constants.ARCON_APP));
        row(tbl, c.escalations(), "Escalations", Naming.overviewEscalations());
        row(tbl, c.filters(), "Filters", Naming.overviewFilters());
        row(tbl, c.groups(), "Groups", Naming.groupOverview());
        row(tbl, c.menus(), "Menus", Naming.overviewMenus());
        row(tbl, c.roles(), "Roles", Naming.roleOverview());
        row(tbl, c.forms(), "Forms", Naming.schemaOverview());
        row(tbl, c.users(), "Users", Naming.overviewUsers());
        row(tbl, c.images(), "Images", Naming.overviewImages());
        if (c.associations() > 0) row(tbl, c.associations(), "Associations", Naming.associationOverview());

        webPage.addContent(tbl.toXHtml());

        int totalObjects = c.activeLinks() + c.activeLinkGuides() + c.filterGuides() + c.packingLists() + c.applications()
            + c.webServices() + c.escalations() + c.filters() + c.groups() + c.menus() + c.roles() + c.forms() + c.users() + c.images()
            + c.associations();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("%,d", totalObjects)).append(" ARSystem objects and ").append(String.format("%,d", c.totalFields()))
            .append(" fields loaded in ").append(hms(c.loadSeconds())).append(".<br/>\n");
        summary.append(String.format("%,d", c.filesCreated())).append(" files created in ").append(hms(c.documentationSeconds())).append(".<br/>\n");
        if (!appConfig.scope.isEmpty()) {
            summary.append("<br/>Scoped export limited to: ").append(WebUtil.validate(appConfig.scope)).append(".<br/>\n");
        }
        if (appConfig.runNotes != null && !appConfig.runNotes.isEmpty()) {
            summary.append("<br/>Run Notes: ").append(WebUtil.validate(appConfig.runNotes));
        }
        webPage.addContent(summary.toString());

        webPage.saveInFolder(page.path());
    }

    private static String hms(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static void row(Table tbl, int count, String caption, PagePath target) {
        TableRow row = new TableRow();
        row.addCell(new TableCell(count));
        row.addCell(URLLink.to(caption, target, 0).toHtml());
        tbl.addRow(row);
    }
}
