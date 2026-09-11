package telamin.fluxtion.audit.analyser.analyser.diff;

import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.Change;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.DiffRow;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiffBuilderTest {

    private static LogRecord rec(String nodeLogItems) {
        String text = "#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: 1\n  nodeLogs:\n"
                + nodeLogItems + "  endTime: 2\n";
        return RecordParser.parse(text, 0);
    }

    /** A record under the declared quoted-scalar grammar, as the binary reader constructs one. */
    private static LogRecord quotedRec(String nodeLogItems) {
        String text = "eventLogRecord:\n  logTime: 1\n  nodeLogsEncoding: quoted\n  nodeLogs:\n"
                + nodeLogItems + "  endTime: 2\n";
        return RecordParser.parse(text, 0);
    }

    private static Map<String, DiffRow> byKey(LogRecord a, LogRecord b) {
        Map<String, DiffRow> m = new java.util.HashMap<>();
        for (DiffRow r : DiffBuilder.diff(a, b)) m.put(r.key(), r);
        return m;
    }

    /**
     * REVIEWER PROBE (round 5). A number 42.0 and the STRING "42.0" flattened to the same characters
     * and the diff said SAME while a graphable figure had disappeared - the diff disagreeing with the
     * chart and the scorer over one record. Kinds are compared, from the model's own reading.
     */
    @Test
    void aNumberAndAStringSpellingTheSameCharactersAreDifferent() {
        Map<String, DiffRow> rows = byKey(quotedRec("    - n: { price: 42.0}\n"), quotedRec("    - n: { price: \"42.0\"}\n"));
        DiffRow price = rows.get("n.price");
        assertEquals(Change.CHANGED, price.change());
        assertTrue(price.kindDiffers());
        assertEquals("42.0 (number)", price.displayA());
        assertEquals("42.0 (text)", price.displayB());

        rows = byKey(quotedRec("    - n: { live: true, gone: null}\n"), quotedRec("    - n: { live: \"true\", gone: \"null\"}\n"));
        assertEquals(Change.CHANGED, rows.get("n.live").change(), "a flag and the text true");
        assertEquals("true (boolean)", rows.get("n.live").displayA());
        assertEquals(Change.CHANGED, rows.get("n.gone").change(), "null and the text null");
        assertEquals("null (null)", rows.get("n.gone").displayA());
        assertEquals("null (text)", rows.get("n.gone").displayB());
    }

    @Test
    void sameKindAndValueIsSame_howeverItWasSpelled() {
        Map<String, DiffRow> rows = byKey(quotedRec("    - n: { s: hello, x: 1, f: 1.0}\n"),
                                          quotedRec("    - n: { s: \"hello\", x: 1.0, f: 1}\n"));
        assertEquals(Change.SAME, rows.get("n.s").change(), "quoted and bare hello are both the text hello");
        assertFalse(rows.get("n.s").kindDiffers());
        assertEquals("hello", rows.get("n.s").displayB(), "no kind annotation when kinds agree");
        assertEquals(Change.SAME, rows.get("n.x").change(), "1 and 1.0 are the same number");
        assertEquals(Change.SAME, rows.get("n.f").change());
        Map<String, DiffRow> changed = byKey(rec("    - n: { x: 1}\n"), rec("    - n: { x: 2}\n"));
        assertEquals(Change.CHANGED, changed.get("n.x").change());
        assertEquals("1", changed.get("n.x").displayA(), "a plain change is displayed plainly");
    }

    @Test
    void reportsChangedAddedRemovedAndSame() {
        LogRecord a = rec("    - n: { x: 1, y: NEW, gone: 5}\n");
        LogRecord b = rec("    - n: { x: 2, y: NEW, added: 9}\n");
        Map<String, DiffRow> byKey = new java.util.HashMap<>();
        for (DiffRow r : DiffBuilder.diff(a, b)) byKey.put(r.key(), r);

        assertEquals(Change.CHANGED, byKey.get("n.x").change());   // 1 -> 2
        assertEquals(Change.SAME, byKey.get("n.y").change());      // NEW == NEW
        assertEquals(Change.ONLY_A, byKey.get("n.gone").change());
        assertEquals(Change.ONLY_B, byKey.get("n.added").change());
    }

    @Test
    void differencesAreListedFirst() {
        LogRecord a = rec("    - n: { x: 1, same: k}\n");
        LogRecord b = rec("    - n: { x: 2, same: k}\n");
        List<DiffRow> rows = DiffBuilder.diff(a, b);
        assertTrue(rows.get(0).isDifference(), "a difference sorts before the SAME row");
        assertEquals(Change.SAME, rows.get(rows.size() - 1).change());
    }
}
