package arinside.output;

import arinside.Version;
import arinside.config.AppConfig;
import arinside.util.DateTimeFormat;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Assembles one generated HTML page: an HTML5 CSS-grid app shell (sticky header with a global
 * search box + theme toggle, a sidebar populated client-side from {@code img/nav.js}, the page
 * body, and a footer). Replaces the original XHTML 1.0 + {@code <table class="TblMain">} +
 * navigation-{@code <iframe>} layout ported from the C++ tool.
 *
 * GZip output (AppConfig.gzCompression) writes {@code .htm.gz} instead of {@code .htm}; otherwise
 * plain {@code .htm}.
 */
public final class WebPage {

    /** Matches the C++'s global `nFilesCreated` counter (extern int in Main.cpp). */
    public static final AtomicInteger filesCreated = new AtomicInteger(0);

    /**
     * Directories already confirmed to exist this run. Files.createDirectories does a stat check
     * even when the directory is already there, and with ~1500 schema directories each holding
     * hundreds of field pages, that's hundreds of thousands of redundant stat calls otherwise -
     * cheap to skip once a directory's been created the first time.
     */
    private static final java.util.Set<Path> knownDirs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final String fileName;
    private final String title;
    private final int rootLevel;
    private final AppConfig appConfig;
    private final List<String> bodyContent = new ArrayList<>();
    private final List<String> cssReferences = new ArrayList<>();
    private final List<String> jsReferences = new ArrayList<>();
    private String bodyClass = "";

    public WebPage(String fileName, String title, int rootLevel, AppConfig appConfig) {
        this.fileName = fileName;
        this.title = title;
        this.rootLevel = rootLevel;
        this.appConfig = appConfig;
        setupDefaultReferences();
    }

    public void addContent(String content) { bodyContent.add(content); }

    public void addContentHead(String description) { addContentHead(description, ""); }

    public void addContentHead(String description, String rightInfo) {
        addContent("<div class=\"ari-pagehead\">");
        addContent("<div id='locLeft'>");
        addContent(description);
        addContent("</div><div id='locRight'>");
        addContent(rightInfo.isEmpty() ? "&nbsp;" : rightInfo);
        addContent("</div></div>");
    }

    public WebPage addScriptReference(String scriptPath) { jsReferences.add(scriptPath); return this; }
    public WebPage addStyleSheetReference(String cssPath) { cssReferences.add(cssPath); return this; }

    /** Adds a class to {@code <body>} (e.g. "list-page" for the wide, header-only overview lists). */
    public WebPage bodyClass(String cssClass) {
        this.bodyClass = bodyClass.isEmpty() ? cssClass : bodyClass + " " + cssClass;
        return this;
    }

    private void setupDefaultReferences() {
        addStyleSheetReference("img/app.css");
        // nav.js is generated per run into <target>/img/ (see NavigationPage); app.js reads
        // window.ARI_NAV from it. search-index.js (also generated) can be multi-MB on a large
        // server, so app.js loads it lazily on first use of the search box rather than here.
        addScriptReference("img/nav.js");
        addScriptReference("img/app.js");
    }

    /**
     * Saves the page under &lt;targetFolder&gt;/&lt;path&gt;/&lt;fileName&gt;.htm(.gz). Returns 1
     * on success (matches CWebPage::SaveInFolder).
     */
    public int saveInFolder(String path) {
        Path dir = path.isEmpty()
            ? Path.of(appConfig.targetFolder)
            : Path.of(appConfig.targetFolder, path);
        Path file = dir.resolve(WebUtil.docName(fileName));

        long t0 = arinside.util.Timing.start();
        try {
            if (knownDirs.add(dir)) {
                Files.createDirectories(dir);
            }
            try (java.io.OutputStream out = rawOutputStream(file);
                 Writer w = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writeContent(w);
            }
            filesCreated.incrementAndGet();
            return 1;
        } catch (IOException e) {
            throw new RuntimeException("Error saving file '" + file + "' to disk. Error: " + e.getMessage(), e);
        } finally {
            arinside.util.Timing.addWrite(t0);
        }
    }

    /** Explicit buffering: writeContent() makes dozens of small Writer.write() calls per page (header/body/footer boilerplate) - without this, each one risks its own raw syscall to the OS instead of batching into one write on close/flush. */
    private java.io.OutputStream rawOutputStream(Path file) throws IOException {
        java.io.OutputStream fileOut = new java.io.BufferedOutputStream(Files.newOutputStream(file));
        return appConfig.gzCompression ? new java.util.zip.GZIPOutputStream(fileOut) : fileOut;
    }

    private void writeContent(Writer w) throws IOException {
        pageHeader(w);
        contentOpen(w);
        for (String c : bodyContent) w.write(c);
        contentClose(w);
    }

    private String rootPath() { return RootPath.of(rootLevel); }

    private void pageHeader(Writer w) throws IOException {
        String root = rootPath();
        w.write("<!doctype html>\n");
        w.write("<html lang=\"en\" data-root=\"" + root + "\">\n");
        w.write("<head>\n");
        w.write("<meta charset=\"utf-8\" />\n");
        w.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n");
        w.write("<title>" + title + "</title>\n");
        w.write("<meta name=\"generator\" content=\"" + Version.PRODUCT_NAME + " v" + Version.APP_VERSION + "\" />\n");
        w.write("<script>var rootLevel=" + rootLevel + ";</script>\n");
        for (String css : cssReferences) {
            w.write("<link rel=\"stylesheet\" href=\"" + root + css + "\" />\n");
        }
        for (String js : jsReferences) {
            w.write("<script src=\"" + root + js + "\" defer></script>\n");
        }
        w.write("</head>\n");
    }

    private void contentOpen(Writer w) throws IOException {
        String root = rootPath();
        w.write(bodyClass.isEmpty() ? "<body>\n" : "<body class=\"" + bodyClass + "\">\n");
        w.write("<a id=\"top\"></a>\n");
        w.write("<div class=\"ari-shell\">\n");

        // --- header ---
        w.write("<header class=\"ari-header\">\n");
        w.write("<button id=\"ari-navtoggle\" class=\"ari-iconbtn\" type=\"button\" aria-label=\"Menu\">"
            + "<svg class=\"ico\" viewBox=\"0 0 16 16\" aria-hidden=\"true\"><path d=\"M2 4h12M2 8h12M2 12h12\" stroke=\"currentColor\" stroke-width=\"1.6\" fill=\"none\"/></svg>"
            + "</button>\n");
        w.write("<span class=\"ari-brand\"><a href=\"" + root + Naming.mainHome().fullFileName() + "\">"
            + Version.PRODUCT_NAME + "</a></span>\n");
        w.write("<span class=\"ari-context\">");
        String serverLabel = appConfig.serverName != null && !appConfig.serverName.isEmpty()
            ? WebUtil.validate(appConfig.serverName)
            : (appConfig.fileMode ? "file export" : "server");
        w.write("<a href=\"" + root + Naming.serverInfo().fullFileName() + "\">" + serverLabel + "</a>");
        if (appConfig.companyName != null && !appConfig.companyName.isEmpty()) {
            String company = appConfig.companyUrl == null || appConfig.companyUrl.isEmpty()
                ? WebUtil.validate(appConfig.companyName)
                : "<a href=\"" + appConfig.companyUrl + "\" target=\"_blank\" rel=\"noopener\">" + WebUtil.validate(appConfig.companyName) + "</a>";
            w.write(" &middot; " + company);
        }
        w.write("</span>\n");
        w.write("<span class=\"ari-header-spacer\"></span>\n");
        // Opens the command palette (app.js) - also bound to Ctrl/Cmd-K and "/".
        w.write("<button id=\"ari-search\" class=\"ari-search-trigger\" type=\"button\" aria-label=\"Search (Ctrl-K)\">"
            + "<svg class=\"ico\" viewBox=\"0 0 16 16\" aria-hidden=\"true\"><path fill=\"currentColor\" d=\"M7 2a5 5 0 013.98 8.06l3 3-1.42 1.42-3-3A5 5 0 117 2zm0 2a3 3 0 100 6 3 3 0 000-6z\"/></svg>"
            + "<span>Search</span><kbd>Ctrl K</kbd></button>\n");
        w.write("<button id=\"ari-theme\" class=\"ari-iconbtn\" type=\"button\" aria-label=\"Toggle theme\">"
            + "<svg class=\"ico ari-theme-dark\" viewBox=\"0 0 16 16\" aria-hidden=\"true\"><path fill=\"currentColor\" d=\"M6 1.5A6.5 6.5 0 1014.5 10 5 5 0 016 1.5z\"/></svg>"
            + "<svg class=\"ico ari-theme-light\" viewBox=\"0 0 16 16\" aria-hidden=\"true\" hidden><path fill=\"currentColor\" d=\"M8 4a4 4 0 100 8 4 4 0 000-8zM8 0l1.2 2.2L8 4 6.8 2.2zm0 12l1.2 2.2L8 16l-1.2-1.8zM0 8l2.2-1.2L4 8l-1.8 1.2zm12 0l2.2-1.2L16 8l-1.8 1.2zM2.3 2.3l2.4.9L4 6 1.4 4.7zm9.3 9.3l2.4.9-.7 2.4L10.7 12zM13.7 2.3l-.7 2.4L10.7 4l.9-2.4zM4 10l-.7 2.4-2.4-.9L1.4 10z\"/></svg>"
            + "</button>\n");
        w.write("</header>\n");

        // --- sidebar (filled by app.js from window.ARI_NAV) ---
        w.write("<nav id=\"ari-nav\" class=\"ari-nav\" aria-label=\"Site\"></nav>\n");

        // --- main ---
        w.write("<main class=\"ari-main\"><div class=\"ari-main-inner\">\n");
    }

    private void contentClose(Writer w) throws IOException {
        w.write("\n</div></main>\n");
        w.write("<footer class=\"ari-footer\">\n");
        w.write("Page created " + DateTimeFormat.currentToHtmlString()
            + " by <a href=\"https://github.com/gabeluci/ARInside\" target=\"_blank\" rel=\"noopener\">"
            + Version.PRODUCT_NAME + " v" + Version.APP_VERSION + "</a>"
            + " &nbsp;&middot;&nbsp; <a href=\"#top\">Top</a>\n");
        w.write("</footer>\n");
        w.write("</div>\n</body>\n</html>\n");
    }
}
