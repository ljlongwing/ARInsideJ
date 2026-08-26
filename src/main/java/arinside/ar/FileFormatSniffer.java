package arinside.ar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Distinguishes the AR System Administrator export formats file mode can be pointed at: the
 * genuine, self-describing .xml format (fully offline via arinside.ar.xmlfile.ArsXmlFileParser),
 * and the packed .def text format - now ALSO genuinely offline via arinside.ar.deffile.DefFileParser
 * (see its javadoc) rather than the earlier live-RPC path (FileModeSchemaRepository etc., now
 * superseded for this format - see its own javadoc). Only reads the first few hundred bytes - cheap
 * even against a multi-gigabyte export.
 */
public final class FileFormatSniffer {
    private FileFormatSniffer() {}

    public static boolean isXmlFormat(String path) {
        return prefix(path).startsWith("<?xml");
    }

    /** Real .def exports start with a "char-set: UTF-8"-style header line (confirmed against a real 473MB production export) - DefItemLabel.FILE_CHAR_SET's own label text. */
    public static boolean isDefFormat(String path) {
        return prefix(path).startsWith("char-set:");
    }

    private static String prefix(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) return "";
            return new String(buf, 0, n, StandardCharsets.UTF_8).stripLeading();
        } catch (IOException e) {
            return "";
        }
    }
}
