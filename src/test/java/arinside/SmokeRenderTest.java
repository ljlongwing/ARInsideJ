package arinside;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end smoke test: render the bundled {@code combined_test.xml} AR System export (offline,
 * no server) and assert on the generated site. Covers the plumbing that has broken silently in the
 * past - stale/legacy assets, the reskin shell, the Workflow-tab Shared column, the search index
 * and the JSON export - without pinning byte-for-byte output (which would need regenerating on
 * every intentional tweak).
 *
 * The whole site is rendered once in {@link #renderOnce()}; each test inspects the result.
 */
class SmokeRenderTest {

    private static Path out;

    @BeforeAll
    static void renderOnce() throws IOException {
        Path tmp = Files.createTempDirectory("arinsidej-smoke");
        Path fixture = tmp.resolve("combined_test.xml");
        try (InputStream in = SmokeRenderTest.class.getResourceAsStream("/combined_test.xml")) {
            if (in == null) fail("test fixture /combined_test.xml missing from the test classpath");
            Files.copy(in, fixture);
        }
        out = tmp.resolve("out");

        // java.util.Properties treats '\' as an escape, so keep every path forward-slashed.
        String ini = String.join("\n",
            "FileMode = TRUE",
            "ObjListXML = " + fixture.toString().replace('\\', '/'),
            "TargetFolder = " + out.toString().replace('\\', '/'),
            "CompanyName = Smoke Test",
            "CompanyUrl =",
            "JsonOutput = TRUE",
            "SearchIndex = TRUE",
            "");
        Path iniFile = tmp.resolve("smoke.ini");
        Files.writeString(iniFile, ini, StandardCharsets.UTF_8);

        Main.main(new String[] {"-i", iniFile.toString()});
    }

    private static String read(String relPath) {
        Path p = out.resolve(relPath);
        assertTrue(Files.isRegularFile(p), "expected generated file: " + relPath);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void coreTreeExists() {
        for (String f : List.of(
                "index.htm",
                "schema/index.htm",
                "overview/actlinks.htm",
                "overview/filters.htm",
                "overview/escalations.htm",
                "img/app.css",
                "img/app.js",
                "img/lists.js",
                "img/schema.js",
                "img/nav.js",
                "img/search-index.js")) {
            assertTrue(Files.isRegularFile(out.resolve(f)), "missing " + f);
        }
    }

    @Test
    void pagesAreHtml5ReskinShell() {
        String home = read("index.htm").toLowerCase();
        assertTrue(home.startsWith("<!doctype html>"), "index.htm is not HTML5");
        assertTrue(home.contains("ari-shell"), "reskin shell class missing");
    }

    @Test
    void noLegacyAssetsOrIframeNav() throws IOException {
        try (Stream<Path> tree = Files.walk(out)) {
            List<String> offenders = tree
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".htm"))
                .filter(p -> {
                    String body;
                    try {
                        body = Files.readString(p, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return false;
                    }
                    String low = body.toLowerCase();
                    return low.contains("jquery") || low.contains("<iframe") || body.startsWith("<?xml");
                })
                .map(p -> out.relativize(p).toString())
                .toList();
            assertTrue(offenders.isEmpty(), "legacy jQuery/iframe/XHTML found in: " + offenders);
        }
    }

    @Test
    void schemaWorkflowTabHasSharedColumn() {
        String schema = read("schema/User__o/index.htm");
        assertTrue(schema.contains(">Shared</th>"), "Workflow-tab Shared column header missing");
        assertTrue(schema.contains("var referenceList = ["), "Workflow-tab reference JSON missing");
    }

    @Test
    void fieldDetailIsOneSidecarNotAPagePerField() throws IOException {
        // field detail moved from one HTML file per field into schema/<form>/fields.js
        String sidecar = read("schema/User__o/fields.js");
        assertTrue(sidecar.startsWith("window.ARI_FIELDDETAIL="), "fields.js sidecar missing/!ARI_FIELDDETAIL");
        assertTrue(sidecar.contains("Field ID"), "fields.js has no field detail HTML");
        assertTrue(sidecar.contains("window.ARI_FIELDVUI_IDS="), "fields.js missing the per-view id list");
        try (Stream<Path> tree = Files.walk(out)) {
            long fldPages = tree.filter(p -> p.getFileName().toString().matches("fld_-?\\d+\\.htm")).count();
            assertTrue(fldPages == 0, "per-field HTML pages should be gone, found " + fldPages);
        }
        // links to field detail are now anchors on the form page
        assertTrue(read("schema/User__o/index.htm").contains("index.htm#field-"),
            "field links should point at index.htm#field-<id>");
    }

    @Test
    void searchIndexPopulated() {
        String idx = read("img/search-index.js");
        assertTrue(idx.contains("window.ARI_SEARCH"), "search index global missing");
        assertTrue(idx.contains("["), "search index looks empty");
    }

    @Test
    void jsonExportWritten() {
        String forms = read("data/forms.json").trim();
        assertTrue(forms.startsWith("["), "data/forms.json is not a JSON array");
        assertTrue(Files.isRegularFile(out.resolve("data/manifest.json")), "data/manifest.json missing");
        for (String f : List.of("active_links.json", "filters.json", "escalations.json",
                "menus.json", "containers.json", "images.json")) {
            assertTrue(Files.isRegularFile(out.resolve("data/" + f)), "missing data/" + f);
        }
        // v2: per-form detail file with a field list
        String userDetail = read("data/forms/User.json");
        assertTrue(userDetail.contains("\"fields\""), "form detail JSON missing a fields array");
        assertTrue(userDetail.contains("\"permissions\""), "form detail JSON missing permissions");
    }

    @Test
    void navTreeGenerated() {
        String nav = read("img/nav.js");
        assertTrue(nav.contains("window.ARI_NAV"), "nav global missing");
        assertFalse(nav.contains("undefined"), "nav tree has an undefined entry");
    }
}
