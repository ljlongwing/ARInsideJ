package arinside.ar.deffile;

import com.bmc.arsys.api.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Java port of {@code com.bmc.arsys.server.domain.util.decode.Decoder} (ported from the real
 * AR Server). This is the shared primitive
 * every packed {@code .def} field builds on: a byte-cursor over one already-extracted tag value,
 * tokenized on a single-byte delimiter ({@code '\'}, same char as {@link DefItemLabel#FILE_SEPARATOR}'s
 * own use as a field separator - confirmed via {@code DELIMITER = DefItemLabel.FILE_SEPARATOR}
 * in the real source), with {@link #decodeValue()} as a small tagged-union decoder for a single
 * {@link Value} (field default values, and - repeated many times in a row - every entry of an
 * object/display property list, see {@link DefPropertyDecoder}).
 *
 * <p>Ported directly onto {@code com.bmc.arsys.api.*} client types (not the server-internal
 * {@code com.bmc.arsys.domain.value.*} model the real class builds) - constructors/DataTypes
 * confirmed via {@code javap} against the real 23.3.002 jar.
 *
 * <p>Tag 9 (byte-list/BYTES), 13 (DATE), 14 (TIME_OF_DAY), and 41 (COORDS) are real but rare types
 * with no rendering path anywhere in this port's {@code Doc*Page} classes (confirmed:
 * {@code ValueFormat.format()} only switches on NULL/KEYWORD/INTEGER/ENUM/REAL/DECIMAL/CHAR/DIARY/
 * TIME/CURRENCY, falling back to a generic {@code toString()} for everything else) - decoded
 * best-effort as a plain string-tagged {@link Value} rather than precisely reconstructed, since
 * nothing downstream distinguishes them further.
 */
final class DefValueDecoder {
    private static final byte DELIMITER = '\\';

    private final byte[] bytes;
    private final Charset charset;
    private int pos;

    DefValueDecoder(String encoded, Charset charset) {
        this.charset = charset != null ? charset : Charset.forName("UTF-8");
        this.bytes = encoded == null ? new byte[0] : toBytes(encoded);
        this.pos = 0;
    }

    private byte[] toBytes(String s) {
        try {
            return s.getBytes(charset.name());
        } catch (UnsupportedEncodingException e) {
            return s.getBytes(charset);
        }
    }

    boolean isEmpty() {
        return pos >= bytes.length;
    }

    /** Reads up to (not including) the next delimiter byte, trimmed - matches Decoder.readString(). */
    String readString() {
        if (isEmpty()) return "";
        int start = pos;
        while (pos < bytes.length && bytes[pos] != DELIMITER) pos++;
        String s = decode(start, pos);
        if (pos < bytes.length) pos++; // skip delimiter
        return s.trim();
    }

    /** Reads every remaining byte in the buffer, verbatim (no delimiter handling - there is nothing left to delimit). */
    String readRest() {
        if (isEmpty()) return "";
        String s = decode(pos, bytes.length);
        pos = bytes.length;
        return s;
    }

    /** Reads exactly `size` raw bytes (not delimiter-terminated), then skips one trailing delimiter if present - matches Decoder.readString(int). */
    String readString(int size) {
        if (isEmpty()) return "";
        int end = Math.min(pos + Math.max(size, 0), bytes.length);
        String s = decode(pos, end);
        pos = end;
        if (pos < bytes.length && bytes[pos] == DELIMITER) pos++;
        return s;
    }

    private String decode(int from, int to) {
        try {
            return new String(bytes, from, to - from, charset.name());
        } catch (UnsupportedEncodingException e) {
            return new String(bytes, from, to - from, charset);
        }
    }

    int readInt() {
        String s = readString();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    long readLong() {
        String s = readString();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    double readDouble() {
        String s = readString();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Java port of Decoder.decodeValueList() - a plain count-prefixed run of decodeValue() calls, used by qualification VALUE_SET operands. */
    List<Value> decodeValues() {
        int size = readInt();
        List<Value> values = new ArrayList<>(Math.max(size, 0));
        for (int i = 0; i < size; i++) values.add(decodeValue());
        return values;
    }

    /** Java port of Decoder.decodeValue() - the tagged-union field-value decoder. Null if nothing left to read. */
    Value decodeValue() {
        if (isEmpty()) return null;
        int tag = readInt();
        return switch (tag) {
            case 0 -> new Value();
            case 1 -> new Value(Keyword.toKeyword(readInt()));
            case 2 -> new Value(readInt());
            case 3 -> new Value(readDouble());
            case 4 -> new Value(readString(readInt()));
            case 5 -> decodeDiary();
            case 6 -> new Value(readString(), DataType.ENUM);
            case 7 -> new Value(new Timestamp(readLong()));
            case 8 -> new Value(readString(), DataType.BITMASK);
            case 9 -> decodeByteList();
            case 10 -> new Value(readString(readInt()), DataType.DECIMAL);
            case 11 -> new Value(decodeAttachment());
            case 12 -> new Value(decodeCurrency());
            case 13 -> new Value(String.valueOf(readInt()), DataType.DATE);
            case 14 -> new Value(String.valueOf(readLong()), DataType.TIME_OF_DAY);
            case 40 -> new Value(readString(), DataType.ULONG);
            case 41 -> decodeCoords();
            case 42 -> new Value(readString(readInt()), DataType.VIEW);
            case 43 -> new Value(readString(readInt()), DataType.DISPLAY);
            case 100 -> decodeValueList();
            default -> null;
        };
    }

    private Value decodeDiary() {
        int len = readInt();
        String str = readString(len);
        DiaryListValue diary;
        try {
            diary = DiaryListValue.decode(str);
        } catch (ARException e) {
            diary = null;
        }
        if (diary == null) diary = new DiaryListValue();
        return new Value(diary);
    }

    /**
     * BYTES(9): {@code <subtype>\<byteLen>\<hex-bytes>\}. Real, common carrier for embedded VUI
     * icon images (confirmed against real full.def data - the client jar's own {@code
     * Value(String, DataType)} constructor rejects DataType.BYTES with an IllegalArgumentException,
     * there is no rendering path anywhere in this port for
     * raw icon bytes either) - the bytes are consumed (to keep the cursor aligned for whatever
     * follows in the same property list) but discarded, returning null so the caller simply omits
     * this one property rather than storing a placeholder.
     */
    private Value decodeByteList() {
        readInt(); // byte-list subtype, discarded
        int byteLen = readInt();
        readString(byteLen * 2); // hex-encoded raw bytes, discarded
        return null;
    }

    /** COORDS(41): a real but effectively obsolete geographic-field type with no {@link Value} constructor that accepts it (confirmed via the same ported constructor check as BYTES) and no rendering path in this port - tokens consumed to keep the cursor aligned, value dropped. */
    private Value decodeCoords() {
        int numPairs = readInt();
        if (numPairs == 0) {
            readString(0);
        } else {
            for (int i = 0; i < numPairs; i++) {
                readLong();
                readLong();
            }
        }
        return null;
    }

    /** VALUELIST(100): same story as BYTES/COORDS - DataType.VALUELIST is also rejected by {@code Value(String, DataType)} (confirmed via the same ported check), no rendering path in this port. Token consumed, value dropped. */
    private Value decodeValueList() {
        readString();
        return null;
    }

    private AttachmentValue decodeAttachment() {
        AttachmentValue value = new AttachmentValue();
        long originalSize = readLong();
        long compressedSize = readLong();
        int nameLen = readInt();
        String name = readString(nameLen);
        value.setOriginalSize(originalSize);
        value.setCompressedSize(compressedSize);
        value.setName(name);
        return value;
    }

    private CurrencyValue decodeCurrency() {
        readInt(); // matches the real decoder's own discarded leading token
        String currVal = readString(readInt());
        String currCode = readString(readInt());
        long timestamp = readLong();
        readFuncCurrencyList(); // functional-currency breakdown - no rendering path in this port, read to keep the cursor aligned
        return new CurrencyValue(currVal, currCode, new Timestamp(timestamp), null);
    }

    private void readFuncCurrencyList() {
        int numFuncItems = readInt();
        if (numFuncItems == 0) {
            readString();
        } else {
            for (int i = 0; i < numFuncItems; i++) {
                readString(readInt());
                readString(readInt());
            }
        }
    }
}
