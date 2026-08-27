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
 * SOLVED (2026-08-15, after three rounds of investigation): the C++'s
 * ARSetSessionConfiguration(AR_SESS_CONTROL_PROP_API_OVERLAYGROUP, AR_OVERLAY_CLIENT_MODE_FULL)
 * step - property 13 set to the string "-2" (`ar.h`'s AR_OVERLAY_CLIENT_MODE_FULL, not an int/bool
 * as earlier rounds assumed) - maps directly to ApiUserContextBase's `setOverlayGroup(String)`,
 * inherited by ARServerUser. **The earlier rounds tried setOverlayFlag(boolean)/setBaseOverlayFlag
 * (boolean), which are a different, unrelated pair of flags - setOverlayGroup(String) was never
 * tried with the "-2" sentinel until this round.** Confirmed empirically against the live server
 * (`arinside.spike.OverlayGroupSpike*`, deleted after use):
 * - `server.setOverlayGroup("-2")` can be toggled on/off **mid-session**, no re-login needed;
 *   `setOverlayGroup(null)` (or `""`) resets to default behavior.
 * - With it active, `getListForm()` (and presumably getListActiveLink/getListFilter/
 *   getListEscalation) returns every name from the default list **plus** one extra entry per
 *   object with a hidden base layer, already suffixed by the *server itself* with the same "__o"
 *   marker the C++ computes client-side for file naming (e.g. "HPD:Help Desk__o") - this is a
 *   real AR System naming convention, not something ARInside invented.
 * - **The lookup semantics flip while in "-2" mode**: the *plain* name (no suffix) now resolves to
 *   the hidden BASE layer (`AR_SMOPROP_OVERLAY_PROPERTY`=1, AR_OVERLAID_OBJECT) instead of the
 *   active/overlay layer; fetching the "__o"-suffixed name explicitly gives back the
 *   active/overlay layer (=2) instead - the reverse of default-mode lookup. Confirmed on a
 *   non-overlaid form too (`getForm("User")` normally, vs the same call with "-2" active) - only
 *   objects that actually have a hidden base layer are affected; everything else is unchanged.
 *
 * **Net effect / how this is wired up (see `Main.java`)**: `discoverOverlayBaseNames()` below diffs
 * the default-mode name list against the "-2"-mode name list to find which plain names have a
 * hidden base layer (stripping the server's own "__o" suffix). For just those names, the existing
 * `SchemaDetailPage`/`ActiveLinkDetailPage`/`FilterDetailPage`/`EscalationDetailPage.render(name)`
 * methods are called AGAIN with `setOverlayGroup("-2")` active - no changes needed to those classes
 * at all, since they already independently re-fetch the object and compute `isOverlaidForNaming`
 * from whatever `overlayType` comes back on that fetch (previously always 2 in practice, now
 * correctly 1 for these names since the session is in "-2" mode) - the correct "__o"-suffixed page
 * falls out of the existing code path automatically. `setOverlayGroup` must be reset to `null`
 * immediately after this second pass so it doesn't affect the other ~100,000 normal fetches.
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

    /** Java port of core/AREnum.cpp's CAREnum::GetOverlayType - used by CustomWorkflowPage's "Type" column. Kept verbatim (not touched by the "Customization Type" feature below) since it's an already-verified C++-parity port for that specific existing page. */
    public static String overlayTypeLabel(int overlayType) {
        if (overlayType == Constants.AR_OVERLAID_OBJECT) return "Original";
        if (overlayType == Constants.AR_OVERLAY_OBJECT) return "Overlay";
        if (overlayType == Constants.AR_CUSTOM_OBJECT) return "Custom";
        return "";
    }

    /**
     * Real BMC Dev Studio wording for this same overlay-type value (its own "Customization Type"
     * column: Unmodified/Overlay/Custom) - confirmed by the user directly, distinct from
     * {@link #overlayTypeLabel}'s C++-ported "Original" wording. Used by the "Changes from Base
     * Layer" feature's own per-object-type "Customization Type" row, which has no C++ precedent to
     * match, so it uses the terminology AR System administrators actually see day to day instead.
     * AR_OVERLAID_OBJECT (the hidden base layer, only ever fetched via the "-2" mechanism) maps to
     * "Unmodified" here rather than "Original" - accurate on its own terms (the base layer itself
     * has nothing customized about IT; the customization lives in the separate overlay layer) and
     * matches what Dev Studio would call it if it could show a base layer at all.
     */
    public static String customizationTypeLabel(int overlayType) {
        if (overlayType == Constants.AR_OVERLAID_OBJECT) return "Unmodified";
        if (overlayType == Constants.AR_OVERLAY_OBJECT) return "Overlay";
        if (overlayType == Constants.AR_CUSTOM_OBJECT) return "Custom";
        return "";
    }
}
