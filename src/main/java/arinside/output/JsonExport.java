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
    /** Per-form full detail (fields / permissions / indexes / sort / views) - written to data/forms/&lt;name&gt;.json. */
    private static final Queue<Map<String, Object>> formDetails = new ConcurrentLinkedQueue<>();
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
    private static final Queue<Map<String, Object>> isBundles = new ConcurrentLinkedQueue<>();
    private static final Queue<Map<String, Object>> isDefinitions = new ConcurrentLinkedQueue<>();

    public static void clear() {
        for (Queue<?> q : List.of(forms, formDetails, activeLinks, filters, escalations, menus, containers,
                groups, roles, users, images, associations, isBundles, isDefinitions)) q.clear();
    }

    /* ---------- Innovation Studio ---------- */

    public static void addIsBundle(arinside.ar.is.IsBundle b) {
        Map<String, Object> r = rec();
        r.put("id", b.id());
        r.put("name", b.friendlyName());
        r.put("version", b.version());
        r.put("developer", b.developerId());
        r.put("isApplication", b.isApplication());
        r.put("lastDeployed", b.lastDeployedTime());
        isBundles.add(r);
    }

    public static void addIsDefinition(arinside.ar.is.IsDefinition d) {
        Map<String, Object> r = rec();
        r.put("type", d.type().label);
        r.put("name", d.name());
        r.put("description", d.description());
        r.put("enabled", d.enabled());
        r.put("scope", d.scope());
        r.put("overlay", d.isOverlay());
        r.put("modified", d.modifiedEpoch());
        r.put("modifiedBy", d.modifiedBy());
        r.put("guid", d.guid());
        isDefinitions.add(r);
    }

    /* ---------- builders ---------- */

    private static Map<String, Object> rec() { return new LinkedHashMap<>(); }

    private static Long epoch(Timestamp ts) { return ts == null ? null : ts.getValue(); }

    private static List<String> actionTypes(List<?> actionList) {
        List<String> out = new ArrayList<>();
        if (actionList != null) for (Object a : actionList) out.add(a.getClass().getSimpleName());
        return out;
    }

    public static void addForm(String name, Form form, int overlayType, List<Field> fields, List<String> viewNames) {
        int fieldCount = fields == null ? 0 : fields.size();
        int viewCount = viewNames == null ? 0 : viewNames.size();
        AuditInfo audit = form.getAuditInfo();
        ArchiveInfo archive = form.getArchiveInfo();
        boolean auditEnabled = audit != null && audit.isEnable();
        boolean archiveEnabled = archive != null && archive.isEnable();

        Map<String, Object> r = rec();
        r.put("name", name);
        r.put("type", form.getFormType());
        r.put("typeName", arinside.ar.AREnumLabels.schemaType(form.getFormType()));
        r.put("overlay", overlayType);
        r.put("modified", epoch(form.getLastUpdateTime()));
        r.put("modifiedBy", form.getLastChangedBy());
        r.put("fieldCount", fieldCount);
        r.put("viewCount", viewCount);
        r.put("auditEnabled", auditEnabled);
        r.put("archiveEnabled", archiveEnabled);
        forms.add(r);

        Map<String, Object> d = rec();
        d.put("name", name);
        d.put("type", form.getFormType());
        d.put("typeName", arinside.ar.AREnumLabels.schemaType(form.getFormType()));
        d.put("overlay", overlayType);
        d.put("modified", epoch(form.getLastUpdateTime()));
        d.put("modifiedBy", form.getLastChangedBy());
        d.put("auditEnabled", auditEnabled);
        d.put("archiveEnabled", archiveEnabled);
        d.put("permissions", permissionList(form.getAssignedGroup()));
        d.put("adminGroups", form.getAdminGrpList() == null ? List.of() : new ArrayList<>(form.getAdminGrpList()));
        d.put("indexes", indexList(form.getIndexInfo()));
        d.put("sortList", sortList(form.getSortInfo()));
        d.put("views", viewNames == null ? List.of() : new ArrayList<>(viewNames));
        List<Object> fieldRecs = new ArrayList<>();
        if (fields != null) for (Field f : fields) {
            Map<String, Object> fr = rec();
            fr.put("id", f.getFieldID());
            fr.put("name", f.getName());
            fr.put("dataType", f.getDataType());
            fr.put("dataTypeName", arinside.ar.AREnumLabels.dataType(f.getDataType()));
            fr.put("option", f.getFieldOption());
            fr.put("optionName", arinside.ar.AREnumLabels.fieldOption(f.getFieldOption()));
            fr.put("createMode", f.getCreateMode());
            fr.put("permissions", permissionList(f.getAssignedGroup()));
            fieldRecs.add(fr);
        }
        d.put("fields", fieldRecs);
        formDetails.add(d);
    }

    private static List<Object> permissionList(List<PermissionInfo> perms) {
        List<Object> out = new ArrayList<>();
        if (perms != null) for (PermissionInfo p : perms) {
            Map<String, Object> m = rec();
            m.put("groupId", p.getGroupID());
            m.put("permission", p.getPermissionValue());
            out.add(m);
        }
        return out;
    }

    private static List<Object> indexList(List<IndexInfo> indexes) {
        List<Object> out = new ArrayList<>();
        if (indexes != null) for (IndexInfo ix : indexes) {
            Map<String, Object> m = rec();
            m.put("name", ix.getIndexName());
            m.put("unique", ix.isUnique());
            m.put("fields", ix.getIndexFields() == null ? List.of() : new ArrayList<>(ix.getIndexFields()));
            out.add(m);
        }
        return out;
    }

    private static List<Object> sortList(List<SortInfo> sorts) {
        List<Object> out = new ArrayList<>();
        if (sorts != null) for (SortInfo s : sorts) {
            Map<String, Object> m = rec();
            m.put("fieldId", s.getFieldID());
            m.put("order", s.getSortOrder());
            out.add(m);
        }
        return out;
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
        r.put("groups", al.getGroupList() == null ? List.of() : new ArrayList<>(al.getGroupList()));
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
            if (!isBundles.isEmpty() || !isDefinitions.isEmpty()) {
                Path isDir = dir.resolve("is");
                Files.createDirectories(isDir);
                writeArray(isDir.resolve("bundles.json"), isBundles);
                writeArray(isDir.resolve("definitions.json"), isDefinitions);
            }

            // Per-object form detail (fields / permissions / indexes / sort / views).
            int detailFiles = 0;
            if (!formDetails.isEmpty()) {
                Path formsDir = dir.resolve("forms");
                Files.createDirectories(formsDir);
                for (Map<String, Object> d : formDetails) {
                    Files.writeString(formsDir.resolve(sanitize(String.valueOf(d.get("name"))) + ".json"),
                        Json.write(d), StandardCharsets.UTF_8);
                    detailFiles++;
                }
            }

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
            manifest.put("formDetailFiles", detailFiles); // data/forms/<name>.json
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

    /** Filename-safe form name for data/forms/&lt;name&gt;.json (same scheme as the diff report pages). */
    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return s.isEmpty() ? "_" : s;
    }
}
