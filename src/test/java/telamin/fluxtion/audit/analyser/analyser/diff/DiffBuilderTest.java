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
