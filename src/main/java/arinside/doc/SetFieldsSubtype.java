package arinside.doc;

import com.bmc.arsys.api.SetFieldsAction;
import com.bmc.arsys.api.SetFieldsFromFilterAPI;
import com.bmc.arsys.api.SetFieldsFromForm;
import com.bmc.arsys.api.SetFieldsFromRESTWebService;
import com.bmc.arsys.api.SetFieldsFromSQL;
import com.bmc.arsys.api.SetFieldsFromWebService;

/**
 * The "where does this Set Fields action read from" sub-types, used to break the flat "Set Fields"
 * row on the AL/Filter/Escalation "by action" overview pages (see {@link ActiveLinkActionPage} etc.)
 * into per-source child rows/pages - so questions like "which filters make a web service call" can
 * actually be answered.
 *
 * <p>Set Fields is the only action type with genuinely distinct API subclasses ({@code
 * SetFieldsFrom*}); Push Fields / Service / Direct SQL / Call Guide differ only by target server,
 * which is an attribute of the action rather than a sub-type, so they get no breakdown.
 *
 * <p>{@link #of(SetFieldsAction)} mirrors {@link ActionSummaryTable}'s own {@code setFieldsOf()}
 * dispatch order exactly (including its Atrium-Orchestrator-hidden-inside-Web-Service special case).
 */
public enum SetFieldsSubtype {
    CURRENT_SCREEN("current", "Current Screen"),
    FROM_FORM("form", "From Form"),
    SQL("sql", "SQL"),
    FILTER_API("filterapi", "Filter API"),
    WEB_SERVICE("webservice", "Web Service"),
    REST_WEB_SERVICE("rest", "REST Web Service"),
    ATRIUM_ORCHESTRATOR("bao", "Atrium Orchestrator");

    private final String key;
    private final String label;

    SetFieldsSubtype(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Stable slug used in the detail page file name (e.g. {@code filters_action_4_webservice.htm}). */
    public String key() {
        return key;
    }

    /** Human-readable label shown in the child row / page heading. */
    public String label() {
        return label;
    }

    public static SetFieldsSubtype of(SetFieldsAction a) {
        if (a instanceof SetFieldsFromRESTWebService) return REST_WEB_SERVICE;
        if (a instanceof SetFieldsFromWebService fa) return ActionSummaryTable.isAtriumOrchestrator(fa) ? ATRIUM_ORCHESTRATOR : WEB_SERVICE;
        if (a instanceof SetFieldsFromFilterAPI) return FILTER_API;
        if (a instanceof SetFieldsFromSQL) return SQL;
        if (a instanceof SetFieldsFromForm) return FROM_FORM;
        return CURRENT_SCREEN;
    }
}
