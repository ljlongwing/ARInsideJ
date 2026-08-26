package arinside.ar.deffile;

import java.math.BigDecimal;

/** Lenient numeric-token parsing shared by every {@code Def*Builder} - returns a zero value on
 * garbage input rather than throwing, so one malformed token doesn't abort the whole object. */
final class ParseUtil {
    private ParseUtil() {}

    static int intValue(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static long longValue(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static double doubleValue(String s) {
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    static BigDecimal decimalValue(String s) {
        if (s == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    static boolean booleanValue(String s) {
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }
}
