package arinside.config;

/**
 * Java port of AppConfig.h/.cpp. Plain settings holder populated from settings.ini
 * ({@link AppConfigReader}) and then overridden by command line arguments
 * ({@link arinside.cli.CommandLineArgs}).
 */
public class AppConfig {

    /** Mirrors AppConfig::verboseMode - a process-wide flag checked by the LOG macro in the C++. */
    public static boolean verboseMode = false;

    // ARSystem data
    public String userForm = "";
    public String groupForm = "";
    public String roleForm = "";
    public String userQuery = "";
    public String groupQuery = "";
    public String roleQuery = "";
    public int maxRetrieve = 0;
    public String overlayMode = "";
    public boolean slowObjectLoading = false;

    // Output and layout configuration
    public String companyName = "";
    public String companyUrl = "";
    public String targetFolder = "";
    public boolean fileMode = false;
    public String objListXML = "";
    public boolean oldNaming = false;

    // BlackList
    public String blackList = "";

    /**
     * When non-blank, limits the write phase to this form plus its directly-related workflow
     * tree (AL/Filter/Escalation executing on it, the containers those belong to, and the menus
     * its own fields reference) - see arinside.scan.ScopeFilter's javadoc. Scan/indexing passes
     * are unaffected; only which per-object detail pages get fully rendered vs. stubbed changes.
     */
    public String scope = "";

    // Restrict object library
    public boolean loadServerInfoList = true;
    public boolean loadUserList = true;
    public boolean loadGroupList = true;
    public boolean loadRoleList = true;
    public boolean useUtf8 = false;
    public boolean compactFolder = false;
    public boolean gzCompression = false;
    public boolean deleteExistingFiles = false;
    public boolean overlaySupport;

    public String serverName = "";
    public String userName = "";
    public String password = "";
    public int tcpPort = 0;
    public int rpcPort = 0;
    public int apiTimeout = 0;

    /**
     * Max concurrent AR System connections used for object fetching / max concurrent worker
     * threads for local page rendering+writing. {@code <= 0} means "unlimited" - no hard-coded
     * cap, size gated only by how fast work is submitted (see ReadPool/WritePool javadoc for what
     * "unlimited" actually means for the read side, since a literal one-connection-per-task
     * reading is neither practical nor what the setting intends). Default 8/16 - the concurrency
     * level verified against the live test server to give the
     * best throughput without overwhelming the server with connections; explicitly override to 1/1
     * in settings.ini to reproduce the old fully-sequential behavior.
     */
    public int readConcurrency = 8;
    public int writeConcurrency = 16;

    public String runNotes = "";

    /**
     * Set by {@link #validate} - true when file mode is pointed at a genuine, self-describing
     * {@code .xml} or {@code .def} export (sniffed via {@link arinside.ar.FileFormatSniffer}), the
     * case where this port can run without ever opening a live AR System connection - see
     * {@code arinside.ar.xmlfile.ArsXmlFileParser}'s javadoc (XML) and {@code
     * arinside.ar.deffile.DefFileParser}'s javadoc (def) for how each format is parsed genuinely
     * offline. A file mode pointed at a non-existent/unreadable/unrecognized-format path (caught
     * later at connection time) still needs a live connection, same as server mode - see {@link
     * arinside.ar.FileModeSchemaRepository}'s javadoc, now a superseded fallback for {@code .def}
     * files specifically.
     */
    public boolean connectionless = false;

    /**
     * Ported from AppConfig::Validate. Applies CLI overrides, checks required fields for
     * server mode, guards against a target folder pointing at the filesystem root, and
     * settles the overlay-support flag from the (string-valued, ini-compatible) overlayMode setting.
     *
     * Server/login are required unless {@link #connectionless} ends up true (file mode pointed at a
     * real .xml export) - every other mode/format combination in this port still needs a live,
     * logged-in session even when the object data itself comes from disk. Was true of BOTH formats
     * until arinside.ar.deffile.DefFileParser shipped - .def exports are now also genuinely offline
     * (a full line-format parser for the AR Server .def format - see its javadoc), so this
     * now only applies to a file mode pointed at neither a recognized .xml nor .def export (e.g. a
     * corrupt/unrecognized file, caught later at connection time).
     */
    public void validate(arinside.cli.CommandLineArgs cmdLine) {
        overrideSettingsByCommandLine(cmdLine);

        if (fileMode && objListXML.isEmpty()) {
            throw new IllegalArgumentException("[ERR] FileMode=TRUE but no 'ObjListXML' file specified in the application configuration file!");
        }
        connectionless = fileMode && !objListXML.isEmpty()
            && (arinside.ar.FileFormatSniffer.isXmlFormat(objListXML) || arinside.ar.FileFormatSniffer.isDefFormat(objListXML));

        StringBuilder missingArgs = new StringBuilder();
        if (!connectionless) {
            if (serverName.isEmpty()) {
                missingArgs.append("server / ServerName");
            }
            if (userName.isEmpty()) {
                if (missingArgs.length() > 0) missingArgs.append(", ");
                missingArgs.append("login / Username");
            }
            if (missingArgs.length() > 0) {
                throw new IllegalArgumentException("Required argument(s) missing: " + missingArgs);
            }
        }

        if (targetFolder.isEmpty()) {
            throw new IllegalArgumentException("[ERR] Target folder setting is missing or not setup correctly!");
        }

        // Just in case someone has set DeleteExistingFiles on and specified the root of the
        // file system. For security reasons we disallow the root directory.
        java.nio.file.Path fullOutputPath = java.nio.file.Paths.get(targetFolder).toAbsolutePath().normalize();
        if (fullOutputPath.getParent() == null) {
            throw new IllegalArgumentException(
                "[ERR] The target directory points to the root of the device. This is not allowed!");
        }

        if (!"FALSE".equals(overlayMode) && !"TRUE".equals(overlayMode)) {
            overlayMode = "TRUE";
        }
        overlaySupport = !"FALSE".equals(overlayMode);
    }

    private void overrideSettingsByCommandLine(arinside.cli.CommandLineArgs cmdLine) {
        if (cmdLine.isServerSet()) serverName = cmdLine.getServer();
        if (cmdLine.isOutputFolderSet()) targetFolder = cmdLine.getOutputFolder();
        if (cmdLine.isUsernameSet()) userName = cmdLine.getUsername();
        if (cmdLine.isPasswordSet()) password = cmdLine.getPassword();
        if (cmdLine.isTcpPortSet()) tcpPort = cmdLine.getTcpPort();
        if (cmdLine.isRpcPortSet()) rpcPort = cmdLine.getRpcPort();
        if (cmdLine.isScopeSet()) scope = cmdLine.getScope();
        slowObjectLoading = cmdLine.isSlowObjectLoading();
    }

    public void dump() {
        System.out.println("UserForm: " + userForm);
        System.out.println("UserQuery: " + userQuery);
        System.out.println("GroupForm: " + groupForm);
        System.out.println("GroupQuery: " + groupQuery);
        System.out.println("RoleForm: " + roleForm);
        System.out.println("RoleQuery: " + roleQuery);
        System.out.println("MaxRetrieve: " + maxRetrieve);
        System.out.println("CompanyName: " + companyName);
        System.out.println("CompanyUrl: " + companyUrl);
        System.out.println("TargetFolder: " + targetFolder);
        System.out.println("FileMode: " + fileMode);
        System.out.println("ObjListXML: " + objListXML);
        System.out.println("Connectionless (offline XML/def file mode): " + connectionless);
        System.out.println("OldNaming: " + oldNaming);
        System.out.println("BlackList: " + blackList);
        System.out.println("Scope: " + (scope.isEmpty() ? "(full run)" : scope));
        System.out.println("LoadServerInfoList: " + loadServerInfoList);
        System.out.println("LoadUserList: " + loadUserList);
        System.out.println("LoadGroupList: " + loadGroupList);
        System.out.println("LoadRoleList: " + loadRoleList);
        System.out.println("Utf-8: " + useUtf8);
        System.out.println("CompactFolder: " + compactFolder);
        System.out.println("GZCompression: " + gzCompression);
        System.out.println("DeleteExistingFiles: " + deleteExistingFiles);
        System.out.println("OverlayMode: " + overlayMode);
        System.out.println("RunNotes: " + runNotes);
        System.out.println("APITimeout: " + apiTimeout);
        System.out.println("ReadConcurrency: " + readConcurrency);
        System.out.println("WriteConcurrency: " + writeConcurrency);
        System.out.println();
    }
}
