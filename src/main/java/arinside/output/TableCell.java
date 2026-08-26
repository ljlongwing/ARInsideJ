package arinside.output;

/** Java port of output/TableCell.{h,cpp}. */
public final class TableCell {
    public final String content;
    public final String cssClass;

    public TableCell(String content) { this(content, ""); }
    public TableCell(int content) { this(Integer.toString(content), ""); }
    public TableCell(int content, String cssClass) { this(Integer.toString(content), cssClass); }

    public TableCell(String content, String cssClass) {
        this.content = content;
        this.cssClass = cssClass == null ? "" : cssClass;
    }

    void toXHtml(StringBuilder sb) {
        if (cssClass.isEmpty()) {
            sb.append("<td>").append(content).append("</td>\n");
        } else {
            sb.append("<td class=\"").append(cssClass).append("\">").append(content).append("</td>\n");
        }
    }

    void toCsv(StringBuilder sb) {
        sb.append(content).append('\t');
    }
}
