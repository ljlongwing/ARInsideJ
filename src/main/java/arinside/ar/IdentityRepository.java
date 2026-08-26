package arinside.ar;

import arinside.config.AppConfig;
import com.bmc.arsys.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of the user/group/role/image loading portions of CARInside::LoadServerObjects
 * (ARInside.cpp, lists/ARUserList.cpp / ARGroupList.cpp / ARRoleList.cpp / ARImageList.cpp).
 *
 * Users, groups and roles are all loaded via RawEntryQuery against a reserved admin form (User/
 * Group/Roles, configurable via AppConfig's userForm/groupForm/roleForm + matching *Query
 * settings) - this exactly matches what the C++ does (confirmed by reading all three lists/AR*List.cpp
 * files, 2026-08-15): none of them use the convenience getListUser/getListGroup/getListRole calls.
 * This was a real fidelity gap in the earlier version of this class (which used the convenience
 * calls and documented several resulting limitations - no Full Name/Group List for users, no
 * catalog listing at all for roles) - now fixed by using the same raw-query approach as the C++.
 *
 * Field IDs below (1700-1702, 101-121) are AR System reserved field IDs with no named constant in
 * this jar's Constants class (same situation as the role-mapping fields already noted) - sourced
 * directly from thirdparty/arapi/include/arstruct.h to guarantee exact parity with the C++.
 */
public final class IdentityRepository implements ImageSource {
    private static final int AR_CORE_SUBMITTER = 2;
    private static final int AR_CORE_CREATE_DATE = 3;
    private static final int AR_CORE_LAST_MODIFIED_BY = 5;
    private static final int AR_CORE_MODIFIED_DATE = 6;
    private static final int AR_CORE_SHORT_DESCRIPTION = 8;

    private static final int AR_RESERV_USER_NAME = 101;
    private static final int AR_RESERV_EMAIL = 103;
    private static final int AR_RESERV_GROUP_LIST = 104;
    private static final int AR_RESERV_GROUP_NAME = 105;
    private static final int AR_RESERV_GROUP_ID = 106;
    private static final int AR_RESERV_GROUP_TYPE = 107;
    private static final int AR_RESERV_USER_NOTIFY = 108;
    private static final int AR_RESERV_LICENSE_TYPE = 109;
    private static final int AR_RESERV_LIC_FULL_TEXT = 110;
    private static final int AR_RESERV_GROUP_CATEGORY = 120;
    private static final int AR_RESERV_COMPUTED_GROUP_QUAL = 121;

    private static final int AR_RESERV_ROLE_MAPPING_APPLICATION = 1700;
    private static final int AR_RESERV_ROLE_MAPPING_ROLE_NAME = 1701;
    private static final int AR_RESERV_ROLE_MAPPING_ROLE_ID = 1702;
    private static final int AR_RESERV_APP_STATE_TEST = 2001;
    private static final int AR_RESERV_APP_STATE_PRODUCTION = 2002;

    private final ArClient client;
    private final AppConfig appConfig;
    private final BlackList blackList;

    public IdentityRepository(ArClient client, AppConfig appConfig, BlackList blackList) {
        this.client = client;
        this.appConfig = appConfig;
        this.blackList = blackList;
    }

    public List<UserRecord> listUsers() throws ARException {
        int[] fieldIds = {AR_RESERV_USER_NAME, AR_RESERV_EMAIL, AR_RESERV_GROUP_LIST, AR_CORE_SHORT_DESCRIPTION,
            AR_RESERV_USER_NOTIFY, AR_RESERV_LICENSE_TYPE, AR_RESERV_LIC_FULL_TEXT,
            AR_CORE_SUBMITTER, AR_CORE_CREATE_DATE, AR_CORE_LAST_MODIFIED_BY, AR_CORE_MODIFIED_DATE};
        List<Entry> entries = RawEntryQuery.query(client, appConfig.userForm, appConfig.userQuery, fieldIds);

        List<UserRecord> users = new ArrayList<>();
        for (Entry e : entries) {
            String loginName = RawEntryQuery.str(e, AR_RESERV_USER_NAME);
            if (loginName.isEmpty()) continue;
            users.add(new UserRecord(
                loginName,
                RawEntryQuery.str(e, AR_RESERV_EMAIL),
                RawEntryQuery.intList(e, AR_RESERV_GROUP_LIST),
                RawEntryQuery.str(e, AR_CORE_SHORT_DESCRIPTION),
                RawEntryQuery.intVal(e, AR_RESERV_USER_NOTIFY),
                RawEntryQuery.intVal(e, AR_RESERV_LICENSE_TYPE),
                RawEntryQuery.intVal(e, AR_RESERV_LIC_FULL_TEXT),
                RawEntryQuery.str(e, AR_CORE_SUBMITTER),
                RawEntryQuery.timestamp(e, AR_CORE_CREATE_DATE),
                RawEntryQuery.str(e, AR_CORE_LAST_MODIFIED_BY),
                RawEntryQuery.timestamp(e, AR_CORE_MODIFIED_DATE)));
        }
        users.sort((a, b) -> a.loginName.compareToIgnoreCase(b.loginName));
        return users;
    }

    public List<GroupRecord> listGroups() throws ARException {
        int[] fieldIds = {AR_RESERV_GROUP_NAME, AR_RESERV_GROUP_ID, AR_RESERV_GROUP_TYPE, AR_CORE_SHORT_DESCRIPTION,
            AR_CORE_SUBMITTER, AR_CORE_CREATE_DATE, AR_CORE_LAST_MODIFIED_BY, AR_CORE_MODIFIED_DATE, AR_RESERV_GROUP_CATEGORY,
            AR_RESERV_COMPUTED_GROUP_QUAL};
        List<Entry> entries = RawEntryQuery.query(client, appConfig.groupForm, appConfig.groupQuery, fieldIds);

        List<GroupRecord> groups = new ArrayList<>();
        for (Entry e : entries) {
            String name = RawEntryQuery.str(e, AR_RESERV_GROUP_NAME);
            if (name.isEmpty()) continue;
            groups.add(new GroupRecord(
                name,
                RawEntryQuery.intVal(e, AR_RESERV_GROUP_ID),
                RawEntryQuery.intVal(e, AR_RESERV_GROUP_TYPE),
                RawEntryQuery.str(e, AR_CORE_SHORT_DESCRIPTION),
                RawEntryQuery.intVal(e, AR_RESERV_GROUP_CATEGORY),
                RawEntryQuery.str(e, AR_CORE_SUBMITTER),
                RawEntryQuery.timestamp(e, AR_CORE_CREATE_DATE),
                RawEntryQuery.str(e, AR_CORE_LAST_MODIFIED_BY),
                RawEntryQuery.timestamp(e, AR_CORE_MODIFIED_DATE),
                RawEntryQuery.str(e, AR_RESERV_COMPUTED_GROUP_QUAL)));
        }
        groups.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return groups;
    }

    public List<RoleRecord> listRoles() throws ARException {
        int[] fieldIds = {AR_RESERV_ROLE_MAPPING_APPLICATION, AR_RESERV_ROLE_MAPPING_ROLE_NAME, AR_RESERV_ROLE_MAPPING_ROLE_ID,
            AR_RESERV_APP_STATE_TEST, AR_RESERV_APP_STATE_PRODUCTION,
            AR_CORE_SUBMITTER, AR_CORE_CREATE_DATE, AR_CORE_LAST_MODIFIED_BY, AR_CORE_MODIFIED_DATE};
        List<Entry> entries = RawEntryQuery.query(client, appConfig.roleForm, appConfig.roleQuery, fieldIds);

        List<RoleRecord> roles = new ArrayList<>();
        for (Entry e : entries) {
            String name = RawEntryQuery.str(e, AR_RESERV_ROLE_MAPPING_ROLE_NAME);
            if (name.isEmpty()) continue;
            roles.add(new RoleRecord(
                e.getEntryId(),
                RawEntryQuery.str(e, AR_RESERV_ROLE_MAPPING_APPLICATION),
                name,
                RawEntryQuery.intVal(e, AR_RESERV_ROLE_MAPPING_ROLE_ID),
                RawEntryQuery.intList(e, AR_RESERV_APP_STATE_TEST),
                RawEntryQuery.intList(e, AR_RESERV_APP_STATE_PRODUCTION),
                RawEntryQuery.str(e, AR_CORE_SUBMITTER),
                RawEntryQuery.timestamp(e, AR_CORE_CREATE_DATE),
                RawEntryQuery.str(e, AR_CORE_LAST_MODIFIED_BY),
                RawEntryQuery.timestamp(e, AR_CORE_MODIFIED_DATE)));
        }
        roles.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return roles;
    }

    public List<String> listImageNames() throws ARException {
        List<String> names = new ArrayList<>(client.raw().getListImage());
        names.removeIf(blackList::containsImage);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public Image getImage(String name) throws ARException {
        return client.raw().getImage(name);
    }
}
