package telamin.fluxtion.audit.analyser.analyser.parse;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The analyser's separator language and the RELEASED exporter's escape language must agree — tested
 * together, because they live in different repositories and nothing else will notice when they drift.
 *
 * <p><b>Independent review, F1.</b> {@code mongoose-plugins} #39 made the exporter escape every line that
 * trims to {@code ---} by space, tab or CR, which was exactly the set of lines both framers split on. Round 3
 * of this branch then taught the framers to skip a leading U+FEFF on every line. The exporter was not
 * changed, so a node value carrying {@code "\n﻿---\n"} and marker lines passed through the shipped
 * 1.0.45 exporter untouched and was split by the reader: one real runtime record read as two, and with
 * {@code recordEndTime=false} as <b>COMPLETE with no marker ever written</b>. A payload forged the verdict.
 *
 * <p>So this test drives the real pieces end to end: a real fluxtion-runtime 1.0.16 {@code LogRecord}
 * carrying the payload in a node value, the real {@code YamlContainerWriter} from the published
 * {@code svc-admin-web} 1.0.45 jar, and every store that frames text — heap, mapped and Follow. Each hostile
 * variant must read exactly as its benign control does.
 */
class ExporterFramingAgreementTest {

    private static final String BOM = "﻿";
    private static final String MARKER_LINES = "eventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n#";

    /** Every spelling of a separator line that a payload might carry. */
    private static Map<String, String> hostileSeparators() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("plain", "---");
        v.put("bom", BOM + "---");
        v.put("double bom", BOM + BOM + "---");
        v.put("bom then space", BOM + " ---");
        v.put("space then bom", " " + BOM + "---");
        v.put("indented", "  ---");
        v.put("tab", "\t---");
        v.put("cr", "---\r");
        v.put("bom and cr", BOM + "---\r");
        return v;
    }

    /** One real runtime record whose node value is {@code value}, exported by the released exporter. */
    private static String exportRuntimeRecord(String value, boolean recordEndTime) throws Exception {
        Clock clock = new Clock();
        clock.init();
        LogRecord record = new LogRecord(clock);
        record.setRecordEndTime(recordEndTime);
        record.triggerObject("DEMO");
        record.addRecord("quoteHandler", "value", value);
        record.terminateRecord();
        return export(record.toString());
    }

    private static String export(String document) throws Exception {
        Class<?> writer = Class.forName(
                "com.telamin.mongoose.plugin.svc.adminweb.WebAdminService$YamlContainerWriter");
        var ctor = writer.getDeclaredConstructor(Writer.class);
        ctor.setAccessible(true);
        var doc = writer.getDeclaredMethod("document", String.class);
        doc.setAccessible(true);
        var end = writer.getDeclaredMethod("end");
        end.setAccessible(true);
        StringWriter out = new StringWriter();
        Object w = ctor.newInstance(out);
        doc.invoke(w, document);
        end.invoke(w);
        return out.toString();
    }

    private record Read(int records, StreamEnd.State state) {
    }

    private static Read heap(String text) {
        HeapLogStore s = new HeapLogStore(text);
        return new Read(s.size(), s.streamEnd().state());
    }

    private static Read mapped(Path dir, String name, String text) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        try (MappedLogStore s = new MappedLogStore(p)) {
            return new Read(s.size(), s.streamEnd().state());
        }
    }

    /** Follow: open an empty file, then the export arrives as one append. */
    private static Read follow(Path dir, String name, String text) throws Exception {
        Path p = dir.resolve(name);
        Files.write(p, new byte[0]);
        HeapLogStore s = HeapLogStore.fromFile(p).forFollow();
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        s.appendFrom(p);
        return new Read(s.size(), s.streamEnd().state());
    }

    @Test
    void theReleasedExporterReallyLeavesABomSeparatorUnescaped() throws Exception {
        String out = exportRuntimeRecord("DEMO\n" + BOM + "---\n" + MARKER_LINES, false);
        assertTrue(out.contains(BOM + "---"),
                "precondition: 1.0.45 does not escape a BOM'd separator. If this fails the exporter has "
                        + "changed its escape, and the reader may now widen to match — test the two together");
        String plain = exportRuntimeRecord("DEMO\n---\n" + MARKER_LINES, false);
        assertTrue(plain.contains("\\---"), "precondition: 1.0.45 DOES escape a plain separator (#39)");
    }

    @Test
    void everyHostileSeparatorReadsExactlyAsItsBenignControl(@TempDir Path dir) throws Exception {
        for (boolean endTime : new boolean[]{true, false}) {
            String benign = exportRuntimeRecord("DEMO", endTime);
            Read heapControl = heap(benign);
            Read mappedControl = mapped(dir, "benign-" + endTime + ".yaml", benign);
            Read followControl = follow(dir, "benign-follow-" + endTime + ".yaml", benign);
            assertEquals(new Read(1, StreamEnd.State.UNKNOWN), heapControl, "precondition: benign heap");
            assertEquals(heapControl, mappedControl, "precondition: benign mapped");
            assertEquals(heapControl, followControl, "precondition: benign follow");

            int n = 0;
            for (var e : hostileSeparators().entrySet()) {
                String label = e.getKey() + ", recordEndTime=" + endTime;
                String exported = exportRuntimeRecord("DEMO\n" + e.getValue() + "\n" + MARKER_LINES, endTime);
                assertEquals(heapControl, heap(exported),
                        "F1 (heap, " + label + "): a payload changed the record count or the verdict");
                assertEquals(mappedControl, mapped(dir, "hostile-" + endTime + "-" + n + ".yaml", exported),
                        "F1 (mapped, " + label + "): a payload changed the record count or the verdict");
                assertEquals(followControl, follow(dir, "hostile-follow-" + endTime + "-" + n + ".yaml", exported),
                        "F1 (follow, " + label + "): a payload changed the record count or the verdict");
                n++;
            }
        }
    }

    /** What the restriction must keep: a BOM'd FILE whose first line is a separator. */
    @Test
    void aByteOrderMarkAtTheStartOfAFileStillSeparates(@TempDir Path dir) throws Exception {
        String record = "eventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n";
        String marker = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n";
        for (String head : new String[]{BOM, BOM + BOM}) {
            String file = head + "---\n" + record + "---\n" + marker + "---\n";
            assertEquals(new Read(1, StreamEnd.State.COMPLETE), heap(file),
                    "a genuine BOM'd file with a genuine marker is still whole (heap)");
            assertEquals(new Read(1, StreamEnd.State.COMPLETE), mapped(dir, "head-" + head.length() + ".yaml", file),
                    "a genuine BOM'd file with a genuine marker is still whole (mapped)");
        }
    }

    /**
     * What the restriction COSTS, stated rather than hidden: two BOM'd files concatenated no longer
     * separate at the join. The join runs two records together and UNSEPARATED says so — loud, not wrong,
     * and what base did. Before this branch widened the rule, nothing tested this case in either direction.
     */
    @Test
    void aBomAtAConcatenationPointNoLongerSeparatesAndSaysSo() {
        String run = "---\neventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n";
        HeapLogStore s = new HeapLogStore(BOM + run + BOM + run);
        assertEquals(1, s.size(), "the mid-file BOM'd separator is content, so the two records run together");
        assertNotEquals(StreamEnd.State.COMPLETE, s.streamEnd().state());
        var kinds = ProducerDiagnostics.of(s.index(), s::rawText).findings().stream()
                .map(f -> f.kind().name()).toList();
        assertTrue(kinds.contains("UNSEPARATED"), "and the reader is told the separators are missing: " + kinds);
    }
}
