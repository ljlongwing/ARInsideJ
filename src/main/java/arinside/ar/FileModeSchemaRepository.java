package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.Form;
import com.bmc.arsys.api.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SchemaSource backed by an AR System Administrator XML export file instead of a live listing -
 * see FileModeSpike's findings, summarized in ArClient's javadoc, for why this still needs a live
 * connected ArClient (unlike the C++'s fully offline ARParseXMLDocument, this jar's *FromDef calls
 * are real server RPCs that just happen to source their data from the local file instead of the
 * server's own object store).
 *
 * <p><b>Superseded for real {@code .def} files</b>: {@code arinside.ar.deffile.DefFileParser} now
 * parses the real {@code .def} export format genuinely offline (no live server dependency at all), and {@code FileFormatSniffer.isDefFormat}
 * routes any real {@code .def} file to that connectionless path before this class is ever reached.
 * This class remains reachable only if a live connection happens to be available anyway (matching
 * how live-mode server access is used elsewhere as a development-time verification oracle) - it is
 * not part of the shipped fully-offline file-mode feature for {@code .def} files any more, kept as
 * a working reference implementation rather than deleted.
 *
 * <p><b>Known limitation for overlaid forms</b>: confirmed via spike that {@code
 * getListFormsFromDef} does NOT cleanly map one map-key to one canonical Form the way the other
 * *FromDef calls appear to - for an overlaid form (e.g. "User" on the test server), BOTH the
 * "User" and "User__o" map keys came back with overlapping, redundantly-duplicated Form entries
 * mixing base-layer (overlayType=1) and active-layer (overlayType=2) objects, and one entry's own
 * {@code getName()} was literally "User__o" (not "User") despite being the ACTIVE layer, not the
 * base layer - the opposite of what the "__o" suffix means everywhere else in this port. Chasing
 * this further to also reconstruct file-mode base-layer pages (mirroring
 * Main.documentOverlayBaseLayers' live-mode two-pass mechanism) was judged not worth the effort
 * for how narrow this edge case is (file mode AND an overlaid object in the export). Deliberate,
 * documented scope cut: forms are deduped by their own real name with any literal "__o" ambiguity
 * stripped, always preferring the active-layer (non-base) variant when both exist - only the
 * normal, non-suffixed page is produced for such forms in file mode, the base-layer "__o" page is
 * simply not generated.
 *
 * <p><b>Fully pre-loaded, not lazy</b>: forms, fields, and VUIs for every form are all fetched
 * once in the constructor (three {@code *FromDef} calls per form) rather than fields/VUIs being
 * fetched lazily per {@link #getFields}/{@link #getViews} call as an earlier version of this class
 * did. This is what lets file mode safely use the parallel write pool - the earlier lazy version's
 * per-form live calls all shared the one {@code ArClient} connection built in file mode's
 * constructor, which would race under concurrent access, exactly the failure mode the read/write
 * pool split exists to avoid elsewhere. A def file is expected to hold a small, hand-picked object
 * set (the whole point of file mode), so pre-loading everything up front is cheap.
 */
public final class FileModeSchemaRepository implements SchemaSource {
    private final Map<String, Form> formsByName = new HashMap<>();
    private final Map<String, List<Field>> fieldsByForm = new HashMap<>();
    private final Map<String, List<View>> viewsByForm = new HashMap<>();

    public FileModeSchemaRepository(ArClient client, String defFile) {
        try {
            Map<String, List<Form>> raw = client.raw().getListFormsFromDef(defFile, null, 0, false);
            if (raw != null) {
                for (List<Form> list : raw.values()) {
                    for (Form f : list) {
                        if (f == null || f.getName() == null) continue;
                        String cleanName = f.getName().endsWith("__o")
                            ? f.getName().substring(0, f.getName().length() - "__o".length())
                            : f.getName();
                        Form existing = formsByName.get(cleanName);
                        boolean fIsBase = OverlaySupport.overlayType(f.getProperties()) == com.bmc.arsys.api.Constants.AR_OVERLAID_OBJECT;
                        // First entry wins if nothing's there yet; a non-base entry always wins over
                        // whatever's already there (promotes past an earlier base-layer duplicate);
                        // a base-layer entry never overwrites an already-present non-base one.
                        if (existing == null || !fIsBase) formsByName.put(cleanName, f);
                    }
                }
            }

            for (String formName : formsByName.keySet()) {
                fieldsByForm.put(formName, fetchFields(client, defFile, formName));
                viewsByForm.put(formName, fetchViews(client, defFile, formName));
            }
        } catch (ARException | IOException e) {
            throw new RuntimeException("Failed reading forms from def file '" + defFile + "': " + e.getMessage(), e);
        }
    }

    private static List<Field> fetchFields(ArClient client, String defFile, String formName) throws ARException, IOException {
        Map<Integer, List<Field>> raw = client.raw().getListFieldsFromDef(defFile, formName, 0, false);
        List<Field> fields = new ArrayList<>();
        if (raw != null) {
            for (List<Field> list : raw.values()) {
                if (!list.isEmpty() && list.get(0) != null) fields.add(list.get(0));
            }
        }
        return fields;
    }

    private static List<View> fetchViews(ArClient client, String defFile, String formName) throws ARException, IOException {
        Map<Integer, List<View>> raw = client.raw().getListViewsFromDef(defFile, formName, 0, false);
        List<View> views = new ArrayList<>();
        if (raw != null) {
            for (List<View> list : raw.values()) {
                if (!list.isEmpty() && list.get(0) != null) views.add(list.get(0));
            }
        }
        return views;
    }

    @Override
    public List<String> listFormNames() {
        List<String> names = new ArrayList<>(formsByName.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Form getForm(String name) {
        return formsByName.get(name);
    }

    @Override
    public List<Field> getFields(String formName) {
        return fieldsByForm.getOrDefault(formName, List.of());
    }

    @Override
    public int getViewCount(String formName) {
        return getViews(formName).size();
    }

    @Override
    public List<View> getViews(String formName) {
        return viewsByForm.getOrDefault(formName, List.of());
    }
}
