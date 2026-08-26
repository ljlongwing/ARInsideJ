package arinside.ar;

import com.bmc.arsys.api.PropertyMap;
import com.bmc.arsys.api.Value;

/** Java port of the small ARProplistHelper::Find lookups used throughout doc/. */
public final class PropertyHelper {
    private PropertyHelper() {}

    public static String stringProperty(PropertyMap props, int propId) {
        if (props == null) return "";
        Value v = props.get(propId);
        if (v == null || v.getValue() == null) return "";
        return String.valueOf(v.getValue());
    }
}
