package arinside.doc;

import arinside.ar.ImageSource;
import arinside.ar.OverlaySupport;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.ImageReferenceIndex;
import arinside.scan.WorkflowReferenceIndex;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Java port of doc/DocImageDetails.cpp + the PAGE_IMAGE_DATA raw-file writer in FileNaming.cpp. */
public final class ImageDetailPage {
    private final ImageSource repo;
    private final AppConfig appConfig;
    private final WorkflowReferenceIndex workflowIndex;
    private final ImageReferenceIndex imageRefs;
    private final Set<String> knownUserNames;
    private final int serverOverlayMode;

    public ImageDetailPage(ImageSource repo, AppConfig appConfig, WorkflowReferenceIndex workflowIndex, ImageReferenceIndex imageRefs, Set<String> knownUserNames, int serverOverlayMode) {
        this.repo = repo;
        this.appConfig = appConfig;
        this.workflowIndex = workflowIndex;
        this.imageRefs = imageRefs;
        this.knownUserNames = knownUserNames;
        this.serverOverlayMode = serverOverlayMode;
    }

    /** The fetch half - safe to run on a pooled read connection. */
    public Image fetch(ImageSource repo, String name) throws ARException {
        return repo.getImage(name);
    }

    /** Fused fetch+render, for callers (file mode) that don't route through the parallel read/write pools. */
    public void render(String name) throws ARException {
        render(name, fetch(repo, name));
    }

    /** The render+write half - pure local work, safe to run on the write pool. */
    public void render(String name, Image img) throws ARException {
        render(name, img, (Image) null);
    }

    /**
     * Same as {@link #render(String, Image)}, plus an overlay-diff summary (or, on a base-layer
     * page, a reciprocal note) when {@code base} is non-null - see {@link OverlayDiff}. Unlike the
     * other 6 overlay-capable types, no overlaid Image existed on the test server this feature was
     * developed/verified against - wired up the same way as the proven types (mechanical, matches
     * an already-working pattern), but not live-verified the way the others were.
     */
    public void render(String name, Image img, Image base) throws ARException {
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(img.getProperties(), serverOverlayMode);
        PagePath page = Naming.imageDetail(name, isOverlaid);

        String ext = (img.getType() == null || img.getType().isBlank()) ? "img" : img.getType().toLowerCase();
        String dataFileName = Naming.imageDataFileName(name, isOverlaid, ext);
        if (img.getImageData() != null) {
            try {
                Path dataFile = Path.of(appConfig.targetFolder, page.path(), dataFileName);
                Files.createDirectories(dataFile.getParent());
                Files.write(dataFile, img.getImageData().getValue());
            } catch (IOException e) {
                System.out.println("EXCEPTION writing image data for '" + name + "': " + e.getMessage());
            }
        }

        WebPage webPage = new WebPage(page.fileName(), name, page.rootLevel(), appConfig);

        String head = URLLink.to("Images", Naming.imageOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Image, page.rootLevel()).toHtml() + WebUtil.objName(name);
        webPage.addContentHead(head);

        if (isOverlaid) {
            webPage.addContent(OverlayDiff.renderBaseLayerNote(URLLink.to("overlay page", Naming.imageDetail(name, false), ImageTag.Id.Image, page.rootLevel()).toHtml()));
        } else if (base != null) {
            webPage.addContent(overlaySummary(img, base, page.rootLevel()));
        }

        Table tbl = new Table("imageGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        int overlayType = OverlaySupport.overlayType(img.getProperties());
        if (overlayType != Constants.AR_ORIGINAL_OBJECT) {
            tbl.addRow(new TableRow().addCellList("Customization Type", OverlaySupport.customizationTypeLabel(overlayType)));
        }
        tbl.addRow(new TableRow().addCellList("Description", img.getDescription() == null ? "" : WebUtil.validate(img.getDescription())));
        tbl.addRow(new TableRow().addCellList("Type", img.getType() == null ? "" : img.getType()));
        int size = img.getImageData() == null ? 0 : img.getImageData().getSize();
        tbl.addRow(new TableRow().addCellList("Size", size + " bytes"));
        webPage.addContent(tbl.toXHtml());

        webPage.addContent("<img src=\"" + dataFileName + "\" alt=\"" + WebUtil.validate(name) + "\" />");

        webPage.addContent(workflowReference(name, page.rootLevel()));

        // Java port of DocImageDetails.cpp's pInside->ServerObjectHistory(&image, rootLevel) call -
        // the same shared Owner/Last-changed/History-Log/Helptext widget every other ObjectBase
        // detail page uses, replacing this page's own previous ad-hoc Owner/Last-changed-by/Last-
        // modified rows (plain unlinked text, no History Log/Helptext rows at all). Unlike User/
        // Group/Role (whose GetHelpText()/GetChangeDiary() are hardcoded to return NULL), core/
        // ARImage.cpp's real implementations delegate to imageList.ImageGetHelptext()/
        // ImageGetChangeDiary() - images genuinely can have help text and a real change diary, so
        // this surfaces real content, not just a structurally-complete-but-always-empty pair of rows.
        webPage.addContent(ServerObjectHistoryWidget.render(img, knownUserNames, page.rootLevel()));

        if (workflowIndex != null) {
            workflowIndex.addIfOverlayOrCustom(img.getProperties(),
                new WorkflowReferenceIndex.Ref(name, "Image", ImageTag.Id.Image, Naming.imageDetail(name, false)), null, img.getLastUpdateTime(), img.getLastChangedBy());
        }

        webPage.saveInFolder(page.path());
    }

    /**
     * "Changes from Base Layer" summary - properties (see {@link OverlayDiff}) plus a checksum-
     * based "image data changed" flag and description/type text diff. {@code getImageData()}/
     * {@code getCheckSum()}/{@code getDescription()}/{@code getType()} all live outside
     * getProperties() entirely (confirmed via the Java API), same structural-content situation as
     * every other type this feature covers - a byte-for-byte image comparison isn't attempted, the
     * checksum is the cheap, correct signal for "did the picture itself change".
     */
    private String overlaySummary(Image img, Image base, int rootLevel) {
        List<OverlayDiff.PropChange> propertyDiff = OverlayDiff.diffProperties(base.getProperties(), img.getProperties());
        boolean dataChanged = !java.util.Objects.equals(base.getCheckSum(), img.getCheckSum());
        boolean descriptionChanged = !java.util.Objects.equals(base.getDescription(), img.getDescription());
        boolean typeChanged = !java.util.Objects.equals(base.getType(), img.getType());
        if (!dataChanged && !descriptionChanged && !typeChanged && propertyDiff.isEmpty()) {
            return "<div class=\"overlaySummary\"><p><b>This object is an overlay, but no differences from its base layer were found.</b></p></div>\n";
        }
        StringBuilder sb = new StringBuilder("<div class=\"overlaySummary\">\n<h2>");
        sb.append(new ImageTag(ImageTag.Id.Document, rootLevel).toHtml()).append("Changes from Base Layer</h2>\n<div>\n<ul>\n");
        if (dataChanged) sb.append("<li>Image data changed</li>\n");
        if (descriptionChanged) sb.append("<li>Description changed: \"").append(nullToEmpty(base.getDescription())).append("\" &rarr; \"").append(nullToEmpty(img.getDescription())).append("\"</li>\n");
        if (typeChanged) sb.append("<li>Type changed: \"").append(nullToEmpty(base.getType())).append("\" &rarr; \"").append(nullToEmpty(img.getType())).append("\"</li>\n");
        sb.append("</ul>\n");
        sb.append(OverlayDiff.renderPropertyTable(propertyDiff));
        sb.append("</div>\n</div>\n");
        return sb.toString();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * Java port of DocImageDetails.cpp's WorkflowReferenceTable section, sourced from
     * {@link ImageReferenceIndex} (see its javadoc for the covered reference kinds - VUI background/
     * title-bar images, per-field push-button/image display properties, packing-list members).
     */
    private String workflowReference(String imageName, int rootLevel) {
        List<ImageReferenceIndex.Ref> refs = imageRefs.forImage(imageName);
        if (refs.isEmpty()) return "";
        Table tbl = new Table("imageWorkflowRefs", "TblObjectList");
        tbl.description = "Workflow Reference";
        tbl.addColumn(15, "Type");
        tbl.addColumn(65, "Name");
        tbl.addColumn(20, "Detail");
        for (ImageReferenceIndex.Ref ref : refs) {
            tbl.addRow(new TableRow().addCellList(ref.typeLabel(),
                URLLink.to(ref.name(), ref.link(), ref.icon(), rootLevel).toHtml(), ref.detail()));
        }
        return tbl.toXHtml();
    }
}
