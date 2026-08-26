package arinside.ar.deffile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Tokenizes a {@code .def} export into a stream of (tag, value) pairs; per-object-type meaning is
 * layered on top by {@link DefFileParser}.
 *
 * <p>Two independent continuation layers:
 * <ol>
 * <li><b>Raw physical-line joining</b> ({@link #nextJoinedLine()}) - a single logical line's bytes
 * can be hard-wrapped across multiple physical lines. A continuation physical line is signalled by
 * starting with an embedded-newline marker (ASCII 0x01, substituted back to a literal {@code '\n'}
 * once per joined line) or the continuation char {@code '&'}. Blank lines (bare {@code "\n"} or
 * {@code "\r\n"}) are skipped before a logical line starts.</li>
 * <li><b>Tag-repeat continuation</b> ({@link #nextTagValue()}) - after one joined line is split into
 * a (tag, value) pair, the SAME tag can repeat on subsequent joined lines to continue accumulating
 * one logical value, in one of 3 modes keyed by which {@link DefItemLabel} the tag is: <i>explicit</i>
 * (a trailing {@code '&'} on the current value - the default for any tag not in the other two
 * categories), <i>implicit</i> (a fixed ~25-tag set - OBJECT_PROP/DISPLAY_PROPLIST/VALUE/SORT_LIST/
 * etc. - keeps consuming same-tag lines unconditionally until a different tag appears), and
 * <i>conditional</i> (CHAR_MENU/PUSH_FIELD/SET_FIELD only - itemized lists where each line carries a
 * shared numeric index prefix before the first {@code '\'}; continues only while consecutive lines'
 * prefixes match). A handful of tags (ENUM_VALUE/ENUM_VALUE_NUM/NAME/OBJECT) never continue at all.</li>
 * </ol>
 *
 * <p>Not handled: the legacy {@code export-version <= 3} explicit-continuation-char variant (not
 * relevant to modern exports) and per-tag typed value decoding, which is deferred to {@link
 * DefFileParser}'s per-object builders - this class stays a pure tag/value tokenizer.
 */
final class DefLineReader implements AutoCloseable {
    private static final char CONT_CHAR = '&';
    /** ASCII 0x01, an internal embedded-newline marker in the raw export bytes. Written via (char) 1 rather than a character-literal escape to avoid tooling that silently rewrites unicode escapes into raw control bytes in source files. */
    private static final char EMBEDDED_RETURN = (char) 1;
    private static final char FILE_SEPARATOR = '\\';

    /** One real logical (tag, value) line - value is untrimmed, may itself contain embedded '\n' from EMBEDDED_RETURN substitution. */
    record TagValue(String tag, String value) {}

    private final BufferedReader reader;
    private String pushedBackRawLine;
    private TagValue pushedBackTagValue;

    DefLineReader(Reader reader) {
        this.reader = reader instanceof BufferedReader br ? br : new BufferedReader(reader);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    /** Null at EOF. */
    TagValue nextTagValue() throws IOException {
        TagValue pair;
        if (pushedBackTagValue != null) {
            pair = pushedBackTagValue;
            pushedBackTagValue = null;
        } else {
            pair = parseLine(nextJoinedLine());
        }
        if (pair == null) return null;

        StringBuilder value = new StringBuilder(pair.value());
        String tag = pair.tag();
        TagValue current = pair;

        while (!noContinuedTag(current.tag())) {
            boolean implicit = implicitlyContinuedTag(current.tag());
            boolean conditional = conditionallyContinuedTag(current.tag());
            int len = current.value().length();
            boolean explicitTrigger = len > 0 && current.value().charAt(len - 1) == CONT_CHAR && !implicit && !conditional;

            if (explicitTrigger) {
                TagValue next = parseLine(nextJoinedLine());
                if (next != null && next.tag().equals(tag) && !next.value().isEmpty()) {
                    value.deleteCharAt(value.length() - 1).append(next.value());
                } else {
                    value.deleteCharAt(value.length() - 1);
                }
                if (next == null) break;
                current = next; // advance even on a mismatched tag, so the mismatched pair is re-parsed as the next call's starting pair
            } else if (implicit) {
                TagValue next = parseLine(nextJoinedLine());
                if (next == null) break;
                if (!next.tag().equals(tag)) {
                    pushedBackTagValue = next;
                    break;
                }
                value.append(next.value());
                current = next;
            } else if (conditional) {
                TagValue next = parseLine(nextJoinedLine());
                if (next == null) break;
                if (!checkContinuationCondition(next, tag, value)) {
                    pushedBackTagValue = next;
                    break;
                }
                int i = next.value().indexOf(FILE_SEPARATOR);
                if (i + 1 > 0 && i + 1 <= next.value().length()) {
                    value.append(next.value().substring(i + 1));
                }
                current = next;
            } else {
                break;
            }
        }
        return new TagValue(tag, value.toString());
    }

    private boolean checkContinuationCondition(TagValue next, String origTag, CharSequence origValueSoFar) {
        if (!next.tag().equals(origTag)) return false;
        if (origTag.equals(DefItemLabel.CHAR_MENU.label().trim())) {
            return !next.value().isEmpty() && next.value().charAt(0) == FILE_SEPARATOR;
        }
        String oldIndex = indexPrefix(origValueSoFar.toString());
        String newIndex = indexPrefix(next.value());
        return oldIndex != null && oldIndex.equals(newIndex);
    }

    private String indexPrefix(String value) {
        int i = value.indexOf(FILE_SEPARATOR);
        return (i > 0 && i <= value.length()) ? value.substring(0, i) : null;
    }

    private boolean noContinuedTag(String tag) {
        DefItemLabel label = DefItemLabel.of(tag);
        if (label == null) return false;
        return switch (label) {
            case ENUM_VALUE, ENUM_VALUE_NUM, NAME, OBJECT -> true;
            default -> false;
        };
    }

    private boolean implicitlyContinuedTag(String tag) {
        DefItemLabel label = DefItemLabel.of(tag);
        if (label == null) return false;
        return switch (label) {
            case ACTLINK_QRY, SVC_IN_FLD_MAP, SVC_OUT_FLD_MAP, ALLOW_CODES, CHANGE_DIARY, COMMAND, CONTENT, DATA,
                 DATA_MAPPING, DIRECT_SQL, DEFAULT, DESCRIPTION, DISPLAY_PROPLIST, TBLFLD_QUERY, ESCALATION_QRY,
                 FILTER_QRY, FUNC_CODES, GET_LIST_FLDS, HELP, IMAGE_CONTENT, JOIN_QRY, MAPPING, MSG_TEXT, NOT_TEXT,
                 OBJECT_PROP, OPEN_DLG_INPUT, OPEN_DLG_OUTPUT, OPEN_DLG_QUERY, OPEN_DLG_MSG_TEXT, RTN_MAPPING,
                 SORT_LIST, VALUE, ARCHIVEINFO_QRY, ASSN_SPECIFIC_LIST, ADD_ASSN_SPECIFIC_LIST, ASSN_PRIMARY_FORM_QUAL,
                 ASSN_SECONDARY_FORM_QUAL, ASSN_ASSOCIATION_FORM_QUAL -> true;
            default -> false;
        };
    }

    private boolean conditionallyContinuedTag(String tag) {
        DefItemLabel label = DefItemLabel.of(tag);
        if (label == null) return false;
        return switch (label) {
            case CHAR_MENU, PUSH_FIELD, SET_FIELD -> true;
            default -> false;
        };
    }

    /** Splits one already-joined logical line at the first ':' (tag includes the colon, value starts right after ": "). */
    private TagValue parseLine(String line) {
        if (line == null) return null;
        String tag;
        String value;
        if (line.startsWith("#") || line.isEmpty()) {
            tag = "#";
            value = "";
        } else {
            int index = line.indexOf(':');
            if (index == -1) {
                tag = line.trim();
                value = "";
            } else {
                index += 2; // skip ": "
                if (index < line.length()) {
                    tag = line.substring(0, index).trim();
                    value = line.substring(index);
                } else if (index == line.length() && line.startsWith(DefItemLabel.COMMIT_CHANGES.label())) {
                    tag = DefItemLabel.COMMIT_CHANGES.label().trim();
                    value = "";
                } else {
                    tag = line.trim();
                    value = "";
                }
            }
        }
        return new TagValue(tag, value);
    }

    /** Joins raw physical lines into one logical line, substituting embedded-newline markers back to '\n' once at the end. Null at EOF. */
    private String nextJoinedLine() throws IOException {
        String line = nextRawLine();
        while (line != null && (line.length() == 1 || line.equals("\r\n"))) {
            line = nextRawLine();
        }
        if (line == null) return null;

        StringBuilder complete = new StringBuilder(line);
        String next = nextRawLine();
        while (next != null) {
            int length = next.length();
            char firstChar = length > 0 ? next.charAt(0) : 0;
            if (length == 0 || firstChar == EMBEDDED_RETURN || firstChar == CONT_CHAR) {
                complete.append(next);
                next = nextRawLine();
            } else {
                while (complete.length() > 0
                    && (complete.charAt(complete.length() - 1) == '\r' || complete.charAt(complete.length() - 1) == '\n')) {
                    complete.deleteCharAt(complete.length() - 1);
                }
                pushedBackRawLine = next;
                break;
            }
        }
        return complete.length() > 0 ? complete.toString().replace(EMBEDDED_RETURN, '\n') : null;
    }

    private String nextRawLine() throws IOException {
        if (pushedBackRawLine != null) {
            String l = pushedBackRawLine;
            pushedBackRawLine = null;
            return l;
        }
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            sb.append((char) c);
            if (c == '\n') break;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
