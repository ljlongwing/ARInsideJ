package arinside.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Java port of AppConfigReader + ConfigFile. settings.ini is a flat "key = value" file with
 * "#" comments and no [section] headers, which is exactly what java.util.Properties parses,
 * so we use it directly instead of porting the C++'s hand-rolled ConfigFile template class.
 */
public class AppConfigReader {

    private final Path configFile;

    public AppConfigReader(String filename) {
        this.configFile = Path.of(filename);
    }

    public void loadTo(AppConfig cfg) {
        if (!Files.isReadable(configFile)) {
            throw new ConfigException("File '" + configFile + "' not found.");
        }

        Properties props = new Properties();
        try (var reader = new InputStreamReader(new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new ConfigException("File '" + configFile + "' doesn't look like a valid configuration file!");
        }

        if (props.isEmpty()) {
            throw new ConfigException("File '" + configFile + "' doesn't look like a valid configuration file!");
        }

        cfg.userForm = readString(props, "UserForm", "User");
        cfg.userQuery = readString(props, "UserQuery", "1=1");
        cfg.groupForm = readString(props, "GroupForm", "Group");
        cfg.groupQuery = readString(props, "GroupQuery", "1=1");
        cfg.roleForm = readString(props, "RoleForm", "Roles");
        cfg.roleQuery = readString(props, "RoleQuery", "1=1");
        cfg.maxRetrieve = readInt(props, "MaxRetrieve", 0);
        cfg.companyName = readString(props, "CompanyName", "");
        cfg.companyUrl = readString(props, "CompanyUrl", "");
        cfg.targetFolder = readString(props, "TargetFolder", "");
        cfg.fileMode = readBool(props, "FileMode", false);
        cfg.objListXML = readString(props, "ObjListXML", "");
        cfg.oldNaming = readBool(props, "OldNaming", false);
        cfg.blackList = readString(props, "BlackList", "");
        cfg.scope = readString(props, "Scope", "");
        cfg.loadServerInfoList = readBool(props, "LoadServerInfoList", true);
        cfg.loadUserList = readBool(props, "LoadUserList", true);
        cfg.loadGroupList = readBool(props, "LoadGroupList", true);
        cfg.loadRoleList = readBool(props, "LoadRoleList", true);
        cfg.useUtf8 = readBool(props, "Utf-8", false);
        cfg.compactFolder = readBool(props, "CompactFolder", false);
        cfg.gzCompression = readBool(props, "GZCompression", false);
        cfg.deleteExistingFiles = readBool(props, "DeleteExistingFiles", false);
        cfg.runNotes = readString(props, "RunNotes", "");
        cfg.serverName = readString(props, "ServerName", "");
        cfg.tcpPort = readInt(props, "TCPPort", 0);
        cfg.rpcPort = readInt(props, "RPCPort", 0);
        cfg.userName = readString(props, "Username", "");
        cfg.password = readString(props, "Password", "");
        cfg.apiTimeout = readInt(props, "APITimeout", 0);
        cfg.overlayMode = readString(props, "OverlayMode", "TRUE");
        cfg.readConcurrency = readInt(props, "ReadConcurrency", 8);
        cfg.writeConcurrency = readInt(props, "WriteConcurrency", 16);
    }

    private static String readString(Properties props, String key, String def) {
        return props.getProperty(key, def).trim();
    }

    private static int readInt(Properties props, String key, int def) {
        String v = props.getProperty(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Matches ConfigFile::string_as_T<bool>: FALSE/F/NO/N/0/NONE (case-insensitive) are false,
     * anything else (including an unset key) falls back to the caller's default / true.
     */
    private static boolean readBool(Properties props, String key, boolean def) {
        String v = props.getProperty(key);
        if (v == null) return def;
        String upper = v.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (upper) {
            case "FALSE", "F", "NO", "N", "0", "NONE" -> false;
            default -> true;
        };
    }

    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) {
            super(message);
        }
    }
}
