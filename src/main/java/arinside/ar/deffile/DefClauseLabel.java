package arinside.ar.deffile;

import java.util.HashMap;
import java.util.Map;

/**
 * The nested {@code field {}/vui {}/action {}/else {}/}}-style sub-block markers within a struct.
 *
 * <p>Every end-of-block marker ({@code field {}`s closing {@code }}, {@code action {}'s, {@code
 * else {}'s, {@code support file {}'s) trims to the identical key {@code "}"}, so a bare {@code
 * "}"} can't say by itself which kind of block just closed - the caller must track its own
 * open-block context (a stack of "currently inside field/action/else/file") and interpret it
 * against whatever is on top. This is why there's only one {@code END} constant here.
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
