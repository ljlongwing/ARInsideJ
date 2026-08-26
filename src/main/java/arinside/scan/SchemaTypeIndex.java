package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ArchiveInfo;
import com.bmc.arsys.api.AuditInfo;
import com.bmc.arsys.api.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of CARSchema::GetInternalSchemaType's Audit(100)/Archive(101) synthetic type override:
 * - IsAuditTarget(): true if some OTHER form's enabled AuditInfo.auditForm names this form - a
 *   genuine cross-form back-reference (scan/ScanSchema.cpp builds it as a REFM_SCHEMA_AUDIT_SOURCE
 *   reference during its own pass), so this needs the full-form scan below.
 * - IsArchiveTarget(): true if THIS form's own ArchiveInfo has archiveType==NONE and a non-empty
 *   archiveFrom - archiveFrom is a self-contained marker the AR server writes onto the destination
 *   form itself when another form's "Archive To Form" is configured, needing no cross-form lookup
 *   at all (a genuinely different mechanism than audit's, easy to conflate since both look like
 *   "who points at me" - confirmed by reading the C++ source after an initial guessed
 *   archiveType==FORM/archiveDest-collecting version produced Type=Regular for a real archive
 *   target instead of Type=Archive).
 */
public final class SchemaTypeIndex {
    private final Set<String> auditTargets = ConcurrentHashMap.newKeySet();
    private final Set<String> archiveTargets = ConcurrentHashMap.newKeySet();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static SchemaTypeIndex build(SchemaSource repo) throws ARException {
        return build(repo, null, null);
    }

    public static SchemaTypeIndex build(SchemaSource repo, ReadPool reads, Function<ArClient, SchemaSource> repoFactory) throws ARException {
        SchemaTypeIndex idx = new SchemaTypeIndex();

        List<String> realFormNames = repo.listFormNames();

        if (reads == null) {
            for (String name : realFormNames) {
                try {
                    idx.indexForm(repo.getForm(name));
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema type for '" + name + "': " + e.getMessage());
                }
            }
            idx.auditTargets.retainAll(realFormNames);
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : realFormNames) {
            tasks.add(reads.<Void>submit(c -> { idx.indexForm(repoFactory.apply(c).getForm(name)); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema type for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        // Java port of ScanSchema.cpp's "if (auditForm.Exists())" guard before recording the
        // REFM_SCHEMA_AUDIT_SOURCE reference - a stale/misconfigured audit.getAuditForm() pointing
        // at a form that doesn't exist shouldn't count as a real audit-target classification. Post-
        // filtered here (rather than an extra existence lookup per audited form) since the full set
        // of real form names is already available for free. Functionally near-inert in practice
        // (internalSchemaType() is only ever queried with real form names anyway, so a phantom
        // non-existent entry never matches one) - added for exact source fidelity, not a fix for an
        // observed wrong result.
        idx.auditTargets.retainAll(realFormNames);
        return idx;
    }

    private void indexForm(Form f) {
        AuditInfo audit = f.getAuditInfo();
        if (audit != null && audit.isEnable() && audit.getAuditForm() != null && !audit.getAuditForm().isEmpty()) {
            auditTargets.add(audit.getAuditForm());
        }
        // Self-check, not a cross-form scan - see class javadoc. No isEnable() gate in the real
        // C++ check either: archiveType==NONE is itself part of how a genuine archive-destination
        // form is distinguished from one with its own independent archiving configured.
        ArchiveInfo archive = f.getArchiveInfo();
        if (archive != null && archive.getArchiveType() == ArchiveInfo.AR_ARCHIVE_NONE
            && archive.getArchiveFrom() != null && !archive.getArchiveFrom().isEmpty()) {
            archiveTargets.add(f.getName());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    /** 100/101 are the synthetic Audit/Archive override - not real AR_SCHEMA_* values. Returns the form's own real type unchanged if it's neither target. */
    public int internalSchemaType(String formName, int realFormType) {
        if (auditTargets.contains(formName)) return 100;
        if (archiveTargets.contains(formName)) return 101;
        return realFormType;
    }
}
