package arinside.doc;

import arinside.output.ImageTag;
import arinside.output.PagePath;
import arinside.output.URLLink;

/**
 * Java port of output/WebUtil.cpp's LinkToHelper() (via LinkToSchemaIndex/LinkToActiveLinkIndex/
 * LinkToFilterIndex/LinkToEscalationIndex/LinkToMenuIndex/LinkToContainer(count,...) overloads) -
 * "{count} [icon]{Label/Labels}" hyperlinked to the object type's overview page, singular/plural
 * picked by count. Used by DocApplicationDetails.cpp's ApplicationInformation() "Type" column.
 */
public final class CountLink {
    private CountLink() {}

    public static String render(int count, String singular, String plural, PagePath overview, ImageTag.Id icon, int rootLevel) {
        String label = count == 1 ? singular : plural;
        return count + " " + URLLink.to(label, overview, icon, rootLevel).toHtml();
    }
}
