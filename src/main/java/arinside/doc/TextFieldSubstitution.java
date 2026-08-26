package arinside.doc;

import arinside.ar.ARKeywordLabels;
import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.URLLink;

/**
 * Java port of doc/DocTextReferences.cpp's TextFindFields - scans free text for AR System's
 * pseudo-field/command syntax and resolves it to readable, hyperlinked text.
 *
 * <p>Two passes, exactly matching the C++'s replaceAllSpecials() then replaceAllFields():
 * <ol>
 *   <li>{@link #replaceAllSpecials} recognizes ~35 specific "Application-*"/"PERFORM-ACTION-*"
 *       command prefixes (each command's own bare numeric field-id/form-name argument, NOT a
 *       "$...$"-delimited token) and resolves that argument to a real hyperlinked field/form
 *       reference - matches processOneField/processTwoFields/processForm/processSecondParameter.</li>
 *   <li>{@link #resolveDollarTokens} (the original scope of this class) scans for "$...$"-delimited
 *       tokens anywhere in the text and, for each one whose content is a plain integer, replaces it
 *       with either a real hyperlinked field name (positive ID) or the AR System keyword name
 *       (zero/negative ID - see {@link ARKeywordLabels}), matching
 *       CDocBasicField::GetResolvedAndLinkedField's fieldId&lt;=0 branch. A non-numeric token is
 *       left completely unchanged, matching CARParseField::tag==0's passthrough.</li>
 * </ol>
 *
 * <p>Neither pass HTML-escapes anything, matching the real C++ exactly (confirmed by reading every
 * line of DocTextReferences.cpp and its DocAlMessageAction.cpp call site - no validating/escaping
 * call appears anywhere in this path) - directly confirmed against real data too: a real
 * "Application-Parse-Qual" active link's literal quote characters (from processForm's own
 * hardcoded '"' quoting, not user data) show up unescaped in the real C++ output
 * (<code>Application-Parse-Qual "$...$" $...$</code>), not as "&amp;quot;". An earlier version of
 * this class escaped literal text via WebUtil.validate(), which happened to never surface as a
 * mismatch only because none of the real "$...$" free-text values exercised so far contained a
 * literal &lt;/&gt;/&amp;/" character outside of a token - this real Parse-Qual example proved the
 * escaping wrong, not merely unverified, so it was removed rather than special-cased around.
 *
 * <p>Currency-field and status-history pseudo-tokens inside a "$...$" token (CARParseField's other
 * two tag types) are treated as non-numeric and left as-is in pass 2 - a small scope cut kept from
 * the original port (QualificationRenderer's operand rendering covers the qualification-side
 * equivalent of these two types in full; this free-text path doesn't).
 *
 * <p>detail is the real REFM_*-shaped label the caller supplies (e.g. "Field in Run Process
 * If-Action 3", REFM_RUN_PROCESS) - threaded unchanged through every field reference this class
 * discovers, matching the real C++'s one-label-per-call-site attribution (see
 * ActionSummaryTable's own detail-threading for the fuller rationale).
 */
final class TextFieldSubstitution {
    private TextFieldSubstitution() {}

    static String substitute(String text, String primaryForm, QualificationRenderer qr, String detail) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        if (qr == null) return text;
        String withSpecials = replaceAllSpecials(text, primaryForm, qr, detail);
        return resolveDollarTokens(withSpecials, primaryForm, qr, detail);
    }

    // ---------------------------------------------------------------------
    // Pass 2 (original scope): "$...$" token substitution
    // ---------------------------------------------------------------------

    private static String resolveDollarTokens(String text, String primaryForm, QualificationRenderer qr, String detail) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf('$', i);
            if (start < 0) {
                out.append(text, i, text.length());
                break;
            }
            int end = text.indexOf('$', start + 1);
            if (end < 0) {
                out.append(text, i, text.length());
                break;
            }
            String token = text.substring(start + 1, end);
            Integer fieldId = parsePlainInt(token);
            out.append(text, i, start);
            if (fieldId == null) {
                out.append(text, start, end + 1); // not a numeric token - passthrough unchanged, including the delimiters
            } else if (fieldId <= 0) {
                out.append('$').append(ARKeywordLabels.forFieldId(fieldId)).append('$'); // both "$" delimiters are kept in the resolved output too, matching CDocTextReferences::replaceAllFields
            } else {
                out.append('$').append(qr.fieldRef(primaryForm, fieldId, detail)).append('$');
            }
            i = end + 1;
        }
        return out.toString();
    }

    private static Integer parsePlainInt(String s) {
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Pass 1: "Application-*"/"PERFORM-ACTION-*" command-argument substitution
    // ---------------------------------------------------------------------

    /**
     * Java port of replaceAllSpecials() - two independent sequential scans over the SAME original
     * text (not over each other's output), exactly like the C++: if only one family matches, its
     * reconstruction is the result; if neither matches, the text is returned unchanged; if (in the
     * unlikely case) both families' command prefixes appear in the same text, both reconstructions
     * get concatenated - a real, if obscure, C++ quirk kept here rather than "fixed".
     */
    private static String replaceAllSpecials(String text, String primaryForm, QualificationRenderer qr, String detail) {
        String application = matchApplicationCommand(text, primaryForm, qr, detail);
        String performAction = matchPerformActionCommand(text, primaryForm, qr, detail);
        if (application == null && performAction == null) return text;
        StringBuilder combined = new StringBuilder();
        if (application != null) combined.append(application);
        if (performAction != null) combined.append(performAction);
        return combined.toString();
    }

    private static String matchApplicationCommand(String text, String primaryForm, QualificationRenderer qr, String detail) {
        int cmdStart = text.indexOf("Application-");
        if (cmdStart < 0) return null;
        int next = cmdStart + "Application-".length();
        if (text.startsWith("Copy-Field-Value ", next)) return processTwoFields(text, "Application-Copy-Field-Value", primaryForm, qr, detail);
        if (text.startsWith("Delete-Entry ", next)) return processForm(text, "Application-Delete-Entry", primaryForm, qr, true);
        if (text.startsWith("Format-Qual ", next)) return processForm(text, "Application-Format-Qual", primaryForm, qr, false);
        if (text.startsWith("Format-Qual-Filter ", next)) return processForm(text, "Application-Format-Qual-Filter", primaryForm, qr, false);
        if (text.startsWith("Format-Qual-ID ", next)) return processForm(text, "Application-Format-Qual-ID", primaryForm, qr, false);
        if (text.startsWith("Format-Qual-L ", next)) return processForm(text, "Application-Format-Qual-L", primaryForm, qr, false);
        if (text.startsWith("Get-Approval-Join ", next)) return processForm(text, "Application-Get-Approval-Join", primaryForm, qr, false);
        if (text.startsWith("Get-Approval-Join2 ", next)) return processForm(text, "Application-Get-Approval-Join2", primaryForm, qr, false);
        if (text.startsWith("Get-DetSig-Join ", next)) return processForm(text, "Application-Get-DetSig-Join", primaryForm, qr, false);
        if (text.startsWith("Get-DetSig-Join2 ", next)) return processForm(text, "Application-Get-DetSig-Join2", primaryForm, qr, false);
        if (text.startsWith("Get-Form-Alias ", next)) return processForm(text, "Application-Get-Form-Alias", primaryForm, qr, false);
        if (text.startsWith("Get-Locale-VuiID ", next)) return processForm(text, "Application-Get-Locale-VuiID", primaryForm, qr, false);
        if (text.startsWith("Get-Next-Recurrence-Time ", next)) return processForm(text, "Application-Get-Next-Recurrence-Time", primaryForm, qr, false);
        if (text.startsWith("Map-Ids-To-Names ", next)) return processForm(text, "Application-Map-Ids-To-Names", primaryForm, qr, false);
        if (text.startsWith("Map-Ids-To-Names-L ", next)) return processForm(text, "Application-Map-Ids-To-Names-L", primaryForm, qr, false);
        if (text.startsWith("Map-Names-To-Ids ", next)) return processForm(text, "Application-Map-Names-To-Ids", primaryForm, qr, false);
        if (text.startsWith("Map-Names-To-Ids-L ", next)) return processForm(text, "Application-Map-Names-To-Ids-L", primaryForm, qr, false);
        if (text.startsWith("Parse-Qual ", next)) return processForm(text, "Application-Parse-Qual", primaryForm, qr, false);
        if (text.startsWith("Parse-Qual-Filter ", next)) return processForm(text, "Application-Parse-Qual-Filter", primaryForm, qr, false);
        if (text.startsWith("Parse-Qual-L ", next)) return processForm(text, "Application-Parse-Qual-L", primaryForm, qr, false);
        if (text.startsWith("Parse-Qual-SField-L ", next)) return processForm(text, "Application-Parse-Qual-SField-L", primaryForm, qr, false);
        if (text.startsWith("Parse-Val-SField ", next)) return processForm(text, "Application-Parse-Val-SField", primaryForm, qr, false);
        if (text.startsWith("Query-Delete-Entry ", next)) return processForm(text, "Application-Query-Delete-Entry", primaryForm, qr, true);
        return null;
    }

    private static String matchPerformActionCommand(String text, String primaryForm, QualificationRenderer qr, String detail) {
        int cmdStart = text.indexOf("PERFORM-ACTION-");
        if (cmdStart < 0) return null;
        int next = cmdStart + "PERFORM-ACTION-".length();
        if (text.startsWith("ACTIVE-LINK ", next)) return processSecondParameter(text, "PERFORM-ACTION-ACTIVE-LINK", primaryForm, qr, detail);
        if (text.startsWith("ADD-ATTACHMENT ", next)) return processOneField(text, "PERFORM-ACTION-ADD-ATTACHMENT", primaryForm, qr, detail);
        if (text.startsWith("DELETE-ATTACHMENT ", next)) return processOneField(text, "PERFORM-ACTION-DELETE-ATTACHMENT", primaryForm, qr, detail);
        if (text.startsWith("GET-FIELD-LABEL ", next)) return processOneField(text, "PERFORM-ACTION-GET-FIELD-LABEL", primaryForm, qr, detail);
        if (text.startsWith("GET-PREFERENCE ", next)) return processOneField(text, "PERFORM-ACTION-GET-PREFERENCE", primaryForm, qr, detail);
        if (text.startsWith("NAV-FIELD-SET-SELECTED-ITEM ", next)) return processOneField(text, "PERFORM-ACTION-NAV-FIELD-SET-SELECTED-ITEM", primaryForm, qr, detail);
        if (text.startsWith("OPEN-ATTACHMENT ", next)) return processOneField(text, "PERFORM-ACTION-OPEN-ATTACHMENT", primaryForm, qr, detail);
        if (text.startsWith("SAVE-ATTACHMENT ", next)) return processOneField(text, "PERFORM-ACTION-SAVE-ATTACHMENT", primaryForm, qr, detail);
        if (text.startsWith("SET-PREFERENCE ", next)) return processOneField(text, "PERFORM-ACTION-SET-PREFERENCE", primaryForm, qr, detail);
        if (text.startsWith("TABLE-CLEAR ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-CLEAR", primaryForm, qr, detail);
        if (text.startsWith("TABLE-CLEAR-ROWCHANGED ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-CLEAR-ROWCHANGED", primaryForm, qr, detail);
        if (text.startsWith("TABLE-DESELECTALL ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-DESELECTALL", primaryForm, qr, detail);
        if (text.startsWith("TABLE-GET-SELECTED-COLUMN ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-GET-SELECTED-COLUMN", primaryForm, qr, detail);
        if (text.startsWith("TABLE-IS-LEAF-SELECTED ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-IS-LEAF-SELECTED", primaryForm, qr, detail);
        if (text.startsWith("TABLE-NEXT-CHUNK ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-NEXT-CHUNK", primaryForm, qr, detail);
        if (text.startsWith("TABLE-PREV-CHUNK ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-PREV-CHUNK", primaryForm, qr, detail);
        if (text.startsWith("TABLE-REFRESH ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-REFRESH", primaryForm, qr, detail);
        if (text.startsWith("TABLE-REPORT ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-REPORT", primaryForm, qr, detail);
        if (text.startsWith("TABLE-SELECT-NODE ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-SELECT-NODE", primaryForm, qr, detail);
        if (text.startsWith("TABLE-SELECTALL ", next)) return processOneField(text, "PERFORM-ACTION-TABLE-SELECTALL", primaryForm, qr, detail);
        return null;
    }

    /** Java port of processOneField() - "COMMAND &lt;fieldId&gt; [rest]" -&gt; "COMMAND &lt;link&gt; [rest]". */
    private static String processOneField(String text, String command, String primaryForm, QualificationRenderer qr, String detail) {
        int length = command.length() + 1;
        int pos = text.indexOf(command);
        if (pos < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, length + pos));
        String tmp = text.substring(length + pos);
        int spacePos = tmp.indexOf(' ');
        int fieldId = atoi(spacePos >= 0 ? tmp.substring(0, spacePos) : tmp);
        if (fieldId > 0) out.append(refFieldID(fieldId, primaryForm, qr, detail));
        if (spacePos >= 0) out.append(tmp.substring(spacePos));
        return out.toString();
    }

    /**
     * Java port of processTwoFields() - only used by Application-Copy-Field-Value. Each of the two
     * field parameters is either a bare numeric field id (resolved here) or already a "$...$" token
     * (left completely untouched here, deferring resolution to pass 2 - the C++'s own design, not a
     * scope cut). Trailing text after the second field's bare-numeric case is dropped, matching a
     * real (if odd) quirk in the C++ (no append after the final refFieldID() there either).
     */
    private static String processTwoFields(String text, String command, String primaryForm, QualificationRenderer qr, String detail) {
        int length = command.length() + 1;
        int pos = text.indexOf(command);
        if (pos < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, length + pos));
        String tmp = text.substring(length + pos);
        if (tmp.startsWith("$")) {
            tmp = tmp.substring(1);
            int closing = tmp.indexOf('$') + 1;
            out.append("$").append(tmp, 0, Math.max(closing, 0));
        } else {
            int spacePos = tmp.indexOf(' ');
            int fieldId = atoi(spacePos >= 0 ? tmp.substring(0, spacePos) : tmp);
            out.append(refFieldID(fieldId, primaryForm, qr, detail));
        }
        out.append(" ");
        int spacePos2 = tmp.indexOf(' ');
        tmp = spacePos2 >= 0 ? tmp.substring(spacePos2 + 1) : "";
        if (tmp.startsWith("$")) {
            out.append(tmp);
        } else {
            int fieldId2 = atoi(tmp);
            out.append(refFieldID(fieldId2, primaryForm, qr, detail));
        }
        return out.toString();
    }

    /**
     * Java port of processForm() - a form-name parameter (quoted or bare) becomes a real schema
     * link when the form exists, plain echoed text otherwise. trackReference (true only for
     * Application-Delete-Entry/Application-Query-Delete-Entry, matching the C++'s refItem!=NULL
     * callers) registers a REFM_DELETE_ENTRY_ACTION schema reference via
     * QualificationRenderer.SchemaReferenceSink - either on the resolved form (when it exists) or,
     * matching the C++'s "$SCHEMA$ used as form name" fallback, on primaryForm when the raw
     * parameter is the literal "$-5$" keyword (AR_KEYWORD_SCHEMA) and doesn't resolve to a real
     * form on its own.
     */
    private static String processForm(String text, String command, String primaryForm, QualificationRenderer qr, boolean trackReference) {
        int length = command.length() + 1;
        int pos = text.indexOf(command);
        if (pos < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, length + pos));
        String tmp = text.substring(length + pos);
        String form;
        int afterPos;
        if (tmp.startsWith("\"")) {
            out.append("\"");
            int closeQuote = tmp.indexOf('"', 1);
            if (closeQuote < 0) {
                form = tmp.substring(1);
                afterPos = -1;
            } else {
                form = tmp.substring(1, closeQuote);
                afterPos = closeQuote;
            }
        } else {
            int spacePos = tmp.indexOf(' ');
            form = spacePos >= 0 ? tmp.substring(0, spacePos) : tmp;
            afterPos = spacePos;
        }
        boolean exists = qr != null && qr.fieldIndex() != null && qr.fieldIndex().formExists(form);
        out.append(exists ? schemaLink(form, qr) : form);
        if (trackReference && qr != null && qr.schemaSink() != null) {
            if (exists) {
                qr.schemaSink().reference(form, QualificationRenderer.SchemaReferenceSink.Reason.DELETE_ENTRY);
            } else if ("$-5$".equals(form)) {
                qr.schemaSink().reference(primaryForm, QualificationRenderer.SchemaReferenceSink.Reason.DELETE_ENTRY);
            }
        }
        if (afterPos >= 0) out.append(tmp.substring(afterPos));
        return out.toString();
    }

    /** Java port of processSecondParameter() - only used by PERFORM-ACTION-ACTIVE-LINK: "ACTIVE-LINK &lt;alName&gt; &lt;fieldId&gt; [rest]" -&gt; the AL name is echoed unlinked (matching the C++ exactly - no AL lookup happens here), the field id becomes a link; a trailing newline and dropped remainder both match real (if odd) C++ quirks. */
    private static String processSecondParameter(String text, String command, String primaryForm, QualificationRenderer qr, String detail) {
        int length = command.length() + 1;
        int pos = text.indexOf(command);
        if (pos < 0) return text;
        StringBuilder out = new StringBuilder(text.substring(0, length + pos));
        String tmp = text.substring(length + pos);
        int spacePos = tmp.indexOf(' ');
        out.append(spacePos >= 0 ? tmp.substring(0, spacePos) : tmp);
        out.append(" ");
        if (spacePos >= 0) {
            String rest = tmp.substring(spacePos);
            int fieldId = atoi(rest);
            out.append(refFieldID(fieldId, primaryForm, qr, detail)).append("\n");
        }
        return out.toString();
    }

    /** Java port of refFieldID(). */
    private static String refFieldID(int fieldId, String primaryForm, QualificationRenderer qr, String detail) {
        if (fieldId <= 0) return ARKeywordLabels.forFieldId(fieldId);
        return qr != null ? qr.fieldRef(primaryForm, fieldId, detail) : "Field " + fieldId;
    }

    /** Java port of processForm()'s schema-link branch (URLLink(schema, rootLevel, showImage=false)) - a plain no-icon schema link. */
    private static String schemaLink(String formName, QualificationRenderer qr) {
        boolean isOverlaid = qr.fieldIndex().isOverlaid(formName);
        return URLLink.to(formName, Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.NoImage, qr.rootLevel()).toHtml();
    }

    /** C's atoi() semantics (leading whitespace, optional sign, then digits; 0 if none) - matches DocTextReferences.cpp's repeated use of atoi() on parsed command arguments. */
    private static int atoi(String s) {
        int i = 0, n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        int sign = 1;
        if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            if (s.charAt(i) == '-') sign = -1;
            i++;
        }
        int start = i;
        while (i < n && Character.isDigit(s.charAt(i))) i++;
        if (i == start) return 0;
        try {
            return sign * Integer.parseInt(s.substring(start, i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
