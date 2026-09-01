package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.View;

import java.util.ArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of the schema-loading portion of CARInside::LoadForms + CARSchemaList/CARFieldList
 * (ARInside.cpp, lists/ARSchemaList.cpp). See {@link SchemaBulkCache}'s javadoc for the bulk-fetch
 * story: getListFormObjects() looked broken on a first pass (silently returned 0 forms no matter the
 * filter params tried) but was actually just missing an explicit `setRetrieveAll(true)` - inherited
 * from FormCriteria's CriteriaFlags grandparent, not visible via javap on FormCriteria in isolation,
 * so easy to miss. With that cache, listFormNames()/getForm() are fast; without it (cache == null,
 * e.g. bulk load failed), they fall back to the original per-object live calls.
 */
public final class SchemaRepository implements SchemaSource {
    private final ArClient client;
    private final BlackList blackList;
    private final SchemaBulkCache cache;

    /** No bulk cache - always fetches live, one object per call (matches this class's original behavior). */
    public SchemaRepository(ArClient client, BlackList blackList) {
        this(client, blackList, null);
    }

    /** cache may be null (no bulk cache built, or this repository intentionally shouldn't use one) - falls back to live per-name fetches exactly as before. See SchemaBulkCache's javadoc. */
    public SchemaRepository(ArClient client, BlackList blackList, SchemaBulkCache cache) {
        this.client = client;
        this.blackList = blackList;
        this.cache = cache;
    }

    /**
     * Matches CARInside::InBlacklist filtering applied in CScanSchema/LoadForms - excluded forms
     * never appear in any listing. The live fallback path explicitly passes AR_LIST_SCHEMA_ALL |
     * AR_HIDDEN_INCREMENT, matching lists/ARSchemaList.cpp's own ARGetListSchema call exactly
     * - the plain no-arg getListForm() already returned every
     * form including hidden ones on the test server (measured: identical form count either
     * way), but that's that server's own default behavior, not a documented guarantee of the no-arg
     * overload across every server/config, so the explicit flag is the robust fix rather than
     * relying on an unverified default.
     */
    public List<String> listFormNames() throws ARException {
        List<String> names;
        if (cache != null && cache.hasForms()) {
            names = new ArrayList<>(cache.formNames());
        } else {
            int allWithHidden = com.bmc.arsys.api.Constants.AR_LIST_SCHEMA_ALL | com.bmc.arsys.api.Constants.AR_HIDDEN_INCREMENT;
            names = client.raw().getListForm(0L, allWithHidden);
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.removeIf(blackList::containsSchema);
        // Innovation Studio record definitions are not classic AR forms - they are documented under
        // Innovation Studio -> Record Definitions instead (see SchemaBulkCache.AR_SMOPROP_RX_RECORD_TYPE).
        sorted.removeAll(recordDefinitionFormNames());
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    /**
     * Names of forms that are actually Innovation Studio record definitions (excluded from
     * {@link #listFormNames()}). Empty when there is no bulk cache (per-object fallback path, and
     * file mode) - the marker property is only inspected on the bulk-loaded {@code Form} objects.
     */
    public java.util.Set<String> recordDefinitionFormNames() {
        return cache != null && cache.hasForms() ? cache.recordDefinitionFormNames() : java.util.Set.of();
    }

    public Form getForm(String name) throws ARException {
        if (cache != null) {
            Form f = cache.form(name);
            if (f != null) return f;
        }
        return client.raw().getForm(name);
    }

    public List<Field> getFields(String formName) throws ARException {
        return client.raw().getListFieldObjects(formName);
    }

    /** Matches CARSchema::GetVUIs()->GetCount() - "Views" column on the schema overview / list page. */
    public int getViewCount(String formName) throws ARException {
        return client.raw().getListView(formName, 0L).size();
    }

    /**
     * Full VUI objects for the per-VUI detail page (Naming.schemaVuiDetail) - the Java API
     * identifies VUIs by numeric VUIId, not a name (see Naming's javadoc). A first pass at
     * getListViewObjects(formName, 0L, new ViewCriteria()) looked broken (returned an empty list
     * even when getListView(formName, 0L) returned real VUI IDs for the same form) - same root
     * cause as SchemaBulkCache/WorkflowBulkCache's forms/escalations story: ViewCriteria inherits
     * setRetrieveAll(boolean) from CriteriaFlags but doesn't redeclare it, so a bare `new
     * ViewCriteria()` (retrieveAll unset) returns nothing while `criteria.setRetrieveAll(true)`
     * returns every real VUI with fields matching a direct getView() fetch exactly (confirmed via
     * spike). One bulk call per form instead of one getView() call per VUI.
     */
    public List<View> getViews(String formName) throws ARException {
        long t0 = arinside.util.Timing.start();
        com.bmc.arsys.api.ViewCriteria criteria = new com.bmc.arsys.api.ViewCriteria();
        criteria.setRetrieveAll(true);
        List<View> views = client.raw().getListViewObjects(formName, 0L, criteria);
        arinside.util.Timing.addVuiFetch(t0);
        return views;
    }
}
