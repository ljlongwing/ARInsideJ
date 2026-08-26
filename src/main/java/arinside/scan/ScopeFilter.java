package arinside.scan;

import arinside.ar.SchemaSource;
import arinside.output.ImageTag;
import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.CharacterFieldLimit;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.Field;

import java.util.HashSet;
import java.util.Set;

/**
 * Java port of the new {@code --scope <formName>} feature (task #97 - no C++ counterpart, this
 * tool never had a scoped-export mode). Computes a "hop-1" tree outward from one target form:
 * the form itself, every Active Link/Filter/Escalation that executes directly on it
 * ({@link WorkflowReferenceIndex#forForm}), every container those (or the form itself) belong to
 * ({@link ContainerReferenceIndex}/{@link AppMembershipIndex}), and every menu the form's own
 * fields reference via a character field's charMenu limit.
 *
 * <p>Deliberately NOT followed transitively into other forms the tree's workflow reaches into
 * (e.g. a Set Fields action pushing into a different form, tracked by {@link SchemaReferenceIndex})
 * - that would make the tree's size unpredictable and defeat the point of a fast, narrow export.
 * A user wanting that would run without --scope. This only changes which per-object pages get
 * fully rendered vs. stubbed in {@code Main.java}'s write loops - every scan/indexing pass
 * (including the ones this class itself depends on) still runs a full, unfiltered server pass.
 */
public final class ScopeFilter {
    private final String targetForm;
    private final Set<String> activeLinks = new HashSet<>();
    private final Set<String> filters = new HashSet<>();
    private final Set<String> escalations = new HashSet<>();
    private final Set<String> menus = new HashSet<>();
    private final Set<String> containerKeys = new HashSet<>();

    private ScopeFilter(String targetForm) {
        this.targetForm = targetForm;
    }

    public static ScopeFilter build(String targetForm, SchemaSource schemas, WorkflowReferenceIndex workflowIndex,
                                     ContainerReferenceIndex containerRefs, AppMembershipIndex appIndex) throws ARException {
        ScopeFilter f = new ScopeFilter(targetForm);

        for (WorkflowReferenceIndex.Ref ref : workflowIndex.forForm(targetForm)) {
            if (ref.icon() == ImageTag.Id.ActiveLink) f.activeLinks.add(ref.name());
            else if (ref.icon() == ImageTag.Id.Filter) f.filters.add(ref.name());
            else if (ref.icon() == ImageTag.Id.Escalation) f.escalations.add(ref.name());
        }

        for (String al : f.activeLinks) {
            for (ContainerReferenceIndex.ContainerRef c : containerRefs.activeLinkContainers(al)) f.addContainer(c);
        }
        for (String filter : f.filters) {
            for (ContainerReferenceIndex.ContainerRef c : containerRefs.filterContainers(filter)) f.addContainer(c);
        }
        for (String esc : f.escalations) {
            for (ContainerReferenceIndex.ContainerRef c : containerRefs.escalationContainers(esc)) f.addContainer(c);
        }
        for (ContainerReferenceIndex.ContainerRef c : containerRefs.schemaPackingLists(targetForm)) f.addContainer(c);

        String app = appIndex.formApp(targetForm);
        if (app != null) f.containerKeys.add(Constants.ARCON_APP + ":" + app);

        for (Field field : schemas.getFields(targetForm)) {
            if (field.getFieldLimit() instanceof CharacterFieldLimit charLimit
                && charLimit.getCharMenu() != null && !charLimit.getCharMenu().isBlank()) {
                f.menus.add(charLimit.getCharMenu());
            }
        }

        return f;
    }

    private void addContainer(ContainerReferenceIndex.ContainerRef c) {
        containerKeys.add(c.containerType() + ":" + c.name());
    }

    public boolean formInScope(String formName) { return targetForm.equals(formName); }
    public boolean activeLinkInScope(String name) { return activeLinks.contains(name); }
    public boolean filterInScope(String name) { return filters.contains(name); }
    public boolean escalationInScope(String name) { return escalations.contains(name); }
    public boolean menuInScope(String name) { return menus.contains(name); }
    public boolean containerInScope(int containerType, String name) { return containerKeys.contains(containerType + ":" + name); }
}
