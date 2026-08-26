package arinside.output;

import java.util.ArrayList;
import java.util.List;

/** Java port of output/Table.{h,cpp}. */
public final class Table {
    private final String htmId;
    private String cssClass;
    private boolean hideHeader = false;
    private String emptyMessage = "Table contains no data";
    public String description = "";

    private final List<TableColumn> columns = new ArrayList<>();
    private final List<TableRow> rows = new ArrayList<>();

    public Table() { this("", ""); }
    public Table(String htmId, String cssClass) {
        this.htmId = htmId;
        this.cssClass = cssClass == null ? "" : cssClass;
    }

    public int numRows() { return rows.size(); }

    public Table addColumn(int width, String text) { return addColumn(width, text, "colStdClass"); }
    public Table addColumn(int width, String text, String cssClass) {
        columns.add(new TableColumn(width, text, cssClass));
        return this;
    }

    public Table addRow(TableRow row) { rows.add(row); return this; }
    public void clearRows() { rows.clear(); }
    public void clearColumns() { columns.clear(); }
    public void clear() { clearColumns(); clearRows(); }
    public void setCssClass(String cssClass) { this.cssClass = cssClass; }
    public void setEmptyMessage(String msg) { this.emptyMessage = msg; }
    /** Ported from CTable::RemoveEmptyMessageRow (called once callers know objCount > 0). */
    public void removeEmptyMessageRow() { this.emptyMessage = ""; }
    /** Ported from CTable::DisableHeader - used for small nested sub-tables (e.g. a field's per-group permission list) that don't need their own column header row. */
    public Table disableHeader() { this.hideHeader = true; return this; }

    public String toXHtml() {
        StringBuilder sb = new StringBuilder();
        if (!description.isEmpty()) {
            sb.append("<h2>\n").append(description).append("\n</h2>\n");
        }
        sb.append("<table id=\"").append(htmId).append("\"");
        if (!cssClass.isEmpty()) {
            sb.append(" class=\"").append(cssClass).append("\"\n");
        }
        sb.append(">\n");

        sb.append("<colgroup>\n");
        for (TableColumn c : columns) c.colToXHtml(sb);
        sb.append("</colgroup>\n");

        if (!hideHeader) {
            sb.append("<thead><tr>\n");
            for (TableColumn c : columns) c.headerCellToXHtml(sb);
            sb.append("</tr></thead>");
        }

        sb.append("<tbody>");
        if (!rows.isEmpty()) {
            for (TableRow r : rows) r.toXHtml(sb);
        } else if (!emptyMessage.isEmpty()) {
            sb.append("<tr>\n<td colspan=\"").append(columns.size()).append("\">")
                .append(emptyMessage).append("</td>\n</tr>\n");
        }
        sb.append("</tbody>");
        sb.append("</table>\n");
        return sb.toString();
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder();
        for (TableColumn c : columns) sb.append(c.getTitle()).append('\t');
        sb.append('\n');
        for (TableRow r : rows) {
            r.toCsv(sb);
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() { return toXHtml(); }
}
