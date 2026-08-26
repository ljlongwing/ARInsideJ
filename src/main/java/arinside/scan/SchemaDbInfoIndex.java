package arinside.scan;

import arinside.ar.ArClient;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.SQLResult;
import com.bmc.arsys.api.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of {@code CARSchemaList::LoadDatabaseDetails}/{@code SchemaDbQueryBuilder}
 * (lists/ARSchemaList.cpp, lists/support/SchemaDbQueryBuilder.cpp) - the DB Table ID/View/SH-View
 * rows on the schema General tab. Previously documented as a permanent Java-API-surface gap (a
 * {@code javap}/{@code jar tf} scan found no dedicated {@code Form} accessor) - that check missed
 * that the C++ itself doesn't get this data from a normal object API either: it runs a raw SQL
 * passthrough query through the AR System API (<code>ARGetListSQL(..., "select schemaId, name,
 * viewName, shViewName from arschema", ...)</code>), which has a direct Java equivalent -
 * {@link com.bmc.arsys.api.ARServerUser#getListSQL(String, int, boolean)} - confirmed present via
 * {@code javap} on the real 23.3.002 jar, not previously checked for.
 *
 * <p>Built once, up front, server-mode only (there is no live SQL passthrough in file/XML mode, so
 * this data simply never appears there - same category as ServerInfo/users/groups/roles). Paginates
 * the same way the C++ does when the server truncates results: order by schemaId ascending, resume
 * with {@code where schemaId > <last seen schemaId>}, using {@link SQLResult#getTotalNumberOfMatches()}
 * to detect truncation (the {@code getListSQL} call has no {@code firstRetrieve} offset parameter the
 * way {@code getListEntryObjects} does, so this is the only pagination mechanism available for a raw
 * SQL result set - see {@link arinside.ar.RawEntryQuery} for the offset-based sibling pattern used
 * elsewhere in this port).
 */
public final class SchemaDbInfoIndex {

    public record SchemaDbInfo(int schemaId, String viewName, String shViewName) {}

    private final Map<String, SchemaDbInfo> byName = new HashMap<>();

    public static SchemaDbInfoIndex build(ArClient client) throws ARException {
        SchemaDbInfoIndex idx = new SchemaDbInfoIndex();
        if (client == null) return idx; // file/XML mode - no live SQL passthrough available

        int lastSchemaId = 0;
        while (true) {
            String sql = "select schemaId, name, viewName, shViewName from arschema"
                + (lastSchemaId > 0 ? " where schemaId > " + lastSchemaId : "")
                + " order by schemaId asc";
            SQLResult result = client.raw().getListSQL(sql, 0, false);
            List<List<Value>> rows = result.getContents();
            if (rows.isEmpty()) break;

            int maxSchemaIdThisPage = lastSchemaId;
            for (List<Value> row : rows) {
                if (row.size() < 4) continue;
                Integer schemaId = intOf(row.get(0));
                String name = strOf(row.get(1));
                if (schemaId == null || name == null || name.isEmpty()) continue;
                idx.byName.put(name, new SchemaDbInfo(schemaId, strOf(row.get(2)), strOf(row.get(3))));
                if (schemaId > maxSchemaIdThisPage) maxSchemaIdThisPage = schemaId;
            }

            if (rows.size() >= result.getTotalNumberOfMatches() || maxSchemaIdThisPage <= lastSchemaId) break;
            lastSchemaId = maxSchemaIdThisPage;
        }
        return idx;
    }

    private static Integer intOf(Value v) {
        return (v != null && v.getValue() instanceof Number n) ? n.intValue() : null;
    }

    private static String strOf(Value v) {
        return (v == null || v.getValue() == null) ? "" : v.getValue().toString();
    }

    /** Null if this form has no database-table row at all (e.g. a schema created and never saved to a real DB table, or file/XML mode, which never has this data). */
    public SchemaDbInfo find(String formName) {
        return byName.get(formName);
    }
}
