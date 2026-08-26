package arinside.output;

/**
 * Java port of output/LetterFilterControl.{h,cpp} - the "abcdefghijklmnopqrstuvwxyz0123456789#"
 * jump-bar shown above the large object-list overview pages (Active Links/Filters/Escalations/
 * Forms). Purely a rendering aid for the client-side filtering JS (object_list.js + one per-type
 * *List.js, shipped as web-assets resources) - clicking a letter sets that page's search box to
 * "^&lt;letter&gt;" and
 * re-filters the already-embedded JSON row array; see e.g. filterList.js's
 * "$('#formLetterFilter a').click(...)" handler, identical across every type's *List.js.
 */
public final class LetterFilterControl {
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz0123456789#";

    private final int[] countPerLetter = new int[LETTERS.length()];

    /** Java port of CARObject::GetNameFirstChar + GetFirstCharIndex - first non-space char, lowercased, bucketed to '#' if it's not a-z/0-9. */
    public void incStartLetterOf(String name) {
        countPerLetter[indexOf(firstChar(name))]++;
    }

    private static char firstChar(String name) {
        if (name == null) return 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != ' ') return Character.toLowerCase(c);
        }
        return 0;
    }

    private static int indexOf(char ch) {
        int idx = LETTERS.indexOf(ch);
        return idx < 0 ? LETTERS.length() - 1 : idx; // falls back to '#', the last bucket - see class javadoc
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table id='formLetterFilter'><tr>\n");
        for (int i = 0; i < LETTERS.length(); i++) {
            char c = LETTERS.charAt(i);
            if (countPerLetter[i] > 0) {
                sb.append("<td><a href=\"javascript:void(0)\">").append(c).append("</a></td>\n");
            } else {
                sb.append("<td class=\"disabledLetter\">").append(c).append("</td>\n");
            }
        }
        sb.append("</tr></table>\n");
        return sb.toString();
    }
}
