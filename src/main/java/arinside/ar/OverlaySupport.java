package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.PropertyMap;
import com.bmc.arsys.api.ServerInfoMap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java port of the overlay-support pieces of ARInside.cpp/core/ARServerObject.cpp (7.6.04+
 * overlay feature): CARInside::SetupOverlaySupport, IsVisibleObject, IsObjectOverlaid.
 *
 * <p>The C++'s {@code ARSetSessionConfiguration(AR_SESS_CONTROL_PROP_API_OVERLAYGROUP,
 * AR_OVERLAY_CLIENT_MODE_FULL)} step maps to {@code ApiUserContextBase.setOverlayGroup(String)}
 * (inherited by {@code ARServerUser}), called with the sentinel string {@code "-2"}
 * ({@code ar.h}'s {@code AR_OVERLAY_CLIENT_MODE_FULL}). Key behavior of this mode:
 * <ul>
 * <li>{@code server.setOverlayGroup("-2")} can be toggled mid-session, no re-login needed;
 *   {@code setOverlayGroup(null)} (or {@code ""}) resets to default behavior.</li>
 * <li>With it active, {@code getListForm()} (and getListActiveLink/getListFilter/
 *   getListEscalation) returns every name from the default list plus one extra entry per object
 *   with a hidden base layer, suffixed by the server itself with the same "__o" marker the C++
 *   computes client-side for file naming (e.g. "HPD:Help Desk__o") - a real AR System naming
 *   convention.</li>
 * <li>Lookup semantics flip while in "-2" mode: the plain name (no suffix) now resolves to the
 *   hidden base layer ({@code AR_SMOPROP_OVERLAY_PROPERTY}=1, AR_OVERLAID_OBJECT) instead of the
 *   active/overlay layer; fetching the "__o"-suffixed name explicitly gives back the active/overlay
 *   layer (=2) instead - the reverse of default-mode lookup. Only objects that actually have a
 *   hidden base layer are affected; everything else is unchanged.</li>
 * </ul>
 *
 * <p><b>How this is wired up (see {@code Main.java}</b>): {@code discoverOverlayBaseNames()} below
 * diffs the default-mode name list against the "-2"-mode name list to find which plain names have a
 * hidden base layer (stripping the server's own "__o" suffix). For just those names, the existing
 * {@code SchemaDetailPage}/{@code ActiveLinkDetailPage}/{@code FilterDetailPage}/
 * {@code EscalationDetailPage.render(name)} methods are called again with {@code
 * setOverlayGroup("-2")} active - no changes needed to those classes at all, since they already
 * independently re-fetch the object and compute {@code isOverlaidForNaming} from whatever {@code
 * overlayType} comes back on that fetch (correctly 1 for these names while the session is in "-2"
 * mode) - the correct "__o"-suffixed page falls out of the existing code path automatically.
 * {@code setOverlayGroup} must be reset to {@code null} immediately after this second pass so it
 * doesn't affect the rest of the run's normal fetches.
 */
public final class OverlaySupport {
    private OverlaySupport() {}

    @FunctionalInterface
    public interface NameLister {
        List<String> list() throws ARException;
    }

    /**
     * Diffs the default-mode name list against the "-2" (AR_OVERLAY_CLIENT_MODE_FULL) name list to
     * find plain names with a hidden base layer - see class javadoc. Leaves the session's overlay
     * group reset to default (null) before returning, whether or not an exception occurs.
     */
    public static List<String> discoverOverlayBaseNames(ArClient client, NameLister lister) throws ARException {
        Set<String> defaultNames = new HashSet<>(lister.list());
        client.raw().setOverlayGroup("-2");
        try {
            return lister.list().stream()
                .filter(n -> n.endsWith("__o") && !defaultNames.contains(n))
                .map(n -> n.substring(0, n.length() - "__o".length()))
                .toList();
        } finally {
            client.raw().setOverlayGroup(null);
        }
    }

    /** Fetched once per run (CARInside::overlayMode) - defaults to 1 if the server doesn't report it (matches the C++'s ars764-era default). */
    public static int fetchServerOverlayMode(ArClient client) {
        try {
            ServerInfoMap info = client.raw().getServerInfo(new int[]{Constants.AR_SERVER_INFO_OVERLAY_MODE});
            com.bmc.arsys.api.Value v = info.get(Constants.AR_SERVER_INFO_OVERLAY_MODE);
            if (v != null && v.getValue() instanceof Number n) return n.intValue();
        } catch (ARException e) {
            System.out.println("[WARN] Could not read server overlay mode, defaulting to 1: " + e.getMessage());
        }
        return 1;
    }

    /**
     * Takes the object's property map directly (Form/ActiveLink/Filter/.../getProperties())
     * rather than a common base type - ObjectBase doesn't declare getProperties() (each subtype
     * declares its own, returning ObjectPropertyMap), so there's no shared accessor to call
     * generically here.
     */
    public static int overlayType(PropertyMap props) {
        if (props == null) return Constants.AR_ORIGINAL_OBJECT;
        com.bmc.arsys.api.Value v = props.get(Constants.AR_SMOPROP_OVERLAY_PROPERTY);
        if (v == null || !(v.getValue() instanceof Number n)) return Constants.AR_ORIGINAL_OBJECT;
        return n.intValue();
    }

    /** Ported from core/ARServerObject.cpp IsVisibleObject. */
    public static boolean isVisible(PropertyMap props, int serverOverlayMode, boolean overlaySupportEnabled) {
        if (!overlaySupportEnabled) return true;
        int type = overlayType(props);
        if (serverOverlayMode == 1 && type == Constants.AR_OVERLAID_OBJECT) return false;
        if (serverOverlayMode == 0 && (type == Constants.AR_OVERLAY_OBJECT || type == Constants.AR_CUSTOM_OBJECT)) return false;
        return true;
    }

    /** Ported from output/FileNaming.cpp's IsObjectOverlaid(ARValueStruct*) overload - decides the "__o" file-name suffix. */
    public static boolean isOverlaidForNaming(PropertyMap props, int serverOverlayMode) {
        int type = overlayType(props);
        if (serverOverlayMode == 1 && type == Constants.AR_OVERLAID_OBJECT) return true;
        if (serverOverlayMode == 0 && type == Constants.AR_OVERLAY_OBJECT) return true;
        return false;
    }

    /** Java port of core/AREnum.cpp's CAREnum::GetOverlayType - used by CustomWorkflowPage's "Type" column. */
    public static String overlayTypeLabel(int overlayType) {
        if (overlayType == Constants.AR_OVERLAID_OBJECT) return "Original";
        if (overlayType == Constants.AR_OVERLAY_OBJECT) return "Overlay";
        if (overlayType == Constants.AR_CUSTOM_OBJECT) return "Custom";
        return "";
    }
}
