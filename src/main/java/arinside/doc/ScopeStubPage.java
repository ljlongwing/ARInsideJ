package arinside.doc;

import arinside.config.AppConfig;
import arinside.output.PagePath;
import arinside.output.WebPage;
import arinside.output.WebUtil;

/**
 * Java port of nothing - new for the {@code --scope} feature (task #97, no C++ counterpart).
 * Written in place of a real detail page for any object referenced from within a scoped export's
 * tree but not itself in scope, so no link inside a scoped run ever 404s. Deliberately does no AR
 * API fetch of its own - the object's name (already known from the same full listing every
 * overview page already uses) and its normal {@code Naming.*Detail()} path are all it needs.
 */
public final class ScopeStubPage {
    private ScopeStubPage() {}

    public static void render(AppConfig appConfig, String typeLabel, String name, PagePath page) {
        WebPage webPage = new WebPage(page.fileName(), typeLabel + ": " + name, page.rootLevel(), appConfig);
        webPage.addContentHead(WebUtil.validate(name));
        webPage.addContent("<p>This " + typeLabel.toLowerCase() + " was excluded from this scoped export (--scope \""
            + WebUtil.validate(appConfig.scope) + "\"). Run without --scope to see full documentation.</p>\n");
        webPage.saveInFolder(page.path());
    }
}
