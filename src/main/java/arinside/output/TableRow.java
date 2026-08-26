package arinside.output;

import java.util.ArrayList;
import java.util.List;

/** Java port of output/TableRow.{h,cpp}. */
public final class TableRow {
    private final String cssClass;
    private final List<TableCell> cells = new ArrayList<>();
    public String name = "";

    public TableRow() { this(""); }
    public TableRow(String cssClass) { this.cssClass = cssClass == null ? "" : cssClass; }

    public TableRow addCell(String text) { cells.add(new TableCell(text)); return this; }
    public TableRow addCell(TableCell cell) { cells.add(cell); return this; }

    public TableRow addCellList(String... texts) {
        cells.clear();
        for (String t : texts) cells.add(new TableCell(t));
        return this;
    }

    public TableRow addCellList(TableCell... cellList) {
        cells.clear();
        for (TableCell c : cellList) cells.add(c);
        return this;
    }

    void toXHtml(StringBuilder sb) {
        if (cssClass.isEmpty()) {
            sb.append("<tr>\n");
        } else {
            sb.append("<tr class=\"").append(cssClass).append("\">\n");
        }
        for (TableCell c : cells) c.toXHtml(sb);
        sb.append("</tr>\n");
    }

    void toCsv(StringBuilder sb) {
        for (TableCell c : cells) c.toCsv(sb);
    }
}
