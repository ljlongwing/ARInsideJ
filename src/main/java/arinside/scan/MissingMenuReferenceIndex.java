package arinside.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of DocValidator.cpp's MenuReferenceValidator - "a workflow object's assigned menu name
 * doesn't match a real menu on the server". Built by PermissionIndex.build()'s existing form/
 * active-link/container passes - no extra fetch pass of its own. Matches the C++'s
 * AddMenuReference(), which is called from three real sites (confirmed by reading ARInside.cpp's
 * caller list, not assumed): scan/ScanFields.cpp (a character field's charMenu limit),
 * scan/ScanActiveLinks.cpp (a Change Field action's charMenu), and doc/DocPacklistDetails.cpp (a
 * packing list's char-menu member reference) - all three are ported here via the sealed Entry
 * hierarchy below, one variant per source type. Thread-safe (synchronized list): PermissionIndex.
 * build() runs its passes on the parallel read pool in server mode.
 */
public final class MissingMenuReferenceIndex {

    public sealed interface Entry {
        String menuName();
    }

    public record FieldEntry(String menuName, String formName, String fieldName, int fieldId) implements Entry {}
    public record ActiveLinkEntry(String menuName, String activeLinkName, String branch, int actionIndex) implements Entry {}
    public record PackingListEntry(String menuName, String packingListName) implements Entry {}

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    public void addField(String menuName, String formName, String fieldName, int fieldId) {
        entries.add(new FieldEntry(menuName, formName, fieldName, fieldId));
    }

    public void addActiveLink(String menuName, String activeLinkName, String branch, int actionIndex) {
        entries.add(new ActiveLinkEntry(menuName, activeLinkName, branch, actionIndex));
    }

    public void addPackingList(String menuName, String packingListName) {
        entries.add(new PackingListEntry(menuName, packingListName));
    }

    public List<Entry> entries() {
        return entries;
    }
}
