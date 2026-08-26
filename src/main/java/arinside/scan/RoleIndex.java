package arinside.scan;

import arinside.ar.RoleRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of the app-scoped role-existence check {@code CARInside::ValidateGroup}/{@code LinkToGroup}
 * apply (ARInside.cpp) whenever a permission list entry's group ID is negative (role-based, see
 * {@link RoleRecord}'s javadoc): {@code CARRole(roleId, appRefName).Exists()} looks the role up via
 * {@code ARRoleList::Find(roleId, appName)}, an exact match on BOTH the role ID and the owning
 * application name (confirmed by reading lists/ARRoleList.cpp directly - not "any app with that
 * role ID", a role belonging to a different application does not count). A permission row whose
 * role doesn't exist for the schema's own app is skipped entirely by the C++, not shown as
 * unresolved/dangling text.
 *
 * Built once, early (right after {@code IdentityRepository} is available, well before schema
 * documentation starts) from the same {@code identity.listRoles()} call {@code RoleOverviewPage}
 * makes later in the pipeline for the roles catalog page - a small, accepted second raw-entry-query
 * fetch (a single query returning ~hundreds of rows on a real server, not a per-object loop) rather
 * than threading the already-fetched list all the way through Main.java's later role-detail-page
 * loop, which would have been a larger, riskier restructuring for this fix.
 */
public final class RoleIndex {
    private final Map<Integer, Map<String, RoleRecord>> byIdAndApp = new HashMap<>();

    public static RoleIndex build(List<RoleRecord> roles) {
        RoleIndex idx = new RoleIndex();
        for (RoleRecord r : roles) {
            idx.byIdAndApp.computeIfAbsent(r.roleId, k -> new HashMap<>()).put(nullToEmpty(r.applicationName), r);
        }
        return idx;
    }

    /** Null if no role with this ID exists for this exact app (or this index has no role data at all - e.g. file mode, which never loads a role list either, matching the C++'s own file-mode behavior of ValidateGroup always returning false in that case). */
    public RoleRecord find(int roleId, String appRefName) {
        Map<String, RoleRecord> byApp = byIdAndApp.get(roleId);
        return byApp == null ? null : byApp.get(nullToEmpty(appRefName));
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
