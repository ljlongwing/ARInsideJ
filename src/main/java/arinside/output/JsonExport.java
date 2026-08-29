package arinside.output;

import arinside.config.AppConfig;
import arinside.util.Json;
import com.bmc.arsys.api.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Optional machine-readable export: writes {@code <target>/data/*.json} (one array file per object
 * type plus {@code data/manifest.json}) alongside the HTML, for CI checks / external analysis /
 * snapshot diffing. Off by default; enabled with {@code JsonOutput=TRUE} in settings.ini.
 *
 * Records carry identity + structure + primary relationships (not every last property - that's a
 * later phase). Fed by the {@code *DetailPage.render()} passes via the static {@code add*} methods,
 * same accumulator pattern as {@link SearchIndex}; thread-safe (ConcurrentLinkedQueue) since detail
 * pages render on the parallel write pool. Under {@code --scope} only the in-scope objects have a
 * full detail render, so only those appear here.
 */
public final class JsonExport {
    private JsonExport() {}

    private static final Queue<Map<String, Object>> forms = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> activeLinks = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> filters = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> escalations = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> menus = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> containers = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> groups = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> roles = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> users = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> images = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> associations = new ConcurrentLinkedQueue<>();

    public static void clear() {
        for (Queue<?> q : List.of(forms, activeLinks, filters, escalations, menus, containers,
                groups, roles, users, images, associations)) q.clear();
    }

    /* ---------- builders ---------- */

    private static Map<String, Object> rec() { return new LinkedHashMap<>(); }

    private static Long epoch(Timestamp ts) { return ts == null ? null : ts.getValue(); }

    private static List<String> actionTypes(List<?> actionList) {
        List<String> out = new ArrayList<>();
        if (actionList != null) for (Object a : actionList) out.add(a.getClass().getSimpleName());
        return out;
    }

    public static void addForm(String name, Form form, int overlayType, int fieldCount, int viewCount) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("type", form.getFormType());
        r.put("overlay", overlayType);
        r.put("modified", epoch(form.getLastUpdateTime()));
        r.put("modifiedBy", form.getLastChangedBy());
        r.put("fieldCount", fieldCount);
        r.put("viewCount", viewCount);
        AuditInfo audit = form.getAuditInfo();
        ArchiveInfo archive = form.getArchiveInfo();
        r.put("auditEnabled", audit != null && audit.isEnable());
        r.put("archiveEnabled", archive != null && archive.isEnable());
        forms.add(r);
    }

    private static Map<String, Object> workflowRec(String name, boolean enabled, int overlayType,
            Timestamp modified, String modifiedBy, List<String> formList,
            List<?> ifActions, List<?> elseActions) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("enabled", enabled);
        r.put("overlay", overlayType);
        r.put("modified", epoch(modified));
        r.put("modifiedBy", modifiedBy);
        List<String> fl = formList == null ? List.of() : formList;
        r.put("forms", new ArrayList<>(fl));
        r.put("shared", fl.size() > 1);
        r.put("ifActions", actionTypes(ifActions));
        r.put("elseActions", actionTypes(elseActions));
        return r;
    }

    public static void addActiveLink(String name, ActiveLink al, int overlayType) {
        Map<String, Object> r = workflowRec(name, al.isEnable(), overlayType, al.getLastUpdateTime(),
            al.getLastChangedBy(), al.getFormList(), al.getActionList(), al.getElseList());
        r.put("order", al.getOrder());
        r.put("executeMask", al.getExecuteMask());
        r.put("groupCount", al.getGroupList() == null ? 0 : al.getGroupList().size());
        activeLinks.add(r);
    }

    public static void addFilter(String name, Filter filter, int overlayType) {
        Map<String, Object> r = workflowRec(name, filter.isEnable(), overlayType, filter.getLastUpdateTime(),
            filter.getLastChangedBy(), filter.getFormList(), filter.getActionList(), filter.getElseList());
        r.put("order", filter.getOrder());
        r.put("opSet", filter.getOpSet());
        filters.add(r);
    }

    public static void addEscalation(String name, Escalation esc, int overlayType) {
        Map<String, Object> r = workflowRec(name, esc.isEnable(), overlayType, esc.getLastUpdateTime(),
            esc.getLastChangedBy(), esc.getFormList(), esc.getActionList(), esc.getElseList());
        escalations.add(r);
    }

    public static void addMenu(String name, Menu menu, int overlayType) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("menuType", menu.getMenuType());
        r.put("refreshCode", menu.getRefreshCode());
        r.put("overlay", overlayType);
        r.put("modified", epoch(menu.getLastUpdateTime()));
        r.put("modifiedBy", menu.getLastChangedBy());
        menus.add(r);
    }

    public static void addContainer(String name, Container c, int overlayType) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("containerType", c.getType());
        r.put("overlay", overlayType);
        r.put("modified", epoch(c.getLastUpdateTime()));
        r.put("modifiedBy", c.getLastChangedBy());
        containers.add(r);
    }

    public static void addGroup(int groupId, String name, int groupType, int category, Long modified, String modifiedBy) {
        Map<String, Object> r = rec();
        r.put("groupId", groupId);
        r.put("name", name);
        r.put("groupType", groupType);
        r.put("category", category);
        r.put("modified", modified);
        r.put("modifiedBy", modifiedBy);
        groups.add(r);
    }

    public static void addRole(int roleId, String name, String application, Long modified, String modifiedBy) {
        Map<String, Object> r = rec();
        r.put("roleId", roleId);
        r.put("name", name);
        r.put("application", application);
        r.put("modified", modified);
        r.put("modifiedBy", modifiedBy);
        roles.add(r);
    }

    public static void addUser(String loginName, String fullName, String email, int licenseType, int groupCount) {
        Map<String, Object> r = rec();
        r.put("loginName", loginName);
        r.put("fullName", fullName);
        r.put("email", email);
        r.put("licenseType", licenseType);
        r.put("groupCount", groupCount);
        users.add(r);
    }

    public static void addImage(String name, String type, Long modified, String modifiedBy) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("type", type);
        r.put("modified", modified);
        r.put("modifiedBy", modifiedBy);
        images.add(r);
    }

    public static void addAssociation(String name, String kind, String primaryForm, String secondaryForm,
            String cardinality, String enforcement) {
        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("kind", kind);
        r.put("primaryForm", primaryForm);
        r.put("secondaryForm", secondaryForm);
        r.put("cardinality", cardinality);
        r.put("enforcement", enforcement);
        associations.add(r);
    }

    /* ---------- output ---------- */

    public static void writeTo(AppConfig appConfig) {
        Path dir = Path.of(appConfig.targetFolder, "data");
        try {
            Files.createDirectories(dir);
            writeArray(dir.resolve("forms.json"), forms);
            writeArray(dir.resolve("active_links.json"), activeLinks);
            writeArray(dir.resolve("filters.json"), filters);
            writeArray(dir.resolve("escalations.json"), escalations);
            writeArray(dir.resolve("menus.json"), menus);
            writeArray(dir.resolve("containers.json"), containers);
            writeArray(dir.resolve("groups.json"), groups);
            writeArray(dir.resolve("roles.json"), roles);
            writeArray(dir.resolve("users.json"), users);
            writeArray(dir.resolve("images.json"), images);
            writeArray(dir.resolve("associations.json"), associations);

            Map<String, Object> manifest = rec();
            manifest.put("tool", arinside.Version.PRODUCT_NAME);
            manifest.put("version", arinside.Version.APP_VERSION);
            manifest.put("generated", Instant.now().toString());
            manifest.put("server", appConfig.serverName == null || appConfig.serverName.isEmpty()
                ? (appConfig.fileMode ? "file:" + appConfig.objListXML : "") : appConfig.serverName);
            manifest.put("scope", appConfig.scope == null ? "" : appConfig.scope);
            Map<String, Object> counts = rec();
            counts.put("forms", forms.size());
            counts.put("activeLinks", activeLinks.size());
            counts.put("filters", filters.size());
            counts.put("escalations", escalations.size());
            counts.put("menus", menus.size());
            counts.put("containers", containers.size());
            counts.put("groups", groups.size());
            counts.put("roles", roles.size());
            counts.put("users", users.size());
            counts.put("images", images.size());
            counts.put("associations", associations.size());
            manifest.put("counts", counts);
            Files.writeString(dir.resolve("manifest.json"), Json.write(manifest), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing JSON export to " + dir, e);
        }
    }

    private static void writeArray(Path file, Queue<Map<String, Object>> recs) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>(recs);
        list.sort((a, b) -> String.valueOf(firstNameKey(a)).compareToIgnoreCase(String.valueOf(firstNameKey(b))));
        StringBuilder sb = new StringBuilder();
        Json.write(list, sb);
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static Object firstNameKey(Map<String, Object> r) {
        for (String k : new String[]{"name", "loginName"}) if (r.containsKey(k)) return r.get(k);
        return "";
    }
}
