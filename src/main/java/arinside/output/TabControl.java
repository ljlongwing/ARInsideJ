package arinside.output;

import java.util.ArrayList;
import java.util.List;

/** Java port of output/TabControl.{h,cpp} - a jQuery-UI tabs widget wrapper; the actual tab-init JS lives in schema_page.js (bundled web asset), not here. */
public final class TabControl {
    private record Tab(String name, String content, String htmlId) {}

    private final List<Tab> tabs = new ArrayList<>();

    public void addTab(String name, String content) {
        tabs.add(new Tab(name, content, "tab-" + (tabs.size() + 1)));
    }

    public String toXHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"MainObjectTabCtrl\">\n");
        sb.append("<ul>\n");
        for (Tab t : tabs) {
            sb.append("<li><a href=\"#").append(t.htmlId()).append("\">").append(t.name()).append("</a></li>\n");
        }
        sb.append("</ul>\n");
        for (Tab t : tabs) {
            sb.append("<div id=\"").append(t.htmlId()).append("\">\n").append(t.content()).append("\n</div>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }
}
