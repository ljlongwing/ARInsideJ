package arinside.ar.xmlfile;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.*;

/**
 * Thin wrapper around {@link XMLStreamReader} that tracks element nesting depth itself, so a
 * builder that hits unexpected/malformed structure partway through one object can recover cleanly:
 * catch the exception, call {@link #recoverTo(int)} back to the depth recorded before that object
 * started, and move on to the next sibling instead of aborting the whole multi-gigabyte parse.
 * StAX's own {@code XMLStreamReader} exposes no reliable "current depth" query, hence this wrapper.
 */
final class XmlCursor {
    private final XMLStreamReader r;
    private int depth = 0;

    XmlCursor(XMLStreamReader r) {
        this.r = r;
    }

    int depth() {
        return depth;
    }

    XMLStreamReader raw() {
        return r;
    }

    boolean hasNext() throws XMLStreamException {
        return r.hasNext();
    }

    int next() throws XMLStreamException {
        int ev = r.next();
        if (ev == START_ELEMENT) depth++;
        else if (ev == END_ELEMENT) depth--;
        return ev;
    }

    /** Advances to the next START_ELEMENT or END_ELEMENT, skipping whitespace/comments/text. */
    int nextTag() throws XMLStreamException {
        int ev;
        do {
            ev = next();
        } while (ev != START_ELEMENT && ev != END_ELEMENT);
        return ev;
    }

    String localName() {
        return r.getLocalName();
    }

    int eventType() {
        return r.getEventType();
    }

    /** Reads a leaf element's text content. Current event must be START_ELEMENT; leaves the cursor on the matching END_ELEMENT. */
    String elementText() throws XMLStreamException {
        int startDepth = depth;
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ev = next();
            if (ev == CHARACTERS || ev == CDATA) {
                sb.append(r.getText());
            } else if (ev == END_ELEMENT) {
                if (depth == startDepth - 1) break;
            } else if (ev == START_ELEMENT) {
                throw new XMLStreamException("expected leaf text in <" + r.getLocalName() + ">, found nested element");
            }
        }
        return sb.toString();
    }

    /** Current event must be START_ELEMENT; consumes through the matching END_ELEMENT, discarding all content. */
    void skipSubtree() throws XMLStreamException {
        int target = depth - 1;
        while (depth > target) next();
    }

    /** Recovery from a mid-object parse failure: advances (discarding everything) until back at targetDepth. */
    void recoverTo(int targetDepth) throws XMLStreamException {
        while (depth > targetDepth && hasNext()) next();
    }

    /**
     * Reads a leaf element's text as an int, tolerating values serialized as their unsigned 32-bit
     * equivalent (e.g. a real export emitted "4294967184" for a limit meant to be -112) by falling
     * back to a truncating long parse - the same wraparound a C int would already have done.
     */
    int intText() throws XMLStreamException {
        String text = elementText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return (int) Long.parseLong(text);
        }
    }

    /**
     * Same as {@link #intText()}, but for elements that are sometimes a plain int and sometimes a
     * named enum string this port doesn't have a lookup table for (e.g. dataDictionaryMenu's
     * formType/fieldType, which can read "allowedInMultiFormSearch" instead of a number) - falls
     * back to {@code fallback} with a warning rather than aborting the whole containing object's
     * parse over one rarely-used, non-critical field.
     */
    int intTextOrDefault(int fallback, String fieldDescription) throws XMLStreamException {
        String text = elementText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            try {
                return (int) Long.parseLong(text);
            } catch (NumberFormatException e2) {
                System.out.println("[WARN] xmlfile: " + fieldDescription + " has non-numeric value '" + text + "', defaulting to " + fallback);
                return fallback;
            }
        }
    }

    String attr(String localName) {
        return r.getAttributeValue(null, localName);
    }

    String xsiType() {
        return r.getAttributeValue("http://www.w3.org/2001/XMLSchema-instance", "type");
    }
}
