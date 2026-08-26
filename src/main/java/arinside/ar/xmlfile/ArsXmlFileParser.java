package arinside.ar.xmlfile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Genuinely offline parser for the real AR System Administrator .xml export format - file mode must
 * never need a live server connection, and the AR Java API's own {@code getListXFromDef} calls are
 * real server RPCs, not a local parse the way the C++ tool's {@code ARParseXMLDocument} is. This is
 * a from-scratch StAX parser built directly against the AR Java API's own object model.
 *
 * <p>Structural properties of the export format that shape this parser:
 * <ul>
 *   <li>9 top-level element types, each independently root-namespaced: form, activeLink, filter,
 *   escalation, menu, container, image, plus distributedMapping/distributedPool (DSO config -
 *   skipped, nothing in this port renders it).
 *   <li>The file is a direct XML serialization of the same object model the AR Java API already
 *   uses - qualifications/actions/assignments are fully decoded structural XML, not an opaque
 *   encoding, so this parser only has to map elements onto the exact classes {@code Doc*Page}
 *   already knows how to render, never invent a decoder.
 *   <li>The file is NOT well-formed XML as a whole: each object was independently serialized as
 *   its own {@code <root>...</root>} fragment, then all fragments were naively concatenated - one
 *   {@code <?xml?>} decl, one surviving {@code <root>} open, but one {@code </root>} close per
 *   fragment. {@link RootTagStrippingReader} fixes this in one streaming pass.
 * </ul>
 *
 * <p>Fields/VUIs are nested directly inside their owning &lt;form&gt; element (unlike the def-file
 * RPC path, which needed a separate per-form call) - so this parser needs exactly one pass over the
 * file, no follow-up calls of any kind, genuinely offline.
 */
public final class ArsXmlFileParser {
    private ArsXmlFileParser() {}

    public static ParsedObjects parse(String filePath) throws IOException, XMLStreamException {
        // The JDK's default JAXP security limits (jdk.xml.maxElementDepth=100 in particular) are
        // meant to guard against untrusted/malicious XML, but real exports can legitimately nest
        // hundreds of levels deep (e.g. a long string-concatenation <arithmetic> chain in a
        // SetFields action). This is a trusted local export file, not untrusted input, so these
        // limits are raised generously rather than left at their conservative defaults.
        System.setProperty("jdk.xml.maxElementDepth", "100000");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");

        ParsedObjects result = new ParsedObjects();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        try (Reader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8), 1 << 20);
             Reader stripped = new RootTagStrippingReader(fileReader)) {
            XMLStreamReader raw = factory.createXMLStreamReader(stripped);
            try {
                XmlCursor c = new XmlCursor(raw);
                parseTopLevel(c, result);
            } finally {
                raw.close();
            }
        }
        return result;
    }

    private static void parseTopLevel(XmlCursor c, ParsedObjects result) throws XMLStreamException {
        // c starts before any event; the synthesized <root> is the document element.
        if (c.next() != START_ELEMENT) throw new XMLStreamException("expected root element");
        long counted = 0;
        while (c.nextTag() == START_ELEMENT) {
            String tag = c.localName();
            int depthBefore = c.depth();
            try {
                switch (tag) {
                    case "form" -> {
                        FormXmlBuilder.FormResult r = FormXmlBuilder.build(c);
                        String name = r.form().getName();
                        if (name != null) {
                            result.forms.put(name, r.form());
                            result.fieldsByForm.put(name, r.fields());
                            result.viewsByForm.put(name, r.views());
                        }
                    }
                    case "activeLink" -> {
                        var al = WorkflowXmlBuilder.buildActiveLink(c);
                        if (al.getName() != null) result.activeLinks.put(al.getName(), al);
                    }
                    case "filter" -> {
                        var f = WorkflowXmlBuilder.buildFilter(c);
                        if (f.getName() != null) result.filters.put(f.getName(), f);
                    }
                    case "escalation" -> {
                        var e = WorkflowXmlBuilder.buildEscalation(c);
                        if (e.getName() != null) result.escalations.put(e.getName(), e);
                    }
                    case "menu" -> {
                        var m = MenuXmlBuilder.build(c);
                        if (m.getName() != null) result.menus.put(m.getName(), m);
                    }
                    case "container" -> {
                        var container = ContainerXmlBuilder.build(c);
                        if (container.getName() != null) result.containers.put(container.getName(), container);
                    }
                    case "image" -> {
                        var img = ImageXmlBuilder.build(c);
                        if (img.getName() != null) result.images.put(img.getName(), img);
                    }
                    default -> c.skipSubtree(); // distributedMapping/distributedPool/anything else
                }
            } catch (Exception e) {
                System.out.println("[WARN] xmlfile: failed parsing a top-level <" + tag + ">, skipping it: " + e);
                try {
                    c.recoverTo(depthBefore);
                } catch (Exception recoveryFailure) {
                    System.out.println("[WARN] xmlfile: recovery itself failed after a parse error, stopping early with "
                        + counted + " objects parsed so far: " + recoveryFailure);
                    return;
                }
            }
            if (++counted % 5000 == 0) {
                System.out.println("[INFO] xmlfile: parsed " + counted + " top-level objects so far...");
            }
        }
    }
}
