package arinside.ar.is;

import java.util.Map;

/**
 * One Innovation Studio bundle (an application or a shared library). {@code isApplication}
 * separates the deployable apps from platform/library bundles.
 */
public record IsBundle(
        String id,
        String name,
        String friendlyName,
        String version,
        String developerId,
        String description,
        boolean isApplication,
        String lastDeployedTime,
        Map<String, Object> raw) {
}
