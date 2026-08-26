package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ImageSource backed by an AR System Administrator XML export file - see FileModeSchemaRepository's javadoc for the shared live-connection caveat. */
public final class FileModeImageRepository implements ImageSource {
    private final Map<String, Image> imagesByName = new HashMap<>();

    public FileModeImageRepository(ArClient client, String defFile) {
        try {
            Map<String, List<Image>> raw = client.raw().getListImagesFromDef(defFile, null, 0, false);
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    if (!entry.getValue().isEmpty() && entry.getValue().get(0) != null) {
                        imagesByName.put(entry.getKey(), entry.getValue().get(0));
                    }
                }
            }
        } catch (ARException | IOException e) {
            throw new RuntimeException("Failed reading images from def file '" + defFile + "': " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listImageNames() {
        List<String> names = new ArrayList<>(imagesByName.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    @Override
    public Image getImage(String name) {
        return imagesByName.get(name);
    }
}
