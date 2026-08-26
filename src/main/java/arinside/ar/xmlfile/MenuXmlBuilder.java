package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds {@link Menu} (dispatching on the xsi:type attribute to the matching concrete subtype -
 * characterMenu/queryMenu/sqlMenu/fileMenu/dataDictionaryMenu, vocabulary confirmed against real
 * export samples of each, see ArsXmlFileParser's javadoc) from a &lt;menu&gt; top-level element.
 */
final class MenuXmlBuilder {
    private MenuXmlBuilder() {}

    /** c positioned at the &lt;menu&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Menu build(XmlCursor c) throws XMLStreamException {
        String xsiType = c.xsiType();
        String name = null, owner = null, lastModifiedBy = null, helpText = null, modifiedDate = null;
        ObjectPropertyMap props = null;
        int refreshCode = 0;
        MenuBody body = new MenuBody();

        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "menuName" -> name = c.elementText();
                case "owner" -> owner = c.elementText();
                case "lastModifiedBy" -> lastModifiedBy = c.elementText();
                case "modifiedDate" -> modifiedDate = c.elementText();
                case "helpText" -> helpText = c.elementText();
                case "objectProperties" -> props = PropertyMapXmlBuilder.build(c, new ObjectPropertyMap());
                case "menuRefresh" -> refreshCode = readRefreshCode(c);
                case "characterMenu" -> body.items = readMenuItems(c);
                case "serverName" -> body.server = c.elementText();
                case "formName" -> body.form = c.elementText();
                case "menuLabelFieldIDLv1" -> body.labelField = c.intText();
                case "menuValueFieldID" -> body.valueField = c.intText();
                case "sortOnLabel" -> body.sortOnLabel = Boolean.parseBoolean(c.elementText());
                case "qualifier" -> body.qualifier = QualifierXmlBuilder.build(c);
                case "sqlCommand" -> body.sqlCommand = c.elementText();
                case "menuLabelDBColumnIndexLv1" -> body.labelField = c.intText();
                case "menuValueDBColIndex" -> body.valueField = c.intText();
                case "fileName" -> { body.fileLocation = "server".equals(c.attr("fileLocation")) ? 1 : 0; body.fileName = c.elementText(); }
                case "nameType" -> body.nameType = "localName".equals(c.elementText()) ? 1 : 0;
                case "valueFormat" -> body.valueFormat = c.elementText();
                case "licenseItem" -> {
                    body.ddictKind = "license";
                    while (c.nextTag() == START_ELEMENT) {
                        if ("licenseType".equals(c.localName())) body.licenseType = c.intText();
                        else c.skipSubtree();
                    }
                }
                case "fieldItem" -> {
                    body.ddictKind = "field";
                    while (c.nextTag() == START_ELEMENT) {
                        switch (c.localName()) {
                            case "fieldType" -> body.fieldType = c.intTextOrDefault(0, "dataDictionaryMenu fieldItem/fieldType");
                            case "form" -> body.form = c.elementText();
                            default -> c.skipSubtree();
                        }
                    }
                }
                case "formItem" -> {
                    body.ddictKind = "form";
                    while (c.nextTag() == START_ELEMENT) {
                        switch (c.localName()) {
                            case "formType" -> body.formType = c.intTextOrDefault(0, "dataDictionaryMenu formItem/formType");
                            case "includeHidden" -> body.includeHidden = Boolean.parseBoolean(c.elementText());
                            default -> c.skipSubtree();
                        }
                    }
                }
                default -> c.skipSubtree();
            }
        }

        Menu menu = switch (xsiType == null ? "characterMenu" : xsiType) {
            case "queryMenu" -> new QueryMenu(body.server, body.form, body.qualifier,
                body.labelField >= 0 ? List.of(body.labelField) : List.of(), body.valueField,
                body.sortOnLabel, "", "");
            case "sqlMenu" -> new SqlMenu(body.server, body.sqlCommand,
                body.labelField >= 0 ? List.of(body.labelField) : List.of(), body.valueField);
            case "fileMenu" -> new FileMenu(body.fileLocation, body.fileName);
            case "dataDictionaryMenu" -> buildDataDictionaryMenu(body);
            case "characterMenu" -> new ListMenu(body.items != null ? body.items : List.of());
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized menu xsi:type '" + xsiType + "', defaulting to characterMenu");
                yield new ListMenu(body.items != null ? body.items : List.of());
            }
        };

        if (name != null) menu.setName(name);
        if (owner != null) menu.setOwner(owner);
        if (lastModifiedBy != null) menu.setLastChangedBy(lastModifiedBy);
        arinside.ar.ObjectTimestamp.set(menu, XmlTimestamp.parse(modifiedDate));
        if (helpText != null) menu.setHelpText(helpText);
        if (props != null) menu.setProperties(props);
        menu.setRefreshCode(refreshCode);
        return menu;
    }

    private static Menu buildDataDictionaryMenu(MenuBody body) {
        return switch (body.ddictKind == null ? "" : body.ddictKind) {
            case "license" -> new LicenseDataDictionaryMenu(body.server, body.nameType, 0, body.licenseType);
            case "field" -> new FieldDataDictionaryMenu(body.server, body.nameType, 0, body.fieldType, body.form);
            case "form" -> new FormDataDictionaryMenu(body.server, body.nameType, 0, body.formType, body.includeHidden);
            default -> {
                System.out.println("[WARN] xmlfile: dataDictionaryMenu with no recognized item type, defaulting to license");
                yield new LicenseDataDictionaryMenu(body.server, body.nameType, 0, 0);
            }
        };
    }

    private static int readRefreshCode(XmlCursor c) throws XMLStreamException {
        int code = 0;
        while (c.nextTag() == START_ELEMENT) {
            if ("refreshType".equals(c.localName())) {
                code = switch (c.elementText()) {
                    case "onFormConnect" -> Constants.AR_MENU_REFRESH_CONNECT;
                    case "onFormOpen" -> Constants.AR_MENU_REFRESH_OPEN;
                    case "onInterval" -> Constants.AR_MENU_REFRESH_INTERVAL;
                    default -> 0;
                };
            } else {
                c.skipSubtree();
            }
        }
        return code;
    }

    private static List<MenuItem> readMenuItems(XmlCursor c) throws XMLStreamException {
        List<MenuItem> items = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"menuItem".equals(c.localName())) { c.skipSubtree(); continue; }
            String label = null, value = null;
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "itemLabel" -> label = c.elementText();
                    case "itemValue" -> value = c.elementText();
                    default -> c.skipSubtree();
                }
            }
            items.add(new MenuItem(label != null ? label : "", value != null ? value : ""));
        }
        return items;
    }

    /** Scratch accumulator for the union of fields every menu xsi:type variant might set, since the concrete Menu subtype can't be chosen until the whole element (and its xsi:type attribute) has been seen. */
    private static final class MenuBody {
        String server = "@", form = "", sqlCommand = "", fileName = "";
        int labelField = -1, valueField = 0, fileLocation = 0, nameType = 0, licenseType = 0, fieldType = 0, formType = 0;
        boolean sortOnLabel = false, includeHidden = false;
        QualifierInfo qualifier;
        List<MenuItem> items;
        String valueFormat, ddictKind;
    }
}
