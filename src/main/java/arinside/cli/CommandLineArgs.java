package arinside.cli;

import arinside.config.AppConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Java port of util/CommandLineValidator (which wraps tclap). Hand-rolled instead of pulling
 * in a CLI-parsing library, since the flag set is small and fixed: -i/-s/-l/-p/-t/-r/-o/--slow/-v/-h.
 */
public class CommandLineArgs {

    private String iniFilename = "settings.ini";
    private String server = "";
    private String username = "";
    private String password = "";
    private String outputFolder = "";
    private int tcpPort = 0;
    private int rpcPort = 0;
    private boolean slowObjectLoading = false;
    private String scope = "";
    private String diffBaseline = "";
    private String diffCurrent = "";

    private boolean serverSet, outputFolderSet, usernameSet, passwordSet, tcpPortSet, rpcPortSet, scopeSet, diffSet, incrementalSet;
    private boolean helpRequested;

    public static CommandLineArgs parse(String[] args) {
        CommandLineArgs result = new CommandLineArgs();
        Deque<String> queue = new ArrayDeque<>();
        for (String a : args) queue.add(a);

        while (!queue.isEmpty()) {
            String arg = queue.poll();
            switch (arg) {
                case "-i", "--ini" -> result.iniFilename = requireValue(arg, queue);
                case "-s", "--server" -> { result.server = requireValue(arg, queue); result.serverSet = true; }
                case "-l", "--login" -> { result.username = requireValue(arg, queue); result.usernameSet = true; }
                case "-p", "--pwd" -> { result.password = requireValue(arg, queue); result.passwordSet = true; }
                case "-t", "--tcp" -> { result.tcpPort = Integer.parseInt(requireValue(arg, queue)); result.tcpPortSet = true; }
                case "-r", "--rpc" -> { result.rpcPort = Integer.parseInt(requireValue(arg, queue)); result.rpcPortSet = true; }
                case "-o", "--output" -> { result.outputFolder = requireValue(arg, queue); result.outputFolderSet = true; }
                case "--slow" -> result.slowObjectLoading = true;
                case "--scope" -> { result.scope = requireValue(arg, queue); result.scopeSet = true; }
                case "--diff" -> {
                    result.diffBaseline = requireValue(arg, queue);
                    result.diffCurrent = requireValue(arg, queue);
                    result.diffSet = true;
                }
                case "--incremental" -> result.incrementalSet = true;
                case "-v", "--verbose" -> AppConfig.verboseMode = true;
                case "-h", "--help" -> result.helpRequested = true;
                default -> throw new IllegalArgumentException("error: unknown argument '" + arg + "'");
            }
        }
        return result;
    }

    private static String requireValue(String arg, Deque<String> queue) {
        if (queue.isEmpty()) {
            throw new IllegalArgumentException("error: missing value for argument '" + arg + "'");
        }
        return queue.poll();
    }

    public static void printUsage() {
        System.out.println("""
            ARInside (Java) -- https://github.com/gabeluci/ARInside
            Copyright (C) 2014 Stefan Nerlich, LJ Longwing, John Luthgers
            This program comes with ABSOLUTELY NO WARRANTY, is free software, and you are welcome to
            redistribute it under certain conditions; see COPYING file for more details.

              -i, --ini <string>      Application settings filename (required, default settings.ini)
              -s, --server <string>   ARSystem server
              -l, --login <string>    Login name
              -p, --pwd <string>      Password
              -t, --tcp <int>         TCP port
              -r, --rpc <int>         RPC port
              -o, --output <string>   Output folder
                  --slow               Uses slow object loading
                  --scope <string>     Only document this form and its directly-related workflow
                  --diff <baseline> <current>
                                       Compare two offline .xml/.def exports; write a change report
                                       to the output folder instead of the normal documentation
                  --incremental        Skip the whole run if nothing changed since the last one
                                       (writes/reads .arinside-state in the output folder)
              -v, --verbose            Verbose output
              -h, --help                Show this help
            """);
    }

    public boolean isHelpRequested() { return helpRequested; }
    public String getIniFilename() { return iniFilename; }
    public String getServer() { return server; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getOutputFolder() { return outputFolder; }
    public int getTcpPort() { return tcpPort; }
    public int getRpcPort() { return rpcPort; }
    public boolean isSlowObjectLoading() { return slowObjectLoading; }
    public String getScope() { return scope; }

    public boolean isServerSet() { return serverSet; }
    public boolean isOutputFolderSet() { return outputFolderSet; }
    public boolean isUsernameSet() { return usernameSet; }
    public boolean isPasswordSet() { return passwordSet; }
    public boolean isTcpPortSet() { return tcpPortSet; }
    public boolean isRpcPortSet() { return rpcPortSet; }
    public boolean isScopeSet() { return scopeSet; }
    public boolean isDiffSet() { return diffSet; }
    public boolean isIncrementalSet() { return incrementalSet; }
    public String getDiffBaseline() { return diffBaseline; }
    public String getDiffCurrent() { return diffCurrent; }
}
