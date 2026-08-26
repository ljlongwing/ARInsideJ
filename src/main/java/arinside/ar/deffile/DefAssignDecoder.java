package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.util.decode.AssignDecoder} (ported from the
 * real AR Server), targeting
 * {@code com.bmc.arsys.api.AssignInfo} directly (the exact shape {@code
 * arinside.ar.xmlfile.AssignInfoXmlBuilder} already builds for XML mode - used directly as the
 * client-API reference). Used for {@code set-field}/{@code push-field} action tags on Active
 * Links/Filters/Escalations.
 *
 * <p>The DEF format's own inner assignment-type tag numbering (0=none/1=value/2=field/3=process/
 * 4=arith/5=function/7=sql/8=filterAPI) matches {@code AssignInfo.AR_ASSIGN_TYPE_*} exactly -
 * confirmed via javap, same C-API-era numbering convention already relied on throughout this file -
 * so most cases pass the raw tag straight through with no translation table.
 *
 * <p><b>Deliberately unsupported</b> (see {@link DefQualificationDecoder}'s javadoc for the same
 * reasoning): tag 6 (DDE - not actually reachable via this generic path even in the real decoder,
 * DDE actions are a distinct top-level action type handled elsewhere, not a nested assignment),
 * tag 9 (unused), tags 10/11 (JavaBean expression / custom action - obscure, thrown so one exotic
 * assignment fails only its own object via {@link DefFileParser}'s existing per-object recovery).
 * Tag 12 (ValueExpression) is a thin wrapper the domain re-decodes as a fresh nested assignment and
 * wraps in a marker type with no client-API equivalent - simplified here by returning the
 * recursively-decoded inner assignment directly, unwrapped (drops a meaningless wrapper, keeps the
 * real data).
 */
final class DefAssignDecoder {
    private final DefValueDecoder d;

    private DefAssignDecoder(DefValueDecoder d) {
        this.d = d;
    }

    static AssignInfo decode(String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) return null;
        return new DefAssignDecoder(new DefValueDecoder(encoded, charset)).decodeAssignment(false);
    }

    /** Decodes one assignment against an ALREADY-POSITIONED shared cursor - used for indexed field-assignment lists (Open/Close Window input/output mappings: {@code numAssignments\(fieldId\<assignment>)*}) where each entry's assignment consumes a variable number of tokens from one shared stream. */
    static AssignInfo decodeInline(DefValueDecoder sharedCursor) {
        return new DefAssignDecoder(sharedCursor).decodeAssignment(false);
    }

    /** Same as {@link #decodeInline} but forces the field-assignment tag (matches {@code decodePushFields}'s target half, which the real decoder always forces to tag 2 - a push target is always a plain field reference, never a value/arithmetic/etc.). */
    static AssignInfo decodeInlinePushTarget(DefValueDecoder sharedCursor) {
        return new DefAssignDecoder(sharedCursor).decodeAssignment(true);
    }

    /** set-field/push-field pair: target is always a plain field reference (tag forced to 2), value is a normal assignment of any type. */
    static AssignInfo[] decodePushFields(String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) return null;
        DefAssignDecoder dec = new DefAssignDecoder(new DefValueDecoder(encoded, charset));
        AssignInfo target = dec.decodeAssignment(true);
        AssignInfo value = dec.decodeAssignment(false);
        return new AssignInfo[]{target, value};
    }

    private AssignInfo decodeAssignment(boolean forcedField) {
        int tag = forcedField ? 2 : d.readInt();
        if (tag > 100) tag -= 100;

        AssignInfo info = new AssignInfo();
        switch (tag) {
            case 0 -> {
                return null;
            }
            case 1 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_VALUE);
                info.setValue(d.decodeValue());
            }
            case 2 -> {
                AssignFieldInfo field = decodeFieldAssignment();
                if (field == null) return null; // matches the real decoder's own "unhandled sub-tag -> null" quirk
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FIELD);
                info.setField(field);
            }
            case 3 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_PROCESS);
                info.setProcess(d.readString(d.readInt()));
            }
            case 4 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_ARITH);
                info.setArithOp(decodeArith());
            }
            case 5 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FUNCTION);
                info.setFunction(decodeFunc());
            }
            case 7 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_SQL);
                info.setSql(decodeSql());
            }
            case 8 -> {
                info.setAssignType(AssignInfo.AR_ASSIGN_TYPE_FILTER_API);
                info.setFilterApi(decodeFilterApi());
            }
            case 12 -> {
                int len = d.readInt();
                String nested = d.readString(len);
                return decode(nested, null); // ValueExpression - unwrap, see class javadoc
            }
            case 6, 9 -> {
                return null;
            }
            case 10, 11 -> throw new IllegalStateException("unsupported assignment type " + tag + " (JavaBean expression / custom action)");
            default -> {
                return null;
            }
        }
        return info;
    }

    /** server\schema\assignTag\(fieldId if 1 | statHistory if 4 | currencyPart if 6)\<inline "Set If" qualifier bytes>noMatch\multiMatch\ - the qualifier/noMatch/multiMatch trailer is only present (and only read) when assignTag matched one of 1/4/6, exactly matching the real decoder's own control flow. */
    private AssignFieldInfo decodeFieldAssignment() {
        String server = d.readString(d.readInt());
        String schema = d.readString(d.readInt());
        int assignTag = d.readInt();

        AssignFieldInfo op = null;
        if (assignTag == AssignFieldInfo.AR_FIELD) {
            op = new AssignFieldInfo();
            op.setFieldId(d.readInt());
        } else if (assignTag == AssignFieldInfo.AR_STAT_HISTORY) {
            op = new AssignFieldInfo();
            op.setStatHistory(decodeStatusHistory2());
        } else if (assignTag == AssignFieldInfo.AR_CURRENCY_FLD) {
            CurrencyPartInfo cpi = decodeCurrencyPart();
            if (cpi != null) {
                op = new AssignFieldInfo();
                op.setCurrencyPart(cpi);
            }
        }

        if (op != null) {
            op.setTag(assignTag);
            QualifierInfo qual = DefQualificationDecoder.decodeInline(d);
            op.setQualifier(qual);
            op.setNoMatchOption(d.readInt());
            op.setMultiMatchOption(d.readInt());
            op.setServer(server);
            op.setForm(schema);
        }
        return op;
    }

    /** "<longEnumValue> <intType>" - a single space-delimited token, not backslash-delimited like everything else in this format (matches the real Decoder.decodeStatusHistory2() exactly). */
    private StatusHistoryValueIndicator decodeStatusHistory2() {
        String s = d.readString();
        int space = s.indexOf(' ');
        if (space < 0) return new StatusHistoryValueIndicator();
        int enumValue;
        try {
            long l = Long.parseLong(s.substring(0, space));
            enumValue = (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? (int) l : -1;
        } catch (NumberFormatException e) {
            enumValue = 0;
        }
        return new StatusHistoryValueIndicator(false, enumValue); // isUser/isTime unknown from this encoding - see class javadoc precedent (STATUS_HISTORY is a placeholder-rendered type throughout this port)
    }

    /** currFieldId\partTag\(currencyCode if partTag==functional)\ - identical packed shape to DefQualificationDecoder's own currency-part operand. */
    private CurrencyPartInfo decodeCurrencyPart() {
        int fieldId = d.readInt();
        int partTag = d.readInt();
        String code = partTag == Constants.AR_CURRENCY_PART_FUNCTIONAL ? d.readString() : "";
        return new CurrencyPartInfo(partTag, fieldId, code);
    }

    /** arithOp(1-6)\<operand>(\<operand> unless NEGATE/6, unary)\ */
    private ArithOpAssignInfo decodeArith() {
        int op = d.readInt();
        if (op < 1 || op > 6) return new ArithOpAssignInfo(ArithmeticOperationInfo.AR_ARITH_OP_ADD, new AssignInfo(), new AssignInfo());
        AssignInfo left = orEmpty(decodeAssignment(false));
        if (op == 6) { // NEGATE (unary) - ActionSummaryTable reads getOperandRight() for this case, confirmed via source
            return new ArithOpAssignInfo(op, left, left);
        }
        AssignInfo right = orEmpty(decodeAssignment(false));
        return new ArithOpAssignInfo(op, left, right);
    }

    private AssignInfo orEmpty(AssignInfo a) {
        return a != null ? a : new AssignInfo();
    }

    /** funcCode\numParams\<operand>*numParams */
    private FunctionAssignInfo decodeFunc() {
        int funcCode = d.readInt();
        int numParams = d.readInt();
        List<AssignInfo> params = new ArrayList<>();
        for (int i = 0; i < numParams; i++) params.add(orEmpty(decodeAssignment(false)));
        return new FunctionAssignInfo(funcCode, params);
    }

    /** serverLen\server\commandLen\command\valueIndex\noMatch\multiMatch\ */
    private AssignSQLInfo decodeSql() {
        String server = d.readString(d.readInt());
        String command = d.readString(d.readInt());
        int valueIndex = d.readInt();
        int noMatch = d.readInt();
        int multiMatch = d.readInt();
        return new AssignSQLInfo(server, command, noMatch, multiMatch, valueIndex);
    }

    /** serviceLen\service\numInputs\<operand>*numInputs\valueIndex\ */
    private AssignFilterApiInfo decodeFilterApi() {
        String service = d.readString(d.readInt());
        int numInputs = d.readInt();
        List<AssignInfo> inputs = new ArrayList<>();
        for (int i = 0; i < numInputs; i++) inputs.add(orEmpty(decodeAssignment(false)));
        long valueIndex = d.readInt();
        return new AssignFilterApiInfo(service, inputs, valueIndex);
    }
}
