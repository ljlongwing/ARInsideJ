package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.OverlaySupport;
import arinside.ar.PropertyHelper;
import arinside.ar.SchemaSource;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.SchemaTypeIndex;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Form;

import java.util.List;

/**
 * Java port of CDocMain::SchemaList/SchemaListJson + output/SchemaTable.{h,cpp} - includes the
 * jQuery letter-filter/JSON-search widget (see ActiveLinkOverviewPage's javadoc) plus the
 * "Restrict results to [Regular/Join/View/Dialog/Vendor/Audit/Archive]" type-filter checkboxes
 * (schemaList.js's onCheckTypeFilterForRow reads row[5] against these exact values). Audit(100)/
 * Archive(101) aren't real AR_SCHEMA_* types - they're CARSchema::GetInternalSchemaType's synthetic
 * override for a form that's *the target* of another form's audit trail or archive-to-form
 * destination, sourced from the shared {@link SchemaTypeIndex} (also used by SchemaDetailPage, so
 * both pages agree on a given form's type instead of each computing their own out-of-sync answer).
 * DB Table ID (row slot 0, unused by the row renderer itself) has no Java API accessor (Form
 * declares no getDbTableId()) so it's stubbed at 0, same "not exposed by the convenience API" gap
 * already documented for SchemaDetailPage's General tab.
 */
public final class SchemaOverviewPage {
    private final SchemaSource repo;
    private final AppConfig appConfig;
    private final int serverOverlayMode;
    private final SchemaTypeIndex schemaTypes;

    public SchemaOverviewPage(SchemaSource repo, AppConfig appConfig, int serverOverlayMode, SchemaTypeIndex schemaTypes) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.serverOverlayMode = serverOverlayMode;
        this.schemaTypes = schemaTypes;
    }

    public int render() throws ARException {
        PagePath page = Naming.schemaOverview();

        Table tbl = new Table("schemaList", "TblObjectList");
        tbl.addColumn(30, "Form Name");
        tbl.addColumn(20, "Web Alias");
        tbl.addColumn(5, "Fields");
        tbl.addColumn(5, "Views");
        tbl.addColumn(10, "Type");
        tbl.addColumn(15, "Modified");
        tbl.addColumn(15, "By");

        LetterFilterControl letterFilter = new LetterFilterControl();
        StringBuilder json = new StringBuilder("\nvar schemaList = [");

        int count = 0;
        for (String name : repo.listFormNames()) {
            try {
                Form form = repo.getForm(name);
                if (!OverlaySupport.isVisible(form.getProperties(), serverOverlayMode, appConfig.overlaySupport)) continue;

                int fieldCount = repo.getFields(name).size();
                int viewCount = repo.getViewCount(name);
                String webAlias = PropertyHelper.stringProperty(form.getProperties(), Constants.AR_OPROP_FORM_NAME_WEB_ALIAS);
                int schemaType = schemaTypes.internalSchemaType(name, form.getFormType());

                boolean isOverlaid = OverlaySupport.isOverlaidForNaming(form.getProperties(), serverOverlayMode);
                PagePath detailPage = Naming.schemaDetail(name, isOverlaid);
                ImageTag img = new ImageTag(AREnumLabels.schemaImage(form.getFormType()), page.rootLevel(), OverlaySupport.overlayType(form.getProperties()));
                letterFilter.incStartLetterOf(name);
                SearchIndex.add(name, "schema", detailPage);

                String modified = DateTimeFormat.toHtmlString(form.getLastUpdateTime().getValue());
                String modifiedPlain = DateTimeFormat.toPlainString(form.getLastUpdateTime().getValue());
                String link = URLLink.relativeUrl(page.rootLevel(), detailPage);

                TableRow row = new TableRow();
                row.addCell(URLLink.to(name, detailPage, img, page.rootLevel()).toHtml());
                row.addCell(webAlias);
                row.addCell(new TableCell(fieldCount));
                row.addCell(new TableCell(viewCount));
                row.addCell(AREnumLabels.internalSchemaType(schemaType));
                row.addCell(modified);
                row.addCell(form.getLastChangedBy());
                tbl.addRow(row);

                if (count > 0) json.append(',');
                json.append("[0,\"").append(WebUtil.jsString(name)).append("\",\"")
                    .append(WebUtil.jsString(webAlias)).append("\",")
                    .append(fieldCount).append(',').append(viewCount).append(',')
                    .append(schemaType).append(",\"")
                    .append(WebUtil.jsString(modifiedPlain)).append("\",\"")
                    .append(WebUtil.jsString(form.getLastChangedBy())).append("\",\"")
                    .append(WebUtil.jsString(link)).append("\",")
                    .append(OverlaySupport.overlayType(form.getProperties())).append(']');
                count++;
            } catch (ARException e) {
                System.out.println("EXCEPTION SchemaList '" + name + "': " + e.getMessage());
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        tbl.maxRenderedRows(0); // rows come from lists.js (see Table.maxRenderedRows javadoc)
        json.append("];\nvar rootLevel = ").append(page.rootLevel()).append(";\n");

        StringBuilder typeFilter = new StringBuilder();
        typeFilter.append("<div class='restrictions' style='max-width: 750px;'>");
        typeFilter.append("<div id='clearButton'><button id='typeFilterNone'>Clear All</button></div>");
        typeFilter.append("<div id='listMultiFilter'><div class='left ptop'>Restrict results to: </div><div class='filterOpts'>");
        typeFilter.append(typeCheckbox("typeFilterRegular", 1, "Regular"));
        typeFilter.append(typeCheckbox("typeFilterJoin", 2, "Join"));
        typeFilter.append(typeCheckbox("typeFilterView", 3, "View"));
        typeFilter.append(typeCheckbox("typeFilterDialog", 4, "Dialog"));
        typeFilter.append(typeCheckbox("typeFilterVendor", 5, "Vendor"));
        typeFilter.append(typeCheckbox("typeFilterAudit", 100, "Audit"));
        typeFilter.append(typeCheckbox("typeFilterArchive", 101, "Archive"));
        typeFilter.append("</div></div></div>");

        WebPage webPage = new WebPage(page.fileName(), "Forms", page.rootLevel(), appConfig);
        webPage.addScriptReference("img/lists.js").bodyClass("list-page");
        webPage.addContent(count + " Forms\n");
        webPage.addContent("<span id='schemaListFilterResultCount'></span>\n");
        webPage.addContent("<script type=\"text/javascript\">" + json + "</script>\n");
        webPage.addContent("<div><div id='search'>" + WebUtil.standardFilterControl("formFilter", "search by name or id") + "</div>" + typeFilter + "</div>\n");
        webPage.addContent(letterFilter.render());
        webPage.addContent(tbl.toXHtml());
        webPage.saveInFolder(page.path());

        return count;
    }

    private static String typeCheckbox(String id, int value, String label) {
        return "<div class='checkbox-sl'><input id='" + id + "' type='checkbox' value='" + value + "'/><label for='" + id + "'>&nbsp;" + label + "</label></div>";
    }

}
