package arinside.ar;

import com.bmc.arsys.api.ARException;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.Menu;

import java.util.List;

/**
 * Shape every Doc*Page/scan class needs from "wherever active links/filters/escalations/menus
 * come from" - implemented by WorkflowRepository (live server) and FileModeWorkflowRepository (a
 * def-file export). See SchemaSource's javadoc for why this split exists.
 */
public interface WorkflowSource {
    List<String> listActiveLinkNames() throws ARException;
    ActiveLink getActiveLink(String name) throws ARException;

    List<String> listFilterNames() throws ARException;
    Filter getFilter(String name) throws ARException;

    List<String> listEscalationNames() throws ARException;
    Escalation getEscalation(String name) throws ARException;

    List<String> listMenuNames() throws ARException;
    Menu getMenu(String name) throws ARException;
}
