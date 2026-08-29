package arinside;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises {@code --diff <baseline> <current>} end-to-end against the bundled fixture:
 *  - baseline vs an unmodified copy of itself  -> a report with zero changes;
 *  - baseline vs a copy with one active link disabled -> exactly that one MODIFIED change.
 */
class SnapshotDiffTest {

    private static Path selfOut;   // fixture vs identical copy
    private static Path editOut;   // fixture vs one-active-link-disabled copy

    @BeforeAll
    static void runDiffs() throws IOException {
        Path tmp = Files.createTempDirectory("arinsidej-diff");

        Path baseline = tmp.resolve("baseline.xml");
        try (InputStream in = SnapshotDiffTest.class.getResourceAsStream("/combined_test.xml")) {
            if (in == null) fail("test fixture /combined_test.xml missing from the test classpath");
            Files.copy(in, baseline);
        }
        String xml = Files.readString(baseline, StandardCharsets.UTF_8);

        Path identical = tmp.resolve("identical.xml");
        Files.writeString(identical, xml, StandardCharsets.UTF_8);

        Path edited = tmp.resolve("edited.xml");
        String editedXml = xml.replaceFirst("<enabled>true</enabled>", "<enabled>false</enabled>");
        assertFalse(editedXml.equals(xml), "fixture edit did not take - expected an <enabled>true</enabled> to flip");
        Files.writeString(edited, editedXml, StandardCharsets.UTF_8);

        selfOut = runOne(tmp, "self", baseline, identical);
        editOut = runOne(tmp, "edit", baseline, edited);
    }

    private static Path runOne(Path tmp, String tag, Path baseline, Path current) throws IOException {
        Path out = tmp.resolve(tag + "-out");
        Path ini = tmp.resolve(tag + ".ini");
        Files.writeString(ini, "TargetFolder = " + out.toString().replace('\\', '/') + "\n", StandardCharsets.UTF_8);
        Main.main(new String[] {
            "-i", ini.toString(),
            "--diff", baseline.toString().replace('\\', '/'), current.toString().replace('\\', '/')
        });
        return out;
    }

    private static String read(Path out, String rel) {
        Path p = out.resolve(rel);
        assertTrue(Files.isRegularFile(p), "expected " + rel + " under " + out);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void diffReportStructureExists() {
        assertTrue(Files.isRegularFile(selfOut.resolve("diff/index.htm")), "diff/index.htm not written");
        String json = read(selfOut, "data/diff.json");
        assertTrue(json.contains("\"changes\""), "diff.json missing changes array");
        assertTrue(json.contains("\"summary\""), "diff.json missing summary block");
    }

    @Test
    void identicalSnapshotsProduceNoChanges() {
        String json = read(selfOut, "data/diff.json").replaceAll("\\s+", "");
        assertTrue(json.contains("\"changes\":[]"), "self-diff should report zero changes: " + json);
    }

    @Test
    void oneDisabledActiveLinkIsTheOnlyChange() {
        String json = read(editOut, "data/diff.json");
        String compact = json.replaceAll("\\s+", "");
        assertFalse(compact.contains("\"changes\":[]"), "edited-diff should report a change");

        // exactly one change entry, and it is a MODIFIED active link
        int entries = compact.split("\"status\"").length - 1;
        assertTrue(entries == 1, "expected exactly one change, diff.json was: " + json);
        assertTrue(compact.contains("\"status\":\"MODIFIED\"") || compact.contains("\"status\":\"modified\""),
            "the single change should be MODIFIED: " + json);
        assertTrue(json.toLowerCase().contains("active"), "the change should be an active link: " + json);
    }
}
