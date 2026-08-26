package arinside.ar.xmlfile;

import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.FunctionAssignInfo;
import com.bmc.arsys.api.RelationalOperationInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * String-to-int lookup tables for the AR System .xml export's enum vocabulary, built from the AR
 * Java API's own {@link Constants} values. Anything not in a table falls back to a logged default
 * rather than throwing, since the export's long tail of rarely-used enum values can't be fully
 * enumerated up front - unknowns should show up as log warnings to fix incrementally, not crash
 * the parse.
 */
final class XmlEnums {
    private XmlEnums() {}

    static int relOp(String s) {
        return switch (s) {
            case "equal" -> RelationalOperationInfo.AR_REL_OP_EQUAL;
            case "notEqual" -> RelationalOperationInfo.AR_REL_OP_NOT_EQUAL;
            case "greater" -> RelationalOperationInfo.AR_REL_OP_GREATER;
            case "greaterEqual" -> RelationalOperationInfo.AR_REL_OP_GREATER_EQUAL;
            case "less" -> RelationalOperationInfo.AR_REL_OP_LESS;
            case "lessEqual" -> RelationalOperationInfo.AR_REL_OP_LESS_EQUAL;
            case "like" -> RelationalOperationInfo.AR_REL_OP_LIKE;
            case "in" -> RelationalOperationInfo.AR_REL_OP_IN;
            case "notIn" -> RelationalOperationInfo.AR_REL_OP_NOT_IN;
            case "exists" -> RelationalOperationInfo.AR_REL_OP_EXISTS;
            default -> warnUnknown("relationalOperation operation", s, RelationalOperationInfo.AR_REL_OP_EQUAL);
        };
    }

    static int arithOp(String s) {
        return switch (s) {
            case "add" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_ADD;
            case "subtract" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_SUBTRACT;
            case "multiply" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_MULTIPLY;
            case "divide" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_DIVIDE;
            case "modulo" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_MODULO;
            case "negate" -> com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_NEGATE;
            default -> warnUnknown("arithmetic operation", s, com.bmc.arsys.api.ArithmeticOperationInfo.AR_ARITH_OP_ADD);
        };
    }

    static int noMatchOption(String s) {
        return switch (s) {
            case "error" -> Constants.AR_NO_MATCH_ERROR;
            case "setNull" -> Constants.AR_NO_MATCH_SET_NULL;
            case "noAction" -> Constants.AR_NO_MATCH_NO_ACTION;
            case "submit" -> Constants.AR_NO_MATCH_SUBMIT;
            default -> warnUnknown("noMatchOption", s, Constants.AR_NO_MATCH_ERROR);
        };
    }

    static int multiMatchOption(String s) {
        return switch (s) {
            case "displayPickList" -> 1;
            case "useFirstMatch" -> 2;
            case "modifyAll" -> Constants.AR_MULTI_MATCH_MODIFY_ALL;
            case "noAction" -> 6;
            case "setNull" -> 7;
            case "useLocale" -> 8;
            case "error" -> 9;
            default -> warnUnknown("multipleMatchOption", s, 1);
        };
    }

    /** ActiveLink.executeMask - a bitmask, so an active link's XML may repeat &lt;executeOn&gt; several times; OR the bits together. */
    static int activeLinkExecuteOnBit(String s) {
        return switch (s) {
            case "none" -> Constants.AR_EXECUTE_ON_NONE;
            case "button" -> Constants.AR_EXECUTE_ON_BUTTON;
            case "return" -> Constants.AR_EXECUTE_ON_RETURN;
            case "submit" -> Constants.AR_EXECUTE_ON_SUBMIT;
            case "modify" -> Constants.AR_EXECUTE_ON_MODIFY;
            case "display" -> Constants.AR_EXECUTE_ON_DISPLAY;
            case "modifyAll" -> Constants.AR_EXECUTE_ON_MODIFY_ALL;
            case "menuOpen" -> Constants.AR_EXECUTE_ON_MENU_OPEN;
            case "menuChoice" -> Constants.AR_EXECUTE_ON_MENU_CHOICE;
            case "loseFocus" -> Constants.AR_EXECUTE_ON_LOSE_FOCUS;
            case "setDefault" -> Constants.AR_EXECUTE_ON_SET_DEFAULT;
            case "query" -> Constants.AR_EXECUTE_ON_QUERY;
            case "afterModify" -> Constants.AR_EXECUTE_ON_AFTER_MODIFY;
            case "afterSubmit" -> Constants.AR_EXECUTE_ON_AFTER_SUBMIT;
            case "gainFocus" -> Constants.AR_EXECUTE_ON_GAIN_FOCUS;
            case "windowOpen" -> Constants.AR_EXECUTE_ON_WINDOW_OPEN;
            case "windowClose" -> Constants.AR_EXECUTE_ON_WINDOW_CLOSE;
            case "undisplay" -> Constants.AR_EXECUTE_ON_UNDISPLAY;
            case "copySubmit" -> Constants.AR_EXECUTE_ON_COPY_SUBMIT;
            case "windowLoaded" -> Constants.AR_EXECUTE_ON_LOADED;
            case "onInterval" -> Constants.AR_EXECUTE_ON_INTERVAL;
            case "onEvent" -> Constants.AR_EXECUTE_ON_EVENT;
            case "tableContentChange" -> Constants.AR_EXECUTE_ON_TABLE_CONTENT_CHANGE;
            case "hoverOnFieldLabel" -> Constants.AR_EXECUTE_ON_HOVER_FIELD_LABEL;
            case "hoverOnFieldData" -> Constants.AR_EXECUTE_ON_HOVER_FIELD_DATA;
            case "hoverOnField" -> Constants.AR_EXECUTE_ON_HOVER_FIELD;
            case "expand" -> Constants.AR_EXECUTE_ON_PAGE_EXPAND;
            case "collapse" -> Constants.AR_EXECUTE_ON_PAGE_COLLAPSE;
            case "drag" -> Constants.AR_EXECUTE_ON_DRAG;
            case "drop" -> Constants.AR_EXECUTE_ON_DROP;
            default -> warnUnknown("activeLink executeOn", s, 0);
        };
    }

    /** Filter/Escalation.opSet - a bitmask over AR_OPERATION_*; a filter's XML may repeat &lt;executeOn&gt; several times. */
    static int filterOpBit(String s) {
        return switch (s) {
            case "getEntry" -> Constants.AR_OPERATION_GET;
            case "modify" -> Constants.AR_OPERATION_SET;
            case "submit" -> Constants.AR_OPERATION_CREATE;
            case "delete" -> Constants.AR_OPERATION_DELETE;
            case "merge" -> Constants.AR_OPERATION_MERGE;
            case "guide" -> Constants.AR_OPERATION_GUIDE;
            case "svcAction" -> Constants.AR_OPERATION_SERVICE;
            default -> warnUnknown("filter/escalation executeOn", s, 0);
        };
    }

    static int fullTextOption(String s) {
        return switch (s) {
            case "none" -> Constants.AR_FULLTEXT_OPTIONS_NONE;
            case "indexed" -> Constants.AR_FULLTEXT_OPTIONS_INDEXED;
            case "literal" -> Constants.AR_FULLTEXT_OPTIONS_LITERAL;
            case "excludeFieldBased" -> Constants.AR_FULLTEXT_OPTIONS_EXCLUDE_FIELD_BASED;
            case "mfs only" -> Constants.AR_FULLTEXT_OPTIONS_INDEXED;
            default -> warnUnknown("fullTextOption", s, Constants.AR_FULLTEXT_OPTIONS_NONE);
        };
    }

    static int qbeMatch(String s) {
        return switch (s) {
            case "anyMatch" -> Constants.AR_QBE_MATCH_ANYWHERE;
            case "leadingMatch" -> Constants.AR_QBE_MATCH_LEADING;
            case "equalMatch", "equalityMatch" -> Constants.AR_QBE_MATCH_EQUAL;
            default -> warnUnknown("queryByExample", s, Constants.AR_QBE_MATCH_ANYWHERE);
        };
    }

    /** functionType XML strings (camelCase, e.g. "rightTrim") to AR_FUNCTION_* codes, via a small table built once from FunctionAssignInfo.toFuncCode's own accepted names where they line up, plus explicit overrides where the XML's naming differs. */
    private static final Map<String, Integer> FUNCTION_CODES = buildFunctionCodes();

    /**
     * An out-of-[1,93]-range code isn't just "wrong" for an unrecognized functionType -
     * ActionSummaryTable's functionOf() passes it straight into FunctionAssignInfo.toFuncName(),
     * whose internal lookup table indexes by (code - 1) with no bounds check, throwing an
     * uncaught IndexOutOfBoundsException instead of the documented ARException for an invalid code.
     * Falling back to AR_FUNCTION_DATE (1, the lowest real function code) is always in range - it
     * renders the wrong function name for a genuinely-unrecognized one, but never crashes the whole
     * page over it.
     */
    static int functionCode(String s) {
        Integer code = FUNCTION_CODES.get(s);
        if (code != null) return code;
        try {
            int code2 = FunctionAssignInfo.toFuncCode(s);
            if (code2 >= 1) return code2;
        } catch (Exception ignored) {
            // fall through to the unknown-function warning below
        }
        return warnUnknown("functionType", s, Constants.AR_FUNCTION_DATE);
    }

    private static Map<String, Integer> buildFunctionCodes() {
        Map<String, Integer> m = new HashMap<>();
        // AR_FUNCTION_* constants, keyed by the XML export's camelCase functionType spelling.
        m.put("date", Constants.AR_FUNCTION_DATE);
        m.put("time", Constants.AR_FUNCTION_TIME);
        m.put("month", Constants.AR_FUNCTION_MONTH);
        m.put("day", Constants.AR_FUNCTION_DAY);
        m.put("year", Constants.AR_FUNCTION_YEAR);
        m.put("weekday", Constants.AR_FUNCTION_WEEKDAY);
        m.put("hour", Constants.AR_FUNCTION_HOUR);
        m.put("minute", Constants.AR_FUNCTION_MINUTE);
        m.put("second", Constants.AR_FUNCTION_SECOND);
        m.put("truncate", Constants.AR_FUNCTION_TRUNC);
        m.put("round", Constants.AR_FUNCTION_ROUND);
        m.put("convert", Constants.AR_FUNCTION_CONVERT);
        m.put("length", Constants.AR_FUNCTION_LENGTH);
        m.put("upper", Constants.AR_FUNCTION_UPPER);
        m.put("lower", Constants.AR_FUNCTION_LOWER);
        m.put("substring", Constants.AR_FUNCTION_SUBSTR);
        m.put("left", Constants.AR_FUNCTION_LEFT);
        m.put("right", Constants.AR_FUNCTION_RIGHT);
        m.put("leftTrim", Constants.AR_FUNCTION_LTRIM);
        m.put("rightTrim", Constants.AR_FUNCTION_RTRIM);
        m.put("leftPad", Constants.AR_FUNCTION_LPAD);
        m.put("rightPadC", Constants.AR_FUNCTION_RPAD);
        m.put("replace", Constants.AR_FUNCTION_REPLACE);
        m.put("strstr", Constants.AR_FUNCTION_STRSTR);
        m.put("min", Constants.AR_FUNCTION_MIN);
        m.put("max", Constants.AR_FUNCTION_MAX);
        m.put("columnSum", Constants.AR_FUNCTION_COLSUM);
        m.put("columnCount", Constants.AR_FUNCTION_COLCOUNT);
        m.put("columnAvg", Constants.AR_FUNCTION_COLAVG);
        m.put("columnMin", Constants.AR_FUNCTION_COLMIN);
        m.put("columnMax", Constants.AR_FUNCTION_COLMAX);
        m.put("dateAdd", Constants.AR_FUNCTION_DATEADD);
        m.put("dateDiff", Constants.AR_FUNCTION_DATEDIFF);
        m.put("dateName", Constants.AR_FUNCTION_DATENAME);
        m.put("dateNum", Constants.AR_FUNCTION_DATENUM);
        m.put("currencyConvert", Constants.AR_FUNCTION_CURRCONVERT);
        m.put("currencySetDate", Constants.AR_FUNCTION_CURRSETDATE);
        m.put("currencySetType", Constants.AR_FUNCTION_CURRSETTYPE);
        m.put("currencySetValue", Constants.AR_FUNCTION_CURRSETVALUE);
        m.put("lengthC", Constants.AR_FUNCTION_LENGTHC);
        m.put("leftC", Constants.AR_FUNCTION_LEFTC);
        m.put("rightC", Constants.AR_FUNCTION_RIGHTC);
        m.put("leftPadC", Constants.AR_FUNCTION_LPADC);
        m.put("strstrc", Constants.AR_FUNCTION_STRSTRC);
        m.put("substringc", Constants.AR_FUNCTION_SUBSTRC);
        m.put("encrypt", Constants.AR_FUNCTION_ENCRYPT);
        m.put("decrpyt", Constants.AR_FUNCTION_DECRYPT);
        m.put("hover", Constants.AR_FUNCTION_HOVER);
        m.put("template", Constants.AR_FUNCTION_TEMPLATE);
        m.put("selectedRowCount", Constants.AR_FUNCTION_SELECTEDROWCOUNT);
        m.put("mapGet", Constants.AR_FUNCTION_MAPGET);
        m.put("listGet", Constants.AR_FUNCTION_LISTGET);
        m.put("listSize", Constants.AR_FUNCTION_LISTSIZE);
        m.put("visibleRows", Constants.AR_FUNCTION_VISIBLEROWS);
        m.put("quarter", Constants.AR_FUNCTION_QUARTER);
        m.put("week", Constants.AR_FUNCTION_WEEK);
        m.put("workday", Constants.AR_FUNCTION_FIRSTDAYOF);
        return m;
    }

    private static int warnUnknown(String what, String value, int fallback) {
        System.out.println("[WARN] xmlfile: unrecognized " + what + " value '" + value + "', defaulting to " + fallback);
        return fallback;
    }
}
