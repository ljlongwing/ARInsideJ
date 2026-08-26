package arinside.output;

import arinside.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Java port of output/CsvPage.{h,cpp}. */
public final class CsvPage {
    private final String fileName;
    private final AppConfig appConfig;

    public CsvPage(String fileName, AppConfig appConfig) {
        this.fileName = fileName;
        this.appConfig = appConfig;
    }

    public int saveInFolder(String path, String content) {
        Path dir = path.isEmpty() ? Path.of(appConfig.targetFolder) : Path.of(appConfig.targetFolder, path);
        Path file = dir.resolve(WebUtil.csvDocName(fileName));
        try {
            Files.createDirectories(dir);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            WebPage.filesCreated.incrementAndGet();
            return 1;
        } catch (IOException e) {
            throw new RuntimeException("Error saving file '" + file + "' to disk. Error: " + e.getMessage(), e);
        }
    }
}
