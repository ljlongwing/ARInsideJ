package arinside.output;

import arinside.config.AppConfig;
import com.bmc.arsys.api.Constants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java port of output/NavigationPage.cpp - the persistent left-hand navigation tree every other
 * page's `&lt;iframe id="IFrameMenu" src="../template/navigation.htm"&gt;` already references (that
 * reference has been in every generated page since early in this port; this is the file it points
 * at, previously never generated - the iframe rendered as a blank pane in a browser until now).
 *
 * Unlike every other generated page, this one does NOT go through {@link WebPage} - the C++'s own
 * `CNavigationPage::Header/Footer` builds a genuinely different, minimal `&lt;ul&gt;` document, not
 * the standard site chrome (breadcrumb, tabs, server-name banner) - so this writes the file
 * directly instead.
 *
 * The `overview/*_action*.htm` "By Action" breakdown pages under Active Links/Filters/Escalations
 * and `overview/error_handler.htm` are now built too (see ActiveLinkActionPage/FilterActionPage/
 * EscalationActionPage/FilterErrorHandlersPage) - every link in the C++'s tree now has a real
 * equivalent page in this port.
 */
public final class NavigationPage {
    private NavigationPage() {}

    public static void write(AppConfig appConfig) {
        int rootLevel = 1;
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?> \n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">");
        sb.append("<head><title>Navigation</title>");
        sb.append("<meta name=\"description\" content=\"ARSystem Documentation\" />");
        sb.append("<meta name=\"author\" content=\"ARInsideJ\" />");
        sb.append("<meta http-equiv=\"CONTENT-LANGUAGE\" content=\"EN\" />");
        sb.append("<meta name=\"ROBOTS\" content=\"INDEX, FOLLOW\" />");
        sb.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\" />");
        sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../img/style.css\" />");
        sb.append("</head><body><ul>");

        li(sb, "Forms", Naming.schemaOverview(), ImageTag.Id.Schema, rootLevel);

        ImageTag doc0 = new ImageTag(ImageTag.Id.Document, rootLevel);
        sb.append("<li>").append(URLLink.to("Active Links", Naming.overviewActiveLinks(), new ImageTag(ImageTag.Id.ActiveLink, rootLevel), rootLevel, true, URLLink.Target.PARENT).toHtml());
        sb.append("<ul><li>").append(URLLink.to("By Action", Naming.activeLinkActionOverview(), doc0, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li></ul>");
        sb.append("</li>");

        sb.append("<li>").append(URLLink.to("Filters", Naming.overviewFilters(), new ImageTag(ImageTag.Id.Filter, rootLevel), rootLevel, true, URLLink.Target.PARENT).toHtml());
        sb.append("<ul>");
        sb.append("<li>").append(URLLink.to("By Action", Naming.filterActionOverview(), doc0, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Error Handler", Naming.filterErrorHandlers(), doc0, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("</ul></li>");

        sb.append("<li>").append(URLLink.to("Escalations", Naming.overviewEscalations(), new ImageTag(ImageTag.Id.Escalation, rootLevel), rootLevel, true, URLLink.Target.PARENT).toHtml());
        sb.append("<ul><li>").append(URLLink.to("By Action", Naming.escalationActionOverview(), doc0, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li></ul>");
        sb.append("</li>");

        li(sb, "Menus", Naming.overviewMenus(), ImageTag.Id.Menu, rootLevel);
        li(sb, "Active Link Guides", Naming.overviewContainer(Constants.ARCON_GUIDE), ImageTag.Id.ActiveLinkGuide, rootLevel);
        li(sb, "Filter Guides", Naming.overviewContainer(Constants.ARCON_FILTER_GUIDE), ImageTag.Id.FilterGuide, rootLevel);
        li(sb, "Applications", Naming.overviewContainer(Constants.ARCON_APP), ImageTag.Id.Application, rootLevel);
        li(sb, "Packing Lists", Naming.overviewContainer(Constants.ARCON_PACK), ImageTag.Id.PackingList, rootLevel);
        li(sb, "Webservices", Naming.overviewContainer(Constants.ARCON_WEBSERVICE), ImageTag.Id.Webservice, rootLevel);
        li(sb, "Groups", Naming.groupOverview(), ImageTag.Id.Group, rootLevel);
        li(sb, "Roles", Naming.roleOverview(), ImageTag.Id.Role, rootLevel);
        li(sb, "Users", Naming.overviewUsers(), ImageTag.Id.User, rootLevel);
        li(sb, "Images", Naming.overviewImages(), ImageTag.Id.Image, rootLevel);
        // Associations are live-server-only - see AssociationSource's javadoc - so the page (and
        // therefore this link) only exists outside file/connectionless mode.
        if (!appConfig.fileMode && !appConfig.connectionless) {
            li(sb, "Associations", Naming.associationOverview(), ImageTag.Id.Association, rootLevel);
        }

        sb.append("<li>").append(new ImageTag(ImageTag.Id.Folder, rootLevel).toHtml()).append("Information:<ul>");
        ImageTag doc = new ImageTag(ImageTag.Id.Document, rootLevel);
        sb.append("<li>").append(URLLink.to("Messages", Naming.messageList(), doc, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Notifications", Naming.notificationList(), doc, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Global&nbsp;Fields", Naming.globalFields(), doc, rootLevel, false, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Customizations", Naming.customWorkflow(), doc, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Validator", Naming.validatorMain(), doc, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("<li>").append(URLLink.to("Analyzer", Naming.analyzerMain(), doc, rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
        sb.append("</ul></li>");

        sb.append("</ul></body></html>");

        Path file = Path.of(appConfig.targetFolder, "template", WebUtil.docName("navigation"));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException("Error saving file '" + file + "' to disk", e);
        }
    }

    private static void li(StringBuilder sb, String caption, PagePath page, ImageTag.Id imageId, int rootLevel) {
        sb.append("<li>").append(URLLink.to(caption, page, new ImageTag(imageId, rootLevel), rootLevel, true, URLLink.Target.PARENT).toHtml()).append("</li>");
    }
}
