package arinside.diff;

import arinside.Version;
import arinside.config.AppConfig;
import arinside.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes {@code data/diff.json} - the machine-readable form of the change report. */
final class DiffJson {
    private DiffJson() {}

    static void write(AppConfig cfg, List<Change> changes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tool", Version.PRODUCT_NAME);
        root.put("version", Version.APP_VERSION);
        root.put("generated", Instant.now().toString());
        root.put("baseline", cfg.diffBaseline);
        root.put("current", cfg.diffCurrent);

        Map<String, int[]> byType = new LinkedHashMap<>();
        for (Change c : changes) {
            int[] t = byType.computeIfAbsent(c.typeLabel, k -> new int[3]);
            t[c.kind.ordinal()]++;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        byType.forEach((type, t) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("added", t[Change.Kind.ADDED.ordinal()]);
            m.put("removed", t[Change.Kind.REMOVED.ordinal()]);
            m.put("modified", t[Change.Kind.MODIFIED.ordinal()]);
            summary.put(type, m);
        });
        root.put("summary", summary);

        List<Object> arr = new ArrayList<>();
        for (Change c : changes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", c.typeLabel);
            m.put("name", c.name);
            m.put("status", c.kind.name().toLowerCase());
            if (!c.summary.isEmpty()) m.put("summary", new ArrayList<>(c.summary));
            if (!c.json.isEmpty()) m.put("detail", c.json);
            arr.add(m);
        }
        root.put("changes", arr);

        Path file = Path.of(cfg.targetFolder, "data", "diff.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing " + file, e);
        }
    }
}
