package arinside.doc;

import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.URLLink;
import com.bmc.arsys.api.Constants;

/**
 * Java port of the "Application &lt;link&gt;" breadcrumb suffix repeated across
 * DocAlDetails.cpp/DocFilterDetails.cpp/DocCharMenuDetails.cpp/DocContainerHelper.cpp's own header
 * builders (each checks GetAppRefName() and, if set, appends a link to that Application container)
 * - shared here since the rendering itself is identical across all four real call sites, only the
 * app-name lookup differs (see AppMembershipIndex). isOverlaid is not tracked for this link (a
 * minor, accepted simplification already precedented elsewhere in this port for secondary
 * cross-links) - defaults to false.
 */
final class ApplicationHeaderLink {
    private ApplicationHeaderLink() {}

    /** Returns "" if appName is null/empty (no Application owns this object), else the MenuSeparator-prefixed link suffix matching the C++'s header format. */
    static String suffix(String appName, int rootLevel) {
        if (appName == null || appName.isEmpty()) return "";
        return " &gt; Application " + URLLink.to(appName, Naming.containerDetail(Constants.ARCON_APP, appName, false), ImageTag.Id.Application, rootLevel).toHtml();
    }
}
