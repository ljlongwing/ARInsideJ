package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ContainerSource;
import arinside.ar.OverlaySupport;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the group/role permission-membership scans scattered through DocGroupDetails.cpp /
 * DocRoleDetails.cpp (FormsDoc/FieldPermissionDoc/AlPermissionDoc/ContainerPermissionDoc) - both
 * the C++ classes redo these same nested "does this object's permission list contain my group/role
 * ID" loops separately per group and per role (O(groups+roles) full passes over every schema/field/
 * AL/container). This builds the same information as one index, keyed by group/role ID, in a
 * single pass instead - roles use the same index as groups since AR System stores role-based
 * permissions as negative group IDs in the exact same ARPermissionList/getAssignedGroup() entries
 * as regular groups (confirmed empirically - see RoleRecord's javadoc), so no separate role-side
 * scan is needed.
 *
 * Costs one more full fetch pass over every form+field+active link+container (on top of the passes
 * SchemaDetailPage/GlobalFieldIndex/WorkflowReferenceIndex/container doc pages already do) - same
 * known, accepted tradeoff as those two indexes (see their javadocs). The VUI/view portion of that
 * redundant fetch is now consolidated: this is the "Phase 7+ pass" the javadoc used to say might
 * happen - views are read from GlobalFieldIndex.views(formName) (built earlier, once, in Main.java)
 * instead of this class's own schemaRepo.getViews(formName) call, cutting three full-server VUI
 * fetch passes down to one. Field/form/AL/container fetches still happen independently here since
 * only the VUI fetch was actually triplicated across GlobalFieldIndex/PermissionIndex/SchemaDetailPage.
 */
public final class PermissionIndex {

    public record FormEntry(String name, int permissionValue) {}
    public record FieldEntry(String fieldName, int permissionValue) {}
    public record ContainerEntry(String name, int containerType) {}
    /** Java port of DocAnalyzer.cpp's IndexAnalyzer finding: an indexed character field with an inefficient QBE match mode or an overlong index key. */
    public record IndexFinding(String schemaName, String fieldName, String indexName, String message) {}
    public record FieldRef(String formName, String fieldName, int fieldId, int overlayType, Timestamp lastUpdateTime, String lastChangedBy) {}
    /** displayName is the resolved {@link arinside.ar.AREnumLabels#vuiDisplayName} string, captured at scan time while the real View object (with its DisplayProperties) is still in hand - see this class's build() for why. */
    public record ViewRef(String formName, int vuiId, String displayName, int overlayType, Timestamp lastUpdateTime, String lastChangedBy) {}
    /** Java port of DocCustomWorkflow.cpp's per-form overlay/custom row - see overlayOrCustomForms's javadoc. */
    public record FormRef(String name, int overlayType, Timestamp lastUpdateTime, String lastChangedBy) {}

    // All accumulator collections below are thread-safe: PermissionIndex.build() runs its
    // form/active-link/container passes on the parallel read pool in server mode (see the two
    // build() overloads) - ConcurrentHashMap for every Map level (computeIfAbsent is atomic per
    // key) plus a synchronized list for every bucket a concurrent .add() can land in. Buckets that
    // are only ever touched by the single task owning that key (e.g. fieldsByForm's innermost
    // per-form field list) are left as plain ArrayLists - no concurrent writers possible there.
    private final Map<Integer, List<FormEntry>> visibleForms = new ConcurrentHashMap<>();
    private final Map<Integer, List<String>> subadminForms = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, List<FieldEntry>>> fieldsByForm = new ConcurrentHashMap<>();
    private final Map<Integer, List<String>> activeLinks = new ConcurrentHashMap<>();
    private final Map<Integer, List<ContainerEntry>> containers = new ConcurrentHashMap<>();
    private final Map<Integer, List<String>> containerSubadmin = new ConcurrentHashMap<>(); // packing lists only

    // Java port of DocValidator.cpp's "objects with no or unknown access group" checks (FormGroupValidator/
    // FieldGroupValidator/AlGroupValidator/ContainerGroupValidator) - piggybacked on this index's existing
    // full pass over forms/fields/active links rather than re-fetching everything a second time for
    // ValidatorPage. Container type 2 (ARCON_APP) is added to the container pass below only for this check,
    // since group/role cross-reference pages never need application-type containers but the validator does.
    private final List<String> formsNoPermission = Collections.synchronizedList(new ArrayList<>());
    private final SortedMap<String, List<String>> fieldsNoPermissionByForm = Collections.synchronizedSortedMap(new TreeMap<>());
    private final List<String> activeLinksNoPermission = Collections.synchronizedList(new ArrayList<>());
    private final List<ContainerEntry> containersNoPermission = Collections.synchronizedList(new ArrayList<>());
    private final List<IndexFinding> indexFindings = Collections.synchronizedList(new ArrayList<>());
    /** Java port of DocCustomWorkflow.cpp's overlay/custom object listing, scoped to forms - see PermissionIndex.build()'s javadoc note. */
    private final List<FormRef> overlayOrCustomForms = Collections.synchronizedList(new ArrayList<>());
    /** Overlay-naming status per active link/container, populated as a side effect of this index's existing indexActiveLink/indexContainer passes - lets GroupDetailPage/RoleDetailPage/ValidatorPage link to the correct "__o"-suffixed page instead of guessing isOverlaid=false, same fix already applied to schema/field links via GlobalFieldIndex. Container key is containerType + ":" + name (containers aren't unique by name alone across types). */
    private final Map<String, Boolean> alOverlaidByName = new ConcurrentHashMap<>();
    private final Map<String, Boolean> containerOverlaidByKey = new ConcurrentHashMap<>();
    private int serverOverlayMode = 1;
    /** Same idea, extended to fields and VUIs (not part of the original DocCustomWorkflow.cpp scope this port ported from - added directly on top of this index's existing per-form field+VUI visit, no new fetch pass beyond the one extra getViews(formName) call per form this adds). */
    private final List<FieldRef> overlayOrCustomFields = Collections.synchronizedList(new ArrayList<>());
    private final List<ViewRef> overlayOrCustomViews = Collections.synchronizedList(new ArrayList<>());
    private final MissingMenuReferenceIndex missingMenuRefs = new MissingMenuReferenceIndex();
    /** Java port of the AppRefName-derived app-scoping check DocRoleDetails.cpp applies (FormsDoc/AlPermissionDoc/ContainerPermissionDoc) - "which application owns this active link/container", one hop beyond AppMembershipIndex's direct form/packing-list ownership. Forms don't need their own map here since AppMembershipIndex.formApp() already answers that directly. */
    private final Map<String, String> alApp = new ConcurrentHashMap<>();
    private final Map<String, String> containerApp = new ConcurrentHashMap<>();
    private AppMembershipIndex appIndex = null;
    private GlobalFieldIndex globalFields = null;

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static PermissionIndex build(SchemaSource schemaRepo, WorkflowSource workflowRepo, ContainerSource containerRepo, int serverOverlayMode, AppMembershipIndex appIndex, GlobalFieldIndex globalFields) throws ARException {
        return build(schemaRepo, workflowRepo, containerRepo, serverOverlayMode, appIndex, globalFields, null, null, null, null);
    }

    /**
     * Server mode: each form/active link/container's fetch+index-accumulate runs as one task on
     * the read pool - see WorkflowReferenceIndex.build's javadoc for why no separate write-pool
     * hop is needed here. Falls back to the plain sequential passes when reads is null.
     *
     * <p>appIndex is built once, upfront, by the caller (Main.java, right after containers are
     * available - see AppMembershipIndex's javadoc on why this must happen before AL/Filter/Menu/
     * Container are documented, matching the real C++'s own ContainerList(ARCON_APP)-before-
     * everything-else ordering in ARInside.cpp) and passed in here rather than built fresh each
     * time, avoiding a duplicate Application-container fetch pass.
     *
     * <p>globalFields is likewise built once, upfront, before this index (see Main.java) - its
     * already-fetched per-form view list is reused here instead of this class re-fetching views
     * itself (see class javadoc). May be null (e.g. isolated unit tests), in which case this index
     * falls back to its own schemaRepo.getViews(formName) call.
     */
    public static PermissionIndex build(SchemaSource schemaRepo, WorkflowSource workflowRepo, ContainerSource containerRepo, int serverOverlayMode, AppMembershipIndex appIndex, GlobalFieldIndex globalFields,
            ReadPool reads, Function<ArClient, SchemaSource> schemaFactory, Function<ArClient, WorkflowSource> workflowFactory, Function<ArClient, ContainerSource> containerFactory) throws ARException {
        PermissionIndex idx = new PermissionIndex();
        idx.serverOverlayMode = serverOverlayMode;
        Set<String> knownMenuNames = new HashSet<>(workflowRepo.listMenuNames());
        idx.appIndex = appIndex;
        idx.globalFields = globalFields;

        if (reads == null) {
            try {
                for (String formName : schemaRepo.listFormNames()) {
                    try {
                        idx.indexForm(schemaRepo, formName, knownMenuNames);
                    } catch (ARException e) {
                        System.out.println("EXCEPTION indexing permissions for form '" + formName + "': " + e.getMessage());
                    }
                }
            } catch (ARException e) {
                System.out.println("EXCEPTION listing forms for permission index: " + e.getMessage());
            }

            try {
                for (String alName : workflowRepo.listActiveLinkNames()) {
                    try {
                        idx.indexActiveLink(workflowRepo, alName, knownMenuNames);
                    } catch (ARException e) {
                        System.out.println("EXCEPTION indexing permissions for active link '" + alName + "': " + e.getMessage());
                    }
                }
            } catch (ARException e) {
                System.out.println("EXCEPTION listing active links for permission index: " + e.getMessage());
            }

            for (int containerType : CONTAINER_TYPES) {
                try {
                    for (String name : containerRepo.listContainerNames(containerType)) {
                        try {
                            idx.indexContainer(containerRepo, name, containerType, knownMenuNames);
                        } catch (ARException e) {
                            System.out.println("EXCEPTION indexing permissions for container '" + name + "': " + e.getMessage());
                        }
                    }
                } catch (ARException e) {
                    System.out.println("EXCEPTION listing containers (type " + containerType + ") for permission index: " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String formName : schemaRepo.listFormNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexForm(schemaFactory.apply(c), formName, knownMenuNames); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing permissions for form '" + formName + "': " + rootMessage(ex)); return null; }));
        }
        for (String alName : workflowRepo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexActiveLink(workflowFactory.apply(c), alName, knownMenuNames); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing permissions for active link '" + alName + "': " + rootMessage(ex)); return null; }));
        }
        // containerType: 1=ARCON_GUIDE, 2=ARCON_APP, 3=ARCON_PACK, 4=ARCON_FILTER_GUIDE, 5=ARCON_WEBSERVICE
        // DocGroupDetails/DocRoleDetails only ever build companion pages for PACK/GUIDE/WEBSERVICE; APP (2)
        // is fetched here only to feed the no-permission validator check above (ContainerGroupValidator
        // checks WEBSERVICE/GUIDE/APP, not PACK).
        for (int containerType : CONTAINER_TYPES) {
            for (String name : containerRepo.listContainerNames(containerType)) {
                int ct = containerType;
                tasks.add(reads.<Void>submit(c -> { idx.indexContainer(containerFactory.apply(c), name, ct, knownMenuNames); return null; })
                    .exceptionally(ex -> { System.out.println("EXCEPTION indexing permissions for container '" + name + "': " + rootMessage(ex)); return null; }));
            }
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private static final int[] CONTAINER_TYPES = {1, 2, 3, 5};

    private void indexForm(SchemaSource schemaRepo, String formName, Set<String> knownMenuNames) throws ARException {
        Form form = schemaRepo.getForm(formName);
        int overlayType = OverlaySupport.overlayType(form.getProperties());
        if (overlayType == Constants.AR_OVERLAY_OBJECT || overlayType == Constants.AR_CUSTOM_OBJECT) {
            overlayOrCustomForms.add(new FormRef(formName, overlayType, form.getLastUpdateTime(), form.getLastChangedBy()));
        }
        List<PermissionInfo> formPerms = nullSafe(form.getAssignedGroup());
        if (formPerms.isEmpty()) formsNoPermission.add(formName);
        for (PermissionInfo p : formPerms) {
            visibleForms.computeIfAbsent(p.getGroupID(), k -> Collections.synchronizedList(new ArrayList<>())).add(new FormEntry(formName, p.getPermissionValue()));
        }
        for (Integer grp : nullSafe(form.getAdminGrpList())) {
            subadminForms.computeIfAbsent(grp, k -> Collections.synchronizedList(new ArrayList<>())).add(formName);
        }

        // fieldId -> index names it participates in, for the IndexAnalyzer check below
        Map<Integer, List<String>> indexNamesByFieldId = new HashMap<>();
        for (IndexInfo ix : nullSafe(form.getIndexInfo())) {
            for (Integer fieldId : nullSafe(ix.getIndexFields())) {
                indexNamesByFieldId.computeIfAbsent(fieldId, k -> new ArrayList<>()).add(ix.getIndexName());
            }
        }

        List<View> views = globalFields != null ? globalFields.views(formName) : schemaRepo.getViews(formName);
        for (View view : views) {
            int viewOverlayType = OverlaySupport.overlayType(view.getObjectProperties());
            if (viewOverlayType == Constants.AR_OVERLAY_OBJECT || viewOverlayType == Constants.AR_CUSTOM_OBJECT) {
                overlayOrCustomViews.add(new ViewRef(formName, view.getVUIId(), arinside.ar.AREnumLabels.vuiDisplayName(view), viewOverlayType, view.getLastUpdateTime(), view.getLastChangedBy()));
            }
        }

        for (Field field : schemaRepo.getFields(formName)) {
            int fieldOverlayType = OverlaySupport.overlayType(field.getObjectProperty());
            if (fieldOverlayType == Constants.AR_OVERLAY_OBJECT || fieldOverlayType == Constants.AR_CUSTOM_OBJECT) {
                overlayOrCustomFields.add(new FieldRef(formName, field.getName(), field.getFieldID(), fieldOverlayType, field.getLastUpdateTime(), field.getLastChangedBy()));
            }

            List<PermissionInfo> fieldPerms = nullSafe(field.getAssignedGroup());
            if (fieldPerms.isEmpty()) {
                fieldsNoPermissionByForm.computeIfAbsent(formName, k -> Collections.synchronizedList(new ArrayList<>())).add(field.getName());
            }
            for (PermissionInfo p : fieldPerms) {
                fieldsByForm.computeIfAbsent(p.getGroupID(), k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(formName, k -> new ArrayList<>())
                    .add(new FieldEntry(field.getName(), p.getPermissionValue()));
            }

            if (field.getDataType() == Constants.AR_DATA_TYPE_CHAR
                && field.getFieldLimit() instanceof CharacterFieldLimit charLimit) {
                List<String> indexNames = indexNamesByFieldId.get(field.getFieldID());
                if (indexNames != null) {
                    for (String indexName : indexNames) {
                        if (charLimit.getQBEMatch() == Constants.AR_QBE_MATCH_ANYWHERE) {
                            indexFindings.add(new IndexFinding(formName, field.getName(), indexName,
                                "Inefficient index because of QBE match anywhere in Index " + indexName));
                        }
                        if (charLimit.getMaxLength() > 255) {
                            indexFindings.add(new IndexFinding(formName, field.getName(), indexName,
                                "Length of field is greater than 255 (Index " + indexName + ")"));
                        }
                    }
                }

                // Java port of DocValidator.cpp's MenuReferenceValidator - a character field's assigned menu doesn't match a real menu.
                String charMenu = charLimit.getCharMenu();
                if (charMenu != null && !charMenu.isEmpty() && !knownMenuNames.contains(charMenu)) {
                    missingMenuRefs.addField(charMenu, formName, field.getName(), field.getFieldID());
                }
            }
        }
    }

    private void indexActiveLink(WorkflowSource workflowRepo, String alName, Set<String> knownMenuNames) throws ARException {
        ActiveLink al = workflowRepo.getActiveLink(alName);
        alOverlaidByName.put(alName, OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode));
        List<Integer> grps = nullSafe(al.getGroupList());
        if (grps.isEmpty()) activeLinksNoPermission.add(alName);
        for (Integer grp : grps) {
            activeLinks.computeIfAbsent(grp, k -> Collections.synchronizedList(new ArrayList<>())).add(alName);
        }

        // Java port of DocValidator.cpp's MenuReferenceValidator's second real source (see
        // MissingMenuReferenceIndex's javadoc) - a Change Field action's assigned menu doesn't
        // match a real menu, same check as indexForm's field-side one above.
        indexActionsForMissingMenus(al.getActionList(), alName, "If", knownMenuNames);
        indexActionsForMissingMenus(al.getElseList(), alName, "Else", knownMenuNames);

        // Java port of DocApplicationDetails.cpp's SearchActiveLinks - an AL's app is the app of
        // any form it's attached to (first match; real data has every attached form under the same
        // app in practice, matching the C++'s own uniquify-then-take-list behavior).
        for (String formName : nullSafe(al.getFormList())) {
            String app = appIndex.formApp(formName);
            if (app != null) {
                alApp.put(alName, app);
                break;
            }
        }
    }

    private void indexActionsForMissingMenus(List<ActiveLinkAction> actions, String alName, String branch, Set<String> knownMenuNames) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            if (!(actions.get(i) instanceof ChangeFieldAction cf)) continue;
            String charMenu = cf.getCharMenu();
            if (charMenu != null && !charMenu.isEmpty() && !knownMenuNames.contains(charMenu)) {
                missingMenuRefs.addActiveLink(charMenu, alName, branch, i);
            }
        }
    }

    private void indexContainer(ContainerSource containerRepo, String name, int containerType, Set<String> knownMenuNames) throws ARException {
        Container c = containerRepo.getContainer(name);
        containerOverlaidByKey.put(containerType + ":" + name, OverlaySupport.isOverlaidForNaming(c.getProperties(), serverOverlayMode));

        // Java port of DocApplicationDetails.cpp's SearchContainer - PACK containers get their app
        // directly (they're a member of the app's own content, like forms); GUIDE/FILTER_GUIDE
        // containers derive it from their owner form's app (via ContainerOwner, the reverse of
        // schema.GetActLinkGuides()/GetFilterGuides()). APP/WEBSERVICE never get an app assigned -
        // confirmed via source that SearchContainer's switch has no case for either, a genuine gap
        // in the original tool (a role's Webservice Permission page is always empty for any role
        // with a real application name), not something to "fix" beyond replicating it faithfully.
        String app = null;
        if (containerType == Constants.ARCON_PACK) {
            app = appIndex.packApp(name);
        } else if (containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE) {
            for (ContainerOwner owner : nullSafe(c.getContainerOwner())) {
                if (owner.getType() == ContainerOwner.SCHEMA && owner.getName() != null) {
                    String ownerApp = appIndex.formApp(owner.getName());
                    if (ownerApp != null) {
                        app = ownerApp;
                        break;
                    }
                }
            }
        }
        if (app != null) containerApp.put(containerType + ":" + name, app);
        List<PermissionInfo> containerPerms = nullSafe(c.getAssignedGroup());
        if (containerPerms.isEmpty() && containerType != 3) containersNoPermission.add(new ContainerEntry(name, containerType));
        for (PermissionInfo p : containerPerms) {
            containers.computeIfAbsent(p.getGroupID(), k -> Collections.synchronizedList(new ArrayList<>())).add(new ContainerEntry(name, containerType));
        }
        if (containerType == 3) {
            for (Integer grp : nullSafe(c.getAdminGroupList())) {
                containerSubadmin.computeIfAbsent(grp, k -> Collections.synchronizedList(new ArrayList<>())).add(name);
            }

            // Java port of DocValidator.cpp's MenuReferenceValidator's third real source (see
            // MissingMenuReferenceIndex's javadoc) - a packing list's char-menu member reference
            // doesn't match a real menu. Matches DocPacklistDetails.cpp's ARREF_CHAR_MENU case,
            // which calls AddMenuReference for every packing-list menu member unconditionally.
            for (Reference ref : nullSafe(c.getReferences())) {
                if (ref.getReferenceType() == null || ref.getReferenceType().toInt() != ReferenceType.CHAR_MENU.toInt()) continue;
                String menuName = ref.getName();
                if (menuName != null && !menuName.isEmpty() && !knownMenuNames.contains(menuName)) {
                    missingMenuRefs.addPackingList(menuName, name);
                }
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** The owning app of a form/active link/container, or null if none - see AppMembershipIndex's javadoc and DocRoleDetails.cpp's app-scoping check (RoleDetailPage is the only real consumer; groups have no such filter in the C++). */
    public String formApp(String formName) { return appIndex == null ? null : appIndex.formApp(formName); }
    public String alApp(String alName) { return alApp.get(alName); }
    public String containerApp(int containerType, String name) { return containerApp.get(containerType + ":" + name); }

    public List<FormEntry> visibleForms(int groupId) { return visibleForms.getOrDefault(groupId, List.of()); }
    public List<String> subadminForms(int groupId) { return subadminForms.getOrDefault(groupId, List.of()); }
    public Map<String, List<FieldEntry>> fields(int groupId) { return fieldsByForm.getOrDefault(groupId, Map.of()); }
    public List<String> activeLinks(int groupId) { return activeLinks.getOrDefault(groupId, List.of()); }

    public boolean isOverlaidActiveLink(String name) {
        return alOverlaidByName.getOrDefault(name, false);
    }

    public boolean isOverlaidContainer(int containerType, String name) {
        return containerOverlaidByKey.getOrDefault(containerType + ":" + name, false);
    }

    public List<ContainerEntry> containers(int groupId, int containerType) {
        return this.containers.getOrDefault(groupId, List.of()).stream().filter(c -> c.containerType() == containerType).toList();
    }
    public List<String> containerSubadmin(int groupId) { return containerSubadmin.getOrDefault(groupId, List.of()); }

    public List<String> formsNoPermission() { return formsNoPermission; }
    public Map<String, List<String>> fieldsNoPermissionByForm() { return fieldsNoPermissionByForm; }
    public List<String> activeLinksNoPermission() { return activeLinksNoPermission; }
    public List<ContainerEntry> containersNoPermission() { return containersNoPermission; }
    public List<IndexFinding> indexFindings() { return indexFindings; }
    public List<FormRef> overlayOrCustomForms() { return overlayOrCustomForms; }
    public List<FieldRef> overlayOrCustomFields() { return overlayOrCustomFields; }
    public List<ViewRef> overlayOrCustomViews() { return overlayOrCustomViews; }
    public MissingMenuReferenceIndex missingMenuRefs() { return missingMenuRefs; }
}
