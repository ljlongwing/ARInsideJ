package arinside.diff;

import arinside.ar.ArClient;
import arinside.config.AppConfig;
import arinside.doc.DiffReportPage;

import java.util.Comparator;
import java.util.List;

/**
 * Entry point for {@code --diff}. Loads both snapshots, classifies every object, and writes the
 * standalone change report ({@code diff/…}) + {@code data/diff.json} into {@code targetFolder}.
 * Called from {@code Main} instead of the normal documentation pipeline.
 */
public final class DiffRunner {
    private DiffRunner() {}

    public static void run(AppConfig cfg) throws Exception {
        System.out.println("Diff mode:");
        System.out.println("  baseline: " + cfg.diffBaseline);
        System.out.println("  current:  " + cfg.diffCurrent);

        try (ArClient client = cfg.diffUsesServer() ? ArClient.connect(cfg) : null) {
            RepoSet baseline = cfg.diffBaselineIsServer()
                ? RepoSet.loadServer(client, cfg) : RepoSet.load(cfg.diffBaseline, cfg.overlaySupport);
            RepoSet current = cfg.diffCurrentIsServer()
                ? RepoSet.loadServer(client, cfg) : RepoSet.load(cfg.diffCurrent, cfg.overlaySupport);

            System.out.println("Comparing snapshots...");
            compare(cfg, baseline, current);
        }
    }

    private static void compare(AppConfig cfg, RepoSet baseline, RepoSet current) throws Exception {
        List<Change> changes = new SnapshotDiff(baseline, current).run();
        changes.sort(Comparator
            .comparing((Change c) -> c.typeLabel)
            .thenComparing(c -> c.kind.ordinal())
            .thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER));

        long added = changes.stream().filter(c -> c.kind == Change.Kind.ADDED).count();
        long removed = changes.stream().filter(c -> c.kind == Change.Kind.REMOVED).count();
        long modified = changes.stream().filter(c -> c.kind == Change.Kind.MODIFIED).count();
        System.out.println(changes.size() + " changes (" + added + " added, " + removed + " removed, " + modified + " modified).");

        DiffReportPage.write(cfg, changes, baseline, current);
        DiffJson.write(cfg, changes);
        System.out.println("Report written to " + cfg.targetFolder + " (open diff/index.htm).");
    }
}
