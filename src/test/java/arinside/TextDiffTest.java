package arinside;

import arinside.util.TextDiff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Word-level inline diff used by the snapshot-diff report to show what changed inside a Run If. */
class TextDiffTest {

    @Test
    void identicalInputProducesNoInsOrDel() {
        String s = TextDiff.inlineWords("<b>'Status'</b> = \"Closed\"", "'Status' = \"Closed\"");
        assertFalse(s.contains("<del"), "no deletions expected: " + s);
        assertFalse(s.contains("<ins"), "no insertions expected: " + s);
    }

    @Test
    void oneChangedTokenIsMarkedInsAndDel() {
        String s = TextDiff.inlineWords("'Field 3236' = $NULL$", "'Field 7' = $NULL$");
        assertTrue(s.contains("<del class=\"tokdel\">3236</del>"), "old token not struck: " + s);
        assertTrue(s.contains("<ins class=\"tokins\">7</ins>"), "new token not inserted: " + s);
        assertTrue(s.contains("$NULL$"), "unchanged tail should survive: " + s);
    }

    @Test
    void htmlTagsAreStrippedNotDiffed() {
        String s = TextDiff.inlineWords("<span class=x>'A'</span> = 1", "<span class=y>'A'</span> = 2");
        assertFalse(s.contains("class=x") || s.contains("class=y"), "tags should be stripped: " + s);
        assertTrue(s.contains("<del class=\"tokdel\">1</del>") && s.contains("<ins class=\"tokins\">2</ins>"), s);
    }
}
