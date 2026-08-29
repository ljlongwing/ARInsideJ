package arinside.scan;

import arinside.output.ImageTag;
import arinside.output.PagePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java port of the image-reference half of CARImage::AddReference (core/ARImage.cpp) - "which
 * workflow object actually uses this image", sourced from doc/DocVUIDetails.cpp's per-field/
 * per-VUI AR_DPROP_IMAGE/AR_DPROP_PUSH_BUTTON_IMAGE/AR_DPROP_DETAIL_PANE_IMAGE/
 * AR_DPROP_TITLE_BAR_ICON_IMAGE display-property scan and doc/DocPacklistDetails.cpp's container
 * member-reference scan (ARREF_IMAGE). Populated as a side effect of VuiDetailPage/ContainerDetailPage
 * rendering (both run before ImageDetailPage in Main.java's pipeline order - see FieldReferenceIndex's
 * javadoc for the same "documented after its dependents" pattern) rather than a dedicated fetch pass.
 *
 * Thread-safe: {@link #add} runs concurrently once VUI/container rendering is on the parallel write
 * pool - ConcurrentHashMap + a synchronized list per bucket, same shape as FieldReferenceIndex.
 */
public final class ImageReferenceIndex {

    public record Ref(String name, String typeLabel, ImageTag.Id icon, PagePath link, String detail) {}

    private final Map<String, List<Ref>> byImageName = new ConcurrentHashMap<>();

    public void add(String imageName, Ref ref) {
        if (imageName == null || imageName.isEmpty()) return;
        byImageName.computeIfAbsent(imageName, k -> Collections.synchronizedList(new ArrayList<>())).add(ref);
    }

    /** Snapshot copy (same reasoning as {@link FieldReferenceIndex#forField}): {@link #add} runs on the parallel write pool. */
    public List<Ref> forImage(String imageName) {
        List<Ref> live = byImageName.get(imageName);
        if (live == null) return List.of();
        synchronized (live) {
            return new ArrayList<>(live);
        }
    }
}
