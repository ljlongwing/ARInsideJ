package arinside.ar;

import com.bmc.arsys.api.Timestamp;

import java.util.List;

/**
 * Java port of a CARUser row (lists/ARUserList.cpp) - loaded via RawEntryQuery against the "User"
 * admin form. Replaces the earlier getListUser()-based UserInfo, which doesn't carry Full Name or
 * Group List at all (see IdentityRepository's prior javadoc).
 */
public final class UserRecord {
    public final String loginName;
    public final String email;
    public final List<Integer> groupIds;
    public final String fullName;
    public final int defaultNotify;
    public final int licenseType;
    public final int ftLicenseType;
    public final String owner;
    public final Timestamp created;
    public final String modifiedBy;
    public final Timestamp modified;

    public UserRecord(String loginName, String email, List<Integer> groupIds, String fullName,
                       int defaultNotify, int licenseType, int ftLicenseType,
                       String owner, Timestamp created, String modifiedBy, Timestamp modified) {
        this.loginName = loginName;
        this.email = email;
        this.groupIds = groupIds;
        this.fullName = fullName;
        this.defaultNotify = defaultNotify;
        this.licenseType = licenseType;
        this.ftLicenseType = ftLicenseType;
        this.owner = owner;
        this.created = created;
        this.modifiedBy = modifiedBy;
        this.modified = modified;
    }
}
