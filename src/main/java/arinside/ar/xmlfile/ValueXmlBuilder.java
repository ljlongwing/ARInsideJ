package arinside.ar.xmlfile;

import com.bmc.arsys.api.*;

import javax.xml.stream.XMLStreamException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Builds a {@link Value} from a &lt;value&gt; element's single child, whose tag name picks the
 * {@link DataType} (characterValue/integerValue/enumValue/... - vocabulary confirmed by scanning a
 * 1.5GB real-export sample, see ArsXmlFileParser's javadoc). Cursor must be positioned at the
 * &lt;value&gt; START_ELEMENT; leaves it at the matching END_ELEMENT.
 */
final class ValueXmlBuilder {
    private ValueXmlBuilder() {}

    /** c positioned at the &lt;value&gt; START_ELEMENT; leaves c at its END_ELEMENT. */
    static Value build(XmlCursor c) throws XMLStreamException {
        Value result = null;
        while (c.nextTag() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
            if (result == null) result = buildOne(c);
            else c.skipSubtree();
        }
        return result != null ? result : new Value();
    }

    private static Value buildOne(XmlCursor c) throws XMLStreamException {
        String tag = c.localName();
        return switch (tag) {
            case "nullValue" -> { c.skipSubtree(); yield new Value(); }
            case "characterValue" -> new Value(c.elementText(), DataType.CHAR);
            case "integerValue" -> new Value(c.intText(), DataType.INTEGER);
            case "enumValue" -> new Value(c.intText(), DataType.ENUM);
            case "unsignedLongValue" -> new Value(Long.parseLong(c.elementText().trim()), DataType.ULONG);
            case "maskValue" -> new Value(Long.parseLong(c.elementText().trim()), DataType.BITMASK);
            case "realValue" -> new Value(Double.parseDouble(c.elementText().trim()), DataType.REAL);
            case "decimalValue" -> new Value(new BigDecimal(c.elementText().trim()), DataType.DECIMAL);
            case "keywordValue" -> new Value(Keyword.toKeyword(c.intText()));
            case "diaryValue" -> new Value(buildDiaryList(c.elementText()));
            case "viewValue" -> new Value(c.elementText(), DataType.CHAR); // VIEW isn't accepted by Value(String,DataType); this is always a blank/cosmetic value in practice
            case "dateValue" -> new Value(new DateInfo(c.intText()));
            case "timeValue", "dateTimeValue" -> new Value(new Timestamp(Long.parseLong(c.elementText().trim()) * 1000L));
            case "timeOfDayValue" -> new Value(new Time(Long.parseLong(c.elementText().trim())));
            case "coordinateList" -> new Value(buildCoordinates(c));
            case "byteList" -> new Value(buildByteList(c));
            case "currency" -> new Value(buildCurrency(c));
            default -> {
                System.out.println("[WARN] xmlfile: unrecognized <value> child <" + tag + ">, skipping");
                c.skipSubtree();
                yield new Value();
            }
        };
    }

    /** A &lt;diaryValue&gt; literal (e.g. inside an assignment/qualifier operand) is plain text, not AR's own diary-encoded history format, so it's wrapped as a single anonymous DiaryItem rather than run through DiaryListValue.decode(). */
    private static DiaryListValue buildDiaryList(String text) {
        DiaryListValue list = new DiaryListValue();
        list.add(new DiaryItem(null, text, new Timestamp(0)));
        return list;
    }

    private static List<CoordinateInfo> buildCoordinates(XmlCursor c) throws XMLStreamException {
        List<CoordinateInfo> list = new ArrayList<>();
        while (c.nextTag() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
            if ("coordinate".equals(c.localName())) {
                int x = 0, y = 0;
                while (c.nextTag() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
                    switch (c.localName()) {
                        case "X" -> x = c.intText();
                        case "Y" -> y = c.intText();
                        default -> c.skipSubtree();
                    }
                }
                list.add(new CoordinateInfo(x, y));
            } else {
                c.skipSubtree();
            }
        }
        return list;
    }

    private static ByteListValue buildByteList(XmlCursor c) throws XMLStreamException {
        String type = c.attr("type");
        int typeCode = "JPEG".equalsIgnoreCase(type) ? 1 : "GIF".equalsIgnoreCase(type) ? 2 : "PNG".equalsIgnoreCase(type) ? 4 : 0;
        byte[] bytes = new byte[0];
        while (c.nextTag() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
            if ("bytes".equals(c.localName())) {
                String b64 = c.elementText().trim();
                bytes = b64.isEmpty() ? new byte[0] : Base64.getMimeDecoder().decode(b64);
            } else {
                c.skipSubtree();
            }
        }
        return new ByteListValue(typeCode, bytes);
    }

    private static CurrencyValue buildCurrency(XmlCursor c) throws XMLStreamException {
        CurrencyValue cv = new CurrencyValue();
        while (c.nextTag() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
            switch (c.localName()) {
                case "currencyValue" -> {
                    String text = c.elementText().trim();
                    if (!text.isEmpty()) cv.setValue(new BigDecimal(text));
                }
                case "currencyCode" -> cv.setCurrencyCode(c.elementText());
                case "currencyConversionDate" -> {
                    String text = c.elementText().trim();
                    if (!text.isEmpty()) cv.setConversionDate(Long.parseLong(text) * 1000L);
                }
                default -> c.skipSubtree();
            }
        }
        return cv;
    }
}
