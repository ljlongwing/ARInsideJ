package arinside.incremental;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import arinside.Version;

/**
 * The {@code .arinside-state} file dropped in the output folder after an incremental-enabled run,
 * and read at the start of the next one to decide whether anything changed (the {@code IncrementalRuns}
 * ini setting / {@code --incremental} flag). Deliberately a plain tab-separated text file, not JSON -
 * it has to be read back and {@code util.Json} only writes.
 *
 * <pre>
 * #ARInsideJ incremental run state - do not edit
 * version    4.2
 * generated  2026-08-28T21:15:30Z
 * mode       server            (or "file")
 * source     myserver          (server name, or the export file path)
 * probeTime  1756412130        (epoch seconds; the "changed since" cutoff for the next run)
 * filehash   &lt;sha-256 hex&gt;      (file mode only; "-" for server mode)
 * name       schema     HPD:Help Desk
 * name       activelink HPD:HII:CreateHelpDesk
 * ...
 * </pre>
 */
public final class RunState {

    public static final String FILE_NAME = ".arinside-state";

    public String version = "";
    public String generated = "";
    public String mode = "";        // "server" | "file"
    public String source = "";      // server name, or export file path
    public long probeTime = 0L;     // epoch seconds
    public String fileHash = "-";
    public final Map<String, Set<String>> names = new LinkedHashMap<>();

    public Set<String> namesFor(String type) {
        return names.computeIfAbsent(type, k -> new LinkedHashSet<>());
    }

    /** Reads {@code <targetFolder>/.arinside-state}, or returns null if it is missing or unreadable. */
    public static RunState readOrNull(String targetFolder) {
        Path file = Path.of(targetFolder, FILE_NAME);
        if (!Files.isRegularFile(file)) return null;
        try {
            RunState s = new RunState();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\t", -1);
                switch (p[0]) {
                    case "version" -> s.version = val(p, 1);
                    case "generated" -> s.generated = val(p, 1);
                    case "mode" -> s.mode = val(p, 1);
                    case "source" -> s.source = val(p, 1);
                    case "probeTime" -> s.probeTime = parseLong(val(p, 1));
                    case "filehash" -> s.fileHash = val(p, 1);
                    case "name" -> { if (p.length >= 3) s.namesFor(p[1]).add(p[2]); }
                    default -> { /* forward-compatible: ignore unknown keys */ }
                }
            }
            return s;
        } catch (IOException e) {
            return null;
        }
    }

    public void write(String targetFolder) {
        StringBuilder sb = new StringBuilder();
        sb.append("#ARInsideJ incremental run state - do not edit\n");
        sb.append("version\t").append(Version.APP_VERSION).append('\n');
        sb.append("generated\t").append(generated.isEmpty() ? Instant.now().toString() : generated).append('\n');
        sb.append("mode\t").append(mode).append('\n');
        sb.append("source\t").append(source).append('\n');
        sb.append("probeTime\t").append(probeTime).append('\n');
        sb.append("filehash\t").append(fileHash == null || fileHash.isEmpty() ? "-" : fileHash).append('\n');
        names.forEach((type, set) -> {
            for (String n : set) {
                if (n.indexOf('\t') >= 0 || n.indexOf('\n') >= 0) continue; // can't happen for AR object names; skip defensively
                sb.append("name\t").append(type).append('\t').append(n).append('\n');
            }
        });
        Path file = Path.of(targetFolder, FILE_NAME);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Error writing " + file, e);
        }
    }

    /** Lower-case hex SHA-256 of a file's bytes. */
    public static String sha256(String path) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(Path.of(path)));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String val(String[] parts, int i) { return i < parts.length ? parts[i] : ""; }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }
}
