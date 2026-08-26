package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Association;

import java.util.List;

/**
 * Shape AssociationOverviewPage/AssociationDetailPage need from "wherever associations come from".
 * Genuinely new (post-C++) functionality - the original ARInside never documented Association
 * objects at all (confirmed via a full source-tree search, not assumed), so unlike every other
 * *Source interface in this package there's no C++ file naming/behavior to port against, and (for
 * now) no file-mode/XML equivalent either - the real .xml export format this port parses doesn't
 * serialize associations as a top-level object type the way it does forms/workflow/containers.
 * Live server only.
 */
public interface AssociationSource {
    List<String> listAssociationNames() throws ARException;
    Association getAssociation(String name) throws ARException;
}
