package arinside.output;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Process-global accumulator for the header search box's data. Overview pages call {@link #add}
 * once per documented object during their normal render pass; {@link #writeTo} then emits
 * {@code <target>/img/search-index.js} ({@code window.ARI_SEARCH = [[name, icon, href], …]}).
 * {@code href} is output-root-relative - {@code app.js} resolves it against the page's
 * {@code data-root}. Static/shared, matching {@link WebPage#filesCreated}'s pattern.
 */
public final class SearchIndex {
    private SearchIndex() {}

    private record Entry(String name, String icon, String href) {}

    private static final Queue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();

    public static void add(String name, String icon, PagePath detailPage) {
        if (name == null || detailPage == null) return;
        ENTRIES.add(new Entry(name, icon == null ? "document" : icon, detailPage.fullFileName()));
    }

    /** Cleared between runs (the CLI process is single-run, but tests may reuse the JVM). */
    public static void clear() { ENTRIES.clear(); }

    public static int size() { return ENTRIES.size(); }

    public static void writeTo(String targetFolder) {
        StringBuilder sb = new StringBuilder("window.ARI_SEARCH=[");
        boolean first = true;
        for (Entry e : ENTRIES) {
            if (!first) sb.append(',');
            first = false;
            sb.append("[\"").append(WebUtil.jsString(e.name())).append("\",\"")
                .append(WebUtil.jsString(e.icon())).append("\",\"")
                .append(WebUtil.jsString(e.href())).append("\"]");
        }
        sb.append("];\n");

        Path file = Path.of(targetFolder, "img", "search-index.js");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Error saving file '" + file + "' to disk", ex);
        }
    }

    /** Writes an empty index so every page's {@code <script src="img/search-index.js">} still resolves. */
    public static void writeEmpty(String targetFolder) {
        Path file = Path.of(targetFolder, "img", "search-index.js");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "window.ARI_SEARCH=[];\n", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Error saving file '" + file + "' to disk", ex);
        }
    }
}
