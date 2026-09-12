package telamin.fluxtion.audit.analyser.analyser.report;

import com.telamin.fluxtion.runtime.audit.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.BinaryLogWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEWER PROBE (round 10, R10-1). A report table re-parsed each record's TEXT under the legacy
 * grammar instead of taking the store's parsed record, so binary evidence lost its declared encoding
 * at exactly one consumer: a quoted business key {@code "@invoked": 123} became the trace marker's
 * {@code true}, {@code "null": 99} kept its quotes and vanished from the {@code n.null} column, and a
 * string value split on its own comma. The dispatcher's {@code read} was already right; the report
 * assembly must agree with it, because both answer the same call.
 */
class ReportBinaryEvidenceTest {

    public static final class Tick { }

    private static LogStore store(Path dir) throws Exception {
        Path p = dir.resolve("evidence.flxa");
        try (OutputStream out = Files.newOutputStream(p); BinaryLogWriter w = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord r = new BinaryLogRecord(clock);
            r.triggerObject(new Tick());
            r.addRecord("n", (String) null, 42);                         // keyless evidence
            r.addRecord("n", "null", 99);                                // a business key spelled null
            r.addRecord("n", "@invoked", 123);                           // a business key spelled like the marker
            r.addTrace("n");                                             // the real trace: metadata, not an entry
            r.addRecord("n", "text", (CharSequence) "ok, price: 7");     // a value with the legacy separators inside
            w.processLogRecord(r);
        }
        return SpiLogStore.open(new BinaryAuditReader(), p);
    }

    private static final String FIELDS = "n.null, n.@invoked, n.@unkeyed, n.text";

    private static Map<String, String> tableCells(LogStore store) {
        var section = ReportSpec.SectionSpec.table(
                Map.of("verb", "read", "fields", FIELDS, "recordIndex", "0", "count", "1"),
                List.of(), null, null);
        var a = ReportVerb.assembleTable(section, store);
        assertEquals(1, a.table().rows().size(), "one record, one row: " + a.notes());
        var cols = a.table().columns();
        var row = a.table().rows().get(0);
        Map<String, String> cells = new java.util.LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i++) cells.put(cols.get(i).key(), row.get(i));
        return cells;
    }

    @Test
    void aReportTableReadsTheStoresParsedRecord_neverAReparseOfItsText(@TempDir Path dir) throws Exception {
        Map<String, String> cells = tableCells(store(dir));
        assertEquals("99", cells.get("n.null"), "the business key spelled null: " + cells);
        assertEquals("123", cells.get("n.@invoked"), "the business key spelled like the marker keeps its value: " + cells);
        assertEquals("", cells.get("n.@unkeyed"), "keyless evidence has no name and no column value: " + cells);
        assertEquals("ok, price: 7", cells.get("n.text"), "a string value is not split on its own separators: " + cells);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theReportAndTheDispatcherAnswerTheSameCallTheSameWay(@TempDir Path dir) throws Exception {
        LogStore store = store(dir);
        ActionDispatcher d = new ActionDispatcher(false, null, () -> store.index().snapshot(),
                store::rawText, store::record, null);
        ActionResult r = d.dispatch(Map.of("action", "read", "params",
                Map.of("fields", List.of(FIELDS.split("\\s*,\\s*")), "recordIndex", 0, "count", 1)));
        assertTrue(r.ok(), r.error());
        var records = (List<Map<String, Object>>) r.payload().get("records");   // the payload IS the read result
        var values = (Map<String, Object>) records.get(0).get("values");

        Map<String, String> cells = tableCells(store);
        for (String f : List.of("n.null", "n.@invoked", "n.text")) {
            assertEquals(values.get(f), cells.get(f), f + ": the table and the dispatcher disagree");
        }
        assertFalse(values.containsKey("n.@unkeyed"), "the dispatcher projects no keyless value either: " + values);
    }
}
