package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.ARKeywordLabels;
import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.Table;
import arinside.output.TableRow;
import arinside.output.URLLink;
import arinside.output.WebUtil;
import arinside.scan.GlobalFieldIndex;
import com.bmc.arsys.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.function.Function;

/**
 * Shared helper for the If/Else action list rendered on the ActiveLink/Filter/Escalation detail
 * pages. Scoped-down stand-in for doc/DocAlActionStruct.cpp (906 lines) and
 * doc/DocFilterActionStruct.cpp (697 lines), which fully pretty-print every action type's
 * parameters including qualification expressions.
 *
 * Renders real detail for every AssignInfo assignment type (VALUE/FIELD/PROCESS/ARITH/FUNCTION/
 * DDE/SQL/FILTER_API - see assignmentOf()), recursively for the ones whose operands/parameters are
 * themselves AssignInfo trees (arithmetic, function calls, filter-API calls), plus
 * Message/RunProcess/Notify/Log/CallGuide/ChangeField/OpenWindow (including its input/output field
 * pairs). Set/PushFields "only if" qualifications render via the same {@link QualificationRenderer}
 * already used for Run If (a single instance is threaded through from the caller and reused - the
 * two-form TR./DB. case isn't distinguished, a minor accepted simplification since Run If already
 * has the same single-form bias). Every field a Set/PushFields/OpenWindow/Service action's
 * assignment list references (both the target being set AND any field-valued source) is now also
 * hyperlinked and fed through the same {@link QualificationRenderer.FieldReferenceSink} Run If
 * uses, via {@link QualificationRenderer#fieldRef}, so `FieldReferenceIndex`/
 * `MissingFieldReferenceIndex` cover action assignments too, not just Run If.
 *
 * One thing this still deliberately does NOT attempt:
 * FieldAssignInfo/PushFieldsInfo.getAssignmentAsString() - despite the name, this returned null for
 * every real action sampled, so isn't the free shortcut it looks like; the class's own
 * prepareAssignmentString() that would populate it is package-private in this jar, not callable
 * from here. Arithmetic sub-expressions are always parenthesized rather than precedence-minimized
 * (unlike Run If's qualification renderer) - simpler, still unambiguous.
 */
public final class ActionSummaryTable {
    private ActionSummaryTable() {}

    /**
     * qr may be null (no field-reference context available) - falls back to plain, unlinked "Field N" text and skips "only if" rendering entirely, matching the old scoped-down behavior. currentServerName is this run's connected AR server (AppConfig.serverName) - see serverInfoLink's javadoc for why it's needed.
     *
     * <p>Renders two independent tables (If-Actions, then Else-Actions) rather than one combined
     * table with a branch column, matching the original tool's layout - one table per branch, each
     * with its own "If-Actions"/"Else-Actions" heading.
     */
    public static <A> String render(List<A> ifActions, List<A> elseActions, Function<A, Integer> typeOf, Function<Integer, String> label,
                                     String primaryForm, QualificationRenderer qr, String currentServerName) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderOne(ifActions, "If-Actions", "If", typeOf, label, primaryForm, qr, currentServerName, qr != null ? qr.rootLevel() : 0));
        sb.append(renderOne(elseActions, "Else-Actions", "Else", typeOf, label, primaryForm, qr, currentServerName, qr != null ? qr.rootLevel() : 0));
        return sb.toString();
    }

    private static <A> String renderOne(List<A> actions, String heading, String ifElse, Function<A, Integer> typeOf, Function<Integer, String> label,
                                         String primaryForm, QualificationRenderer qr, String currentServerName, int rootLevel) {
        Table tbl = new Table("actionList", "TblObjectList");
        tbl.addColumn(5, "Position");
        tbl.addColumn(15, "Action Type");
        tbl.addColumn(80, "Description");
        tbl.description = new ImageTag(ImageTag.Id.Document, rootLevel).toHtml() + heading;

        int count = addRows(tbl, actions, ifElse, typeOf, label, primaryForm, qr, currentServerName);
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl.toXHtml();
    }

    /**
     * Attributes every field reference {@code detailOf} discovers while rendering one action to a
     * "&lt;Action Type&gt; &lt;If|Else&gt;-Action &lt;N&gt;" label (e.g. "Set Fields If-Action 3") via
     * {@link QualificationRenderer#setCurrentDetail} before rendering that action - real per-call
     * granularity (the C++'s util/RefItem.cpp distinguishes "Target in 'Set Fields'" from "Value in
     * 'Set Fields'" within the very same action, ~30 such REFM_* variants total) isn't threaded all
     * the way through this file's own ~15 internal fieldRef()/fieldRefWithText() call sites - a
     * scoped-down but real fix: every reference is now correctly attributed to the action that
     * actually found it, instead of every reference on the whole page being mislabeled "Run If"
     * regardless of which action (or none at all) discovered it.
     */
    private static <A> int addRows(Table tbl, List<A> actions, String ifElse, Function<A, Integer> typeOf, Function<Integer, String> label,
                                    String primaryForm, QualificationRenderer qr, String currentServerName) {
        if (actions == null) return 0;
        // Action index is 0-based (matches the "#" column and every REFM_* reference label), not 1-based.
        int count = 0;
        for (A action : actions) {
            int position = count;
            count++;
            String actionType = label.apply(typeOf.apply(action));
            if (qr != null) {
                qr.setCurrentDetail(actionType + " " + ifElse + "-Action " + position);
                qr.setCurrentActionSuffix(ifElse + "-Action " + position);
            }
            TableRow row = new TableRow();
            row.addCell(new arinside.output.TableCell(position));
            row.addCell(actionType);
            row.addCell(detailOf(action, primaryForm, qr, currentServerName));
            tbl.addRow(row);
        }
        return count;
    }

    private static String detailOf(Object action, String primaryForm, QualificationRenderer qr, String currentServerName) {
        if (action instanceof SetFieldsAction a) return setFieldsOf(a, primaryForm, qr, currentServerName);
        if (action instanceof PushFieldsAction a) return pushFieldsOf(a, primaryForm, qr, currentServerName);
        if (action instanceof MessageAction a) return messageActionOf(a.getMessageNum(), a.getMessageType(), a.getMessageText(), true, a.isUsePromptingPane(), primaryForm, qr);
        if (action instanceof FilterMessageAction a) return messageActionOf(a.getMessageNum(), a.getMessageType(), a.getMessageText(), false, false, primaryForm, qr);
        if (action instanceof RunProcessAction a) return "<code>" + TextFieldSubstitution.substitute(a.getCommandLine(), primaryForm, qr, qualDetail("Field in Run Process", qr)) + "</code>";
        if (action instanceof NotifyAction a) return notifyOf(a, primaryForm, qr);
        // Java port of DocFilterActionStruct.cpp's FilterActionLog - was missing the "File Name: "
        // label prefix entirely (just the bare path, or "" when unset) - real C++ always shows the
        // label, falling back to "(null)" when the path is empty, matching every other single-value
        // action type's label+value shape.
        if (action instanceof LogAction a) return "File Name: " + (hasText(a.getFilePath()) ? WebUtil.validate(a.getFilePath()) : "(null)");
        if (action instanceof CallGuideAction a) return callGuideOf(a, primaryForm, qr, currentServerName);
        if (action instanceof ChangeFieldAction a) return changeFieldOf(a, primaryForm, qr);
        if (action instanceof OpenWindowAction a) return openWindow(a, primaryForm, qr, currentServerName);
        if (action instanceof CloseWindowAction a) return checkbox(a.isCloseAll()) + "Close All Windows";
        if (action instanceof CommitChangesAction a) return commitChangesOf(a, qr);
        if (action instanceof ExitGuideAction a) return checkbox(a.isCloseAll()) + "Close all guides on exit";
        // Java port of DocAlActionStruct.cpp's ActionSql/DocFilterActionStruct.cpp's equivalent -
        // the command text goes through the same field-substitution pass as Run Process/SetFields
        // SQL (TextFindFields), previously missing here entirely (plain WebUtil.validate, no
        // "$fieldId$" resolution at all) - a real content gap, not just a labeling one.
        if (action instanceof DirectSqlAction a) return sqlServerPrefix(a.getServer()) + "<code>" + TextFieldSubstitution.substitute(a.getCommand(), primaryForm, qr, qualDetail("Value in Direct SQL", qr)) + "</code>";
        if (action instanceof OleAutomationAction a) return oleAutomation(a);
        if (action instanceof RunMacroAction a) return "Macro " + WebUtil.validate(nullToEmpty(a.getMacroName()));
        if (action instanceof GotoGuideLabelAction a) return "Goto label " + (hasText(a.getLabel()) ? WebUtil.validate(a.getLabel()) : "(null)");
        if (action instanceof GotoAction a) return gotoOf(a);
        // Java port of DocAlActionStruct.cpp's ActionWait - was conditionally omitted entirely when
        // empty (real C++ always shows this row, "(null)" included) under a different label
        // ("Continue button:" vs the real "Label for Continue Button:").
        if (action instanceof WaitAction a) return "Label for Continue Button: " + (hasText(a.getContinueButtonTitle()) ? WebUtil.validate(a.getContinueButtonTitle()) : "(null)");
        if (action instanceof ServiceAction a) return serviceOf(a, primaryForm, qr, currentServerName);
        if (action instanceof DSOAction a) return dsoOf(a);
        return "";
    }

    /** "@" is AR System's literal token for "current server" (see fieldRefOf's javadoc) - omit the prefix entirely for that case rather than printing a meaningless "@: ". */
    private static String sqlServerPrefix(String server) {
        return server != null && !server.isEmpty() && !server.equals("@") ? WebUtil.validate(server) + ": " : "";
    }

    /**
     * Java port of DocAlMessageAction.cpp/DocFilterActionStruct.cpp's FilterActionMessage - Message
     * Number/Type/Text, with real "$fieldId$" field substitution. showPromptingPaneCheckbox is
     * Active-Link-only (FilterMessageAction has no
     * such field) - the C++ additionally gates this behind a server-version check
     * (CompareServerVersion(7,6)) this port doesn't replicate (no server-version context is
     * threaded into ActionSummaryTable), so it always shows once server version 7.6+ features start
     * being exposed - a small, accepted simplification, not a data-correctness issue. The checkbox
     * markup itself is copied byte-for-byte from the C++ (name/value attributes, no "disabled") since
     * it's a real, different shape from every other checkbox() call in this file.
     */
    private static String messageActionOf(int messageNum, int messageType, String messageText, boolean isActiveLink, boolean usePromptingPane, String primaryForm, QualificationRenderer qr) {
        StringBuilder sb = new StringBuilder();
        sb.append("Message Number: ").append(messageNum).append("<br/>");
        sb.append("Message Type: ").append(AREnumLabels.messageType(messageType)).append("<br/>");
        if (messageText != null && !messageText.isEmpty()) {
            sb.append("Message Text:<br/>").append(TextFieldSubstitution.substitute(messageText, primaryForm, qr, qualDetail("Message", qr))).append("<br/>");
        }
        if (isActiveLink) {
            sb.append("<input type=\"checkbox\" name=\"showInPromptPane\" value=\"showInPromptPane\"").append(usePromptingPane ? " checked" : "").append(">Show Message in Prompt Bar<br/>");
        }
        return sb.toString();
    }

    /**
     * Java port of DocFilterActionStruct.cpp's FilterActionNotify - Notify Text/User Name/Priority/
     * Mechanism always shown, then (when the mechanism isn't the plain Notifier/Alert delivery,
     * matching AR_NOTIFY_VIA_NOTIFIER) Subject + the resolved Include-Fields list + the advanced
     * mail-routing block (Mailbox/From/ReplyTo/CC/BCC/Organisation/Header/Content/Footer templates) -
     * previously this port rendered only "User: Text" with no field substitution, no priority/
     * mechanism, and none of the advanced fields at all. NotifyAction's Java API shape flattens the
     * C++'s separate nullable ARFilterActionNotifyAdvanced sub-struct into plain nullable fields
     * directly on NotifyAction - anyAdvanced tracks whether any of them are present, standing in for
     * the C++'s "notifyAdvanced != NULL" struct-existence check (which gates the whole block,
     * including its leading blank line, not just individual fields).
     */
    private static String notifyOf(NotifyAction a, String primaryForm, QualificationRenderer qr) {
        StringBuilder sb = new StringBuilder();
        sb.append("Notify Text: ").append(hasText(a.getNotifyText())
            ? TextFieldSubstitution.substitute(a.getNotifyText(), primaryForm, qr, "Field in Notify Action (Text)") : "(null)").append("<br/>");
        sb.append("User Name: ").append(hasText(a.getUser())
            ? TextFieldSubstitution.substitute(a.getUser(), primaryForm, qr, "Field in Notify Action (User Name)") : "(null)").append("<br/>");
        sb.append("Priority: ").append(a.getNotifyPriority()).append("<br/>");
        sb.append("Mechanism: ").append(AREnumLabels.notifyMechanism(a.getNotifyMechanism())).append("<br/>");

        if (a.getNotifyMechanism() != Constants.AR_NOTIFY_VIA_NOTIFIER) {
            if (hasText(a.getSubjectText())) {
                sb.append("Subject: ").append(TextFieldSubstitution.substitute(a.getSubjectText(), primaryForm, qr, "Field in Notify Action (Subject)")).append("<br/>");
            }

            sb.append("<br/>Include Fields: ").append(AREnumLabels.notifyFieldList(a.getFieldIdListType())).append("<br/>");
            if (a.getFieldIdList() != null) {
                for (Integer fieldId : a.getFieldIdList()) {
                    sb.append(qr != null ? qr.fieldRef(primaryForm, fieldId, "Field in Notify Action (Field List)") : "Field " + fieldId).append("<br/>");
                }
            }

            // Real live data confirmed this jar's NotifyAction returns "" (not null) for every one
            // of these 9 fields when the advanced mail-routing block simply wasn't configured - a
            // plain `!= null` check (matching the C++'s notifyAdvanced-pointer-is-NULL gate
            // literally) left every Notify action showing all 9 rows blank. hasText() (non-null AND
            // non-empty) is the correct translation of "was this sub-struct really provided" for
            // this jar's flattened, always-non-null-string shape.
            boolean anyAdvanced = hasText(a.getMailboxName()) || hasText(a.getFrom()) || hasText(a.getReplyTo()) || hasText(a.getCc())
                || hasText(a.getBcc()) || hasText(a.getOrganization()) || hasText(a.getHeaderTemplate())
                || hasText(a.getContentTemplate()) || hasText(a.getFooterTemplate());
            if (anyAdvanced) {
                sb.append("<br/><br/>");
                if (hasText(a.getMailboxName())) sb.append("Mailbox Name: ").append(TextFieldSubstitution.substitute(a.getMailboxName(), primaryForm, qr, "Field in Notify Action (Mailbox Name)")).append("<br/>");
                if (hasText(a.getFrom())) sb.append("From: ").append(TextFieldSubstitution.substitute(a.getFrom(), primaryForm, qr, "Field in Notify Action (From)")).append("<br/>");
                if (hasText(a.getReplyTo())) sb.append("Reply To: ").append(TextFieldSubstitution.substitute(a.getReplyTo(), primaryForm, qr, "Field in Notify Action (Reply To)")).append("<br/>");
                if (hasText(a.getCc())) sb.append("CC: ").append(TextFieldSubstitution.substitute(a.getCc(), primaryForm, qr, "Field in Notify Action (CC)")).append("<br/>");
                if (hasText(a.getBcc())) sb.append("BCC: ").append(TextFieldSubstitution.substitute(a.getBcc(), primaryForm, qr, "Field in Notify Action (BCC)")).append("<br/>");
                if (hasText(a.getOrganization())) sb.append("Organisation: ").append(TextFieldSubstitution.substitute(a.getOrganization(), primaryForm, qr, "Field in Notify Action (Organisation)")).append("<br/>");
                if (hasText(a.getHeaderTemplate())) sb.append("Header Template: ").append(TextFieldSubstitution.substitute(a.getHeaderTemplate(), primaryForm, qr, "Field in Notify Action (Header Template)")).append("<br/>");
                if (hasText(a.getContentTemplate())) sb.append("Content Template: ").append(TextFieldSubstitution.substitute(a.getContentTemplate(), primaryForm, qr, "Field in Notify Action (Content Template)")).append("<br/>");
                if (hasText(a.getFooterTemplate())) sb.append("Footer Template: ").append(TextFieldSubstitution.substitute(a.getFooterTemplate(), primaryForm, qr, "Field in Notify Action (Footer Template)")).append("<br/>");
            }
        }
        return sb.toString();
    }

    /**
     * Java port of DocAlActionStruct.cpp's ActionSetChar (139 lines) - renders field + menu plus
     * all real sub-fields (access/font/visibility/focus/label color/refresh-table-field/field
     * label). All of these come from ChangeFieldAction's own getOption()/getAccessOption()/
     * getFocus()/getProps() (a DisplayPropertyMap of AR_DPROP_* - font/visibility/color/refresh/
     * label are display-property overrides, not plain fields).
     */
    private static String changeFieldOf(ChangeFieldAction a, String primaryForm, QualificationRenderer qr) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        String field = qr != null ? qr.fieldRef(primaryForm, a.getFieldId(), qualDetail("Change Field", qr)) : "Field " + a.getFieldId();
        StringBuilder sb = new StringBuilder();
        if (a.getOption() == Constants.AR_FIELD_CHAR_OPTION_REFERENCE) {
            sb.append("Field (Use Value Of): $").append(field).append("$<br/>");
        } else {
            sb.append("Field Name: ").append(field).append("<br/>");
        }
        sb.append("Field Access: ").append(setCharFieldAccess(a.getAccessOption())).append("<br/>");

        DisplayPropertyMap props = a.getProps();
        Value fontVal = props == null ? null : props.get(Constants.AR_DPROP_LABEL_FONT_STYLE);
        sb.append("Field Font: ").append(fontVal != null && fontVal.getValue() instanceof String s ? WebUtil.validate(s) : "Unchanged").append("<br/>");

        Value visVal = props == null ? null : props.get(Constants.AR_DPROP_VISIBLE);
        sb.append("Visibility: ").append(visVal != null && visVal.getValue() instanceof Number n ? setCharFieldVisibility(n.intValue()) : "Unchanged").append("<br/>");

        if (a.getCharMenu() != null && !a.getCharMenu().isEmpty()) {
            sb.append("Menu: ").append(URLLink.to(a.getCharMenu(), Naming.menuDetail(a.getCharMenu(), false), ImageTag.Id.Menu, rootLevel).toHtml()).append("<br/>");
        }
        if (a.getFocus() == Constants.AR_FOCUS_SET_TO_FIELD) {
            sb.append("<input type=\"checkbox\" checked disabled/>Set Focus to Field<br/>");
        }

        if (props != null) {
            Value colorVal = props.get(Constants.AR_DPROP_LABEL_COLOR_TEXT);
            if (colorVal != null) sb.append("Label Color: ").append(labelColorSwatch(colorVal)).append("<br/>");
            if (props.get(Constants.AR_DPROP_REFRESH) != null) {
                sb.append("<input type=\"checkbox\" checked disabled/>Refresh Table Field<br/>");
            }
            Value labelVal = props.get(Constants.AR_DPROP_LABEL);
            if (labelVal != null && labelVal.getValue() instanceof String s && !s.isEmpty()) {
                sb.append("Field Label: ").append(TextFieldSubstitution.substitute(s, primaryForm, qr, qualDetail("Change Field Label", qr))).append("<br/>");
            }
        }
        return sb.toString();
    }

    private static String setCharFieldAccess(int accessOption) {
        if (accessOption == Constants.AR_DVAL_ENABLE_READ_ONLY) return "Read Only";
        if (accessOption == Constants.AR_DVAL_ENABLE_READ_WRITE) return "Read/Write";
        if (accessOption == Constants.AR_DVAL_ENABLE_DISABLE) return "Disabled";
        return "Unchanged";
    }

    private static String setCharFieldVisibility(int visibility) {
        if (visibility == 0) return "Hidden";
        if (visibility == 1) return "Visible";
        return "Unchanged";
    }

    /** Java port of ActionSetChar's AR_DPROP_LABEL_COLOR_TEXT handling - a "0xAABBCC"-style COLORREF string reordered into CSS "#CCBBAA" (BGR->RGB byte swap), rendered as a small color swatch; falls back to the raw value's text form when it isn't that exact 8-char "0x"-prefixed shape. */
    private static String labelColorSwatch(Value colorVal) {
        Object raw = colorVal.getValue();
        if (!(raw instanceof String s) || s.isEmpty()) return "Default";
        if (s.length() == 8 && s.startsWith("0x")) {
            String css = "" + s.charAt(6) + s.charAt(7) + s.charAt(4) + s.charAt(5) + s.charAt(2) + s.charAt(3);
            return "<span style='background-color:#" + css + "; width:16px; height:16px;'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>";
        }
        return WebUtil.validate(s);
    }

    /** Java port of DocAlActionStruct.cpp's ActionAutomation - Action/Server Name/CLS Id lines plus an Id+Name method table. */
    private static String oleAutomation(OleAutomationAction a) {
        StringBuilder sb = new StringBuilder();
        if (a.getAction() != null && !a.getAction().isEmpty()) sb.append("Action: ").append(WebUtil.validate(a.getAction())).append("<br/>");
        if (a.getAutoServerName() != null && !a.getAutoServerName().isEmpty()) sb.append("Server Name: ").append(WebUtil.validate(a.getAutoServerName())).append("<br/>");
        if (a.getClsId() != null && !a.getClsId().isEmpty()) sb.append("CLS Id: ").append(WebUtil.validate(a.getClsId())).append("<br/>");
        List<COMMethodInfo> methods = a.getMethodList();
        if (methods != null && !methods.isEmpty()) {
            Table tbl = new Table("tblOleMethods", "TblObjectList");
            tbl.addColumn(30, "Id");
            tbl.addColumn(30, "Name");
            for (COMMethodInfo m : methods) {
                tbl.addRow(new TableRow().addCellList(nullToEmpty(m.getMethodIId()), nullToEmpty(m.getMethodName())));
            }
            sb.append(tbl.toXHtml());
        }
        return sb.toString();
    }

    private static String checkbox(boolean checked) {
        return "<input type=\"checkbox\"" + (checked ? " checked" : "") + " disabled/>";
    }

    /**
     * Java port of core/ARAssignHelper.cpp's AssignValue. Quoting matches the original tool's own
     * rules: double-quotes for CHAR/enum labels, no quotes at all for REAL/ULONG numbers, and a
     * KEYWORD value ($NEWLINE$ etc.) renders as "$value$" rather than being quoted. An INTEGER/ENUM
     * literal being assigned to an enum-typed target field resolves to that field's real enum label
     * ("Yes") via GlobalFieldIndex.enumLabel(targetForm, targetFieldId, intVal), falling back to the
     * raw int only when the field isn't a recognized enum (or targetFieldId is -1, e.g. a Filter API
     * positional input with no target field at all) - matching the C++'s "print the raw int if
     * GetFieldEnumValue comes back empty" fallback.
     */
    private static String assignValueOf(Value value, String enumForm, int targetFieldId, QualificationRenderer qr) {
        DataType type = value.getDataType();
        Object v = value.getValue();
        if (type == DataType.NULL || v == null) return "$NULL$";
        if (type == DataType.CHAR) return "\"" + WebUtil.validate(String.valueOf(v)) + "\"";
        if (type == DataType.REAL || type == DataType.ULONG) return String.valueOf(v);
        if (type == DataType.KEYWORD) return "$" + v + "$";
        if ((type == DataType.INTEGER || type == DataType.ENUM) && v instanceof Integer intVal) {
            GlobalFieldIndex fieldIndex = qr == null ? null : qr.fieldIndex();
            String label = targetFieldId >= 0 && fieldIndex != null ? fieldIndex.enumLabel(enumForm, targetFieldId, intVal) : null;
            return label != null ? "\"" + WebUtil.validate(label) + "\"" : String.valueOf(intVal);
        }
        return "\"" + WebUtil.validate(String.valueOf(v)) + "\"";
    }

    /** Java port of DocAlActionStruct.cpp's ActionCommitChanges - schema link plus the same static explanatory paragraph the C++ always shows (there's nothing object-specific left to render beyond the target form). */
    private static String commitChangesOf(CommitChangesAction a, QualificationRenderer qr) {
        return "Schema: " + schemaLink(a.getFormName(), qr) + "<br/>"
            + "The Commit Changes action is applicable to regular form, join form or dialog box. "
            + "When the Open Window active link action is set to Dialog Window Type Commit Changes pushes predetermined values to fields in the parent form. "
            + "The values for these fields are specified in the Open Window active link action when the Field Mapping Mode is set to On Close. "
            + "When called within a regular form or join form, this action applies the changes.";
    }

    /**
     * Java port of DocAlActionStruct.cpp's ActionCallGuide - Server (with "$fieldId$" runtime-
     * substitution support, same shape as resolveServerRef) plus Table Loop/Table Field when
     * guideTableId &gt; 0. The guide name itself stays plain (unlinked) text rather than the C++'s
     * LinkToContainer - CallGuideAction is shared by both Active Link (Guide) and Filter (Filter
     * Guide) callers via the same generic detailOf() dispatch, and nothing at this call site says
     * which of the two container types the name refers to, so a Naming.containerDetail(...) link
     * would have a 50% chance of pointing at the wrong container type.
     */
    private static String callGuideOf(CallGuideAction a, String primaryForm, QualificationRenderer qr, String currentServerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Server: ").append(dollarFieldOrServerLink(a.getServerName(), a.getSampleServer(), currentServerName, primaryForm, qr, qualDetail("Used as CallGuide Server in", qr))).append("<br/>");
        sb.append("Guide: ").append(dollarFieldOrText(a.getGuideName(), primaryForm, qr, qualDetail("Used as CallGuide Name in", qr))).append("<br/>");
        if (a.getGuideTableId() > 0) {
            sb.append("Table Loop: ").append(AREnumLabels.callGuideMode(a.getGuideMode())).append("<br/>");
            sb.append("Table Field: ").append(qr != null ? qr.fieldRef(primaryForm, a.getGuideTableId(), qualDetail("Guide Table Loop", qr)) : "Field " + a.getGuideTableId());
        }
        return sb.toString();
    }

    /**
     * The "$fieldId" runtime-substitution literal AR System uses for CallGuideAction's
     * serverName/guideName and several OpenWindowAction fields - a negative id is a keyword, a
     * positive one a real field on the calling form; anything else is plain literal text. Strips an
     * optional trailing "$" before parsing - most of these raw fields use a single-leading-"$"
     * convention, but OpenWindowAction.ReportInfo's string fields (getType/getName/getDestination/
     * etc.) come back from the jar as the fully-delimited "$fieldId$" display form instead.
     */
    private static String dollarFieldOrText(String raw, String primaryForm, QualificationRenderer qr, String detail) {
        if (raw != null && raw.length() > 1 && raw.charAt(0) == '$') {
            String trimmed = raw.endsWith("$") && raw.length() > 2 ? raw.substring(0, raw.length() - 1) : raw;
            Integer fieldId = parseSampleFieldId(trimmed);
            if (fieldId != null) {
                String inner = fieldId < 0 ? ARKeywordLabels.forFieldId(fieldId) : (qr != null ? qr.fieldRef(primaryForm, fieldId, detail) : "Field " + fieldId);
                return "$" + inner + "$";
            }
        }
        return WebUtil.validate(nullToEmpty(raw));
    }

    private static String gotoOf(GotoAction a) {
        return switch (a.getTag()) {
            case GotoAction.AR_GOTO_FIELD_XREF -> "Goto field " + a.getFieldIdOrValue();
            case GotoAction.AR_GOTO_ABSOLUTE_ORDER -> "Goto step " + a.getFieldIdOrValue();
            case GotoAction.AR_GOTO_OFFSET_FORWARD -> "Goto +" + a.getFieldIdOrValue() + " steps";
            case GotoAction.AR_GOTO_OFFSET_BACKWARD -> "Goto -" + a.getFieldIdOrValue() + " steps";
            default -> "Goto " + a.getFieldIdOrValue();
        };
    }

    /**
     * Java port of DocAlActionStruct.cpp's ActionService/DocFilterActionStruct.cpp's equivalent -
     * renders Server Name (with the same "$field$ (Sample Server: link)" indirection every other
     * action type gets), a linked Request Id field, and Input/Output Mapping as real 2-column
     * tables. Also registers a REFM_SERVICE_CALL schema reference on the resolved service form -
     * see SchemaReferenceIndex's javadoc.
     */
    private static String serviceOf(ServiceAction a, String primaryForm, QualificationRenderer qr, String currentServerName) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        StringBuilder sb = new StringBuilder("<p>Server Name: ")
            .append(resolveServerRef(a.getServerName(), a.getSampleServer(), currentServerName, rootLevel, primaryForm, qr, qualDetail("Used as Service Server in", qr))).append("<br/>");
        String serviceForm = resolveFormRef(a.getServiceForm(), a.getSampleForm(), primaryForm, qr);
        sb.append("Service Form: ").append(formNameOf(a.getServiceForm(), a.getSampleForm(), serviceForm, primaryForm, qr, qualDetail("Used as Service Form in", qr))).append("<br/>");
        sb.append("Request Id: ");
        if (a.getRequestIdMap() != 0) sb.append(qr != null ? qr.fieldRef(primaryForm, a.getRequestIdMap(), qualDetail("Service Request-Id", qr)) : "Field " + a.getRequestIdMap());
        sb.append("</p>");

        if (qr != null && qr.schemaSink() != null) {
            qr.schemaSink().reference(serviceForm, QualificationRenderer.SchemaReferenceSink.Reason.SERVICE_CALL);
        }

        List<FieldAssignInfo> in = a.getInputFieldMapping();
        sb.append("Input Mapping: ").append(in == null || in.isEmpty() ? "None" : "").append("<br/>");
        if (in != null && !in.isEmpty()) sb.append(serviceFieldAssignments(in, serviceForm, primaryForm, qr, "Service Input Mapping"));

        List<FieldAssignInfo> out = a.getOutputFieldMapping();
        sb.append("Output Mapping: ").append(out == null || out.isEmpty() ? "None" : "").append("<br/>");
        if (out != null && !out.isEmpty()) sb.append(serviceFieldAssignments(out, primaryForm, serviceForm, qr, "Service Output Mapping"));

        return sb.toString();
    }

    /**
     * Java port of core/ARAssignHelper.cpp's ServiceAssignment - same 2-column table shape as
     * {@link #fieldAssignments}, but (matching the C++'s two-CARAssignHelper-instance construction,
     * one per direction) target and value resolve against two DIFFERENT, explicitly-passed forms
     * rather than a single primaryForm: Input Mapping's target is the service form / value is the
     * calling form, Output Mapping is the reverse. directionLabel is util/RefItem.cpp's ServiceInfo()
     * text ("Service Input Mapping"/"Service Output Mapping") - REFM_SERVICE_TARGET/VALUE both use
     * whichever direction applies, there's no separate REFM_* pair per direction.
     */
    private static String serviceFieldAssignments(List<FieldAssignInfo> list, String targetForm, String valueForm, QualificationRenderer qr, String directionLabel) {
        Table tbl = new Table("setFieldsList", "TblObjectList");
        tbl.addColumn(30, "Field Name");
        tbl.addColumn(70, "Value");
        String targetDetail = qualDetail("Target in '" + directionLabel + "'", qr);
        String valueDetail = qualDetail("Value in '" + directionLabel + "'", qr);
        for (FieldAssignInfo fai : list) {
            String target = qr != null ? qr.fieldRef(targetForm, fai.getFieldId(), targetDetail) : "Field " + fai.getFieldId();
            String value = assignmentOf(fai.getAssignmentType(), fai.getAssignment(), valueForm, qr, true, fai.getFieldId(), valueForm, valueDetail);
            tbl.addRow(new TableRow().addCellList(target, value));
        }
        return tbl.toXHtml();
    }

    private static String dsoOf(DSOAction a) {
        String type = switch (a.getType()) {
            case DSOAction.DSOACTION_TYPE_TRANSFER -> "Transfer";
            case DSOAction.DSOACTION_TYPE_RETURN -> "Return";
            case DSOAction.DSOACTION_TYPE_DELETE -> "Delete";
            default -> "DSO";
        };
        return type + " to " + WebUtil.validate(nullToEmpty(a.getServer())) + ":" + WebUtil.validate(nullToEmpty(a.getForm()));
    }

    /**
     * Java port of doc/actions/DocOpenWindowAction.cpp (~450 lines) - renders every real sub-field:
     * window/display type, target location, inline form, server/form/view with $field$-sample
     * indirection, the report sub-block, qualification, close-button/suppress-empty-list/
     * set-to-defaults checkboxes, the sort-order table, report extras, polling interval. Section
     * visibility is gated by window mode exactly like the C++'s ActionOpenDlg*() predicate family
     * (see the has*() helpers below).
     *
     * Unlike the C++'s MappingContext (which gives input mapping's *value*-side "@" a
     * different default form than its *target*-side), this only varies the target side (opened
     * form for input, primaryForm for output) - a field value genuinely marked cross-form still
     * resolves correctly either way, only the rarer bare "@"-on-input-value case could show the
     * wrong same-form default; same accepted single-form-bias simplification already documented
     * elsewhere in this file (see twoSchemaRenderer's callers).
     */
    private static String openWindow(OpenWindowAction a, String primaryForm, QualificationRenderer qr, String currentServerName) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        int rawMode = a.getWindowMode();
        int mode = AREnumLabels.openWindowModeMapped(rawMode);
        StringBuilder sb = new StringBuilder();

        sb.append("<p>Window Type: ").append(AREnumLabels.openWindowMode(mode));
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY) {
            sb.append("<br/>Display Type: ").append(AREnumLabels.openWindowDisplayType(rawMode));
        }
        if (hasTargetLocation(mode)) {
            sb.append("<br/>Target Location: ").append(targetLocationOf(a.getTargetLocation(), primaryForm, qr));
        }
        if (hasInlineForm(mode) && a.getReportInfo() != null) {
            sb.append("<br/>").append(checkbox(a.getReportInfo().isInlineForm())).append("Inline Form");
        }
        sb.append("</p>");

        sb.append("<p>Server Name: ").append(dollarFieldOrServerLink(a.getServerName(), a.getSampleServer(), currentServerName, primaryForm, qr, qualDetail("Window Open Server Name", qr))).append("<br/>");
        String targetSchema = resolveFormRef(a.getFormName(), a.getSampleForm(), primaryForm, qr);
        sb.append("Form Name: ").append(formNameOf(a.getFormName(), a.getSampleForm(), targetSchema, primaryForm, qr, qualDetail("Window Open Form Name", qr))).append("<br/>");
        sb.append("View Name: ").append(viewNameOf(a.getVuiLabel(), targetSchema, primaryForm, qr)).append("</p>");
        // Java port of DocOpenWindowAction.cpp's "add a used-as-open-window-schema reference"
        // (REFM_OPENWINDOW_FORM) - see SchemaReferenceIndex's javadoc.
        if (qr != null && qr.schemaSink() != null && qr.fieldIndex() != null && qr.fieldIndex().formExists(targetSchema)) {
            qr.schemaSink().reference(targetSchema, QualificationRenderer.SchemaReferenceSink.Reason.OPEN_WINDOW_TARGET);
        }

        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT) {
            sb.append(reportBlock(a.getReportInfo(), primaryForm, qr));
        }

        if (hasQualifier(mode) && a.getQuery() != null) {
            QualificationRenderer twoSchema = twoSchemaRenderer(primaryForm, targetSchema, qr);
            String qual = twoSchema != null ? twoSchema.render(a.getQuery(), qualDetail("Open Window Qualification", qr)) : "";
            sb.append("<p>Qualification:<br/>").append(qual).append("</p>");
        }

        sb.append("<p>");
        if (hasCloseButton(mode)) sb.append(checkbox(a.isCloseBox())).append("Show Close Button in Dialog<br/>");
        if (hasSuppressEmptyList(mode)) sb.append(checkbox(a.isSuppressEmptyLst())).append("Suppress Empty List<br/>");
        boolean setToDefault = a.isInputDefault();
        if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SEARCH || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SUBMIT) {
            sb.append(checkbox(setToDefault)).append("Set Fields To Defaults<br/>");
        }
        if (hasInputMapping(mode) && !setToDefault) {
            sb.append(fieldAssignments(a.getInputValueFieldPairs(), targetSchema, qr, "Target in 'Open Window'", "Value in 'Open Window'"));
        }
        if (hasOutputMapping(mode)) {
            // Output/close mapping fields are labeled "Close Window", not "Open Window" - they apply when the dialog closes.
            sb.append("On Dialog Close Action:<br/>").append(fieldAssignments(a.getOutputValueFieldPairs(), primaryForm, qr, "Target in 'Close Window'", "Value in 'Close Window'"));
        }
        sb.append("</p>");

        if (hasMessage(mode)) {
            sb.append("<p>If No Request Match: ");
            MessageAction msg = a.getMsg();
            if (a.isNoMatchContinue()) sb.append("Do not show any message");
            else if (msg == null || msg.getMessageText() == null || msg.getMessageText().isEmpty()) sb.append("Show default message");
            else if (msg.getMessageType() > 0) {
                // Java port of DocOpenWindowAction.cpp's ActionOpenDlgMessage block, which delegates
                // to DocAlMessageAction::ToStream for this case - the exact same Number/Type/Text
                // (+field substitution)/prompting-pane rendering as a real top-level Message action,
                // previously just an unresolved raw-escaped text dump here.
                sb.append("Show message<br/>").append(messageActionOf(msg.getMessageNum(), msg.getMessageType(), msg.getMessageText(), true, msg.isUsePromptingPane(), primaryForm, qr));
            }
            sb.append("</p>");

            if (a.getSortOrderList() != null && !a.getSortOrderList().isEmpty()) {
                sb.append("<p>Sort Order").append(sortOrderTable(a.getSortOrderList(), targetSchema, qr)).append("</p>");
            }

            if (mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT && a.getReportInfo() != null) {
                sb.append(reportExtras(a.getReportInfo(), primaryForm, qr));
            }

            if (hasPollingInterval(mode) && a.getPollinginterval() > 0) {
                sb.append("<p>Polling interval: ").append(a.getPollinginterval()).append("</p>");
            }
        }
        return sb.toString();
    }

    private static boolean hasTargetLocation(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SEARCH || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SUBMIT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT;
    }

    private static boolean hasInlineForm(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SEARCH || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SUBMIT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT;
    }

    private static boolean hasQualifier(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_SPLIT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_DETAIL || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY_SPLIT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_DETAIL
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT_SPLIT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_LST || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_DETAIL
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT_SPLIT;
    }

    private static boolean hasCloseButton(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DLG || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_POPUP;
    }

    private static boolean hasSuppressEmptyList(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT;
    }

    private static boolean hasInputMapping(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DLG || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SEARCH
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_SUBMIT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_POPUP;
    }

    private static boolean hasOutputMapping(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DLG || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_POPUP;
    }

    private static boolean hasMessage(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_REPORT;
    }

    private static boolean hasPollingInterval(int mode) {
        return mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DSPLY || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY
            || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_DISPLAY_DIRECT || mode == Constants.AR_ACTIVE_LINK_ACTION_OPEN_MODIFY_DIRECT;
    }

    /**
     * Target Location: "$fieldId" runtime-substitution (see dollarFieldOrText), a "VF&lt;n&gt;"
     * view-field reference (a field-id link on primaryForm whose display text is the original
     * "VF&lt;n&gt;" digits, not the field's real name - Java port of DocOpenWindowAction.cpp's
     * IsViewFieldReference branch), or plain literal text otherwise.
     */
    private static String targetLocationOf(String raw, String primaryForm, QualificationRenderer qr) {
        if (raw == null || raw.isEmpty()) return "";
        String detail = qualDetail("Window Open Location", qr);
        if (raw.charAt(0) == '$') return dollarFieldOrText(raw, primaryForm, qr, detail);
        if (qr != null && isViewFieldReference(raw)) {
            String digits = raw.substring(2);
            int fieldId = atoi(digits);
            if (fieldId > 0) return "VF" + qr.fieldRefWithText(primaryForm, fieldId, WebUtil.validate(digits), detail);
        }
        return WebUtil.validate(raw);
    }

    /** Java port of DocOpenWindowAction.cpp's IsViewFieldReference - "VF" followed by zero or more decimal digits and nothing else. */
    private static boolean isViewFieldReference(String s) {
        if (s == null || !s.startsWith("VF")) return false;
        for (int i = 2; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /** Server Name: like dollarFieldOrText, but the non-"$" branch links to this port's single ServerInfoPage (matching the C++'s LinkToServerInfo - there's only ever one connected server here) instead of plain text. */
    private static String dollarFieldOrServerLink(String raw, String sampleServer, String currentServerName, String primaryForm, QualificationRenderer qr, String detail) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        if (raw != null && raw.length() > 1 && raw.charAt(0) == '$') {
            Integer fieldId = parseSampleFieldId(raw);
            if (fieldId != null) {
                String inner = fieldId < 0 ? ARKeywordLabels.forFieldId(fieldId) : (qr != null ? qr.fieldRef(primaryForm, fieldId, detail) : "Field " + fieldId);
                return "$" + inner + "$ (Sample Server: " + serverInfoLink(sampleServer, currentServerName, rootLevel) + ")";
            }
        }
        return serverInfoLink(raw, currentServerName, rootLevel);
    }

    /**
     * An empty name or the "@" (current server/current screen) literal both link to this run's
     * actual connected server (AppConfig.serverName) with that real name/address as link text, not
     * the literal "@".
     */
    private static String serverInfoLink(String serverName, String currentServerName, int rootLevel) {
        if (serverName == null || serverName.isEmpty() || serverName.equals("@")) {
            if (currentServerName == null || currentServerName.isEmpty()) return "";
            return URLLink.to(currentServerName, Naming.serverInfo(), ImageTag.Id.NoImage, rootLevel).toHtml();
        }
        return URLLink.to(serverName, Naming.serverInfo(), ImageTag.Id.NoImage, rootLevel).toHtml();
    }

    /** Form Name: "$fieldId (Sample Schema: link)" vs a plain schemaLink - fieldId == -AR_KEYWORD_SCHEMA means "current form" (handled the same way resolveFormRef already resolves it for the internal targetSchema lookup). */
    private static String formNameOf(String raw, String sampleForm, String resolvedTargetSchema, String primaryForm, QualificationRenderer qr, String detail) {
        if (raw != null && raw.length() > 1 && raw.charAt(0) == '$') {
            Integer fieldId = parseSampleFieldId(raw);
            if (fieldId != null) {
                String inner = fieldId < 0 ? ARKeywordLabels.forFieldId(fieldId) : (qr != null ? qr.fieldRef(primaryForm, fieldId, detail) : "Field " + fieldId);
                return "$" + inner + "$ (Sample Schema: " + schemaLink(resolvedTargetSchema, qr) + ")";
            }
        }
        return schemaLink(resolvedTargetSchema, qr);
    }

    /**
     * View Name: "$fieldId$" runtime-substitution, "(Clear)" for an explicit empty VUI, or a real
     * hyperlink to the named VUI on the resolved target schema - Java port of
     * DocOpenWindowAction.cpp's View Name block (the CARVui-exists branch). Falls back to plain
     * text when the VUI name isn't found on that schema, matching this port's established
     * missing-reference simplification (plain text instead of the C++'s styled "not found" span).
     */
    private static String viewNameOf(String vuiLabel, String targetSchema, String primaryForm, QualificationRenderer qr) {
        if (vuiLabel != null && vuiLabel.length() > 1 && vuiLabel.charAt(0) == '$') {
            return dollarFieldOrText(vuiLabel, primaryForm, qr, qualDetail("Window Open View Name", qr));
        }
        if (vuiLabel == null || vuiLabel.isEmpty()) return "(Clear)";
        GlobalFieldIndex fieldIndex = qr == null ? null : qr.fieldIndex();
        Integer vuiId = fieldIndex == null ? null : fieldIndex.vuiId(targetSchema, vuiLabel);
        if (vuiId != null) {
            boolean isOverlaid = fieldIndex.isOverlaid(targetSchema);
            return URLLink.to(vuiLabel, Naming.schemaVuiDetail(targetSchema, isOverlaid, vuiId), ImageTag.Id.NoImage, qr.rootLevel()).toHtml();
        }
        return WebUtil.validate(vuiLabel);
    }

    /** Java port of DocOpenWindowAction.cpp's report-details block (windowMode == OPEN_REPORT only) - Report Type/Location/Name/Destination, sourced from OpenWindowAction.ReportInfo (already parsed by the jar, unlike the C++'s hand-rolled OpenWindowReportData string parser). */
    private static String reportBlock(OpenWindowAction.ReportInfo r, String primaryForm, QualificationRenderer qr) {
        if (r == null) return "<p>" + WebUtil.validate("Could not load report informations!") + "</p>";
        StringBuilder sb = new StringBuilder("<p>Report Type: ").append(dollarFieldOrText(r.getType(), primaryForm, qr, qualDetail("Window Open Report Type", qr))).append("<br/>");
        sb.append("Report Location: ").append(AREnumLabels.reportLocation(r.getLocation())).append("<br/>");
        sb.append("Report Name: ").append(dollarFieldOrText(r.getName(), primaryForm, qr, qualDetail("Window Open Report Name", qr))).append("<br/>");
        sb.append("Report Destination: ").append(dollarFieldOrText(r.getDestination(), primaryForm, qr, qualDetail("Window Open Report Destination", qr))).append("</p>");
        return sb.toString();
    }

    /**
     * Report-specific message-block extras (EntryIDs/Query Override/Report Operation/Character
     * Encoding), shown only under hasMessage() alongside the sort-order table. Query Override
     * intentionally renders the literal text "null" when unset - matches the original C++ tool's
     * output, not a bug to normalize away.
     */
    private static String reportExtras(OpenWindowAction.ReportInfo r, String primaryForm, QualificationRenderer qr) {
        StringBuilder sb = new StringBuilder("<p>EntryIDs: ").append(dollarFieldOrText(r.getEntryIds(), primaryForm, qr, qualDetail("Window Open EntryIDs", qr))).append("<br/>");
        sb.append("Query Override: ").append(dollarFieldOrText(r.getQueryOverride(), primaryForm, qr, qualDetail("Window Open Query Override", qr))).append("<br/>");
        int op = 0;
        try { op = r.getOperation() == null || r.getOperation().isEmpty() ? 0 : Integer.parseInt(r.getOperation()); } catch (NumberFormatException ignored) {}
        if (op == 0) op = 2; // default to Run, matching the C++
        sb.append("Report Operation: ").append(AREnumLabels.reportOperation(op)).append("<br/>");
        sb.append("Character Encoding: ").append(dollarFieldOrText(r.getCharacterEncoding(), primaryForm, qr, qualDetail("Window Open Character Encoding", qr))).append("</p>");
        return sb.toString();
    }

    /**
     * Java port of DocOpenWindowAction.cpp's sort-order table (Field Name/Field Id/Field Type/Sort
     * Order), shown when the action has a non-empty sortOrderList. Field Type is left blank - no
     * field-datatype lookup is available from just a GlobalFieldIndex (it only resolves names, not
     * types), and threading a full SchemaSource in for one column wasn't worth it; Field Name/Id
     * still hyperlink correctly via QualificationRenderer.fieldRef.
     */
    private static String sortOrderTable(List<SortInfo> sortList, String targetSchema, QualificationRenderer qr) {
        Table tbl = new Table("sortList", "TblObjectList");
        tbl.addColumn(40, "Field Name");
        tbl.addColumn(15, "Field Id");
        tbl.addColumn(15, "Field Type");
        tbl.addColumn(30, "Sort Order");
        String detail = qualDetail("Open Window SortBy", qr);
        for (SortInfo s : sortList) {
            String field = qr != null ? qr.fieldRef(targetSchema, s.getFieldID(), detail) : "Field " + s.getFieldID();
            tbl.addRow(new TableRow().addCellList(field, String.valueOf(s.getFieldID()), "", AREnumLabels.schemaSortOrder(s.getSortOrder())));
        }
        return tbl.toXHtml();
    }

    /**
     * Dispatches a Set Fields action on its real Java subtype (SetFieldsFromForm/FromSQL/
     * FromCurrentScreen/FromFilterAPI/FromWebService/FromRESTWebService). Any type that reads from
     * somewhere other than the current screen renders the server/form being queried, a two-schema
     * "Set Field If" qualification (primaryForm=this action's own form, secondaryForm=the form being
     * read from), and the No-Match/Multi-Match behavior. FilterAPI gets the Plugin-Name/
     * Input-Mapping/Output-Mapping treatment (see filterApiPluginDetail). WebService gets the
     * XML-embedded-content-parsed WSDL/Operation/URI/Input-Output-Mapping treatment (see
     * webServiceDetail). RESTWebService gets its own treatment (see restWebServiceDetail).
     * AtriumOrchestrator isn't its own Java subtype - it's a SetFieldsFromWebService in disguise
     * (see isAtriumOrchestrator), detected and given a distinct rendering.
     */
    private static String setFieldsOf(SetFieldsAction a, String primaryForm, QualificationRenderer qr, String currentServerName) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        StringBuilder sb = new StringBuilder();
        if (a instanceof SetFieldsFromCurrentScreen || !(a instanceof SetFieldsFromForm) && !(a instanceof SetFieldsFromSQL)
            && !(a instanceof SetFieldsFromFilterAPI) && !(a instanceof SetFieldsFromWebService) && !(a instanceof SetFieldsFromRESTWebService)) {
            // "Current screen" still has a server (it's always this run's connected server), even
            // though there's no cross-form "From:" line to go with it.
            sb.append("Data Source: CURRENT SCREEN<br/>");
            sb.append("Server: ").append(serverInfoLink(null, currentServerName, rootLevel)).append("<br/>");
        } else if (a instanceof SetFieldsFromForm sf) {
            boolean sample = isSampleRef(sf.getFromServer());
            sb.append("Data Source: ").append(sample ? "SAMPLE DATA" : "SERVER").append("<br/>");
            String readForm = resolveFormRef(sf.getReadValuesFrom(), sf.getSampleForm(), primaryForm, qr);
            sb.append("Server: ").append(resolveServerRef(sf.getFromServer(), sf.getSampleServer(), currentServerName, rootLevel, primaryForm, qr, qualDetail("Server Name in 'SetFields'", qr))).append("<br/>");
            sb.append("From: ").append(schemaLink(readForm, qr)).append("<br/>");
            QualificationRenderer twoSchema = twoSchemaRenderer(primaryForm, readForm, qr);
            sb.append("<br/>Set Field If<br/>");
            String onlyIf = twoSchema != null && sf.getSetIfQualification() != null ? twoSchema.render(sf.getSetIfQualification(), qualDetail("Set Field If Qualification", qr)) : "";
            sb.append(onlyIf.isEmpty() ? WebUtil.EMPTY_VALUE : onlyIf).append("<br/><br/>");
            sb.append("If No Requests Match: ").append(AREnumLabels.noMatchOption(sf.getNoMatchOption())).append("<br/>");
            sb.append("If Multiple Requests Match: ").append(AREnumLabels.multiMatchOption(sf.getMultiMatchOption())).append("<br/><br/>");
        } else if (a instanceof SetFieldsFromSQL sql) {
            sb.append("Data Source: SQL<br/>");
            sb.append("Server: ").append(serverInfoLink(sql.getFromServer(), currentServerName, rootLevel)).append("<br/>");
            sb.append("<br/>Query:<br/>");
            String cmd = sql.getSqlCommand();
            sb.append(cmd == null || cmd.isEmpty() ? WebUtil.EMPTY_VALUE : "<code>" + TextFieldSubstitution.substitute(cmd, primaryForm, qr, qualDetail("SQL Set Field If Qualification", qr)) + "</code>").append("<br/><br/>");
            sb.append("If No Requests Match: ").append(AREnumLabels.noMatchOption(sql.getNoMatchOption())).append("<br/>");
            sb.append("If Multiple Requests Match: ").append(AREnumLabels.multiMatchOption(sql.getMultiMatchOption())).append("<br/><br/>");
        } else if (a instanceof SetFieldsFromRESTWebService fa) {
            sb.append("Data Source: REST WEBSERVICE<br/>");
            sb.append(restWebServiceDetail(fa, primaryForm, qr));
            return sb.toString();
        } else if (a instanceof SetFieldsFromWebService fa) {
            sb.append("Data Source: WEBSERVICE<br/>");
            sb.append(isAtriumOrchestrator(fa) ? atriumOrchestratorDetail(fa) : webServiceDetail(fa, qr));
            return sb.toString();
        } else if (a instanceof SetFieldsFromFilterAPI fa) {
            // Java port of DocActionSetFieldsHelper.cpp's SFT_FILTERAPI case - Plugin-Name/
            // Input-Mapping/Output-Mapping replace the generic Field Mapping table entirely
            // (useDefaultFieldMappingTable=false in the C++), not shown alongside it.
            sb.append("Data Source: FILTER API<br/>");
            sb.append(filterApiPluginDetail(fa, primaryForm, qr));
            return sb.toString();
        }
        sb.append("Field Mapping:<br/>").append(fieldAssignments(a.getSetFieldsList(), primaryForm, qr, "Target in 'Set Fields'", "Value in 'Set Fields'"));
        return sb.toString();
    }

    /**
     * Java port of DocActionSetFieldsHelper.cpp's SFT_FILTERAPI block - Plugin-Name (the same
     * underlying serviceName field as filterApiSourceDetail's "Service:" line, just relabeled and,
     * when it's a "$..." token, run through TextFieldSubstitution instead of shown as plain
     * literal text - matches the C++'s own substr(0,1)=="$" check), Input-Mapping (a "Position"/
     * "Value" table over the same positional AssignInfo list filterApiSourceDetail already reads,
     * ported from CARAssignHelper::FilterApiInputAssignment), and Output-Mapping (the ordinary
     * "Field Name"/"Value" setFieldsList table, ported from CARAssignHelper::SetFieldsAssignment -
     * the exact same table {@link #fieldAssignments} already builds for every other Set Fields data
     * source). Web Service gets its own real treatment - see {@link #webServiceDetail}. REST Web
     * Service (no C++ equivalent at all) keeps the older Service/Input summary (see
     * filterApiSourceDetail).
     */
    private static String filterApiPluginDetail(SetFieldsFromFilterAPI fa, String primaryForm, QualificationRenderer qr) {
        StringBuilder sb = new StringBuilder();
        String serviceName = fa.getServiceName();
        String pluginName = serviceName != null && serviceName.startsWith("$")
            ? TextFieldSubstitution.substitute(serviceName, primaryForm, qr, qualDetail("Plugin-Name in FilterAPI-Call", qr))
            : WebUtil.validate(nullToEmpty(serviceName));
        sb.append("Plugin-Name: ").append(pluginName).append("<br/><br/>");
        sb.append("Input-Mapping: <br/>").append(filterApiInputTable(fa.getInputAssignList(), primaryForm, qr));
        sb.append("Output-Mapping: <br/>").append(fieldAssignments(fa.getSetFieldsList(), primaryForm, qr, "Target in 'Set Fields'", "Value in 'Set Fields'"));
        return sb.toString();
    }

    /**
     * A "Web Service" Set Fields action (SetFieldsFromWebService) is ALSO how BMC Atrium
     * Orchestrator actions are stored - not a distinct class. The two are distinguished by checking
     * index 18 (WS_TYPE) against the literal string "BMC ATRIUM ORCHESTRATOR"; if it matches, the
     * same positional slots that otherwise hold WSDL Location/Operation/etc. (indices 4-11) instead
     * hold Atrium-specific config unrelated to WSDL (Alias Name at index 19, plus Grid Name, Mode,
     * Processes, Execute Mode, Username, Password whose exact index layout isn't mapped here). Only
     * the Alias Name is currently rendered.
     */
    private static boolean isAtriumOrchestrator(SetFieldsFromWebService fa) {
        return "BMC ATRIUM ORCHESTRATOR".equals(webServiceCharValue(fa.getInputAssignList(), 18));
    }

    private static String atriumOrchestratorDetail(SetFieldsFromWebService fa) {
        StringBuilder sb = new StringBuilder("Type: BMC Atrium Orchestrator<br/>");
        sb.append("Alias Name: ").append(webServiceValueText(fa.getInputAssignList(), 19)).append("<br/>");
        return sb.toString();
    }

    /**
     * A genuine Web Service Set Fields action stores its config as a positional input-assignment
     * list (the same shape every FilterAPI subtype exposes via getInputAssignList()); specific slots
     * hold CHAR values that are themselves small XML documents (WSDL/operation metadata, and the
     * input/output field-mapping trees). Index 6 is itself XML:
     * {@code <operation><inputMapping name="OperationName">...</inputMapping>...</operation>} - only
     * the inputMapping's own "name" attribute is used. Indices 9/10 are each
     * {@code <arDocMapping><formMapping><form formName="..."/>...<element name="..."><fieldMapping
     * arFieldId="N"/></element>...</formMapping></arDocMapping>} - walked recursively by
     * {@link #walkWebServiceMapping}, tracking the nearest enclosing element name (the "Element"
     * column) and form name (which schema the field id resolves against) as it descends.
     *
     * <p>Index layout: WS_URL=4, WS_NAME=5, WS_OPERATION_DOC=6, WS_ENDPOINT_URI=7, WS_TARGETNS=8,
     * WS_INPUT_MAP=9, WS_OUTPUT_MAP=10, WS_PORT=11. AUTH_TYPE(14) and three empty slots (15-17) are
     * never populated for a genuine WSDL-based action and aren't rendered here; see
     * isAtriumOrchestrator for what indices 18/19 are reserved for on this same class.
     */
    private static String webServiceDetail(SetFieldsFromWebService fa, QualificationRenderer qr) {
        List<AssignInfo> inputs = fa.getInputAssignList();
        StringBuilder sb = new StringBuilder();
        sb.append("WSDL Location: ").append(webServiceValueText(inputs, 4)).append("<br/>");
        sb.append("Web Service: ").append(webServiceValueText(inputs, 5)).append("<br/>");
        sb.append("Port: ").append(webServiceValueText(inputs, 11)).append("<br/>");
        sb.append("Operation: ").append(WebUtil.validate(nullToEmpty(webServiceOperationName(inputs)))).append("<br/>");
        sb.append("URI: ").append(webServiceValueText(inputs, 7)).append("<br/>");
        sb.append("URN: ").append(webServiceValueText(inputs, 8)).append("<br/>");

        if (inputs != null && inputs.size() > 9) {
            sb.append("<br/>Input Mapping: ").append(webServiceMappingTable(webServiceCharValue(inputs, 9), qr, "Web Service Set Fields Input Mapping")).append("<br/>");
        }
        if (inputs != null && inputs.size() > 10) {
            sb.append("Output Mapping: ").append(webServiceMappingTable(webServiceCharValue(inputs, 10), qr, "Web Service Set Fields Output Mapping"));
        }
        return sb.toString();
    }

    /**
     * SetFieldsFromRESTWebService (added to the AR Java API more recently than the other Set
     * Fields data sources) reads/writes the same positional getInputAssignList() every FilterAPI
     * subtype uses. Index layout: 0=Base URI, 1=Static Body, 2=Http Method, 3=Auth Type, 4=Auth
     * Param, 5=Custom Headers, 6=Content Type, 7=Query Param, 8=Path Param, 9=Request Mapping,
     * 10=Response Mapping, 11=Multipart Info - all plain CHAR values (unlike WebService, nothing
     * here is itself XML). Base URI/Static Body/Http Method/Auth Type/Content Type/Path Param are
     * plain strings. Auth Param/Custom Headers/Query Param/Multipart Info are each a
     * "key#COLSEP#value" row list joined by "#ARSEP#" (only the first two columns of each row are
     * read even though the encoder writes exactly two). Request/Response Mapping use the same
     * separators but with the richer 10-or-11-column {@link #REST_MAPPING_COLUMNS} row shape,
     * rendered as per-cell field hyperlinks (Field ID validates against the row's own Current Form;
     * Primary Key against Parent Form; Foreign Key and Distinguishing Key against Current Form
     * again). The literal string "null" marks an unset cell in the underlying encoding - rendered
     * as an empty cell here, not the literal text.
     *
     * <p>Base URI/Path Param/Static Body and the key-value tables' values (not their keys) are free
     * text typed directly into the editor, and get AR's classic "$fieldId$"/"$fieldName$"
     * keyword-substitution treatment via {@link TextFieldSubstitution} (same mechanism as Message
     * text/Run Process command lines).
     */
    private static String restWebServiceDetail(SetFieldsFromRESTWebService fa, String primaryForm, QualificationRenderer qr) {
        List<AssignInfo> inputs = fa.getInputAssignList();
        StringBuilder sb = new StringBuilder();
        // Base URI/Path Param/Static Body are free text typed directly into the editor - AR's
        // classic "$fieldId$"/"$fieldName$" keyword-substitution syntax applies here, same as
        // Message text/Run Process command lines. "Value in REST Web Service" is a clearly-labeled
        // placeholder detail string, not a ported reference label.
        String restDetail = qualDetail("Value in REST Web Service", qr);
        sb.append("Base URI: ").append(TextFieldSubstitution.substitute(webServiceCharValue(inputs, 0), primaryForm, qr, restDetail)).append("<br/>");
        sb.append("Path Param: ").append(TextFieldSubstitution.substitute(webServiceCharValue(inputs, 8), primaryForm, qr, restDetail)).append("<br/>");
        sb.append("Http Method: ").append(webServiceValueText(inputs, 2)).append("<br/>");
        sb.append("Auth Type: ").append(webServiceValueText(inputs, 3)).append("<br/>");
        sb.append("Content Type: ").append(webServiceValueText(inputs, 6)).append("<br/>");
        sb.append("Static Body: ").append(TextFieldSubstitution.substitute(webServiceCharValue(inputs, 1), primaryForm, qr, restDetail)).append("<br/>");
        sb.append("<br/>Auth Param: ").append(restKeyValueTable(webServiceCharValue(inputs, 4), primaryForm, qr));
        sb.append("Custom Headers: ").append(restKeyValueTable(webServiceCharValue(inputs, 5), primaryForm, qr));
        sb.append("Query Param: ").append(restKeyValueTable(webServiceCharValue(inputs, 7), primaryForm, qr));
        sb.append("Multipart Info: ").append(restKeyValueTable(webServiceCharValue(inputs, 11), primaryForm, qr));
        sb.append("<br/>Request Mapping: ").append(restMappingTable(webServiceCharValue(inputs, 9), qr));
        sb.append("Response Mapping: ").append(restMappingTable(webServiceCharValue(inputs, 10), qr));
        return sb.toString();
    }

    /** "key#COLSEP#value" rows joined by "#ARSEP#" - see restWebServiceDetail's javadoc. Values (not keys) get the same "$fieldId$" substitution as Base URI/Static Body. */
    private static String restKeyValueTable(String raw, String primaryForm, QualificationRenderer qr) {
        Table tbl = new Table("pushFieldsList", "TblObjectList");
        tbl.addColumn(30, "Key");
        tbl.addColumn(70, "Value");
        String detail = qualDetail("Value in REST Web Service", qr);
        if (raw != null && !raw.isEmpty()) {
            for (String row : raw.split("#ARSEP#")) {
                if (row.isEmpty()) continue;
                String[] cols = row.split("#COLSEP#", -1);
                if (cols.length >= 2) tbl.addRow(new TableRow().addCellList(restCell(cols[0]), TextFieldSubstitution.substitute(restRawCell(cols[1]), primaryForm, qr, detail)));
            }
        }
        return tbl.toXHtml();
    }

    private static final String[] REST_MAPPING_COLUMNS = {
        "JSON Key", "Current Form", "Parent Form", "Field ID", "Primary Key",
        "Foreign Key", "Distinguishing Key", "JSON Type", "Child Array Index", "Multipart Info", "DateTime Format"
    };

    /** Request/Response Mapping's richer row shape - see restWebServiceDetail's javadoc. */
    private static String restMappingTable(String raw, QualificationRenderer qr) {
        Table tbl = new Table("pushFieldsList", "TblObjectList");
        for (String col : REST_MAPPING_COLUMNS) tbl.addColumn(100 / REST_MAPPING_COLUMNS.length, col);
        if (raw != null && !raw.isEmpty()) {
            for (String row : raw.split("#ARSEP#")) {
                if (row.isEmpty()) continue;
                String[] cols = row.split("#COLSEP#", -1);
                if (cols.length < 10) continue;
                String currentForm = restRawCell(cols[1]);
                String parentForm = restRawCell(cols[2]);
                tbl.addRow(new TableRow().addCellList(
                    restCell(cols[0]),
                    restCell(cols[1]),
                    restCell(cols[2]),
                    restFieldCell(currentForm, cols[3], qr),
                    restFieldCell(parentForm, cols[4], qr),
                    restFieldCell(currentForm, cols[5], qr),
                    restFieldCell(currentForm, cols[6], qr),
                    restCell(cols[7]),
                    restCell(cols[8]),
                    restCell(cols[9]),
                    cols.length > 10 ? restCell(cols[10]) : ""));
            }
        }
        return tbl.toXHtml();
    }

    /** Raw (unvalidated) cell text, "" for the "null" sentinel - for building a form-name to resolve a sibling field cell against. */
    private static String restRawCell(String s) {
        return s == null || s.equals("null") ? "" : s;
    }

    /** Validated cell text, "" for the "null" sentinel (RestInputOutputMappingWidget.NULL_VALUE). */
    private static String restCell(String s) {
        return s == null || s.equals("null") ? "" : WebUtil.validate(s);
    }

    /** A field-id cell, hyperlinked against the given form when both are present and the id parses; falls back to plain text otherwise. */
    private static String restFieldCell(String form, String rawFieldId, QualificationRenderer qr) {
        if (rawFieldId == null || rawFieldId.equals("null") || rawFieldId.isEmpty()) return "";
        if (form == null || form.isEmpty()) return WebUtil.validate(rawFieldId);
        try {
            int fieldId = Integer.parseInt(rawFieldId.trim());
            return qr != null ? qr.fieldRef(form, fieldId) : "Field " + fieldId;
        } catch (NumberFormatException e) {
            return WebUtil.validate(rawFieldId);
        }
    }

    private static String webServiceValueText(List<AssignInfo> inputs, int index) {
        String raw = webServiceCharValue(inputs, index);
        return raw == null ? "" : WebUtil.validate(raw);
    }

    private static String webServiceCharValue(List<AssignInfo> inputs, int index) {
        if (inputs == null || inputs.size() <= index) return null;
        AssignInfo p = inputs.get(index);
        if (p == null || p.getValue() == null) return null;
        Object v = p.getValue().getValue();
        return v == null ? null : v.toString();
    }

    /**
     * The operation name lives on the root &lt;operation&gt; element's own "name" attribute, not on
     * the &lt;inputMapping&gt; child's "name" (a separate, per-parameter-mapping name) - falls back
     * to the inputMapping-based read only if the root element somehow has none.
     */
    private static String webServiceOperationName(List<AssignInfo> inputs) {
        String xml = webServiceCharValue(inputs, 6);
        if (xml == null || xml.isEmpty()) return "";
        try {
            Node operation = firstChildElement(parseXml(xml), "operation");
            String name = attr(operation, "name");
            if (name == null) name = attr(firstChildElement(operation, "inputMapping"), "name");
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    private static String webServiceMappingTable(String xml, QualificationRenderer qr, String detail) {
        Table tbl = new Table("pushFieldsList", "TblObjectList");
        tbl.addColumn(30, "Element");
        tbl.addColumn(70, "Field");
        if (xml != null && !xml.isEmpty()) {
            try {
                Node docMapping = firstChildElement(parseXml(xml), "arDocMapping");
                Node formMapping = firstChildElement(docMapping, "formMapping");
                if (formMapping != null) walkWebServiceMapping(formMapping, "", "", tbl, qr, detail);
            } catch (Exception e) {
                // matches the C++'s own tolerance for a malformed mapping blob - a real filter on
                // this server produced one already (WARN logged, table just ends up incomplete)
                System.out.println("WARN: parsing web service mapping XML failed: " + e.getMessage());
            }
        }
        return tbl.toXHtml();
    }

    /** Java port of DocActionSetFieldsHelper.cpp's processMappingXML - see webServiceDetail's javadoc. */
    private static void walkWebServiceMapping(Node node, String sParent, String form, Table tbl, QualificationRenderer qr, String detail) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) return;
        String tag = node.getNodeName();
        if ("element".equals(tag)) {
            String name = attr(node, "name");
            sParent = name == null ? "" : name;
        } else if ("formMapping".equals(tag)) {
            Node formChild = firstChildElement(node, "form");
            String formName = attr(formChild, "formName");
            form = formName == null ? "" : formName;
        } else if ("fieldMapping".equals(tag)) {
            int fieldId = atoi(nullToEmpty(attr(node, "arFieldId")));
            tbl.addRow(new TableRow().addCellList(WebUtil.validate(sParent), qr != null ? qr.fieldRef(form, fieldId, detail) : "Field " + fieldId));
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            walkWebServiceMapping(children.item(i), sParent, form, tbl, qr, detail);
        }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Node firstChildElement(Node parent, String tag) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE && tag.equals(c.getNodeName())) return c;
        }
        return null;
    }

    private static String attr(Node node, String name) {
        if (node == null || node.getAttributes() == null) return null;
        Node a = node.getAttributes().getNamedItem(name);
        return a == null ? null : a.getNodeValue();
    }

    /**
     * pushFieldValue=true is passed for a reason unrelated to "push": it makes assignmentOf's
     * AR_ASSIGN_TYPE_FIELD branch resolve a field id directly against primaryForm via
     * qr.fieldRef(), with no "(on server:form)" cross-form annotation - matching
     * CARAssignHelper::AssignField, which always resolves against a single schema
     * (schemaInsideId1, or schemaInsideId2 only when pushFieldFlag is false and the two differ) and
     * never reads/shows a per-field form/server, unlike fieldRefOf's annotation, which doesn't apply
     * here. FilterApiInputAssignment's caller constructs its CARAssignHelper with
     * schema1==schema2==the attached form, so plain primaryForm resolution is correct - no
     * "(on OtherForm)" suffix should appear for this table.
     */
    private static String filterApiInputTable(List<AssignInfo> inputs, String primaryForm, QualificationRenderer qr) {
        Table tbl = new Table("setFieldsList", "TblObjectList");
        tbl.addColumn(30, "Position");
        tbl.addColumn(70, "Value");
        String valueDetail = qualDetail("Value in FilterAPI-Input-Mapping", qr);
        if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                AssignInfo p = inputs.get(i);
                tbl.addRow(new TableRow().addCellList(Integer.toString(i + 1), assignmentOf(p.getAssignType(), p, primaryForm, qr, true, -1, primaryForm, valueDetail)));
            }
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of doc/DocAlActionStruct.cpp's ActionPushFields - Push Fields is always a
     * "query another form" action (no CURRENT-screen variant exists), so this always renders the
     * target server/form, a two-schema "Push Field If" qualification (primaryForm=this AL's own
     * form, secondaryForm=the resolved push-target form), and No-Match/Multi-Match - previously
     * missing entirely, same category of gap as setFieldsOf above.
     */
    private static String pushFieldsOf(PushFieldsAction a, String primaryForm, QualificationRenderer qr, String currentServerName) {
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        StringBuilder sb = new StringBuilder();
        String pushForm = resolveFormRef(a.getPushToForm(), a.getSampleForm(), primaryForm, qr);
        sb.append("Server Name: ").append(resolveServerRef(a.getPushToServer(), a.getSampleServer(), currentServerName, rootLevel, primaryForm, qr, qualDetail("Server Name in 'Push Fields'", qr))).append("<br/>");
        sb.append("Push Value To: ").append(schemaLink(pushForm, qr)).append("<br/>");
        if (qr != null && qr.schemaSink() != null) {
            qr.schemaSink().reference(pushForm, QualificationRenderer.SchemaReferenceSink.Reason.PUSH_FIELDS_TARGET);
        }
        QualificationRenderer twoSchema = twoSchemaRenderer(primaryForm, pushForm, qr);
        sb.append("<br/>Push Field If<br/>");
        String onlyIf = twoSchema != null && a.getPushIfQualification() != null ? twoSchema.render(a.getPushIfQualification(), qualDetail("Push Field If Qualification", qr)) : "";
        sb.append(onlyIf.isEmpty() ? WebUtil.EMPTY_VALUE : onlyIf).append("<br/><br/>");
        sb.append("If No Requests Match: ").append(AREnumLabels.noMatchOption(a.getNoMatchOption())).append("<br/>");
        sb.append("If Multiple Requests Match: ").append(AREnumLabels.multiMatchOption(a.getMultiMatchOption())).append("<br/><br/>");
        sb.append("Field Mapping:<br/>").append(pushFieldAssignments(a.getPushFieldsList(), primaryForm, pushForm, qr));
        return sb.toString();
    }

    private static boolean isSampleRef(String raw) {
        return raw != null && raw.startsWith("$");
    }

    /**
     * Java port of the "$fieldId (Sample Server: link)" vs plain-server-literal branch shared by
     * DocActionSetFieldsHelper's SFT_SAMPLEDATA case and DocAlActionStruct's Push Fields
     * target-server rendering. Now links to this port's ServerInfoPage via serverInfoLink (resolving
     * "@"/empty to the real connected server) - a live C++-vs-Java comparison of OpenWindowAction's
     * equivalent Server Name line found the real tool always links here (LinkToServerInfo), so the
     * "no ServerInfo hyperlink anywhere in this port" note this method used to carry was stale, not
     * a deliberate scope decision.
     */
    private static String resolveServerRef(String raw, String sampleServer, String currentServerName, int rootLevel, String primaryForm, QualificationRenderer qr, String detail) {
        if (!isSampleRef(raw)) return serverInfoLink(raw, currentServerName, rootLevel);
        Integer fieldId = parseSampleFieldId(raw);
        if (fieldId == null) return WebUtil.validate(raw);
        String fieldText = fieldId < 0 ? "$" + ARKeywordLabels.forFieldId(fieldId) + "$" : "$" + (qr != null ? qr.fieldRef(primaryForm, fieldId, detail) : "Field " + fieldId) + "$";
        return fieldText + " (Sample Server: " + serverInfoLink(sampleServer, currentServerName, rootLevel) + ")";
    }

    /**
     * Resolves a Set/Push Fields "read/push target form" reference to a plain form name, handling
     * the C++'s "@" (own form) literal and "$fieldId"-prefixed sample-field-indirection cases
     * (resolving to sampleForm, or ownForm when the encoded fieldId is -AR_KEYWORD_SCHEMA). Unlike
     * DocActionSetFieldsHelper.cpp's SFT_SAMPLEDATA case - which builds a compound "$field$ (Sample
     * Form: link)" HTML string and then passes that whole string back into LinkToSchema() as if it
     * were a schema name (an apparent quirk in the original, not deliberately replicated here) -
     * this always resolves to a clean, real form name that schemaLink() can link correctly.
     */
    private static String resolveFormRef(String raw, String sampleForm, String ownForm, QualificationRenderer qr) {
        if (raw != null && raw.equals("@")) return ownForm;
        if (!isSampleRef(raw)) return raw == null || raw.isEmpty() ? ownForm : raw;
        Integer fieldId = parseSampleFieldId(raw);
        if (fieldId == null) return ownForm;
        if (fieldId == -Constants.AR_KEYWORD_SCHEMA) return ownForm;
        return sampleForm == null || sampleForm.isEmpty() || sampleForm.equals("@") ? ownForm : sampleForm;
    }

    private static Integer parseSampleFieldId(String raw) {
        try {
            return Integer.parseInt(raw.substring(1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static QualificationRenderer twoSchemaRenderer(String primaryForm, String secondaryForm, QualificationRenderer qr) {
        return qr == null ? null : new QualificationRenderer(primaryForm, secondaryForm, qr.rootLevel(), qr.fieldIndex(), qr.sink());
    }

    /** Java port of REFM_SETFIELDS_QUALIFICATION/REFM_PUSHFIELD_IF/REFM_OPENWINDOW_QUALIFICATION's real "{label} {IfElse}-Action {N}" shape (util/RefItem.cpp) - the fresh twoSchemaRenderer instance these three call sites use has no currentActionSuffix of its own (see that field's javadoc), so the ORIGINAL qr's suffix (set once per action by addRows, still valid here since we're still inside that same action's rendering) is appended explicitly. */
    private static String qualDetail(String label, QualificationRenderer qr) {
        String suffix = qr == null ? "" : qr.currentActionSuffix();
        return suffix.isEmpty() ? label : label + " " + suffix;
    }

    private static String schemaLink(String formName, QualificationRenderer qr) {
        if (formName == null || formName.isEmpty()) return "";
        GlobalFieldIndex fieldIndex = qr == null ? null : qr.fieldIndex();
        boolean isOverlaid = fieldIndex != null && fieldIndex.isOverlaid(formName);
        int rootLevel = qr == null ? 1 : qr.rootLevel();
        return URLLink.to(formName, Naming.schemaDetail(formName, isOverlaid), ImageTag.Id.Schema, rootLevel).toHtml();
    }

    /**
     * Java port of core/ARAssignHelper.cpp's SetFieldsAssignment/OpenWindowAssignment/
     * ServiceAssignment - a real 2-column "Field Name"/"Value" table (id="setFieldsList"). Target
     * fields belong to the owning object's own form (primaryForm) - used for
     * setFields/openWindow/service field lists.
     */
    private static String fieldAssignments(List<FieldAssignInfo> list, String primaryForm, QualificationRenderer qr, String targetLabel, String valueLabel) {
        if (list == null || list.isEmpty()) return "";
        Table tbl = new Table("setFieldsList", "TblObjectList");
        tbl.addColumn(30, "Field Name");
        tbl.addColumn(70, "Value");
        String targetDetail = qualDetail(targetLabel, qr);
        String valueDetail = qualDetail(valueLabel, qr);
        for (FieldAssignInfo fai : list) {
            String target = qr != null ? qr.fieldRef(primaryForm, fai.getFieldId(), targetDetail) : "Field " + fai.getFieldId();
            String value = assignmentOf(fai.getAssignmentType(), fai.getAssignment(), primaryForm, qr, false, fai.getFieldId(), primaryForm, valueDetail);
            tbl.addRow(new TableRow().addCellList(target, value));
        }
        return tbl.toXHtml();
    }

    /**
     * Java port of core/ARAssignHelper.cpp's PushFieldsAssignment - same 2-column table
     * (id="pushFieldsList") as fieldAssignments. Push targets carry their own (possibly cross-form)
     * AssignFieldInfo - the field being pushed TO, not necessarily on primaryForm.
     */
    private static String pushFieldAssignments(List<PushFieldsInfo> list, String primaryForm, String pushForm, QualificationRenderer qr) {
        if (list == null || list.isEmpty()) return "";
        Table tbl = new Table("pushFieldsList", "TblObjectList");
        tbl.addColumn(30, "Field Name");
        tbl.addColumn(70, "Value");
        String targetDetail = qualDetail("Target in 'Push Fields'", qr);
        String valueDetail = qualDetail("Value in 'Push Fields'", qr);
        for (PushFieldsInfo pfi : list) {
            String target = pfi.getField() == null ? "Field ?" : fieldRefOf(pfi.getField(), primaryForm, qr, targetDetail);
            int targetFieldId = pfi.getField() == null ? -1 : pfi.getField().getFieldId();
            AssignInfo assign = pfi.getAssignment() != null ? pfi.getAssignment() : pfi.getAssign();
            // pushFieldValue=true: matches core/ARAssignHelper.cpp's AssignField, which for a Push
            // Fields action (pushFieldFlag) always resolves a value-side AR_ASSIGN_TYPE_FIELD
            // against the action's own primary/source form, ignoring AssignFieldInfo's server/form
            // entirely - the value side's literal "*"/"*" server/form (not "@") is not a genuine
            // cross-form pointer, and must not be treated as one.
            // AssignValue's own enum-lookup form is different again (matches AssignHelper's
            // schemaInsideId2 for pushFieldFlag=true) - the *push-target* form, not primaryForm -
            // passed separately as pushForm since AssignField/AssignValue disagree on which form to
            // use when pushFieldFlag is set.
            String value = assign == null ? "" : assignmentOf(assign.getAssignType(), assign, primaryForm, qr, true, targetFieldId, pushForm, valueDetail);
            tbl.addRow(new TableRow().addCellList(target, value));
        }
        return tbl.toXHtml();
    }

    /**
     * targetFieldId is the field this assignment's *result* is ultimately being stored into (-1
     * when there isn't one, e.g. a Filter API positional input) - threaded through every recursive
     * call (arithmetic/function operands included) purely so a literal AR_ASSIGN_TYPE_VALUE integer
     * deep inside the expression can still be resolved against it, matching
     * core/ARAssignHelper.cpp's CheckAssignment(targetFieldId, ...) recursion exactly. valueEnumForm
     * is the form AssignValue's own enum lookup runs against - normally primaryForm, but the
     * push-target form for a Push Fields value (see pushFieldAssignments' javadoc). detail is the
     * caller's real REFM_*_VALUE-shaped label (e.g. "Value in 'Set Fields' If-Action 3") - passed
     * unchanged through every recursive call (arithmetic/function operands, HOVER's field param)
     * since the real C++ doesn't relabel per expression-tree depth either, only per top-level
     * call site (see util/RefItem.cpp - there's no REFM_* variant for "field inside an arithmetic
     * sub-expression", it's still just REFM_SETFIELDS_VALUE etc.).
     */
    private static String assignmentOf(int assignmentType, AssignInfo assign, String primaryForm, QualificationRenderer qr, boolean pushFieldValue, int targetFieldId, String valueEnumForm, String detail) {
        if (assign == null) return "";
        if (assignmentType == Constants.AR_ASSIGN_TYPE_VALUE && assign.getValue() != null) {
            return assignValueOf(assign.getValue(), valueEnumForm, targetFieldId, qr);
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_FIELD && assign.getField() != null) {
            return pushFieldValue
                ? "$" + (qr != null ? qr.fieldRef(primaryForm, assign.getField().getFieldId(), detail) : "Field " + assign.getField().getFieldId()) + "$"
                : "$" + fieldRefOf(assign.getField(), primaryForm, qr, detail) + "$";
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_PROCESS && assign.getProcess() != null) {
            return "$PROCESS$ " + TextFieldSubstitution.substitute(assign.getProcess(), primaryForm, qr, detail);
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_ARITH && assign.getArithOp() != null) {
            return arithOf(assign.getArithOp(), primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail);
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_FUNCTION && assign.getFunction() != null) {
            return functionOf(assign.getFunction(), primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail);
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_DDE && assign.getDde() != null) {
            return ddeOf(assign.getDde());
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_SQL && assign.getSql() != null) {
            return "$" + assign.getSql().getValueIndex() + "$";
        }
        if (assignmentType == Constants.AR_ASSIGN_TYPE_FILTER_API && assign.getFilterApi() != null) {
            return "$" + assign.getFilterApi().getValueIndex() + "$";
        }
        return "(" + assignmentTypeName(assignmentType) + ")";
    }

    /**
     * Field id, plus cross-form (server:form) and currency-part detail when present -
     * AssignFieldInfo carries more than just a bare field id. AR System uses the literal token
     * "@" for both server and form to mean "current server"/"current form" ("@:@" for a same-form
     * reference, "@:OtherForm" for a genuine cross-form one), so the "(on ...)" suffix is only
     * shown when the form isn't just that placeholder. The field itself
     * is hyperlinked+tracked against whichever form it actually resolves to (primaryForm for a
     * same-form reference, the named form for a genuine cross-form one) via
     * QualificationRenderer.fieldRef, which already knows how to look up any named form, not just
     * the renderer's own configured one. NOT used for a Push Fields action's value-side field
     * assignment (see pushFieldAssignments/assignmentOf's pushFieldValue param) - that side's
     * server/form are a different, non-"@" placeholder pair in real data and must always resolve
     * to primaryForm regardless, matching the C++'s pushFieldFlag override; this method's
     * cross-form logic is still correct for push TARGETS (this method's other caller) and for
     * plain (non-push) Set Fields/Service/OpenWindow field-to-field assignments.
     */
    private static String fieldRefOf(AssignFieldInfo f, String primaryForm, QualificationRenderer qr, String detail) {
        boolean crossForm = f.getForm() != null && !f.getForm().isEmpty() && !f.getForm().equals("@");
        String targetForm = crossForm ? f.getForm() : primaryForm;
        StringBuilder sb = new StringBuilder(qr != null ? qr.fieldRef(targetForm, f.getFieldId(), detail) : "Field " + f.getFieldId());
        if (crossForm) {
            sb.insert(0, "").append(" (on ");
            if (f.getServer() != null && !f.getServer().isEmpty() && !f.getServer().equals("@")) sb.append(WebUtil.validate(f.getServer())).append(":");
            sb.append(WebUtil.validate(f.getForm())).append(")");
        }
        if (f.getCurrencyPart() != null) {
            sb.append(" [currency field ").append(f.getCurrencyPart().getFieldId()).append("]");
        }
        return sb.toString();
    }

    /**
     * Recursive - AR_ASSIGN_TYPE_ARITH's operands are themselves AssignInfo, not the
     * qualification-side ArithmeticOrRelationalOperand QualificationRenderer already handles, so
     * this is a separate small renderer rather than reusing that one. Java port of
     * ARAssignHelper.cpp's CheckAssignment AR_ASSIGN_TYPE_ARITH case + CAREnum::OperandPrecedence -
     * a nested arith operand is only parenthesized when needed to preserve evaluation order (parent
     * binds looser than child, or child is MODULO tied with its parent and sits on the right), not
     * unconditionally. pushFieldValue/targetFieldId/valueEnumForm thread down to nested operands -
     * see assignmentOf's javadoc note.
     */
    private static String arithOf(ArithOpAssignInfo op, String primaryForm, QualificationRenderer qr, boolean pushFieldValue, int targetFieldId, String valueEnumForm, String detail) {
        int operation = op.getOperation();
        if (operation == ArithmeticOperationInfo.AR_ARITH_OP_NEGATE) {
            return "-" + operandOf(op.getOperandRight(), operation, true, primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail);
        }
        return operandOf(op.getOperandLeft(), operation, false, primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail)
            + arithOpText(operation)
            + operandOf(op.getOperandRight(), operation, true, primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail);
    }

    /** parentOp/isRightOperand identify where this operand sits in its parent arith node - see arithOf's javadoc. */
    private static String operandOf(AssignInfo operand, int parentOp, boolean isRightOperand, String primaryForm, QualificationRenderer qr, boolean pushFieldValue, int targetFieldId, String valueEnumForm, String detail) {
        if (operand == null) return "";
        String rendered = assignmentOf(operand.getAssignType(), operand, primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail);
        if (operand.getAssignType() == Constants.AR_ASSIGN_TYPE_ARITH && operand.getArithOp() != null) {
            int childOp = operand.getArithOp().getOperation();
            int parentPrecedence = operandPrecedence(parentOp);
            int currentPrecedence = operandPrecedence(childOp);
            boolean addBracket = parentPrecedence < currentPrecedence
                || (childOp == ArithmeticOperationInfo.AR_ARITH_OP_MODULO && parentPrecedence == currentPrecedence && isRightOperand);
            if (addBracket) return "(" + rendered + ")";
        }
        return rendered;
    }

    /** Java port of CAREnum::OperandPrecedence - lower number binds tighter; unlisted ops (there are none besides these six) fall back to 99 (never bracketed). */
    private static int operandPrecedence(int op) {
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_ADD) return 4;
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_SUBTRACT) return 4;
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_MULTIPLY) return 3;
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_DIVIDE) return 3;
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_MODULO) return 3;
        if (op == ArithmeticOperationInfo.AR_ARITH_OP_NEGATE) return 7;
        return 99;
    }

    private static String arithOpText(int arithOp) {
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_ADD) return " + ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_SUBTRACT) return " - ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_MULTIPLY) return " * ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_DIVIDE) return " / ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_MODULO) return " mod ";
        return " ? ";
    }

    private static String functionOf(FunctionAssignInfo fn, String primaryForm, QualificationRenderer qr, boolean pushFieldValue, int targetFieldId, String valueEnumForm, String detail) {
        String name;
        try {
            name = FunctionAssignInfo.toFuncName(fn.getFunctionCode());
        } catch (ARException e) {
            name = "func" + fn.getFunctionCode();
        }
        StringBuilder sb = new StringBuilder(WebUtil.validate(name)).append("(");
        List<AssignInfo> params = fn.getParameterList();
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append(", ");
                AssignInfo p = params.get(i);
                String hover = i == 0 && fn.getFunctionCode() == Constants.AR_FUNCTION_HOVER ? hoverFieldOf(p, primaryForm, qr, detail) : null;
                sb.append(hover != null ? hover : assignmentOf(p.getAssignType(), p, primaryForm, qr, pushFieldValue, targetFieldId, valueEnumForm, detail));
            }
        }
        return sb.append(")").toString();
    }

    /**
     * Java port of ARAssignHelper.cpp's AssignFunction AR_FUNCTION_HOVER special case - HOVER()'s
     * first parameter is a field-id literal (given as an INTEGER value or a numeric CHAR string),
     * not an ordinary value: when it resolves to a positive field id on primaryForm, it renders as
     * a field-reference link whose display text is the original literal (not the target field's
     * real name). Returns null (caller falls back to the normal assignmentOf rendering) when the
     * parameter isn't a recognizable positive field-id literal - matches the C++ falling through to
     * its default CheckAssignment call in that case.
     */
    private static String hoverFieldOf(AssignInfo p, String primaryForm, QualificationRenderer qr, String detail) {
        if (qr == null || p == null || p.getAssignType() != Constants.AR_ASSIGN_TYPE_VALUE || p.getValue() == null) return null;
        Value v = p.getValue();
        int fieldId = 0;
        String displayText = null;
        if (v.getDataType() == DataType.INTEGER && v.getValue() != null) {
            fieldId = v.getIntValue();
            displayText = Integer.toString(fieldId);
        } else if (v.getDataType() == DataType.CHAR && v.getValue() != null) {
            String s = v.getValue().toString();
            if (!s.isEmpty()) {
                fieldId = atoi(s);
                displayText = "\"" + WebUtil.validate(s) + "\"";
            }
        }
        return fieldId > 0 ? qr.fieldRefWithText(primaryForm, fieldId, displayText, detail) : null;
    }

    /** C's atoi() semantics (leading whitespace, optional sign, then digits; 0 if none) - matches ARAssignHelper.cpp's use of atoi() on the HOVER field's CHAR literal. */
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

    /** Java port of core/ARAssignHelper.cpp's AssignDDE - Command/Item/Path to Program/Server Name/Topic, one per non-null field, in that exact order. */
    private static String ddeOf(DDEAction dde) {
        StringBuilder sb = new StringBuilder();
        if (dde.getCommand() != null) sb.append("Command: ").append(WebUtil.validate(dde.getCommand())).append("<br/>");
        if (dde.getItem() != null) sb.append("Item: ").append(WebUtil.validate(dde.getItem())).append("<br/>");
        if (dde.getPathToProgram() != null) sb.append("Path to Program: ").append(WebUtil.validate(dde.getPathToProgram())).append("<br/>");
        if (dde.getServiceName() != null) sb.append("Server Name: ").append(WebUtil.validate(dde.getServiceName())).append("<br/>");
        if (dde.getTopic() != null) sb.append("Topic: ").append(WebUtil.validate(dde.getTopic())).append("<br/>");
        return sb.toString();
    }

    private static String assignmentTypeName(int type) {
        if (type == Constants.AR_ASSIGN_TYPE_NONE) return "none";
        return "unset";
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Non-null AND non-empty - see notifyOf's javadoc for why a plain null-check isn't enough for this jar's NotifyAction fields. */
    private static boolean hasText(String s) { return s != null && !s.isEmpty(); }

    public static Function<ActiveLinkAction, Integer> activeLinkTypeOf() {
        return a -> Action.getActionType((Action) a, true);
    }

    public static Function<FilterAction, Integer> filterTypeOf() {
        return a -> Action.getActionType((Action) a, false);
    }

    public static Function<Integer, String> activeLinkLabel() {
        return AREnumLabels::activeLinkActionType;
    }

    public static Function<Integer, String> filterLabel() {
        return AREnumLabels::filterActionType;
    }
}
