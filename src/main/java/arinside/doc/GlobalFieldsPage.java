package arinside.doc;

import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;

/** Java port of CDocMain::GlobalFieldList - see GlobalFieldIndex's javadoc for what "global field" means here. */
public final class GlobalFieldsPage {
    private final GlobalFieldIndex index;
    private final AppConfig appConfig;

    public GlobalFieldsPage(GlobalFieldIndex index, AppConfig appConfig) {
        this.index = index;
        this.appConfig = appConfig;
    }

    public void render() {
        PagePath page = Naming.globalFields();

        Table outer = new Table("fieldListAll", "TblObjectList");
        outer.addColumn(20, "GlobalFieldId");
        outer.addColumn(80, "References");

        for (var e : index.byFieldId().entrySet()) {
            Table inner = new Table("refList", "TblObjectList");
            inner.addColumn(50, "Schema Name");
            inner.addColumn(50, "Field Name");
            for (GlobalFieldIndex.Entry ref : e.getValue()) {
                TableRow row = new TableRow();
                row.addCell(URLLink.to(ref.schemaName(), ref.schemaLink(), ImageTag.Id.Schema, page.rootLevel()).toHtml());
                row.addCell(ref.fieldName());
                inner.addRow(row);
            }

            TableRow outerRow = new TableRow();
            outerRow.addCell(new TableCell(e.getKey()));
            outerRow.addCell(inner.toXHtml());
            outer.addRow(outerRow);
        }
        if (!index.byFieldId().isEmpty()) outer.removeEmptyMessageRow();

        WebPage webPage = new WebPage(page.fileName(), "Global Fields", page.rootLevel(), appConfig);
        webPage.addContent(new ImageTag(ImageTag.Id.Document, page.rootLevel()).toHtml() + index.byFieldId().size() + " Global Fields\n");
        webPage.addContent(outer.toXHtml());
        webPage.saveInFolder(page.path());
    }
}
