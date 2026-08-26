package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Builds a {@link FieldLimit} from a &lt;limits&gt; element, for the datatypes FieldDetailPage
 * actually renders limits for (character/integer/real/decimal/currency/attachment/enumeration -
 * matches the scope already carved out in the approved plan). Anything else (date/dateTime/diary/
 * timeOfDay/table/column/display/view) is skipped - those fields' &lt;limits&gt;, when present, are
 * VUI layout metadata rather than data constraints, and aren't consumed by the doc pages.
 */
final class FieldLimitXmlBuilder {
    private FieldLimitXmlBuilder() {}

    static FieldLimit build(XmlCursor c, String xsiType) throws XMLStreamException {
        FieldLimit limit = switch (xsiType == null ? "" : xsiType) {
            case "character" -> buildCharacter(c);
            case "integer" -> buildInteger(c);
            case "real" -> buildReal(c);
            case "decimal" -> buildDecimal(c);
            case "currency" -> buildCurrency(c);
            case "attachment" -> buildAttachment(c);
            case "enumeration" -> buildEnumeration(c);
            default -> { c.skipSubtree(); yield null; }
        };
        return limit;
    }

    private static CharacterFieldLimit buildCharacter(XmlCursor c) throws XMLStreamException {
        CharacterFieldLimit limit = new CharacterFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "maximumLength" -> limit.setMaxLength(c.intText());
                // "byte" is the only real lengthUnit value this format uses - still must consume
                // the element's text via elementText(), even though the value is discarded, or the
                // cursor is left mid-element and every sibling after it desyncs silently.
                case "lengthUnit" -> { c.elementText(); limit.setLengthUnits(0); }
                case "fullTextOption" -> limit.setFullTextOption(XmlEnums.fullTextOption(c.elementText()));
                case "queryByExample" -> limit.setQBEMatch(XmlEnums.qbeMatch(c.elementText()));
                case "menuStyle" -> limit.setMenuStyle("overwrite".equals(c.elementText()) ? 1 : 0);
                case "clobStorageOption" -> limit.setStorageOptionForCLOB(clobStorageOption(c.elementText()));
                case "menuNameReference" -> limit.setCharMenu(c.elementText());
                case "pattern" -> limit.setPattern(c.elementText());
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    /** No public AR_STORAGE_OPTION_* constant exists to reference; ordinal ints (Default/InRow/OutRow) are this port's own numbering since no documented constant exposes the real ones. */
    private static int clobStorageOption(String s) {
        return switch (s) {
            case "InRow" -> 1;
            case "OutRow" -> 2;
            default -> 0; // "Default"
        };
    }

    private static IntegerFieldLimit buildInteger(XmlCursor c) throws XMLStreamException {
        IntegerFieldLimit limit = new IntegerFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "lowRange" -> limit.setLowRange(c.intText());
                case "highRange" -> limit.setHighRange(c.intText());
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    private static RealFieldLimit buildReal(XmlCursor c) throws XMLStreamException {
        RealFieldLimit limit = new RealFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "lowRange" -> limit.setLowRange(Double.parseDouble(c.elementText().trim()));
                case "highRange" -> limit.setHighRange(Double.parseDouble(c.elementText().trim()));
                case "precision" -> limit.setPrecision(c.intText());
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    private static DecimalFieldLimit buildDecimal(XmlCursor c) throws XMLStreamException {
        DecimalFieldLimit limit = new DecimalFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "lowRange" -> limit.setLowRange(new BigDecimal(c.elementText().trim()));
                case "highRange" -> limit.setHighRange(new BigDecimal(c.elementText().trim()));
                case "precision" -> limit.setPrecision(c.intText());
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    private static CurrencyFieldLimit buildCurrency(XmlCursor c) throws XMLStreamException {
        CurrencyFieldLimit limit = new CurrencyFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "lowRange" -> limit.setLowRange(new BigDecimal(c.elementText().trim()));
                case "highRange" -> limit.setHighRange(new BigDecimal(c.elementText().trim()));
                case "precision" -> limit.setPrecision(c.intText());
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    private static AttachmentFieldLimit buildAttachment(XmlCursor c) throws XMLStreamException {
        AttachmentFieldLimit limit = new AttachmentFieldLimit();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "maximumSize" -> limit.setMaxSize(c.intText());
                case "attachmentType" -> limit.setAttachType("link".equals(c.elementText()) ? Constants.AR_ATTACH_FIELD_TYPE_LINK : Constants.AR_ATTACH_FIELD_TYPE_EMBED);
                default -> c.skipSubtree();
            }
        }
        return limit;
    }

    private static SelectionFieldLimit buildEnumeration(XmlCursor c) throws XMLStreamException {
        List<EnumItem> items = new ArrayList<>();
        while (c.nextTag() == START_ELEMENT) {
            switch (c.localName()) {
                case "regular" -> {
                    int idx = 0;
                    while (c.nextTag() == START_ELEMENT) {
                        if ("valueList".equals(c.localName())) {
                            while (c.nextTag() == START_ELEMENT) {
                                if ("value".equals(c.localName())) items.add(new EnumItem(c.elementText(), idx++));
                                else c.skipSubtree();
                            }
                        } else {
                            c.skipSubtree();
                        }
                    }
                }
                case "custom" -> {
                    while (c.nextTag() == START_ELEMENT) {
                        if ("enumItem".equals(c.localName())) {
                            String name = null;
                            int number = 0;
                            while (c.nextTag() == START_ELEMENT) {
                                switch (c.localName()) {
                                    case "itemName" -> name = c.elementText();
                                    case "itemNumber" -> number = c.intText();
                                    default -> c.skipSubtree();
                                }
                            }
                            items.add(new EnumItem(name != null ? name : "", number));
                        } else {
                            c.skipSubtree();
                        }
                    }
                }
                default -> c.skipSubtree();
            }
        }
        return new SelectionFieldLimit(items);
    }
}
