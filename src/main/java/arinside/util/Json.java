package arinside.util;

import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serializer for the {@code data/*.json} export (see {@link arinside.output.JsonExport}).
 * Handles the value types that export builds: {@link Map} (insertion-ordered -> object),
 * {@link List} -> array, {@link String}, {@link Number}, {@link Boolean}, {@code null}. No parser,
 * no dependency - the project has no JSON library and this is all the export needs.
 */
public final class Json {
    private Json() {}

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    public static void write(Object value, StringBuilder sb) {
        if (value == null) { sb.append("null"); return; }
        if (value instanceof String s) { quote(s, sb); return; }
        if (value instanceof Boolean || value instanceof Number) { sb.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                write(list.get(i), sb);
            }
            sb.append(']');
            return;
        }
        quote(value.toString(), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
