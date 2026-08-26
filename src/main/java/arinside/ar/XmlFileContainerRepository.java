package arinside.ar;

import arinside.ar.xmlfile.ParsedObjects;
import com.bmc.arsys.api.Container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** ContainerSource backed by a genuinely offline parse of an AR System Administrator .xml export - see XmlFileSchemaRepository's javadoc. */
public final class XmlFileContainerRepository implements ContainerSource {
    private final ParsedObjects parsed;

    public XmlFileContainerRepository(ParsedObjects parsed) {
        this.parsed = parsed;
    }

    @Override
    public List<String> listContainerNames(int containerType) {
        List<String> names = new ArrayList<>();
        for (Container c : parsed.containers.values()) {
            if (c.getType() == containerType) names.add(c.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Container getContainer(String name) {
        return parsed.containers.get(name);
    }
}
