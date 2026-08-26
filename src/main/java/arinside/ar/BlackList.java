package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.Reference;
import com.bmc.arsys.api.ReferenceType;

import java.util.HashSet;
import java.util.Set;

/**
 * Java port of lists/BlackList.cpp + util/BlackListItem.cpp. The C++ loads a Packing List by
 * name (the "BlackList" settings.ini key) and treats every object it references as excluded from
 * documentation - which is exactly the same {@code Container.getReferences()} data
 * {@link ContainerRepository}/{@link ContainerDetailPage} already use, so this is a thin wrapper
 * around one more container fetch rather than new API surface.
 */
public final class BlackList {
    private final Set<String> schemas = new HashSet<>();
    private final Set<String> activeLinks = new HashSet<>();
    private final Set<String> filters = new HashSet<>();
    private final Set<String> escalations = new HashSet<>();
    private final Set<String> containers = new HashSet<>();
    private final Set<String> menus = new HashSet<>();
    private final Set<String> images = new HashSet<>();

    /** Empty blacklist (matches CBlackList::LoadFromServer's early-return for an empty packingListName). */
    public static BlackList empty() {
        return new BlackList();
    }

    public static BlackList loadFromServer(ArClient client, String packingListName) {
        BlackList bl = new BlackList();
        if (packingListName == null || packingListName.isBlank()) return bl;

        System.out.println("Loading blacklist from packing list '" + packingListName + "'");
        try {
            Container container = client.raw().getContainer(packingListName);
            bl.containers.add(packingListName); // the blacklist container itself is always excluded too

            if (container.getReferences() != null) {
                for (Reference ref : container.getReferences()) {
                    Set<String> target = bl.setFor(ref.getReferenceType());
                    if (target != null && ref.getName() != null) target.add(ref.getName());
                }
            }
        } catch (ARException e) {
            System.out.println("[WARN] Could not load blacklist packing list '" + packingListName + "': " + e.getMessage());
        }
        return bl;
    }

    private Set<String> setFor(ReferenceType type) {
        if (type == null) return null;
        int code = type.toInt();
        if (code == ReferenceType.SCHEMA.toInt()) return schemas;
        if (code == ReferenceType.ACTIVELINK.toInt()) return activeLinks;
        if (code == ReferenceType.FILTER.toInt()) return filters;
        if (code == ReferenceType.ESCALATION.toInt()) return escalations;
        if (code == ReferenceType.CONTAINER.toInt()) return containers;
        if (code == ReferenceType.CHAR_MENU.toInt()) return menus;
        if (code == ReferenceType.IMAGE.toInt()) return images;
        return null;
    }

    public boolean containsSchema(String name) { return schemas.contains(name); }
    public boolean containsActiveLink(String name) { return activeLinks.contains(name); }
    public boolean containsFilter(String name) { return filters.contains(name); }
    public boolean containsEscalation(String name) { return escalations.contains(name); }
    public boolean containsContainer(String name) { return containers.contains(name); }
    public boolean containsMenu(String name) { return menus.contains(name); }
    public boolean containsImage(String name) { return images.contains(name); }
}
