package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.AssociationSource;
import arinside.config.AppConfig;
import arinside.output.*;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Association;
import com.bmc.arsys.api.DirectAssociation;

import java.util.List;

/** New (post-C++) functionality - see AssociationSource's javadoc. No naming-class precedent to port against, so this follows the same list-page shape every other object type in this port already uses. */
public final class AssociationOverviewPage {
    private final AssociationSource repo;
    private final AppConfig appConfig;

    public AssociationOverviewPage(AssociationSource repo, AppConfig appConfig) {
        this.repo = repo;
        this.appConfig = appConfig;
    }

    public int render() throws ARException {
        PagePath page = Naming.associationOverview();

        Table tbl = new Table("associationList", "TblObjectList");
        tbl.addColumn(25, "Association Name");
        tbl.addColumn(10, "Type");
        tbl.addColumn(20, "Primary Form");
        tbl.addColumn(20, "Secondary Form");
        tbl.addColumn(10, "Cardinality");
        tbl.addColumn(15, "Enforcement");

        List<String> names = repo.listAssociationNames();
        int count = 0;
        for (String name : names) {
            try {
                Association a = repo.getAssociation(name);
                PagePath detail = Naming.associationDetail(name);
                SearchIndex.add(name, "association", detail);
                if (appConfig.jsonOutput) JsonExport.addAssociation(name, a instanceof DirectAssociation ? "Direct" : "Indirect", a.getPrimaryFormName(), a.getSecondaryFormName(), AREnumLabels.associationCardinality(a.getCardinality()), AREnumLabels.associationEnforcement(a.getEnforcement()));

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, ImageTag.Id.Association, page.rootLevel()).toHtml());
                row.addCell(a instanceof DirectAssociation ? "Direct" : "Indirect");
                row.addCell(WebUtil.validate(nullToEmpty(a.getPrimaryFormName())));
                row.addCell(WebUtil.validate(nullToEmpty(a.getSecondaryFormName())));
                row.addCell(AREnumLabels.associationCardinality(a.getCardinality()));
                row.addCell(AREnumLabels.associationEnforcement(a.getEnforcement()));
                tbl.addRow(row);
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION AssociationList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        // NOTE: no lists.js here (associations have no client-side filter widget), so this table
        // must keep server-rendering all its rows - do NOT add tbl.maxRenderedRows(0).

        WebPage webPage = new WebPage(page.fileName(), "Association List", page.rootLevel(), appConfig);
        webPage.addContent(count + " Associations\n" + tbl.toXHtml());
        webPage.saveInFolder(page.path());

        return count;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
