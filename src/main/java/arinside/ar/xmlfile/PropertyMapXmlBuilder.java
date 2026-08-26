package arinside.ar.xmlfile;

import com.bmc.arsys.api.PropertyMap;

import javax.xml.stream.XMLStreamException;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds any {@link PropertyMap} subtype (ObjectPropertyMap, DisplayPropertyMap, ...) from a
 * container element whose children are a flat list of &lt;property&gt;&lt;value&gt;...&lt;tag&gt;N
 * &lt;/tag&gt;&lt;/property&gt; entries - the same shape used for objectProperties, VUI/field
 * displayProperties, and container/menu properties alike.
 */
final class PropertyMapXmlBuilder {
    private PropertyMapXmlBuilder() {}

    static <T extends PropertyMap> T build(XmlCursor c, T target) throws XMLStreamException {
        while (c.nextTag() == START_ELEMENT) {
            if ("property".equals(c.localName())) {
                com.bmc.arsys.api.Value value = null;
                Integer tag = null;
                while (c.nextTag() == START_ELEMENT) {
                    switch (c.localName()) {
                        case "value" -> value = ValueXmlBuilder.build(c);
                        case "tag" -> tag = c.intText();
                        default -> c.skipSubtree();
                    }
                }
                if (tag != null && value != null) target.put(tag, value);
            } else {
                c.skipSubtree();
            }
        }
        return target;
    }
}
