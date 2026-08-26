package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code ContainerParseEventHandler} + {@code ReferenceParseEventHandler},
 * targeting {@code com.bmc.arsys.api.Container} subtypes directly - the exact shapes {@code
 * ContainerXmlBuilder} already builds for XML mode, used as the client-API reference.
 *
 * <p>The real handler starts every container as a generic {@code ApplicationContainerImpl}
 * (matching {@code beginContainerParsing}), then swaps to the real subtype once the {@code type:}
 * tag arrives, copying every field seen so far across (a container's {@code type:} tag is not
 * guaranteed to be the first tag, confirmed by reading {@code setContainerType}'s explicit
 * field-by-field copy) - this port defers subtype selection to {@link #build()} instead (buffering
 * plain fields, then constructing the one real subtype once at the end), simpler than replaying a
 * live copy since nothing here is emitted incrementally to a listener.
 *
 * <p>{@code type: 5} in real data ("Roles", carrying {@code &lt;portProperties&gt;}/operation-mapping
 * reference blobs) confirmed {@link ContainerType#WEBSERVICE} per the domain enum ordering (ACTIVELINK_GUIDE=1/APPLICATION=2/PACKINGLIST=3/FILTER_GUIDE=4/WEBSERVICE=5); the constant names alone don't reveal the raw int ordering.
 */
final class DefContainerBuilder {
    private int type = 2; // ApplicationContainer - matches beginContainerParsing's default before a type: tag arrives
    private String name, owner, lastChangedBy, label, description;
    private long timestamp;
    private ObjectPropertyMap properties;
    private List<PermissionInfo> permissions;
    private List<Integer> adminGroupIds;
    private final List<ContainerOwner> owners = new ArrayList<>();
    private final List<Reference> references = new ArrayList<>();

    private boolean inReference;
    private String refLabel, refDescription, refValueText;
    private int refType;

    Container build() {
        if (name == null || name.isEmpty()) return null;
        Container c = switch (type) {
            case 1 -> new ActiveLinkGuide();
            case 3 -> new PackingList();
            case 4 -> new FilterGuide();
            case 5 -> new WebService();
            default -> new ApplicationContainer();
        };
        c.setName(name);
        c.setOwner(owner);
        c.setLastChangedBy(lastChangedBy);
        c.setLabel(label);
        c.setDescription(description);
        if (properties != null) c.setProperties(properties);
        if (permissions != null) c.setPermissions(permissions);
        if (adminGroupIds != null) c.setAdminGroupList(adminGroupIds);
        c.setContainerOwner(owners);
        c.setReferences(references);
        arinside.ar.ObjectTimestamp.set(c, timestamp);
        return c;
    }

    void item(DefItemLabel item, String raw, Charset charset) {
        if (inReference) { referenceItem(item, raw, charset); return; }
        switch (item) {
            case NAME -> name = raw;
            case TYPE -> type = ParseUtil.intValue(raw);
            case OWNER -> owner = raw;
            case LAST_CHANGED -> lastChangedBy = raw;
            case LABEL -> label = raw;
            case DESCRIPTION -> description = raw;
            case OBJECT_PROP -> properties = DefPropertyDecoder.decode(raw, charset, properties != null ? properties : new ObjectPropertyMap());
            case PERMISSION, ADD_PERMISSION -> {
                int sep = raw.indexOf('\\');
                if (sep > 0) {
                    if (permissions == null) permissions = new ArrayList<>();
                    permissions.add(new PermissionInfo(ParseUtil.intValue(raw.substring(0, sep)), ParseUtil.intValue(raw.substring(sep + 1))));
                }
            }
            case CONTAINER_SUBADM, ADD_CTNR_SUBADM -> {
                if (adminGroupIds == null) adminGroupIds = new ArrayList<>();
                for (String tok : raw.trim().split(" ")) if (!tok.isBlank()) adminGroupIds.add(ParseUtil.intValue(tok));
            }
            case CONTAINER_OWNER, ADD_CTNR_OWNER -> decodeOwners(raw, charset);
            case TIMESTAMP -> timestamp = ParseUtil.longValue(raw);
            default -> { /* HELP/CHANGE_DIARY/REFERENCE_GROUPS/CORE_VERS/EXPORT_VERS/NUM_REFERENCES - not rendered or no client setter */ }
        }
    }

    /**
     * {@code owning-obj}/{@code add-owning-obj}: {@code count\(ownerType\len\value\)*} - Java port of
     * {@code DefParserHelper.loadContainerOwners}. Real data only ever carries ownerType 2 (SCHEMA -
     * confirmed by the real handler itself throwing on anything else), so only that type is kept,
     * matching {@code ContainerXmlBuilder}'s own "form" mapping to {@link ContainerOwner#SCHEMA}.
     */
    private void decodeOwners(String raw, Charset charset) {
        DefValueDecoder d = new DefValueDecoder(raw, charset);
        int count = d.readInt();
        for (int i = 0; i < count && !d.isEmpty(); i++) {
            int ownerType = d.readInt();
            int len = d.readInt();
            String value = d.readString(len);
            if (ownerType == ContainerOwner.SCHEMA) owners.add(new ContainerOwner(ContainerOwner.SCHEMA, value));
        }
    }

    void beginReference() {
        inReference = true;
        refLabel = null;
        refDescription = null;
        refValueText = null;
        refType = 0;
    }

    void endReference() {
        inReference = false;
        if (refValueText != null) {
            Reference r = new Reference();
            r.setLabel(refLabel);
            r.setDescription(refDescription);
            r.setReferenceType(ReferenceType.toReferenceType(refType));
            r.setName(refValueText);
            references.add(r);
        }
    }

    private void referenceItem(DefItemLabel item, String raw, Charset charset) {
        switch (item) {
            case LABEL -> refLabel = raw;
            case DESCRIPTION -> refDescription = raw;
            case TYPE -> refType = ParseUtil.intValue(raw);
            case OBJECT -> refValueText = raw;
            case VALUE -> {
                Value v = new DefValueDecoder(raw, charset).decodeValue();
                if (v != null) refValueText = v.getValue() != null ? v.getValue().toString() : "";
            }
            default -> { /* DATA_TYPE/REFERENCE_GROUPS - no client Reference setter for either */ }
        }
    }
}
