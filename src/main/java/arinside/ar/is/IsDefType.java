package arinside.ar.is;

/**
 * The Innovation Studio definition families ARInsideJ documents. Record definitions are
 * deliberately excluded - every classic AR form is an "IS record definition", and those are
 * already documented from the Java API. Each entry
 * carries its DataPage query class (for listing) and its REST url segment (for a by-name fetch).
 */
public enum IsDefType {
    RULE        ("Rule",             "Rules",             "rule",        "ruledefinition",        "com.bmc.arsys.rx.application.rule.datapage.RuleDefinitionDataPageQuery"),
    PROCESS     ("Process",          "Processes",         "process",     "processdefinition",     "com.bmc.arsys.rx.application.process.datapage.ProcessDefinitionDataPageQuery"),
    WEB_API     ("Web API",          "Web APIs",          "webapi",      "webapidefinition",      "com.bmc.arsys.rx.application.webapi.datapage.WebApiDefinitionDataPageQuery"),
    DOCUMENT    ("Document",         "Documents",         "document",    "documentdefinition",    "com.bmc.arsys.rx.application.document.datapage.DocumentDefinitionDataPageQuery"),
    VIEW        ("View",             "Views",             "view",        "viewdefinition",        "com.bmc.arsys.rx.application.view.datapage.ViewDefinitionDataPageQuery"),
    NAMED_LIST  ("Named List",       "Named Lists",       "namedlist",   "namedlistdefinition",   "com.bmc.arsys.rx.application.namedlist.datapage.NamedListDefinitionDataPageQuery"),
    EVENT       ("Event",            "Events",            "event",       "eventdefinition",       "com.bmc.arsys.rx.application.event.datapage.EventDefinitionDataPageQuery"),
    EVENT_STATS ("Event Statistics", "Event Statistics",  "event",       "eventstatisticsdefinition", "com.bmc.arsys.rx.application.event.datapage.EventStatisticsDefinitionDataPageQuery"),
    ASSOCIATION ("Association",      "Associations",      "association", "associationdefinition", "com.bmc.arsys.rx.application.association.datapage.AssociationDefinitionDataPageQuery");

    public final String label;        // singular, e.g. "Web API"
    public final String pluralLabel;  // e.g. "Web APIs"
    public final String urlArea;      // path area, e.g. "webapi"
    public final String urlSegment;   // e.g. "webapidefinition"
    public final String dataPageQuery;

    IsDefType(String label, String pluralLabel, String urlArea, String urlSegment, String dataPageQuery) {
        this.label = label;
        this.pluralLabel = pluralLabel;
        this.urlArea = urlArea;
        this.urlSegment = urlSegment;
        this.dataPageQuery = dataPageQuery;
    }

    /** Filename-safe folder name for this type's detail pages, e.g. "is/web_api". */
    public String dir() {
        return "is/" + name().toLowerCase();
    }
}
