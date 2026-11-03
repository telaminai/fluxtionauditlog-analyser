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
        assertTrue(out.contains("graph key: log/audit.yaml · graph shape " + SHAPE + " — the bundled graph's shape matches"), out);
        assertTrue(out.contains("run key: log/audit.yaml · pricing-1 · 2c41aa00 · pricing/quoter#1 · 1 records"), out);
        assertTrue(out.contains("run key: log/audit.yaml · pricing-1 · 7d02bb00 · pricing/quoter#1 · 1 records"), out);
        assertFalse(out.contains("same build"), "D4");
    }

    @Test
    @DisplayName("A log of another shape, and a log with no header, say so; without keys the manifest is unchanged")
    void mismatchesAndMissingHeadersAreStated(@TempDir Path tmp) throws Exception {
        Path folder = payload(tmp, "---\n" + header("0000other", "r") + "---\n" + record(1) + "---\n");
        Path bundle = tmp.resolve("m.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null,
                EvidenceKeys.of(folder));
        assertTrue(verify(bundle).contains("graph shape 0000other — the bundled graph's shape does not match"));

        Path old = payload(tmp.resolve("old"), "---\n" + record(1) + "---\n");
        var keys = EvidenceKeys.of(old);
        assertEquals(0, keys.get(1).get("streamHeaders"));
        Path b2 = tmp.resolve("o.fexp");
        EvidenceBundle.pack(old, b2, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, keys);
        assertTrue(verify(b2).contains("graph key: log/audit.yaml · no stream header: graph not verified"));
        // no keys: byte for byte the manifest it always was
        Path b3 = tmp.resolve("plain.fexp");
        Path b4 = tmp.resolve("plain-null.fexp");
        String a = EvidenceBundle.pack(old, b3, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test");
        String b = EvidenceBundle.pack(old, b4, java.time.Instant.parse("2026-11-03T10:00:00Z"), "test", null, null, null, null);
        assertEquals(a, b);
        assertNull(EvidenceBundle.verify(b3).evidenceKeys());
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
