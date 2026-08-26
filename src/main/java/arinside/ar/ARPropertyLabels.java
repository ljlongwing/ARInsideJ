package arinside.ar;

import com.bmc.arsys.api.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Java port of core/AREnum.cpp's CAREnum::FieldPropertiesLabel - a 259-case switch mapping
 * AR_DPROP_, AR_OPROP_ and AR_SMOPROP_ display/object property IDs to human names. Same
 * reflection-over-Constants approach as {@link ServerInfoLabels}/{@link ARKeywordLabels} rather
 * than hand-transcribing the whole table (this jar has ~469 AR_DPROP_ fields alone, more than the
 * C++'s table - reflection stays in sync automatically). Produces a readable-enough label
 * reconstructed from the constant name (e.g. AR_DPROP_ENUM_STYLE -&gt; "Enum Style") rather than the
 * C++'s hand-tuned English text - a known, minor cosmetic near-miss, same tradeoff already accepted
 * for the other two reflection-based label classes.
 */
public final class ARPropertyLabels {
    private static final String[] PREFIXES = {"AR_DPROP_", "AR_OPROP_", "AR_SMOPROP_"};
    private static final Map<Integer, String> BY_ID = new HashMap<>();

    static {
        for (Field f : Constants.class.getFields()) {
            String prefix = matchingPrefix(f.getName());
            if (prefix == null) continue;
            if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) continue;
            try {
                int value = f.getInt(null);
                BY_ID.putIfAbsent(value, prettyName(f.getName(), prefix));
            } catch (IllegalAccessException ignored) {
                // unreachable - all matched fields are public static final
            }
        }
    }

    private static String matchingPrefix(String name) {
        for (String p : PREFIXES) {
            if (name.startsWith(p)) return p;
        }
        return null;
    }

    private static String prettyName(String constantName, String prefix) {
        String suffix = constantName.substring(prefix.length());
        String[] words = suffix.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static String label(int propertyId) {
        return BY_ID.getOrDefault(propertyId, "Property " + propertyId);
    }
}
