package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.FormCriteria;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the real C++'s default "fast" object loading (lists/ARSchemaList.cpp's
 * LoadFromServer(), which calls ARGetMultipleSchemas) - one bulk getListFormObjects() RPC instead of
 * this port's original one-getForm(name)-call-per-object pattern. Mirrors {@link WorkflowBulkCache}'s
 * shape and the same lesson learned there: a first pass at this call (0L, AR_LIST_SCHEMA_ALL |
 * AR_HIDDEN_INCREMENT, null, null, `new FormCriteria()`) silently returned 0 forms against the live
 * test server despite 4,684 real forms existing, and was wrongly written off as a jar quirk - the
 * real cause was that `FormCriteria` inherits `setRetrieveAll(boolean)` from its `CriteriaFlags`
 * grandparent (via ObjectBaseCriteria) but doesn't redeclare it, so `javap` on `FormCriteria` alone
 * never surfaced it. With `criteria.setRetrieveAll(true)` explicitly called, the exact same call
 * returns all 4,684 forms in ~525ms with fields (helpText/properties/owner) matching a direct
 * getForm() fetch exactly - confirmed via spike.
 *
 * AR_LIST_SCHEMA_ALL | AR_HIDDEN_INCREMENT matches lists/ARSchemaList.cpp's own ARGetListSchema
 * flags exactly (confirmed by reading it) - hidden forms are included by default in the real C++, so
 * this bulk fetch must include them too.
 */
public final class SchemaBulkCache {
    /**
     * Object property that marks a form as an Innovation Studio record definition - its value is
     * the rx record-type class (e.g. {@code com.bmc.arsys.rx.services.record.domain.DefaultRecordType}).
     * Not a named constant in the arapi build this targets: the {@code AR_SMOPROP_*} series stops
     * at 90019. Forms carrying it are authored in Innovation Studio, not classic Dev Studio; Dev
     * Studio itself hides them from its Forms list. ARInsideJ documents them under Innovation
     * Studio -> Record Definitions instead of Forms (see {@code arinside.doc.is.IsPages}).
     */
    private static final int AR_SMOPROP_RX_RECORD_TYPE = 90025;

    private final Map<String, Form> forms;
    private final Set<String> recordDefinitionForms;

    private SchemaBulkCache(Map<String, Form> forms) {
        this.forms = forms;
        this.recordDefinitionForms = findRecordDefinitionForms(forms);
    }

    public static SchemaBulkCache load(ArClient client) {
        return new SchemaBulkCache(loadForms(client));
    }

    private static Set<String> findRecordDefinitionForms(Map<String, Form> forms) {
        Set<String> out = new LinkedHashSet<>();
        if (forms == null) return out;
        for (Form f : forms.values()) {
            if (f.getProperties() != null && f.getProperties().containsKey(AR_SMOPROP_RX_RECORD_TYPE)) {
                out.add(f.getName());
            }
        }
        return out;
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

    /**
     * Forms that are really Innovation Studio record definitions (property 90025). Still present in
     * {@link #form(String)} / {@link #formNames()}; {@link SchemaRepository#listFormNames()} filters
     * them out of the classic Forms documentation, and Main hands the list to the IS pass.
     */
    public Set<String> recordDefinitionFormNames() {
        return forms == null ? Set.of() : java.util.Collections.unmodifiableSet(recordDefinitionForms);
    }
}
