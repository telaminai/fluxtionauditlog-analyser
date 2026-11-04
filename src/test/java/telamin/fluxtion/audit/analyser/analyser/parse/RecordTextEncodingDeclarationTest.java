package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.export.RecordExporter;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Format-spec §3a: a text record may declare the quoted-scalar grammar ITSELF, with {@code textEncoding: QUOTED_SCALARS}
 * as the first line of its {@code eventLogRecord} mapping. Recognised by position only; every other record reads exactly
 * as before. Fixture C35 pins the mixed file on both paths; this pins the rules one at a time.
 */
class RecordTextEncodingDeclarationTest {

    private static final String GYM =
            "        - gymAccess: { reason: \"membership not active, balance due 30.0\", granted: false}\n";

    private static String record(String firstLines, String nodeLines) {
        return "eventLogRecord:\n" + firstLines
                + "    logTime: 10\n"
                + "    event: MemberEvent\n"
                + "    nodeLogs:\n" + nodeLines
                + "    endTime: 11\n";
    }

    private static String marked(String nodeLines) {
        return record("    textEncoding: QUOTED_SCALARS\n", nodeLines);
    }

    private static NodeLog node(String text) {
        return RecordParser.parse(text, 0).nodeLogs().get(0);
    }

    // ---- recognition ---------------------------------------------------------------------------------------------

    @Test
    void anUnmarkedRecordIsLegacy_exactlyAsBefore() {
        LogRecord r = RecordParser.parse(record("", GYM), 0);
        assertEquals(TextEncoding.LEGACY, r.textEncoding());
        assertFalse(r.recordDeclaredEncoding());
        var reason = r.nodeLogs().get(0).last("reason");
        assertEquals("\"membership not active, balance due 30.0\"", reason.rawValue(),
                "legacy: the quotes protect the comma while scanning and stay in the value (§3)");
        assertFalse(reason.quoted());
        // the bare value the old runtime wrote is cut at its first comma, as it always was
        NodeLog bare = node(record("", "        - gymAccess: { reason: membership not active, balance due 30.0}\n"));
        assertEquals("membership not active", bare.last("reason").rawValue());
        assertEquals(2, bare.entries().size());
    }

    @Test
    void aMarkedRecordDecodes_theGymReasonIsOneValue() {
        LogRecord r = RecordParser.parse(marked(GYM), 0);
        assertEquals(TextEncoding.QUOTED_SCALARS, r.textEncoding());
        assertTrue(r.recordDeclaredEncoding());
        NodeLog gym = r.nodeLogs().get(0);
        assertEquals(2, gym.entries().size(), gym.entries().toString());
        assertEquals("membership not active, balance due 30.0", gym.last("reason").rawValue());
        assertTrue(gym.last("reason").quoted());
        assertTrue(gym.last("reason").numeric().isEmpty());
        assertEquals(Boolean.FALSE, gym.last("granted").asBoolean());
        assertEquals(10L, r.logTime());
        assertEquals("MemberEvent", r.event());
        assertEquals(11L, r.endTime());
        assertEquals(1, r.nodeLogsCount());
    }

    @Test
    void quotedNumbersAndFlagsStayText_bareOnesStayTyped() {
        NodeLog n = node(marked("        - n: { code: \"123\", flag: \"true\", nil: \"null\", count: 123, on: true}\n"));
        assertTrue(n.last("code").numeric().isEmpty(), "\"123\" is text");
        assertEquals("123", n.last("code").rawValue());
        assertNull(n.last("flag").asBoolean(), "\"true\" is text");
        assertFalse(n.last("nil").isNull(), "\"null\" is text");
        assertEquals(123.0, n.last("count").numeric().getAsDouble(), 0);
        assertEquals(Boolean.TRUE, n.last("on").asBoolean());
    }

    @Test
    void theMarkerIsRecognisedOnlyAsTheFirstLineOfTheMapping() {
        // blank lines, surrounding whitespace and a byte-order mark are stripped as §1a strips them
        assertEquals(TextEncoding.QUOTED_SCALARS,
                RecordParser.parse(record("\n    textEncoding: QUOTED_SCALARS   \r\n", GYM), 0).textEncoding());
        assertEquals(TextEncoding.QUOTED_SCALARS,
                RecordParser.parse("\uFEFF" + marked(GYM), 0).textEncoding(), "a BOM before the record key");
        assertEquals(TextEncoding.QUOTED_SCALARS,
                RecordParser.parse("#10:00:00.000 [main] INFO DEMO\n" + marked(GYM), 0).textEncoding(),
                "the header comment precedes the record key");

        // anywhere else it is content, and the record is legacy
        String afterLogTime = "eventLogRecord:\n    logTime: 10\n    textEncoding: QUOTED_SCALARS\n    nodeLogs:\n" + GYM;
        String afterEndTime = record("", GYM) + "    textEncoding: QUOTED_SCALARS\n";
        String afterComment = record("    # note\n    textEncoding: QUOTED_SCALARS\n", GYM);
        String inNodeValue = record("", "        - n: { note: \"x\n    textEncoding: QUOTED_SCALARS\n    y\"}\n");
        String inEventToString = "eventLogRecord:\n    logTime: 10\n    eventToString: \"admin\n    textEncoding: "
                + "QUOTED_SCALARS\n    end\"\n    nodeLogs:\n" + GYM;
        String noRecordKey = "    textEncoding: QUOTED_SCALARS\n    logTime: 10\n    nodeLogs:\n" + GYM;
        String nested = record("    other:\n        textEncoding: QUOTED_SCALARS\n", GYM);
        for (String text : List.of(afterLogTime, afterEndTime, afterComment, inNodeValue, inEventToString,
                noRecordKey, nested)) {
            LogRecord r = RecordParser.parse(text, 0);
            assertEquals(TextEncoding.LEGACY, r.textEncoding(), text);
            assertFalse(r.recordDeclaredEncoding(), text);
            assertNull(RecordParser.declaration(text), text);
        }
        assertEquals("admin", RecordParser.parse(inEventToString, 0).eventToString().replace("\"", ""),
                "the forged line stayed inside the legacy value");
    }

    @Test
    void anUnknownValueIsLegacy_andNamedOnceForTheLog() {
        String unknown = record("    textEncoding: QUOTED_SCALARS_V2\n", GYM);
        String lower = record("    textEncoding: quoted_scalars\n", GYM);
        LogRecord r = RecordParser.parse(unknown, 0);
        assertEquals(TextEncoding.LEGACY, r.textEncoding());
        assertFalse(r.recordDeclaredEncoding());
        assertEquals(telamin.fluxtion.audit.analyser.analyser.model.EventKind.OK, r.kind(), "never fails the record");
        assertEquals(new RecordParser.Declaration(2, "QUOTED_SCALARS_V2"), RecordParser.declaration(unknown));

        HeapLogStore store = new HeapLogStore("---\n" + record("", GYM) + "---\n" + unknown + "---\n" + marked(GYM)
                + "---\n" + lower + "---\n");
        var findings = ProducerDiagnostics.of(store.index(), store::rawText).findings().stream()
                .filter(f -> f.kind() == ProducerDiagnostics.Kind.UNKNOWN_TEXT_ENCODING).toList();
        assertEquals(1, findings.size(), "one finding for the log, however many records");
        assertTrue(findings.get(0).message().startsWith(
                "2 records declare, the first at 2, 'textEncoding: QUOTED_SCALARS_V2'"), findings.get(0).message());

        HeapLogStore clean = new HeapLogStore("---\n" + record("", GYM) + "---\n" + marked(GYM) + "---\n");
        assertTrue(ProducerDiagnostics.of(clean.index(), clean::rawText).findings().stream()
                .noneMatch(f -> f.kind() == ProducerDiagnostics.Kind.UNKNOWN_TEXT_ENCODING));
    }

    @Test
    void theMarkerIsMetadata_notAFieldNotAnEntryAndItMovesNoOffset() {
        String text = marked(GYM);
        LogRecord r = RecordParser.parse(text, 42);
        assertEquals(42, r.fileOffset());
        assertEquals(text, r.rawText(), "the record's text is kept whole");
        assertTrue(r.nodeLogs().stream().flatMap(n -> n.entries().stream())
                .noneMatch(kv -> "textEncoding".equals(kv.key()) || "QUOTED_SCALARS".equals(kv.rawValue())));
        assertTrue(r.nodeLogs().stream().noneMatch(n -> n.instanceId().contains("textEncoding")));
        assertEquals(0, r.brokenAtLine());
    }

    @Test
    void aBrokenMarkedRecordStillReadsItsFieldsBeforeTheBreak() {
        // the declaration carries no payload, so it does not trigger the payload cap on a broken record
        String text = "eventLogRecord:\n    textEncoding: QUOTED_SCALARS\n    logTime: 10\n    event: E\n"
                + "  less indented\n    nodeLogs:\n" + GYM;
        LogRecord r = RecordParser.parse(text, 0);
        assertTrue(r.brokenAtLine() > 0);
        assertEquals(10L, r.logTime());
        assertEquals("E", r.event());
        assertEquals(TextEncoding.QUOTED_SCALARS, r.textEncoding());
    }

    @Test
    void aReaderDeclarationIsNeverOverridden_andItsHeaderScalarsAreRawAsBefore() {
        String text = "eventLogRecord:\n    logTime: 10\n    eventToString: \"abc\"\n    nodeLogs:\n"
                + "        - n: { v: \"a\\u0041\"}\n";
        LogRecord binaryLike = RecordParser.parse(text, 0, TextEncoding.QUOTED_SCALARS);
        assertEquals(TextEncoding.QUOTED_SCALARS, binaryLike.textEncoding());
        assertFalse(binaryLike.recordDeclaredEncoding());
        assertEquals("\"abc\"", binaryLike.eventToString(), "header scalars decode only under the record's own marker");
        assertEquals("aA", binaryLike.nodeLogs().get(0).last("v").rawValue(), "the new escapes on the reader path too");
        // and a marker the reader's text happens to carry changes nothing about the reader's declaration
        LogRecord markedUnderReader = RecordParser.parse(marked(GYM), 0, TextEncoding.QUOTED_SCALARS);
        assertFalse(markedUnderReader.recordDeclaredEncoding());
    }

    // ---- header scalars ------------------------------------------------------------------------------------------

    @Test
    void underTheMarkerAHeaderValueThatIsOneQuotedScalarDecodes() {
        String text = "eventLogRecord:\n    textEncoding: QUOTED_SCALARS\n    logTime: 10\n"
                + "    groupingId: \"null\"\n    event: \"Member\\x22Event\"\n"
                + "    eventToString: \"MemberEvent{id=7, note: \\x22vip\\x22, tab:\\tx}\"\n"
                + "    thread: \"pool-1, worker \\u00e9\"\n    nodeLogs:\n" + GYM;
        LogRecord r = RecordParser.parse(text, 0);
        assertEquals("null", r.groupingId(), "a quoted \"null\" is the text null, not absent");
        assertEquals("Member\"Event", r.event());
        assertEquals("MemberEvent{id=7, note: \"vip\", tab:\tx}", r.eventToString());
        assertEquals("pool-1, worker \u00e9", r.thread());

        // a bare value under the marker is the raw text it always was; a bare null is still absent
        LogRecord bare = RecordParser.parse(record("    textEncoding: QUOTED_SCALARS\n    groupingId: null\n"
                + "    thread: main\n", GYM), 0);
        assertNull(bare.groupingId());
        assertEquals("main", bare.thread());
    }

    @Test
    void withoutTheMarkerHeaderValuesKeepTheirQuotes() {
        String text = "eventLogRecord:\n    logTime: 10\n    groupingId: \"g\"\n    eventToString: \"x\\x22y\"\n"
                + "    thread: \"main\"\n    nodeLogs:\n" + GYM;
        LogRecord r = RecordParser.parse(text, 0);
        assertEquals("\"g\"", r.groupingId());
        assertEquals("\"x\\x22y\"", r.eventToString());
        assertEquals("\"main\"", r.thread());
    }

    // ---- escapes -------------------------------------------------------------------------------------------------

    private static NodeLogTokenizer.Scalar un(String s) {
        return NodeLogTokenizer.unquote(s);
    }

    @Test
    void everyDeclaredEscapeDecodes() {
        assertEquals("a\\b\"c\nd\re\tf\0g", un("\"a\\\\b\\\"c\\nd\\re\\tf\\0g\"").text());
        assertEquals("say \"hi\"", un("\"say \\x22hi\\x22\"").text(), "\\x22 is the writer's quote");
        assertEquals("say \"hi\"", un("\"say \\\"hi\\\"\"").text(), "and \\\" is accepted too");
        assertEquals("caf\u00e9", un("\"caf\\u00e9\"").text());
        assertEquals("caf\u00e9", un("\"caf\\u00E9\"").text(), "hex digits in either case");
        assertEquals("caf\u00e9", un("\"caf\u00e9\"").text(), "a literal non-ASCII character is itself");
        assertEquals("\uD83D\uDE00!", un("\"\\uD83D\\uDE00!\"").text(), "consecutive surrogate escapes combine");
        assertEquals(1, un("\"\\uD83D\\uDE00\"").text().codePointCount(0, 2));
        assertEquals("\u00ff\u0001", un("\"\\xff\\x01\"").text());
        assertTrue(un("\"\\0\"").quoted());
    }

    @Test
    void malformedEscapesAreKeptVerbatim_neverFatal() {
        assertEquals("\\q", un("\"\\q\"").text(), "unknown escape");
        assertEquals("\\x2", un("\"\\x2\"").text(), "too few hex digits before the closing quote");
        assertEquals("\\xZZ", un("\"\\xZZ\"").text());
        assertEquals("\\u12G4", un("\"\\u12G4\"").text());
        assertEquals("\\u00e", un("\"\\u00e\"").text(), "truncated");
        assertEquals("\\u\u0661\u0662\u0663\u0664", un("\"\\u\u0661\u0662\u0663\u0664\"").text(),
                "only ASCII hex digits count");
        assertEquals("\\uD83Dx", un("\"\\uD83Dx\"").text(), "a lone high surrogate is not a character");
        assertEquals("\\uDE00", un("\"\\uDE00\"").text(), "nor a lone low one");
        assertEquals("\\uD83DA", un("\"\\uD83D\\u0041\"").text(),
                "a high surrogate followed by a non-surrogate escape stays verbatim; the next escape still decodes");
        assertTrue(un("\"\\u12G4\"").quoted(), "the scalar is still one quoted string");
        // a dangling backslash before the closing quote is not one quoted scalar: the raw text, as before
        assertEquals("\"abc\\\"", un("\"abc\\\"").text());
        assertFalse(un("\"abc\\\"").quoted());

        // and in a record: nothing fails, the neighbours are read
        NodeLog n = node(marked("        - n: { bad: \"x\\q\\u12G4y\", good: \"\\u00e9\", price: 5}\n"));
        assertEquals("x\\q\\u12G4y", n.last("bad").rawValue());
        assertEquals("\u00e9", n.last("good").rawValue());
        assertEquals(5.0, n.last("price").numeric().getAsDouble(), 0);
    }

    // ---- mixed files, follow, export, read -----------------------------------------------------------------------

    private static final String MIXED = "---\n" + record("", GYM) + "---\n" + marked(GYM) + "---\n";

    @Test
    void aMixedFileReadsEachRecordByItsOwnDeclaration_onEveryStore(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("mixed.yaml");
        Files.writeString(f, MIXED, StandardCharsets.UTF_8);
        try (MappedLogStore mapped = new MappedLogStore(f)) {
            for (LogStore s : List.of(new HeapLogStore(MIXED), HeapLogStore.fromFile(f), mapped)) {
                assertEquals(2, s.size());
                assertEquals(TextEncoding.LEGACY, s.textEncoding(), "the store's reader declares nothing");
                assertEquals(TextEncoding.LEGACY, s.record(0).textEncoding());
                assertEquals(TextEncoding.QUOTED_SCALARS, s.record(1).textEncoding());
                assertEquals("membership not active, balance due 30.0",
                        s.record(1).nodeLogs().get(0).last("reason").rawValue());
            }
        }
    }

    @Test
    void followReEvaluatesEveryAppendedRecord(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("live.yaml");
        Files.writeString(f, "---\n" + record("", GYM) + "---\n", StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(f).forFollow();
        assertEquals(1, store.size());
        // the producer is upgraded mid-capture: a marked record, then an unmarked one again
        Files.writeString(f, marked(GYM) + "---\n" + record("", GYM) + "---\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        assertEquals(2, store.appendFrom(f));
        assertEquals(List.of(TextEncoding.LEGACY, TextEncoding.QUOTED_SCALARS, TextEncoding.LEGACY),
                List.of(store.record(0).textEncoding(), store.record(1).textEncoding(), store.record(2).textEncoding()));
        assertEquals("membership not active, balance due 30.0",
                store.record(1).nodeLogs().get(0).last("reason").rawValue());
        assertFalse(store.record(2).nodeLogs().get(0).last("reason").quoted(), "legacy again: nothing decoded");
    }

    @Test
    void aYamlExportOfAMixedTextLogReloadsAsTheSameRecords() {
        HeapLogStore s = new HeapLogStore(MIXED);
        assertNull(RecordExporter.yamlRefusal(s), "the marker travels in each record's own text");
        HeapLogStore again = new HeapLogStore(RecordExporter.toYaml(s, new FilterState()));
        assertEquals(s.size(), again.size());
        for (int i = 0; i < s.size(); i++) {
            assertEquals(s.record(i).textEncoding(), again.record(i).textEncoding());
            assertEquals(s.record(i).nodeLogs().toString(), again.record(i).nodeLogs().toString());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void readProjectsTheDecodedValue() {
        HeapLogStore s = new HeapLogStore(MIXED);
        var records = (List<Map<String, Object>>) telamin.fluxtion.audit.analyser.analyser.llm.ReadService
                .read(s.index().snapshot(), Map.of("recordIndex", 0, "count", 2,
                        "fields", List.of("gymAccess.reason")), s::rawText, s::record)
                .get("records");
        assertEquals(Map.of("gymAccess.reason", "\"membership not active, balance due 30.0\""),
                records.get(0).get("values"));
        assertEquals(Map.of("gymAccess.reason", "membership not active, balance due 30.0"), records.get(1).get("values"));
    }
}
