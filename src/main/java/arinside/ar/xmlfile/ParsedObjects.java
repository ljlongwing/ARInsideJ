package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory result of parsing an AR System Administrator .xml export - one entry per named object, last-one-wins on a duplicate name (matches how the live API's own name-keyed lookups behave). */
public final class ParsedObjects {
    public final Map<String, Form> forms = new LinkedHashMap<>();
    public final Map<String, List<Field>> fieldsByForm = new LinkedHashMap<>();
    public final Map<String, List<View>> viewsByForm = new LinkedHashMap<>();
    public final Map<String, ActiveLink> activeLinks = new LinkedHashMap<>();
    public final Map<String, Filter> filters = new LinkedHashMap<>();
    public final Map<String, Escalation> escalations = new LinkedHashMap<>();
    public final Map<String, Menu> menus = new LinkedHashMap<>();
    public final Map<String, Container> containers = new LinkedHashMap<>();
    public final Map<String, Image> images = new LinkedHashMap<>();
}
