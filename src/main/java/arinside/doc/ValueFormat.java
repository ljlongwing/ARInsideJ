package arinside.doc;

import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.DataType;
import com.bmc.arsys.api.Keyword;
import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.api.Value;

/**
 * Java port of the AR_VALUE-rendering half of core/ARQualification.cpp's CheckOperand (the
 * `case AR_VALUE:` switch on `data->dataType`) - shared by field default-value display and the
 * qualification renderer, since both need "how do I print this literal value" and the C++ answers
 * it the same way in both places.
 */
public final class ValueFormat {
    private ValueFormat() {}

    public static String format(Value value) {
        if (value == null) return "";
        DataType type = value.getDataType();
        Object v = value.getValue();
        if (type == DataType.NULL || v == null) return "$NULL$";
        if (type == DataType.KEYWORD) return "$" + v + "$";
        if (type == DataType.INTEGER || type == DataType.ENUM) return v.toString();
        if (type == DataType.REAL || type == DataType.DECIMAL) return v.toString();
        if (type == DataType.CHAR || type == DataType.DIARY) return "\"" + v + "\"";
        if (type == DataType.TIME && v instanceof Timestamp t) return "\"" + DateTimeFormat.toHtmlString(t.getValue()) + "\"";
        if (type == DataType.CURRENCY) return v.toString();
        return v.toString();
    }
}
