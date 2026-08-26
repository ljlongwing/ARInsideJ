package arinside.ar.deffile;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code begin X}/{@code end} top-level block markers. See {@link DefItemLabel}'s javadoc for
 * the tag-lookup convention (trimmed label string).
 */
public enum DefStructLabel {
    ACTIVE_LINK("begin active link\n"),
    ADMIN_EXT("begin admin ext\n"),
    CHAR_MENU("begin char menu\n"),
    CONTAINER("begin container\n"),
    DIST_MAPPING("begin distributed mapping\n"),
    FILTER("begin filter\n"),
    ESCALATION("begin escalation\n"),
    GROUP("begin group\n"),
    SCHEMA("begin schema\n"),
    SCHEMA_DATA("begin schema data\n"),
    DIST_POOL("begin distributed pool\n"),
    VUI("begin vui\n"),
    LOCK_BLOCK("begin lock block\n"),
    APP("begin application\n"),
    IMAGE_OBJECT("begin image\n"),
    ASSOCIATION("begin association\n"),
    LOCALIZABLE_STRING("begin localizable string\n"),
    PROCESS_DEFINITION("begin process definition\n"),
    RECORD_DEFINITION("begin record definition\n"),
    VIEW_DEFINITION("begin view definition\n"),
    ASSOCIATION_DEFINITION("begin association definition\n"),
    RULE_DEFINITION("begin rule definition\n"),
    WEBAPI_DEFINITION("begin web api definition\n"),
    NAMED_LIST_DEFINITION("begin named list definition\n"),
    DOCUMENT_DEFINITION("begin document definition\n"),
    EVENT_DEFINITION("begin event definition\n"),
    EVENT_STATISTICS_DEFINITION("begin event statistics definition\n"),
    END("end\n");

    private final String label;
    private static final Map<String, DefStructLabel> LOOKUP = new HashMap<>(32);

    static {
        for (DefStructLabel l : values()) LOOKUP.put(l.label.trim(), l);
    }

    DefStructLabel(String label) {
        this.label = label;
    }

    public static DefStructLabel of(String trimmedTag) {
        return LOOKUP.get(trimmedTag);
    }
}
