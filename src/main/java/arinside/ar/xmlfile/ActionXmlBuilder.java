package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds ActiveLink/Filter/Escalation action lists from an &lt;ifActions&gt;/&lt;elseActions&gt;
 * wrapper. Each &lt;action&gt; element's single child tag name picks the concrete {@link Action}
 * subtype - vocabulary confirmed by scanning a 1.5GB real-export sample's &lt;action&gt; children,
 * see ArsXmlFileParser's javadoc. Since almost every concrete Action class implements both
 * ActiveLinkAction and FilterAction (only Message/FilterMessage are asymmetric), this returns plain
 * {@link Action} objects and lets the caller build whichever typed list it needs.
 */
final class ActionXmlBuilder {
    private ActionXmlBuilder() {}

    /** c positioned at the &lt;ifActions&gt;/&lt;elseActions&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static List<Action> buildActionList(XmlCursor c, boolean activeLinkContext, String ownerName) throws XMLStreamException {
        List<Action> result = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"action".equals(c.localName())) {
                c.skipSubtree();
                continue;
            }
            int depthBefore = c.depth();
            try {
                Action a = buildOneAction(c, activeLinkContext);
                if (a != null) result.add(a);
            } catch (Exception e) {
                System.out.println("[WARN] xmlfile: failed parsing an action of '" + ownerName + "': " + e);
                c.recoverTo(depthBefore);
            }
        }
        return result;
    }

    private static Action buildOneAction(XmlCursor c, boolean activeLinkContext) throws XMLStreamException {
        Action result = null;
        while (c.nextTag() == START_ELEMENT) {
            if (result == null) {
                result = dispatch(c, activeLinkContext);
            } else {
                c.skipSubtree();
            }
        }
        return result;
    }

    private static Action dispatch(XmlCursor c, boolean activeLinkContext) throws XMLStreamException {
        String tag = c.localName();
        return switch (tag) {
            case "setFields" -> buildSetFields(c);
            case "changeField" -> buildChangeField(c);
            case "callGuide" -> buildCallGuide(c);
            case "pushFields" -> buildPushFields(c);
            case "runProcess" -> buildRunProcess(c);
            case "message" -> activeLinkContext ? buildMessageAction(c) : buildFilterMessageAction(c);
            case "openWindow" -> buildOpenWindow(c);
            case "sql" -> buildDirectSql(c);
            case "goto" -> buildGoto(c);
            case "svcAction" -> buildServiceAction(c);
            case "closeWindow" -> buildCloseWindow(c);
            case "gotoGuide" -> buildGotoGuideLabel(c);
            case "exitGuide" -> buildExitGuide(c);
            case "commitChanges" -> buildCommitChanges(c);
            case "notify" -> buildNotify(c);
            case "macro" -> buildRunMacro(c);
            case "oleAutomation" -> buildOleAutomation(c);
            case "log" -> buildLog(c);
            case "wait" -> buildWait(c);
            case "dso" -> buildDso(c);
            case "filterMessage" -> buildFilterMessageAction(c);
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized action <" + tag + ">, skipping");
                c.skipSubtree();
                yield null;
            }
        };
    }

    private static SetFieldsAction buildSetFields(XmlCursor c) throws XMLStreamException {
        SetFieldsFromForm a = new SetFieldsFromForm();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "setFieldList" -> a.setSetFieldsList(buildFieldAssignInfoList(c, "setField"));
                case "sampleServer" -> a.setSampleServer(c.elementText());
                case "sampleForm" -> a.setSampleForm(c.elementText());
                case "setIfQualification" -> a.setSetIfQualification(QualifierXmlBuilder.build(c));
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static ChangeFieldAction buildChangeField(XmlCursor c) throws XMLStreamException {
        ChangeFieldAction a = new ChangeFieldAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "fieldID" -> a.setFieldId(c.intText());
                case "propertyList" -> a.setProps(PropertyMapXmlBuilder.build(c, new DisplayPropertyMap()));
                case "focus" -> a.setFocus("setToField".equals(c.elementText()) ? Constants.AR_FOCUS_SET_TO_FIELD : Constants.AR_FOCUS_UNCHANGED);
                case "accessOption" -> a.setAccessOption(accessOption(c.elementText()));
                case "charMenu" -> a.setCharMenu(c.elementText());
                case "option" -> a.setOption(c.intText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static int accessOption(String s) {
        return switch (s) {
            case "readOnly" -> Constants.AR_ACCESS_OPTION_READ_ONLY;
            case "readWrite" -> Constants.AR_ACCESS_OPTION_READ_WRITE;
            case "disabled" -> Constants.AR_ACCESS_OPTION_DISABLE;
            default -> Constants.AR_ACCESS_OPTION_UNCHANGED;
        };
    }

    private static CallGuideAction buildCallGuide(XmlCursor c) throws XMLStreamException {
        CallGuideAction a = new CallGuideAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serverName" -> a.setServerName(c.elementText());
                case "guideName" -> a.setGuideName(c.elementText());
                case "guideMode" -> a.setGuideMode(c.intText());
                case "loopTableFieldID" -> a.setGuideTableId(c.intText());
                case "sampleServer" -> a.setSampleServer(c.elementText());
                case "sampleGuide" -> a.setSampleGuide(c.elementText());
                case "inputFieldList" -> a.setInputValueFieldPairs(buildFieldAssignInfoList(c, "inputField"));
                case "outputFieldList" -> a.setOutputValueFieldPairs(buildFieldAssignInfoList(c, "outputField"));
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static PushFieldsAction buildPushFields(XmlCursor c) throws XMLStreamException {
        PushFieldsAction a = new PushFieldsAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "pushFieldList" -> a.setPushFieldsList(buildPushFieldsInfoList(c));
                case "sampleServer" -> a.setSampleServer(c.elementText());
                case "sampleForm" -> a.setSampleForm(c.elementText());
                case "pushIfQualification" -> a.setPushIfQualification(QualifierXmlBuilder.build(c));
                case "assignmentByMatchingIds" -> a.setAssignmentByMatchingIds(Boolean.parseBoolean(c.elementText()));
                case "assignmentToDefaults" -> a.setAssignmentToDefaults(Boolean.parseBoolean(c.elementText()));
                case "pushToForm" -> a.setPushToForm(c.elementText());
                case "pushToServer" -> a.setPushToServer(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static List<PushFieldsInfo> buildPushFieldsInfoList(XmlCursor c) throws XMLStreamException {
        List<PushFieldsInfo> list = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"pushField".equals(c.localName())) { c.skipSubtree(); continue; }
            AssignFieldInfo field = null;
            AssignInfo value = null;
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "targetField" -> field = AssignInfoXmlBuilder.buildAssignFieldInfo(c);
                    case "targetFieldValue" -> value = AssignInfoXmlBuilder.buildWrapped(c);
                    default -> c.skipSubtree();
                }
            }
            list.add(new PushFieldsInfo(field != null ? field : new AssignFieldInfo(), value != null ? value : new AssignInfo()));
        }
        return list;
    }

    private static RunProcessAction buildRunProcess(XmlCursor c) throws XMLStreamException {
        String command = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("command".equals(c.localName())) command = c.elementText();
            else c.skipSubtree();
        }
        return new RunProcessAction(command != null ? command : "");
    }

    private static MessageAction buildMessageAction(XmlCursor c) throws XMLStreamException {
        MessageAction a = new MessageAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "messageType" -> a.setMessageType(messageType(c.elementText()));
                case "messageText" -> a.setMessageText(c.elementText());
                case "messageNumber" -> a.setMessageNum(c.intText());
                case "usePromptPane" -> a.setUsePromptingPane(Boolean.parseBoolean(c.elementText()));
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static FilterMessageAction buildFilterMessageAction(XmlCursor c) throws XMLStreamException {
        FilterMessageAction a = new FilterMessageAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "messageType" -> a.setMessageType(messageType(c.elementText()));
                case "messageText" -> a.setMessageText(c.elementText());
                case "messageNumber" -> a.setMessageNum(c.intText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static int messageType(String s) {
        return switch (s) {
            case "note" -> 1;
            case "warning" -> 2;
            case "error" -> 3;
            default -> 1;
        };
    }

    private static OpenWindowAction buildOpenWindow(XmlCursor c) throws XMLStreamException {
        OpenWindowAction a = new OpenWindowAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serverName" -> a.setServerName(c.elementText());
                case "formName" -> a.setFormName(c.elementText());
                case "viewLabel" -> a.setVuiLabel(c.elementText());
                case "showCloseButton" -> a.setCloseBox(Boolean.parseBoolean(c.elementText()));
                case "inputFieldList" -> a.setInputValueFieldPairs(buildFieldAssignInfoList(c, "inputField"));
                case "outputFieldList" -> a.setOutputValueFieldPairs(buildFieldAssignInfoList(c, "outputField"));
                case "query" -> a.setQuery(QualifierXmlBuilder.build(c));
                case "pollingInterval" -> a.setPollinginterval(c.intText());
                case "targetLocation" -> a.setTargetLocation(c.elementText());
                case "suppressEmptyLst" -> a.setSuppressEmptyLst(Boolean.parseBoolean(c.elementText()));
                case "noMatchContinue" -> a.setNoMatchContinue(Boolean.parseBoolean(c.elementText()));
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static DirectSqlAction buildDirectSql(XmlCursor c) throws XMLStreamException {
        String server = null, command = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serverName" -> server = c.elementText();
                case "sqlCommand" -> command = c.elementText();
                default -> c.skipSubtree();
            }
        }
        return new DirectSqlAction(server != null ? server : "", command != null ? command : "");
    }

    private static GotoAction buildGoto(XmlCursor c) throws XMLStreamException {
        GotoAction a = new GotoAction();
        while (c.nextTag() == START_ELEMENT) {
            if ("executionOrder".equals(c.localName())) {
                while (c.nextTag() == START_ELEMENT) {
                    switch (c.localName()) {
                        case "absoluteOrder" -> { a.setTag(GotoAction.AR_GOTO_ABSOLUTE_ORDER); a.setFieldIdOrValue(c.intText()); }
                        case "offsetForward" -> { a.setTag(GotoAction.AR_GOTO_OFFSET_FORWARD); a.setFieldIdOrValue(c.intText()); }
                        case "offsetBackward" -> { a.setTag(GotoAction.AR_GOTO_OFFSET_BACKWARD); a.setFieldIdOrValue(c.intText()); }
                        case "fieldID" -> { a.setTag(GotoAction.AR_GOTO_FIELD_XREF); a.setFieldIdOrValue(c.intText()); }
                        default -> c.skipSubtree();
                    }
                }
            } else {
                c.skipSubtree();
            }
        }
        return a;
    }

    private static ServiceAction buildServiceAction(XmlCursor c) throws XMLStreamException {
        String server = null, form = null;
        int reqIdMap = 0;
        List<FieldAssignInfo> in = List.of(), out = List.of();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "svcServer" -> server = c.elementText();
                case "svcSchema" -> form = c.elementText();
                case "svcRqstIdMap" -> reqIdMap = c.intText();
                case "inputFieldList" -> in = buildFieldAssignInfoList(c, "inputField");
                case "outputFieldList" -> out = buildFieldAssignInfoList(c, "outputField");
                default -> c.skipSubtree();
            }
        }
        return new ServiceAction(server, form, reqIdMap, in, out, null, null);
    }

    private static CloseWindowAction buildCloseWindow(XmlCursor c) throws XMLStreamException {
        boolean closeAll = false;
        while (c.nextTag() == START_ELEMENT) {
            if ("closeAll".equals(c.localName())) closeAll = Boolean.parseBoolean(c.elementText());
            else c.skipSubtree();
        }
        return new CloseWindowAction(closeAll);
    }

    private static GotoGuideLabelAction buildGotoGuideLabel(XmlCursor c) throws XMLStreamException {
        String label = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("label".equals(c.localName())) label = c.elementText();
            else c.skipSubtree();
        }
        return new GotoGuideLabelAction(label != null ? label : "");
    }

    private static ExitGuideAction buildExitGuide(XmlCursor c) throws XMLStreamException {
        boolean closeAll = false;
        while (c.nextTag() == START_ELEMENT) {
            if ("closeAll".equals(c.localName())) closeAll = Boolean.parseBoolean(c.elementText());
            else c.skipSubtree();
        }
        return new ExitGuideAction(closeAll);
    }

    private static CommitChangesAction buildCommitChanges(XmlCursor c) throws XMLStreamException {
        String formName = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("formName".equals(c.localName())) formName = c.elementText();
            else c.skipSubtree();
        }
        return new CommitChangesAction(formName != null ? formName : "");
    }

    private static NotifyAction buildNotify(XmlCursor c) throws XMLStreamException {
        NotifyAction a = new NotifyAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "user" -> a.setUser(c.elementText());
                case "notifyText" -> a.setNotifyText(c.elementText());
                case "priority" -> a.setNotifyPriority(c.intText());
                case "mechanism" -> a.setNotifyMechanism(c.intText());
                case "mechanismXRef" -> a.setNotifyMechanismXRef(c.intText());
                case "subject" -> a.setSubjectText(c.elementText());
                case "behavior" -> a.setNotifyBehavior(c.intText());
                case "permission" -> a.setNotifyPermission(c.intText());
                case "from" -> a.setFrom(c.elementText());
                case "replyTo" -> a.setReplyTo(c.elementText());
                case "cc" -> a.setCc(c.elementText());
                case "bcc" -> a.setBcc(c.elementText());
                case "organization" -> a.setOrganization(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static RunMacroAction buildRunMacro(XmlCursor c) throws XMLStreamException {
        RunMacroAction a = new RunMacroAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "macroName" -> a.setMacroName(c.elementText());
                case "macroText" -> a.setMacroText(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static OleAutomationAction buildOleAutomation(XmlCursor c) throws XMLStreamException {
        OleAutomationAction a = new OleAutomationAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "automationServerName" -> a.setAutoServerName(c.elementText());
                case "classID" -> a.setClsId(c.elementText());
                case "action" -> a.setAction(c.elementText());
                case "visible" -> a.setVisible(Boolean.parseBoolean(c.elementText()));
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    private static LogAction buildLog(XmlCursor c) throws XMLStreamException {
        String path = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("filePath".equals(c.localName())) path = c.elementText();
            else c.skipSubtree();
        }
        return new LogAction(path != null ? path : "");
    }

    private static WaitAction buildWait(XmlCursor c) throws XMLStreamException {
        String title = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("continueButtonTitle".equals(c.localName())) title = c.elementText();
            else c.skipSubtree();
        }
        return new WaitAction(title != null ? title : "");
    }

    private static DSOAction buildDso(XmlCursor c) throws XMLStreamException {
        DSOAction a = new DSOAction();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "server" -> a.setServer(c.elementText());
                case "form" -> a.setForm(c.elementText());
                case "dsoMapping" -> a.setDSOMapping(c.elementText());
                case "dsoPool" -> a.setDSOPool(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return a;
    }

    /** Shared &lt;X&gt;&lt;itemTag&gt;*&lt;/X&gt; where each item is &lt;fieldID&gt;+&lt;fieldValue&gt; (wrapped AssignInfo) -> FieldAssignInfo. Used by setFieldList/inputFieldList/outputFieldList alike. */
    private static List<FieldAssignInfo> buildFieldAssignInfoList(XmlCursor c, String itemTag) throws XMLStreamException {
        List<FieldAssignInfo> list = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!itemTag.equals(c.localName())) { c.skipSubtree(); continue; }
            int fieldId = 0;
            AssignInfo value = null;
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "fieldID" -> fieldId = c.intText();
                    case "fieldValue" -> value = AssignInfoXmlBuilder.buildWrapped(c);
                    default -> c.skipSubtree();
                }
            }
            list.add(new FieldAssignInfo(fieldId, value != null ? value : new AssignInfo()));
        }
        return list;
    }
}
