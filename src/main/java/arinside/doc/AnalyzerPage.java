package arinside.doc;

import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.PermissionIndex;

/**
 * Java port of doc/DocAnalyzer.cpp - the "Form Index Analyzer" check (indexed character fields
 * with an inefficient QBE match mode or an overlong key), sourced from PermissionIndex.indexFindings()
 * (which already visits every form+field during its own pass, so this adds no extra fetch).
 */
public final class AnalyzerPage {
    private final AppConfig appConfig;
    private final PermissionIndex permIndex;
    private final GlobalFieldIndex globalFields;

    public AnalyzerPage(AppConfig appConfig, PermissionIndex permIndex, GlobalFieldIndex globalFields) {
        this.appConfig = appConfig;
        this.permIndex = permIndex;
        this.globalFields = globalFields;
    }

    public void render() {
        PagePath page = Naming.analyzerMain();
        WebPage webPage = new WebPage(page.fileName(), "Analyzer", page.rootLevel(), appConfig);
        webPage.addContentHead("Performance Analyzer");
        webPage.addContent(URLLink.to("Form Index Analyzer", Naming.analyzerQbeCheck(), ImageTag.Id.Document, page.rootLevel()).toHtml());
        webPage.saveInFolder(page.path());

        indexAnalyzer();
    }

    private void indexAnalyzer() {
        PagePath page = Naming.analyzerQbeCheck();
        WebPage webPage = new WebPage(page.fileName(), "Form Index Analyzer", page.rootLevel(), appConfig);
        webPage.addContentHead("Form Index Analyzer");

        Table tbl = new Table("fieldListAll", "TblObjectList");
        tbl.description = "If the QBE Match property on a character field is set to Anywhere, the database will "
            + "not be able to optimize its search and will perform a table scan. Avoid using this setting if possible.";
        tbl.addColumn(20, "Field");
        tbl.addColumn(20, "Form");
        tbl.addColumn(60, "Comment");
        for (var f : permIndex.indexFindings()) {
            tbl.addRow(new TableRow().addCellList(f.fieldName(),
                URLLink.to(f.schemaName(), Naming.schemaDetail(f.schemaName(), globalFields.isOverlaid(f.schemaName())), ImageTag.Id.Schema, page.rootLevel()).toHtml(),
                f.message()));
        }
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());
    }
}
