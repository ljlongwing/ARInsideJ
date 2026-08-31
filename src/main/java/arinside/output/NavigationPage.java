package arinside.output;

import arinside.config.AppConfig;
import com.bmc.arsys.api.Constants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits {@code <target>/img/nav.js} - the sidebar navigation tree as a JS data literal
 * ({@code window.ARI_NAV = [...]}). {@code app.js} renders it into {@code <nav id="ari-nav">} on
 * every page and marks the current page's entry active. Replaces the former
 * {@code template/navigation.htm} XHTML document that was loaded through an {@code <iframe>}.
 *
 * {@code href} values are output-root-relative (no {@code ../} prefix); {@code app.js} resolves
 * them against the page's {@code data-root} attribute.
 */
public final class NavigationPage {
    private NavigationPage() {}

    private record Node(String label, String href, String icon, List<Node> children) {
        Node(String label, String href, String icon) { this(label, href, icon, List.of()); }
    }

    /** A caller-supplied nav entry appended after the built-in tree (e.g. an "Innovation Studio" section). {@code href} may be empty for a label-only group. */
    public record NavItem(String label, String href, String icon, List<NavItem> children) {
        public NavItem(String label, String href, String icon) { this(label, href, icon, List.of()); }
    }

    public static void write(AppConfig appConfig) {
        write(appConfig, List.of());
    }

    public static void write(AppConfig appConfig, List<NavItem> extraSections) {
        List<Node> tree = new ArrayList<>();

        tree.add(new Node("Forms", href(Naming.schemaOverview()), "schema"));
        tree.add(new Node("Active Links", href(Naming.overviewActiveLinks()), "active-link", List.of(
            new Node("By Action", href(Naming.activeLinkActionOverview()), "document"))));
        tree.add(new Node("Filters", href(Naming.overviewFilters()), "filter", List.of(
            new Node("By Action", href(Naming.filterActionOverview()), "document"),
            new Node("Error Handler", href(Naming.filterErrorHandlers()), "document"))));
        tree.add(new Node("Escalations", href(Naming.overviewEscalations()), "escalation", List.of(
            new Node("By Action", href(Naming.escalationActionOverview()), "document"))));
        tree.add(new Node("Menus", href(Naming.overviewMenus()), "menu"));
        tree.add(new Node("Active Link Guides", href(Naming.overviewContainer(Constants.ARCON_GUIDE)), "al-guide"));
        tree.add(new Node("Filter Guides", href(Naming.overviewContainer(Constants.ARCON_FILTER_GUIDE)), "filter-guide"));
        tree.add(new Node("Applications", href(Naming.overviewContainer(Constants.ARCON_APP)), "application"));
        tree.add(new Node("Packing Lists", href(Naming.overviewContainer(Constants.ARCON_PACK)), "packing-list"));
        tree.add(new Node("Webservices", href(Naming.overviewContainer(Constants.ARCON_WEBSERVICE)), "webservice"));
        tree.add(new Node("Groups", href(Naming.groupOverview()), "group"));
        tree.add(new Node("Roles", href(Naming.roleOverview()), "role"));
        tree.add(new Node("Users", href(Naming.overviewUsers()), "user"));
        tree.add(new Node("Images", href(Naming.overviewImages()), "image"));
        // Associations are live-server-only - see AssociationSource's javadoc.
        if (!appConfig.fileMode && !appConfig.connectionless) {
            tree.add(new Node("Associations", href(Naming.associationOverview()), "association"));
        }
        tree.add(new Node("Information", "", "folder", List.of(
            new Node("Messages", href(Naming.messageList()), "document"),
            new Node("Notifications", href(Naming.notificationList()), "document"),
            new Node("Global Fields", href(Naming.globalFields()), "document"),
            new Node("Customizations", href(Naming.customWorkflow()), "document"),
            new Node("Validator", href(Naming.validatorMain()), "document"),
            new Node("Analyzer", href(Naming.analyzerMain()), "document"))));

        for (NavItem s : extraSections) tree.add(toNode(s));

        StringBuilder sb = new StringBuilder("window.ARI_NAV=");
        writeArray(sb, tree);
        sb.append(";\n");

        Path file = Path.of(appConfig.targetFolder, "img", "nav.js");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Error saving file '" + file + "' to disk", e);
        }
    }

    private static String href(PagePath page) { return page.fullFileName(); }

    private static Node toNode(NavItem s) {
        List<Node> kids = new ArrayList<>();
        for (NavItem c : s.children()) kids.add(toNode(c));
        return new Node(s.label(), s.href() == null ? "" : s.href(), s.icon(), kids);
    }

    private static void writeArray(StringBuilder sb, List<Node> nodes) {
        sb.append('[');
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(',');
            writeNode(sb, nodes.get(i));
        }
        sb.append(']');
    }

    private static void writeNode(StringBuilder sb, Node n) {
        sb.append("{\"label\":\"").append(WebUtil.jsString(n.label())).append('"');
        if (!n.href().isEmpty()) sb.append(",\"href\":\"").append(WebUtil.jsString(n.href())).append('"');
        sb.append(",\"icon\":\"").append(WebUtil.jsString(n.icon())).append('"');
        if (!n.children().isEmpty()) {
            sb.append(",\"children\":");
            writeArray(sb, n.children());
        }
        sb.append('}');
    }
}
