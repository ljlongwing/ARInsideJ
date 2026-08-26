package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds {@link Form} (+ its {@link Field}/{@link View} children) from a &lt;form&gt; top-level
 * element, including its real {@link JoinForm}/{@link ViewForm} subtype from &lt;compoundForm&gt;
 * when present (confirmed against the real export: `&lt;compoundForm&gt;` wraps exactly one of
 * `&lt;regularForm/&gt;`/`&lt;joinForm&gt;`/`&lt;viewForm&gt;`/other rarer variants) - this is what
 * lets FieldDetailPage's join/view Field Mapping section (see its javadoc) work in file mode too,
 * not just live server mode. Dialog/vendor forms still fall back to plain {@link RegularForm} -
 * nothing in this port renders their extra detail either way, matching FieldDetailPage's own scope.
 * The common form-level properties are accumulated into locals first and the concrete Form
 * subtype only decided once the whole &lt;form&gt; element has been read, since &lt;compoundForm&gt;
 * isn't guaranteed to appear before every other child tag in the source.
 *
 * <p>{@code <modifiedDate>}'s last-update-time is set via {@link arinside.ar.ObjectTimestamp} - see
 * that class's javadoc for why reflection is needed (the field has no public setter anywhere in the
 * AR Java API's object model - this was previously left unreconstructed here for that reason,
 * before that helper existed).
 */
final class FormXmlBuilder {
    private FormXmlBuilder() {}

    record FormResult(Form form, List<Field> fields, List<View> views) {}

    /** c positioned at the &lt;form&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static FormResult build(XmlCursor c) throws XMLStreamException {
        String name = null, owner = null, lastModifiedBy = null, helpText = null, defaultVui = null, modifiedDate = null;
        ObjectPropertyMap properties = null;
        List<EntryListFieldInfo> entryListFields = null;
        List<IndexInfo> indexInfo = null;
        List<PermissionInfo> permissions = null;
        Form compound = null;
        List<Field> fields = new ArrayList<>();
        List<View> views = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "formName" -> name = c.elementText();
                case "owner" -> owner = c.elementText();
                case "lastModifiedBy" -> lastModifiedBy = c.elementText();
                case "modifiedDate" -> modifiedDate = c.elementText();
                case "helpText" -> helpText = c.elementText();
                case "objectProperties" -> properties = PropertyMapXmlBuilder.build(c, new ObjectPropertyMap());
                case "resultListFields" -> entryListFields = buildEntryListFields(c);
                case "formIndexList" -> indexInfo = buildIndexInfo(c);
                case "viewPermissionList" -> permissions = buildPermissions(c, true);
                case "defaultVui" -> defaultVui = c.elementText();
                case "fields" -> readFields(c, fields);
                case "views" -> readViews(c, views);
                case "compoundForm" -> compound = buildCompoundForm(c);
                default -> c.skipSubtree();
            }
        }

        Form form = compound != null ? compound : new RegularForm();
        if (name != null) form.setName(name);
        if (owner != null) form.setOwner(owner);
        if (lastModifiedBy != null) form.setLastChangedBy(lastModifiedBy);
        if (helpText != null) form.setHelpText(helpText);
        if (properties != null) form.setProperties(properties);
        if (entryListFields != null) form.setEntryListFieldInfo(entryListFields);
        if (indexInfo != null) form.setIndexInfo(indexInfo);
        if (permissions != null) form.setPermissions(permissions);
        if (defaultVui != null) form.setDefaultVUI(defaultVui);
        arinside.ar.ObjectTimestamp.set(form, XmlTimestamp.parse(modifiedDate));
        return new FormResult(form, fields, views);
    }

    /** c positioned at the &lt;compoundForm&gt; START_ELEMENT; leaves c at its END_ELEMENT. Returns null for dialog/vendor/anything else this port doesn't render extra detail for, letting the caller fall back to RegularForm. */
    private static Form buildCompoundForm(XmlCursor c) throws XMLStreamException {
        Form result = null;
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "regularForm" -> { c.skipSubtree(); if (result == null) result = new RegularForm(); }
                case "joinForm" -> result = buildJoinForm(c);
                case "viewForm" -> result = buildViewForm(c);
                default -> c.skipSubtree();
            }
        }
        return result;
    }

    /** c positioned at the &lt;joinForm&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    private static JoinForm buildJoinForm(XmlCursor c) throws XMLStreamException {
        JoinForm join = new JoinForm();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "primaryFormName" -> join.setMemberA(c.elementText());
                case "secondaryFormName" -> join.setMemberB(c.elementText());
                case "joinOption" -> join.setJoinOption("outer".equals(c.elementText()) ? Constants.AR_JOIN_OPTION_OUTER : Constants.AR_JOIN_OPTION_NONE);
                case "joinQualifier" -> join.setJoinQualification(QualifierXmlBuilder.build(c));
                default -> c.skipSubtree();
            }
        }
        return join;
    }

    /** c positioned at the &lt;viewForm&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    private static ViewForm buildViewForm(XmlCursor c) throws XMLStreamException {
        ViewForm view = new ViewForm();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "tableName" -> view.setTableName(c.elementText());
                case "keyFieldName" -> view.setKeyField(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return view;
    }

    private static List<EntryListFieldInfo> buildEntryListFields(XmlCursor c) throws XMLStreamException {
        List<EntryListFieldInfo> list = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"resultListField".equals(c.localName())) { c.skipSubtree(); continue; }
            EntryListFieldInfo info = new EntryListFieldInfo();
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "fieldID" -> info.setFieldId(c.intText());
                    case "fieldWidth" -> info.setColumnWidth(c.intText());
                    case "separator" -> info.setSeparator(c.elementText());
                    default -> c.skipSubtree();
                }
            }
            list.add(info);
        }
        return list;
    }

    private static List<IndexInfo> buildIndexInfo(XmlCursor c) throws XMLStreamException {
        List<IndexInfo> list = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"formIndex".equals(c.localName())) { c.skipSubtree(); continue; }
            IndexInfo info = new IndexInfo();
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "indexName" -> info.setIndexName(c.elementText());
                    case "uniqueIndex" -> info.setUnique(Boolean.parseBoolean(c.elementText()));
                    case "indexType" -> info.setIndexType(c.intText());
                    case "fieldIDList" -> info.setIndexFields(buildFieldIdList(c));
                    default -> c.skipSubtree();
                }
            }
            list.add(info);
        }
        return list;
    }

    private static List<Integer> buildFieldIdList(XmlCursor c) throws XMLStreamException {
        List<Integer> ids = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if ("fieldID".equals(c.localName())) ids.add(c.intText());
            else c.skipSubtree();
        }
        return ids;
    }

    /** viewPermissionList entries use &lt;viewRights&gt;; modifyPermissionList entries use &lt;modifyRights&gt;. */
    static List<PermissionInfo> buildPermissions(XmlCursor c, boolean isView) throws XMLStreamException {
        List<PermissionInfo> list = new ArrayList<>();
        String itemTag = isView ? "viewPermission" : "modifyPermission";
        String rightsTag = isView ? "viewRights" : "modifyRights";
        while (c.nextTag() == START_ELEMENT) {
            if (!itemTag.equals(c.localName())) { c.skipSubtree(); continue; }
            int groupId = 0, value = isView ? Constants.AR_PERMISSIONS_VISIBLE : Constants.AR_PERMISSIONS_VIEW;
            while (c.nextTag() == START_ELEMENT) {
                if ("groupID".equals(c.localName())) groupId = c.intText();
                else if (rightsTag.equals(c.localName())) value = permissionValue(c.elementText(), isView);
                else c.skipSubtree();
            }
            list.add(new PermissionInfo(groupId, value));
        }
        return list;
    }

    private static int permissionValue(String s, boolean isView) {
        if (isView) {
            return switch (s) {
                case "hidden" -> Constants.AR_PERMISSIONS_HIDDEN;
                default -> Constants.AR_PERMISSIONS_VISIBLE;
            };
        }
        return switch (s) {
            case "readWrite" -> Constants.AR_PERMISSIONS_CHANGE;
            case "hidden" -> Constants.AR_PERMISSIONS_NONE;
            default -> Constants.AR_PERMISSIONS_VIEW;
        };
    }

    private static void readFields(XmlCursor c, List<Field> out) throws XMLStreamException {
        while (c.nextTag() == START_ELEMENT) {
            if (!"field".equals(c.localName())) { c.skipSubtree(); continue; }
            int depthBefore = c.depth();
            try {
                Field f = buildField(c);
                if (f != null) out.add(f);
            } catch (Exception e) {
                System.out.println("[WARN] xmlfile: failed parsing a field: " + e);
                c.recoverTo(depthBefore);
            }
        }
    }

    private static Field buildField(XmlCursor c) throws XMLStreamException {
        Field result = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("definition".equals(c.localName()) && result == null) {
                result = buildFieldDefinition(c);
            } else {
                c.skipSubtree();
            }
        }
        return result;
    }

    private static Field buildFieldDefinition(XmlCursor c) throws XMLStreamException {
        String xsiType = c.xsiType();
        Field f = FieldXsiType.newInstance(xsiType);
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "fieldName" -> f.setName(c.elementText());
                case "fieldID" -> f.setFieldID(c.intText());
                case "owner" -> f.setOwner(c.elementText());
                case "lastModifiedBy" -> f.setLastChangedBy(c.elementText());
                case "helpText" -> f.setHelpText(c.elementText());
                case "entryMode" -> f.setFieldOption(fieldOption(c.elementText()));
                case "createMode" -> f.setCreateMode(createMode(c.elementText()));
                case "defaultValue" -> f.setDefaultValue(ValueXmlBuilder.build(c));
                case "fieldMapping" -> f.setFieldMap(buildFieldMapping(c));
                case "modifyPermissionList" -> f.setPermissions(buildPermissions(c, false));
                case "objectProperties" -> f.setObjectProperty(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "displayInstanceList" -> f.setDisplayInstance(buildDisplayInstances(c));
                case "limits" -> f.setFieldLimit(FieldLimitXmlBuilder.build(c, xsiType));
                default -> c.skipSubtree();
            }
        }
        return f;
    }

    private static int fieldOption(String s) {
        return switch (s) {
            case "required" -> Constants.AR_FIELD_OPTION_REQUIRED;
            case "system" -> Constants.AR_FIELD_OPTION_SYSTEM;
            case "display" -> Constants.AR_FIELD_OPTION_DISPLAY;
            default -> Constants.AR_FIELD_OPTION_OPTIONAL;
        };
    }

    private static int createMode(String s) {
        return switch (s) {
            case "protected" -> 1;
            case "open" -> 0;
            default -> 0;
        };
    }

    private static FieldMapping buildFieldMapping(XmlCursor c) throws XMLStreamException {
        FieldMapping mapping = new RegularFieldMapping();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "regular" -> c.skipSubtree();
                case "join" -> {
                    JoinFieldMapping join = new JoinFieldMapping();
                    while (c.nextTag() == START_ELEMENT) {
                        switch (c.localName()) {
                            case "formIndex" -> join.setIndex(c.intText());
                            case "fieldID" -> join.setFieldID(c.intText());
                            default -> c.skipSubtree();
                        }
                    }
                    mapping = join;
                }
                case "view" -> {
                    ViewFieldMapping view = new ViewFieldMapping();
                    while (c.nextTag() == START_ELEMENT) {
                        if ("fieldName".equals(c.localName())) view.setFieldName(c.elementText());
                        else c.skipSubtree();
                    }
                    mapping = view;
                }
                default -> c.skipSubtree();
            }
        }
        return mapping;
    }

    private static DisplayInstanceMap buildDisplayInstances(XmlCursor c) throws XMLStreamException {
        DisplayInstanceMap map = new DisplayInstanceMap();
        while (c.nextTag() == START_ELEMENT) {
            if (!"displayInstance".equals(c.localName())) { c.skipSubtree(); continue; }
            int viewId = 0;
            DisplayPropertyMap props = null;
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "viewID" -> viewId = c.intText();
                    case "displayProperties" -> props = PropertyMapXmlBuilder.build(c, new DisplayPropertyMap());
                    default -> c.skipSubtree();
                }
            }
            if (props != null) {
                for (var e : props.entrySet()) map.setProperty(viewId, e.getKey(), e.getValue());
            }
        }
        return map;
    }

    private static void readViews(XmlCursor c, List<View> out) throws XMLStreamException {
        while (c.nextTag() == START_ELEMENT) {
            if (!"view".equals(c.localName())) { c.skipSubtree(); continue; }
            int depthBefore = c.depth();
            try {
                out.add(buildView(c));
            } catch (Exception e) {
                System.out.println("[WARN] xmlfile: failed parsing a view: " + e);
                c.recoverTo(depthBefore);
            }
        }
    }

    private static View buildView(XmlCursor c) throws XMLStreamException {
        View v = new View();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "viewName" -> v.setName(c.elementText());
                case "viewID" -> v.setVUIId(c.intText());
                case "owner" -> v.setOwner(c.elementText());
                case "lastModifiedBy" -> v.setLastChangedBy(c.elementText());
                case "properties" -> v.setDisplayProperties(PropertyMapXmlBuilder.build(c, new ViewDisplayPropertyMap()));
                case "objectProperties" -> v.setObjectProperties(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "locale" -> v.setLocale(c.elementText());
                case "vuiType" -> v.setVUIType("windows".equals(c.elementText()) ? 0 : 1);
                default -> c.skipSubtree();
            }
        }
        return v;
    }
}
