package arinside.output;

/** Java port of output/TableColumn.{h,cpp}. */
public final class TableColumn {
    private final int width;
    private final String title;
    private final String cssClass;

    public TableColumn(int width, String title) { this(width, title, ""); }

    public TableColumn(int width, String title, String cssClass) {
        this.width = width;
        this.title = title;
        this.cssClass = cssClass == null ? "" : cssClass;
    }

    public String getTitle() { return title; }

    void colToXHtml(StringBuilder sb) {
        sb.append("<col width=\"").append(width).append("%\"/>\n");
    }

    void headerCellToXHtml(StringBuilder sb) {
        if (cssClass.isEmpty()) {
            sb.append("<th>").append(title).append("</th>\n");
        } else {
            sb.append("<th class=\"").append(cssClass).append("\" width=\"").append(width).append("%\">")
                .append(title).append("</th>\n");
        }
    }
}
