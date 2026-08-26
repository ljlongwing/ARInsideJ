package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.Association;

import java.util.List;

/**
 * Shape AssociationOverviewPage/AssociationDetailPage need from "wherever associations come from".
 * Association documentation is new functionality with no C++ ARInside equivalent, so unlike every
 * other *Source interface in this package there's no prior file naming/behavior to port against,
 * and (for now) no file-mode/XML equivalent either - the AR System Administrator .xml export format
 * this port parses doesn't serialize associations as a top-level object type the way it does
 * forms/workflow/containers. Live server only.
 */
public interface AssociationSource {
    List<String> listAssociationNames() throws ARException;
    Association getAssociation(String name) throws ARException;
}
