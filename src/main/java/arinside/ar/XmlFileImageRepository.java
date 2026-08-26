package arinside.ar;

import arinside.ar.xmlfile.ParsedObjects;
import com.bmc.arsys.api.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ImageSource backed by a genuinely offline parse of an AR System Administrator .xml export - see XmlFileSchemaRepository's javadoc. */
public final class XmlFileImageRepository implements ImageSource {
    private final ParsedObjects parsed;

    public XmlFileImageRepository(ParsedObjects parsed) {
        this.parsed = parsed;
    }

    @Override
    public List<String> listImageNames() {
        List<String> names = new ArrayList<>(parsed.images.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Image getImage(String name) {
        return parsed.images.get(name);
    }
}
