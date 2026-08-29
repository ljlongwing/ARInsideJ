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
 * File-mode incremental runs ({@code IncrementalRuns=TRUE}): an unchanged export skips the whole
 * run and leaves the output alone; a changed export runs in full again. Uses a deleted sentinel
 * file to tell "skipped" from "re-rendered" unambiguously.
 */
class IncrementalRunTest {

    private static Path tmp;
    private static Path fixture;
    private static Path out;
    private static Path ini;

    @BeforeAll
    static void setup() throws IOException {
        tmp = Files.createTempDirectory("arinsidej-incremental");
        fixture = tmp.resolve("export.xml");
        try (InputStream in = IncrementalRunTest.class.getResourceAsStream("/combined_test.xml")) {
            if (in == null) fail("test fixture /combined_test.xml missing from the test classpath");
            Files.copy(in, fixture);
        }
        out = tmp.resolve("out");
        ini = tmp.resolve("incremental.ini");
        Files.writeString(ini, String.join("\n",
            "FileMode = TRUE",
            "ObjListXML = " + fixture.toString().replace('\\', '/'),
            "TargetFolder = " + out.toString().replace('\\', '/'),
            "CompanyName = Incremental Test",
            "CompanyUrl =",
            "IncrementalRuns = TRUE",
            ""), StandardCharsets.UTF_8);
    }

    private static void run() {
        Main.main(new String[] {"-i", ini.toString()});
    }

    @Test
    void unchangedExportIsSkippedAndChangedExportReruns() throws IOException {
        // Run 1: fresh full render, state file written.
        run();
        Path stateFile = out.resolve(".arinside-state");
        Path indexPage = out.resolve("index.htm");
        assertTrue(Files.isRegularFile(stateFile), ".arinside-state not written on the first run");
        assertTrue(Files.isRegularFile(indexPage), "index.htm not written on the first run");

        // Run 2: export untouched -> the run must be skipped. Delete a sentinel first; if the run
        // is skipped it stays gone, if it re-renders it comes back.
        Files.delete(indexPage);
        run();
        assertFalse(Files.exists(indexPage), "second run re-rendered index.htm despite no change to the export");

        // Run 3: change the export -> full run again, sentinel restored.
        String xml = Files.readString(fixture, StandardCharsets.UTF_8);
        Files.writeString(fixture, xml.replaceFirst("<enabled>true</enabled>", "<enabled>false</enabled>"),
            StandardCharsets.UTF_8);
        run();
        assertTrue(Files.isRegularFile(indexPage), "third run did not re-render after the export changed");
    }
}
