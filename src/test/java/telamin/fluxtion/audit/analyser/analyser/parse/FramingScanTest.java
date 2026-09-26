package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M68.3: which lines look like the start of a further record. Pure; each case names what it guards. */
class FramingScanTest {

    private static List<Integer> candidates(String text) {
        return FramingScan.of(text, 1 << 20).candidates();
    }

    @Test
    @DisplayName("two records run together: the second header's line is named")
    void aRealCollapse() {
        assertEquals(List.of(4), candidates("eventLogRecord: \n    event: A\n    logTime: 1\neventLogRecord:\n    event: B\n"));
    }

    @Test
    @DisplayName("THE FALSE VERDICT: the key inside a quoted value is not a record")
    void aQuotedKeyIsText() {
        // witness: FramingScan treating every column-0 key as a header regardless of quote state
        assertEquals(List.of(), candidates("eventLogRecord:\n    eventToString: \"Note{text=eventLogRecord: hello}\"\n"));
        assertEquals(List.of(), candidates("eventLogRecord:\n    detail: \"spans lines\neventLogRecord:\n      closes\"\n"),
                "a double-quoted value can span lines, and the key inside it is text");
        assertEquals(List.of(), candidates("eventLogRecord:\n    detail: 'it''s\neventLogRecord:\n      still quoted'\n"),
                "'' is an escaped quote inside a single-quoted value");
    }

    @Test
    @DisplayName("an apostrophe in a plain value opens nothing, so it cannot hide a real collapse")
    void anApostropheIsNotAQuote() {
        // witness: quoteStateAfter opening a quote anywhere, not only at a value position
        assertEquals(List.of(3), candidates("eventLogRecord:\n    eventToString: it's plain\neventLogRecord:\n"));
    }

    @Test
    @DisplayName("an indented or mid-line key is not a header")
    void onlyColumnZeroCounts() {
        assertEquals(List.of(), candidates("eventLogRecord:\n    nested:\n      eventLogRecord:\n    note: eventLogRecord: x\n"));
    }

    @Test
    @DisplayName("an item longer than the limit says it was not read whole")
    void beyondTheLimitIsNotAssessed() {
        String big = "eventLogRecord:\n" + "    pad: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n".repeat(40) + "eventLogRecord:\n";
        FramingScan scan = FramingScan.of(big, 200);
        assertTrue(scan.truncated());
        assertFalse(scan.suspected(), "the second header is past the limit, so it was not seen — and that is stated");
    }
}
