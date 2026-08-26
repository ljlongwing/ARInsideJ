package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.FormCriteria;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the real C++'s default "fast" object loading (lists/ARSchemaList.cpp's
 * LoadFromServer(), which calls ARGetMultipleSchemas) - one bulk getListFormObjects() RPC instead of
 * this port's original one-getForm(name)-call-per-object pattern. Mirrors {@link WorkflowBulkCache}'s
 * shape: {@code FormCriteria} inherits {@code setRetrieveAll(boolean)} from its {@code
 * CriteriaFlags} grandparent (via {@code ObjectBaseCriteria}) without redeclaring it, so {@code
 * criteria.setRetrieveAll(true)} must be called explicitly - a bare {@code new FormCriteria()} (the
 * default, retrieveAll unset) returns zero forms from {@code getListFormObjects()}.
 *
 * AR_LIST_SCHEMA_ALL | AR_HIDDEN_INCREMENT matches lists/ARSchemaList.cpp's own ARGetListSchema
 * flags exactly - hidden forms are included by default in the real C++, so this bulk fetch must
 * include them too.
 */
public final class SchemaBulkCache {
    private final Map<String, Form> forms;

    private SchemaBulkCache(Map<String, Form> forms) {
        this.forms = forms;
    }

    public static SchemaBulkCache load(ArClient client) {
        return new SchemaBulkCache(loadForms(client));
    }

    private static Map<String, Form> loadForms(ArClient client) {
        try {
            long t0 = System.currentTimeMillis();
            int allWithHidden = Constants.AR_LIST_SCHEMA_ALL | Constants.AR_HIDDEN_INCREMENT;
            FormCriteria criteria = new FormCriteria();
            criteria.setRetrieveAll(true);
            List<Form> all = client.raw().getListFormObjects(0L, allWithHidden, null, null, criteria);
            Map<String, Form> map = new HashMap<>(all.size() * 2);
            for (Form f : all) map.put(f.getName(), f);
            System.out.println("Bulk-loaded " + map.size() + " forms in " + (System.currentTimeMillis() - t0) + "ms");
            return map;
        } catch (ARException e) {
            System.out.println("WARN: bulk form load failed (" + e.getMessage() + ") - falling back to per-object loading");
            return null;
        }
    }

    boolean hasForms() { return forms != null; }
    Form form(String name) { return forms == null ? null : forms.get(name); }
    Set<String> formNames() { return forms.keySet(); }
}
