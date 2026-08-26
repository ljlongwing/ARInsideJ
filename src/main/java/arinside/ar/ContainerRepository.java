package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Container;
import com.bmc.arsys.api.ObjectPropertyMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java port of the container-loading portion of CARInside::LoadContainer (ARInside.cpp,
 * lists/ARContainerList.cpp). One repository, parameterized by ARCON_* type, covers all five
 * container subtypes (guide/application/packing list/filter guide/webservice) - matching how
 * CDocMain::ContainerList(nType, title) in the C++ already shares one code path per subtype.
 */
public final class ContainerRepository implements ContainerSource {
    private final ArClient client;
    private final BlackList blackList;

    public ContainerRepository(ArClient client, BlackList blackList) {
        this.client = client;
        this.blackList = blackList;
    }

    /**
     * The 3rd param (undocumented in this jar - no javadoc, no parameter names in bytecode)
     * confirmed empirically against the real C++ baseline count: false returned 37/101 real
     * applications on the test server, true returned all 101 (matching the C++ exactly). Guessing
     * this is something like "include containers this session doesn't directly own/isn't a
     * subadmin for" given the name pattern of ContainerOwner (the next param), but that's inferred
     * from the count-matching behavior, not confirmed from documentation.
     */
    public List<String> listContainerNames(int containerType) throws ARException {
        List<String> names = client.raw().getListContainer(0L, new int[]{containerType}, true, null, (ObjectPropertyMap) null);
        List<String> sorted = new ArrayList<>(names);
        sorted.removeIf(blackList::containsContainer);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    public Container getContainer(String name) throws ARException {
        return client.raw().getContainer(name);
    }
}
