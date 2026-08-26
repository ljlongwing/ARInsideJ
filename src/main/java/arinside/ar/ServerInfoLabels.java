package arinside.ar;

import com.bmc.arsys.api.Constants;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Java port of core/AREnum.cpp's CAREnum::ServerInfoApiCall - a 358-case switch mapping
 * AR_SERVER_INFO_* operation IDs to display names. Rather than hand-transcribing that whole
 * table, this builds the same mapping by reflecting over com.bmc.arsys.api.Constants for every
 * "AR_SERVER_INFO_*" int field and indexing by value - the Java API jar already carries the same
 * names the C++ table was written from, so this stays in sync with whatever constants exist in
 * this jar version rather than a hand-copied snapshot.
 */
public final class ServerInfoLabels {
    private static final Map<Integer, String> BY_ID = new HashMap<>();

    static {
        for (Field f : Constants.class.getFields()) {
            if (!f.getName().startsWith("AR_SERVER_INFO_")) continue;
            if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) continue;
            try {
                int value = f.getInt(null);
                BY_ID.putIfAbsent(value, prettyName(f.getName()));
            } catch (IllegalAccessException ignored) {
                // unreachable - all matched fields are public static final
            }
        }
    }

    private static String prettyName(String constantName) {
        String suffix = constantName.substring("AR_SERVER_INFO_".length());
        String[] words = suffix.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static String label(int operationId) {
        return BY_ID.getOrDefault(operationId, "Operation " + operationId);
    }

    /** All known operation IDs, sorted - used to build the getServerInfo(int[]) request. */
    public static int[] allKnownIds() {
        // exclude sensitive/problematic ops, matching lists/ARServerInfoList.cpp's FillRequest skip-list
        java.util.Set<Integer> excluded = java.util.Set.of(
            Constants.AR_SERVER_INFO_DB_PASSWORD,
            Constants.AR_SERVER_INFO_DSO_MARK_PENDING_RETRY,
            Constants.AR_SERVER_INFO_RESTART_PLUGIN
        );
        return BY_ID.keySet().stream()
            .filter(id -> !excluded.contains(id))
            .mapToInt(Integer::intValue)
            .sorted()
            .toArray();
    }
}
