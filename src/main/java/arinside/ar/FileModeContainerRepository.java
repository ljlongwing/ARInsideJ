package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ContainerSource backed by an AR System Administrator XML export file - see FileModeSchemaRepository's javadoc for the shared live-connection caveat. */
public final class FileModeContainerRepository implements ContainerSource {
    private final Map<String, Container> containersByName = new HashMap<>();

    public FileModeContainerRepository(ArClient client, String defFile) {
        try {
            Map<String, List<Container>> raw = client.raw().getListContainersFromDef(defFile, null, 0, false);
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    if (!entry.getValue().isEmpty() && entry.getValue().get(0) != null) {
                        containersByName.put(entry.getKey(), entry.getValue().get(0));
                    }
                }
            }
        } catch (ARException | IOException e) {
            throw new RuntimeException("Failed reading containers from def file '" + defFile + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listContainerNames(int containerType) {
        List<String> names = new ArrayList<>();
        for (Container c : containersByName.values()) {
            if (c.getType() == containerType) names.add(c.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Container getContainer(String name) {
        return containersByName.get(name);
    }
}
