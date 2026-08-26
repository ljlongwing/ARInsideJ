package arinside.ar;

import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic raw entry-query helper - the Java equivalent of the C++'s ARGetListEntryWithFields-based
 * loaders (lists/ARUserList.cpp, ARGroupList.cpp, ARRoleList.cpp). All three load their data via a
 * raw query against a reserved admin form (User/Group/Roles) rather than the convenience
 * getListUser/getListGroup/getListRole calls: those convenience calls don't carry the same field
 * set (no Full Name/Group List for users, no Category for groups) and, for roles, don't work as a
 * catalog listing at all - getListRole is a per-user membership lookup, not a roles catalog.
 * Centralized here since users/groups/roles all follow the identical shape: parseQualification +
 * getListEntryObjects against a configurable form/query (AppConfig's userForm/userQuery/groupForm/
 * groupQuery/roleForm/roleQuery, defaults "User"/"Group"/"Roles", all "1=1").
 *
 * Handles server-side result chunking the same way the C++'s offset loop does (maxRetrieve=0 means
 * "no limit" in a single call in practice on most servers, but the loop below is kept for servers
 * that do chunk).
 */
public final class RawEntryQuery {
    private RawEntryQuery() {}

    public static List<Entry> query(ArClient client, String formName, String qualifierString, int[] fieldIds) throws ARException {
        QualifierInfo qualifier = client.raw().parseQualification(formName, qualifierString);
        List<Entry> all = new ArrayList<>();
        int firstRetrieve = 0;
        while (true) {
            OutputInteger numMatches = new OutputInteger();
            List<Entry> page = client.raw().getListEntryObjects(formName, qualifier, firstRetrieve, 0, null, fieldIds, false, numMatches);
            if (page.isEmpty()) break;
            all.addAll(page);
            if (all.size() >= numMatches.intValue()) break;
            firstRetrieve = all.size();
        }
        return all;
    }

    public static String str(Entry e, int fieldId) {
        Value v = e.get(fieldId);
        return (v == null || v.getValue() == null) ? "" : v.getValue().toString();
    }

    public static int intVal(Entry e, int fieldId) {
        Value v = e.get(fieldId);
        if (v == null || !(v.getValue() instanceof Number n)) return 0;
        return n.intValue();
    }

    public static Timestamp timestamp(Entry e, int fieldId) {
        Value v = e.get(fieldId);
        return (v != null && v.getValue() instanceof Timestamp t) ? t : null;
    }

    /** Parses the C++'s semicolon-separated group-id-list fields (e.g. "4870051;4870099;"). */
    public static List<Integer> intList(Entry e, int fieldId) {
        List<Integer> ids = new ArrayList<>();
        for (String part : str(e, fieldId).split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                // matches C++'s GetGroupStringAsVector, which silently skips non-numeric tokens via atoi's 0-on-failure semantics
            }
        }
        return ids;
    }
}
