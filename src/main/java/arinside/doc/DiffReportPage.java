package arinside.doc;

import arinside.config.AppConfig;
import arinside.diff.Change;
import arinside.diff.RepoSet;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import com.bmc.arsys.api.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Renders the standalone two-snapshot change report ({@code diff/index.htm} + one
 * {@code diff/<type>/<name>.htm} per change) into the target folder, reusing the reskin shell
 * ({@link WebPage}) and {@link OverlayDiff}'s renderers. Called by {@code arinside.diff.DiffRunner}.
 */
public final class DiffReportPage {
    private DiffReportPage() {}

    private static final QualificationRenderer.FieldReferenceSink NOOP = (f, id, exists, detail) -> {};

    /**
     * Above this many changes the per-object before/after pages are skipped (the summary table +
     * data/diff.json still cover everything). A diff that large is almost always two unrelated
     * snapshots or a server compared against a tiny export - writing 100k+ HTML files serially is
     * the bottleneck, not the comparison.
     */
    static final int MAX_DETAIL_PAGES = 5000;

    public static void write(AppConfig cfg, List<Change> changes, RepoSet baseline, RepoSet current) {
        writeNav(cfg);
        writeIndex(cfg, changes);
        if (changes.size() > MAX_DETAIL_PAGES) {
            System.out.println("Diff: " + changes.size() + " changes exceeds " + MAX_DETAIL_PAGES
                + " - per-object pages skipped (see diff/index.htm and data/diff.json).");
            return;
        }
        for (Change c : changes) writeDetail(cfg, c, baseline, current);
    }

    /* ---------- nav ---------- */

    private static void writeNav(AppConfig cfg) {
        String navJs = "window.ARI_NAV=[{\"label\":\"Diff Summary\",\"href\":\"diff/index.htm\",\"icon\":\"document\"}];\n";
        try {
            Path f = Path.of(cfg.targetFolder, "img", "nav.js");
            Files.createDirectories(f.getParent());
            Files.writeString(f, navJs, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /* ---------- index ---------- */

    private static void writeIndex(AppConfig cfg, List<Change> changes) {
        PagePath page = new PagePath("diff", "index", 1);
        WebPage web = new WebPage(page.fileName(), "Snapshot Diff", page.rootLevel(), cfg);
        web.bodyClass("list-page");
        web.addContentHead("Changes: " + baseName(cfg.diffBaseline) + " → " + baseName(cfg.diffCurrent));

        long added = count(changes, Change.Kind.ADDED), removed = count(changes, Change.Kind.REMOVED), modified = count(changes, Change.Kind.MODIFIED);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"overlaySummary\"><p><b>").append(changes.size()).append("</b> object")
            .append(changes.size() == 1 ? "" : "s").append(" changed — ")
            .append(added).append(" added, ").append(removed).append(" removed, ").append(modified).append(" modified.</p>");
        sb.append("<p>Baseline: <code>").append(WebUtil.validate(cfg.diffBaseline)).append("</code><br/>Current: <code>")
            .append(WebUtil.validate(cfg.diffCurrent)).append("</code></p></div>\n");

        boolean detailPages = changes.size() <= MAX_DETAIL_PAGES;
        if (!detailPages) {
            sb.append("<div class=\"overlaySummary\"><p>Too many changes for per-object before/after pages "
                + "(over ").append(MAX_DETAIL_PAGES).append("); the table below and <code>data/diff.json</code> "
                + "still list every change.</p></div>\n");
        }

        Table tbl = new Table("diffList", "TblObjectList");
        tbl.addColumn(34, "Object");
        tbl.addColumn(12, "Type");
        tbl.addColumn(10, "Change");
        tbl.addColumn(44, "What changed");
        for (Change c : changes) {
            String link = "../diff/" + c.typeSlug + "/" + WebUtil.docName(sanitize(c.name));
            String obj = detailPages
                ? "<a href=\"" + link + "\">" + WebUtil.validate(c.name) + "</a>"
                : WebUtil.validate(c.name);
            tbl.addRow(new TableRow(cssFor(c.kind)).addCellList(
                obj, c.typeLabel, c.kindLabel(),
                c.summary.isEmpty() ? "" : WebUtil.validate(String.join("; ", c.summary))));
        }
        if (!changes.isEmpty()) tbl.removeEmptyMessageRow();

        web.addContent(sb.toString());
        web.addContent(tbl.toXHtml());
        web.saveInFolder(page.path());
    }

    /* ---------- per-object detail ---------- */

    private static void writeDetail(AppConfig cfg, Change c, RepoSet a, RepoSet b) {
        PagePath page = new PagePath("diff/" + c.typeSlug, sanitize(c.name), 2);
        WebPage web = new WebPage(page.fileName(), c.name + " — diff", page.rootLevel(), cfg);
        web.addContentHead(WebUtil.validate(c.name) + " <span class=\"additionalInfo\">(" + c.typeLabel + ")</span>");

        String body;
        try {
            body = switch (c.kind) {
                case ADDED -> "<div class=\"overlaySummary overlayAdded\"><p><b>Added</b> — exists only in the current snapshot.</p></div>\n" + facts(c.after);
                case REMOVED -> "<div class=\"overlaySummary overlayRemoved\"><p><b>Removed</b> — existed only in the baseline snapshot.</p></div>\n" + facts(c.before);
                case MODIFIED -> renderModified(cfg, c, a, b, page.rootLevel());
            };
        } catch (Exception e) {
            body = "<p>Could not render detail: " + WebUtil.validate(String.valueOf(e)) + "</p>";
        }
        web.addContent(body);
        web.saveInFolder(page.path());
    }

    private static String renderModified(AppConfig cfg, Change c, RepoSet a, RepoSet b, int rootLevel) throws Exception {
        return switch (c.typeSlug) {
            case "form" -> renderFormDiff(c.name, a, b);
            case "active_link" -> renderWorkflowDiff(cfg, c.before, c.after, a.globalFields(), b.globalFields(), rootLevel,
                ActionSummaryTable.activeLinkTypeOf(), ActionSummaryTable.activeLinkLabel());
            case "filter", "escalation" -> renderWorkflowDiff(cfg, c.before, c.after, a.globalFields(), b.globalFields(), rootLevel,
                ActionSummaryTable.filterTypeOf(), ActionSummaryTable.filterLabel());
            default -> "<div class=\"overlaySummary\"><h2>Property changes</h2><div>"
                + diffPropTable(OverlayDiff.diffProperties(props(c.before), props(c.after))) + "</div></div>\n";
        };
    }

    private static String renderFormDiff(String name, RepoSet a, RepoSet b) throws Exception {
        Form fa = a.schemas().getForm(name), fb = b.schemas().getForm(name);
        List<Field> flA = a.schemas().getFields(name), flB = b.schemas().getFields(name);
        flA.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        flB.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));

        StringBuilder sb = new StringBuilder("<div class=\"overlaySummary\"><h2>Changes</h2><div>\n");
        sb.append(diffItemList("Fields",
            OverlayDiff.diffKeyed(flA, flB, Field::getFieldID, arinside.diff.SnapshotDiff::fieldsEqual), Field::getName));
        sb.append(diffItemList("Indexes",
            OverlayDiff.diffKeyed(fa.getIndexInfo(), fb.getIndexInfo(), IndexInfo::getIndexName, IndexInfo::equals), IndexInfo::getIndexName));
        sb.append(diffItemList("Permissions",
            OverlayDiff.diffKeyed(fa.getPermissions(), fb.getPermissions(), PermissionInfo::getGroupID, PermissionInfo::equals),
            p -> "Group " + p.getGroupID()));
        sb.append(diffItemList("Sort fields",
            OverlayDiff.diffKeyed(fa.getSortInfo(), fb.getSortInfo(), SortInfo::getFieldID, SortInfo::equals),
            si -> "Field " + si.getFieldID()));
        sb.append(diffItemList("Result-list fields",
            OverlayDiff.diffKeyed(fa.getEntryListFieldInfo(), fb.getEntryListFieldInfo(), EntryListFieldInfo::getFieldId, EntryListFieldInfo::equals),
            el -> "Field " + el.getFieldId()));
        sb.append(diffItemList("Views",
            OverlayDiff.diffKeyed(a.schemas().getViews(name), b.schemas().getViews(name), View::getVUIId, View::equals),
            v -> v.getName() == null ? "VUI " + v.getVUIId() : v.getName()));
        sb.append(diffPropTable(OverlayDiff.diffProperties(fa.getProperties(), fb.getProperties())));
        if (!Objects.equals(fa.getArchiveInfo(), fb.getArchiveInfo())) sb.append("<p><b>Archive settings changed.</b></p>\n");
        if (!Objects.equals(fa.getAuditInfo(), fb.getAuditInfo())) sb.append("<p><b>Audit settings changed.</b></p>\n");
        sb.append("</div></div>\n");
        return sb.toString();
    }

    private static <A> String renderWorkflowDiff(AppConfig cfg, Object before, Object after,
            GlobalFieldIndex gfiA, GlobalFieldIndex gfiB, int rootLevel,
            Function<A, Integer> typeOf, Function<Integer, String> label) {
        var wa = wf(before);
        var wb = wf(after);
        var propDiff = OverlayDiff.diffProperties(wa.props, wb.props);
        boolean qualifierChanged = !Objects.equals(wa.qual, wb.qual);
        boolean actionsChanged = !Objects.equals(wa.actions, wb.actions) || !Objects.equals(wa.elseActions, wb.elseActions);
        var formListDiff = OverlayDiff.diffKeyed(wa.forms, wb.forms, Function.identity(), Object::equals);
        String resolveA = firstForm(wa.forms), resolveB = firstForm(wb.forms);

        StringBuilder sb = new StringBuilder("<div class=\"overlaySummary\"><h2>Changes</h2><div>\n");
        if (wa.enabled != wb.enabled) sb.append("<p><b>Enabled:</b> ").append(wa.enabled ? "Yes" : "No").append(" &rarr; ").append(wb.enabled ? "Yes" : "No").append("</p>\n");
        if (wa.order != wb.order) sb.append("<p><b>Order:</b> ").append(wa.order).append(" &rarr; ").append(wb.order).append("</p>\n");
        sb.append(diffItemList("Form list", formListDiff, Function.identity()));
        if (qualifierChanged) {
            String qhA = qualHtml(wa.qual, new QualificationRenderer(resolveA, rootLevel, gfiA, NOOP));
            String qhB = qualHtml(wb.qual, new QualificationRenderer(resolveB, rootLevel, gfiB, NOOP));
            sb.append("<h3>Run If &mdash; what changed</h3>\n<p>")
              .append(arinside.util.TextDiff.inlineWords(qhA, qhB)).append("</p>\n");
            sb.append("<h3>Run If &mdash; baseline</h3>\n").append(qhA);
            sb.append("<h3>Run If &mdash; current</h3>\n").append(qhB);
        }
        if (actionsChanged) {
            @SuppressWarnings("unchecked")
            Function<A, Integer> to = typeOf;
            sb.append("<h3>Actions &mdash; baseline</h3>\n")
              .append(ActionSummaryTable.render((List<A>) wa.actions, (List<A>) wa.elseActions, to, label, resolveA, null, cfg.serverName));
            sb.append("<h3>Actions &mdash; current</h3>\n")
              .append(ActionSummaryTable.render((List<A>) wb.actions, (List<A>) wb.elseActions, to, label, resolveB, null, cfg.serverName));
        }
        sb.append(diffPropTable(propDiff));
        sb.append("</div></div>\n");
        return sb.toString();
    }

    /* diff-worded renderers (OverlayDiff's own say "by Overlay" / "Base"/"Overlay") */

    private static <T> String diffItemList(String title, List<OverlayDiff.Item<T>> diff, Function<T, String> labelFn) {
        if (diff.isEmpty()) return "";
        Table t = new Table("", "TblObjectList");
        t.description = title;
        t.addColumn(100, "Item");
        for (var i : diff) {
            String verb = switch (i.status()) { case ADDED -> " (added)"; case CHANGED -> " (changed)"; case REMOVED -> " (removed)"; };
            t.addRow(new TableRow(OverlayDiff.cssClass(i.status())).addCell(WebUtil.validate(labelFn.apply(i.current())) + verb));
        }
        t.removeEmptyMessageRow();
        return t.toXHtml();
    }

    private static String diffPropTable(List<OverlayDiff.PropChange> diff) {
        if (diff.isEmpty()) return "";
        Table t = new Table("", "TblObjectList");
        t.description = "Properties";
        t.addColumn(30, "Property");
        t.addColumn(35, "Baseline");
        t.addColumn(35, "Current");
        for (var p : diff) {
            t.addRow(new TableRow().addCellList(p.label(),
                p.baseValue() == null ? "" : p.baseValue(), p.overlayValue() == null ? "" : p.overlayValue()));
        }
        t.removeEmptyMessageRow();
        return t.toXHtml();
    }

    /* ---------- helpers ---------- */

    private record Wf(PropertyMap props, Object qual, List<?> actions, List<?> elseActions, List<String> forms, boolean enabled, int order) {}

    private static Wf wf(Object o) {
        if (o instanceof ActiveLink al) return new Wf(al.getProperties(), al.getQualifier(), al.getActionList(), al.getElseList(), al.getFormList(), al.isEnable(), al.getOrder());
        if (o instanceof Filter f) return new Wf(f.getProperties(), f.getQualifier(), f.getActionList(), f.getElseList(), f.getFormList(), f.isEnable(), f.getOrder());
        Escalation e = (Escalation) o;
        return new Wf(e.getProperties(), e.getQualifier(), e.getActionList(), e.getElseList(), e.getFormList(), e.isEnable(), 0);
    }

    private static String qualHtml(Object q, QualificationRenderer qr) {
        QualifierInfo qi = (QualifierInfo) q;
        return qi != null && qi.getOperation() != QualifierInfo.AR_COND_OP_NONE
            ? qr.render(qi, "Run If") : "No qualification specified";
    }

    private static String firstForm(List<String> forms) {
        return forms != null && !forms.isEmpty() ? forms.get(0) : "";
    }

    private static PropertyMap props(Object o) {
        if (o instanceof Menu m) return m.getProperties();
        if (o instanceof Container c) return c.getProperties();
        if (o instanceof Image i) return i.getProperties();
        return null;
    }

    /** A tiny "what this object is" table for ADDED / REMOVED pages. */
    private static String facts(Object o) {
        Table t = new Table("diffFacts", "TblObjectList");
        t.addColumn(30, "Property");
        t.addColumn(70, "Value");
        if (o instanceof Form f) {
            row(t, "Form type", String.valueOf(f.getFormType()));
        } else if (o instanceof ActiveLink al) {
            row(t, "Enabled", al.isEnable() ? "Yes" : "No");
            row(t, "Order", String.valueOf(al.getOrder()));
            row(t, "Forms", String.join(", ", nz(al.getFormList())));
        } else if (o instanceof Filter fl) {
            row(t, "Enabled", fl.isEnable() ? "Yes" : "No");
            row(t, "Order", String.valueOf(fl.getOrder()));
            row(t, "Forms", String.join(", ", nz(fl.getFormList())));
        } else if (o instanceof Escalation e) {
            row(t, "Enabled", e.isEnable() ? "Yes" : "No");
            row(t, "Forms", String.join(", ", nz(e.getFormList())));
        } else if (o instanceof Menu m) {
            row(t, "Menu type", String.valueOf(m.getMenuType()));
        } else if (o instanceof Container c) {
            row(t, "Container type", String.valueOf(c.getType()));
        }
        if (t.numRows() == 0) return "";
        t.removeEmptyMessageRow();
        return t.toXHtml();
    }

    private static void row(Table t, String k, String v) { t.addRow(new TableRow().addCellList(k, WebUtil.validate(v))); }
    private static List<String> nz(List<String> l) { return l == null ? List.of() : l; }

    private static long count(List<Change> changes, Change.Kind k) { return changes.stream().filter(c -> c.kind == k).count(); }
    private static String cssFor(Change.Kind k) {
        return switch (k) { case ADDED -> "overlayAdded"; case REMOVED -> "overlayRemoved"; case MODIFIED -> "overlayChanged"; };
    }
    private static String baseName(String path) {
        String p = path.replace('\\', '/');
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._ -]", "_");
    }
}
