package arinside.diff;

import arinside.doc.OverlayDiff;
import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Classifies every object as ADDED / REMOVED / MODIFIED (or unchanged, dropped) between two
 * {@link RepoSet}s, and builds each {@link Change}'s human-readable summary + machine detail.
 * The comparison primitives are {@link OverlayDiff}'s (built for base-vs-overlay of one object -
 * the operation is identical for A-object vs B-object).
 */
public final class SnapshotDiff {
    private final RepoSet a;
    private final RepoSet b;
    private final List<Change> changes = new ArrayList<>();

    public SnapshotDiff(RepoSet baseline, RepoSet current) {
        this.a = baseline;
        this.b = current;
    }

    public List<Change> run() throws Exception {
        forms();
        workflow("Active Link", "active_link",
            a.workflow().listActiveLinkNames(), b.workflow().listActiveLinkNames(),
            n -> a.workflow().getActiveLink(n), n -> b.workflow().getActiveLink(n));
        workflow("Filter", "filter",
            a.workflow().listFilterNames(), b.workflow().listFilterNames(),
            n -> a.workflow().getFilter(n), n -> b.workflow().getFilter(n));
        workflow("Escalation", "escalation",
            a.workflow().listEscalationNames(), b.workflow().listEscalationNames(),
            n -> a.workflow().getEscalation(n), n -> b.workflow().getEscalation(n));
        menus();
        containers();
        images();
        return changes;
    }

    /* ---------- forms ---------- */

    private void forms() throws Exception {
        for (String name : union(a.schemas().listFormNames(), b.schemas().listFormNames())) {
            boolean inA = a.schemas().listFormNames().contains(name);
            boolean inB = b.schemas().listFormNames().contains(name);
            if (inA && !inB) { add(new Change("Form", "form", name, Change.Kind.REMOVED, a.schemas().getForm(name), null)); continue; }
            if (!inA && inB) { add(new Change("Form", "form", name, Change.Kind.ADDED, null, b.schemas().getForm(name))); continue; }

            Form fa = a.schemas().getForm(name), fb = b.schemas().getForm(name);
            List<Field> flA = sorted(a.schemas().getFields(name)), flB = sorted(b.schemas().getFields(name));

            var fieldDiff = OverlayDiff.diffKeyed(flA, flB, Field::getFieldID, SnapshotDiff::fieldsEqual);
            var indexDiff = OverlayDiff.diffKeyed(fa.getIndexInfo(), fb.getIndexInfo(), IndexInfo::getIndexName, IndexInfo::equals);
            var propDiff = OverlayDiff.diffProperties(fa.getProperties(), fb.getProperties());
            var permDiff = OverlayDiff.diffKeyed(fa.getPermissions(), fb.getPermissions(), PermissionInfo::getGroupID, PermissionInfo::equals);
            var sortDiff = OverlayDiff.diffKeyed(fa.getSortInfo(), fb.getSortInfo(), SortInfo::getFieldID, SortInfo::equals);
            var elDiff = OverlayDiff.diffKeyed(fa.getEntryListFieldInfo(), fb.getEntryListFieldInfo(), EntryListFieldInfo::getFieldId, EntryListFieldInfo::equals);
            var viewDiff = OverlayDiff.diffKeyed(a.schemas().getViews(name), b.schemas().getViews(name), View::getVUIId, View::equals);
            boolean archiveChanged = !Objects.equals(fa.getArchiveInfo(), fb.getArchiveInfo());
            boolean auditChanged = !Objects.equals(fa.getAuditInfo(), fb.getAuditInfo());

            boolean changed = !fieldDiff.isEmpty() || !indexDiff.isEmpty() || !propDiff.isEmpty() || !permDiff.isEmpty()
                || !sortDiff.isEmpty() || !elDiff.isEmpty() || !viewDiff.isEmpty() || archiveChanged || auditChanged;
            if (!changed) continue;

            Change c = new Change("Form", "form", name, Change.Kind.MODIFIED, fa, fb);
            countLine(c, fieldDiff, "field");
            if (!propDiff.isEmpty()) c.summary.add(propDiff.size() + " property change" + s(propDiff.size()));
            countLine(c, permDiff, "permission");
            countLine(c, indexDiff, "index");
            countLine(c, sortDiff, "sort field");
            countLine(c, elDiff, "result-list field");
            countLine(c, viewDiff, "view");
            if (archiveChanged) c.summary.add("archive settings changed");
            if (auditChanged) c.summary.add("audit settings changed");
            c.json.put("fields", itemJson(fieldDiff, Field::getName));
            c.json.put("properties", propJson(propDiff));
            c.json.put("permissions", itemJson(permDiff, p -> String.valueOf(p.getGroupID())));
            c.json.put("indexes", itemJson(indexDiff, IndexInfo::getIndexName));
            c.json.put("archiveChanged", archiveChanged);
            c.json.put("auditChanged", auditChanged);
            add(c);
        }
    }

    /* ---------- workflow (AL / Filter / Escalation) ---------- */

    private interface Fetch<T> { T get(String name) throws Exception; }

    private <T> void workflow(String typeLabel, String slug, List<String> namesA, List<String> namesB,
                              Fetch<T> fetchA, Fetch<T> fetchB) throws Exception {
        Set<String> setA = new LinkedHashSet<>(namesA), setB = new LinkedHashSet<>(namesB);
        for (String name : union(namesA, namesB)) {
            if (setA.contains(name) && !setB.contains(name)) { add(new Change(typeLabel, slug, name, Change.Kind.REMOVED, fetchA.get(name), null)); continue; }
            if (!setA.contains(name) && setB.contains(name)) { add(new Change(typeLabel, slug, name, Change.Kind.ADDED, null, fetchB.get(name))); continue; }

            Object oa = fetchA.get(name), ob = fetchB.get(name);
            WorkflowView wa = WorkflowView.of(oa), wb = WorkflowView.of(ob);

            var propDiff = OverlayDiff.diffProperties(wa.properties, wb.properties);
            boolean qualifierChanged = !Objects.equals(wa.qualifier, wb.qualifier);
            boolean actionsChanged = !Objects.equals(wa.actions, wb.actions) || !Objects.equals(wa.elseActions, wb.elseActions);
            var formListDiff = OverlayDiff.diffKeyed(wa.forms, wb.forms, java.util.function.Function.identity(), Object::equals);
            boolean enabledChanged = wa.enabled != wb.enabled;
            boolean orderChanged = wa.order != wb.order;

            if (propDiff.isEmpty() && !qualifierChanged && !actionsChanged && formListDiff.isEmpty() && !enabledChanged && !orderChanged) continue;

            Change c = new Change(typeLabel, slug, name, Change.Kind.MODIFIED, oa, ob);
            if (enabledChanged) c.summary.add("enabled: " + yn(wa.enabled) + " → " + yn(wb.enabled));
            if (orderChanged) c.summary.add("order: " + wa.order + " → " + wb.order);
            if (qualifierChanged) c.summary.add("Run If qualification changed");
            if (actionsChanged) c.summary.add("action list changed");
            long fAdd = formListDiff.stream().filter(i -> i.status() == OverlayDiff.Status.ADDED).count();
            long fRem = formListDiff.stream().filter(i -> i.status() == OverlayDiff.Status.REMOVED).count();
            if (fAdd > 0 || fRem > 0) c.summary.add("form list: " + (fAdd > 0 ? "+" + fAdd + " " : "") + (fRem > 0 ? "-" + fRem : "").trim());
            if (!propDiff.isEmpty()) c.summary.add(propDiff.size() + " property change" + s(propDiff.size()));
            c.json.put("enabledChanged", enabledChanged);
            c.json.put("orderChanged", orderChanged);
            c.json.put("qualifierChanged", qualifierChanged);
            c.json.put("actionsChanged", actionsChanged);
            c.json.put("formsAdded", formListDiff.stream().filter(i -> i.status() == OverlayDiff.Status.ADDED).map(OverlayDiff.Item::current).toList());
            c.json.put("formsRemoved", formListDiff.stream().filter(i -> i.status() == OverlayDiff.Status.REMOVED).map(OverlayDiff.Item::current).toList());
            c.json.put("properties", propJson(propDiff));
            add(c);
        }
    }

    /** Common accessor view over ActiveLink / Filter / Escalation. */
    private record WorkflowView(PropertyMap properties, Object qualifier, List<?> actions, List<?> elseActions,
                                List<String> forms, boolean enabled, int order) {
        static WorkflowView of(Object o) {
            if (o instanceof ActiveLink al) return new WorkflowView(al.getProperties(), al.getQualifier(), al.getActionList(), al.getElseList(), al.getFormList(), al.isEnable(), al.getOrder());
            if (o instanceof Filter f) return new WorkflowView(f.getProperties(), f.getQualifier(), f.getActionList(), f.getElseList(), f.getFormList(), f.isEnable(), f.getOrder());
            Escalation e = (Escalation) o;
            return new WorkflowView(e.getProperties(), e.getQualifier(), e.getActionList(), e.getElseList(), e.getFormList(), e.isEnable(), 0);
        }
    }

    /* ---------- menus / containers / images ---------- */

    private void menus() throws Exception {
        genericProps("Menu", "menu", a.workflow().listMenuNames(), b.workflow().listMenuNames(),
            n -> a.workflow().getMenu(n), n -> b.workflow().getMenu(n),
            o -> ((Menu) o).getProperties());
    }

    private void containers() throws Exception {
        List<String> na = new ArrayList<>(), nb = new ArrayList<>();
        for (int t : new int[]{Constants.ARCON_GUIDE, Constants.ARCON_APP, Constants.ARCON_PACK, Constants.ARCON_FILTER_GUIDE, Constants.ARCON_WEBSERVICE}) {
            na.addAll(a.containers().listContainerNames(t));
            nb.addAll(b.containers().listContainerNames(t));
        }
        genericProps("Container", "container", na, nb,
            n -> a.containers().getContainer(n), n -> b.containers().getContainer(n),
            o -> ((Container) o).getProperties());
    }

    private void images() throws Exception {
        genericProps("Image", "image", a.images().listImageNames(), b.images().listImageNames(),
            n -> a.images().getImage(n), n -> b.images().getImage(n),
            o -> ((Image) o).getProperties());
    }

    private void genericProps(String typeLabel, String slug, List<String> namesA, List<String> namesB,
                              Fetch<?> fetchA, Fetch<?> fetchB,
                              java.util.function.Function<Object, PropertyMap> props) throws Exception {
        Set<String> setA = new LinkedHashSet<>(namesA), setB = new LinkedHashSet<>(namesB);
        for (String name : union(namesA, namesB)) {
            if (setA.contains(name) && !setB.contains(name)) { add(new Change(typeLabel, slug, name, Change.Kind.REMOVED, fetchA.get(name), null)); continue; }
            if (!setA.contains(name) && setB.contains(name)) { add(new Change(typeLabel, slug, name, Change.Kind.ADDED, null, fetchB.get(name))); continue; }
            Object oa = fetchA.get(name), ob = fetchB.get(name);
            var propDiff = OverlayDiff.diffProperties(props.apply(oa), props.apply(ob));
            boolean menuItemsChanged = oa instanceof ListMenu la && ob instanceof ListMenu lb && !Objects.equals(la.getItems(), lb.getItems());
            if (propDiff.isEmpty() && !menuItemsChanged) continue;
            Change c = new Change(typeLabel, slug, name, Change.Kind.MODIFIED, oa, ob);
            if (!propDiff.isEmpty()) c.summary.add(propDiff.size() + " property change" + s(propDiff.size()));
            if (menuItemsChanged) c.summary.add("menu items changed");
            c.json.put("properties", propJson(propDiff));
            c.json.put("menuItemsChanged", menuItemsChanged);
            add(c);
        }
    }

    /* ---------- helpers ---------- */

    private void add(Change c) { changes.add(c); }

    private static <T> void countLine(Change c, List<OverlayDiff.Item<T>> diff, String noun) {
        if (diff.isEmpty()) return;
        long added = diff.stream().filter(i -> i.status() == OverlayDiff.Status.ADDED).count();
        long removed = diff.stream().filter(i -> i.status() == OverlayDiff.Status.REMOVED).count();
        long changed = diff.stream().filter(i -> i.status() == OverlayDiff.Status.CHANGED).count();
        List<String> parts = new ArrayList<>();
        if (added > 0) parts.add(added + " added");
        if (removed > 0) parts.add(removed + " removed");
        if (changed > 0) parts.add(changed + " changed");
        c.summary.add(noun + "s: " + String.join(", ", parts));
    }

    private static <T> List<Object> itemJson(List<OverlayDiff.Item<T>> diff, java.util.function.Function<T, String> label) {
        List<Object> out = new ArrayList<>();
        for (var i : diff) {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("name", label.apply(i.current()));
            m.put("change", i.status().name().toLowerCase());
            out.add(m);
        }
        return out;
    }

    private static List<Object> propJson(List<OverlayDiff.PropChange> diff) {
        List<Object> out = new ArrayList<>();
        for (var p : diff) {
            var m = new java.util.LinkedHashMap<String, Object>();
            m.put("property", p.label());
            m.put("from", p.baseValue());
            m.put("to", p.overlayValue());
            out.add(m);
        }
        return out;
    }

    /** Snapshot-diff field equality: like SchemaDetailPage.fieldsEqual but also compares the name (a field can be renamed while keeping its ID between snapshots). */
    public static boolean fieldsEqual(Field a, Field b) {
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (a.getDataType() != b.getDataType()) return false;
        if (a.getFieldOption() != b.getFieldOption()) return false;
        if (!Objects.equals(a.getDefaultValue(), b.getDefaultValue())) return false;
        if (!Objects.equals(a.getFieldLimit(), b.getFieldLimit())) return false;
        return permSig(a.getAssignedGroup()).equals(permSig(b.getAssignedGroup()));
    }

    private static Set<String> permSig(List<PermissionInfo> perms) {
        Set<String> sig = new TreeSet<>();
        if (perms != null) for (PermissionInfo p : perms) sig.add(p.getGroupID() + ":" + p.getPermissionValue());
        return sig;
    }

    private static List<Field> sorted(List<Field> fields) {
        List<Field> copy = new ArrayList<>(fields);
        copy.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        return copy;
    }

    private static List<String> union(List<String> a, List<String> b) {
        Set<String> u = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        u.addAll(a); u.addAll(b);
        return new ArrayList<>(u);
    }

    private static String s(int n) { return n == 1 ? "" : "s"; }
    private static String yn(boolean b) { return b ? "Yes" : "No"; }
}
