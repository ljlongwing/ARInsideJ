package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds {@link Container} (dispatching on &lt;containerType&gt;'s text to the matching concrete
 * subtype - activeLinkGuide/application/filterGuide/packingList/webService, the full vocabulary
 * confirmed present in a real export via grep, see ArsXmlFileParser's javadoc) from a
 * &lt;container&gt; top-level element.
 */
final class ContainerXmlBuilder {
    private ContainerXmlBuilder() {}

    /** c positioned at the &lt;container&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Container build(XmlCursor c) throws XMLStreamException {
        String name = null, owner = null, lastModifiedBy = null, label = null, description = null, modifiedDate = null;
        ObjectPropertyMap props = null;
        List<PermissionInfo> permissions = null;
        List<ContainerOwner> owners = new ArrayList<>();
        List<Reference> refs = new ArrayList<>();
        String containerType = null;

        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "containerName" -> name = c.elementText();
                case "owner" -> owner = c.elementText();
                case "lastModifiedBy" -> lastModifiedBy = c.elementText();
                case "modifiedDate" -> modifiedDate = c.elementText();
                case "label" -> label = c.elementText();
                case "description" -> description = c.elementText();
                case "objectProperties" -> props = PropertyMapXmlBuilder.build(c, new ObjectPropertyMap());
                case "viewPermissionList" -> permissions = FormXmlBuilder.buildPermissions(c, true);
                case "ownerObjectList" -> owners = buildOwners(c);
                case "containerType" -> containerType = c.elementText();
                case "referenceList" -> refs = buildReferences(c);
                default -> c.skipSubtree();
            }
        }

        Container container = switch (containerType == null ? "" : containerType) {
            case "activeLinkGuide" -> new ActiveLinkGuide();
            case "application" -> new ApplicationContainer();
            case "filterGuide" -> new FilterGuide();
            case "packingList" -> new PackingList();
            case "webService" -> new WebService();
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized containerType '" + containerType + "', defaulting to application");
                yield new ApplicationContainer();
            }
        };
        if (name != null) container.setName(name);
        if (owner != null) container.setOwner(owner);
        if (lastModifiedBy != null) container.setLastChangedBy(lastModifiedBy);
        arinside.ar.ObjectTimestamp.set(container, XmlTimestamp.parse(modifiedDate));
        if (label != null) container.setLabel(label);
        if (description != null) container.setDescription(description);
        if (props != null) container.setProperties(props);
        if (permissions != null) container.setPermissions(permissions);
        container.setContainerOwner(owners);
        container.setReferences(refs);
        return container;
    }

    private static List<ContainerOwner> buildOwners(XmlCursor c) throws XMLStreamException {
        List<ContainerOwner> owners = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"ownerObject".equals(c.localName())) { c.skipSubtree(); continue; }
            String type = null, name = null;
            while (c.nextTag() == START_ELEMENT) {
                switch (c.localName()) {
                    case "ownerType" -> type = c.elementText();
                    case "ownerName" -> name = c.elementText();
                    default -> c.skipSubtree();
                }
            }
            int typeCode = switch (type == null ? "" : type) {
                case "form" -> ContainerOwner.SCHEMA;
                case "all" -> ContainerOwner.ALL;
                default -> ContainerOwner.NONE;
            };
            owners.add(new ContainerOwner(typeCode, name != null ? name : ""));
        }
        return owners;
    }

    private static List<Reference> buildReferences(XmlCursor c) throws XMLStreamException {
        List<Reference> refs = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            if (!"reference".equals(c.localName())) { c.skipSubtree(); continue; }
            int depthBefore = c.depth();
            try {
                Reference r = buildOneReference(c);
                if (r != null) refs.add(r);
            } catch (Exception e) {
                System.out.println("[WARN] xmlfile: failed parsing a container reference: " + e);
                c.recoverTo(depthBefore);
            }
        }
        return refs;
    }

    private static Reference buildOneReference(XmlCursor c) throws XMLStreamException {
        Reference r = null;
        while (c.nextTag() == START_ELEMENT) {
            if ("externalReference".equals(c.localName()) && r == null) {
                r = new Reference();
                while (c.nextTag() == START_ELEMENT) {
                    switch (c.localName()) {
                        case "label" -> r.setLabel(c.elementText());
                        case "description" -> r.setDescription(c.elementText());
                        case "referenceType" -> r.setReferenceType(ReferenceType.toReferenceType(c.intText()));
                        case "referenceValue" -> r.setName(readReferenceValueText(c));
                        default -> c.skipSubtree();
                    }
                }
            } else {
                c.skipSubtree();
            }
        }
        return r;
    }

    private static String readReferenceValueText(XmlCursor c) throws XMLStreamException {
        String text = "";
        while (c.nextTag() == START_ELEMENT) {
            if ("characterValue".equals(c.localName())) text = c.elementText();
            else c.skipSubtree();
        }
        return text;
    }
}
