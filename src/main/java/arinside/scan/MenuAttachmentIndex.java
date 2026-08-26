package arinside.scan;

import arinside.ar.ArClient;
import arinside.ar.ReadPool;
import arinside.ar.SchemaSource;
import arinside.ar.WorkflowSource;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.ActiveLinkAction;
import com.bmc.arsys.api.CharacterFieldLimit;
import com.bmc.arsys.api.ChangeFieldAction;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.FieldLimit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Java port of doc/DocCharMenuDetails.cpp's BuildUniqueSchemaList/RelatedFields/RelatedActiveLinks
 * - "which forms is this menu actually attached to" (a real character field's charMenu limit, or
 * an active link's Change Field action setting the menu onto a field), which drives which form(s)
 * a query/SQL menu's runtime-substitution qualifier is rendered against (see MenuDetailPage's
 * javadoc). Two full passes (every form's fields, every active link's actions), same accepted
 * duplicate-fetch tradeoff as every other scan/ index in this port.
 */
public final class MenuAttachmentIndex {

    public record FieldRef(String formName, String fieldName, int fieldId) {}
    public record ActiveLinkRef(String activeLinkName, String branch, int actionIndex) {}

    private final Map<String, Set<String>> attachedForms = new ConcurrentHashMap<>();
    private final Map<String, List<FieldRef>> relatedFields = new ConcurrentHashMap<>();
    private final Map<String, List<ActiveLinkRef>> relatedActiveLinks = new ConcurrentHashMap<>();

    /** Sequential fallback - used by file mode (no ReadPool available). */
    public static MenuAttachmentIndex build(SchemaSource schemaRepo, WorkflowSource workflowRepo) throws ARException {
        return build(schemaRepo, workflowRepo, null, null, null);
    }

    public static MenuAttachmentIndex build(SchemaSource schemaRepo, WorkflowSource workflowRepo, ReadPool reads,
                                              Function<ArClient, SchemaSource> schemaFactory, Function<ArClient, WorkflowSource> workflowFactory) throws ARException {
        MenuAttachmentIndex idx = new MenuAttachmentIndex();

        if (reads == null) {
            for (String formName : schemaRepo.listFormNames()) {
                try {
                    idx.indexForm(schemaRepo, formName);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing menu attachments for form '" + formName + "': " + e.getMessage());
                }
            }
            for (String alName : workflowRepo.listActiveLinkNames()) {
                try {
                    idx.indexActiveLink(workflowRepo, alName);
                } catch (ARException e) {
                    System.out.println("EXCEPTION indexing menu attachments for active link '" + alName + "': " + e.getMessage());
                }
            }
            return idx;
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String formName : schemaRepo.listFormNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexForm(schemaFactory.apply(c), formName); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing menu attachments for form '" + formName + "': " + rootMessage(ex)); return null; }));
        }
        for (String alName : workflowRepo.listActiveLinkNames()) {
            tasks.add(reads.<Void>submit(c -> { idx.indexActiveLink(workflowFactory.apply(c), alName); return null; })
                .exceptionally(ex -> { System.out.println("EXCEPTION indexing menu attachments for active link '" + alName + "': " + rootMessage(ex)); return null; }));
        }
        for (CompletableFuture<Void> t : tasks) t.join();
        return idx;
    }

    private void indexForm(SchemaSource schemaRepo, String formName) throws ARException {
        for (Field field : schemaRepo.getFields(formName)) {
            FieldLimit limit = field.getFieldLimit();
            if (!(limit instanceof CharacterFieldLimit charLimit)) continue;
            String menuName = charLimit.getCharMenu();
            if (menuName == null || menuName.isEmpty()) continue;

            attachedForms.computeIfAbsent(menuName, k -> ConcurrentHashMap.newKeySet()).add(formName);
            relatedFields.computeIfAbsent(menuName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new FieldRef(formName, field.getName(), field.getFieldID()));
        }
    }

    private void indexActiveLink(WorkflowSource workflowRepo, String alName) throws ARException {
        ActiveLink al = workflowRepo.getActiveLink(alName);
        scanActions(al.getActionList(), "If", al, alName);
        scanActions(al.getElseList(), "Else", al, alName);
    }

    private void scanActions(List<ActiveLinkAction> actions, String branch, ActiveLink al, String alName) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            if (!(actions.get(i) instanceof ChangeFieldAction cf)) continue;
            String menuName = cf.getCharMenu();
            if (menuName == null || menuName.isEmpty()) continue;

            List<String> forms = al.getFormList();
            if (forms != null) {
                attachedForms.computeIfAbsent(menuName, k -> ConcurrentHashMap.newKeySet()).addAll(forms);
            }
            relatedActiveLinks.computeIfAbsent(menuName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new ActiveLinkRef(alName, branch, i));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage();
    }

    /** Every form this menu is attached to (via a field's charMenu limit or an AL's Change Field action) - empty if none. */
    public Set<String> attachedForms(String menuName) {
        return attachedForms.getOrDefault(menuName, Set.of());
    }

    public boolean isUsed(String menuName) {
        return !attachedForms(menuName).isEmpty();
    }

    public List<FieldRef> relatedFields(String menuName) {
        return relatedFields.getOrDefault(menuName, List.of());
    }

    public List<ActiveLinkRef> relatedActiveLinks(String menuName) {
        return relatedActiveLinks.getOrDefault(menuName, List.of());
    }
}
