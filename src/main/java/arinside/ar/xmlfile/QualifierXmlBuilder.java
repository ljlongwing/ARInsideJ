package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds a {@link QualifierInfo} tree from a wrapper element (&lt;runIfQualification&gt;,
 * &lt;qualifier&gt;, a &lt;field&gt; lookup's &lt;qualification&gt;, ...) whose single child is the
 * actual qualifier node (&lt;and&gt;/&lt;or&gt;/&lt;not&gt;/&lt;relationalOperation&gt;, or a bare
 * &lt;fieldID&gt; used directly as a boolean predicate - confirmed present in real exports, maps to
 * {@link QualifierFromFieldInfo}). Also builds the {@link ArithmeticOrRelationalOperand} operand
 * tree used by relationalOperation's &lt;left&gt;/&lt;right&gt; (fieldID / currentValueFieldID /
 * value / nested arithmeticOperation - the full vocabulary confirmed by scanning a 1.5GB real-export
 * sample, see ArsXmlFileParser's javadoc), since operand and qualifier recursion share the same
 * wrapper idiom.
 */
final class QualifierXmlBuilder {
    private QualifierXmlBuilder() {}

    /** c positioned at the wrapper START_ELEMENT (e.g. &lt;runIfQualification&gt;); leaves c at its END_ELEMENT. */
    static QualifierInfo build(XmlCursor c) throws XMLStreamException {
        return wrappedQualifier(c);
    }

    private static QualifierInfo wrappedQualifier(XmlCursor c) throws XMLStreamException {
        QualifierInfo result = null;
        while (c.nextTag() == START_ELEMENT) {
            if (result == null) result = buildQualifierNode(c);
            else c.skipSubtree();
        }
        return result != null ? result : new QualifierInfo();
    }

    private static QualifierInfo buildQualifierNode(XmlCursor c) throws XMLStreamException {
        String tag = c.localName();
        return switch (tag) {
            case "and", "or" -> {
                QualifierInfo left = null, right = null;
                while (c.nextTag() == START_ELEMENT) {
                    switch (c.localName()) {
                        case "left" -> left = wrappedQualifier(c);
                        case "right" -> right = wrappedQualifier(c);
                        default -> c.skipSubtree();
                    }
                }
                int op = "and".equals(tag) ? QualifierInfo.AR_COND_OP_AND : QualifierInfo.AR_COND_OP_OR;
                yield new QualifierInfo(op, left != null ? left : new QualifierInfo(), right != null ? right : new QualifierInfo());
            }
            case "not" -> {
                QualifierInfo inner = null;
                while (c.nextTag() == START_ELEMENT) {
                    if (inner == null) inner = buildQualifierNode(c);
                    else c.skipSubtree();
                }
                yield new QualifierInfo(QualifierInfo.AR_COND_OP_NOT, inner != null ? inner : new QualifierInfo(), null);
            }
            case "relationalOperation" -> {
                String op = null;
                ArithmeticOrRelationalOperand left = null, right = null;
                while (c.nextTag() == START_ELEMENT) {
                    switch (c.localName()) {
                        case "operation" -> op = c.elementText();
                        case "left" -> left = wrappedOperand(c);
                        case "right" -> right = wrappedOperand(c);
                        default -> c.skipSubtree();
                    }
                }
                RelationalOperationInfo rel = new RelationalOperationInfo(
                    XmlEnums.relOp(op != null ? op : "equal"),
                    left != null ? left : new ArithmeticOrRelationalOperand(new Value()),
                    right != null ? right : new ArithmeticOrRelationalOperand(new Value()));
                yield new QualifierInfo(rel);
            }
            case "fieldID", "qualifierFromField" -> {
                int fid = c.intText();
                yield new QualifierInfo(new QualifierFromFieldInfo(fid));
            }
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized qualifier node <" + tag + ">, treating as no-op");
                c.skipSubtree();
                yield new QualifierInfo();
            }
        };
    }

    private static ArithmeticOrRelationalOperand wrappedOperand(XmlCursor c) throws XMLStreamException {
        ArithmeticOrRelationalOperand result = null;
        while (c.nextTag() == START_ELEMENT) {
            if (result == null) result = buildOperandNode(c);
            else c.skipSubtree();
        }
        return result != null ? result : new ArithmeticOrRelationalOperand(new Value());
    }

    private static ArithmeticOrRelationalOperand buildOperandNode(XmlCursor c) throws XMLStreamException {
        String tag = c.localName();
        return switch (tag) {
            case "fieldID" -> new ArithmeticOrRelationalOperand(OperandType.FIELDID.toInt(), c.intText());
            case "currentValueFieldID" -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_CURRENT.toInt(), c.intText());
            case "databaseValueFieldID" -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_DB.toInt(), c.intText());
            case "transactionValueFieldID" -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_TRANSACTION.toInt(), c.intText());
            case "value" -> new ArithmeticOrRelationalOperand(ValueXmlBuilder.build(c));
            case "arithmeticOperation" -> new ArithmeticOrRelationalOperand(buildArithmeticOperationInfo(c));
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized relational operand <" + tag + ">, defaulting to null value");
                c.skipSubtree();
                yield new ArithmeticOrRelationalOperand(new Value());
            }
        };
    }

    private static ArithmeticOperationInfo buildArithmeticOperationInfo(XmlCursor c) throws XMLStreamException {
        String op = null;
        ArithmeticOrRelationalOperand left = null, right = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "operation" -> op = c.elementText();
                case "left" -> left = wrappedOperand(c);
                case "right" -> right = wrappedOperand(c);
                default -> c.skipSubtree();
            }
        }
        return new ArithmeticOperationInfo(
            XmlEnums.arithOp(op != null ? op : "add"),
            left != null ? left : new ArithmeticOrRelationalOperand(new Value()),
            right != null ? right : new ArithmeticOrRelationalOperand(new Value()));
    }
}
