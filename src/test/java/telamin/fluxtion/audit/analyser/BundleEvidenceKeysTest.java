package telamin.fluxtion.audit.analyser;

import com.telamin.fluxtion.runtime.audit.AuditStreamHeader;
import com.telamin.fluxtion.runtime.audit.conformance.FlxaConformanceCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.bundle.BundleExcerpt;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;
import telamin.fluxtion.audit.analyser.bundle.EvidenceKeys;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key: a bundle records the GRAPH KEY and RUN KEY of every log and graph it carries, read from the
 * members' own bytes, and {@code --verify} shows them (spec §4.5 "Bundles record the graph key and run key of every
 * file"). A manifest without them is byte for byte what it was.
 */
class BundleEvidenceKeysTest {

    static final String SHAPE = "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e";

    static String header(String fingerprint, String runId) {
        Map<String, String> host = new LinkedHashMap<>();
        host.put("serverId", "pricing-1");
        host.put("runId", runId);
        host.put("group", "pricing");
        host.put("name", "quoter");
        host.put("generation", "1");
        return new AuditStreamHeader("com.acme.TradeProcessor", fingerprint, "1.0.84", "1.1.4",
                "2026-11-03T09:15:02.118Z", host) + "\n";
    }

    static String record(long t) {
        return "eventLogRecord:\n    logTime: " + t + "\n    event: Tick\n    nodeLogs:\n    - book: { mid: 1.5}\n";
    }

    private static Path payload(Path tmp, String log) throws Exception {
        Path folder = tmp.resolve("payload");
        Files.createDirectories(folder.resolve("log"));
        Files.createDirectories(folder.resolve("graph"));
        Files.writeString(folder.resolve("log/audit.yaml"), log);
        Files.copy(Path.of("src/test/resources/topology/demo-quote-processor.graphml"), folder.resolve("graph/quote.graphml"));
        return folder;
    }

    private static String verify(Path bundle) {
        var out = new ByteArrayOutputStream();
        int code = Main.bundle(new String[]{"--verify", bundle.toString()}, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertEquals(0, code);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Each log's graph and run keys and each graph's key are recorded, verified and shown")
    void keysAreRecordedAndShown(@TempDir Path tmp) throws Exception {
        String log = "---\n" + header(SHAPE, "2c41aa00-1111") + "---\n" + record(1) + "---\n" + header(SHAPE, "7d02bb00-2222")
                + "---\n" + record(2) + "---\n";
        Path folder = payload(tmp, log);
        var keys = EvidenceKeys.of(folder);
        assertEquals(2, keys.size());
        assertEquals("graph/quote.graphml", keys.get(0).get("member"));
        assertEquals(SHAPE, keys.get(0).get("graphKey"));
        assertEquals("log/audit.yaml", keys.get(1).get("member"));
        assertEquals(List.of(SHAPE), keys.get(1).get("graphKeys"));
        assertEquals(2, ((List<?>) keys.get(1).get("runKeys")).size(), "two runs: a restart");

        Path bundle = tmp.resolve("b.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, keys);
        var v = EvidenceBundle.verify(bundle);
        assertTrue(v.ok(), v.refusal());
        assertEquals(keys.size(), v.evidenceKeys().size());
        String out = verify(bundle);
        assertTrue(out.contains("graph key: graph/quote.graphml · graph shape " + SHAPE), out);
        assertTrue(out.contains("graph key: log/audit.yaml · graph shape " + SHAPE
                + " — against graph/quote.graphml: graph shape matches"), out);
        assertFalse(out.contains("evidence keys: the manifest's recorded keys differ"), "the manifest agrees with the bytes:\n" + out);
        assertTrue(out.contains("run key: log/audit.yaml · pricing-1 · 2c41aa00 · pricing/quoter#1 · 1 records"), out);
        assertTrue(out.contains("run key: log/audit.yaml · pricing-1 · 7d02bb00 · pricing/quoter#1 · 1 records"), out);
        assertFalse(out.contains("same build"), "D4");
    }

    @Test
    @DisplayName("A log of another shape, and a log with no header, say so")
    void mismatchesAndMissingHeadersAreStated(@TempDir Path tmp) throws Exception {
        Path folder = payload(tmp, "---\n" + header("0000other", "r") + "---\n" + record(1) + "---\n");
        Path bundle = tmp.resolve("m.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null,
                EvidenceKeys.of(folder));
        assertTrue(verify(bundle).contains("graph shape 0000other — against graph/quote.graphml: graph shape does not match"));

        Path old = payload(tmp.resolve("old"), "---\n" + record(1) + "---\n");
        var keys = EvidenceKeys.of(old);
        assertEquals(0, keys.get(1).get("streamHeaders"));
        Path b2 = tmp.resolve("o.fexp");
        EvidenceBundle.pack(old, b2, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, keys);
        assertTrue(verify(b2).contains("graph key: log/audit.yaml · no stream header — against graph/quote.graphml: graph not verified"));
    }

    @Test
    @DisplayName("Review F3, R4: --verify judges the members' bytes with the app's judgement — a record before the header is not verified")
    void verifyJudgesLikeTheApp(@TempDir Path tmp) throws Exception {
        String log = "---\n" + record(1) + "---\n" + header(SHAPE, "run-1") + "---\n" + record(2) + "---\n";
        Path folder = payload(tmp, log);
        Path bundle = tmp.resolve("r4.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null,
                EvidenceKeys.of(folder));
        String out = verify(bundle);
        assertTrue(out.contains("against graph/quote.graphml: graph not verified (record 1 comes before the first stream header"), out);
        assertFalse(out.contains("graph shape matches"), out);
        // the app's judgement of the same bytes
        var store = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(log);
        var app = telamin.fluxtion.audit.analyser.analyser.parse.EvidenceVerdict.judge(
                telamin.fluxtion.audit.analyser.analyser.parse.EvidenceVerdict.shape(store.streamHeaders(), store.size(),
                        store.streamBreaks()), SHAPE, null);
        assertEquals("UNVERIFIED", app.verdict().name());
    }

    @Test
    @DisplayName("Review F3: keys the manifest claims but the bytes do not say are reported; a malformed entry is a refusal")
    void theManifestsKeysAreNeverTrusted(@TempDir Path tmp) throws Exception {
        Path folder = payload(tmp, "---\n" + header(SHAPE, "run-1") + "---\n" + record(1) + "---\n");
        var forged = new java.util.ArrayList<Map<String, Object>>();
        for (var k : EvidenceKeys.of(folder)) {
            var copy = new java.util.LinkedHashMap<>(k);
            if ("log".equals(copy.get("kind"))) copy.put("graphKeys", List.of("someone-else"));
            forged.add(copy);
        }
        Path bundle = tmp.resolve("forged.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, forged);
        String out = verify(bundle);
        assertTrue(out.contains("evidence keys: the manifest's recorded keys differ from what the members' bytes state"), out);
        assertTrue(out.contains("graph shape matches"), "judged from the bytes, not the claim:\n" + out);

        var bad = new java.util.ArrayList<Map<String, Object>>(EvidenceKeys.of(folder));
        var entry = new java.util.LinkedHashMap<>(bad.get(1));
        entry.put("streamHeaders", "one");                       // the hand edit that used to throw ClassCastException
        bad.set(1, entry);
        Path b2 = tmp.resolve("bad.fexp");
        EvidenceBundle.pack(folder, b2, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, bad);
        var outS = new ByteArrayOutputStream();
        var errS = new ByteArrayOutputStream();
        int code = Main.bundle(new String[]{"--verify", b2.toString()}, new PrintStream(outS, true, StandardCharsets.UTF_8),
                new PrintStream(errS, true, StandardCharsets.UTF_8));
        assertEquals(1, code, "a refusal, not a crash");
        assertTrue(errS.toString(StandardCharsets.UTF_8).contains("evidenceKeys entry"), errS.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A binary log's keys come from its HEADER frames")
    void aBinaryLogsKeys(@TempDir Path tmp) throws Exception {
        Path folder = tmp.resolve("p");
        Files.createDirectories(folder.resolve("log"));
        Files.write(folder.resolve("log/audit.flxa"), FlxaConformanceCorpus.committed("f28-stream-header-resent"));
        var keys = EvidenceKeys.of(folder);
        assertEquals(2, keys.get(0).get("streamHeaders"));
        assertEquals(2, ((List<?>) keys.get(0).get("runKeys")).size(), "run-1 then run-2");
    }

    @Test
    @DisplayName("An excerpt carries the header in force and each one re-sent inside it; the records are unchanged")
    void anExcerptCarriesItsHeaders() throws Exception {
        String log = "---\n" + header(SHAPE, "run-1") + "---\n" + record(1) + "---\n" + record(2) + "---\n"
                + header(SHAPE, "run-2") + "---\n" + record(3) + "---\n";
        HeapLogStore store = new HeapLogStore(log);
        String text = BundleExcerpt.text(store, new BundleExcerpt.Range(1, 2, 3, null, null));
        HeapLogStore back = new HeapLogStore(text);
        assertEquals(2, back.size(), "records 2 and 3, and nothing else counted");
        assertEquals(List.of("run-1", "run-2"), back.streamHeaders().stream().map(h -> h.host().get("runId")).toList());
        assertEquals(List.of(0, 1), back.streamHeaders().stream().map(h -> h.firstRecord()).toList());
        assertEquals(store.rawText(1), back.rawText(0));
    }
}
