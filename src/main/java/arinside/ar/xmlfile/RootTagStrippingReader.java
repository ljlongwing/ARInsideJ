package arinside.ar.xmlfile;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

/**
 * Real AR System Administrator .xml exports are not well-formed XML: each object was independently
 * serialized as its own {@code <root>...</root>} document, then all fragments were naively
 * concatenated - confirmed by direct byte-offset inspection of a real 4.5GB export, which has
 * exactly one {@code <?xml?>} declaration followed by exactly one {@code <root>} open tag (so the
 * source's own opening tag is already correctly placed and needs no rewriting), but thousands of
 * {@code </root>} close tags - one genuine close per concatenated fragment, all of them literal
 * with no attributes.
 *
 * <p>This wraps the file's Reader, passes everything through unchanged except it strips every
 * {@code </root>} occurrence as the stream is read, and appends exactly one synthesized
 * {@code </root>} at true EOF to close the source's still-present, still correctly-positioned
 * opening tag. The result is well-formed XML - solved in one streaming pass, no whole-file
 * buffering, and no risk of reordering relative to the leading {@code <?xml?>} declaration.
 */
public final class RootTagStrippingReader extends Reader {
    private static final char[] CLOSE = "</root>".toCharArray();

    private final Reader source;
    private final char[] window = new char[CLOSE.length];
    private int windowLen = 0;
    private boolean sourceExhausted = false;
    private boolean closeAppended = false;
    private int closeEmitPos = -1; // >=0 while emitting the synthesized </root> at EOF

    public RootTagStrippingReader(Reader source) {
        this.source = source;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) return 0;
        if (closeAppended) return -1;
        int written = 0;

        while (written < len) {
            if (closeEmitPos >= 0) {
                while (written < len && closeEmitPos < CLOSE.length) {
                    cbuf[off + written++] = CLOSE[closeEmitPos++];
                }
                if (closeEmitPos >= CLOSE.length) {
                    closeAppended = true;
                }
                return written;
            }

            fillWindow();
            if (windowLen == 0) {
                if (sourceExhausted) {
                    closeEmitPos = 0;
                    continue;
                }
                return written == 0 ? -1 : written;
            }

            if (windowLen == CLOSE.length && matches(window, CLOSE)) {
                windowLen = 0;
                continue;
            }

            cbuf[off + written++] = window[0];
            System.arraycopy(window, 1, window, 0, windowLen - 1);
            windowLen--;
        }
        return written;
    }

    private void fillWindow() throws IOException {
        while (windowLen < CLOSE.length && !sourceExhausted) {
            int c = source.read();
            if (c < 0) {
                sourceExhausted = true;
                break;
            }
            window[windowLen++] = (char) c;
        }
    }

    private static boolean matches(char[] buf, char[] pattern) {
        return Arrays.equals(buf, pattern);
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
