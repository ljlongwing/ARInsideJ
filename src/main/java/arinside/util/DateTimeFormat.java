package arinside.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Java port of util/Util.cpp's DateTimeToString / DateTimeToHTMLString / CurrentDateTimeToHTMLString. */
public final class DateTimeFormat {
    private DateTimeFormat() {}

    private static final DateTimeFormatter PLAIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Matches CUtil::DateTimeToString - local time, plain space separator. */
    public static String toPlainString(long epochSeconds) {
        return format(epochSeconds, " ");
    }

    /** Matches CUtil::DateTimeToHTMLString - local time, "&nbsp;" separator, negative timestamps clamped to 0. */
    public static String toHtmlString(long epochSeconds) {
        return format(Math.max(epochSeconds, 0), "&nbsp;");
    }

    public static String currentToHtmlString() {
        return toHtmlString(Instant.now().getEpochSecond());
    }

    private static String format(long epochSeconds, String separator) {
        var dt = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'" + separator + "'HH:mm:ss"));
    }
}
