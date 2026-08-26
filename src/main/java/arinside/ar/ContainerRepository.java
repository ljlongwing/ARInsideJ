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
     * The 3rd param is undocumented in the AR System Java API (no javadoc, no parameter names in
     * bytecode); passing {@code true} is required to get the full container list, matching the
     * original tool's behavior - {@code false} silently omits containers this session doesn't
     * directly own or isn't a subadmin for.
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
