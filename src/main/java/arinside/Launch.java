package arinside;

/**
 * Executable entry point (the jar's {@code Main-Class}). This class deliberately has <b>no</b>
 * compile-time reference to {@code com.bmc.arsys.*}: ARInsideJ is built against BMC's AR System
 * Java API but does not bundle it (proprietary, non-redistributable), and every run mode - live
 * server, {@code .def} and {@code .xml} file mode - needs it on the classpath. If it's missing,
 * the JVM can't even verify {@link Main} (its method bodies reference {@code ARException} etc.),
 * so the check has to happen here, before {@code Main} is touched, and hand back a "where to get
 * the jars" message instead of a raw {@code NoClassDefFoundError}. See {@code lib/README.txt}.
 */
public final class Launch {
    private Launch() {}

    public static void main(String[] args) throws Exception {
        try {
            Class.forName("com.bmc.arsys.api.ARServerUser", false, Launch.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError missing) {
            printMissingApiGuidance();
            System.exit(2);
            return;
        }
        Main.main(args);
    }

    private static void printMissingApiGuidance() {
        String libHint = "a lib/ folder next to arinsidej.jar";
        try {
            java.net.URL loc = Launch.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null && "file".equals(loc.getProtocol())) {
                java.nio.file.Path self = java.nio.file.Path.of(loc.toURI());
                java.nio.file.Path dir = java.nio.file.Files.isDirectory(self) ? self : self.getParent();
                if (dir != null) libHint = dir.resolve("lib") + " (next to arinsidej.jar)";
            }
        } catch (Exception ignored) {
            // best-effort only - keep the generic hint
        }

        System.err.println(Version.PRODUCT_NAME + " " + Version.APP_VERSION);
        System.err.println();
        System.err.println("[ERR] The BMC AR System Java API is not on the classpath.");
        System.err.println();
        System.err.println("      ARInsideJ needs BMC's proprietary jars, which are not bundled:");
        System.err.println("        arapi*.jar     - the AR System Java API (com.bmc.arsys.api.*)");
        System.err.println("        arlogger*.jar  - com.bmc.arsys.logger.ARLogger (live-server / .def mode)");
        System.err.println();
        System.err.println("      Copy them into " + libHint + ", then use");
        System.err.println("      run-arinsidej.bat / run-arinsidej.sh, or run:");
        System.err.println("        java -cp \"arinsidej.jar:lib/*\" arinside.Launch ...   (use \";\" not \":\" on Windows)");
        System.err.println();
        System.err.println("      lib/README.txt lists every BMC install they can be found in (AR System");
        System.err.println("      server, Mid Tier, Developer Studio, DISERVER, the BMC EPD download, ...).");
    }
}
