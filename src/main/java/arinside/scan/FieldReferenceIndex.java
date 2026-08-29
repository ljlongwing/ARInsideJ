package arinside.scan;

import arinside.output.ImageTag;
import arinside.output.PagePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java port of the field-level half of CARInside::AddFieldReference (ARInside.cpp) - "which
 * workflow object references this specific field on this specific form", as opposed to
 * WorkflowReferenceIndex's form-level "executes on this form" tracking.
 *
 * Unlike WorkflowReferenceIndex/PermissionIndex/GlobalFieldIndex (each a dedicated full pass built
 * once up front), this index is a plain mutable accumulator populated as a side effect of
 * QualificationRenderer's field-reference callback while active link/filter/escalation "Run If"
 * qualifiers are rendered during their own normal detail-page pass - no extra fetch pass of its
 * own. Active links/filters/escalations are documented BEFORE schemas/fields in Main.java's
 * pipeline order specifically so this index is already complete by the time FieldDetailPage first
 * (and only) renders each field's "Referenced By" section - forms used to be documented first and
 * re-rendered a second time at the end of the pipeline once this index was complete, which roughly
 * doubled total file-write volume for no benefit; the reorder eliminated that second pass entirely.
 *
 * Thread-safe: {@link #add} is called concurrently once active link/filter/escalation rendering
 * runs on the parallel write pool (see ReadPool/WritePool) - ConcurrentHashMap for both map
 * levels (computeIfAbsent is atomic per key) plus a synchronized list for the innermost bucket
 * (computeIfAbsent alone doesn't protect concurrent .add() calls on the SAME already-created list).
 */
public final class FieldReferenceIndex {

    /**
     * order/enabled/executeOn: only populated for Control Field/Focus Field references (see
     * ActiveLinkDetailPage's sink) - the Java port of CRefItem::GetObjectOrder/GetObjectEnabled/
     * GetObjectExecuteOn (RefItem.cpp) used by DocFieldDetails.cpp's WorkflowAttached() table. Null
     * for every other reference kind, matching the C++'s own GetObjectOrder() default (-1) for
     * object types other than Active Link/Filter not being consulted outside that one table.
     */
    public record Ref(String name, String typeLabel, ImageTag.Id icon, PagePath link, String detail, Integer order, Boolean enabled, String executeOn) {
        public Ref(String name, String typeLabel, ImageTag.Id icon, PagePath link, String detail) {
            this(name, typeLabel, icon, link, detail, null, null, null);
        }
    }

    private final Map<String, Map<Integer, List<Ref>>> byFormAndField = new ConcurrentHashMap<>();

    public void add(String formName, int fieldId, Ref ref) {
        byFormAndField.computeIfAbsent(formName, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(fieldId, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(ref);
    }

    /**
     * Returns a snapshot copy, not the live bucket: {@link #add} runs concurrently on the write
     * pool while FieldDetailPage iterates this result for its "Referenced By" table. Other forms'
     * SchemaDetailPage renders add refs keyed by *referenced* form names (ResultList/Sort/Join/
     * Audit/Archive qualifications, menu targets), so a reader and a writer really can hit the same
     * {@code (form, fieldId)} bucket at once - iterating it live threw a rare
     * ConcurrentModificationException (seen once per full-server run, always on the ubiquitous core
     * field 179 "InstanceId"). Copying under the bucket's own monitor makes the read safe.
     */
    public List<Ref> forField(String formName, int fieldId) {
        Map<Integer, List<Ref>> byField = byFormAndField.get(formName);
        if (byField == null) return List.of();
        List<Ref> live = byField.get(fieldId);
        if (live == null) return List.of();
        synchronized (live) {
            return new ArrayList<>(live);
        }
    }
}
