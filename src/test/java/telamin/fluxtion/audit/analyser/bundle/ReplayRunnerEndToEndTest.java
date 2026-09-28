package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipient's whole path (M70.R4, spec-evidence-bundle-replay §5–§6), end to end: a bundle with replay records, a
 * build of the processor compiled from the committed DEMO sources as a recipient would have it, the runner
 * ({@code tools/replay/ReplayBundle.java}) replaying the records into it, and the analyser's comparison judging what it
 * wrote. Then the same with a build that behaves differently, and one that is not the bundle's processor at all.
 */
class ReplayRunnerEndToEndTest {

    static final Path DEMO_SRC = Path.of("examples/fixture-generator/src/main/java");
    static final Path DEMO_RES = Path.of("examples/fixture-generator/src/main/resources");
    static final Path RUNNER = Path.of("tools/replay/ReplayBundle.java");
    static final String PROCESSOR = "com.acme.demo.generated.DemoQuoteRecordedProcessor";
    static final String GRAPHML = "com/acme/demo/generated/DemoQuoteRecordedProcessor.graphml";

    record Run(int code, String out, String err) { }

    /** A recipient's build: the DEMO sources (no builder: it needs the compiler), compiled, with the generator's GraphML. */
    static Path build(Path tmp, String name, String riskLimit) throws Exception {
        Path src = tmp.resolve(name + "-src"), classes = Files.createDirectories(tmp.resolve(name + "-classes"));
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(DEMO_SRC)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = DEMO_SRC.relativize(p).toString();
                if (rel.contains("/builder/") || rel.endsWith("GenerateFixtures.java")) continue;
                Path to = src.resolve(rel);
                Files.createDirectories(to.getParent());
                String text = Files.readString(p);
                if (riskLimit != null && rel.endsWith("DemoQuoteRecordedProcessor.java")) {
                    String was = "new com.acme.demo.node.Nodes.RiskMonitor(orderTracker, 2)";
                    assertTrue(text.contains(was), "the changed build edits the generated processor's risk limit");
                    text = text.replace(was, "new com.acme.demo.node.Nodes.RiskMonitor(orderTracker, " + riskLimit + ")");
                }
                Files.writeString(to, text);
                files.add(to.toString());
            }
        }
        compile(classes, files);
        Path graph = classes.resolve(GRAPHML);
        Files.createDirectories(graph.getParent());
        Files.copy(DEMO_RES.resolve(GRAPHML), graph);
        return classes;
    }

    static void compile(Path into, List<String> files) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of("-proc:none", "-nowarn", "-d", into.toString(),
                "-cp", System.getProperty("java.class.path")));
        args.addAll(files);
        ByteArrayOutputStream diag = new ByteArrayOutputStream();
        int rc = javac.run(null, diag, diag, args.toArray(String[]::new));
        assertEquals(0, rc, "compiles: " + diag.toString(StandardCharsets.UTF_8));
    }

    /** Re-stamp one member's sha256 and size in the manifest, as a bundle packed with those bytes would have them. */
    static void restamp(java.util.Map<String, byte[]> entries, String member, byte[] bytes) throws Exception {
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        String m = new String(entries.get("manifest.json"), StandardCharsets.UTF_8).replaceFirst(
                "\\{\"path\":\"" + java.util.regex.Pattern.quote(member) + "\",\"sha256\":\"[0-9a-f]{64}\",\"bytes\":\\d+}",
                java.util.regex.Matcher.quoteReplacement("{\"path\":\"" + member + "\",\"sha256\":\"" + sha + "\",\"bytes\":" + bytes.length + "}"));
        entries.put("manifest.json", m.getBytes(StandardCharsets.UTF_8));
    }

    /** The runner, compiled from its committed source and called as its main would be. */
    static Run runner(Path tmp, String... args) throws Exception {
        Path classes = tmp.resolve("runner-classes");
        if (!Files.exists(classes.resolve("ReplayBundle.class"))) {
            Files.createDirectories(classes);
            compile(classes, List.of(RUNNER.toString()));
        }
        try (URLClassLoader l = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ReplayRunnerEndToEndTest.class.getClassLoader())) {
            Class<?> c = l.loadClass("ReplayBundle");
            Method run = c.getDeclaredMethod("run", String[].class, PrintStream.class, PrintStream.class);
            run.setAccessible(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
            int code = (int) run.invoke(null, args, new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        }
    }

    /** Why the runner's JBang header would not resolve as the analyser's build does, or empty when it would. */
    static List<String> headerProblems(String runner, String rootPom) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher v = java.util.regex.Pattern.compile("<fluxtion.version>([^<]+)</fluxtion.version>").matcher(rootPom);
        String version = v.find() ? v.group(1) : null;
        if (!runner.contains("//DEPS com.telamin.fluxtion:fluxtion-runtime:" + version + "\n")) {
            out.add("//DEPS is not fluxtion-runtime:" + version + ", the analyser's fluxtion.version");
        }
        java.util.regex.Matcher r = java.util.regex.Pattern.compile("<id>repsy-fluxtion-public</id>\\s*<url>([^<]+)</url>").matcher(rootPom);
        String repo = r.find() ? r.group(1) : null;
        if (repo == null || !runner.contains("//REPOS mavencentral,repsy-fluxtion-public=" + repo + "\n")) {
            out.add("//REPOS does not name " + repo + ": fluxtion-runtime is not on Maven Central");
        }
        return out;
    }

    @Test
    @DisplayName("the runner's JBang header resolves where the analyser's build does (the runtime is not on Central)")
    void theRunnerResolvesWhereTheAnalyserDoes() throws Exception {
        String runner = Files.readString(RUNNER), pom = Files.readString(Path.of("pom.xml"));
        assertEquals(List.of(), headerProblems(runner, pom));
        // witness: the header as first written, with no repository, is named (a recipient's `jbang` failed on it)
        String withoutRepos = runner.replaceFirst("//REPOS [^\\n]*\\n", "");
        assertEquals(1, headerProblems(withoutRepos, pom).size(), headerProblems(withoutRepos, pom).toString());
    }

    @Test
    @DisplayName("the recipient's build replays the bundle, and the analyser says it AGREES: only endTime and thread differ")
    void theBundlesOwnBuildAgrees(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        Path out = tmp.resolve("replayed.yaml");
        Run r = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", out.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("graph: your build's nodes and edges are the bundle's"), r.out());
        assertTrue(r.out().contains("replayed: 7 recorded inputs"), r.out());
        assertTrue(r.out().contains("(8 audit records)"), "the graph raised its own breach again, by itself: " + r.out());

        var c = ReplayCompare.compare(bundle, out, 256);
        assertTrue(c.agrees(), c.refusal() + " / " + c.divergence());
        assertEquals(8, c.records());
    }

    @Test
    @DisplayName("a build that behaves differently (risk limit 3, not 2) replays, and DIVERGES where the breach was")
    void aChangedBuildDiverges(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "changed", "3");
        Path out = tmp.resolve("replayed.yaml");
        Run r = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", out.toString());
        assertEquals(0, r.code(), "same graph, different behaviour: the runner replays it: " + r.err());

        var c = ReplayCompare.compare(bundle, out, 256);
        assertFalse(c.agrees());
        // the WHOLE message: a prefix check passed while the tail compared the risk monitor's entry with an endTime
        assertEquals("record 6 (OrderUpdateEvent): eventLogRecord.nodeLogs.riskMonitor: the bundled log has "
                + "'{ liveOrders: 2, limit: 2, redispatch: true}', and the replay has no such line", c.divergence(),
                "with the limit at 3 the risk monitor does not log at all on the cycle that breached");
    }

    /** The short DEMO TEST fixture: the recorded run's seven inputs on the same clock, then two exported-service calls. */
    static final Path WITH_SERVICE_CALLS = Path.of("src/test/resources/topology/demo-quote-audit.yaml");

    @Test
    @DisplayName("RB-9: a log whose run made service calls pairs, is warned about, and a REAL replay diverges at the first call")
    void aLogWithServiceCallsDivergesAtTheFirstCall(@TempDir Path tmp) throws Exception {
        // the pairing, observed for real (not a literal): its seven inputs ARE this log's, and it counts the two calls
        ReplayPairing.Observed o;
        try (var store = telamin.fluxtion.audit.analyser.analyser.parse.LogStores.open(WITH_SERVICE_CALLS, 256)) {
            o = ReplayPairing.observe(ReplayBundleTest.REPLAY, store.index(), store.size());
        }
        assertTrue(o.pairs(), o.problem());
        assertEquals(2, o.serviceCalls(), "suspendQuoting and resumeQuoting");
        Path bundle = tmp.resolve("with-calls.fexp");
        BundleWriter.write(new BundleWriter.Job(bundle, WITH_SERVICE_CALLS, ReplayBundleTest.GRAPH, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, java.time.Instant.now(), "test", 256, null, false,
                ReplayBundleTest.REPLAY, o.records(), o.serviceCalls(), o.sha256()));
        assertEquals(2, ((Number) EvidenceBundle.verify(bundle).replay().get("serviceCalls")).intValue());

        Path out = tmp.resolve("replayed.yaml");
        Run r = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build(tmp, "same", null).toString(),
                "--out", out.toString());
        assertEquals(0, r.code(), r.err());

        // the replay cannot carry the calls, so it ends where they begin: named, never an AGREE
        var c = ReplayCompare.compare(bundle, out, 256);
        assertFalse(c.agrees(), "a log with service calls the replay does not carry must never read as agreeing");
        assertEquals("record 8: the bundled log has record 8 (ExportFunctionAuditEvent), and the replay does not "
                + "(10 records bundled, 8 replayed)", c.divergence());
        assertEquals(8, c.records(), "the eight records before the first call agree");
    }

    @Test
    @DisplayName("PR #70 review 2: a replay member with a preamble and trailing garbage is REFUSED, never partly read")
    void aMalformedReplayDocumentIsRefused(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        String member = "replay/demo-quote-recorded.replay.yaml";
        var entries = EvidenceBundleTest.entries(bundle);
        String good = new String(entries.get(member), StandardCharsets.UTF_8);
        String oneRecord = good.substring(0, good.indexOf("---", 1));
        // the reviewer's document: a non-record preamble, ONE event/time pair, trailing garbage. Before the fix the
        // runner searched for an event and a time, accepted it as one input and wrote an audit log
        byte[] malformed = ("preamble: not a record\n" + oneRecord + "trailing: garbage\n").getBytes(StandardCharsets.UTF_8);
        entries.put(member, malformed);
        restamp(entries, member, malformed);          // a manifest that agrees, so only the grammar can refuse it
        Path out = tmp.resolve("m.yaml");
        Run r = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("malformed.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertEquals(1, r.code(), "refused, not replayed: " + r.out());
        assertTrue(r.err().contains("line 1 is not part of a replay record: preamble: not a record"), r.err());
        assertFalse(Files.exists(out), "nothing written");

        // trailing garbage alone, after whole records, is refused too, naming its line
        byte[] trailing = (good + "trailing: garbage\n").getBytes(StandardCharsets.UTF_8);
        entries.put(member, trailing);
        restamp(entries, member, trailing);
        Run t = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("trailing.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("t.yaml").toString());
        assertEquals(1, t.code(), t.out());
        assertTrue(t.err().contains("is not part of a replay record: trailing: garbage"), t.err());

        // control: blank lines around well-formed records are allowed
        byte[] spaced = ("\n" + good.replace("---\n", "\n---\n") + "\n\n").getBytes(StandardCharsets.UTF_8);
        entries.put(member, spaced);
        restamp(entries, member, spaced);
        Run c = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("spaced.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("c.yaml").toString());
        assertEquals(0, c.code(), c.err());
    }

    @Test
    @DisplayName("second review S3: every member is bounded, the log and anything else too, not only the two kept")
    void everyMemberIsBounded(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        long logBytes = EvidenceBundleTest.entries(bundle).get("log/demo-quote-recorded-audit.yaml").length;
        String was = System.getProperty("replayBundle.maxMemberBytes");
        try {
            // a limit just under the log member: the log, which is scanned and never kept, is refused by name
            System.setProperty("replayBundle.maxMemberBytes", Long.toString(logBytes - 1));
            Run r = runner(tmp.resolve("a"), "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                    "--out", tmp.resolve("x.yaml").toString());
            assertEquals(1, r.code(), r.out());
            assertTrue(r.err().contains("is larger than the runner's limit of " + (logBytes - 1) + " bytes"), r.err());
            // and a filler member the runner does not otherwise read
            var entries = EvidenceBundleTest.entries(bundle);
            entries.put("notes/filler.bin", new byte[(int) logBytes]);
            System.setProperty("replayBundle.maxMemberBytes", Long.toString(logBytes + 100_000));
            byte[] big = new byte[(int) (logBytes + 200_000)];
            entries.put("notes/filler.bin", big);
            Run f = runner(tmp.resolve("b"), "--bundle", EvidenceBundleTest.zip(tmp.resolve("filler.fexp"), entries).toString(),
                    "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("y.yaml").toString());
            assertEquals(1, f.code(), f.out());
            assertTrue(f.err().contains("notes/filler.bin is larger than the runner's limit"), f.err());
        } finally {
            if (was == null) System.clearProperty("replayBundle.maxMemberBytes");
            else System.setProperty("replayBundle.maxMemberBytes", was);
        }
    }

    @Test
    @DisplayName("review S6: a bundled log that changes its audit level mid-run is warned about, not replayed silently")
    void aLevelChangeInTheLogIsWarnedAbout(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        var entries = EvidenceBundleTest.entries(bundle);
        String log = "log/demo-quote-recorded-audit.yaml";
        entries.put(log, (new String(entries.get(log), StandardCharsets.UTF_8)
                + "---\neventLogRecord: \n    event: EventLogControlEvent\n").getBytes(StandardCharsets.UTF_8));
        Run r = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("levels.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("r.yaml").toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("warning: the bundled log changes its audit level 1 time(s)"), r.out());
        Run control = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("s.yaml").toString());
        assertFalse(control.out().contains("warning:"), "control: " + control.out());
    }

    @Test
    @DisplayName("a build that is not the bundle's processor is refused by name; --skip-graph-check replays and says so")
    void aDifferentGraphIsRefused(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "other", null);
        // the plain DEMO processor's graph, which has no replay writer compiled in
        Files.copy(DEMO_RES.resolve("com/acme/demo/generated/DemoQuoteProcessor.graphml"), build.resolve(GRAPHML),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Run refused = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("x.yaml").toString());
        assertEquals(1, refused.code());
        assertTrue(refused.err().contains("your build's graph is not the bundle's: node(s) [replayCapture] missing"), refused.err());
        assertFalse(Files.exists(tmp.resolve("x.yaml")), "nothing written");

        Run skipped = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("y.yaml").toString(), "--skip-graph-check");
        assertEquals(0, skipped.code(), skipped.err());
        assertTrue(skipped.out().contains("graph: NOT checked"), skipped.out());
    }

    @Test
    @DisplayName("refused by name: no replay records, a processor not on the classpath, a record type the build does not handle")
    void whatCannotBeReplayedIsRefused(@TempDir Path tmp) throws Exception {
        Path build = build(tmp, "same", null);
        Path plain = tmp.resolve("plain.fexp");
        BundleWriter.write(new BundleWriter.Job(plain, ReplayBundleTest.AUDIT, ReplayBundleTest.GRAPH,
                "project.fluxtion-settings", Files.readAllBytes(BundleProfileTest.FIXTURE), null, null,
                java.time.Instant.now(), "test", 256, null));
        Run none = runner(tmp, "--bundle", plain.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("a.yaml").toString());
        assertEquals(1, none.code());
        assertTrue(none.err().contains("the bundle carries no replay records"), none.err());

        Path bundle = ReplayCompareTest.bundle(tmp.resolve("b"));
        Run missing = runner(tmp, "--bundle", bundle.toString(), "--processor", "com.acme.Nowhere", "--cp", build.toString(),
                "--out", tmp.resolve("b.yaml").toString());
        assertEquals(1, missing.code());
        assertTrue(missing.err().contains("com.acme.Nowhere is not on the classpath"), missing.err());

        // a replay naming a type the build does not handle: refused, never loaded
        var entries = EvidenceBundleTest.entries(bundle);
        String member = "replay/demo-quote-recorded.replay.yaml";
        byte[] renamed = new String(entries.get(member), StandardCharsets.UTF_8)
                .replaceFirst("Events\\$MarketDataEvent", "Events\\$NotHandled").getBytes(StandardCharsets.UTF_8);
        entries.put(member, renamed);
        restamp(entries, member, renamed);        // a SENDER who packed such a record: the manifest agrees with it
        Path tampered = EvidenceBundleTest.zip(tmp.resolve("t.fexp"), entries);
        Run unhandled = runner(tmp, "--bundle", tampered.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("c.yaml").toString());
        assertEquals(1, unhandled.code());
        assertTrue(unhandled.err().contains("names com.acme.demo.event.Events$NotHandled, which your processor does not handle"),
                unhandled.err());

        assertEquals(2, runner(tmp, "--bundle", bundle.toString()).code(), "usage");
        Run badLevel = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("d.yaml").toString(), "--level", "LOUD");
        assertEquals(2, badLevel.code(), "review N5: a bad --level is usage, not a refusal: " + badLevel.err());

        // review S4: the runner holds the replay member to the manifest before your build runs on it
        var changed = EvidenceBundleTest.entries(bundle);
        changed.put(member, (new String(changed.get(member), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8));
        Run tamper = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("changed.fexp"), changed).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("e.yaml").toString());
        assertEquals(1, tamper.code());
        assertTrue(tamper.err().contains(member + " does not match the manifest"), tamper.err());
        assertFalse(Files.exists(tmp.resolve("e.yaml")), "nothing replayed, nothing written");
        assertTrue(File.pathSeparator.length() == 1);
    }
}
