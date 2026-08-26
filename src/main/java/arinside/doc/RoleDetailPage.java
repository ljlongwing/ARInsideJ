package arinside.doc;

import arinside.ar.RoleRecord;
import arinside.config.AppConfig;
import arinside.output.*;
import arinside.scan.GlobalFieldIndex;
import arinside.scan.PermissionIndex;
import com.bmc.arsys.api.Constants;

import java.util.List;
import java.util.Map;

/**
 * Java port of doc/DocRoleDetails.cpp. Renders the role's own detail page plus its 6 permission
 * companion pages (form/field/active-link/packing-list/al-guide/webservice) via PermissionIndex,
 * using role.roleId (a negative number) as the lookup key - see PermissionIndex's javadoc for why
 * roles and groups share the same underlying index. Roles have no "list_user" companion page
 * (unlike groups) since a role isn't something a user is a direct member of.
 */
public final class RoleDetailPage {
    private final AppConfig appConfig;
    private final GlobalFieldIndex globalFields;
    /** groupId -> real group name, built once from the full groups list (see Main.java - groups are documented before roles specifically so this map is ready) so Test/Production Group can hyperlink to the group's real name, matching the C++'s LinkToGroup(appName, groupId, rootLevel). */
    private final Map<Integer, String> groupNamesById;
    private final java.util.Set<String> knownUserNames;

    public RoleDetailPage(AppConfig appConfig, GlobalFieldIndex globalFields, Map<Integer, String> groupNamesById, java.util.Set<String> knownUserNames) {
        this.appConfig = appConfig;
        this.globalFields = globalFields;
        this.groupNamesById = groupNamesById;
        this.knownUserNames = knownUserNames;
    }

    public void render(RoleRecord role, PermissionIndex permIdx) {
        PagePath page = Naming.roleDetail(role.requestId);
        WebPage webPage = new WebPage(page.fileName(), role.name, page.rootLevel(), appConfig);

        String head = URLLink.to("Roles", Naming.roleOverview(), ImageTag.Id.NoImage, page.rootLevel()).toHtml()
            + " &gt; " + new ImageTag(ImageTag.Id.Role, page.rootLevel()).toHtml() + WebUtil.objName(role.name)
            + " (Id: " + role.roleId + ")";
        webPage.addContentHead(head);

        Table tbl = new Table("roleDetails", "TblObjectList");
        tbl.addColumn(30, "Description");
        tbl.addColumn(70, "Value");
        tbl.addRow(new TableRow().addCellList("Application", applicationCell(role, permIdx, page.rootLevel())));
        tbl.addRow(new TableRow().addCellList("Test Group", groupCell(role.groupsTest, page.rootLevel())));
        tbl.addRow(new TableRow().addCellList("Production Group", groupCell(role.groupsProd, page.rootLevel())));
        tbl.addRow(new TableRow().addCellList("Permissions", permissionsSummary(role, permIdx, page.rootLevel())));
        webPage.addContent(tbl.toXHtml());
        webPage.addContent(ServerObjectHistoryWidget.render(role.owner, role.modifiedBy, role.modified, knownUserNames, page.rootLevel()));

        webPage.saveInFolder(page.path());

        int rid = role.roleId;
        formList(role, visibleForms(role, permIdx), subadminForms(role, permIdx));
        fieldList(role, permIdx.fields(rid));
        activeLinkList(role, activeLinks(role, permIdx), permIdx);
        containerList(role, Naming.roleContainerList(role.requestId, 3), "Packing List Permission", containers(role, permIdx, 3), subadminContainers(role, permIdx), permIdx);
        containerList(role, Naming.roleContainerList(role.requestId, 1), "Active Link Guide Permission", containers(role, permIdx, 1), List.of(), permIdx);
        containerList(role, Naming.roleContainerList(role.requestId, 5), "Webservice Permission", containers(role, permIdx, 5), List.of(), permIdx);
    }

    /**
     * Java port of DocRoleDetails.cpp's app-scoping check (FormsDoc/AlPermissionDoc/
     * ContainerPermissionDoc all gate on "schema.GetAppRefName() == pRole->GetApplicationName()"
     * before matching a role ID against a permission list) - a role only "sees" objects belonging
     * to its own application. The group-side equivalents in DocGroupDetails.cpp have no such
     * filter, so this is applied here (RoleDetailPage) only, not in PermissionIndex's shared
     * group/role accumulation. Both-empty (unassigned object, role with no application) counts as
     * a match, matching the C++'s plain string equality on two possibly-empty strings.
     */
    private static boolean appMatches(String objectApp, String roleApp) {
        return (objectApp == null ? "" : objectApp).equals(roleApp == null ? "" : roleApp);
    }

    private List<PermissionIndex.FormEntry> visibleForms(RoleRecord role, PermissionIndex permIdx) {
        return permIdx.visibleForms(role.roleId).stream().filter(f -> appMatches(permIdx.formApp(f.name()), role.applicationName)).toList();
    }

    private List<String> subadminForms(RoleRecord role, PermissionIndex permIdx) {
        return permIdx.subadminForms(role.roleId).stream().filter(name -> appMatches(permIdx.formApp(name), role.applicationName)).toList();
    }

    private List<String> activeLinks(RoleRecord role, PermissionIndex permIdx) {
        return permIdx.activeLinks(role.roleId).stream().filter(name -> appMatches(permIdx.alApp(name), role.applicationName)).toList();
    }

    private List<PermissionIndex.ContainerEntry> containers(RoleRecord role, PermissionIndex permIdx, int containerType) {
        return permIdx.containers(role.roleId, containerType).stream()
            .filter(c -> appMatches(permIdx.containerApp(containerType, c.name()), role.applicationName)).toList();
    }

    private List<String> subadminContainers(RoleRecord role, PermissionIndex permIdx) {
        return permIdx.containerSubadmin(role.roleId).stream()
            .filter(name -> appMatches(permIdx.containerApp(Constants.ARCON_PACK, name), role.applicationName)).toList();
    }

    private String applicationCell(RoleRecord role, PermissionIndex permIdx, int rootLevel) {
        if (role.applicationName == null || role.applicationName.isEmpty()) return "";
        boolean isOverlaid = permIdx.isOverlaidContainer(Constants.ARCON_APP, role.applicationName);
        return URLLink.to(role.applicationName, Naming.containerDetail(Constants.ARCON_APP, role.applicationName, isOverlaid), ImageTag.Id.Application, rootLevel).toHtml();
    }

    /** Java port of DocRoleDetails.cpp's Test/Production Group rows - defaults to group id 0 ("Public") when the role has no test/production group configured at all, matching the C++'s own "int testId = 0;"/"int prodId = 0;" default exactly. */
    private String groupCell(List<Integer> groupIds, int rootLevel) {
        int groupId = groupIds.isEmpty() ? 0 : groupIds.get(0);
        String name = groupNamesById.get(groupId);
        if (name == null) return Integer.toString(groupId); // no known group record for this ID - matches CheckedURLLink's plain-text fallback
        return URLLink.to(name, Naming.groupDetail(groupId), ImageTag.Id.Group, rootLevel).toHtml();
    }

    private String permissionsSummary(RoleRecord role, PermissionIndex permIdx, int rootLevel) {
        int rid = role.roleId;
        Table tbl = new Table("permissionList", "TblNoSort");
        tbl.addColumn(100, "Permission Details");
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Form Permission", "No Form Access", visibleForms(role, permIdx).size(), Naming.roleFormList(role.requestId), rootLevel)));
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Field Permission", "No Field Permission", permIdx.fields(rid).values().stream().mapToInt(List::size).sum(), Naming.roleFieldList(role.requestId), rootLevel)));
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Active Link Permission", "No Active Link Permission", activeLinks(role, permIdx).size(), Naming.roleActiveLinkList(role.requestId), rootLevel)));
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Packing List Permission", "No Packing List Permission", containers(role, permIdx, 3).size(), Naming.roleContainerList(role.requestId, 3), rootLevel)));
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Active Link Guide Permission", "No Active Link Guide Permission", containers(role, permIdx, 1).size(), Naming.roleContainerList(role.requestId, 1), rootLevel)));
        tbl.addRow(new TableRow().addCell(GroupDetailPage.summaryLine("Webservice Permission", "No Webservice Permission", containers(role, permIdx, 5).size(), Naming.roleContainerList(role.requestId, 5), rootLevel)));
        return tbl.toXHtml();
    }

    private void formList(RoleRecord role, List<PermissionIndex.FormEntry> visible, List<String> subadmin) {
        PagePath page = Naming.roleFormList(role.requestId);
        WebPage webPage = new WebPage(page.fileName(), "Form Permission " + role.name, page.rootLevel(), appConfig);
        webPage.addContentHead(GroupDetailPage.companionHead("Roles", Naming.roleOverview(), Naming.roleDetail(role.requestId), role.name, ImageTag.Id.Role, "Form Permission", page.rootLevel()));

        Table tbl = new Table("schemaList", "TblObjectList");
        tbl.description = "Form Permission";
        tbl.addColumn(100, "Form Name");
        for (PermissionIndex.FormEntry f : visible) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(f.name(), Naming.schemaDetail(f.name(), globalFields.isOverlaid(f.name())), ImageTag.Id.Schema, page.rootLevel()).toHtml()));
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

    private void fieldList(RoleRecord role, Map<String, List<PermissionIndex.FieldEntry>> byForm) {
        PagePath page = Naming.roleFieldList(role.requestId);
        WebPage webPage = new WebPage(page.fileName(), "Field Permission " + role.name, page.rootLevel(), appConfig);
        webPage.addContentHead(GroupDetailPage.companionHead("Roles", Naming.roleOverview(), Naming.roleDetail(role.requestId), role.name, ImageTag.Id.Role, "Field Permission", page.rootLevel()));

        for (var entry : byForm.entrySet()) {
            Table tbl = new Table("fieldList", "TblObjectList");
            tbl.description = URLLink.to(entry.getKey(), Naming.schemaDetail(entry.getKey(), globalFields.isOverlaid(entry.getKey())), ImageTag.Id.Schema, page.rootLevel()).toHtml();
            tbl.addColumn(70, "Field");
            tbl.addColumn(30, "Permission");
            for (PermissionIndex.FieldEntry f : entry.getValue()) {
                tbl.addRow(new TableRow().addCellList(f.fieldName(), GroupDetailPage.fieldPermissionLabel(f.permissionValue())));
            }
            webPage.addContent(tbl.toXHtml());
        }

        webPage.saveInFolder(page.path());
    }

    private void activeLinkList(RoleRecord role, List<String> names, PermissionIndex permIdx) {
        PagePath page = Naming.roleActiveLinkList(role.requestId);
        WebPage webPage = new WebPage(page.fileName(), "Active Link Permission " + role.name, page.rootLevel(), appConfig);
        webPage.addContentHead(GroupDetailPage.companionHead("Roles", Naming.roleOverview(), Naming.roleDetail(role.requestId), role.name, ImageTag.Id.Role, "Active Link Permission", page.rootLevel()));

        Table tbl = new Table("alList", "TblObjectList");
        tbl.addColumn(100, "Active Link");
        for (String name : names) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(name, Naming.activeLinkDetail(name, permIdx.isOverlaidActiveLink(name)), ImageTag.Id.ActiveLink, page.rootLevel()).toHtml()));
        }
        webPage.addContent(tbl.toXHtml());

        webPage.saveInFolder(page.path());
    }

    private void containerList(RoleRecord role, PagePath page, String title, List<PermissionIndex.ContainerEntry> entries, List<String> subadmin, PermissionIndex permIdx) {
        WebPage webPage = new WebPage(page.fileName(), title + " " + role.name, page.rootLevel(), appConfig);
        webPage.addContentHead(GroupDetailPage.companionHead("Roles", Naming.roleOverview(), Naming.roleDetail(role.requestId), role.name, ImageTag.Id.Role, title, page.rootLevel()));

        Table tbl = new Table("containerList", "TblObjectList");
        tbl.description = "Container Permission";
        tbl.addColumn(100, "Container");
        for (PermissionIndex.ContainerEntry c : entries) {
            tbl.addRow(new TableRow().addCellList(URLLink.to(c.name(), Naming.containerDetail(c.containerType(), c.name(), permIdx.isOverlaidContainer(c.containerType(), c.name())), GroupDetailPage.containerIcon(c.containerType()), page.rootLevel()).toHtml()));
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
}
