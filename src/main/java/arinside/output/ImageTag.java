package arinside.output;

import com.bmc.arsys.api.Constants;

import java.util.EnumMap;
import java.util.Map;

/**
 * Java port of output/ImageTag.{h,cpp}. The overlay-aware constructor ({@link #ImageTag(Id, int, int)})
 * composites a small overlay/custom badge onto the base type icon - the exact same visual
 * convention already ported into arshelper.js's own {@code getIcon()} (same
 * overlay.gif/custom.gif assets, same "badge as foreground img, base icon as CSS background"
 * trick), which every *List.js row-renderer already calls (schemaList.js/actlinkList.js/
 * filterList.js/escalationList.js/menuList.js/containerList.js/imageList.js) - but only for rows
 * the JS filter widget re-renders after the user types into the filter box, never for the plain
 * server-rendered rows shown on initial page load. This constructor closes that gap server-side so
 * the badge is visible immediately, not just after interacting with the filter.
 */
public final class ImageTag {

    public enum Id {
        NoImage, Schema, SchemaRegular, SchemaJoin, SchemaView, SchemaDialog, SchemaVendor,
        Server, Document, Folder, ActiveLink, Filter, Escalation, Menu, ActiveLinkGuide,
        FilterGuide, Application, PackingList, Webservice, Image, User, Group, Role,
        Hidden, Visible, Edit, Next, Prev, SortAsc, SortDesc, Up, Down, Association
    }

    private record Dim(int w, int h) {}

    private static final Dim DEFAULT_DIM = new Dim(16, 16);
    private static final Map<Id, Dim> DIMENSIONS = new EnumMap<>(Id.class);
    static {
        DIMENSIONS.put(Id.Document, new Dim(15, 10));
        DIMENSIONS.put(Id.Folder, new Dim(16, 13));
        DIMENSIONS.put(Id.Hidden, new Dim(18, 18));
        DIMENSIONS.put(Id.Visible, new Dim(18, 18));
        DIMENSIONS.put(Id.Edit, new Dim(18, 18));
        DIMENSIONS.put(Id.Prev, new Dim(10, 10));
        DIMENSIONS.put(Id.Next, new Dim(10, 10));
        DIMENSIONS.put(Id.Up, new Dim(14, 10));
        DIMENSIONS.put(Id.Down, new Dim(14, 10));
    }

    private static final Map<Id, String> FILE_NAMES = new EnumMap<>(Id.class);
    static {
        FILE_NAMES.put(Id.NoImage, "");
        FILE_NAMES.put(Id.Schema, "schema.gif");
        FILE_NAMES.put(Id.SchemaRegular, "schema.gif"); // aliases ImageTag::Schema (same enum value = 1 in C++)
        FILE_NAMES.put(Id.SchemaJoin, "schema_join.gif");
        FILE_NAMES.put(Id.SchemaView, "schema_view.gif");
        FILE_NAMES.put(Id.SchemaDialog, "schema_display.gif");
        FILE_NAMES.put(Id.SchemaVendor, "schema_vendor.gif");
        FILE_NAMES.put(Id.Server, "server.gif");
        FILE_NAMES.put(Id.Document, "doc.gif");
        FILE_NAMES.put(Id.Folder, "folder.gif");
        FILE_NAMES.put(Id.ActiveLink, "active_link.gif");
        FILE_NAMES.put(Id.Filter, "filter.gif");
        FILE_NAMES.put(Id.Escalation, "escalation.gif");
        FILE_NAMES.put(Id.Menu, "menu.gif");
        FILE_NAMES.put(Id.ActiveLinkGuide, "al_guide.gif");
        FILE_NAMES.put(Id.FilterGuide, "filter_guide.gif");
        FILE_NAMES.put(Id.Application, "application.gif");
        FILE_NAMES.put(Id.PackingList, "packing_list.gif");
        FILE_NAMES.put(Id.Webservice, "webservice.gif");
        FILE_NAMES.put(Id.Image, "image.gif");
        FILE_NAMES.put(Id.User, "user.gif");
        FILE_NAMES.put(Id.Group, "group.gif");
        FILE_NAMES.put(Id.Role, "role.gif");
        FILE_NAMES.put(Id.Hidden, "hidden.gif");
        FILE_NAMES.put(Id.Visible, "visible.gif");
        FILE_NAMES.put(Id.Edit, "edit.gif");
        FILE_NAMES.put(Id.Next, "next.gif");
        FILE_NAMES.put(Id.Prev, "prev.gif");
        FILE_NAMES.put(Id.SortAsc, "sort_asc.gif");
        FILE_NAMES.put(Id.SortDesc, "sort_desc.gif");
        FILE_NAMES.put(Id.Up, "up.gif");
        FILE_NAMES.put(Id.Down, "down.gif");
        // Associations are new (post-C++) functionality, so there's no icon carried over from the
        // original res/ tree - chapter.gif is otherwise unused in this port, a reasonable stand-in
        // for "a link/relationship between two things" without needing a new asset.
        FILE_NAMES.put(Id.Association, "chapter.gif");
    }

    private final Id id;
    private final int rootLevel;
    private final int overlayType;

    public ImageTag(Id id, int rootLevel) {
        this(id, rootLevel, Constants.AR_ORIGINAL_OBJECT);
    }

    /** {@code overlayType} is {@code OverlaySupport.overlayType(props)}'s raw result - AR_OVERLAY_OBJECT/AR_CUSTOM_OBJECT get a badge, anything else (including AR_ORIGINAL_OBJECT and the hidden AR_OVERLAID_OBJECT base layer) renders the plain icon, matching arshelper.js's own getIcon(). */
    public ImageTag(Id id, int rootLevel, int overlayType) {
        this.id = id;
        this.rootLevel = rootLevel;
        this.overlayType = overlayType;
    }

    public String toHtml() {
        if (id == Id.NoImage) return "";
        String src = FILE_NAMES.get(id);
        Dim dim = DIMENSIONS.getOrDefault(id, DEFAULT_DIM);
        String mainSrc = RootPath.of(rootLevel) + "img/" + src;

        String badge = switch (overlayType) {
            case Constants.AR_OVERLAY_OBJECT -> "overlay.gif";
            case Constants.AR_CUSTOM_OBJECT -> "custom.gif";
            default -> null;
        };
        if (badge != null) {
            return "<img src=\"" + RootPath.of(rootLevel) + "img/" + badge + "\" "
                + "width=\"" + dim.w() + "\" height=\"" + dim.h() + "\" alt=\"" + src + "\" "
                + "style=\"background:url(" + mainSrc + ")\" />";
        }
        return "<img src=\"" + mainSrc + "\" width=\"" + dim.w() + "\" height=\"" + dim.h() + "\" alt=\"" + src + "\" />";
    }

    @Override
    public String toString() { return toHtml(); }
}
