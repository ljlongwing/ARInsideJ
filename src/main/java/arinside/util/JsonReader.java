package arinside.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-free recursive-descent JSON parser - the read side of {@link Json} (which only
 * writes). Used to consume Innovation Studio REST responses (see {@code arinside.ar.is}); the port
 * otherwise has no need to parse JSON, and pulling in Jackson for one feature isn't worth the
 * runtime dependency.
 *
 * <p>Maps become insertion-ordered {@link LinkedHashMap}, arrays {@link ArrayList}. Numbers are
 * {@link Long} when integral and in range, otherwise {@link Double}. Lenient enough for real API
 * payloads, strict enough to fail loudly on genuine garbage. Not a general-purpose library - no
 * streaming, no comments, no huge-document tuning.
 */
public final class JsonReader {

    private final String s;
    private int i;

    private JsonReader(String s) { this.s = s; }

    public static Object parse(String json) {
        JsonReader r = new JsonReader(json == null ? "" : json);
        r.ws();
        Object v = r.value();
        r.ws();
        if (r.i < r.s.length()) throw r.err("trailing content");
        return v;
    }

    /* ---------- navigation helpers (all null-safe) ---------- */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    /** Walk map keys; returns null if any hop misses or isn't a map. */
    public static Object at(Object node, String... keys) {
        Object cur = node;
        for (String k : keys) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(k);
        }
        return cur;
    }

    public static String str(Object node, String... keys) {
        Object v = at(node, keys);
        return v == null ? null : String.valueOf(v);
    }

    public static boolean bool(Object node, String... keys) {
        Object v = at(node, keys);
        return v instanceof Boolean b && b;
    }

    public static long lng(Object node, String... keys) {
        Object v = at(node, keys);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    /* ---------- grammar ---------- */

    private Object value() {
        if (i >= s.length()) throw err("unexpected end");
        char c = s.charAt(i);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't', 'f' -> boolLit();
            case 'n' -> nul();
            default -> (c == '-' || (c >= '0' && c <= '9')) ? number() : err("unexpected '" + c + "'").throwIt();
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> m = new LinkedHashMap<>();
        ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value());
            ws();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw err("expected ',' or '}'");
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> a = new ArrayList<>();
        ws();
        if (peek() == ']') { i++; return a; }
        while (true) {
            ws();
            a.add(value());
            ws();
            char c = next();
            if (c == ']') return a;
            if (c != ',') throw err("expected ',' or ']'");
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw err("bad \\u escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw err("bad escape \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object number() {
        int start = i;
        if (peek() == '-') i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        boolean fp = false;
        if (i < s.length() && s.charAt(i) == '.') { fp = true; i++; while (i < s.length() && Character.isDigit(s.charAt(i))) i++; }
        if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            fp = true; i++;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        }
        String num = s.substring(start, i);
        if (!fp) {
            try { return Long.parseLong(num); } catch (NumberFormatException ignored) { /* fall through */ }
        }
        return Double.parseDouble(num);
    }

    private Boolean boolLit() {
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        throw err("expected boolean");
    }

    private Object nul() {
        if (s.startsWith("null", i)) { i += 4; return null; }
        throw err("expected null");
    }

    /* ---------- lexer plumbing ---------- */

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }
    private char next() { if (i >= s.length()) throw err("unexpected end"); return s.charAt(i++); }
    private void expect(char c) { if (next() != c) { i--; throw err("expected '" + c + "'"); } }

    private JsonParseException err(String msg) {
        int from = Math.max(0, i - 20), to = Math.min(s.length(), i + 20);
        return new JsonParseException("JSON parse error at " + i + " (" + msg + "): …" + s.substring(from, to) + "…");
    }

    /** Small holder so {@code err(...).throwIt()} works inside the switch expression. */
    public static final class JsonParseException extends RuntimeException {
        JsonParseException(String m) { super(m); }
        Object throwIt() { throw this; }
    }
}
