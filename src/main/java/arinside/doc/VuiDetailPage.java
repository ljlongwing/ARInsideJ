package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.PropertyHelper;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.ImageReferenceIndex;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.DisplayPropertyMap;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.View;

import java.util.List;

/**
 * Java port of doc/DocVUIDetails.cpp (the "vui_*.htm" pages) - General info + the list of fields
 * shown on this VUI, now including their per-VUI DisplayProperties (position/visible/font/color -
 * see fieldDisplayProps()). Unlike the C++, which decodes these from a raw AR_OPROP-style property
 * list one tag at a time, the Java API exposes them as a PropertyMap keyed by AR_DPROP_* constants
 * directly on Field.getDisplayInstance().get(vuiId) - reused via PropertyHelper.stringProperty's
 * existing generic Value-to-string extraction rather than a new per-type accessor. Scoped to the
 * handful of tags "position/font/color" actually names (BBOX/VISIBLE/COLOR_FILL/COLOR_TEXT/
 * TEXT_FONT_STYLE/TEXT_FONT_SIZE) rather than all ~300 AR_DPROP_* tags that exist - a deliberate,
 * bounded scope, not an oversight. (The C++'s JoinFormReferences/FieldMapping - join/view-schema
 * field mapping - actually lives on CDocFieldDetails, not this page's C++ counterpart
 * DocVUIDetails.cpp; it's ported on {@link FieldDetailPage}, see its fieldMapping()/
 * joinFormReferences() methods, not here.) The page title/heading uses
 * {@link arinside.ar.AREnumLabels#vuiDisplayName} - corrected 2026-08-26 after this javadoc's own
 * former "no display-name field available" claim turned out to be stale/wrong: {@code View} extends
 * {@code ObjectBase}, which does carry a real {@code getName()}, and the AR_DPROP_LABEL display
 * property (already used elsewhere, e.g. SchemaDetailPage's Views-tab table) is available too - see
 * that helper's own javadoc for why Label wins over Name.
 *
 * <p>Also feeds {@link ImageReferenceIndex} (ImageDetailPage's "Workflow Reference" section) as a
 * side effect - matches DocVUIDetails.cpp's SpecialPropertyCallback, which resolves
 * AR_DPROP_DETAIL_PANE_IMAGE/AR_DPROP_TITLE_BAR_ICON_IMAGE (VUI-level) and
 * AR_DPROP_IMAGE/AR_DPROP_PUSH_BUTTON_IMAGE (per-field) display properties to real image names.
 */
public final class VuiDetailPage {
    private final AppConfig appConfig;
    private final ImageReferenceIndex imageRefs;

    public VuiDetailPage(AppConfig appConfig, ImageReferenceIndex imageRefs) {
        this.appConfig = appConfig;
        this.imageRefs = imageRefs;
    }

    public void render(String schemaName, boolean schemaOverlaid, View vui, List<Field> fieldsOnThisVui) {
        String title = AREnumLabels.vuiDisplayName(vui);
        PagePath page = Naming.schemaVuiDetail(schemaName, schemaOverlaid, vui.getVUIId());
        WebPage webPage = new WebPage(page.fileName(), title, page.rootLevel(), appConfig);

        String head = URLLink.to(schemaName, Naming.schemaDetail(schemaName, schemaOverlaid), ImageTag.Id.Schema, page.rootLevel()).toHtml()
            + " &gt; " + WebUtil.objName(title);
        webPage.addContentHead(head);

        ImageReferenceIndex.Ref vuiRef = new ImageReferenceIndex.Ref(schemaName + " / " + title, "VUI", ImageTag.Id.Document, page, "Background/Icon");

        Table tbl = new Table("vuiGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("VUI ID", Integer.toString(vui.getVUIId())));
        tbl.addRow(new TableRow().addCellList("Type", AREnumLabels.vuiType(vui.getVUIType())));
        if (vui.getLocale() != null && !vui.getLocale().isEmpty()) {
            tbl.addRow(new TableRow().addCellList("Locale", vui.getLocale()));
        }
        webPage.addContent(tbl.toXHtml());

        if (vui.getDisplayProperties() != null) {
            imageRefs.add(PropertyHelper.stringProperty(vui.getDisplayProperties(), Constants.AR_DPROP_DETAIL_PANE_IMAGE), vuiRef);
            imageRefs.add(PropertyHelper.stringProperty(vui.getDisplayProperties(), Constants.AR_DPROP_TITLE_BAR_ICON_IMAGE), vuiRef);
        }

        Table fieldsTbl = new Table("vuiFields", "TblObjectList");
        fieldsTbl.description = "Fields";
        fieldsTbl.addColumn(22, "Field Name");
        fieldsTbl.addColumn(8, "Field ID");
        fieldsTbl.addColumn(15, "Datatype");
        fieldsTbl.addColumn(15, "Position");
        fieldsTbl.addColumn(20, "Colors (Fill/Text)");
        fieldsTbl.addColumn(20, "Font (Style/Size)");
        for (Field f : fieldsOnThisVui) {
            DisplayPropertyMap dp = f.getDisplayInstance() == null ? null : f.getDisplayInstance().get(vui.getVUIId());
            fieldsTbl.addRow(new TableRow().addCellList(
                URLLink.to(f.getName(), Naming.schemaFieldDetail(schemaName, schemaOverlaid, f.getFieldID()), ImageTag.Id.Document, page.rootLevel()).toHtml(),
                Integer.toString(f.getFieldID()),
                AREnumLabels.dataType(f.getDataType()),
                PropertyHelper.stringProperty(dp, Constants.AR_DPROP_BBOX),
                PropertyHelper.stringProperty(dp, Constants.AR_DPROP_COLOR_FILL) + " / " + PropertyHelper.stringProperty(dp, Constants.AR_DPROP_COLOR_TEXT),
                PropertyHelper.stringProperty(dp, Constants.AR_DPROP_TEXT_FONT_STYLE) + " / " + PropertyHelper.stringProperty(dp, Constants.AR_DPROP_TEXT_FONT_SIZE)));

            if (dp != null) {
                ImageReferenceIndex.Ref fieldRef = new ImageReferenceIndex.Ref(f.getName(), "Field",
                    ImageTag.Id.Document, Naming.schemaFieldDetail(schemaName, schemaOverlaid, f.getFieldID()), title);
                imageRefs.add(PropertyHelper.stringProperty(dp, Constants.AR_DPROP_IMAGE), fieldRef);
                imageRefs.add(PropertyHelper.stringProperty(dp, Constants.AR_DPROP_PUSH_BUTTON_IMAGE), fieldRef);
            }
        }
        webPage.addContent(fieldsTbl.toXHtml());
        webPage.addContent(ObjectPropertiesTable.render(vui.getDisplayProperties()));

        webPage.saveInFolder(page.path());
    }
}
