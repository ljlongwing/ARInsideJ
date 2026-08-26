package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java port of the {@code action { }}/{@code else { }} handling shared across
 * {@code ActiveLinkParseEventHandler}/{@code FilterParseEventHandler}/{@code EscalationParseEventHandler}
 * and their common base {@code WorkflowParseEventHandler}, targeting {@code
 * com.bmc.arsys.api.Action} subtypes directly (the exact shapes {@code
 * arinside.ar.xmlfile.ActionXmlBuilder} already builds for XML mode - used directly as the
 * client-API reference for constructors/setters).
 *
 * <p>One instance per {@code action { }}/{@code else { }} clause - a real action builds up
 * incrementally across MULTIPLE, separately-tagged item events (e.g. an Open Window action's
 * server/form/vui/mode all arrive as distinct tags), so {@link #currentAction} is lazily created on
 * whichever tag arrives first and mutated by every subsequent tag, mirroring the real handlers'
 * identical "if (this.currentAction == null) currentAction = new X()" pattern throughout.
 *
 * <p>Message vs FilterMessage (MSG_NUM/MSG_TEXT/MSG_TYPE/MSG_PANE) is context-dependent - an
 * Active Link's Message action is {@link MessageAction} (has a prompt-pane flag), a Filter/
 * Escalation's is {@link FilterMessageAction} (no prompt-pane flag) - selected via the {@code
 * activeLinkContext} constructor flag, matching {@code ActionXmlBuilder}'s identical dispatch.
 *
 * <p><b>Deliberately not ported</b> (disclosed, matches a real, confirmed gap in the DEF format
 * itself, not a scope cut on this port's part): CallGuide's input/output field mappings -
 * {@code CALLGUIDE_INPUT}/{@code CALLGUIDE_OUTPUT} have no field-pair decoding logic anywhere in
 * the real {@code ActiveLinkParseEventHandler} either (confirmed by reading it directly - those
 * cases only lazily construct the action, nothing else), and neither tag appears in {@code
 * DefParserImpl.parseValue()}'s central type-decoding dispatch, so the real DEF format apparently
 * never encodes this data the way SET_FIELD/PUSH_FIELD/OPEN_DLG_INPUT do. Open Window's report-mode
 * details ({@code OPEN_DLG_RPTSTR}) - a narrow feature (structured report parameters for the rare
 * "open as report" window mode) whose domain-side parser ({@code OpenWindowActionImpl.
 * parseReportString}) is not ported; the field simply stays unset.
 */
final class DefActionBuilder {
    private final boolean activeLinkContext;
    private Action currentAction;
    private StringBuilder buffer; // OLE COM-method / macro-text multi-line accumulation, flushed at clause end

    DefActionBuilder(boolean activeLinkContext) {
        this.activeLinkContext = activeLinkContext;
    }

    Action build() {
        flushBuffer();
        return currentAction;
    }

    void reset() {
        currentAction = null;
        buffer = null;
    }

    private void flushBuffer() {
        if (currentAction != null && buffer != null) {
            if (currentAction instanceof OleAutomationAction ole) {
                ole.setMethodList(new ArrayList<>()); // COM-method decoding not ported (obscure, no DecodeCOMMethods port) - action still built, method list simply empty
            } else if (currentAction instanceof RunMacroAction macro) {
                macro.setMacroText(buffer.toString());
            }
        }
        buffer = null;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        switch (item) {
            // ---- shared across AL/Filter/Escalation ----
            case COMMAND -> {
                if (raw.startsWith("Distributed-")) break; // DSO action - not ported, no rendering path in this port either
                if (currentAction == null) currentAction = new RunProcessAction();
                ((RunProcessAction) currentAction).setCommandLine(raw);
            }
            case DIRECT_SQL -> decodeDirectSql(raw, charset);
            case SET_FIELD -> decodeSetField(raw, charset);
            case PUSH_FIELD -> decodePushField(raw, charset);
            case CALLGUIDE_SERVER -> { ensureCallGuide(); ((CallGuideAction) currentAction).setServerName(raw); }
            case CALLGUIDE_GUIDE -> { ensureCallGuide(); ((CallGuideAction) currentAction).setGuideName(raw); }
            case CALLGUIDE_MODE -> { ensureCallGuide(); ((CallGuideAction) currentAction).setGuideMode(ParseUtil.intValue(raw)); }
            case CALLGUIDE_TABLEID -> { ensureCallGuide(); ((CallGuideAction) currentAction).setGuideTableId(ParseUtil.intValue(raw)); }
            case CALLGUIDE_INPUT, CALLGUIDE_OUTPUT -> ensureCallGuide(); // no field-pair data in this format - see class javadoc
            case EXITGUIDE -> {
                if (currentAction == null) currentAction = new ExitGuideAction();
                ((ExitGuideAction) currentAction).setCloseAll(booleanOf(raw));
            }
            case GOTOGUIDE -> {
                if (currentAction == null) currentAction = new GotoGuideLabelAction();
                ((GotoGuideLabelAction) currentAction).setLabel(raw);
            }
            case GOTOACTION -> decodeGoto(raw);
            case MSG_NUM -> { ensureMessage(); setMsgNum(ParseUtil.intValue(raw)); }
            case MSG_TEXT -> { ensureMessage(); setMsgText(raw); }
            case MSG_TYPE -> { ensureMessage(); setMsgType(ParseUtil.intValue(raw)); }
            case MSG_PANE -> { if (activeLinkContext) { ensureMessage(); ((MessageAction) currentAction).setUsePromptingPane(booleanOf(raw)); } }
            case NOT_FIELDS -> decodeNotifyFields(raw);
            case NOT_MECH -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyMechanism(ParseUtil.intValue(raw)); }
            case NOT_PRIORITY -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyPriority(ParseUtil.intValue(raw)); }
            case NOT_SUBJECT -> { ensureNotify(); ((NotifyAction) currentAction).setSubjectText(raw); }
            case NOT_TEXT -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyText(raw); }
            case NOT_USER -> { ensureNotify(); ((NotifyAction) currentAction).setUser(raw); }
            case NOT_XREF -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyMechanismXRef(ParseUtil.intValue(raw)); }
            case NOT_BEHAVIOR -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyBehavior(ParseUtil.intValue(raw)); }
            case NOT_PERMISSION -> { ensureNotify(); ((NotifyAction) currentAction).setNotifyPermission(ParseUtil.intValue(raw)); }
            case NOT_ADV_REPLY_TO -> { ensureNotify(); ((NotifyAction) currentAction).setReplyTo(raw); }
            case NOT_ADV_FROM -> { ensureNotify(); ((NotifyAction) currentAction).setFrom(raw); }
            case NOT_ADV_CC -> { ensureNotify(); ((NotifyAction) currentAction).setCc(raw); }
            case NOT_ADV_BCC -> { ensureNotify(); ((NotifyAction) currentAction).setBcc(raw); }
            case NOT_ADV_ORG -> { ensureNotify(); ((NotifyAction) currentAction).setOrganization(raw); }
            case NOT_ADV_MAILBOX -> { ensureNotify(); ((NotifyAction) currentAction).setMailboxName(raw); }
            case NOT_ADV_HDR_TMP -> { ensureNotify(); ((NotifyAction) currentAction).setHeaderTemplate(raw); }
            case NOT_ADV_FTR_TMP -> { ensureNotify(); ((NotifyAction) currentAction).setFooterTemplate(raw); }
            case NOT_ADV_CNT_TMP -> { ensureNotify(); ((NotifyAction) currentAction).setContentTemplate(raw); }
            case LOG_FILE -> {
                if (currentAction == null) currentAction = new LogAction();
                ((LogAction) currentAction).setFilePath(raw);
            }
            case SVC_SERVER -> { ensureService(); ((ServiceAction) currentAction).setServerName(raw); }
            case SVC_SCHEMA -> { ensureService(); ((ServiceAction) currentAction).setServiceForm(raw); }
            case SVC_RQST_MAP -> { ensureService(); ((ServiceAction) currentAction).setRequestIdMap(ParseUtil.intValue(raw)); }
            case SVC_IN_FLD_MAP -> { ensureService(); ((ServiceAction) currentAction).setInputFieldMapping(decodeFieldAssignList(raw, charset)); }
            case SVC_OUT_FLD_MAP -> { ensureService(); ((ServiceAction) currentAction).setOutputFieldMapping(decodeFieldAssignList(raw, charset)); }
            case SVC_SMPL_SERVER -> { if (currentAction instanceof ServiceAction s) s.setSampleServer(raw); else sampleServerFallback(raw); }
            case SVC_SMPL_SCHEMA -> { if (currentAction instanceof ServiceAction s) s.setsampleForm(raw); else sampleFormFallback(raw); }
            case SAMPLE_GUIDE -> { if (currentAction instanceof CallGuideAction g) g.setSampleGuide(raw); }

            // ---- Active-Link-only ----
            case ACCESS_OPTION -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setAccessOption(ParseUtil.intValue(raw)); }
            case FIELD_CHAR_OPTION -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setOption(ParseUtil.intValue(raw)); }
            case CHAR_MENU_2 -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setCharMenu(raw); }
            case DISPLAY_PROPLIST -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setProps(DefPropertyDecoder.decode(raw, charset, new DisplayPropertyMap())); }
            case FOCUS -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setFocus(ParseUtil.intValue(raw)); }
            case ID_2 -> { ensureChangeField(); ((ChangeFieldAction) currentAction).setFieldId(ParseUtil.intValue(raw)); }
            case CLOSE_WND -> { if (currentAction == null) currentAction = new CloseWindowAction(); }
            case CLOSE_WND_ALL -> { if (currentAction == null) currentAction = new CloseWindowAction(); ((CloseWindowAction) currentAction).setCloseAll(booleanOf(raw)); }
            case COMMIT_CHANGES -> { if (currentAction == null) currentAction = new CommitChangesAction(""); }
            case DDE_ACTION -> { ensureDde(); ((DDEAction) currentAction).setAction(ParseUtil.intValue(raw)); }
            case DDE_COMMAND -> { ensureDde(); ((DDEAction) currentAction).setCommand(raw); }
            case DDE_FILE -> { ensureDde(); ((DDEAction) currentAction).setPathToProgram(raw); }
            case DDE_ITEM -> { ensureDde(); ((DDEAction) currentAction).setItem(raw); }
            case DDE_SERVICE -> { ensureDde(); ((DDEAction) currentAction).setServiceName(raw); }
            case DDE_TOPIC -> { ensureDde(); ((DDEAction) currentAction).setTopic(raw); }
            case AUTO_SERVER -> { ensureOle(); ((OleAutomationAction) currentAction).setAutoServerName(raw); }
            case AUTO_CLSID -> { ensureOle(); ((OleAutomationAction) currentAction).setClsId(raw); }
            case AUTO_ACTION -> { ensureOle(); ((OleAutomationAction) currentAction).setAction(raw); }
            case AUTO_VISIBLE -> { ensureOle(); ((OleAutomationAction) currentAction).setVisible(booleanOf(raw)); }
            case AUTO_COM -> { ensureOle(); appendBuffer(raw); }
            case OPEN_DLG_SERVER -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setServerName(raw); }
            case OPEN_DLG_SCHEMA -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setFormName(raw); }
            case OPEN_DLG_VUI -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setVuiLabel(raw); }
            case OPEN_DLG_BOX -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setCloseBox(booleanOf(raw)); }
            case OPEN_DLG_INPUT -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setInputValueFieldPairs(decodeFieldAssignList(raw, charset)); }
            case OPEN_DLG_OUTPUT -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setOutputValueFieldPairs(decodeFieldAssignList(raw, charset)); }
            case OPEN_DLG_WINMODE -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setWindowMode(ParseUtil.intValue(raw)); }
            case OPEN_DLG_TARGET -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setTargetLocation(raw); }
            case OPEN_DLG_QUERY -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setQuery(DefQualificationDecoder.decode(raw, charset)); }
            case OPEN_DLG_CONTINU -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setNoMatchContinue(ParseUtil.intValue(raw) == 1); }
            case OPEN_DLG_SUPPRESS -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setSuppressEmptyLst(booleanOf(raw)); }
            case OPEN_DLG_MSG_TYPE -> { ensureOpenWindow(); ensureOpenWindowMsg().setMessageType(ParseUtil.intValue(raw)); }
            case OPEN_DLG_MSG_NUM -> { ensureOpenWindow(); ensureOpenWindowMsg().setMessageNum(ParseUtil.intValue(raw)); }
            case OPEN_DLG_MSG_PANE -> { ensureOpenWindow(); ensureOpenWindowMsg().setUsePromptingPane(booleanOf(raw)); }
            case OPEN_DLG_MSG_TEXT -> { ensureOpenWindow(); ensureOpenWindowMsg().setMessageText(raw); }
            case OPEN_DLG_POLLINT -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setPollinginterval(ParseUtil.intValue(raw)); }
            case OPEN_DLG_SORTORD -> { ensureOpenWindow(); ((OpenWindowAction) currentAction).setSortOrderList(decodeSortList(raw)); }
            case SAMPLE_SERVER -> { if (currentAction instanceof OpenWindowAction w) w.setSampleServer(raw); else if (currentAction instanceof PushFieldsAction p) p.setSampleServer(raw); else if (currentAction instanceof SetFieldsFromForm f) f.setSampleServer(raw); }
            case SAMPLE_SCHEMA -> { if (currentAction instanceof OpenWindowAction w) w.setSampleForm(raw); else if (currentAction instanceof PushFieldsAction p) p.setSampleForm(raw); else if (currentAction instanceof SetFieldsFromForm f) f.setSampleForm(raw); }
            case MACRO_NAME -> { ensureMacro(); ((RunMacroAction) currentAction).setMacroName(raw); }
            case MACRO_PARMS -> decodeMacroParam(raw);
            case MACRO_TEXT -> { ensureMacro(); appendBuffer(raw); }
            case WAIT -> {
                if (currentAction == null) currentAction = new WaitAction();
                ((WaitAction) currentAction).setContinueButtonTitle(raw);
            }
            default -> { /* not an action-body tag - see DefWorkflowBuilder for object-level tags */ }
        }
    }

    // Distinguishes the two SAMPLE_SERVER/SAMPLE_SCHEMA call sites that only apply when currentAction is a CallGuideAction (context-ambiguous with the switch cases above, since CallGuide's own sample-server/guide use SAMPLE_SERVER too) - see the CALLGUIDE_* cases which already ensure a CallGuideAction; this fallback only fires when NO action type is known yet.
    private void sampleServerFallback(String raw) { if (currentAction instanceof CallGuideAction g) g.setSampleServer(raw); }
    private void sampleFormFallback(String raw) { /* CallGuideAction has no sample-form field - only sampleServer/sampleGuide */ }

    private void ensureCallGuide() { if (currentAction == null) currentAction = new CallGuideAction(); }
    private void ensureChangeField() { if (currentAction == null) currentAction = new ChangeFieldAction(); }
    private void ensureDde() { if (currentAction == null) currentAction = new DDEAction(); }
    private void ensureOle() { if (currentAction == null) currentAction = new OleAutomationAction(); }
    private void ensureOpenWindow() { if (currentAction == null) currentAction = new OpenWindowAction(); }
    private void ensureMacro() { if (currentAction == null) currentAction = new RunMacroAction(); }
    private void ensureService() { if (currentAction == null) currentAction = new ServiceAction(); }
    private void ensureNotify() { if (currentAction == null) currentAction = new NotifyAction(); }

    private void ensureMessage() {
        if (currentAction == null) currentAction = activeLinkContext ? new MessageAction() : new FilterMessageAction();
    }

    private void setMsgNum(int n) { if (currentAction instanceof MessageAction m) m.setMessageNum(n); else if (currentAction instanceof FilterMessageAction m) m.setMessageNum(n); }
    private void setMsgText(String s) { if (currentAction instanceof MessageAction m) m.setMessageText(s); else if (currentAction instanceof FilterMessageAction m) m.setMessageText(s); }
    private void setMsgType(int t) { if (currentAction instanceof MessageAction m) m.setMessageType(t); else if (currentAction instanceof FilterMessageAction m) m.setMessageType(t); }

    private MessageAction ensureOpenWindowMsg() {
        OpenWindowAction w = (OpenWindowAction) currentAction;
        if (!(w.getMsg() instanceof MessageAction)) w.setMsg(new MessageAction());
        return (MessageAction) w.getMsg();
    }

    private void appendBuffer(String raw) {
        if (buffer == null) buffer = new StringBuilder();
        buffer.append(raw);
    }

    private boolean booleanOf(String raw) {
        return "1".equals(raw);
    }

    private void decodeGoto(String raw) {
        int sep = raw.indexOf('\\');
        if (sep < 0) return;
        int type = ParseUtil.intValue(raw.substring(0, sep));
        int value = ParseUtil.intValue(raw.substring(sep + 1));
        if (currentAction == null) currentAction = new GotoAction();
        ((GotoAction) currentAction).setTag(type);
        ((GotoAction) currentAction).setFieldIdOrValue(value);
    }

    /** serverLen\server\sqlLen\<sql bytes, exactly sqlLen-1 long - matches the real decoder's own off-by-one exactly>. */
    private void decodeDirectSql(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        String server = d.readString(d.readInt());
        int sqlLen = d.readInt();
        String command = d.readString(Math.max(sqlLen - 1, 0));
        if (currentAction == null) currentAction = new DirectSqlAction();
        ((DirectSqlAction) currentAction).setServer(server);
        ((DirectSqlAction) currentAction).setCommand(command);
    }

    /**
     * assignIndex\fieldId\<assignment>. This port always builds SetFieldsFromForm regardless of the
     * assignment's real source (matches arinside.ar.xmlfile.ActionXmlBuilder's own established,
     * already-accepted simplification for XML mode - see its javadoc).
     *
     * <p>{@code assignIndex} is NOT a compact list position - confirmed via live data (a real
     * single-field Set-Fields action arrived with {@code assignIndex=7}, which an earlier version of
     * this method took literally, padding 7 null entries in front of the one real assignment; a null
     * entry would NPE the first time {@code ActionSummaryTable} iterates the list). It's some other
     * stable slot/id concept the real importer cares about for re-import purposes, irrelevant to
     * read-only documentation - this port just appends in arrival order instead, matching {@code
     * ActionXmlBuilder}'s own proven-correct approach (which never had an index concept at all,
     * since XML mode's field list is just document order).
     */
    private void decodeSetField(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        d.readInt(); // assignIndex - not a list position, see javadoc
        int fieldId = d.readInt();
        AssignInfo assign = DefAssignDecoder.decodeInline(d);
        if (!(currentAction instanceof SetFieldsFromForm)) currentAction = new SetFieldsFromForm();
        SetFieldsFromForm a = (SetFieldsFromForm) currentAction;
        List<FieldAssignInfo> list = fieldAssignList(a);
        list.add(new FieldAssignInfo(fieldId, orEmptyAssign(assign)));
    }

    /** assignIndex\<target-field descriptor>\<value assignment> - decodePushFields shape (target always forced to a plain field reference). Appends in arrival order - see decodeSetField's javadoc for why assignIndex isn't used as a list position. */
    private void decodePushField(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        d.readInt(); // assignIndex - not a list position, see decodeSetField's javadoc
        AssignInfo target = DefAssignDecoder.decodeInlinePushTarget(d);
        AssignInfo value = DefAssignDecoder.decodeInline(d);
        if (!(currentAction instanceof PushFieldsAction)) currentAction = new PushFieldsAction();
        PushFieldsAction a = (PushFieldsAction) currentAction;
        List<PushFieldsInfo> list = a.getPushFieldsList();
        if (list == null) { list = new ArrayList<>(); a.setPushFieldsList(list); }
        AssignFieldInfo fieldInfo = (target != null && target.getField() != null) ? target.getField() : new AssignFieldInfo();
        list.add(new PushFieldsInfo(fieldInfo, orEmptyAssign(value)));
    }

    private List<FieldAssignInfo> fieldAssignList(SetFieldsFromForm a) {
        // SetFieldsFromForm exposes its list only via the constructor in this jar (no getter surfaced
        // as a plain field list beyond what ActionXmlBuilder already relies on) - mirrored via a small
        // side map keyed by identity, avoided here by always rebuilding through setSetFieldsList each call.
        List<FieldAssignInfo> list = pendingSetFields.computeIfAbsent(a, k -> new ArrayList<>());
        a.setSetFieldsList(list);
        return list;
    }

    private final Map<SetFieldsFromForm, List<FieldAssignInfo>> pendingSetFields = new LinkedHashMap<>();

    private AssignInfo orEmptyAssign(AssignInfo a) {
        return a != null ? a : new AssignInfo();
    }

    /** numAssignments\(fieldId\<assignment>)* - shared shape for Open/Close Window input/output and Service in/out field mapping (SVC_IN_FLD_MAP/SVC_OUT_FLD_MAP arrive already fully implicit-continuation-merged as one value). */
    private List<FieldAssignInfo> decodeFieldAssignList(String raw, Charset charset) {
        List<FieldAssignInfo> list = new ArrayList<>();
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        int num = d.readInt();
        for (int i = 0; i < num && !d.isEmpty(); i++) {
            int fieldId = d.readInt();
            AssignInfo assign = DefAssignDecoder.decodeInline(d);
            list.add(new FieldAssignInfo(fieldId, orEmptyAssign(assign)));
        }
        return list;
    }

    private List<SortInfo> decodeSortList(String raw) {
        List<SortInfo> list = new ArrayList<>();
        DefValueDecoder d = new DefValueDecoder(raw, null);
        int num = d.readInt();
        for (int i = 0; i < num && !d.isEmpty(); i++) list.add(new SortInfo(d.readInt(), d.readInt()));
        return list;
    }

    /** null/empty->NONE, leading '*'->ALL, leading '@'->CHANGED, else space-separated field-id list. */
    private void decodeNotifyFields(String raw) {
        ensureNotify();
        NotifyAction a = (NotifyAction) currentAction;
        if (raw == null || raw.isEmpty()) {
            a.setFieldIdListType(Constants.AR_FILTER_FIELD_IDS_NONE);
        } else if (raw.charAt(0) == '*') {
            a.setFieldIdListType(Constants.AR_FILTER_FIELD_IDS_ALL);
        } else if (raw.charAt(0) == '@') {
            a.setFieldIdListType(Constants.AR_FILTER_FIELD_IDS_CHANGED);
        } else {
            a.setFieldIdListType(Constants.AR_FILTER_FIELD_IDS_LIST);
            List<Integer> ids = new ArrayList<>();
            for (String tok : raw.split(" ")) if (!tok.isBlank()) ids.add(ParseUtil.intValue(tok));
            a.setFieldIdList(ids);
        }
    }

    /** macro-parms: <keyLen>\<key>\<value up to end> */
    private void decodeMacroParam(String raw) {
        ensureMacro();
        DefValueDecoder d = new DefValueDecoder(raw, null);
        int keyLen = d.readInt();
        String key = d.readString(keyLen);
        String value = d.readRest();
        RunMacroAction a = (RunMacroAction) currentAction;
        Map<String, String> parms = a.getMacroParms();
        if (parms == null) { parms = new LinkedHashMap<>(); a.setMacroParms(parms); }
        parms.put(key, value);
    }
}
