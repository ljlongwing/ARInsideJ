package arinside.ar;

import arinside.ar.xmlfile.ParsedObjects;
import com.bmc.arsys.api.ActiveLink;
import com.bmc.arsys.api.Escalation;
import com.bmc.arsys.api.Filter;
import com.bmc.arsys.api.Menu;
import com.bmc.arsys.api.MenuItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WorkflowSource backed by a genuinely offline parse of an AR System Administrator .xml export -
 * see XmlFileSchemaRepository's javadoc for the live-connection contrast with .def-format file
 * mode. Menu objects are built with their real typed subtype (QueryMenu/SqlMenu/ListMenu/FileMenu/
 * DataDictionaryMenu, see MenuXmlBuilder) directly from the export's own definition - no live
 * "expand" call needed or available here, matching how MenuDetailPage now renders every menu type
 * (see its javadoc).
 */
public final class XmlFileWorkflowRepository implements WorkflowSource {
    private final ParsedObjects parsed;

    public XmlFileWorkflowRepository(ParsedObjects parsed) {
        this.parsed = parsed;
    }

    @Override
    public List<String> listActiveLinkNames() {
        return sortedKeys(parsed.activeLinks.keySet());
    }

    @Override
    public ActiveLink getActiveLink(String name) {
        return parsed.activeLinks.get(name);
    }

    @Override
    public List<String> listFilterNames() {
        return sortedKeys(parsed.filters.keySet());
    }

    @Override
    public Filter getFilter(String name) {
        return parsed.filters.get(name);
    }

    @Override
    public List<String> listEscalationNames() {
        return sortedKeys(parsed.escalations.keySet());
    }

    @Override
    public Escalation getEscalation(String name) {
        return parsed.escalations.get(name);
    }

    @Override
    public List<String> listMenuNames() {
        return sortedKeys(parsed.menus.keySet());
    }

    @Override
    public Menu getMenu(String name) {
        return parsed.menus.get(name);
    }

    private static List<String> sortedKeys(java.util.Set<String> keys) {
        List<String> names = new ArrayList<>(keys);
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
