package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.GroupRecord;
import arinside.ar.RoleRecord;
import arinside.ar.UserRecord;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.RoleIndex;

import java.util.Map;
import java.util.Set;

/**
 * Java port of doc/DocUserDetails.cpp. Now sourced from UserRecord (RawEntryQuery against the User
 * form - see IdentityRepository) rather than the old getListUser()-based UserInfo, so Full Name and
 * Group List are available - previously documented as a gap in this class, now closed.
 */
public final class UserDetailPage {
    private final AppConfig appConfig;
    private final Set<String> knownUserNames;
    private final RoleIndex roleIndex;
    private final Map<Integer, GroupRecord> groupsById;

    public UserDetailPage(AppConfig appConfig, Set<String> knownUserNames, RoleIndex roleIndex, Map<Integer, GroupRecord> groupsById) {
        this.appConfig = appConfig;
        this.knownUserNames = knownUserNames;
        this.roleIndex = roleIndex;
        this.groupsById = groupsById;
    }

    public void render(UserRecord user) {
        PagePath page = Naming.userDetail(user.loginName);
        WebPage webPage = new WebPage(page.fileName(), user.loginName, page.rootLevel(), appConfig);

        String head = URLLink.to("Users", Naming.userOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + WebUtil.objName(user.loginName);
        webPage.addContentHead(head);

        Table tbl = new Table("userDetails", "TblObjectList");
        tbl.addColumn(30, "Description");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Full Name", user.fullName == null ? "" : user.fullName));
        tbl.addRow(new TableRow().addCellList("Email", user.email == null ? "" : user.email));
        tbl.addRow(new TableRow().addCellList("License Type", AREnumLabels.licenseType(user.licenseType)));
        tbl.addRow(new TableRow().addCellList("Full Text License Type", AREnumLabels.licenseType(user.ftLicenseType)));
        tbl.addRow(new TableRow().addCellList("Default Notify Mechanism", AREnumLabels.defaultNotify(user.defaultNotify)));

        Table grpTbl = new Table("userGroups", "TblObjectList");
        grpTbl.addColumn(10, "Group ID");
        grpTbl.addColumn(90, "Group Name");
        for (Integer groupId : user.groupIds) {
            grpTbl.addRow(new TableRow().addCellList(Integer.toString(groupId), groupRef(groupId, page.rootLevel())));
        }
        tbl.addRow(new TableRow().addCellList("Group List", grpTbl.toXHtml()));

        webPage.addContent(tbl.toXHtml());
        webPage.addContent(ServerObjectHistoryWidget.render(user.owner, user.modifiedBy, user.modified, knownUserNames, page.rootLevel()));
        webPage.saveInFolder(page.path());
    }

    /**
     * Java port of CARInside::LinkToGroup(appRefName="", groupId, rootLevel) - the user page's own
     * Group List row previously hardcoded the literal group id as link text (e.g. "0" instead of
     * "Public") regardless of the group's real name, the same bug already found and fixed on
     * SchemaDetailPage/ActiveLinkDetailPage/ContainerDetailPage this session - just not yet applied
     * here. appRefName is empty here matching the C++ exactly (a user's group list isn't scoped to
     * any one application), so a negative (role) id essentially never resolves to a real role name
     * in practice - same as the real tool.
     */
    private String groupRef(int groupId, int rootLevel) {
        if (groupId < 0) {
            RoleRecord role = roleIndex == null ? null : roleIndex.find(groupId, "");
            if (role != null) return URLLink.to(role.name, Naming.roleDetail(role.requestId), ImageTag.Id.Role, rootLevel).toHtml();
            return Integer.toString(groupId);
        }
        GroupRecord group = groupsById == null ? null : groupsById.get(groupId);
        if (group != null) {
            return URLLink.to(group.name, Naming.groupDetail(groupId), ImageTag.Id.Group, rootLevel).toHtml();
        }
        return Integer.toString(groupId);
    }
}
