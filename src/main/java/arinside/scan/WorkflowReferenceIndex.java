package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.OverlaySupport;
import arinside.ar.ReadPool;
import arinside.ar.WorkflowSource;
import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.PagePath;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.FilterMessageAction;
import com.bmc.arsys.api.MessageAction;
import com.bmc.arsys.api.NotifyAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the "which workflow executes on this form" half of scan/ScanActiveLinks.cpp /
 * ScanFilters.cpp / ScanEscalations.cpp (the CARInside::AddFieldReference / RefItem machinery
 * in the C++). This index itself is scoped to form-level "executes on" references only, built
 * from ActiveLink/Filter/Escalation.getFormList() - field-level references (which workflow object
 * sets/reads *this specific field*) are tracked separately by {@link FieldReferenceIndex},
 * populated as a side effect of QualificationRenderer's Run-If rendering and ActionSummaryTable's
 * Set/PushFields/arithmetic/function field-assignment threading (see those classes' javadocs) -
 * not duplicated here.
 *
 * Costs one extra full fetch pass over every active link/filter/escalation (in addition to the
 * overview+detail passes those pages already do) - a real, known inefficiency, not an oversight;
 * left as-is for now since correctness came first and a Phase 7 pass can consolidate all three
 * fetches into one if the extra ~3 minutes on a server this size becomes a real problem.
 */
public final class WorkflowReferenceIndex {

    /**
     * order/enabled/executeOn/ifCount/elseCount/modified/changedBy: only populated for AL/Filter/
     * Escalation refs (null for every other typeLabel this record is shared with, e.g. Menu/
     * Container references built elsewhere) - feeds SchemaDetailPage's "Workflow" tab JSON row
     * (DocSchemaDetails.cpp's AddJsonRow(CARActiveLink/CARFilter/CAREscalation, ...)). executeOn is
     * the raw C++ value the client-side ARActLinkExecuteOn/ARFilterOperation/AREscalationTMType
     * decode: AL's execute-mask bitmask, Filter's op-set bitmask, or Escalation's 1=Interval/0=Time
     * flag - never a Java-side label, matching the C++ JSON (decoding happens client-side).
     */
    public record Ref(String name, String typeLabel, ImageTag.Id icon, PagePath link,
                       Integer order, Boolean enabled, Integer executeOn, Integer ifCount, Integer elseCount,
                       com.bmc.arsys.api.Timestamp modified, String changedBy, Integer formCount) {
        public Ref(String name, String typeLabel, ImageTag.Id icon, PagePath link) {
            this(name, typeLabel, icon, link, null, null, null, null, null, null, null, null);
        }
        /** True when this workflow object executes on more than one form (Dev Studio's "Shared" flag). Null formCount (Menu/Container/Image refs) counts as not shared. */
        public boolean shared() {
            return formCount != null && formCount > 1;
        }
    }
    /**
     * Java port of DocCustomWorkflow.cpp's per-object overlay/custom row for AL/Filter/Escalation/
     * Menu/Container/Image - wraps {@link Ref} (kept as-is since it's shared by several unrelated
     * "workflow that touches this form/image/container" call sites) with the extra Enabled/Changed/
     * By metadata CustomWorkflowPage needs. enabled is null for object types with no enable concept
     * (Menu/Container/Image/Schema/Field/VUI), matching the C++'s blank "Enabled" cell for those rows.
     */
    public record CustomWorkflowRef(Ref ref, int overlayType, Boolean enabled, com.bmc.arsys.api.Timestamp lastUpdateTime, String lastChangedBy) {}
    /** Java port of DocMain.cpp's MessageList Message-action scan. */
    public record MessageFinding(Ref owner, String detail, int msgNumber, String msgText, int msgType) {}
    /** Java port of DocMain.cpp's NotificationList Notify-action scan (filters/escalations only - matches the C++, which doesn't scan active links here). */
    public record NotifyFinding(Ref owner, String detail, int notifyMechanism, String text) {}

    /** Thread-safe (ConcurrentHashMap + synchronized buckets): written both by build()'s own AL/filter/escalation pass (parallel when a ReadPool is supplied - see the two build() overloads) and read afterward, never both at once. */
    private final Map<String, List<Ref>> byForm = new ConcurrentHashMap<>();
    /**
     * Java port of DocCustomWorkflow.cpp's overlay/custom object listing, scoped to active
     * links/filters/escalations - see build()'s javadoc note. Thread-safe (synchronized list):
     * populated both from build()'s own AL/filter/escalation pass AND from
     * MenuDetailPage/ContainerDetailPage rendering, which runs concurrently on the parallel write
     * pool - see addIfOverlayOrCustom's javadoc.
     */
    private final List<CustomWorkflowRef> overlayOrCustom = Collections.synchronizedList(new ArrayList<>());
    private final List<MessageFinding> messages = Collections.synchronizedList(new ArrayList<>());
    private final List<NotifyFinding> notifications = Collections.synchronizedList(new ArrayList<>());

    private int serverOverlayMode = 1;

    /** Sequential fallback - used by file mode (no ReadPool available, see FileModeWorkflowRepository's javadoc for why file mode stays fully sequential). */
    public static WorkflowReferenceIndex build(WorkflowSource repo, int serverOverlayMode) throws ARException {
        return build(repo, serverOverlayMode, null, null);
    }

    /**
     * Server mode: each active link/filter/escalation's fetch+index-accumulate runs as one task on
     * the read pool (no separate write-pool hop - accumulating into a few in-memory maps is fast
     * CPU work, not disk I/O, so there's nothing to gain from a second async hop here unlike the
     * per-object detail pages). Falls back to the plain sequential loop when reads is null.
     */
    public static WorkflowReferenceIndex build(WorkflowSource repo, int serverOverlayMode, ReadPool reads, Function<ArClient, WorkflowSource> repoFactory) throws ARException {
        WorkflowReferenceIndex idx = new WorkflowReferenceIndex();
        idx.serverOverlayMode = serverOverlayMode;

        if (reads == null) {
            for (String name : repo.listActiveLinkNames()) {
                try {
                    idx.indexActiveLink(repo, name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing active link '" + name + "': " + e.getMessage());
                }
            }
            for (String name : repo.listFilterNames()) {
                try {
                    idx.indexFilter(repo, name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing filter '" + name + "': " + e.getMessage());
                }
            }
            for (String name : repo.listEscalationNames()) {
                try {
                    idx.indexEscalation(repo, name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing escalation '" + name + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : repo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexActiveLink(repoFactory.apply(c), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing active link '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : repo.listFilterNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexFilter(repoFactory.apply(c), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing filter '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : repo.listEscalationNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexEscalation(repoFactory.apply(c), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing escalation '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexActiveLink(WorkflowSource repo, String name) throws ARException {
        ActiveLink al = repo.getActiveLink(name);
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(al.getProperties(), serverOverlayMode);
        Ref ref = new Ref(name, "Active Link", ImageTag.Id.ActiveLink, Naming.activeLinkDetail(name, isOverlaid),
            al.getOrder(), al.isEnable(), al.getExecuteMask(), size(al.getActionList()), size(al.getElseList()),
            al.getLastUpdateTime(), al.getLastChangedBy(), size(al.getFormList()));
        add(al.getFormList(), ref);
        addIfOverlayOrCustom(al.getProperties(), ref, al.isEnable(), al.getLastUpdateTime(), al.getLastChangedBy());
        scanAlMessages(ref, "If", al.getActionList());
        scanAlMessages(ref, "Else", al.getElseList());
    }

    private void indexFilter(WorkflowSource repo, String name) throws ARException {
        Filter f = repo.getFilter(name);
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(f.getProperties(), serverOverlayMode);
        Ref ref = new Ref(name, "Filter", ImageTag.Id.Filter, Naming.filterDetail(name, isOverlaid),
            f.getOrder(), f.isEnable(), f.getOpSet(), size(f.getActionList()), size(f.getElseList()),
            f.getLastUpdateTime(), f.getLastChangedBy(), size(f.getFormList()));
        add(f.getFormList(), ref);
        addIfOverlayOrCustom(f.getProperties(), ref, f.isEnable(), f.getLastUpdateTime(), f.getLastChangedBy());
        scanFilterActions(ref, "If", f.getActionList());
        scanFilterActions(ref, "Else", f.getElseList());
    }

    private void indexEscalation(WorkflowSource repo, String name) throws ARException {
        Escalation esc = repo.getEscalation(name);
        boolean isOverlaid = OverlaySupport.isOverlaidForNaming(esc.getProperties(), serverOverlayMode);
        // Escalation has no Order concept (matches the C++'s item.PushBack("", alloc) for this slot)
        // - escalationTmType is 1 for interval-based, 0 for specific-time-based (EscalationInterval
        // vs EscalationTime, the two real EscalationTimeCriteria subtypes - confirmed via the arapi
        // jar's class list, matching AREscalationTMType's "val==1 ? Interval : Time" client-side).
        int tmType = esc.getEscalationTm() instanceof com.bmc.arsys.api.EscalationInterval ? 1 : 0;
        Ref ref = new Ref(name, "Escalation", ImageTag.Id.Escalation, Naming.escalationDetail(name, isOverlaid),
            null, esc.isEnable(), tmType, size(esc.getActionList()), size(esc.getElseList()),
            esc.getLastUpdateTime(), esc.getLastChangedBy(), size(esc.getFormList()));
        add(esc.getFormList(), ref);
        addIfOverlayOrCustom(esc.getProperties(), ref, esc.isEnable(), esc.getLastUpdateTime(), esc.getLastChangedBy());
        scanFilterActions(ref, "If", esc.getActionList());
        scanFilterActions(ref, "Else", esc.getElseList());
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    private void add(List<String> forms, Ref ref) {
        if (forms == null) return;
        for (String form : forms) {
            byForm.computeIfAbsent(form, k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
        }
    }

    /**
     * Lets MenuDetailPage/ContainerDetailPage/ImageDetailPage feed this same list as a side effect
     * of their own per-object rendering, in addition to build()'s own AL/filter/escalation pass -
     * see class javadoc. enabled is null for object types with no enable concept (Menu/Container/
     * Image), matching DocCustomWorkflow.cpp's blank "Enabled" cell for those rows.
     */
    public void addIfOverlayOrCustom(com.bmc.arsys.api.PropertyMap props, Ref ref, Boolean enabled, com.bmc.arsys.api.Timestamp lastUpdateTime, String lastChangedBy) {
        int type = OverlaySupport.overlayType(props);
        if (type == Constants.AR_OVERLAY_OBJECT || type == Constants.AR_CUSTOM_OBJECT) {
            overlayOrCustom.add(new CustomWorkflowRef(ref, type, enabled, lastUpdateTime, lastChangedBy));
        }
    }

    private void scanAlMessages(Ref ref, String branch, List<com.bmc.arsys.api.ActiveLinkAction> actions) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i) instanceof MessageAction m) {
                messages.add(new MessageFinding(ref, branch + "-Action " + i, m.getMessageNum(), m.getMessageText(), m.getMessageType()));
            }
        }
    }

    private void scanFilterActions(Ref ref, String branch, List<com.bmc.arsys.api.FilterAction> actions) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action instanceof FilterMessageAction m) {
                messages.add(new MessageFinding(ref, branch + "-Action " + i, m.getMessageNum(), m.getMessageText(), m.getMessageType()));
            } else if (action instanceof NotifyAction n) {
                StringBuilder text = new StringBuilder();
                if (n.getSubjectText() != null && !n.getSubjectText().isEmpty()) text.append(n.getSubjectText()).append("<br/>");
                if (n.getNotifyText() != null) text.append(n.getNotifyText());
                notifications.add(new NotifyFinding(ref, branch + "-Action " + i, n.getNotifyMechanism(), text.toString()));
            }
        }
    }

    public List<Ref> forForm(String formName) {
        return byForm.getOrDefault(formName, List.of());
    }

    public List<CustomWorkflowRef> overlayOrCustom() {
        return overlayOrCustom;
    }

    public List<MessageFinding> messages() {
        return messages;
    }

    public List<NotifyFinding> notifications() {
        return notifications;
    }
}
