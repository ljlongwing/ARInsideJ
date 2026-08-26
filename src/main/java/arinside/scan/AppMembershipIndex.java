package arinside.scan;

import arinside.ar.ContainerSource;
import arinside.ar.SchemaSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.CharacterFieldLimit;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.ContainerOwner;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.FieldLimit;
import com.bmc.arsys.api.Reference;
import com.bmc.arsys.api.ReferenceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of the AppRefName-assignment side effect scattered through
 * doc/DocApplicationDetails.cpp's SearchForms/SearchActiveLinks/SearchFilters/SearchContainer -
 * "which Application container owns this form (directly) / packing list (directly, via the app's
 * own CONTAINER-type content)". The C++ derives every OTHER object type's app ownership from these
 * two direct assignments (an active link/filter/escalation's app = the app of any form it's
 * attached to; a guide/filter-guide's app = the app of its owner form, via ContainerForms/
 * ContainerOwner - see PermissionIndex's containerApp()) rather than a separate per-type pass, so
 * this index only needs to capture the two direct, no-indirection cases. Confirmed via source that
 * ARCON_WEBSERVICE containers are never assigned an app at all (SearchContainer's switch has no
 * case for it) - a genuine gap in the original tool, not something to "fix" here; PermissionIndex's
 * containerApp() replicates that same gap by always returning null for webservices.
 *
 * Built as a small, synchronous, upfront pass (like PermissionIndex's knownMenuNames) since
 * Application containers are a bounded, typically small set (dozens, not thousands) - must finish
 * before PermissionIndex's parallel form/active-link/container passes start, since those need this
 * data to resolve each object's own app membership as they're indexed.
 */
public final class AppMembershipIndex {
    private final Map<String, String> formApp = new HashMap<>();
    private final Map<String, String> packApp = new HashMap<>();
    private final Map<String, String> menuApp = new HashMap<>();
    private final Map<String, List<String>> formsByApp = new HashMap<>();
    private final Map<String, List<String>> packsByApp = new HashMap<>();
    private final Map<String, List<String>> menusByApp = new HashMap<>();

    /** schemaRepo may be null when the caller doesn't need menu-app derivation (matches SearchMenus needing a per-app-owned-form field scan on top of the container-content pass the other two use). */
    public static AppMembershipIndex build(ContainerSource containerRepo, SchemaSource schemaRepo) throws ARException {
        AppMembershipIndex idx = new AppMembershipIndex();
        for (String appName : containerRepo.listContainerNames(Constants.ARCON_APP)) {
            Container app;
            try {
                app = containerRepo.getContainer(appName);
            } catch (ARException e) {
                System.out.println("EXCEPTION indexing app membership for '" + appName + "': " + e.getMessage());
                continue;
            }
            if (app.getReferences() == null) continue;
            for (Reference ref : app.getReferences()) {
                ReferenceType type = ref.getReferenceType();
                if (type == null || ref.getName() == null) continue;
                if (type.toInt() == ReferenceType.SCHEMA.toInt()) {
                    idx.formApp.put(ref.getName(), appName);
                    idx.formsByApp.computeIfAbsent(appName, k -> new java.util.ArrayList<>()).add(ref.getName());
                    if (schemaRepo != null) idx.indexMenusForForm(schemaRepo, ref.getName(), appName);
                } else if (type.toInt() == ReferenceType.CONTAINER.toInt()) {
                    // Only packing-list members count for app-ownership purposes (matches
                    // SearchContainer's ARCON_PACK case) - a referenced guide/webservice container
                    // is handled via its owner form instead, not via this direct app-content path.
                    Container member;
                    try {
                        member = containerRepo.getContainer(ref.getName());
                    } catch (ARException e) {
                        continue;
                    }
                    if (member.getType() == Constants.ARCON_PACK) {
                        idx.packApp.put(ref.getName(), appName);
                        idx.packsByApp.computeIfAbsent(appName, k -> new java.util.ArrayList<>()).add(ref.getName());
                    }
                }
            }
        }
        return idx;
    }

    /**
     * Java port of DocApplicationDetails.cpp's SearchMenus - a menu belongs to an app if any
     * app-owned form has a character field whose charMenu limit names it. Narrower than
     * MenuAttachmentIndex's "attached forms" (field charMenu only, not AL Change-Field actions too)
     * - deliberately matches the C++'s own narrower SearchMenus scope rather than reusing the
     * broader existing index.
     */
    private void indexMenusForForm(SchemaSource schemaRepo, String formName, String appName) {
        List<Field> fields;
        try {
            fields = schemaRepo.getFields(formName);
        } catch (ARException e) {
            return;
        }
        for (Field field : fields) {
            FieldLimit limit = field.getFieldLimit();
            if (!(limit instanceof CharacterFieldLimit charLimit)) continue;
            String menuName = charLimit.getCharMenu();
            if (menuName != null && !menuName.isEmpty()) {
                menuApp.putIfAbsent(menuName, appName);
                List<String> menus = menusByApp.computeIfAbsent(appName, k -> new java.util.ArrayList<>());
                if (!menus.contains(menuName)) menus.add(menuName);
            }
        }
    }

    /** The owning app of a form, or null if the form isn't a direct member of any Application container. */
    public String formApp(String formName) {
        return formApp.get(formName);
    }

    /** The owning app of a packing list, or null if it isn't a direct member of any Application container. */
    public String packApp(String packingListName) {
        return packApp.get(packingListName);
    }

    /** The owning app of a menu (via an app-owned form's charMenu limit), or null. */
    public String menuApp(String menuName) {
        return menuApp.get(menuName);
    }

    /**
     * The owning app of a GUIDE/FILTER_GUIDE container, derived from its owner form's app (matches
     * DocApplicationDetails.cpp's SearchContainer ARCON_GUIDE/ARCON_FILTER_GUIDE case - schema.
     * GetActLinkGuides()/GetFilterGuides(), the reverse of ContainerOwner). First-match if a guide
     * has multiple owner forms with different apps (real data is expected to have one consistent
     * app per guide in practice).
     */
    public String guideApp(List<ContainerOwner> owners) {
        if (owners == null) return null;
        for (ContainerOwner owner : owners) {
            if (owner.getType() == ContainerOwner.SCHEMA && owner.getName() != null) {
                String app = formApp.get(owner.getName());
                if (app != null) return app;
            }
        }
        return null;
    }

    /** The app's own directly-owned member forms (ARREF_SCHEMA content entries), matching DocApplicationDetails.cpp's SearchForms - forward direction of formApp(). */
    public List<String> formsOf(String appName) { return formsByApp.getOrDefault(appName, List.of()); }

    /** The app's own directly-owned member packing lists (ARREF_CONTAINER content entries of type ARCON_PACK), matching SearchContainer's ARCON_PACK case. */
    public List<String> packsOf(String appName) { return packsByApp.getOrDefault(appName, List.of()); }

    /** Every menu named by a charMenu limit on one of the app's own member forms, matching SearchMenus. */
    public List<String> menusOf(String appName) { return menusByApp.getOrDefault(appName, List.of()); }
}
