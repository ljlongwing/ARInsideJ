package arinside.doc.is;

import arinside.ar.is.IsBundle;
import arinside.ar.is.IsDefType;
import arinside.ar.is.IsDefinition;
import arinside.ar.is.IsRepository;
import arinside.config.AppConfig;
import arinside.output.NavigationPage.NavItem;
import arinside.output.PagePath;
import arinside.output.Table;
import arinside.output.TableRow;
import arinside.output.WebPage;
import arinside.output.WebUtil;
import arinside.util.DateTimeFormat;
import arinside.util.Json;
import arinside.util.JsonReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the Innovation Studio documentation: {@code is/index.htm} (bundles + per-type counts),
 * {@code is/<type>/index.htm} (one filterable list per definition family), and
 * {@code is/<type>/<name>.htm} per definition. Type-specific detail is pulled from
 * {@link IsDefinition#raw()}; a collapsed raw-JSON block on every page is the belt-and-suspenders
 * fallback so nothing is silently dropped.
 */
public final class IsPages {
    private IsPages() {}

    private static final String DIR = "is";

    /** Renders everything and returns the nav section to hand to {@code NavigationPage.write}. */
    public static NavItem render(AppConfig cfg, IsRepository repo) {
        writeOverview(cfg, repo);
        List<NavItem> typeNav = new ArrayList<>();
        for (IsDefType type : IsDefType.values()) {
            List<IsDefinition> defs = repo.of(type);
            if (defs.isEmpty()) continue;
            writeTypeList(cfg, type, defs);
            for (IsDefinition d : defs) writeDetail(cfg, type, d);
            typeNav.add(new NavItem(type.pluralLabel + " (" + defs.size() + ")",
                DIR + "/" + type.name().toLowerCase() + "/index.htm", isIcon(type)));
        }
        return new NavItem("Innovation Studio", DIR + "/index.htm", "application", typeNav);
    }

    /* ---------- overview ---------- */

    private static void writeOverview(AppConfig cfg, IsRepository repo) {
        PagePath page = new PagePath(DIR, "index", 1);
        WebPage web = new WebPage(page.fileName(), "Innovation Studio", page.rootLevel(), cfg);
        web.addContentHead("Innovation Studio");

        web.addContent("<p>" + repo.totalDefinitions() + " definitions across " + repo.bundles().size()
            + " bundles, from <code>" + WebUtil.validate(cfg.isServerUrl) + "</code>. "
            + "Record definitions (the classic AR forms) are documented under Forms, not here.</p>");

        Table types = new Table("isTypeCounts", "TblObjectList");
        types.addColumn(40, "Definition type");
        types.addColumn(15, "Count");
        for (IsDefType t : IsDefType.values()) {
            int n = repo.of(t).size();
            String cell = n == 0 ? t.pluralLabel
                : "<a href=\"../" + DIR + "/" + t.name().toLowerCase() + "/index.htm\">" + t.pluralLabel + "</a>";
            types.addRow(new TableRow().addCellList(cell, Integer.toString(n)));
        }
        types.removeEmptyMessageRow();
        web.addContent("<h2>Definitions</h2>\n" + types.toXHtml());

        Table bt = new Table("isBundles", "TblObjectList");
        bt.addColumn(28, "Bundle");
        bt.addColumn(12, "Version");
        bt.addColumn(18, "Developer");
        bt.addColumn(8, "Application");
        bt.addColumn(20, "Last deployed");
        for (IsBundle b : repo.bundles()) {
            bt.addRow(new TableRow().addCellList(
                WebUtil.validate(b.friendlyName() != null && !b.friendlyName().isEmpty() ? b.friendlyName() : b.id())
                    + "<br/><span class=\"additionalInfo\">" + WebUtil.validate(b.id()) + "</span>",
                WebUtil.validate(nz(b.version())),
                WebUtil.validate(nz(b.developerId())),
                b.isApplication() ? "Yes" : "",
                WebUtil.validate(prettyTime(b.lastDeployedTime()))));
        }
        bt.removeEmptyMessageRow();
        web.addContent("<h2>Bundles</h2>\n" + bt.toXHtml());
        web.saveInFolder(page.path());
    }

    /* ---------- per-type list ---------- */

    private static void writeTypeList(AppConfig cfg, IsDefType type, List<IsDefinition> defs) {
        PagePath page = new PagePath(DIR + "/" + type.name().toLowerCase(), "index", 2);
        WebPage web = new WebPage(page.fileName(), "IS " + type.pluralLabel, page.rootLevel(), cfg);
        web.addContentHead(type.pluralLabel + " <span class=\"additionalInfo\">(Innovation Studio)</span>");

        Table t = new Table("isList", "TblObjectList");
        t.addColumn(40, "Name");
        t.addColumn(18, "Bundle");
        t.addColumn(8, "Enabled");
        t.addColumn(10, "Layer");
        t.addColumn(14, "Modified");
        t.addColumn(10, "By");
        for (IsDefinition d : defs) {
            String link = "<a href=\"../../" + DIR + "/" + type.name().toLowerCase() + "/"
                + WebUtil.docName(sanitize(d.name())) + "\">" + WebUtil.validate(d.name()) + "</a>";
            t.addRow(new TableRow().addCellList(
                link,
                WebUtil.validate(bundleOf(d)),
                d.enabled() == null ? "" : (d.enabled() ? "Yes" : "No"),
                d.isOverlay() ? "Overlay" : "Base",
                d.modifiedEpoch() == null ? "" : DateTimeFormat.toPlainString(d.modifiedEpoch()),
                WebUtil.validate(nz(d.modifiedBy()))));
        }
        t.removeEmptyMessageRow();
        web.addContent(t.toXHtml());
        web.saveInFolder(page.path());
    }

    /* ---------- per-definition detail ---------- */

    private static void writeDetail(AppConfig cfg, IsDefType type, IsDefinition d) {
        PagePath page = new PagePath(DIR + "/" + type.name().toLowerCase(), sanitize(d.name()), 2);
        WebPage web = new WebPage(page.fileName(), d.name(), page.rootLevel(), cfg);
        web.addContentHead(WebUtil.validate(d.name()) + " <span class=\"additionalInfo\">(" + type.label + ")</span>");

        Table g = new Table("isGeneral", "TblObjectList");
        g.addColumn(25, "Property");
        g.addColumn(75, "Value");
        row(g, "Type", type.label);
        row(g, "Bundle", bundleOf(d));
        if (d.description() != null && !d.description().isEmpty()) row(g, "Description", WebUtil.validate(d.description()));
        if (d.enabled() != null) row(g, "Enabled", d.enabled() ? "Yes" : "No");
        row(g, "Scope", nz(d.scope()));
        row(g, "Layer", d.isOverlay() ? "Overlay (group " + d.overlayGroupId() + ")" : "Base");
        row(g, "Owner", nz(d.owner()));
        if (d.guid() != null) row(g, "GUID", WebUtil.validate(d.guid()));
        row(g, "Modified", (d.modifiedEpoch() == null ? "" : DateTimeFormat.toHtmlString(d.modifiedEpoch()))
            + (d.modifiedBy() == null ? "" : " by " + WebUtil.validate(d.modifiedBy())));
        web.addContent(g.toXHtml());

        String specific = switch (type) {
            case RULE -> ruleDetail(d);
            case ASSOCIATION -> associationDetail(d);
            case EVENT -> eventDetail(d);
            case WEB_API -> webApiDetail(d);
            default -> "";
        };
        if (!specific.isEmpty()) web.addContent(specific);

        web.addContent("<details class=\"ari-acc\"><summary>Raw definition (JSON)</summary>"
            + "<div class=\"acc-body\"><pre>" + WebUtil.validate(Json.write(d.raw())) + "</pre></div></details>");
        web.saveInFolder(page.path());
    }

    /* ---------- type-specific renderers ---------- */

    private static String ruleDetail(IsDefinition d) {
        StringBuilder sb = new StringBuilder("<h2>Rule</h2>\n");
        Object trg = JsonReader.at(d.raw(), "triggerEvent");
        Table t = new Table("isRuleTrigger", "TblObjectList");
        t.addColumn(25, "Property");
        t.addColumn(75, "Value");
        row(t, "Trigger", shortType(JsonReader.str(trg, "resourceType")));
        Object tc = JsonReader.at(trg, "timeCriteria");
        if (tc instanceof Map) {
            row(t, "Interval", JsonReader.lng(tc, "days") + "d " + JsonReader.lng(tc, "hours") + "h "
                + JsonReader.lng(tc, "minutes") + "m " + JsonReader.lng(tc, "seconds") + "s");
        }
        List<Object> recs = JsonReader.asList(JsonReader.at(d.raw(), "recordDefinitionNames"));
        if (!recs.isEmpty()) row(t, "On records", recs.stream().map(String::valueOf).map(WebUtil::validate)
            .reduce((a, b) -> a + "<br/>" + b).orElse(""));
        String qual = JsonReader.str(d.raw(), "qualification", "expression");
        row(t, "Run If", qual == null || qual.isEmpty() ? "(none)" : "<code>" + WebUtil.validate(qual) + "</code>");
        sb.append(t.toXHtml());

        List<Object> actions = JsonReader.asList(JsonReader.at(d.raw(), "actions"));
        if (!actions.isEmpty()) {
            Table at = new Table("isRuleActions", "TblObjectList");
            at.addColumn(20, "Action");
            at.addColumn(20, "Type");
            at.addColumn(60, "Input / output");
            for (Object a : actions) {
                at.addRow(new TableRow().addCellList(
                    WebUtil.validate(nz(JsonReader.str(a, "name"))),
                    WebUtil.validate(nz(JsonReader.str(a, "actionTypeName")) + " / " + shortType(JsonReader.str(a, "resourceType"))),
                    assignMap(JsonReader.asList(JsonReader.at(a, "inputMap")), "in")
                        + assignMap(JsonReader.asList(JsonReader.at(a, "outputMap")), "out")));
            }
            at.removeEmptyMessageRow();
            sb.append("<h3>Actions</h3>\n").append(at.toXHtml());
        }
        return sb.toString();
    }

    private static String assignMap(List<Object> maps, String dir) {
        if (maps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Object m : maps) {
            sb.append("<div><span class=\"additionalInfo\">").append(dir).append("</span> ")
              .append(WebUtil.validate(nz(JsonReader.str(m, "assignTarget")))).append(" = <code>")
              .append(WebUtil.validate(nz(JsonReader.str(m, "expression")))).append("</code></div>");
        }
        return sb.toString();
    }

    private static String associationDetail(IsDefinition d) {
        Table t = new Table("isAssoc", "TblObjectList");
        t.addColumn(25, "Property");
        t.addColumn(75, "Value");
        row(t, "Node A", WebUtil.validate(nz(JsonReader.str(d.raw(), "nodeAName")))
            + " <span class=\"additionalInfo\">keys " + JsonReader.asList(JsonReader.at(d.raw(), "nodeAKeys")) + "</span>");
        row(t, "Node B", WebUtil.validate(nz(JsonReader.str(d.raw(), "nodeBName")))
            + " <span class=\"additionalInfo\">keys " + JsonReader.asList(JsonReader.at(d.raw(), "nodeBKeys")) + "</span>");
        row(t, "Cardinality", WebUtil.validate(nz(JsonReader.str(d.raw(), "cardinality"))));
        row(t, "Cascade delete", JsonReader.bool(d.raw(), "shouldCascadeDelete") ? "Yes" : "No");
        return "<h2>Association</h2>\n" + t.toXHtml();
    }

    private static String eventDetail(IsDefinition d) {
        List<Object> attrs = JsonReader.asList(JsonReader.at(d.raw(), "eventAttributes"));
        if (attrs.isEmpty()) return "";
        Table t = new Table("isEventAttrs", "TblObjectList");
        t.addColumn(40, "Attribute");
        t.addColumn(60, "Type");
        for (Object a : attrs) {
            t.addRow(new TableRow().addCellList(
                WebUtil.validate(nz(JsonReader.str(a, "name"))),
                WebUtil.validate(nz(JsonReader.str(a, "type") != null ? JsonReader.str(a, "type") : shortType(JsonReader.str(a, "resourceType"))))));
        }
        t.removeEmptyMessageRow();
        return "<h2>Event attributes</h2>\n" + t.toXHtml();
    }

    private static String webApiDetail(IsDefinition d) {
        List<Object> reqs = JsonReader.asList(JsonReader.at(d.raw(), "requestDefinitions"));
        if (reqs.isEmpty()) return "";
        Table t = new Table("isWebApiReqs", "TblObjectList");
        t.addColumn(15, "Method");
        t.addColumn(55, "Path");
        t.addColumn(30, "Name");
        for (Object r : reqs) {
            t.addRow(new TableRow().addCellList(
                WebUtil.validate(nz(JsonReader.str(r, "method"))),
                "<code>" + WebUtil.validate(nz(JsonReader.str(r, "urlPath") != null ? JsonReader.str(r, "urlPath") : JsonReader.str(r, "path"))) + "</code>",
                WebUtil.validate(nz(JsonReader.str(r, "name")))));
        }
        t.removeEmptyMessageRow();
        return "<h2>Requests</h2>\n" + t.toXHtml();
    }

    /* ---------- helpers ---------- */

    private static void row(Table t, String k, String v) {
        t.addRow(new TableRow().addCellList(k, v == null || v.isEmpty() ? WebUtil.EMPTY_VALUE : v));
    }

    /** IS definition names are usually {@code bundleId:Local Name}; fall back to the raw sourceBundle field. */
    private static String bundleOf(IsDefinition d) {
        String n = d.name();
        if (n != null) {
            int c = n.indexOf(':');
            if (c > 0 && n.substring(0, c).contains(".")) return n.substring(0, c);
        }
        for (String k : new String[]{"sourceBundleName", "bundleId", "bundleName"}) {
            String v = JsonReader.str(d.raw(), k);
            if (v != null && !v.isEmpty()) return v;
        }
        return "—"; // em dash - a bare-named platform definition with no bundle prefix
    }

    private static String isIcon(IsDefType t) {
        return switch (t) {
            case RULE -> "active-link";
            case PROCESS -> "escalation";
            case WEB_API -> "webservice";
            case ASSOCIATION -> "association";
            case VIEW -> "schema-view";
            case DOCUMENT -> "document";
            default -> "document";
        };
    }

    private static String shortType(String resourceType) {
        if (resourceType == null) return "";
        int dot = resourceType.lastIndexOf('.');
        return dot >= 0 ? resourceType.substring(dot + 1) : resourceType;
    }

    private static String prettyTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            return DateTimeFormat.toPlainString(java.time.OffsetDateTime.parse(iso).toInstant().getEpochSecond());
        } catch (RuntimeException e) {
            return iso;
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String sanitize(String name) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return s.isEmpty() ? "_" : s;
    }
}
