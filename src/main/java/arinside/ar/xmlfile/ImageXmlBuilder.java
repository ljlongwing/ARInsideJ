package arinside.ar.xmlfile;

import com.bmc.arsys.api.Image;
import com.bmc.arsys.api.ImageData;
import com.bmc.arsys.api.ObjectPropertyMap;

import javax.xml.stream.XMLStreamException;
import java.util.Base64;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/** Builds {@link Image} (base64 &lt;imageContent&gt; decoded straight to bytes) from an &lt;image&gt; top-level element. */
final class ImageXmlBuilder {
    private ImageXmlBuilder() {}

    /** c positioned at the &lt;image&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Image build(XmlCursor c) throws XMLStreamException {
        Image img = new Image();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "imageName" -> img.setName(c.elementText());
                case "imageType" -> img.setType(c.elementText());
                case "owner" -> img.setOwner(c.elementText());
                case "lastModifiedBy" -> img.setLastChangedBy(c.elementText());
                case "objectProperties" -> img.setProperties(PropertyMapXmlBuilder.build(c, new ObjectPropertyMap()));
                case "imageChecksum" -> img.setCheckSum(c.elementText());
                case "imageContent" -> {
                    String b64 = c.elementText().trim();
                    byte[] bytes = b64.isEmpty() ? new byte[0] : Base64.getMimeDecoder().decode(b64);
                    img.setImageData(new ImageData(bytes));
                }
                default -> c.skipSubtree();
            }
        }
        return img;
    }
}
