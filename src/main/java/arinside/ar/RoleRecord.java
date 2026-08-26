package arinside.ar;

import com.bmc.arsys.api.Timestamp;

import java.util.List;

/**
 * Java port of a CARRole row (lists/ARRoleList.cpp) - loaded via RawEntryQuery against the "Roles"
 * admin form, since roles aren't a real AR object type with a convenience API (see
 * IdentityRepository's javadoc). roleId is the raw AR_RESERV_ROLE_MAPPING_ROLE_ID field value -
 * negative, matching how role-based permissions are stored in ARPermissionList.groupId entries
 * (confirmed empirically: field 1702 came back as e.g. -4870051 on the live test server) - permission
 * matching just compares this raw value directly against a schema/field/AL/container's group IDs,
 * no sign conversion needed.
 */
public final class RoleRecord {
    public final String requestId;
    public final String applicationName;
    public final String name;
    public final int roleId;
    public final List<Integer> groupsTest;
    public final List<Integer> groupsProd;
    public final String owner;
    public final Timestamp created;
    public final String modifiedBy;
    public final Timestamp modified;

    public RoleRecord(String requestId, String applicationName, String name, int roleId,
                       List<Integer> groupsTest, List<Integer> groupsProd,
                       String owner, Timestamp created, String modifiedBy, Timestamp modified) {
        this.requestId = requestId;
        this.applicationName = applicationName;
        this.name = name;
        this.roleId = roleId;
        this.groupsTest = groupsTest;
        this.groupsProd = groupsProd;
        this.owner = owner;
        this.created = created;
        this.modifiedBy = modifiedBy;
        this.modified = modified;
    }
}
