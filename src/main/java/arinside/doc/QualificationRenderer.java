package arinside.doc;

import arinside.output.ImageTag;
import arinside.output.Naming;
import arinside.output.URLLink;
import arinside.output.WebUtil;
import arinside.scan.GlobalFieldIndex;
import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Java port of core/ARQualification.cpp's CheckQuery/CheckOperand - renders a QualifierInfo tree
 * (AR System's "Run If"/qualification syntax) as readable text with hyperlinked field names, and
 * records every field it encounters as a side effect via {@link FieldReferenceSink}. The Java
 * API's QualifierInfo/RelationalOperationInfo/ArithmeticOrRelationalOperand/OperandType map
 * closely to the C++'s ARQualifierStruct/ARFieldValueOrArithStruct tagged-union structures.
 *
 * Status-history and currency-field operands get the full DocStatusHistoryField.cpp/
 * DocCurrencyField.cpp resolved-and-linked treatment (see statusHistoryOf/currencyPartSuffix).
 * Scoped down from the C++ in one remaining place: AR System's newer operand types, not present in
 * the original C++ (FUNCTION/CASE/VALUE_SET/QUERY_INFO/LOCAL_VARIABLE/FIELD_ALIAS/LITERAL_ALIAS/
 * REGULAR_COMPLEX_QUERY), render as a generic "&lt;Type&gt;" placeholder rather than crashing - the
 * C++ has no reference behavior to match here since it predates these operand types entirely.
 */
public final class QualificationRenderer {

    @FunctionalInterface
    public interface FieldReferenceSink {
        /**
         * fieldExists is false when the referenced field ID isn't on the target form - see
         * FieldReferenceValidator/ValidatorPage, the Java port of DocValidator.cpp's
         * FieldReferenceValidator. detail is the real per-call-site label (e.g. "Run If",
         * "Control Field", "Set Fields If-Action 3") a caller supplied via {@link #render(QualifierInfo, String)}/
         * {@link #fieldRef(String, int, String)}/{@link #fieldRefWithText(String, int, String, String)} -
         * see CRefItem::GetDescription (util/RefItem.cpp) for the real C++'s full label vocabulary
         * this is meant to approximate. Each field reference on a page is attributed the specific
         * detail label for the action/parameter that discovered it, not a single fixed label per
         * page.
         */
        void reference(String formName, int fieldId, boolean fieldExists, String detail);
    }

    /**
     * Java port of the schema-level (not field-level) CARSchema::AddReference() side effect that
     * several C++ doc/ renderers trigger as a byproduct of resolving a target form - see
     * SchemaReferenceIndex's javadoc for the full real-vs-previously-assumed-dead history. Optional
     * (null when the caller has no SchemaReferenceIndex to write into, e.g. every non-AL/Filter/
     * Escalation QualificationRenderer construction site).
     */
    @FunctionalInterface
    public interface SchemaReferenceSink {
        enum Reason { PUSH_FIELDS_TARGET, SERVICE_CALL, DELETE_ENTRY, OPEN_WINDOW_TARGET }
        void reference(String formName, Reason reason);
    }

    private final String primaryFormName;
    private final String secondaryFormName;
    private final char primaryDelimiter;
    private final char secondaryDelimiter;
    private final int rootLevel;
    private final GlobalFieldIndex fieldIndex;
    private final FieldReferenceSink sink;
    private final SchemaReferenceSink schemaSink;

    /** Java port of CARQualification's qualLevels member - a stack of ancestor QualifierInfo nodes, pushed/popped around each checkQuery recursion, used by findCurrentEnumFieldId to find the nearest enclosing relational operation's field operand when resolving an enum/integer literal's label. */
    private final List<QualifierInfo> qualLevels = new ArrayList<>();

    /** The label passed to the next {@link #sink}.reference() call from an internal (no-explicit-detail) fieldRef/fieldRefWithText call - see {@link #render(QualifierInfo, String)} and {@link #setCurrentDetail}. Defaults to "Run If" (REFM_RUNIF), this class's original and still most common caller. */
    private String currentDetail = "Run If";

    /**
     * The real C++'s "{IfElse}-Action {N}" suffix (util/RefItem.cpp's IfElse()+ActionIndex(),
     * e.g. "If-Action 3") for whichever action {@link ActionSummaryTable#addRows} is currently
     * rendering - set alongside {@link #currentDetail} but tracked separately so a fresh
     * two-schema {@code QualificationRenderer} instance built mid-action (Set/Push Fields' "only
     * if" qualification, Open Window's target-form qualification - see ActionSummaryTable's
     * twoSchemaRenderer()) can still be given a real REFM_SETFIELDS_QUALIFICATION/REFM_PUSHFIELD_IF/
     * REFM_OPENWINDOW_QUALIFICATION-shaped label (e.g. "Set Field If Qualification If-Action 3")
     * instead of a bare, suffix-less label - the fresh instance has no {@link #currentDetail} of
     * its own to append onto, but the *caller* (which still holds the original qr) does.
     */
    private String currentActionSuffix = "";

    public String currentActionSuffix() { return currentActionSuffix; }
    public void setCurrentActionSuffix(String suffix) { this.currentActionSuffix = suffix; }

    private record FieldRef(String formName, int fieldId) {}

    /** Single-form case (Run If on an active link/filter/escalation - the common case). */
    public QualificationRenderer(String formName, int rootLevel, GlobalFieldIndex fieldIndex, FieldReferenceSink sink) {
        this(formName, rootLevel, fieldIndex, sink, null);
    }

    /** Single-form case with a schema-reference sink - see SchemaReferenceSink's javadoc. */
    public QualificationRenderer(String formName, int rootLevel, GlobalFieldIndex fieldIndex, FieldReferenceSink sink, SchemaReferenceSink schemaSink) {
        this(formName, formName, '\'', '\'', rootLevel, fieldIndex, sink, schemaSink);
    }

    /** Two-form case (Set Fields If / Push Fields If, where TR./DB. fields refer to a second form). */
    public QualificationRenderer(String primaryFormName, String secondaryFormName, int rootLevel, GlobalFieldIndex fieldIndex, FieldReferenceSink sink) {
        this(primaryFormName, secondaryFormName, rootLevel, fieldIndex, sink, null);
    }

    /** Two-form case with a schema-reference sink - see SchemaReferenceSink's javadoc. */
    public QualificationRenderer(String primaryFormName, String secondaryFormName, int rootLevel, GlobalFieldIndex fieldIndex, FieldReferenceSink sink, SchemaReferenceSink schemaSink) {
        this(primaryFormName, secondaryFormName, '$', '\'', rootLevel, fieldIndex, sink, schemaSink);
    }

    private QualificationRenderer(String primaryFormName, String secondaryFormName, char primaryDelimiter, char secondaryDelimiter,
                                   int rootLevel, GlobalFieldIndex fieldIndex, FieldReferenceSink sink, SchemaReferenceSink schemaSink) {
        this.primaryFormName = primaryFormName;
        this.secondaryFormName = secondaryFormName;
        this.primaryDelimiter = primaryDelimiter;
        this.secondaryDelimiter = secondaryDelimiter;
        this.rootLevel = rootLevel;
        this.fieldIndex = fieldIndex;
        this.sink = sink;
        this.schemaSink = schemaSink;
    }

    /** Package-visible so ActionSummaryTable can build an ad-hoc two-schema renderer (Set/Push Fields "query another form" qualifications need a different secondary form per action, not the single form this instance was built with) without re-threading rootLevel/fieldIndex/sink through every call site. */
    int rootLevel() { return rootLevel; }
    GlobalFieldIndex fieldIndex() { return fieldIndex; }
    FieldReferenceSink sink() { return sink; }
    SchemaReferenceSink schemaSink() { return schemaSink; }

    public String render(QualifierInfo query) {
        StringBuilder sb = new StringBuilder();
        checkQuery(query, sb);
        return sb.toString();
    }

    /** Same as {@link #render(QualifierInfo)}, but every field reference discovered while walking this tree is attributed the given label instead of whatever {@link #currentDetail} was last set to - e.g. "Set Fields If-Action 3" for a Set Fields action's own "only if" qualification, matching REFM_SETFIELDS_QUALIFICATION/REFM_PUSHFIELD_IF/REFM_OPENWINDOW_QUALIFICATION's real distinct labels instead of inheriting "Run If". */
    public String render(QualifierInfo query, String detail) {
        String previous = currentDetail;
        currentDetail = detail;
        try {
            return render(query);
        } finally {
            currentDetail = previous;
        }
    }

    /** Sets the label future no-explicit-detail {@link #fieldRef}/{@link #fieldRefWithText} calls attribute their discoveries to - callers that make many such calls in a loop (e.g. ActionSummaryTable iterating If/Else actions) set this once per action rather than passing an explicit detail to every single fieldRef() call inside that action's rendering. */
    public void setCurrentDetail(String detail) { this.currentDetail = detail; }

    private void checkQuery(QualifierInfo query, StringBuilder sb) {
        qualLevels.add(query);
        try {
            if (query == null) return;
            int op = query.getOperation();
            if (op == QualifierInfo.AR_COND_OP_NONE) {
                return;
            } else if (op == QualifierInfo.AR_COND_OP_AND || op == QualifierInfo.AR_COND_OP_OR) {
                QualifierInfo left = query.getLeftOperand();
                QualifierInfo right = query.getRightOperand();
                boolean leftParen = needsParen(left, op);
                if (leftParen) sb.append("(");
                checkQuery(left, sb);
                if (leftParen) sb.append(")");
                sb.append(op == QualifierInfo.AR_COND_OP_AND ? " AND " : " OR ");
                boolean rightParen = needsParen(right, op);
                if (rightParen) sb.append("(");
                checkQuery(right, sb);
                if (rightParen) sb.append(")");
            } else if (op == QualifierInfo.AR_COND_OP_NOT) {
                sb.append("NOT ");
                QualifierInfo not = query.getNotOperand();
                if (not != null) {
                    boolean paren = not.getOperation() != QualifierInfo.AR_COND_OP_REL_OP;
                    if (paren) sb.append("(");
                    checkQuery(not, sb);
                    if (paren) sb.append(")");
                }
            } else if (op == QualifierInfo.AR_COND_OP_REL_OP) {
                RelationalOperationInfo rel = query.getRelationalOperationInfo();
                checkOperand(rel.getLeftOperand(), null, sb);
                sb.append(relOpText(rel.getOperation()));
                checkOperand(rel.getRightOperand(), null, sb);
            } else if (op == QualifierInfo.AR_COND_OP_FROM_FIELD) {
                QualifierFromFieldInfo fromField = query.getFromFieldInfo();
                int fieldId = fromField.getValue();
                sb.append("EXTERNAL(").append(primaryDelimiter).append(fieldRef(primaryFormName, fieldId)).append(primaryDelimiter).append(")");
            }
        } finally {
            qualLevels.remove(qualLevels.size() - 1);
        }
    }

    /**
     * Java port of CARQualification::FindCurrentEnumFieldId - walks the ancestor-node stack from
     * innermost outward looking for the nearest relational operation, then returns whichever side
     * (left checked first, then right) is a field operand - used to resolve an AR_VALUE literal's
     * enum label against the field it's actually being compared to. Returns null if no enclosing
     * relational operation has a field on either side (can't be an enum in that case).
     */
    private FieldRef findCurrentEnumFieldId() {
        for (int pos = qualLevels.size() - 1; pos >= 0; pos--) {
            QualifierInfo current = qualLevels.get(pos);
            if (current == null || current.getOperation() != QualifierInfo.AR_COND_OP_REL_OP) continue;
            RelationalOperationInfo rel = current.getRelationalOperationInfo();
            FieldRef left = fieldRefOfOperand(rel.getLeftOperand());
            if (left != null) return left;
            FieldRef right = fieldRefOfOperand(rel.getRightOperand());
            if (right != null) return right;
            return null;
        }
        return null;
    }

    private FieldRef fieldRefOfOperand(ArithmeticOrRelationalOperand operand) {
        if (operand == null) return null;
        OperandType type = operand.getType();
        if (type == OperandType.FIELDID) return new FieldRef(secondaryFormName, fieldIdOf(operand.getValue()));
        if (type == OperandType.FIELDID_TRANSACTION || type == OperandType.FIELDID_DB || type == OperandType.FIELDID_CURRENT)
            return new FieldRef(primaryFormName, fieldIdOf(operand.getValue()));
        return null;
    }

    private static boolean needsParen(QualifierInfo operand, int parentOp) {
        return operand != null && operand.getOperation() != parentOp && operand.getOperation() != QualifierInfo.AR_COND_OP_REL_OP;
    }

    private static String relOpText(int relOp) {
        if (relOp == RelationalOperationInfo.AR_REL_OP_EQUAL) return " = ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_GREATER) return " > ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_GREATER_EQUAL) return " >= ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_LESS) return " < ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_LESS_EQUAL) return " <= ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_NOT_EQUAL) return " != ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_LIKE) return " LIKE ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_IN) return " IN ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_NOT_IN) return " NOT IN ";
        if (relOp == RelationalOperationInfo.AR_REL_OP_EXISTS) return " EXISTS ";
        return " ? ";
    }

    private void checkOperand(ArithmeticOrRelationalOperand operand, ArithmeticOrRelationalOperand parent, StringBuilder sb) {
        if (operand == null) return;
        OperandType type = operand.getType();

        if (type == OperandType.FIELDID || type == OperandType.FIELDID_TRANSACTION
            || type == OperandType.FIELDID_DB || type == OperandType.FIELDID_CURRENT) {
            int fieldId = fieldIdOf(operand.getValue());
            String formName = (type == OperandType.FIELDID) ? secondaryFormName : primaryFormName;
            char delimiter = (type == OperandType.FIELDID) ? secondaryDelimiter : primaryDelimiter;
            String prefix = (type == OperandType.FIELDID_TRANSACTION) ? "TR." : (type == OperandType.FIELDID_DB) ? "DB." : "";
            sb.append(delimiter).append(prefix).append(fieldRef(formName, fieldId)).append(delimiter);
        } else if (type == OperandType.VALUE) {
            Value value = (Value) operand.getValue();
            String enumText = enumValueText(value);
            sb.append(enumText != null ? enumText : ValueFormat.format(value));
        } else if (type == OperandType.ARITHMETIC_OP) {
            ArithmeticOperationInfo arith = (ArithmeticOperationInfo) operand.getValue();
            renderArithmetic(arith, operand, parent, sb);
        } else if (type == OperandType.STATUS_HISTORY) {
            sb.append("'").append(statusHistoryOf(operand.getValue())).append("'");
        } else if (type == OperandType.CURRENCY_FLD || type == OperandType.CURRENCY_FLD_TRAN
            || type == OperandType.CURRENCY_FLD_DB || type == OperandType.CURRENCY_FLD_CURRENT) {
            Object raw = operand.getValue();
            int fieldId = fieldIdOf(raw);
            String formName = (type == OperandType.CURRENCY_FLD) ? secondaryFormName : primaryFormName;
            char delimiter = (type == OperandType.CURRENCY_FLD) ? secondaryDelimiter : primaryDelimiter;
            String prefix = (type == OperandType.CURRENCY_FLD_TRAN) ? "TR." : (type == OperandType.CURRENCY_FLD_DB) ? "DB." : "";
            sb.append(delimiter).append(prefix).append(fieldRef(formName, fieldId)).append(currencyPartSuffix(raw)).append(delimiter);
        } else {
            sb.append("<").append(type).append(">");
        }
    }

    /**
     * getValue() on a FIELDID-family/CURRENCY_FLD-family operand can be either a plain Integer (the
     * field ID directly - the common case) or a FieldOperandInfo/CurrencyPartInfo wrapper
     * (aliased/joined-query field references) - handles both rather than assuming one shape and
     * crashing on the other.
     */
    private static int fieldIdOf(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof FieldOperandInfo f) return f.getFieldId();
        if (value instanceof CurrencyPartInfo c) return c.getFieldId();
        return -1;
    }

    /**
     * Java port of DocCurrencyField.cpp's GetResolvedAndLinkedField suffix - a currency operand
     * that isn't AR_CURRENCY_PART_FIELD (the plain field itself) gets a ".VALUE"/".TYPE"/".DATE"
     * suffix (CAREnum::CurrencyPart) or ".{currencyCode}" for AR_CURRENCY_PART_FUNCTIONAL. Returns
     * "" for the plain-field case (no suffix at all) or a non-CurrencyPartInfo value.
     */
    private static String currencyPartSuffix(Object raw) {
        if (!(raw instanceof CurrencyPartInfo c)) return "";
        int part = c.getPartTag();
        if (part == Constants.AR_CURRENCY_PART_FIELD) return "";
        String label;
        if (part == Constants.AR_CURRENCY_PART_VALUE) label = "VALUE";
        else if (part == Constants.AR_CURRENCY_PART_TYPE) label = "TYPE";
        else if (part == Constants.AR_CURRENCY_PART_DATE) label = "DATE";
        else if (part == Constants.AR_CURRENCY_PART_FUNCTIONAL) label = c.getCurrencyCode();
        else label = null;
        return "." + (label != null ? WebUtil.validate(label) : "");
    }

    /**
     * Java port of DocStatusHistoryField.cpp's GetResolvedAndLinkedField - a hyperlinked reference
     * to the schema's AR_CORE_STATUS_HISTORY core field, followed by ".{status label or raw enum
     * value}.{USER|TIME}" (CAREnum::StatHistoryTag). The status label comes from the schema's own
     * AR_CORE_STATUS enum (GlobalFieldIndex.enumLabel), falling back to the raw numeric value when
     * unresolved - matches CARInside::GetFieldEnumValue's empty-string fallback exactly.
     */
    private String statusHistoryOf(Object value) {
        if (!(value instanceof StatusHistoryValueIndicator sh)) return "";
        StringBuilder sb = new StringBuilder(fieldRef(primaryFormName, Constants.AR_CORE_STATUS_HISTORY));
        sb.append(".");
        int enumId = sh.getEnumValue();
        String enumLabel = fieldIndex == null ? null : fieldIndex.enumLabel(primaryFormName, Constants.AR_CORE_STATUS, enumId);
        sb.append(enumLabel != null ? WebUtil.validate(enumLabel) : Integer.toString(enumId));
        sb.append(".");
        sb.append(sh.isUser() ? "USER" : sh.isTime() ? "TIME" : "Unknown");
        return sb.toString();
    }

    /**
     * Java port of core/ARQualification.cpp CheckOperand's AR_DATA_TYPE_INTEGER/AR_DATA_TYPE_ENUM
     * case - resolves an integer/enum literal against the field it's being compared to (via
     * findCurrentEnumFieldId's relOp-walk), returning null (caller falls back to ValueFormat's
     * plain numeric rendering) if the value isn't an integer/enum type, no enclosing field can be
     * found, or the field isn't actually an enum / has no matching item for this value - matching
     * the C++'s "print the raw int if GetFieldEnumValue comes back empty" fallback exactly.
     */
    private String enumValueText(Value value) {
        if (value == null) return null;
        DataType type = value.getDataType();
        if (type != DataType.INTEGER && type != DataType.ENUM) return null;
        if (!(value.getValue() instanceof Integer intVal)) return null;
        FieldRef ref = findCurrentEnumFieldId();
        if (ref == null || fieldIndex == null) return null;
        String label = fieldIndex.enumLabel(ref.formName(), ref.fieldId(), intVal);
        return label == null ? null : "\"" + label + "\"";
    }

    private void renderArithmetic(ArithmeticOperationInfo arith, ArithmeticOrRelationalOperand operand, ArithmeticOrRelationalOperand parent, StringBuilder sb) {
        boolean addBracket = false;
        if (parent != null && parent.getType() == operand.getType()) {
            ArithmeticOperationInfo parentArith = (ArithmeticOperationInfo) parent.getValue();
            int parentPrec = precedence(parentArith.getOperation());
            int currentPrec = precedence(arith.getOperation());
            if (parentPrec < currentPrec
                || (arith.getOperation() == ArithmeticOperationInfo.AR_ARITH_OP_MODULO
                    && parentPrec == currentPrec && parentArith.getRightOperand() == operand)) {
                addBracket = true;
            }
        }

        if (arith.getOperation() == ArithmeticOperationInfo.AR_ARITH_OP_NEGATE) {
            sb.append(" -");
            checkOperand(arith.getRightOperand(), operand, sb);
            return;
        }

        if (addBracket) sb.append("(");
        checkOperand(arith.getLeftOperand(), operand, sb);
        sb.append(arithOpText(arith.getOperation()));
        checkOperand(arith.getRightOperand(), operand, sb);
        if (addBracket) sb.append(")");
    }

    private static int precedence(int arithOp) {
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_ADD || arithOp == ArithmeticOperationInfo.AR_ARITH_OP_SUBTRACT) return 1;
        return 2; // multiply/divide/modulo
    }

    private static String arithOpText(int arithOp) {
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_ADD) return " + ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_SUBTRACT) return " - ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_MULTIPLY) return " * ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_DIVIDE) return " / ";
        if (arithOp == ArithmeticOperationInfo.AR_ARITH_OP_MODULO) return " mod ";
        return " ? ";
    }

    /** Also reused directly by ActionSummaryTable for Set/PushFields field-assignment targets and sources - same hyperlink+existence-check+reference-tracking behavior, just called from a different action context instead of a qualification tree. Attributes the discovery to {@link #currentDetail} - use {@link #fieldRef(String, int, String)} to supply an explicit per-call label instead. */
    public String fieldRef(String formName, int fieldId) {
        return fieldRef(formName, fieldId, currentDetail);
    }

    /** Same as {@link #fieldRef(String, int)}, but attributes the discovery to the given label instead of {@link #currentDetail} - e.g. "Target in 'Set Fields'"/"Value in 'Push Fields'", matching REFM_SETFIELDS_TARGET/REFM_PUSHFIELD_VALUE's real distinct labels. */
    public String fieldRef(String formName, int fieldId, String detail) {
        String name = fieldIndex == null ? null : fieldIndex.fieldName(formName, fieldId);
        if (sink != null) sink.reference(formName, fieldId, name != null, detail);
        if (name == null) {
            return "Field " + fieldId; // matches a missing/unresolvable reference - see FieldReferenceValidator
        }
        // isOverlaid comes from fieldIndex's own getForm() pass (see its javadoc) rather than a
        // hardcoded false - needed so a link into a form that exists only as a hidden overlay base
        // layer (e.g. "User" on some servers/exports) resolves to the real "__o"-suffixed directory
        // instead of a form/ path that was never written.
        boolean isOverlaid = fieldIndex != null && fieldIndex.isOverlaid(formName);
        return URLLink.to(name, Naming.schemaFieldDetail(formName, isOverlaid, fieldId), ImageTag.Id.Document, rootLevel).toHtml();
    }

    /**
     * Same reference-tracking/link-resolution as {@link #fieldRef}, but with a caller-supplied
     * display string instead of the field's real name - Java port of the custom-linkText overload
     * of CARInside::LinkToField used by ARAssignHelper.cpp's AR_FUNCTION_HOVER special case (the
     * HOVER() function's first parameter is a field-id literal, shown as itself - e.g. "12345" or a
     * quoted string - rather than as the target field's name). Falls back to the plain unlinked
     * displayText (matching this port's established "Field N" missing-reference simplification,
     * not the C++'s styled "field not found" span) when the field doesn't resolve.
     */
    public String fieldRefWithText(String formName, int fieldId, String displayText) {
        return fieldRefWithText(formName, fieldId, displayText, currentDetail);
    }

    /** Same as {@link #fieldRefWithText(String, int, String)}, but attributes the discovery to the given label instead of {@link #currentDetail}. */
    public String fieldRefWithText(String formName, int fieldId, String displayText, String detail) {
        String name = fieldIndex == null ? null : fieldIndex.fieldName(formName, fieldId);
        if (sink != null) sink.reference(formName, fieldId, name != null, detail);
        if (name == null) {
            return displayText;
        }
        boolean isOverlaid = fieldIndex != null && fieldIndex.isOverlaid(formName);
        return URLLink.to(displayText, Naming.schemaFieldDetail(formName, isOverlaid, fieldId), ImageTag.Id.Document, rootLevel).toHtml();
    }
}
