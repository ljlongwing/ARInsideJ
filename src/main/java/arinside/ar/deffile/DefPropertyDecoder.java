package arinside.ar.deffile;

import com.bmc.arsys.api.PropertyMap;
import com.bmc.arsys.api.Value;

import java.nio.charset.Charset;

/**
 * Decodes a packed {@code .def} property list (the {@code OBJECT_PROP}/{@code SMOPROP_LIST}/
 * {@code DISPLAY_PROPLIST} tag family: {@code object-prop : 7\60006\4\0\\60008\40\0\...}) into a
 * flat property map.
 *
 * <p>Format: {@code <numItems>\(<propId>\<value-per-DefValueDecoder.decodeValue()>\)*} - one
 * shared {@link DefValueDecoder} cursor reads the whole list sequentially, since each entry's
 * value consumes a variable number of tokens depending on its own type tag.
 *
 * <p>{@code com.bmc.arsys.api.PropertyMap} is a plain {@code Map<Integer,Value>} - every property
 * id, known or not, is just put into the map by its raw int id.
 */
final class DefPropertyDecoder {
    private DefPropertyDecoder() {}

    static <T extends PropertyMap> T decode(String value, Charset charset, T target) {
        if (value == null || value.isEmpty() || value.equals("(null)")) return target;
        DefValueDecoder d = new DefValueDecoder(value, charset);
        int numItems = d.readInt();
        for (int i = 0; i < numItems && !d.isEmpty(); i++) {
            String propIdStr = d.readString();
            Value propValue = d.decodeValue();
            if (propIdStr.isEmpty()) continue;
            try {
                int propId = Integer.parseInt(propIdStr);
                if (propValue != null) target.put(propId, propValue);
            } catch (NumberFormatException ignored) {
                // skip an unparseable property id
            }
        }
        return target;
    }
}
