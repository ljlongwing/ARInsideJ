package arinside.util;

import arinside.config.AppConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java port of the parts of FileSystemUtil.{h,cpp} still needed once resource packaging no
 * longer requires the C++'s tar/gzip dance (see ResourceExtractor). Directory creation,
 * deletion and validation only - java.nio.file already handles the cross-platform bits the
 * C++ needed WIN32 #ifdefs for.
 */
public final class OutputDirectory {
    private final AppConfig appConfig;

    public OutputDirectory(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /** Matches FileSystemUtil::CreateAppDirectory. */
    public boolean createAppDirectory() {
        try {
            Files.createDirectories(Path.of(appConfig.targetFolder));
            System.out.println("Create target directory: " + appConfig.targetFolder + " [OK]");
            return true;
        } catch (IOException e) {
            System.out.println("Create target directory: " + appConfig.targetFolder + " [" + e.getMessage() + "]");
            return false;
        }
    }

    /** Matches FileSystemUtil::DeleteDirectory, called when DeleteExistingFiles=TRUE. */
    public static void deleteExistingFiles(String path) {
        System.out.println("Deleting existing files");
        Path dir = Path.of(path);
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            System.out.println("Deletion failed!");
        }
    }

    /** Matches FileSystemUtil::ValidateTargetDir - writes and removes a marker file to confirm write access. Returns 0 on success. */
    public static int validateTargetDir(String targetFolder) {
        Path marker = Path.of(targetFolder, "valid.txt");
        try {
            Files.writeString(marker, "arinside\n");
            Files.delete(marker);
            return 0;
        } catch (IOException e) {
            System.out.println("EXCEPTION ValidateTargetDir '" + targetFolder + "' -- " + e.getMessage());
            return -1;
        }
    }
}
