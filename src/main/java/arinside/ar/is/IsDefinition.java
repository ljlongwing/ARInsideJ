package arinside.ar.is;

import java.util.Map;

/**
 * One Innovation Studio definition (rule / process / web API / association / ...). Common
 * envelope fields are lifted out; {@link #raw} holds the full parsed JSON for the type-specific
 * renderers in {@code doc/is}.
 *
 * @param enabled null for types with no enable concept (documents, web APIs, ...)
 * @param modifiedEpoch epoch seconds, or null if the timestamp couldn't be parsed
 */
public record IsDefinition(
        IsDefType type,
        String name,
        String description,
        Boolean enabled,
        String modifiedBy,
        Long modifiedEpoch,
        String owner,
        String overlayGroupId,
        String scope,
        String guid,
        Map<String, Object> raw) {

    /** overlayGroupId "0" is the base layer; anything else is an overlay/custom layer. */
    public boolean isOverlay() {
        return overlayGroupId != null && !overlayGroupId.isEmpty() && !"0".equals(overlayGroupId);
    }
}
