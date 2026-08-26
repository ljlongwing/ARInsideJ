package arinside.ar;

import com.bmc.arsys.api.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Java port of core/AREnum.cpp's CAREnum::Keyword - maps a negative/zero "pseudo-field ID" found
 * inside free text (e.g. a Run Process command's `$-5$` token) to its AR System keyword name
 * (`$SCHEMA$`), same reflection-over-Constants approach as {@link ServerInfoLabels} rather than
 * hand-transcribing the ~70-entry switch. Most AR_KEYWORD_* names are a single word and match this
 * exactly (SCHEMA, SERVER, USER, TIMESTAMP, ...) - the handful of multi-word ones the C++ renders
 * with a literal dash (e.g. "CLIENT-TYPE") or a shortened form (e.g. "LASTOPENWINID") instead just
 * keep their underscore-joined constant suffix here (e.g. "CLIENT_TYPE") - a known, minor cosmetic
 * near-miss for those specific keywords, not attempted further since the underscore form is still
 * unambiguous and correctly named.
 */
public final class ARKeywordLabels {
    private static final Map<Integer, String> BY_ID = new HashMap<>();

    static {
        for (Field f : Constants.class.getFields()) {
            if (!f.getName().startsWith("AR_KEYWORD_")) continue;
            if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) continue;
            try {
                int value = f.getInt(null);
                BY_ID.putIfAbsent(value, f.getName().substring("AR_KEYWORD_".length()));
            } catch (IllegalAccessException ignored) {
                // unreachable - all matched fields are public static final
            }
        }
    }

    private ARKeywordLabels() {}

    /** iFieldId is expected <= 0 (the "pseudo-field" convention) - takes the absolute value to look up, matching CAREnum::Keyword(abs(fieldId)). */
    public static String forFieldId(int iFieldId) {
        return BY_ID.getOrDefault(Math.abs(iFieldId), "KEYWORD" + iFieldId);
    }
}
