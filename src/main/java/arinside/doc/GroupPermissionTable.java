package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.GroupRecord;
import arinside.ar.RoleRecord;
import arinside.output.*;
import arinside.scan.RoleIndex;
import arinside.util.DateTimeFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of output/GroupTable.cpp (CGroupTable) - the rich Name/ID/Type/Category/Modified/By
 * table DocAlDetails.cpp's Permissions() and DocContainerHelper.cpp's SubadminList() both render
 * for a group-id list, used by ActiveLinkDetailPage.permissions() and
 * ContainerDetailPage.subadminList().
 *
 * <p>Role rows genuinely use a different cell layout than group rows under the same 6-column
 * header (Type="Role"/Name-link/ID/Modified/By - only 5 cells, one column short since "Role" takes
 * the slot "Name" occupies for group rows, leaving "Category" perpetually blank on a role row) -
 * a real, confirmed C++ quirk (read directly from GroupTable.cpp's AddRoleRow vs AddGroupRow), not
 * something to "fix" into alignment.
 */
final class GroupPermissionTable {
    private GroupPermissionTable() {}

    static Table render(String tableId, List<Integer> groupIds, String appRefName, RoleIndex roleIndex,
                         Map<Integer, GroupRecord> groupsById, Set<String> knownUserNames, int rootLevel) {
        Table tbl = new Table(tableId, "TblObjectList");
        tbl.addColumn(0, "Name");
        tbl.addColumn(0, "ID");
        tbl.addColumn(0, "Type");
        tbl.addColumn(0, "Category");
        tbl.addColumn(0, "Modified");
        tbl.addColumn(0, "By");
        int count = 0;
        if (groupIds != null) {
            for (int gid : groupIds) {
                tbl.addRow(gid < 0 ? roleRow(gid, appRefName, roleIndex, rootLevel)
                    : groupRow(gid, groupsById, knownUserNames, rootLevel));
                count++;
            }
        }
        if (count > 0) tbl.removeEmptyMessageRow();
        return tbl;
    }

    private static TableRow roleRow(int roleId, String appRefName, RoleIndex roleIndex, int rootLevel) {
        RoleRecord role = roleIndex == null ? null : roleIndex.find(roleId, appRefName);
        if (role != null) {
            return new TableRow().addCellList("Role",
                URLLink.to(role.name, Naming.roleDetail(role.requestId), ImageTag.Id.Role, rootLevel).toHtml(),
                Integer.toString(role.roleId),
                role.modified == null ? "" : DateTimeFormat.toHtmlString(role.modified.getValue()),
                role.modifiedBy == null || role.modifiedBy.isEmpty() ? "" : WebUtil.validate(role.modifiedBy));
        }
        return new TableRow().addCellList("Role", roleId + " (not loaded)", Integer.toString(roleId), WebUtil.EMPTY_VALUE, WebUtil.EMPTY_VALUE);
    }

    private static TableRow groupRow(int groupId, Map<Integer, GroupRecord> groupsById, Set<String> knownUserNames, int rootLevel) {
        GroupRecord g = groupsById == null ? null : groupsById.get(groupId);
        if (g != null) {
            return new TableRow().addCellList(
                URLLink.to(g.name, Naming.groupDetail(groupId), ImageTag.Id.Group, rootLevel).toHtml(),
                Integer.toString(groupId),
                AREnumLabels.groupType(g.groupType),
                AREnumLabels.groupCategory(g.category),
                g.modified == null ? "" : DateTimeFormat.toHtmlString(g.modified.getValue()),
                knownUserNames == null ? "" : ServerObjectHistoryWidget.userLink(g.modifiedBy, knownUserNames, rootLevel));
        }
        return new TableRow().addCellList(groupId + " (not loaded)", Integer.toString(groupId), WebUtil.EMPTY_VALUE, WebUtil.EMPTY_VALUE, WebUtil.EMPTY_VALUE, WebUtil.EMPTY_VALUE);
    }
}
