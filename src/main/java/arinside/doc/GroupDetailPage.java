package arinside.doc;

import arinside.ar.AREnumLabels;
import arinside.ar.GroupRecord;
import arinside.ar.RoleRecord;
import arinside.ar.UserRecord;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.PermissionIndex;
import com.bmc.arsys.api.Constants;

import java.util.List;
import java.util.Map;

/**
 * Java port of doc/DocGroupDetails.cpp. Renders the group's own detail page plus its 7 permission
 * companion pages (form/field/active-link/packing-list/al-guide/webservice/user) via PermissionIndex
 * - matching the C++, which always writes all 7 companion pages regardless of whether they end up
 * empty (only the *link text* on the main page changes based on count).
 */
public final class GroupDetailPage {
    private final AppConfig appConfig;
    private final GlobalFieldIndex globalFields;
    private final java.util.Set<String> knownUserNames;

    public GroupDetailPage(AppConfig appConfig, GlobalFieldIndex globalFields, java.util.Set<String> knownUserNames) {
        this.appConfig = appConfig;
        this.globalFields = globalFields;
        this.knownUserNames = knownUserNames;
    }

    public void render(GroupRecord group, PermissionIndex permIdx, Map<Integer, List<UserRecord>> usersByGroup, List<RoleRecord> roles) {
        PagePath page = Naming.groupDetail(group.groupId);
        WebPage webPage = new WebPage(page.fileName(), group.name, page.rootLevel(), appConfig);

        String head = URLLink.to("Groups", Naming.groupOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Group, page.rootLevel()).toHtml() + WebUtil.objName(group.name)
            + " (Id: " + group.groupId + ")";
        webPage.addContentHead(head);

        Table tbl = new Table("groupGeneral", "TblObjectList");
        tbl.addColumn(30, "Property");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Group Name", WebUtil.validate(group.name)));
        tbl.addRow(new TableRow().addCellList("Long Group Name", group.longName == null ? "" : WebUtil.validate(group.longName)));
        tbl.addRow(new TableRow().addCellList("Group Type", AREnumLabels.groupType(group.groupType)));
        tbl.addRow(new TableRow().addCellList("Group Category", AREnumLabels.groupCategory(group.category)));
        if (group.category == Constants.AR_GROUP_CATEGORY_COMPUTED) {
            tbl.addRow(new TableRow().addCellList("Computed Group Definition", group.computedQualification == null ? "" : group.computedQualification));
        }
        tbl.addRow(new TableRow().addCellList("Role Mapping", roleReferences(group, roles, page.rootLevel())));
        tbl.addRow(new TableRow().addCellList("Permissions", permissionsSummary(group, permIdx, usersByGroup, page.rootLevel())));
        webPage.addContent(tbl.toXHtml());
        webPage.addContent(ServerObjectHistoryWidget.render(group.owner, group.modifiedBy, group.modified, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());

        int gid = group.groupId;
        formList(gid, group.name, permIdx.visibleForms(gid), permIdx.subadminForms(gid));
        fieldList(gid, group.name, permIdx.fields(gid));
        activeLinkList(gid, group.name, permIdx.activeLinks(gid), permIdx);
        containerList(gid, group.name, Naming.groupContainerList(gid, 3), "Packing List Permission",
            permIdx.containers(gid, 3), permIdx.containerSubadmin(gid), permIdx);
        containerList(gid, group.name, Naming.groupContainerList(gid, 1), "Active Link Guide Permission",
            permIdx.containers(gid, 1), List.of(), permIdx);
        containerList(gid, group.name, Naming.groupContainerList(gid, 5), "Webservice Permission",
            permIdx.containers(gid, 5), List.of(), permIdx);
        userList(gid, group.name, usersByGroup.getOrDefault(gid, List.of()));
    }

    private String roleReferences(GroupRecord group, List<RoleRecord> roles, int rootLevel) {
        Table tbl = new Table("roleList", "TblNoSort");
        tbl.addColumn(30, "Type");
        tbl.addColumn(70, "Role");
        for (RoleRecord r : roles) {
            if (r.groupsProd.contains(group.groupId)) {
                tbl.addRow(new TableRow().addCellList("Production", URLLink.to(r.name, Naming.roleDetail(r.requestId), ImageTag.Id.Role, rootLevel).toHtml()));
            }
            if (r.groupsTest.contains(group.groupId)) {
                tbl.addRow(new TableRow().addCellList("Test", URLLink.to(r.name, Naming.roleDetail(r.requestId), ImageTag.Id.Role, rootLevel).toHtml()));
            }
        }
        return tbl.toXHtml();
    }

    private String permissionsSummary(GroupRecord group, PermissionIndex permIdx, Map<Integer, List<UserRecord>> usersByGroup, int rootLevel) {
        int gid = group.groupId;
        Table tbl = new Table("permissionList", "TblNoSort");
        tbl.addColumn(100, "Permission Details");
        tbl.addRow(new TableRow().addCell(summaryLine("Form Permission", "No Form Access", permIdx.visibleForms(gid).size(), Naming.groupFormList(gid), rootLevel)));
        tbl.addRow(new TableRow().addCell(summaryLine("Field Permission", "No Field Permission", permIdx.fields(gid).values().stream().mapToInt(List::size).sum(), Naming.groupFieldList(gid), rootLevel)));
        tbl.addRow(new TableRow().addCell(summaryLine("Active Link Permission", "No Active Link Permission", permIdx.activeLinks(gid).size(), Naming.groupActiveLinkList(gid), rootLevel)));
        // Java port of DocGroupDetails.cpp's GroupPermissions() "Users in group" row (UserDoc) -
        // previously missing here entirely: the Group Members companion page (userList() below)
        // was still generated and reachable by direct URL, but nothing on the main group page ever
        // linked to it.
        tbl.addRow(new TableRow().addCell(summaryLine("Group Members", "No Group Members", usersByGroup.getOrDefault(gid, List.of()).size(), Naming.groupUserList(gid), rootLevel)));
        tbl.addRow(new TableRow().addCell(summaryLine("Packing List Permission", "No Packing List Permission", permIdx.containers(gid, 3).size(), Naming.groupContainerList(gid, 3), rootLevel)));
        tbl.addRow(new TableRow().addCell(summaryLine("Active Link Guide Permission", "No Active Link Guide Permission", permIdx.containers(gid, 1).size(), Naming.groupContainerList(gid, 1), rootLevel)));
        tbl.addRow(new TableRow().addCell(summaryLine("Webservice Permission", "No Webservice Permission", permIdx.containers(gid, 5).size(), Naming.groupContainerList(gid, 5), rootLevel)));
        return tbl.toXHtml();
    }

    static String summaryLine(String title, String emptyText, int count, PagePath link, int rootLevel) {
        if (count == 0) return emptyText;
        return URLLink.to(title, link, ImageTag.Id.Document, rootLevel).toHtml() + " (" + count + ")<br/>";
    }

    private void formList(int gid, String groupName, List<PermissionIndex.FormEntry> visible, List<String> subadmin) {
        PagePath page = Naming.groupFormList(gid);
        WebPage webPage = new WebPage(page.fileName(), "Form Permission " + groupName, page.rootLevel(), appConfig);
        webPage.addContentHead(companionHead(gid, groupName, "Form Permission", page.rootLevel()));

        Table tbl = new Table("schemaList", "TblObjectList");
        tbl.description = "Form Permission";
        tbl.addColumn(15, "Visible");
        tbl.addColumn(85, "Form Name");
        for (PermissionIndex.FormEntry f : visible) {
            ImageTag.Id icon = f.permissionValue() == Constants.AR_PERMISSIONS_VISIBLE ? ImageTag.Id.Visible : ImageTag.Id.Hidden;
            tbl.addRow(new TableRow().addCellList(new ImageTag(icon, page.rootLevel()).toHtml(),
                URLLink.to(f.name(), Naming.schemaDetail(f.name(), globalFields.isOverlaid(f.name())), ImageTag.Id.Schema, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());

        Table subTbl = new Table("schemaListSubadmin", "TblObjectList");
        subTbl.description = "Form Subadministrator Permission";
        subTbl.addColumn(100, "Form Name");
        for (String name : subadmin) {
            subTbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.schemaDetail(name, globalFields.isOverlaid(name)), ImageTag.Id.Schema, page.rootLevel()).toHtml()));
        }
        webPage.addContent(subTbl.toXHtml());

        webPage.saveInFolder(page.path());
    }

    private void fieldList(int gid, String groupName, Map<String, List<PermissionIndex.FieldEntry>> byForm) {
        PagePath page = Naming.groupFieldList(gid);
        WebPage webPage = new WebPage(page.fileName(), "Field Permission " + groupName, page.rootLevel(), appConfig);
        webPage.addContentHead(companionHead(gid, groupName, "Field Permission", page.rootLevel()));

        for (var entry : byForm.entrySet()) {
            Table tbl = new Table("fieldList", "TblObjectList");
            tbl.description = URLLink.to(entry.getKey(), Naming.schemaDetail(entry.getKey(), globalFields.isOverlaid(entry.getKey())), ImageTag.Id.Schema, page.rootLevel()).toHtml();
            tbl.addColumn(70, "Field");
            tbl.addColumn(30, "Permission");
            for (PermissionIndex.FieldEntry f : entry.getValue()) {
                tbl.addRow(new TableRow().addCellList(f.fieldName(), fieldPermissionLabel(f.permissionValue())));
            }
            webPage.addContent(tbl.toXHtml());
        }

        webPage.saveInFolder(page.path());
    }

    private void activeLinkList(int gid, String groupName, List<String> names, PermissionIndex permIdx) {
        PagePath page = Naming.groupActiveLinkList(gid);
        WebPage webPage = new WebPage(page.fileName(), "Active Link Permission " + groupName, page.rootLevel(), appConfig);
        webPage.addContentHead(companionHead(gid, groupName, "Active Link Permission", page.rootLevel()));

        Table tbl = new Table("alList", "TblObjectList");
        tbl.addColumn(100, "Active Link");
        for (String name : names) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.activeLinkDetail(name, permIdx.isOverlaidActiveLink(name)), ImageTag.Id.ActiveLink, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());

        webPage.saveInFolder(page.path());
    }

    private void containerList(int gid, String groupName, PagePath page, String title, List<PermissionIndex.ContainerEntry> entries, List<String> subadmin, PermissionIndex permIdx) {
        WebPage webPage = new WebPage(page.fileName(), title + " " + groupName, page.rootLevel(), appConfig);
        webPage.addContentHead(companionHead(gid, groupName, title, page.rootLevel()));

        Table tbl = new Table("containerList", "TblObjectList");
        tbl.description = "Container Permission";
        tbl.addColumn(100, "Container");
        for (PermissionIndex.ContainerEntry c : entries) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(c.name(), Naming.containerDetail(c.containerType(), c.name(), permIdx.isOverlaidContainer(c.containerType(), c.name())), containerIcon(c.containerType()), page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());

        if (!subadmin.isEmpty()) {
            Table subTbl = new Table("containerListSubadmin", "TblObjectList");
            subTbl.description = "Subadministrator Permission";
            subTbl.addColumn(100, "Container");
            for (String name : subadmin) {
                subTbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.containerDetail(3, name, permIdx.isOverlaidContainer(3, name)), ImageTag.Id.PackingList, page.rootLevel()).toHtml()));
            }
            webPage.addContent(subTbl.toXHtml());
        }

        webPage.saveInFolder(page.path());
    }

    private void userList(int gid, String groupName, List<UserRecord> members) {
        PagePath page = Naming.groupUserList(gid);
        WebPage webPage = new WebPage(page.fileName(), "Group Members " + groupName, page.rootLevel(), appConfig);
        webPage.addContentHead(companionHead(gid, groupName, "Group Members", page.rootLevel()));

        Table tbl = new Table("userList", "TblObjectList");
        tbl.addColumn(100, "Login Name");
        for (UserRecord u : members) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(u.loginName, Naming.userDetail(u.loginName), ImageTag.Id.User, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());

        webPage.saveInFolder(page.path());
    }

    /** Shared by RoleDetailPage's companion pages too (roles pass ownerListLabel="Roles", ownerIcon=Role). */
    static String companionHead(String ownerListLabel, PagePath ownerOverview, PagePath ownerDetail, String ownerName, ImageTag.Id ownerIcon, String title, int rootLevel) {
        return URLLink.to(ownerListLabel, ownerOverview, ImageTag.Id.NoImage, rootLevel).toHtml()
            + " &gt; " + URLLink.to(ownerName, ownerDetail, ownerIcon, rootLevel).toHtml()
            + " &gt; " + WebUtil.objName(title);
    }

    private static String companionHead(int gid, String groupName, String title, int rootLevel) {
        return companionHead("Groups", Naming.groupOverview(), Naming.groupDetail(gid), groupName, ImageTag.Id.Group, title, rootLevel);
    }

    /**
     * Java port of CAREnum::ObjectPermission - whole-object (group/container/schema-level)
     * permission values: None/Visible/Hidden. AR_PERMISSIONS_VISIBLE and AR_PERMISSIONS_HIDDEN share
     * their raw numeric values (1/2) with AR_PERMISSIONS_VIEW/AR_PERMISSIONS_CHANGE respectively, so
     * this must NOT be used for field-level permissions (see {@link #fieldPermissionLabel}) or a
     * real "Hidden" object permission silently mislabels as "Change".
     */
    static String objectPermissionLabel(int permissionValue) {
        if (permissionValue == Constants.AR_PERMISSIONS_VISIBLE) return "Visible";
        if (permissionValue == Constants.AR_PERMISSIONS_HIDDEN) return "Hidden";
        if (permissionValue == Constants.AR_PERMISSIONS_NONE) return "None";
        return "";
    }

    /** Java port of CAREnum::FieldPermission - per-field permission values: None/View/Change (see objectPermissionLabel's javadoc for why this is a distinct function, not just different wording). */
    static String fieldPermissionLabel(int permissionValue) {
        if (permissionValue == Constants.AR_PERMISSIONS_VIEW) return "View";
        if (permissionValue == Constants.AR_PERMISSIONS_CHANGE) return "Change";
        if (permissionValue == Constants.AR_PERMISSIONS_NONE) return "None";
        return "";
    }

    static ImageTag.Id containerIcon(int containerType) {
        return switch (containerType) {
            case 1 -> ImageTag.Id.ActiveLinkGuide;
            case 2 -> ImageTag.Id.Application;
            case 3 -> ImageTag.Id.PackingList;
            case 4 -> ImageTag.Id.FilterGuide;
            case 5 -> ImageTag.Id.Webservice;
            default -> ImageTag.Id.NoImage;
        };
    }
}
