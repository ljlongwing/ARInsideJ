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
 * <p><b>Deliberately unsupported</b> (thrown as a plain {@link IllegalStateException}, caught by
 * {@link DefFileParser}'s existing per-object recovery so one exotic qualification fails only its
 * own object, not the whole file - safer than guessing at token consumption and silently
 * misaligning the cursor for whatever follows): top-level operator 6 (JavaBean-expression) and the
 * JavaBean-expression/value-expression operand types (10/11) - confirmed real but genuinely rare in
 * live data (under 0.6% of real AL/Filter/Escalation objects combined, per the same spike run).
 * STATUS_HISTORY/FUNCTION/VALUE_SET operands ARE fully token-consumed (so they never desync the
 * cursor) but render as a generic null-value placeholder - matching {@code QualificationRenderer}'s
 * own already-established "simplified placeholder" treatment of these same operand types, so no
 * rendering fidelity is actually lost by simplifying here too.
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
            case 4 -> { d.readInt(); d.readInt(); yield nullOperand(); } // STATUS_HISTORY - placeholder, see class javadoc
            case 5 -> new ArithmeticOrRelationalOperand(d.decodeValues());
            case 6 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD, decodeCurrencyPart());
            case 9 -> decodeFunction();
            case 50 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_TRANSACTION.toInt(), d.readInt());
            case 51 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_DB.toInt(), d.readInt());
            case 54 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_TRAN, decodeCurrencyPart());
            case 55 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_DB, decodeCurrencyPart());
            case 56 -> new ArithmeticOrRelationalOperand(OperandType.CURRENCY_FLD_CURRENT, decodeCurrencyPart());
            case 99 -> new ArithmeticOrRelationalOperand(OperandType.FIELDID_CURRENT.toInt(), d.readInt());
            case 10, 11 -> throw new IllegalStateException("unsupported qualification operand type " + type + " (JavaBean/value expression)");
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
