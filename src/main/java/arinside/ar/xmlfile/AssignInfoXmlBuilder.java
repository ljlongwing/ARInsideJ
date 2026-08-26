package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds the {@link AssignInfo} tree (field/value/arithmetic/function/sql/process/filterAPI - the
 * full vocabulary confirmed by scanning a 1.5GB real-export sample's &lt;fieldValue&gt; children,
 * see ArsXmlFileParser's javadoc) plus the related {@link AssignFieldInfo} (a &lt;field&gt; or
 * &lt;targetField&gt; cross-form lookup) and {@link FieldAssignInfo}/{@link PushFieldsInfo} wrappers
 * used by setFields/pushFields actions.
 */
final class AssignInfoXmlBuilder {
    private AssignInfoXmlBuilder() {}

    /** c positioned at a wrapper START_ELEMENT (fieldValue / targetFieldValue / arithmetic's left|right / functionParameter / inputValue); leaves c at its END_ELEMENT. */
    static AssignInfo buildWrapped(XmlCursor c) throws XMLStreamException {
        AssignInfo result = null;
        while (c.nextTag() == START_ELEMENT) {
            if (result == null) result = buildAssignNode(c);
            else c.skipSubtree();
        }
        return result != null ? result : new AssignInfo();
    }

    private static AssignInfo buildAssignNode(XmlCursor c) throws XMLStreamException {
        String tag = c.localName();
        AssignInfo info = new AssignInfo();
        switch (tag) {
            case "field" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FIELD);
                info.setField(buildAssignFieldInfo(c));
            }
            case "value" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_VALUE);
                info.setValue(ValueXmlBuilder.build(c));
            }
            case "arithmetic" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_ARITH);
                info.setArithOp(buildArithOpAssignInfo(c));
            }
            case "function" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FUNCTION);
                info.setFunction(buildFunctionAssignInfo(c));
            }
            case "sql" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_SQL);
                info.setSql(buildAssignSqlInfo(c));
            }
            case "process" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_PROCESS);
                info.setProcess(readSingleChildText(c, "command"));
            }
            case "filterAPI" -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FILTER_API);
                info.setFilterApi(buildFilterApiInfo(c));
            }
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized assignment node <" + tag + ">, treating as no-op");
                c.skipSubtree();
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_NONE);
            }
        }
        return info;
    }

    /** c at the &lt;field&gt;/&lt;targetField&gt; START_ELEMENT. */
    static AssignFieldInfo buildAssignFieldInfo(XmlCursor c) throws XMLStreamException {
        AssignFieldInfo info = new AssignFieldInfo();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serverName" -> info.setServer(c.elementText());
                case "formName" -> info.setForm(c.elementText());
                case "qualification" -> info.setQualifier(QualifierXmlBuilder.build(c));
                case "fieldID" -> info.setFieldId(c.intText());
                case "noMatchOption" -> info.setNoMatchOption(XmlEnums.noMatchOption(c.elementText()));
                case "multipleMatchOption" -> info.setMultiMatchOption(XmlEnums.multiMatchOption(c.elementText()));
                default -> c.skipSubtree();
            }
        }
        return info;
    }

    private static ArithOpAssignInfo buildArithOpAssignInfo(XmlCursor c) throws XMLStreamException {
        String op = null;
        AssignInfo left = null, right = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "operation" -> op = c.elementText();
                case "left" -> left = buildWrapped(c);
                case "right" -> right = buildWrapped(c);
                default -> c.skipSubtree();
            }
        }
        return new ArithOpAssignInfo(XmlEnums.arithOp(op != null ? op : "add"),
            left != null ? left : new AssignInfo(), right != null ? right : new AssignInfo());
    }

    private static FunctionAssignInfo buildFunctionAssignInfo(XmlCursor c) throws XMLStreamException {
        int code = -1;
        List<AssignInfo> params = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "functionType" -> code = XmlEnums.functionCode(c.elementText());
                case "functionParameterList" -> {
                    while (c.nextTag() == START_ELEMENT) {
                        if ("functionParameter".equals(c.localName())) {
                            params.add(buildWrapped(c));
                        } else {
                            c.skipSubtree();
                        }
                    }
                }
                default -> c.skipSubtree();
            }
        }
        return new FunctionAssignInfo(code, params);
    }

    private static AssignSQLInfo buildAssignSqlInfo(XmlCursor c) throws XMLStreamException {
        AssignSQLInfo info = new AssignSQLInfo();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serverName" -> info.setServer(c.elementText());
                case "sqlCommand" -> info.setSqlCommand(c.elementText());
                case "valueIndex" -> info.setValueIndex(c.intText());
                case "noMatchOption" -> info.setNoMatchOption(XmlEnums.noMatchOption(c.elementText()));
                case "multipleMatchOption" -> info.setMultiMatchOption(XmlEnums.multiMatchOption(c.elementText()));
                default -> c.skipSubtree();
            }
        }
        return info;
    }

    private static AssignFilterApiInfo buildFilterApiInfo(XmlCursor c) throws XMLStreamException {
        String service = null;
        long valueIndex = 0;
        List<AssignInfo> inputs = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "serviceName" -> service = c.elementText();
                case "valueIndex" -> valueIndex = Long.parseLong(c.elementText().trim());
                case "inputValueList" -> {
                    while (c.nextTag() == START_ELEMENT) {
                        if ("inputValue".equals(c.localName())) {
                            inputs.add(buildWrapped(c));
                        } else {
                            c.skipSubtree();
                        }
                    }
                }
                default -> c.skipSubtree();
            }
        }
        return new AssignFilterApiInfo(service, inputs, valueIndex);
    }

    /** c at a container START_ELEMENT (e.g. &lt;process&gt;) whose only relevant child is a single named leaf. */
    private static String readSingleChildText(XmlCursor c, String childName) throws XMLStreamException {
        String text = null;
        while (c.nextTag() == START_ELEMENT) {
            if (childName.equals(c.localName())) text = c.elementText();
            else c.skipSubtree();
        }
        return text;
    }
}
