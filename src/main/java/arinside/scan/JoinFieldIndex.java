package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.JoinFieldMapping;
import com.bmc.arsys.api.JoinForm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the reverse-lookup half of doc/DocFieldDetails.cpp's JoinFormReferences - "which
 * join forms pull this field in as their primary/secondary member field". The C++ rescans every
 * join form's every field, from scratch, once per field being documented (its own DocFieldDetails.cpp
 * comment flags this as a known TODO: "each field scans for current schemas join-form references
 * again... maybe its possible to move this scanning to the scan-phase") - on a server with
 * thousands of forms that per-field rescan would be quadratic, so this builds the same information
 * as one reverse index instead, in a single pass over every join form's fields, matching the
 * established pattern of every other scan/ index in this port (GlobalFieldIndex etc.).
 */
public final class JoinFieldIndex {

    public record Ref(String joinFormName, boolean isMemberA) {}

    private final Map<String, Map<Integer, List<Ref>>> byFormAndField = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static JoinFieldIndex build(SchemaSource repo) throws ARException {
        return build(repo, null, null);
    }

    public static JoinFieldIndex build(SchemaSource repo, ReadPool reads, Function<ArClient, SchemaSource> repoFactory) throws ARException {
        JoinFieldIndex idx = new JoinFieldIndex();

        if (reads == null) {
            for (String name : repo.listFormNames()) {
                try {
                    idx.indexForm(repo, name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing join fields for '" + name + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : repo.listFormNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexForm(repoFactory.apply(c), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing join fields for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexForm(SchemaSource repo, String joinFormName) throws ARException {
        Form form = repo.getForm(joinFormName);
        if (!(form instanceof JoinForm join)) return;

        for (Field field : repo.getFields(joinFormName)) {
            if (!(field.getFieldMap() instanceof JoinFieldMapping jm)) continue;
            boolean isMemberA = jm.getIndex() == 0;
            String targetForm = isMemberA ? join.getMemberA() : join.getMemberB();
            if (targetForm == null || targetForm.isEmpty()) continue;

            byFormAndField.computeIfAbsent(targetForm, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(jm.getFieldID(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Ref(joinFormName, isMemberA));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    public List<Ref> forField(String formName, int fieldId) {
        Map<Integer, List<Ref>> byField = byFormAndField.get(formName);
        if (byField == null) return List.of();
        return byField.getOrDefault(fieldId, List.of());
    }
}
