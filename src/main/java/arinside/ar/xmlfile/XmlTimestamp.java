package arinside.ar.xmlfile;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Shared {@code <modifiedDate>}/{@code <...Guid>}-sibling timestamp parsing for every XML-mode
 * builder that feeds {@link arinside.ar.ObjectTimestamp} - a real ISO-8601 offset-datetime string
 * in every real export sample seen (e.g. {@code 2026-05-19T21:46:48+00:00}), confirmed against
 * `xmltest/form_user.xml` and `combined_test.xml`.
 */
final class XmlTimestamp {
    private XmlTimestamp() {}

    /** 0 (the "absent" sentinel {@link arinside.ar.ObjectTimestamp#set} already no-ops on) for null/blank/unparseable input, matching every other XML-mode field's lenient "best effort" parsing. */
    static long parse(String iso8601) {
        if (iso8601 == null || iso8601.isBlank()) return 0L;
        try {
            return OffsetDateTime.parse(iso8601).toEpochSecond();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }
}
