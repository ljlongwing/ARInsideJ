package arinside.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of DocValidator.cpp's FieldReferenceValidator - "workflow references a field ID that
 * doesn't exist on the target form". Populated as a side effect of QualificationRenderer's
 * FieldReferenceSink (fieldExists=false case) during the same AL/filter/escalation "Run If"
 * rendering pass that populates FieldReferenceIndex - no extra fetch pass of its own.
 *
 * Thread-safe: {@link #add} runs concurrently once AL/filter/escalation rendering is on the
 * parallel write pool - backed by a synchronized list (writes far outnumber the single read at
 * the very end when ValidatorPage renders, so a synchronized ArrayList beats a fully concurrent
 * structure here).
 */
public final class MissingFieldReferenceIndex {

    public record Entry(String formName, int fieldId, FieldReferenceIndex.Ref owner) {}

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

    public void add(String formName, int fieldId, FieldReferenceIndex.Ref owner) {
        entries.add(new Entry(formName, fieldId, owner));
    }

    public List<Entry> entries() {
        return entries;
    }
}
