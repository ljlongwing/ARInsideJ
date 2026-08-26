package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ContainerSource;
import arinside.ar.OverlaySupport;
import arinside.ar.ReadPool;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.Reference;
import com.bmc.arsys.api.ReferenceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the "which containers reference this object" pattern repeated identically across
 * doc/DocAlDetails.cpp's ContainerReferences() (149-234), doc/DocFilterDetails.cpp's equivalent,
 * and doc/DocCharMenuDetails.cpp's ContainerReferences() (496-536, task #60) - each scans every
 * non-Application container's content for a reference (ARREF_ACTLINK/ARREF_FILTER/ARREF_CHAR_MENU)
 * matching this object's name. Built once as a shared reverse index instead of a live per-object
 * container scan (which would be prohibitively expensive - e.g. 34k+ filters x thousands of
 * containers per render). Container types scanned: GUIDE(1)/PACK(3)/FILTER_GUIDE(4)/WEBSERVICE(5)
 * - matches the C++'s "GetType() != ARCON_APP" (every container type except Application).
 */
public final class ContainerReferenceIndex {

    public record ContainerRef(String name, int containerType) {}

    private static final int[] CONTAINER_TYPES = {1, 3, 4, 5};
    private static final int[] ALL_CONTAINER_TYPES = {1, 2, 3, 4, 5};

    private final Map<String, List<ContainerRef>> byActiveLink = new ConcurrentHashMap<>();
    private final Map<String, List<ContainerRef>> byFilter = new ConcurrentHashMap<>();
    private final Map<String, List<ContainerRef>> byMenu = new ConcurrentHashMap<>();
    private final Map<String, List<ContainerRef>> byEscalation = new ConcurrentHashMap<>();
    /**
     * Java port of scan/ScanContainers.cpp's ARCON_PACK case (lines 86-110) - "which packing lists
     * contain this container as a member" (CARContainer::AddReference/GetReferences(), backing
     * output/WorkflowReferenceTable's "Workflow Reference" section on Application/AL-Guide/
     * Filter-Guide/Packing-List detail pages). Deliberately scoped to ONLY containers found inside a
     * PACK's own content (not GUIDE/FILTER_GUIDE/WEBSERVICE content, even though those container
     * types are also scanned above for their AL/Filter/Menu/Escalation references) - matches the
     * real C++'s switch, which only calls container.AddReference() from the ARCON_PACK branch.
     * Webservice containers never render this section at all in the real tool (DocWebserviceDetails.cpp
     * has no WorkflowReferenceTable call) even though they could theoretically receive a reference
     * here - a genuine, permanent gap in the original, not replicated as a "fix".
     */
    private final Map<String, List<ContainerRef>> byContainer = new ConcurrentHashMap<>();
    /** Java port of ScanContainers.cpp's ARCON_PACK ARREF_SCHEMA case - "which packing lists contain this form", feeding DocSchemaDetails.cpp's References tab "Container References" row (schema.GetPackingLists()). */
    private final Map<String, List<ContainerRef>> bySchema = new ConcurrentHashMap<>();
    /**
     * Java port of ScanContainers.cpp's ARCON_GUIDE/ARCON_FILTER_GUIDE/ARCON_WEBSERVICE case
     * (schema.AddActLinkGuide/AddFilterGuide/AddWebservice, from the container's OWN
     * ContainerOwner.SCHEMA-typed owner list, GetOwnerObjects() - a completely different mechanism
     * than {@link #byActiveLink} etc. above, which scan a container's *content references* instead)
     * - "which AL/Filter Guides and Webservices are designed to work against this form", feeding
     * DocSchemaDetails.cpp's WorkflowDoc() (the "Workflow" tab). Container objects (not
     * ContainerRef, which has no timestamp/changedBy) since the Workflow tab's JSON row needs those.
     */
    private final Map<String, List<Container>> bySchemaGuideOrWebservice = new ConcurrentHashMap<>();
    /**
     * Forward name -> containerType lookup across all 5 ARCON_* subtypes (including APP, unlike
     * every other map here) - feeds DocPacklistDetails.cpp's ARREF_CONTAINER case
     * (`CARContainer container(name); if (container.Exists()) srvType = CAREnum::ContainerType(...)`,
     * confirmed at DocPacklistDetails.cpp:133-143), which resolves a packing list's nested-container
     * member to its real subtype rather than the generic "Container" reference-type label. Populated
     * directly from listContainerNames() per type - no per-container fetch needed, since only the
     * type (already known from which list a name came from) is recorded, not any Container field.
     */
    private final Map<String, Integer> typeByName = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static ContainerReferenceIndex build(ContainerSource containerRepo, int serverOverlayMode, boolean overlaySupportEnabled) throws ARException {
        return build(containerRepo, serverOverlayMode, overlaySupportEnabled, null, null);
    }

    public static ContainerReferenceIndex build(ContainerSource containerRepo, int serverOverlayMode, boolean overlaySupportEnabled,
                                                  ReadPool reads, Function<ArClient, ContainerSource> containerFactory) throws ARException {
        ContainerReferenceIndex idx = new ContainerReferenceIndex();

        for (int type : ALL_CONTAINER_TYPES) {
            for (String name : containerRepo.listContainerNames(type)) {
                idx.typeByName.put(name, type);
            }
        }

        if (reads == null) {
            for (int type : CONTAINER_TYPES) {
                for (String name : containerRepo.listContainerNames(type)) {
                    try {
                        idx.indexContainer(containerRepo, name, type, serverOverlayMode, overlaySupportEnabled);
                    } catch (ARException e) {
                        System.out.println("EXCEPTION indexing container references for '" + name + "': " + e.getMessage());
                    }
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int type : CONTAINER_TYPES) {
            for (String name : containerRepo.listContainerNames(type)) {
                int t = type;
                tasks.add(reads.<Void>submit(c -> { idx.indexContainer(containerFactory.apply(c), name, t, serverOverlayMode, overlaySupportEnabled); return null; })
                    .exceptionally(ex -> { System.out.println("EXCEPTION indexing container references for '" + name + "': " + rootMessage(ex)); return null; }));
            }
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexContainer(ContainerSource containerRepo, String name, int containerType, int serverOverlayMode, boolean overlaySupportEnabled) throws ARException {
        Container c = containerRepo.getContainer(name);
        if (overlaySupportEnabled && !OverlaySupport.isVisible(c.getProperties(), serverOverlayMode, overlaySupportEnabled)) return;

        if ((containerType == Constants.ARCON_GUIDE || containerType == Constants.ARCON_FILTER_GUIDE || containerType == Constants.ARCON_WEBSERVICE)
            && c.getContainerOwner() != null) {
            for (com.bmc.arsys.api.ContainerOwner owner : c.getContainerOwner()) {
                if (owner.getType() == com.bmc.arsys.api.ContainerOwner.SCHEMA && owner.getName() != null) {
                    bySchemaGuideOrWebservice.computeIfAbsent(owner.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(c);
                }
            }
        }

        if (c.getReferences() == null) return;
        ContainerRef ref = new ContainerRef(name, containerType);
        for (Reference r : c.getReferences()) {
            ReferenceType type = r.getReferenceType();
            if (type == null || r.getName() == null) continue;
            if (type.toInt() == ReferenceType.ACTIVELINK.toInt()) {
                byActiveLink.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            } else if (type.toInt() == ReferenceType.FILTER.toInt()) {
                byFilter.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            } else if (type.toInt() == ReferenceType.CHAR_MENU.toInt()) {
                byMenu.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            } else if (type.toInt() == ReferenceType.ESCALATION.toInt()) {
                byEscalation.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            } else if (type.toInt() == ReferenceType.CONTAINER.toInt() && containerType == Constants.ARCON_PACK) {
                byContainer.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            } else if (type.toInt() == ReferenceType.SCHEMA.toInt() && containerType == Constants.ARCON_PACK) {
                bySchema.computeIfAbsent(r.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    public List<ContainerRef> activeLinkContainers(String name) { return byActiveLink.getOrDefault(name, List.of()); }
    public List<ContainerRef> filterContainers(String name) { return byFilter.getOrDefault(name, List.of()); }
    public List<ContainerRef> menuContainers(String name) { return byMenu.getOrDefault(name, List.of()); }
    public List<ContainerRef> escalationContainers(String name) { return byEscalation.getOrDefault(name, List.of()); }

    /** Every packing list that lists this container as a member - matches WorkflowReferenceTable's single real data source. */
    public List<ContainerRef> packingLists(String name) { return byContainer.getOrDefault(name, List.of()); }

    /** Every packing list that lists this form as a member - matches CARSchema::GetPackingLists(). */
    public List<ContainerRef> schemaPackingLists(String name) { return bySchema.getOrDefault(name, List.of()); }

    /** Every AL Guide/Filter Guide/Webservice whose own ContainerOwner list names this form - matches CARSchema::GetActLinkGuides()/GetFilterGuides()/GetWebservices(). */
    public List<Container> schemaGuidesAndWebservices(String name) { return bySchemaGuideOrWebservice.getOrDefault(name, List.of()); }

    /** This container's own ARCON_* type, or null if no container by this name exists - see {@link #typeByName}'s javadoc. */
    public Integer containerType(String name) { return typeByName.get(name); }
}
