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
 * Java port of output/WebPage.{h,cpp}. Supports the C++'s optional GZip output
 * (ARINSIDE_ENABLE_ZLIB_SUPPORT / AppConfig.gzCompression) - see save()'s javadoc.
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
    private String navContent = "";
    private final List<String> cssReferences = new ArrayList<>();
    private final List<String> jsReferences = new ArrayList<>();

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
        addContent("<div id='locLeft'>");
        addContent(description);
        addContent("</div><div id='locRight'>");
        addContent(rightInfo.isEmpty() ? "&nbsp;" : rightInfo);
        addContent("</div>");
    }

    public void setNavigation(String nav) { this.navContent = nav; }

    public WebPage addScriptReference(String scriptPath) { jsReferences.add(scriptPath); return this; }
    public WebPage addStyleSheetReference(String cssPath) { cssReferences.add(cssPath); return this; }

    private void setupDefaultReferences() {
        addStyleSheetReference("img/style.css");
        addStyleSheetReference("img/jquery-ui-custom.css");
        addScriptReference("img/sortscript.js");
        addScriptReference("img/tabscript.js");
        addScriptReference("img/jquery.js");
        addScriptReference("img/jquery-ui.js");
        addScriptReference("img/arshelper.js");
    }

    /**
     * Saves the page under &lt;targetFolder&gt;/&lt;path&gt;/&lt;fileName&gt;.htm(.gz). Returns 1
     * on success (matches CWebPage::SaveInFolder). Matches the C++'s ARINSIDE_ENABLE_ZLIB_SUPPORT
     * / AppConfig.gzCompression gzip-output option (ogzstream in the C++) - java.util.zip needs
     * no extra dependency, unlike the C++'s vendored zlib.
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

    private void pageHeader(Writer w) throws IOException {
        w.write("<?xml version=\"1.0\" ?>\n");
        w.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        w.write("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n");
        w.write("<head>\n");
        w.write("<title>" + title + "</title>\n");
        w.write("<meta http-equiv=\"content-language\" content=\"EN\" />\n");
        w.write("<meta http-equiv=\"content-type\" content=\"text/html; charset=ISO-8859-1\" />\n");
        w.write("<meta http-equiv=\"expires\" content=\"-1\" />\n");
        w.write("<meta name=\"author\" content=\"" + Version.PRODUCT_NAME + "\" />\n");
        w.write("<script type='text/javascript'>var rootLevel=" + rootLevel + ";</script>\n");
        for (String css : cssReferences) {
            w.write("<link rel=\"stylesheet\" type=\"text/css\" href=\"" + RootPath.of(rootLevel) + css + "\" />\n");
        }
        for (String js : jsReferences) {
            w.write("<script src=\"" + RootPath.of(rootLevel) + js + "\" type=\"text/javascript\"></script>\n");
        }
        w.write("</head>\n");
    }

    private void dynamicHeaderText(Writer w) throws IOException {
        w.write("<table>\n<tr>\n");
        w.write("<td>" + URLLink.to("Main", Naming.mainHome(), ImageTag.Id.Server, rootLevel) + "</td>\n");
        w.write("<td> (Server: " + URLLink.to(appConfig.serverName, Naming.serverInfo(), ImageTag.Id.NoImage, rootLevel) + "</td>\n");
        w.write("<td>@</td>\n");
        w.write("<td><a href=\"" + appConfig.companyUrl + "\" target=\"_blank\">" + appConfig.companyName + "</a>)</td>\n");
        w.write("</tr>\n</table>\n");
    }

    private void dynamicFooterText(Writer w) throws IOException {
        w.write("<table><tr>\n");
        w.write("<td>" + URLLink.to("Main", Naming.mainHome(), ImageTag.Id.Prev, rootLevel) + "</td>\n");
        w.write("<td>&nbsp;</td>\n");
        w.write("<td>" + URLLink.linkToTop(rootLevel) + "</td>\n");
        w.write("<td>&nbsp;</td>\n");
        w.write("<td>Page created " + DateTimeFormat.currentToHtmlString()
            + " by <a href=\"https://github.com/ljlongwing/ARInsideJ\" target=\"_blank\">"
            + Version.PRODUCT_NAME + " v" + Version.APP_VERSION + "</a></td>");
        w.write("</tr></table>\n");
    }

    private void contentOpen(Writer w) throws IOException {
        w.write("<body>\n");
        w.write(URLLink.createTopAnchor().toHtml() + "\n");
        w.write("<table class=\"TblMain\">\n");
        w.write("<tr><td class=\"TdMainHeader\" colspan=\"3\">\n");
        dynamicHeaderText(w);
        w.write("</td></tr><tr><td class=\"TdMainMenu\">\n");
        if (!navContent.isEmpty()) {
            w.write("<div id=\"form_navigation\" class=\"form_navigation\">\n" + navContent + "\n</div>\n");
        }
        w.write("<iframe id=\"IFrameMenu\" src=\"" + RootPath.of(rootLevel) + "template/navigation." + WebUtil.webPageSuffix()
            + "\" name=\"Navigation\" frameborder=\"0\">\n");
        w.write("<p>IFrame not supported by this browser.</p></iframe></td><td class=\"TdMainContent\">\n");
    }

    private void contentClose(Writer w) throws IOException {
        w.write("</td>\n<td></td>\n");
        w.write("</tr><tr><td class=\"TdMainButtom\" colspan=\"3\">\n");
        dynamicFooterText(w);
        w.write("\n</td></tr></table></body></html>\n");
    }
}
