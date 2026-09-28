package telamin.fluxtion.audit.analyser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundleTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle v1, B2: the CLI half (spec r2 §3.3, EP-A10). The exit code is the contract a skill scripts against —
 * 0 ok, 1 refused, 2 usage — and every surface that reports a verified bundle states the limits and never implies the
 * sender was authenticated (D-3). Headless; {@code Main.bundle} is called directly, never through {@code System.exit}.
 */
class MainBundleTest {

    private record Run(int code, String out, String err) {
        String all() {
            return out + err;
        }
    }

    private static Run run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code = Main.bundle(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Path demo(Path tmp) throws Exception {
        Path f = Files.createDirectories(tmp.resolve("demo/log"));
        Files.writeString(f.resolve("demo-quote-audit.yaml"), "eventLogRecord:\n  eventToString: DEMO quote 1\n");
        return tmp.resolve("demo");
    }

    private static void statesTheLimitsAndNeverAuthenticity(Run r) {
        for (String limit : EvidenceBundle.LIMITS) assertTrue(r.out().contains("limit: " + limit), "the limits are stated:\n" + r.out());
        String text = r.all().toLowerCase(Locale.ROOT).replace("does not authenticate", "");
        for (String claim : new String[]{"authenticated", "authentic ", "signed by", "trusted", "genuine"}) {
            assertFalse(text.contains(claim), "nothing may imply the sender is known ('" + claim + "'):\n" + r.all());
        }
    }

    @Test
    @DisplayName("verify, unpack: exit 0, the bundle's identity at each step, the limits stated every time")
    void theHappyPath(@TempDir Path tmp) throws Exception {
        Path bundle = tmp.resolve("demo.fexp");
        String identity = "identity: " + EvidenceBundle.pack(demo(tmp), bundle, java.time.Instant.now(), "test");

        Run verify = run("--verify", bundle.toString());
        assertEquals(0, verify.code(), verify.all());
        assertTrue(verify.out().contains(identity), "verify states the identity pack printed:\n" + verify.out());
        assertTrue(verify.out().contains("verified: 1 members"), verify.out());
        statesTheLimitsAndNeverAuthenticity(verify);

        Run unpack = run("--unpack", bundle.toString(), "--into", tmp.resolve("copies").toString());
        assertEquals(0, unpack.code(), unpack.all());
        assertTrue(unpack.out().contains(identity), unpack.out());
        assertTrue(unpack.out().contains("working copy: " + tmp.resolve("copies")), unpack.out());
        assertTrue(unpack.out().contains("the received bundle is unchanged"), unpack.out());
        statesTheLimitsAndNeverAuthenticity(unpack);
    }

    @Test
    @DisplayName("a bundle carrying replay records: verify says so, with its own limit, and never 'no replay'")
    void aReplayBundleSaysWhatItCarries(@TempDir Path tmp) throws Exception {
        Path folder = demo(tmp);
        Files.createDirectories(folder.resolve("replay"));
        Files.copy(Path.of("src/test/resources/replay/demo-quote-recorded.replay.yaml"),
                folder.resolve("replay/demo-quote-recorded.replay.yaml"));
        Path bundle = tmp.resolve("replay.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.now(), "test", null,
                new java.util.LinkedHashMap<>(java.util.Map.of("records", 7, "serviceCalls", 2)));

        Run verify = run("--verify", bundle.toString());
        assertEquals(0, verify.code(), verify.all());
        assertTrue(verify.out().contains("replay: replay/demo-quote-recorded.replay.yaml, the run's 7 recorded inputs; "
                + "the log holds 2 exported-service call(s) the replay does not carry"), verify.out());
        assertTrue(verify.out().contains("limit: replay: the recorded inputs reproduce this log only on a build whose graph matches"),
                verify.out());
        assertFalse(verify.out().contains("no replay"), "a replay bundle never states the no-replay limit:\n" + verify.out());
        assertTrue(verify.out().contains("limit: unsigned"), verify.out());
    }

    @Test
    @DisplayName("--replay-compare: 0 AGREES with the limits, 1 DIVERGES naming the record, 2 usage")
    void replayCompareExitsByVerdict(@TempDir Path tmp) throws Exception {
        Path bundle = telamin.fluxtion.audit.analyser.bundle.ReplayCompareTest.bundle(tmp);
        Path replayed = telamin.fluxtion.audit.analyser.bundle.ReplayCompareTest.REPLAYED;

        Run agrees = run("--replay-compare", bundle.toString(), replayed.toString());
        assertEquals(0, agrees.code(), agrees.all());
        assertTrue(agrees.out().contains("replay: AGREES, 8 of 8 records (endTime excepted on 8"), agrees.out());
        assertTrue(agrees.out().contains("limit: replay: the recorded inputs reproduce this log only on a build whose graph matches"),
                agrees.out());
        assertTrue(agrees.out().contains("limit: unsigned"), agrees.out());

        String text = Files.readString(replayed);
        Path changed = Files.writeString(tmp.resolve("changed.yaml"), text.substring(0, text.lastIndexOf("---\neventLogRecord")));
        Run diverges = run("--replay-compare", bundle.toString(), changed.toString());
        assertEquals(1, diverges.code(), diverges.all());
        assertTrue(diverges.out().contains("replay: DIVERGES at record 7: the bundled log has record 7 (RiskBreachEvent)"),
                diverges.out());
        assertTrue(diverges.out().contains("replay: the 7 record(s) before it agree"), diverges.out());

        assertEquals(2, run("--replay-compare", bundle.toString()).code());
        assertTrue(run("--replay-compare").err().startsWith("usage: --replay-compare"));
    }

    @Test
    @DisplayName("a refused bundle exits 1, names the member on stderr, prints no 'verified' and unpacks nothing")
    void aRefusalExitsOne(@TempDir Path tmp) throws Exception {
        Path folder = demo(tmp);
        Path bundle = tmp.resolve("demo.fexp");
        EvidenceBundle.pack(folder, bundle, java.time.Instant.now(), "test");
        var entries = EvidenceBundleTest.entries(bundle);
        entries.put("log/demo-quote-audit.yaml", "tampered".getBytes(StandardCharsets.UTF_8));
        Path bad = EvidenceBundleTest.zip(tmp.resolve("bad.fexp"), entries);

        Run verify = run("--verify", bad.toString());
        assertEquals(1, verify.code(), verify.all());
        assertTrue(verify.err().contains("REFUSED: changed member: log/demo-quote-audit.yaml"), verify.err());
        assertFalse(verify.out().contains("verified"), verify.out());

        Run unpack = run("--unpack", bad.toString(), "--into", tmp.resolve("copies").toString());
        assertEquals(1, unpack.code(), unpack.all());
        assertFalse(unpack.out().contains("working copy"), unpack.out());
        assertFalse(Files.exists(tmp.resolve("copies")), "nothing was extracted");

    }

    @Test
    @DisplayName("wrong arguments exit 2 with the usage line")
    void usageExitsTwo() {
        assertEquals(2, run("--verify").code());
        assertEquals(2, run("--unpack", "a.fexp", "--elsewhere", "x").code());
        assertTrue(run("--verify").err().startsWith("usage: --verify"));
    }

    @Test
    @DisplayName("--pack and --bundle-profile are retired: exit 2 saying where bundles are written now, never an app launch")
    void theRetiredFlagsSaySo() {
        for (String flag : new String[]{"--pack", "--bundle-profile"}) {
            Run r = run(flag, "a", "b");
            assertEquals(2, r.code(), flag + ": " + r.all());
            assertTrue(r.err().contains(flag + " was removed") && r.err().contains("report {bundle"), r.err());
        }
    }
}
