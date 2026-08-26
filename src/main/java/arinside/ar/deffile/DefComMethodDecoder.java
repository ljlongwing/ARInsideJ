package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.util.decode.COMMethodDecoder} (ported from
 * the real AR Server for the
 * established port process), targeting {@code com.bmc.arsys.api.COMMethodInfo}/
 * {@code COMMethodParmInfo}/{@code COMValueInfo} directly - the exact shapes an
 * {@code OleAutomationAction}'s method list needs.
 *
 * <p>Previously left as an always-empty list here ("COM-method decoding not ported (obscure, no
 * DecodeCOMMethods port)") - the real decoder turned out to exist under a different class name
 * than expected and is a flat, non-recursive token decoder with no runtime COM/OLE dependency at
 * all (it only reconstructs the already-serialized method/parameter description captured at
 * `.def`-export time; the live COM/OLE call only happens when the action actually executes on a
 * real server). Shares one {@link DefValueDecoder} cursor with plain value decoding, same
 * composition pattern {@link DefQualificationDecoder} already uses.
 *
 * <p>Two leading ints per method/param (before the name) and the value-IID "size" string are read
 * and discarded, matching the real decoder exactly - they're real fields in the wire format with no
 * corresponding accessor on the client API's {@code COMMethodInfo}/{@code COMMethodParmInfo}
 * (whose constructors don't have slots for them), so there's nowhere to put them even though the
 * bytes are real. The real decoder wraps its value-IID read in a try/catch purely because its own
 * cursor implementation can throw past end-of-buffer; {@link DefValueDecoder#readString()} never
 * throws (returns "" instead), so no equivalent try/catch is needed here.
 */
final class DefComMethodDecoder {
    private final DefValueDecoder d;

    private DefComMethodDecoder(DefValueDecoder d) {
        this.d = d;
    }

    static List<COMMethodInfo> decode(String encoded, Charset charset) {
        if (encoded == null || encoded.isEmpty()) return new ArrayList<>();
        return new DefComMethodDecoder(new DefValueDecoder(encoded, charset)).decodeMethodInfos();
    }

    private List<COMMethodInfo> decodeMethodInfos() {
        int count = d.readInt();
        List<COMMethodInfo> methods = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            d.readInt(); // discarded - see class javadoc
            String methodName = d.readString();
            d.readInt(); // discarded - see class javadoc
            String methodIId = d.readString();
            int methodType = d.readInt();
            COMValueInfo methodValue = decodeComValue();
            List<COMMethodParmInfo> params = decodeMethodParms();
            methods.add(new COMMethodInfo(methodName, methodIId, methodType, methodValue, params));
        }
        return methods;
    }

    private List<COMMethodParmInfo> decodeMethodParms() {
        int count = d.readInt();
        List<COMMethodParmInfo> params = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            d.readInt(); // discarded - see class javadoc
            String parmName = d.readString();
            int parmType = d.readInt();
            COMValueInfo parmValue = decodeComValue();
            params.add(new COMMethodParmInfo(parmName, parmType, parmValue));
        }
        return params;
    }

    private COMValueInfo decodeComValue() {
        String valueIId = null;
        String sizeStr = d.readString();
        if (!sizeStr.isEmpty()) {
            valueIId = d.readString();
        }
        int transId = d.readInt();
        int valueType = d.readInt();
        int fieldId = -1;
        Value value = null;
        switch (valueType) {
            case 0 -> d.readString(); // literal string value, discarded (no accessor slot on COMValueInfo - matches the real decoder exactly)
            case 1 -> fieldId = d.readInt();
            case 2 -> value = d.decodeValue();
            default -> { /* real decoder has no other case; leave fieldId/value at defaults */ }
        }
        return new COMValueInfo(valueIId, transId, valueType, fieldId, value);
    }
}
