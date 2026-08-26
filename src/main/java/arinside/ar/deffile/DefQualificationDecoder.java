package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.util.decode.QualificationDecoder},
 * targeting {@code com.bmc.arsys.api.QualifierInfo}/{@code ArithmeticOrRelationalOperand} directly
 * (the exact shapes {@code arinside.ar.xmlfile.QualifierXmlBuilder} already builds for XML mode -
 * used directly as the client-API reference for constructors/OperandType/AR_REL_OP_* values).
 *
 * <p>Shares one {@link DefValueDecoder} cursor with plain value decoding, matching the real
 * class's own inheritance-based cursor sharing (composition here instead, simpler).
 *
 * <p>Top-level qualifier operator codes map directly onto {@code QualifierInfo.AR_COND_OP_*}
 * (0=none/1=AND/2=OR/3=NOT/4=relational/5=from-field - confirmed identical numbering, same
 * C-API-era convention already relied on throughout this port). Relational operator codes (1-9)
 * map directly onto {@code RelationalOperationInfo.AR_REL_OP_*} the same way. Operator 5 was
 * initially mis-scoped as an obscure "external cross-schema qualification" and left unsupported -
 * real data proved this wrong (375 of 539 real qualification failures in one spike run were this
 * single case) - it's the ordinary field-as-boolean-qualifier feature, see {@link
 * QualifierFromFieldInfo}.
 *
 * <p>Top-level operator 6 (JavaBean-expression-as-boolean-qualifier) and qualification operand type
 * 10 (JavaBean-expression) are fully token-consumed (so they never desync the cursor, unlike an
 * earlier version of this class which threw and lost the whole containing object for these two
 * cases) but still degrade to an empty qualifier / null-value placeholder respectively - a genuine,
 * confirmed client-API limitation, not an effort-driven placeholder: {@code
 * com.bmc.arsys.api.OperandType} has no JavaBean-expression case at all (confirmed via
 * {@code javap -constants} - a closed 19-value enum), so there is nowhere on the client side to put
 * the decoded result even in principle. Operand type 11 (value-expression) IS fully recovered,
 * despite sharing the same "no client OperandType" limitation - the real decoder's own
 * {@code decodeValueExpression()} is just a thin wrapper around an ordinary nested operand, so
 * {@link #decodeOperand} returns that inner operand unwrapped with zero fidelity loss (see its own
 * case 11 javadoc). FUNCTION/VALUE_SET operands are similarly fully token-consumed but render as a
 * generic null-value placeholder - matching {@code QualificationRenderer}'s own already-established
 * "simplified placeholder" treatment of these same operand types, so no rendering fidelity is
 * actually lost by simplifying here too. STATUS_HISTORY (operand type 4) is NOT a placeholder -
 * decoded into a real {@link StatusHistoryValueIndicator} matching the real server's {@code
 * Decoder.decodeStatusHistory()} exactly (confirmed: enumValue first, then a type tag
 * 1=USER/2=TIME - see {@link #decodeOperand}'s case 4 for the citation).
 */
final class DefQualificationDecoder {
    private final DefValueDecoder d;

    private DefQualificationDecoder(DefValueDecoder d) {
        this.d = d;
    }

    static QualifierInfo decode(String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) return null;
        return new DefQualificationDecoder(new DefValueDecoder(encoded, charset)).decodeQualification();
    }

    /** Decodes one qualification against an ALREADY-POSITIONED shared cursor - the "Set/Push Field If" qualifier embedded inline within a field-assignment's own byte stream (see DefAssignDecoder), not a separately-tagged value. */
    static QualifierInfo decodeInline(DefValueDecoder sharedCursor) {
        return new DefQualificationDecoder(sharedCursor).decodeQualification();
    }

    QualifierInfo decodeQualification() {
        if (d.isEmpty()) return null;
        int operator = d.readInt();
        return switch (operator) {
            case 0 -> null;
            case 1 -> new QualifierInfo(QualifierInfo.AR_COND_OP_AND, orEmpty(decodeQualification()), orEmpty(decodeQualification()));
            case 2 -> new QualifierInfo(QualifierInfo.AR_COND_OP_OR, orEmpty(decodeQualification()), orEmpty(decodeQualification()));
            case 3 -> new QualifierInfo(QualifierInfo.AR_COND_OP_NOT, orEmpty(decodeQualification()), null);
            case 4 -> decodeRelational();
            // Confirmed via live data, NOT the obscure "external cross-schema qualification" this
            // was first assumed to be (375/539 real qualification failures were this single case,
            // far too common to be obscure): the domain's ExternalQualificationImpl(int) wraps a
            // single field id, exactly matching the client's own QualifierFromFieldInfo(int) /
            // AR_COND_OP_FROM_FIELD=5 shape - a field used directly as a boolean qualifier, a real,
            // ordinary AR System feature already proven working by arinside.ar.xmlfile.
            // QualifierXmlBuilder's identical "fieldID"/"qualifierFromField" case.
            case 5 -> new QualifierInfo(new QualifierFromFieldInfo(d.readInt()));
            // JavaBean-expression-as-boolean-qualifier - see decodeJavaBeanExpression()'s javadoc
            // for why this can only ever degrade to an empty qualifier, never a real one; consuming
            // the tokens (rather than throwing) keeps the cursor aligned for whatever follows in the
            // same object, so only this one qualifier degrades instead of the whole object failing.
            case 6 -> { decodeJavaBeanExpression(); yield new QualifierInfo(); }
            default -> throw new IllegalStateException("unsupported top-level qualification operator " + operator);
        };
    }

    private QualifierInfo orEmpty(QualifierInfo q) {
        return q != null ? q : new QualifierInfo();
    }

    private QualifierInfo decodeRelational() {
        int relOp = d.readInt();
        if (relOp < 1 || relOp > 9) throw new IllegalStateException("bad relational operator " + relOp);
        ArithmeticOrRelationalOperand left = decodeOperand();
        ArithmeticOrRelationalOperand right = decodeOperand();
        return new QualifierInfo(new RelationalOperationInfo(relOp, left, right));
    }

    private ArithmeticOrRelationalOperand decodeOperand() {
        int type = d.readInt();
        return switch (type) {
            case 1 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID.toInt(), d.readInt());
            case 2 -> new ArithmeticOrRelationalOperand(d.decodeValue());
            case 3 -> decodeArithmetic();
            // STATUS_HISTORY - matches the real server's Decoder.decodeStatusHistory() exactly
            // (confirmed - enumValue first, then type 1=USER/2=TIME,
            // see StatusHistoryValueIndicator.StatusHistoryValueIndicatorType): a bare two-int read,
            // NOT the space-delimited "<enumValue> <type>" single-string encoding
            // decodeStatusHistory2() uses for a Set-Fields assignment value (DefAssignDecoder) -
            // those are two different real methods on the same server-side Decoder base class, do
            // not conflate them.
            case 4 -> {
                int enumValue = d.readInt();
                int userOrTime = d.readInt();
                yield new ArithmeticOrRelationalOperand(new StatusHistoryValueIndicator(userOrTime == 1, enumValue));
            }
            case 5 -> new ArithmeticOrRelationalOperand(d.decodeValues());
            case 6 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD, decodeCurrencyPart());
            case 9 -> decodeFunction();
            case 50 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_TRANSACTION.toInt(), d.readInt());
            case 51 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_DB.toInt(), d.readInt());
            case 54 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_TRAN, decodeCurrencyPart());
            case 55 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_DB, decodeCurrencyPart());
            case 56 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_CURRENT, decodeCurrencyPart());
            case 99 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_CURRENT.toInt(), d.readInt());
            // JAVABEAN(10) - see decodeJavaBeanExpression()'s javadoc: the client API's OperandType
            // enum (confirmed via javap -constants: FIELDID/VALUE/ARITHMETIC_OP/STATUS_HISTORY/
            // FUNCTION/CASE/VALUE_SET/CURRENCY_FLD(+3 variants)/FIELDID_(TRANSACTION/DB/CURRENT)/
            // LOCAL_VARIABLE/QUERY_INFO/VALUE_SET_QUERY/REGULAR_COMPLEX_QUERY/FIELD_ALIAS/
            // LITERAL_ALIAS - 19 total, no 20th) has no case for this at all, unlike every other
            // operand type this decoder handles - there's no client-side representation to decode
            // INTO even in principle, so this is a genuine (not effort-driven) placeholder, same
            // FUNCTION/VALUE_SET treatment, not a "not gotten to it yet" gap.
            case 10 -> { decodeJavaBeanExpression(); yield nullOperand(); }
            // VALUE_EXPRESSION(11) - unlike JAVABEAN(10), this one IS fully recoverable: the real
            // decoder's decodeValueExpression() is just a thin wrapper (one throwaway int + one
            // recursive operand decode) around an ordinary operand - ValueExpressionImpl exists
            // purely for the server's own evaluation-context bookkeeping, not because the wrapped
            // operand renders any differently. Returning the inner operand directly (unwrapped,
            // since the client API has no ValueExpression OperandType either) renders correctly
            // with zero fidelity loss - confirmed by reading QualificationDecoder.java's
            // decodeValueExpression() in full.
            case 11 -> { d.readInt(); yield decodeOperand(); }
            default -> nullOperand();
        };
    }

    private ArithmeticOrRelationalOperand nullOperand() {
        return new ArithmeticOrRelationalOperand(new Value());
    }

    private ArithmeticOrRelationalOperand decodeArithmetic() {
        int op = d.readInt();
        if (op >= 1 && op <= 5) {
            ArithmeticOrRelationalOperand left = decodeOperand();
            ArithmeticOrRelationalOperand right = decodeOperand();
            return new ArithmeticOrRelationalOperand(new ArithmeticOperationInfo(op, left, right));
        }
        if (op == 6) { // NEGATE (unary) - QualificationRenderer reads getRightOperand() for this case, see class javadoc
            ArithmeticOrRelationalOperand operand = decodeOperand();
            return new ArithmeticOrRelationalOperand(new ArithmeticOperationInfo(ArithmeticOperationInfo.AR_ARITH_OP_NEGATE, operand, operand));
        }
        return nullOperand();
    }

    /** currFieldId\partTag\(currencyCode if partTag==functional)\ */
    private CurrencyPartInfo decodeCurrencyPart() {
        int fieldId = d.readInt();
        int partTag = d.readInt();
        String code = partTag == Constants.AR_CURRENCY_PART_FUNCTIONAL ? d.readString() : "";
        return new CurrencyPartInfo(partTag, fieldId, code);
    }

    /**
     * Java port of the real server's {@code Decoder.decodeJavaBeanExpression()} - token-consumption only, no return value, since there is nowhere on the client side to
     * put the result (see the two call sites' javadoc). ContextType int, then a count-prefixed list
     * of one of 6 flat property subtypes (int tag 1=INTEGER/2=STRING/3=ARRAY/4=MAP/5=ASSOCIATION/
     * 6=RECORD_INSTANCE, per {@code JavaBeanProperty.PropertyType}'s own
     * int mapping directly) - each 1-3 name/key/index string-or-int reads, no
     * recursion. Consuming these correctly (rather than throwing) is what lets a JavaBean-expression
     * qualifier degrade gracefully to an empty/placeholder result for just itself, instead of
     * corrupting the cursor for whatever real, useful data follows it in the same object.
     */
    private void decodeJavaBeanExpression() {
        d.readInt(); // ContextType - unused, no client-side equivalent
        int numProps = d.readInt();
        for (int i = 0; i < numProps; i++) {
            int propType = d.readInt();
            switch (propType) {
                case 1 -> d.readInt(); // INTEGER
                case 2 -> d.readString(d.readInt()); // STRING: name
                case 3 -> { d.readString(d.readInt()); d.readInt(); } // ARRAY: name, index
                case 4 -> { d.readString(d.readInt()); d.readString(d.readInt()); } // MAP: name, key
                case 5 -> { d.readString(d.readInt()); d.readString(d.readInt()); d.readString(d.readInt()); } // ASSOCIATION: name, participantRoleName, index
                case 6 -> { d.readString(d.readInt()); d.readString(d.readInt()); d.readString(d.readInt()); } // RECORD_INSTANCE: name, recordInstanceId, fieldId
                default -> { /* unrecognized subtype - nothing more can be safely consumed; matches the real decoder's own switch having no default case either */ }
            }
        }
    }

    private ArithmeticOrRelationalOperand decodeFunction() {
        int funcCode = d.readInt();
        int numParams = d.readInt();
        List<ArithmeticOrRelationalOperand> params = new ArrayList<>();
        for (int i = 0; i < numParams; i++) params.add(decodeOperand());
        // FunctionCode has no int-based client lookup found in this jar (unlike Keyword/OperandType) -
        // QualificationRenderer already treats FUNCTION as a generic placeholder regardless (see
        // class javadoc), so the parameter list is decoded (for correct cursor alignment) but not
        // wrapped in a real FunctionOperandInfo - funcCode itself is unused, matching that scope.
        return nullOperand();
    }
}
