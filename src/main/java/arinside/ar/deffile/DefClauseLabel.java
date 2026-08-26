package arinside.ar.deffile;

import java.util.HashMap;
import java.util.Map;

/**
 * Java port of {@code com.bmc.arsys.server.domain.export.def.DefClauseLabel} - the nested
 * {@code field {}/vui {}/action {}/else {}/}}-style sub-block markers within a struct.
 *
 * <p>The real source declares 4 distinct end-of-block constants ({@code END}/{@code END_ACTION}/
 * {@code END_ELSE}/{@code END_FILE}), but their raw label strings ({@code "}\n"}, {@code "   }\n"}
 * x3) all trim to the identical key {@code "}"} - confirmed by reading the trim-based lookup logic
 * directly, not assumed. That means the real parser cannot and does not distinguish which kind of
 * block just closed from the tag string alone; it must track its own open-block context (a stack
 * of "currently inside field/action/else/file") and interpret a bare {@code "}"} against whatever
 * is on top. This port does the same - one {@code END} constant, nesting tracked by the caller.
 */
public enum DefClauseLabel {
    FIELD("field {\n"),
    REFERENCE("reference {\n"),
    VUI("vui  {\n"),
    END("}\n"),
    ACTION("   action {\n"),
    ELSE("   else {\n"),
    FILE("   support file {\n"),
    TAG("tag {\n"),
    PKFKMAPPING("   pkfk-mapping {\n"),
    PKFKMAPPING_PRIMARY_TO_ASSOCIATION_FORM("   pkfk-mapping-primaryFormToAssociationForm {\n"),
    PKFKMAPPING_SECONDARY_TO_ASSOCIATION_FORM("   pkfk-mapping-secondaryFormToAssociationForm {\n"),
    FIELD_INHERITANCE_SOURCE_FORM("source form {\n");

    private final String label;
    private static final Map<String, DefClauseLabel> LOOKUP = new HashMap<>();

    static {
        for (DefClauseLabel l : values()) LOOKUP.put(l.label.trim(), l);
    }

    DefClauseLabel(String label) {
        this.label = label;
    }

    public static DefClauseLabel of(String trimmedTag) {
        return LOOKUP.get(trimmedTag);
    }
}
