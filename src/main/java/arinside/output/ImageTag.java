package arinside.output;

import com.bmc.arsys.api.Constants;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renders an object-type icon as a reference into the icon sprite {@code app.js} injects into every
 * page ({@code <svg class="ico"><use href="#i-schema"/></svg>}). The sprite is inlined by JS rather
 * than shipped as an .svg file because external-file {@code <use href>} is blocked under
 * {@code file://} in Chrome. The overlay-aware constructor ({@link #ImageTag(Id, int, int)}) stacks
 * a small overlay/custom badge on top of the base icon - same visual convention the C++ tool
 * composited from {@code overlay.gif}/{@code custom.gif}.
 *
 * {@code rootLevel} is retained on the constructors for call-site compatibility but no longer
 * affects output (the sprite reference is a bare fragment).
 */
public final class ImageTag {

    public enum Id {
        NoImage, Schema, SchemaRegular, SchemaJoin, SchemaView, SchemaDialog, SchemaVendor,
        Server, Document, Folder, ActiveLink, Filter, Escalation, Menu, ActiveLinkGuide,
        FilterGuide, Application, PackingList, Webservice, Image, User, Group, Role,
        Hidden, Visible, Edit, Next, Prev, SortAsc, SortDesc, Up, Down, Association
    }

    private static final Map<Id, String> SYMBOL = new EnumMap<>(Id.class);
    static {
        SYMBOL.put(Id.NoImage, "");
        SYMBOL.put(Id.Schema, "schema");
        SYMBOL.put(Id.SchemaRegular, "schema");
        SYMBOL.put(Id.SchemaJoin, "schema-join");
        SYMBOL.put(Id.SchemaView, "schema-view");
        SYMBOL.put(Id.SchemaDialog, "schema-dialog");
        SYMBOL.put(Id.SchemaVendor, "schema-vendor");
        SYMBOL.put(Id.Server, "server");
        SYMBOL.put(Id.Document, "document");
        SYMBOL.put(Id.Folder, "folder");
        SYMBOL.put(Id.ActiveLink, "active-link");
        SYMBOL.put(Id.Filter, "filter");
        SYMBOL.put(Id.Escalation, "escalation");
        SYMBOL.put(Id.Menu, "menu");
        SYMBOL.put(Id.ActiveLinkGuide, "al-guide");
        SYMBOL.put(Id.FilterGuide, "filter-guide");
        SYMBOL.put(Id.Application, "application");
        SYMBOL.put(Id.PackingList, "packing-list");
        SYMBOL.put(Id.Webservice, "webservice");
        SYMBOL.put(Id.Image, "image");
        SYMBOL.put(Id.User, "user");
        SYMBOL.put(Id.Group, "group");
        SYMBOL.put(Id.Role, "role");
        SYMBOL.put(Id.Hidden, "hidden");
        SYMBOL.put(Id.Visible, "visible");
        SYMBOL.put(Id.Edit, "edit");
        SYMBOL.put(Id.Next, "next");
        SYMBOL.put(Id.Prev, "prev");
        SYMBOL.put(Id.SortAsc, "sort-asc");
        SYMBOL.put(Id.SortDesc, "sort-desc");
        SYMBOL.put(Id.Up, "up");
        SYMBOL.put(Id.Down, "down");
        SYMBOL.put(Id.Association, "association");
    }

    private final Id id;
    private final int rootLevel;
    private final int overlayType;

    public ImageTag(Id id, int rootLevel) {
        this(id, rootLevel, Constants.AR_ORIGINAL_OBJECT);
    }

    /** {@code overlayType} is {@code OverlaySupport.overlayType(props)}'s raw result - AR_OVERLAY_OBJECT/AR_CUSTOM_OBJECT get a badge, anything else renders the plain icon. */
    public ImageTag(Id id, int rootLevel, int overlayType) {
        this.id = id;
        this.rootLevel = rootLevel;
        this.overlayType = overlayType;
    }

    public String toHtml() {
        if (id == Id.NoImage) return "";
        String sym = SYMBOL.get(id);
        if (sym == null || sym.isEmpty()) return "";

        String base = use(sym, "ico");
        String badgeSym = switch (overlayType) {
            case Constants.AR_OVERLAY_OBJECT -> "overlay";
            case Constants.AR_CUSTOM_OBJECT -> "custom";
            default -> null;
        };
        if (badgeSym == null) return base;
        return "<span class=\"ico-badge\">" + base + use(badgeSym, "ico ico-over") + "</span>";
    }

    private static String use(String symbol, String cssClass) {
        return "<svg class=\"" + cssClass + "\" aria-hidden=\"true\"><use href=\"#i-" + symbol + "\"/></svg>";
    }

    @Override
    public String toString() { return toHtml(); }
}
