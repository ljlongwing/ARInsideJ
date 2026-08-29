package arinside.util;

import java.util.ArrayList;
import java.util.List;

import arinside.output.WebUtil;

/**
 * Word-level inline diff of two short HTML fragments (used by the snapshot-diff report to show
 * <em>what</em> changed inside a Run If qualification, not just that it changed). Tags are stripped,
 * the remaining text is tokenised on whitespace and on qualifier punctuation, and an LCS walk emits
 * one merged string with removed runs wrapped in {@code <del class="tokdel">} and added runs in
 * {@code <ins class="tokins">}. Fine for tens-of-tokens inputs; not meant for large documents.
 */
public final class TextDiff {
    private TextDiff() {}

    public static String inlineWords(String aHtml, String bHtml) {
        List<String> a = tokenize(stripTags(aHtml));
        List<String> b = tokenize(stripTags(bHtml));
        if (a.equals(b)) return "<span class=\"tokdiff\">" + joinEscaped(a) + "</span>";

        int n = a.size(), m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                lcs[i][j] = a.get(i).equals(b.get(j))
                    ? lcs[i + 1][j + 1] + 1
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);

        StringBuilder out = new StringBuilder("<span class=\"tokdiff\">");
        List<String> del = new ArrayList<>(), ins = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                flush(out, del, ins);
                out.append(esc(a.get(i))).append(' ');
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                del.add(a.get(i++));
            } else {
                ins.add(b.get(j++));
            }
        }
        while (i < n) del.add(a.get(i++));
        while (j < m) ins.add(b.get(j++));
        flush(out, del, ins);
        return out.append("</span>").toString();
    }

    private static void flush(StringBuilder out, List<String> del, List<String> ins) {
        if (!del.isEmpty()) { out.append("<del class=\"tokdel\">").append(joinEscaped(del)).append("</del> "); del.clear(); }
        if (!ins.isEmpty()) { out.append("<ins class=\"tokins\">").append(joinEscaped(ins)).append("</ins> "); ins.clear(); }
    }

    private static String joinEscaped(List<String> toks) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < toks.size(); k++) {
            if (k > 0) sb.append(' ');
            sb.append(esc(toks.get(k)));
        }
        return sb.toString();
    }

    private static String esc(String s) { return WebUtil.validate(s); }

    private static String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replaceAll("[ \\t\\r\\n\\u00a0]+", " ")
            .trim();
    }

    /** Split on whitespace, then peel qualifier punctuation ( ) " ' = < > , into their own tokens. */
    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String chunk : text.split(" ")) {
            if (chunk.isEmpty()) continue;
            StringBuilder word = new StringBuilder();
            for (int k = 0; k < chunk.length(); k++) {
                char c = chunk.charAt(k);
                if ("()\"'=<>,".indexOf(c) >= 0) {
                    if (word.length() > 0) { out.add(word.toString()); word.setLength(0); }
                    out.add(String.valueOf(c));
                } else {
                    word.append(c);
                }
            }
            if (word.length() > 0) out.add(word.toString());
        }
        return out;
    }
}
