package arinside.doc;

import arinside.output.*;
import arinside.util.DateTimeFormat;
import com.bmc.arsys.api.DiaryItem;
import com.bmc.arsys.api.DiaryListValue;
import com.bmc.arsys.api.ObjectBase;
import com.bmc.arsys.api.Timestamp;

import java.util.Set;

/**
 * Java port of CARInside::ServerObjectHistory - the shared Owner/Last-changed/History-Log/Helptext
 * block shown on every workflow/schema/menu/container detail page. Works against any ObjectBase
 * subtype (Form/ActiveLink/Filter/Escalation/Menu/Container/Field all extend it, sharing
 * getOwner()/getLastChangedBy()/getLastUpdateTime()/getDiary()/getHelpText()), unlike
 * User/Group/Role which are raw-entry-query records, not ObjectBase instances - those don't get
 * this widget, they show a smaller, separately-built set of rows instead.
 *
 * The C++'s ARDecodeDiary() manual decode step isn't needed - the Java API's getDiary() already
 * returns a decoded `DiaryListValue` (a List&lt;DiaryItem&gt;), each carrying user/timestamp/text
 * directly.
 */
public final class ServerObjectHistoryWidget {
    private ServerObjectHistoryWidget() {}

    public static String render(ObjectBase obj, Set<String> knownUserNames, int rootLevel) {
        Table tbl = new Table("history", "TblObjectHistory");
        tbl.description = "Change History";
        tbl.addColumn(20, "Description");
        tbl.addColumn(80, "Value");

        tbl.addRow(new TableRow().addCellList("Owner", userLink(obj.getOwner(), knownUserNames, rootLevel)));

        String lastChanged = (obj.getLastUpdateTime() == null ? "" : DateTimeFormat.toHtmlString(obj.getLastUpdateTime().getValue()) + " ")
            + userLink(obj.getLastChangedBy(), knownUserNames, rootLevel);
        tbl.addRow(new TableRow().addCellList("Last changed", lastChanged));

        tbl.addRow(new TableRow().addCellList("History Log", historyLog(obj.getDiary(), knownUserNames, rootLevel)));

        String helpText = obj.getHelpText();
        tbl.addRow(new TableRow().addCellList("Helptext", helpText == null || helpText.isEmpty()
            ? "" : WebUtil.validate(helpText).replace("\n", "<br/>")));

        return tbl.toXHtml();
    }

    /**
     * Overload for User/Group/Role - loaded as raw-query records (UserRecord/GroupRecord/RoleRecord),
     * not ObjectBase instances, but core/ARUser.cpp, core/ARGroup.cpp and core/ARRole.cpp all back
     * GetOwner()/GetLastChanged()/GetTimestamp() with the exact same CreatedBy/ModifiedBy/ModifiedDate
     * fields already captured on these records (confirmed by reading all three) - so this renders the
     * identical Owner/Last changed/History Log/Helptext table. History Log and Helptext are always
     * empty for these three: GetChangeDiary()/GetHelpText() are hardcoded to return NULL in the real
     * C++ ("no support for helptext") - not a scope cut, matching real (empty) behavior exactly.
     */
    public static String render(String owner, String modifiedBy, Timestamp modified, Set<String> knownUserNames, int rootLevel) {
        Table tbl = new Table("history", "TblObjectHistory");
        tbl.description = "Change History";
        tbl.addColumn(20, "Description");
        tbl.addColumn(80, "Value");

        tbl.addRow(new TableRow().addCellList("Owner", userLink(owner, knownUserNames, rootLevel)));

        String lastChanged = (modified == null ? "" : DateTimeFormat.toHtmlString(modified.getValue()) + " ") + userLink(modifiedBy, knownUserNames, rootLevel);
        tbl.addRow(new TableRow().addCellList("Last changed", lastChanged));

        tbl.addRow(new TableRow().addCellList("History Log", ""));
        tbl.addRow(new TableRow().addCellList("Helptext", ""));

        return tbl.toXHtml();
    }

    private static String historyLog(DiaryListValue diary, Set<String> knownUserNames, int rootLevel) {
        if (diary == null || diary.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DiaryItem item : diary) {
            sb.append(item.getTimestamp() == null ? "" : DateTimeFormat.toHtmlString(item.getTimestamp().getValue()))
                .append(" ").append(userLink(item.getUser(), knownUserNames, rootLevel)).append("<br/>\n")
                .append(item.getText() == null ? "" : WebUtil.validate(item.getText())).append("<br/><br/>\n");
        }
        return sb.toString();
    }

    /** Matches CARInside::LinkToUser - only links when the name resolves to a real, currently-registered user, otherwise plain text (avoids dangling links for historical/system account names). Package-visible so GroupPermissionTable can reuse it for CGroupTable's own "By" column. */
    static String userLink(String userName, Set<String> knownUserNames, int rootLevel) {
        if (userName == null || userName.isEmpty()) return "";
        if (!knownUserNames.contains(userName)) return WebUtil.validate(userName);
        return URLLink.to(userName, Naming.userDetail(userName), ImageTag.Id.User, rootLevel).toHtml();
    }
}
