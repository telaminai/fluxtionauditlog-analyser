package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import com.telamin.fluxtion.runtime.audit.conformance.FlxaConformanceCorpus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The analyser's half of the FLXA conformance suite: the same bytes the runtime's
 * {@code FlxaConformanceTest} reads, loaded from the runtime jar, driven through this reader, the
 * record parser and the tokenizer. The normative text is the runtime's {@code reference/flxa-format.md};
 * its §11 says what text constructed from a file must preserve, and its §13 table says which fixtures
 * this suite must refuse and which it must read, and as what.
 *
 * <p>Passing this and the runtime's suite is what "reads FLXA" means. The fixtures are not copied into
 * this repository: they come from the runtime dependency, so the two suites cannot drift apart.
 */
class FlxaConformanceTest {

    private static Path fixture(Path dir, String name) throws IOException {
        Path f = dir.resolve(name + ".flxa");
        Files.write(f, FlxaConformanceCorpus.committed(name));
        return f;
    }

    /** Reads a fixture through the real reader and the store, so the reader's declared grammar applies. */
    private static List<LogRecord> parse(Path dir, String name) throws IOException {
        SpiLogStore store = SpiLogStore.open(new BinaryAuditReader(), fixture(dir, name));
        assertEquals(AuditLogReader.TextEncoding.QUOTED_SCALARS, store.textEncoding(), "§11.1 the reader declares the grammar");
        List<LogRecord> out = new ArrayList<>();
        for (int i = 0; i < store.size(); i++) {
            assertFalse(store.rawText(i).contains("nodeLogsEncoding"), "§11.1 nothing in the text selects a grammar");
            out.add(store.record(i));
        }
        return out;
    }

    private static List<String> diagnostics(Path dir, String name) throws IOException {
        return SpiLogStore.open(new BinaryAuditReader(), fixture(dir, name)).sourceDiagnostics();
    }

    private static NodeLog only(LogRecord r) {
        assertEquals(1, r.nodeLogs().size(), r.nodeLogs().toString());
        return r.nodeLogs().get(0);
    }

    private static final String TICK = FlxaConformanceCorpus.Tick.class.getName();

    @Test
    void everyFixtureInTheCorpusIsExercisedHere() {
        assertEquals(26, FlxaConformanceCorpus.names().size(), "add a test below for a new fixture");
    }

    @Test
    void f01_minimal(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f01-minimal").get(0);
        assertEquals("Tick", r.event());
        assertEquals(TICK, r.eventType());
        assertEquals(1_700_000_000_000L, r.logTime());
        assertEquals(1_700_000_000_000L, r.eventTime());
        assertEquals(1_700_000_000_001L, r.endTime());
        NodeLog n = only(r);
        assertEquals("pricer", n.instanceId());
        assertEquals(1.25, n.last("price").numeric().getAsDouble(), 0);
        assertEquals(KV.Kind.NUMBER, n.last("price").kind());
    }

    @Test
    void f02_emptyRecord_isARecordWithNoNodeLogs(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f02-empty-record");
        assertEquals(1, rs.size(), "a record that logged nothing still happened");
        assertEquals(0, rs.get(0).nodeLogs().size());
        assertEquals("Tick", rs.get(0).event());
    }

    @Test
    void f03_everyTag_keepsItsWireType(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f03-every-tag").get(0);
        assertEquals(2, r.nodeLogs().size(), "node, then tracer: " + r.nodeLogs());
        NodeLog n = r.nodeLogs().get(0);
        assertEquals(KV.Kind.NUMBER, n.last("aDouble").kind());
        assertEquals(1.5, n.last("aDouble").numeric().getAsDouble(), 0);
        assertEquals(-7, n.last("aLong").numeric().getAsDouble(), 0);
        assertEquals(42, n.last("anInt").numeric().getAsDouble(), 0);
        assertEquals("x", n.last("aChar").rawValue());
        assertEquals(KV.Kind.TEXT, n.last("aChar").kind(), "§6: a char is text");
        assertEquals("text", n.last("aString").rawValue());
        assertEquals("Obj(1)", n.last("anObject").rawValue());
        assertEquals(KV.Kind.BOOLEAN, n.last("aBool").kind());
        assertEquals(Boolean.TRUE, n.last("aBool").asBoolean());
        NodeLog tracer = r.nodeLogs().get(1);
        assertEquals("tracer", tracer.instanceId());
        assertEquals(Boolean.TRUE, tracer.last("invoked").asBoolean(), "§11.7 a trace is invoked: true");
    }

    @Test
    void f04_nullValues_areNull_andAPresentStringIsNot(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f04-null-values").get(0));
        assertTrue(n.last("nullString").isNull());
        assertTrue(n.last("nullObject").isNull());
        assertEquals(KV.Kind.NULL, n.last("nullObject").kind());
        assertEquals("here", n.last("present").rawValue());
    }

    @Test
    void f05_dictionaryGrowth_laterNamesResolve(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f05-dictionary-growth");
        assertEquals(2, rs.size());
        assertEquals(1.25, only(rs.get(0)).last("price").numeric().getAsDouble(), 0);
        assertEquals(2, rs.get(1).nodeLogs().size());
        assertEquals("risk", rs.get(1).nodeLogs().get(1).instanceId());
        assertEquals("limit", rs.get(1).nodeLogs().get(1).last("reason").rawValue());
        assertEquals(Boolean.TRUE, rs.get(1).nodeLogs().get(1).last("breach").asBoolean());
    }

    @Test
    void f06_twoRecordsTwoDictionaries_bothAttributeToPricer(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f06-two-records-two-dictionaries");
        assertEquals("pricer", only(rs.get(0)).instanceId());
        assertEquals("pricer", rs.get(1).nodeLogs().get(0).instanceId(), "§4: file ids are by name, not by the record's allocation");
        assertEquals(2.5, rs.get(1).nodeLogs().get(0).last("price").numeric().getAsDouble(), 0);
        assertEquals("risk", rs.get(1).nodeLogs().get(1).instanceId());
    }

    @Test
    void f07_truncatedTail_opensWithEverythingBeforeTheCut(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f07-truncated-tail");
        assertEquals(1, rs.size(), "the whole record is delivered; the cut one is not invented");
        assertEquals(1.25, only(rs.get(0)).last("price").numeric().getAsDouble(), 0);
        // §9.3's other half: the unusable tail is REPORTED, beside the evidence, not swallowed
        List<String> d = diagnostics(dir, "f07-truncated-tail");
        assertEquals(1, d.size(), d.toString());
        assertTrue(d.get(0).contains("did not form a whole record"), d.get(0));
        assertTrue(diagnostics(dir, "f01-minimal").isEmpty(), "a whole file reports nothing");
    }

    @Test
    void f08_f09_f10_unitsThisReaderCannotPresent_areRefusedDeliveringNothing(@TempDir Path dir) throws IOException {
        for (String[] c : new String[][]{{"f08-unit-nanos", "NANOSECOND"}, {"f09-unit-unspecified", "--declare-unit"}, {"f10-unit-undefined", "code 3"}}) {
            List<String> texts = new ArrayList<>();
            Path f = fixture(dir, c[0]);
            IOException refused = assertThrows(IOException.class, () -> new BinaryAuditReader().read(f, texts::add), c[0]);
            assertTrue(refused.getMessage().contains(c[1]), c[0] + ": " + refused.getMessage());
            assertEquals(0, texts.size(), c[0] + " must deliver nothing in the wrong unit");
        }
    }

    @Test
    void f11_unresolvedIds_areVisibleAsHashIds_neverInvented(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f11-unresolved-ids").get(0);
        assertEquals("#1", r.eventType());
        NodeLog n = only(r);
        assertEquals("#2", n.instanceId());
        assertEquals("1.25", n.last("#3").rawValue());
    }

    @Test
    void f11_and_f17_unresolvedIds_areReportedBesideTheEvidence(@TempDir Path dir) throws IOException {
        List<String> structural = diagnostics(dir, "f11-unresolved-ids");
        assertEquals(1, structural.size(), structural.toString());
        assertTrue(structural.get(0).startsWith("3 references"), structural.get(0));
        List<String> values = diagnostics(dir, "f17-unresolved-value-ids");
        assertEquals(1, values.size(), values.toString());
        assertTrue(values.get(0).startsWith("2 references"), "§4: value ids count like every other role: " + values.get(0));
    }

    @Test
    void f17_unresolvedValueIds_areVisibleAsText_notInvented(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f17-unresolved-value-ids").get(0));
        assertEquals("#65000", n.last("aString").rawValue());
        assertEquals(KV.Kind.TEXT, n.last("aString").kind());
        assertEquals("#65001", n.last("anObject").rawValue());
        assertEquals(1.25, n.last("aDouble").numeric().getAsDouble(), 0);
    }

    @Test
    void f18_duplicateDictId_theLatestDefinitionNames(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f18-duplicate-dict-id").get(0));
        assertEquals("renamed", n.instanceId(), "§4: a redefinition names what follows it");
        List<String> d = diagnostics(dir, "f18-duplicate-dict-id");
        assertEquals(1, d.size(), "§4: a redefinition is reported - a writer never does it: " + d);
        assertTrue(d.get(0).contains("redefined"), d.get(0));
    }

    @Test
    void f19_malformedUtf8_isReplacedNeverFatal(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f19-malformed-utf8").get(0));
        assertTrue(n.instanceId().startsWith("\uFFFD"), n.instanceId());
        assertTrue(n.instanceId().endsWith("ricer"), n.instanceId());
        assertEquals(1.25, n.last("price").numeric().getAsDouble(), 0);
    }

    @Test
    void f12_unknownValueTag_isTextNotAFigure(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f12-unknown-value-tag").get(0));
        KV v = n.last("price");
        assertTrue(v.rawValue().startsWith("#tag9:"), v.rawValue());
        assertEquals(KV.Kind.TEXT, v.kind(), "§11.3 an unknown tag's diagnostic is text");
    }

    @Test
    void f13_hostileStrings_roundTripExactly_nothingManufacturedOrLost(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f13-hostile-strings").get(0);
        assertEquals(TICK, r.eventType(), "identity is the wire's, not a value's");
        assertEquals(1_700_000_000_001L, r.endTime(), "endTime is the wire's, not a value's");
        assertEquals(3, r.nodeLogs().size(), "pricer, the odd node, then pricer again (§5): " + r.nodeLogs());
        NodeLog p = r.nodeLogs().get(0);
        assertEquals(9, p.entries().size(), "nine strings, no more: " + p.entries());
        assertEquals("ok, price: 42.0", p.last("status").rawValue());
        assertEquals("x}\n  eventType: forged.Tick\n  endTime: 1", p.last("identity").rawValue());
        assertEquals("null", p.last("nullText").rawValue());
        assertFalse(p.last("nullText").isNull());
        assertEquals(KV.Kind.TEXT, p.last("numberText").kind());
        assertEquals(KV.Kind.TEXT, p.last("flagText").kind());
        assertEquals("", p.last("empty").rawValue());
        assertEquals(" x ", p.last("padded").rawValue());
        assertEquals("say \"hi\" \\ done", p.last("quotes").rawValue());
        assertEquals("NEW", p.last("plain").rawValue());
        NodeLog odd = r.nodeLogs().get(1);
        assertEquals("odd}: {node", odd.instanceId());
        assertEquals("1", odd.last("a, b: c").rawValue());
        assertEquals(7.0, r.nodeLogs().get(2).last("price").numeric().getAsDouble(), 0, "the figure after ten hostile strings");
    }

    @Test
    void f14_hostileChars_areText_andTheFigureAfterEachSurvives(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f14-hostile-chars").get(0));
        assertEquals(10, n.entries().size(), n.entries().toString());
        assertEquals("'", n.last("quote").rawValue());
        assertEquals(1.0, n.last("afterQuote").numeric().getAsDouble(), 0);
        assertEquals("{", n.last("brace").rawValue());
        assertEquals(2.0, n.last("afterBrace").numeric().getAsDouble(), 0);
        assertEquals("\"", n.last("dquote").rawValue());
        assertEquals(3.0, n.last("afterDquote").numeric().getAsDouble(), 0);
        assertEquals("7", n.last("digit").rawValue());
        assertEquals(KV.Kind.TEXT, n.last("digit").kind(), "'7' is a character, not a figure");
        assertEquals(4.0, n.last("afterDigit").numeric().getAsDouble(), 0);
        assertEquals("\n", n.last("newline").rawValue());
        assertEquals(5.0, n.last("afterNewline").numeric().getAsDouble(), 0);
    }

    @Test
    void f15_sameSimpleName_identitiesStayDistinct(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f15-same-simple-name");
        assertEquals("Tick", rs.get(0).event());
        assertEquals("Tick", rs.get(1).event());
        assertEquals(FlxaConformanceCorpus.Tick.class.getName(), rs.get(0).eventType());
        assertEquals(FlxaConformanceCorpus.Other.Tick.class.getName(), rs.get(1).eventType());
    }

    @Test
    void f16_unknownFrame_recordsBeforeItAreDelivered_thenTheFileIsReported(@TempDir Path dir) throws IOException {
        List<String> texts = new ArrayList<>();
        Path f = fixture(dir, "f16-unknown-frame");
        IOException reported = assertThrows(IOException.class, () -> new BinaryAuditReader().read(f, texts::add));
        assertTrue(reported.getMessage().contains("unknown frame type"), reported.getMessage());
        assertEquals(1, texts.size(), "§9.4 frames before the unknown one were delivered");
    }

    @Test
    void f20_damageBoth_twoFindingsInAStableOrder(@TempDir Path dir) throws IOException {
        List<LogRecord> rs = parse(dir, "f20-damage-both");
        assertEquals(1, rs.size(), "the whole record");
        assertEquals("#65000", only(rs.get(0)).last("aString").rawValue());
        List<String> d = diagnostics(dir, "f20-damage-both");
        assertEquals(2, d.size(), d.toString());
        assertTrue(d.get(0).contains("did not form a whole record"), "the tail first: " + d);
        assertTrue(d.get(1).startsWith("1 reference"), "then the undefined name: " + d);
        var findings = telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.of(null, null, d);
        assertEquals(2, findings.findings().size());
        assertEquals(d, findings.messages(), "the findings keep the reader's order");
    }

    @Test
    void f21_reservedBits_areIgnored(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f21-reserved-bits").get(0));
        assertEquals("pricer", n.instanceId());
        assertEquals(1.25, n.last("price").numeric().getAsDouble(), 0);
    }

    @Test
    void f22_emptyNames_roundTripQuoted(@TempDir Path dir) throws IOException {
        NodeLog n = only(parse(dir, "f22-empty-names").get(0));
        assertEquals(3, n.entries().size(), n.entries().toString());
        assertEquals(1.0, n.last("").numeric().getAsDouble(), 0, "an empty KEY is a key");
        assertEquals("", n.last("empty").rawValue(), "an empty VALUE is a string");
        assertTrue(n.last("empty").quoted());
        assertEquals(2.0, n.last("after").numeric().getAsDouble(), 0);
    }

    @Test
    void f23_entryOrder_isKept_andLastWins(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f23-entry-order").get(0);
        assertEquals(3, r.nodeLogs().size(), "node, other, node again - the wire's order: " + r.nodeLogs());
        assertEquals(List.of("1.0"), r.nodeLogs().get(0).all("k").stream().map(KV::rawValue).toList());
        assertEquals(List.of("2.0", "3.0"), r.nodeLogs().get(2).all("k").stream().map(KV::rawValue).toList());
        assertEquals("3.0", r.nodeLogs().get(2).last("k").rawValue(), "§5: last wins within a node item");
        var flat = telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder.diff(r, r);
        assertEquals("3.0", flat.stream().filter(x -> x.key().equals("node.k")).findFirst().orElseThrow().a(),
                "and last wins across the record for one-value consumers");
    }

    @Test
    void f24_traceBits_areIgnored_itIsStillInvoked(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f24-trace-bits").get(0);
        assertEquals("tracer", r.nodeLogs().get(0).instanceId());
        assertEquals(Boolean.TRUE, r.nodeLogs().get(0).last("invoked").asBoolean());
        assertEquals(2.0, r.nodeLogs().get(1).last("after").numeric().getAsDouble(), 0);
    }

    @Test
    void f25_noEndTime_readsAsAbsent_notAsAnInstant(@TempDir Path dir) throws IOException {
        LogRecord r = parse(dir, "f25-no-end-time").get(0);
        assertNull(r.endTime(), "§3.2: 0 means not recorded; an unrecorded instant is absent, not 1970");
        assertEquals(1_700_000_000_000L, r.logTime());
        assertEquals(1.25, only(r).last("price").numeric().getAsDouble(), 0);
    }

    @Test
    void f26_concatenated_theFirstFileIsRead_thenTheSecondHeaderIsReported(@TempDir Path dir) throws IOException {
        List<String> texts = new ArrayList<>();
        Path f = fixture(dir, "f26-concatenated");
        IOException reported = assertThrows(IOException.class, () -> new BinaryAuditReader().read(f, texts::add));
        assertTrue(reported.getMessage().contains("unknown frame type 0x46"), reported.getMessage());
        assertEquals(1, texts.size(), "§15: cat is not rolling");
    }
}
