package arinside.doc;

import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.URLLink;
import arinside.scan.ContainerReferenceIndex;

import java.util.List;

/**
 * Shared "Container References" table rendering (icon + link per referencing container) - the
 * output half of {@link ContainerReferenceIndex}, reused by MenuDetailPage/ActiveLinkDetailPage/
 * FilterDetailPage since all three real C++ ContainerReferences() functions produce the same shape
 * (a CContainerTable of every non-Application container referencing this object). isOverlaid is
 * not tracked per container here (matches ApplicationHeaderLink's same accepted simplification for
 * this kind of secondary cross-link) - defaults to false.
 */
final class ContainerReferencesTable {
    private ContainerReferencesTable() {}

    static String render(List<ContainerReferenceIndex.ContainerRef> refs, int rootLevel) {
        if (refs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ContainerReferenceIndex.ContainerRef ref : refs) {
            sb.append(URLLink.to(ref.name(), Naming.containerDetail(ref.containerType(), ref.name(), false), GroupDetailPage.containerIcon(ref.containerType()), rootLevel).toHtml())
                .append("<br/>\n");
        }
        return sb.toString();
    }
}
