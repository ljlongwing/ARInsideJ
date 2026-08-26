package arinside.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Java replacement for util/ResourceFileLocatorAndExtractor + util/UntarStream. The C++ has to
 * bundle res/ and the jquery assets into a tar+gzip archive (arires.tgz) baked into the exe at
 * build time and unpack it at runtime, because C++ has no built-in resource system. Java does -
 * these assets are just classpath resources under web-assets/ (see java-port/src/main/resources)
 * copied out with java.nio.file, so the whole tar/gzip layer is dropped rather than ported.
 */
public final class ResourceExtractor {
    private ResourceExtractor() {}

    private static final String CLASSPATH_ROOT = "web-assets/img";

    /** Extracts the bundled web assets (css/js/images) into &lt;targetFolder&gt;/img. */
    public static void extractTo(String targetFolder) {
        URL url = ResourceExtractor.class.getClassLoader().getResource(CLASSPATH_ROOT);
        if (url == null) {
            throw new IllegalStateException("Bundled web assets not found on classpath at " + CLASSPATH_ROOT);
        }

        Path destRoot = Path.of(targetFolder, "img");
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    copyTree(fs.getPath("/" + CLASSPATH_ROOT), destRoot);
                }
            } else {
                copyTree(Path.of(uri), destRoot);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to extract web assets to " + destRoot, e);
        }
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path src : (Iterable<Path>) walk::iterator) {
                Path relative = source.relativize(src);
                Path target = dest.resolve(relative.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(src, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
