package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.JoinForm;
import com.bmc.arsys.api.Menu;
import com.bmc.arsys.api.QueryMenu;
import com.bmc.arsys.api.SetFieldsAction;
import com.bmc.arsys.api.SetFieldsFromForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the CARSchemaList reverse-reference list (CARSchema::AddReference/GetReferences())
 * feeding DocSchemaDetails.cpp's References tab. Populated from two different phases, matching the
 * real C++ exactly:
 * <ul>
 *   <li>A scan/-phase pass, built once here up front (matching the established "amortize the full
 *       scan" pattern used throughout this port): REFM_SETFIELDS_FORM (scan/ScanActiveLinks.cpp
 *       etc. - AL/Filter/Escalation Set Fields reading from another form, ported as
 *       {@link #setFieldsReaders}), REFM_CHARMENU_FORM (scan/ScanMenus.cpp - a Query-type menu
 *       whose schema targets another form, ported as {@link #queryMenus}), and the Join Form scan
 *       (ported as {@link #joinReferences}). REFM_SCHEMA_AUDIT_SOURCE (scan/ScanSchema.cpp) is
 *       populated in the real tool but never actually read by GenerateReferencesTable(), so not
 *       ported - a genuine orphaned reverse-index entry in the original, not a missing feature.</li>
 *   <li>A doc/-phase side effect, mutated live as AL/Filter/Escalation pages render (matching how
 *       {@link FieldReferenceIndex} is populated the same way, for the same reason: Schema pages
 *       render last - see Main.java - specifically so these are complete by the time they're read).
 *       Four reference sub-tables are populated this way: "writing data to this form"
 *       (REFM_PUSHFIELD_TARGET), "deleting data" (REFM_DELETE_ENTRY_ACTION), "executing services"
 *       (REFM_SERVICE_CALL, including the "$-5$"/current-schema-keyword fallback branch), and
 *       "open window" (REFM_OPENWINDOW_FORM, Active-Link-only - OpenWindowAction isn't available to
 *       Filter/Escalation action lists). All four ported here as {@link #pushFieldTargets}/
 *       {@link #deleteEntryCallers}/{@link #serviceCallers}/{@link #openWindowTargets}, populated
 *       via {@link SchemaReferenceSink} (see QualificationRenderer).</li>
 * </ul>
 */
public final class SchemaReferenceIndex {
    /** order: the referencing Active Link/Filter's own Order (null for every other typeLabel, e.g. "Escalation"/"Primary Form in") - see WorkflowRefLabel's javadoc for what this feeds. */
    public record Caller(String objectName, String typeLabel, boolean enabled, Integer order) {
        public Caller(String objectName, String typeLabel, boolean enabled) {
            this(objectName, typeLabel, enabled, null);
        }
    }

    private final Map<String, List<Caller>> setFieldsReaders = new ConcurrentHashMap<>();
    private final Map<String, List<String>> queryMenus = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> joinReferences = new ConcurrentHashMap<>();
    private final Map<String, List<String>> auditSources = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> pushFieldTargets = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> deleteEntryCallers = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> serviceCallers = new ConcurrentHashMap<>();
    private final Map<String, List<Caller>> openWindowTargets = new ConcurrentHashMap<>();

    public static SchemaReferenceIndex build(WorkflowSource repo, SchemaSource schemas) throws ARException {
        return build(repo, schemas, null, null);
    }

    public static SchemaReferenceIndex build(WorkflowSource repo, SchemaSource schemas, ReadPool reads, Function<ArClient, WorkflowSource> repoFactory) throws ARException {
        SchemaReferenceIndex idx = new SchemaReferenceIndex();

        // Join Form References: DocSchemaDetails.cpp's JoinFormReferences() is a live O(schemaCount)
        // scan at render time in the original tool - built once here instead, amortizing the full
        // scan across every form. Cheap regardless of ReadPool availability:
        // getForm() is backed by SchemaBulkCache when one was built (see Main.java), so this is an
        // in-memory-only pass, not a fresh live fetch per form.
        //
        // Audit Source References (REFM_SCHEMA_AUDIT_SOURCE, scan/ScanSchema.cpp's
        // ScanAuditReference) are piggybacked on this same pass: every form whose own audit settings
        // (style != NONE, a real target form name) name ANOTHER form as their audit destination adds
        // a reverse reference onto that destination form, read back by DocSchemaDetails.cpp's
        // AuditTargetReferences() for the LOG_SHADOW style's "Audited From Forms" row - see
        // SchemaDetailPage.auditInfo's javadoc.
        Set<String> allFormNames = new HashSet<>(schemas.listFormNames());
        for (String formName : allFormNames) {
            try {
                Form form = schemas.getForm(formName);
                if (form instanceof JoinForm jf) {
                    if (jf.getMemberA() != null) idx.joinReferences.computeIfAbsent(jf.getMemberA(), k -> Collections.synchronizedList(new ArrayList<>())).add(new Caller(formName, "Primary Form in", true));
                    if (jf.getMemberB() != null) idx.joinReferences.computeIfAbsent(jf.getMemberB(), k -> Collections.synchronizedList(new ArrayList<>())).add(new Caller(formName, "Secondary Form in", true));
                }
                com.bmc.arsys.api.AuditInfo audit = form.getAuditInfo();
                if (audit != null && audit.getAuditStyle() != com.bmc.arsys.api.Constants.AR_AUDIT_NONE
                    && audit.getAuditForm() != null && !audit.getAuditForm().isEmpty() && allFormNames.contains(audit.getAuditForm())) {
                    idx.auditSources.computeIfAbsent(audit.getAuditForm(), k -> Collections.synchronizedList(new ArrayList<>())).add(formName);
                }
            } catch (ARException e) {
                logEx("schema (join scan)", formName, e);
            }
        }

        if (reads == null) {
            for (String name : repo.listActiveLinkNames()) {
                try { idx.indexAl(repo.getActiveLink(name), name); } catch (ARException e) { logEx("AL", name, e); }
            }
            for (String name : repo.listFilterNames()) {
                try { idx.indexFilter(repo.getFilter(name), name); } catch (ARException e) { logEx("filter", name, e); }
            }
            for (String name : repo.listEscalationNames()) {
                try { idx.indexEscalation(repo.getEscalation(name), name); } catch (ARException e) { logEx("escalation", name, e); }
            }
            for (String name : repo.listMenuNames()) {
                try { idx.indexMenu(repo.getMenu(name), name); } catch (ARException e) { logEx("menu", name, e); }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : repo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexAl(repoFactory.apply(c).getActiveLink(name), name); return null; })
                .exceptionally(ex -> { logExRoot("AL", name, ex); return null; }));
        }
        for (String name : repo.listFilterNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexFilter(repoFactory.apply(c).getFilter(name), name); return null; })
                .exceptionally(ex -> { logExRoot("filter", name, ex); return null; }));
        }
        for (String name : repo.listEscalationNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexEscalation(repoFactory.apply(c).getEscalation(name), name); return null; })
                .exceptionally(ex -> { logExRoot("escalation", name, ex); return null; }));
        }
        for (String name : repo.listMenuNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexMenu(repoFactory.apply(c).getMenu(name), name); return null; })
                .exceptionally(ex -> { logExRoot("menu", name, ex); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexAl(ActiveLink al, String name) {
        indexSetFields(al.getActionList(), al.getFormList(), name, "Active Link", al.isEnable(), al.getOrder());
        indexSetFields(al.getElseList(), al.getFormList(), name, "Active Link", al.isEnable(), al.getOrder());
    }

    private void indexFilter(Filter flt, String name) {
        indexSetFields(flt.getActionList(), flt.getFormList(), name, "Filter", flt.isEnable(), flt.getOrder());
        indexSetFields(flt.getElseList(), flt.getFormList(), name, "Filter", flt.isEnable(), flt.getOrder());
    }

    private void indexEscalation(Escalation esc, String name) {
        indexSetFields(esc.getActionList(), esc.getFormList(), name, "Escalation", esc.isEnable(), null);
        indexSetFields(esc.getElseList(), esc.getFormList(), name, "Escalation", esc.isEnable(), null);
    }

    /** Matches ScanActiveLinks.cpp/ScanFilters.cpp/ScanEscalations.cpp's ScanActions - only SFT_SERVER/SFT_SAMPLEDATA (a real "query another form" Set Fields) counts, and only against the object's own first attached form (matching CARSetFieldHelper's own-form context - the C++ "simply uses the first schema here"). */
    private <A> void indexSetFields(List<A> actions, List<String> formList, String objectName, String typeLabel, boolean enabled, Integer order) {
        if (actions == null || formList == null || formList.isEmpty()) return;
        String ownForm = formList.get(0);
        for (A action : actions) {
            if (!(action instanceof SetFieldsFromForm sf)) continue;
            String readForm = resolveFormRef(sf.getReadValuesFrom(), sf.getSampleForm(), ownForm);
            if (readForm == null || readForm.isEmpty() || readForm.equals(ownForm)) continue;
            setFieldsReaders.computeIfAbsent(readForm, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Caller(objectName, typeLabel, enabled, order));
        }
    }

    /** Matches ScanMenus.cpp's Scan() - a Query-type menu whose schema is a genuine cross-form reference (not "@" - AR System's "current schema" placeholder). */
    private void indexMenu(Menu menu, String name) {
        if (!(menu instanceof QueryMenu qm)) return;
        String schema = qm.getForm();
        if (schema != null && schema.startsWith("$")) schema = qm.getSampleForm();
        if (schema == null || schema.isEmpty() || schema.equals("@")) return;
        queryMenus.computeIfAbsent(schema, k -> Collections.synchronizedList(new ArrayList<>())).add(name);
    }

    /** Same "@"/"$fieldId" resolution as ActionSummaryTable's resolveFormRef, duplicated here since this index has no QualificationRenderer/field-lookup context to resolve a sample-field-driven form name - only the literal "@" (own form) and plain non-"$" names are resolvable without one. */
    private static String resolveFormRef(String raw, String sampleForm, String ownForm) {
        if (raw != null && raw.equals("@")) return ownForm;
        if (raw == null || raw.isEmpty() || raw.startsWith("$")) {
            return sampleForm == null || sampleForm.isEmpty() || sampleForm.equals("@") ? ownForm : sampleForm;
        }
        return raw;
    }

    private static void logEx(String type, String name, Exception e) {
        System.out.println("EXCEPTION indexing schema references for " + type + " '" + name + "': " + e.getMessage());
    }

    private static void logExRoot(String type, String name, Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        System.out.println("EXCEPTION indexing schema references for " + type + " '" + name + "': " + cause.getMessage());
    }

    /** Every AL/Filter/Escalation whose Set Fields action reads data FROM this form. */
    public List<Caller> setFieldsReaders(String formName) { return setFieldsReaders.getOrDefault(formName, List.of()); }

    /** Every form that names this form as its own audit destination - matches DocSchemaDetails.cpp's AuditTargetReferences(), rendered on the LOG_SHADOW-style form's own "Audited From Forms" row. */
    public List<String> auditSources(String formName) { return auditSources.getOrDefault(formName, List.of()); }

    /** Every Query-type menu whose data source is this form. */
    public List<String> queryMenus(String formName) { return queryMenus.getOrDefault(formName, List.of()); }

    /** Every Join-type form that names this form as its memberA/memberB - matches DocSchemaDetails.cpp's JoinFormReferences(). */
    public List<Caller> joinReferences(String formName) { return joinReferences.getOrDefault(formName, List.of()); }

    /** Mutated live as AL/Filter/Escalation pages render - see class javadoc's second bullet. Safe to call concurrently (write-pool rendering runs these pages in parallel). */
    public void addPushFieldTarget(String formName, Caller caller) {
        if (formName == null || formName.isEmpty()) return;
        pushFieldTargets.computeIfAbsent(formName, k -> Collections.synchronizedList(new ArrayList<>())).add(caller);
    }

    public void addDeleteEntryCaller(String formName, Caller caller) {
        if (formName == null || formName.isEmpty()) return;
        deleteEntryCallers.computeIfAbsent(formName, k -> Collections.synchronizedList(new ArrayList<>())).add(caller);
    }

    public void addServiceCaller(String formName, Caller caller) {
        if (formName == null || formName.isEmpty()) return;
        serviceCallers.computeIfAbsent(formName, k -> Collections.synchronizedList(new ArrayList<>())).add(caller);
    }

    public void addOpenWindowTarget(String formName, Caller caller) {
        if (formName == null || formName.isEmpty()) return;
        openWindowTargets.computeIfAbsent(formName, k -> Collections.synchronizedList(new ArrayList<>())).add(caller);
    }

    /** Every AL/Filter Push Fields action that writes data TO this form. */
    public List<Caller> pushFieldTargets(String formName) { return pushFieldTargets.getOrDefault(formName, List.of()); }

    /** Every AL/Filter/Escalation whose free text (Message/Process/SQL/etc.) contains an "Application-Delete-Entry"/"Application-Query-Delete-Entry" command targeting this form. */
    public List<Caller> deleteEntryCallers(String formName) { return deleteEntryCallers.getOrDefault(formName, List.of()); }

    /** Every AL/Filter Service action that calls a service on this form. */
    public List<Caller> serviceCallers(String formName) { return serviceCallers.getOrDefault(formName, List.of()); }

    /** Every Active Link OpenWindowAction whose target form is this form - matches DocSchemaDetails.cpp's AlWindowOpenReferences(). */
    public List<Caller> openWindowTargets(String formName) { return openWindowTargets.getOrDefault(formName, List.of()); }
}
