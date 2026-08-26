package arinside.ar;

import com.bmc.arsys.api.Timestamp;

/**
 * Java port of a CARGroup row (lists/ARGroupList.cpp) - loaded via RawEntryQuery against the
 * "Group" admin form, matching the C++ (which also uses a raw entry query here, not the
 * convenience getListGroup call - confirmed by reading ARGroupList.cpp).
 */
public final class GroupRecord {
    public final String name;
    public final int groupId;
    public final int groupType;
    public final String longName;
    public final int category;
    public final String owner;
    public final Timestamp created;
    public final String modifiedBy;
    public final Timestamp modified;
    public final String computedQualification;

    public GroupRecord(String name, int groupId, int groupType, String longName, int category,
                        String owner, Timestamp created, String modifiedBy, Timestamp modified, String computedQualification) {
        this.name = name;
        this.groupId = groupId;
        this.groupType = groupType;
        this.longName = longName;
        this.category = category;
        this.owner = owner;
        this.created = created;
        this.modifiedBy = modifiedBy;
        this.modified = modified;
        this.computedQualification = computedQualification;
    }
}
