package arinside.output;

/** Java port of output/WebUtil.cpp (the parts not tied to CPageParams/PAGE_* link helpers, which live on {@link Naming}/{@link URLLink} instead). */
public final class WebUtil {
    private WebUtil() {}

    /**
     * Java port of stdafx.cpp's global {@code const char* EmptyValue = "(null)"} - the placeholder
     * text shown for an empty/not-applicable cell throughout the whole tool (35 call sites across 8
     * doc/*.cpp files, confirmed via a full grep, not assumed to be SchemaDetailPage-specific).
     * Distinct from {@link #EMPTY_RUN_IF} (a different sentinel, for empty qualifications
     * specifically). Found missing via a live C++-vs-Java comparison (a References-tab cell showed a
     * genuinely blank {@code <td></td>} where the real C++ shows {@code <td>(null)</td>}) - most
     * call sites in this port were returning {@code ""} instead.
     */
    public static final String EMPTY_VALUE = "(null)";

    /** Java port of stdafx.cpp's {@code const char* EmptyRunIf = "No qualification specified"} - already correctly used as a literal string at every one of this port's own qualification-empty call sites; named here only so future code has a single source of truth instead of repeating the literal. */
    public static final String EMPTY_RUN_IF = "No qualification specified";

    /** Set once from AppConfig.gzCompression during startup validation; "htm" or "htm.gz". */
    public static String webpageFileExtension = "htm";

    public static String htmlPageSuffix() { return "htm"; }
    public static String htmlGZPageSuffix() { return "htm.gz"; }
    public static String webPageSuffix() { return webpageFileExtension; }
    public static String csvPageSuffix() { return "txt"; }

    public static String docName(String fileName) { return fileName + "." + webPageSuffix(); }
    public static String csvDocName(String fileName) { return fileName + "." + csvPageSuffix(); }

    /** Escapes &, ", <, > for safe inclusion in HTML - matches CWebUtil::Validate exactly (no apostrophe escaping). */
    public static String validate(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String objName(String objName) {
        return "<span class=\"objName\">" + objName + "</span>\n";
    }

    /** Escapes a string for safe embedding inside a JS double-quoted string literal (the hand-rolled "var xList = [[...]];" blocks the *List.js files consume) - not HTML escaping, a different concern from validate(). */
    public static String jsString(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Java port of CDocMain::CreateStandardFilterControl - the search box every jQuery-filterable overview list page (Active Links/Filters/Escalations/Forms) shares. */
    public static String standardFilterControl(String inputControlId, String placeholder) {
        StringBuilder sb = new StringBuilder();
        sb.append("<span class='clearable'><label for='").append(inputControlId).append("'>Filter: </label>");
        sb.append("<input id='").append(inputControlId).append("' class='data_field' type='text' ");
        if (placeholder != null && !placeholder.isEmpty()) sb.append("placeholder='").append(placeholder).append("'");
        sb.append("/></span>");
        return sb.toString();
    }
}
