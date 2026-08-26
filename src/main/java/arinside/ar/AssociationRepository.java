package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Association;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Live-server AssociationSource - see its javadoc for why this is the only implementation (new functionality, no file-mode equivalent yet). */
public final class AssociationRepository implements AssociationSource {
    private final ArClient client;

    public AssociationRepository(ArClient client) {
        this.client = client;
    }

    @Override
    public List<String> listAssociationNames() throws ARException {
        // (formName, changedSince, propList, firstRetrieve, maxRetrieve, sortOrder) all null/0
        // lists every association server-wide, matching every other getListX-with-a-changedSince
        // call in this jar.
        List<String> names = client.raw().getListAssociation(null, 0L, null, 0, 0, 0);
        List<String> sorted = names == null ? new ArrayList<>() : new ArrayList<>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    @Override
    public Association getAssociation(String name) throws ARException {
        return client.raw().getAssociation(name);
    }
}
