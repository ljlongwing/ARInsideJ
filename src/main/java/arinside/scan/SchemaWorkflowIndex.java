package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ContainerSource;
import arinside.ar.ReadPool;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.ContainerOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of the "which active links/filters/escalations/guides are attached to this form"
 * reverse indices CARSchemaList maintains (GetActiveLinks()/GetFilters()/GetEscalations()/
 * GetActLinkGuides()/GetFilterGuides()) and DocApplicationDetails.cpp's SearchActiveLinks/
 * SearchFilters/SearchEscalations/SearchContainer consume to build a form's "Application Content"
 * listing. Built once as a full workflow+container scan (matching the established
 * ContainerReferenceIndex/FilterErrorHandlerIndex pattern) rather than per-render, since a given form
 * can be attached to thousands of active links across a large server.
 */
public final class SchemaWorkflowIndex {
    private final Map<String, List<String>> activeLinksByForm = new ConcurrentHashMap<>();
    private final Map<String, List<String>> filtersByForm = new ConcurrentHashMap<>();
    private final Map<String, List<String>> escalationsByForm = new ConcurrentHashMap<>();
    private final Map<String, List<String>> alGuidesByForm = new ConcurrentHashMap<>();
    private final Map<String, List<String>> filterGuidesByForm = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static SchemaWorkflowIndex build(WorkflowSource workflowRepo, ContainerSource containerRepo) throws ARException {
        return build(workflowRepo, containerRepo, null, null, null);
    }

    public static SchemaWorkflowIndex build(WorkflowSource workflowRepo, ContainerSource containerRepo,
                                              ReadPool reads, Function<ArClient, WorkflowSource> workflowFactory,
                                              Function<ArClient, ContainerSource> containerFactory) throws ARException {
        SchemaWorkflowIndex idx = new SchemaWorkflowIndex();

        if (reads == null) {
            for (String name : workflowRepo.listActiveLinkNames()) {
                try {
                    idx.add(idx.activeLinksByForm, workflowRepo.getActiveLink(name).getFormList(), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema workflow (AL) for '" + name + "': " + e.getMessage());
                }
            }
            for (String name : workflowRepo.listFilterNames()) {
                try {
                    idx.add(idx.filtersByForm, workflowRepo.getFilter(name).getFormList(), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema workflow (filter) for '" + name + "': " + e.getMessage());
                }
            }
            for (String name : workflowRepo.listEscalationNames()) {
                try {
                    idx.add(idx.escalationsByForm, workflowRepo.getEscalation(name).getFormList(), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema workflow (escalation) for '" + name + "': " + e.getMessage());
                }
            }
            for (String name : containerRepo.listContainerNames(Constants.ARCON_GUIDE)) {
                try {
                    idx.addOwners(idx.alGuidesByForm, containerRepo.getContainer(name).getContainerOwner(), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema workflow (al guide) for '" + name + "': " + e.getMessage());
                }
            }
            for (String name : containerRepo.listContainerNames(Constants.ARCON_FILTER_GUIDE)) {
                try {
                    idx.addOwners(idx.filterGuidesByForm, containerRepo.getContainer(name).getContainerOwner(), name);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing schema workflow (filter guide) for '" + name + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String name : workflowRepo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.add(idx.activeLinksByForm, workflowFactory.apply(c).getActiveLink(name).getFormList(), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema workflow (AL) for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : workflowRepo.listFilterNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.add(idx.filtersByForm, workflowFactory.apply(c).getFilter(name).getFormList(), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema workflow (filter) for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : workflowRepo.listEscalationNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.add(idx.escalationsByForm, workflowFactory.apply(c).getEscalation(name).getFormList(), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema workflow (escalation) for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : containerRepo.listContainerNames(Constants.ARCON_GUIDE)) {
            tasks.add(reads.<Void>submit(c -> { idx.addOwners(idx.alGuidesByForm, containerFactory.apply(c).getContainer(name).getContainerOwner(), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema workflow (al guide) for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (String name : containerRepo.listContainerNames(Constants.ARCON_FILTER_GUIDE)) {
            tasks.add(reads.<Void>submit(c -> { idx.addOwners(idx.filterGuidesByForm, containerFactory.apply(c).getContainer(name).getContainerOwner(), name); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing schema workflow (filter guide) for '" + name + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void add(Map<String, List<String>> byForm, List<String> forms, String objectName) {
        if (forms == null) return;
        for (String form : forms) {
            byForm.computeIfAbsent(form, k -> Collections.synchronizedList(new ArrayList<>())).add(objectName);
        }
    }

    private void addOwners(Map<String, List<String>> byForm, List<ContainerOwner> owners, String containerName) {
        if (owners == null) return;
        for (ContainerOwner owner : owners) {
            if (owner.getType() == ContainerOwner.SCHEMA && owner.getName() != null) {
                byForm.computeIfAbsent(owner.getName(), k -> Collections.synchronizedList(new ArrayList<>())).add(containerName);
            }
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    public List<String> activeLinksOf(String formName) { return activeLinksByForm.getOrDefault(formName, List.of()); }
    public List<String> filtersOf(String formName) { return filtersByForm.getOrDefault(formName, List.of()); }
    public List<String> escalationsOf(String formName) { return escalationsByForm.getOrDefault(formName, List.of()); }
    public List<String> alGuidesOf(String formName) { return alGuidesByForm.getOrDefault(formName, List.of()); }
    public List<String> filterGuidesOf(String formName) { return filterGuidesByForm.getOrDefault(formName, List.of()); }
}
