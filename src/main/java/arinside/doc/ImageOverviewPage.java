package arinside.doc;

import arinside.ar.ImageSource;
import arinside.ar.OverlaySupport;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Image;

import java.util.List;

/**
 * Java port of CDocMain::ImageList-equivalent + output/ImageTable.{h,cpp} - includes the jQuery
 * letter-filter/JSON-search widget (see ActiveLinkOverviewPage's javadoc) - imageList.js's JSON row
 * layout is [name, type, modifiedDateStr, lastChangedBy, link, overlayType], confirmed against the
 * real createImageRowHtml().
 */
public final class ImageOverviewPage {
    private final ImageSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;

    public ImageOverviewPage(ImageSource repo, AppConfig appConfig, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
    }

    public int render() throws ARException {
        PagePath page = Naming.imageOverview();

        Table tbl = new Table("imageList", "TblObjectList");
        tbl.addColumn(40, "Image Name");
        tbl.addColumn(15, "Type");
        tbl.addColumn(20, "Changed");
        tbl.addColumn(25, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar imageList = [");

        List<String> names = repo.listImageNames();
        int count = 0;
        for (String name : names) {
            try {
                Image img = repo.getImage(name);
                if (!OverlaySupport.isVisible(img.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;
                PagePath detail = Naming.imageDetail(name, false);
                letterFilter.incStartLetterOf(name);
                SearchIndex.add(name, "image", detail);
                if (appConfig.jsonOutput) JsonExport.addImage(name, img.getType(), img.getLastUpdateTime() == null ? null : img.getLastUpdateTime().getValue(), img.getLastChangedBy());

                String type = img.getType() == null ? "" : img.getType();
                String modified = DateTimeFormat.toHtmlString(img.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(img.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detail);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detail, new ImageTag(ImageTag.Id.Image, page.rootLevel(), OverlaySupport.overlayType(img.getProperties())), page.rootLevel()).toHtml());
                row.addCell(type);
                row.addCell(modified);
                row.addCell(img.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[\"").append(WebUtil.jsString(name)).append("\",\"")
                    .append(WebUtil.jsString(type)).append("\",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(img.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(OverlaySupport.overlayType(img.getProperties())).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION ImageList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        tbl.maxRenderedRows(0); // rows come from lists.js (see Table.maxRenderedRows javadoc)
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder content = new StringBuilder();
        content.append(count).append(" Images\n");
        content.append("<span id='imageListFilterResultCount'></span>\n");
        content.append("<script type=\"text/javascript\">").append(json).append("</script>\n");
        content.append("<div>").append(WebUtil.standardFilterControl("imageFilter", null)).append("</div>\n");
        content.append(letterFilter.render());
        content.append(tbl.toXHtml());

        WebPage webPage = new WebPage(page.fileName(), "Image List", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/lists.js").bodyClass("list-page");
        webPage.addContent(content.toString());
        webPage.saveInFolder(page.path());

        PagePath overviewPage = Naming.overviewImages();
        WebPage overviewWebPage = new WebPage(overviewPage.fileName(), "Image List", overviewPage.rootLevel(), appConfig);
        overviewWebPage.addScriptReference("img/lists.js").bodyClass("list-page");
        overviewWebPage.addContent(content.toString());
        overviewWebPage.saveInFolder(overviewPage.path());

        return count;
    }
}
