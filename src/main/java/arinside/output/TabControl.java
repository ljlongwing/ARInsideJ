package arinside.output;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a tab widget as ARIA markup (a {@code role="tablist"} of {@code <button role="tab">} plus
 * {@code role="tabpanel"} divs). {@code app.js} wires the click/keyboard/hash behaviour - the panel
 * div keeps the id {@code tab-N} so existing {@code #tab-N} deep links still resolve, and the tab
 * button carries {@code data-panel="tab-N"} for app.js to match on {@code hashchange}.
 */
public final class TabControl {
    private record Tab(String name, String content, String panelId, String btnId) {}

    private final List<Tab> tabs = new ArrayList<>();

    public void addTab(String name, String content) {
        int n = tabs.size() + 1;
        tabs.add(new Tab(name, content, "tab-" + n, "tabbtn-" + n));
    }

    public String toXHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"ari-tabs\" id=\"MainObjectTabCtrl\">\n");
        sb.append("<div class=\"ari-tablist\" role=\"tablist\">\n");
        for (Tab t : tabs) {
            sb.append("<button class=\"ari-tab\" type=\"button\" role=\"tab\" id=\"").append(t.btnId())
                .append("\" data-panel=\"").append(t.panelId())
                .append("\" aria-controls=\"").append(t.panelId())
                .append("\" aria-selected=\"false\" tabindex=\"-1\">")
                .append(t.name()).append("</button>\n");
        }
        sb.append("</div>\n");
        for (Tab t : tabs) {
            sb.append("<div class=\"ari-tabpanel\" id=\"").append(t.panelId())
                .append("\" role=\"tabpanel\" aria-labelledby=\"").append(t.btnId())
                .append("\" hidden>\n").append(t.content()).append("\n</div>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }
}
