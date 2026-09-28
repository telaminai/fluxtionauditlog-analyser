package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bundle that carries the run's replay records (M70.R2, spec-evidence-bundle-replay §4): format 2, and only then.
 * End to end on the committed replay fixture: pair it, write it, verify it.
 */
class ReplayBundleTest {

    static final Path AUDIT = Path.of("src/test/resources/replay/demo-quote-recorded-audit.yaml");
    static final Path REPLAY = Path.of("src/test/resources/replay/demo-quote-recorded.replay.yaml");
    static final Path GRAPH = Path.of("src/test/resources/replay/demo-quote-recorded-processor.graphml");

    static ReplayPairing.Observed pairing() throws Exception {
        return pairing(REPLAY);
    }

    private static ReplayPairing.Observed pairing(Path replay) throws Exception {
        try (LogStore store = LogStores.open(AUDIT, 256)) {
            return ReplayPairing.observe(replay, store.index(), store.size());
        }
    }

    static BundleWriter.Job job(Path out, Path replay, ReplayPairing.Observed o) throws IOException {
        return new BundleWriter.Job(out, AUDIT, GRAPH, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, Instant.parse("2026-09-28T12:00:00Z"), "test",
                256, null, false, replay, o.records(), o.serviceCalls(), o.sha256());
    }

    @Test
    @DisplayName("written with a paired replay: format 2, the replay member, its facts and its own limits")
    void aBundleWithAReplayIsFormat2(@TempDir Path tmp) throws Exception {
        var o = pairing();
        assertTrue(o.pairs(), o.problem());
        Path out = tmp.resolve("with-replay.fexp");
        BundleWriter.write(job(out, REPLAY, o));

        var v = EvidenceBundle.verify(out);
        assertTrue(v.ok(), v.refusal());
        assertEquals("replay/demo-quote-recorded.replay.yaml", v.replay().get("member"));
        assertEquals(7, ((Number) v.replay().get("records")).intValue());
        assertEquals(0, ((Number) v.replay().get("serviceCalls")).intValue());
        assertEquals(EvidenceBundle.LIMITS_REPLAY, EvidenceBundle.limits(v));
        assertTrue(v.members().stream().anyMatch(m -> m.path().equals("replay/demo-quote-recorded.replay.yaml")));

        String manifest = manifest(out);
        assertTrue(manifest.startsWith("{\"format\":2,"), manifest);
        assertFalse(manifest.contains("no replay"), "a replay bundle never states the no-replay limit");
    }

    @Test
    @DisplayName("control: without a replay the bundle stays format 1 and states no replay")
    void withoutAReplayItStaysFormat1(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("plain.fexp");
        BundleWriter.write(new BundleWriter.Job(out, AUDIT, GRAPH, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, Instant.now(), "test", 256, null));
        var v = EvidenceBundle.verify(out);
        assertTrue(v.ok(), v.refusal());
        assertNull(v.replay());
        assertEquals(EvidenceBundle.LIMITS, EvidenceBundle.limits(v));
        assertTrue(manifest(out).startsWith("{\"format\":1,"));
    }

    @Test
    @DisplayName("the copy is held to the bytes that were paired: a replay changed in between is refused, nothing left")
    void aReplayChangedAfterPairingIsRefused(@TempDir Path tmp) throws Exception {
        Path replay = Files.copy(REPLAY, tmp.resolve("demo-quote-recorded.replay.yaml"));
        var o = pairing(replay);
        assertTrue(o.pairs(), o.problem());
        Files.writeString(replay, Files.readString(replay) + "# appended after pairing\n");
        Path out = tmp.resolve("changed.fexp");
        var ex = assertThrows(IOException.class, () -> BundleWriter.write(job(out, replay, o)));
        assertTrue(ex.getMessage().contains("the replay file changed after it was paired"), ex.getMessage());
        assertFalse(Files.exists(out), "a refused capture leaves nothing behind");
    }

    @Test
    @DisplayName("a replay cannot be claimed without its member, nor carried without its claim")
    void claimAndMemberGoTogether(@TempDir Path tmp) throws Exception {
        Path folder = EvidenceBundleTest.demoFolder(tmp);
        Map<String, Object> facts = new LinkedHashMap<>(Map.of("records", 7, "serviceCalls", 0));
        var noMember = assertThrows(IOException.class, () ->
                EvidenceBundle.pack(folder, tmp.resolve("a.fexp"), EvidenceBundleTest.AT, "test", null, facts));
        assertTrue(noMember.getMessage().contains("holds one replay/ member, not 0"), noMember.getMessage());

        Files.createDirectories(folder.resolve("replay"));
        Files.copy(REPLAY, folder.resolve("replay/r.replay.yaml"));
        var noClaim = assertThrows(IOException.class, () ->
                EvidenceBundle.pack(folder, tmp.resolve("b.fexp"), EvidenceBundleTest.AT, "test", null, null));
        assertTrue(noClaim.getMessage().contains("with no replay stated"), noClaim.getMessage());

        // a format-2 manifest naming a replay member the manifest does not list
        Path good = tmp.resolve("good.fexp");
        EvidenceBundle.pack(folder, good, EvidenceBundleTest.AT, "test", null, facts);
        var entries = EvidenceBundleTest.entries(good);
        entries.put(EvidenceBundle.MANIFEST, new String(entries.get(EvidenceBundle.MANIFEST), StandardCharsets.UTF_8)
                .replace("\"member\":\"replay/r.replay.yaml\"", "\"member\":\"replay/elsewhere.yaml\"")
                .getBytes(StandardCharsets.UTF_8));
        var v = EvidenceBundle.verify(EvidenceBundleTest.zip(tmp.resolve("unlisted.fexp"), entries));
        assertFalse(v.ok());
        assertTrue(v.refusal().contains("the replay member replay/elsewhere.yaml is not listed"), v.refusal());

        // and a format-1 manifest that states a replay
        var sneaked = EvidenceBundleTest.entries(good);
        sneaked.put(EvidenceBundle.MANIFEST, new String(sneaked.get(EvidenceBundle.MANIFEST), StandardCharsets.UTF_8)
                .replace("\"format\":2", "\"format\":1").getBytes(StandardCharsets.UTF_8));
        var w = EvidenceBundle.verify(EvidenceBundleTest.zip(tmp.resolve("sneaked.fexp"), sneaked));
        assertFalse(w.ok());
        assertTrue(w.refusal().contains("a format 1 manifest states a replay"), w.refusal());
    }

    private static String manifest(Path bundle) throws IOException {
        return new String(EvidenceBundleTest.entries(bundle).get(EvidenceBundle.MANIFEST), StandardCharsets.UTF_8);
    }
}
