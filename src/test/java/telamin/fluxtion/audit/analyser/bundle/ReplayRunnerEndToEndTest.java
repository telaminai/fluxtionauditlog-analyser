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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipient's whole path (M70.R4, spec-evidence-bundle-replay §5–§6), end to end: a bundle with replay records, a
 * build of the processor compiled from the committed DEMO sources as a recipient would have it, the runner
 * ({@code tools/replay/ReplayBundle.java}) replaying the records into it, and the analyser's comparison judging what it
 * wrote. Then the same with a build that behaves differently, and one whose graph does not match the bundle's at all.
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
        return build(tmp, name, riskLimit, false);
    }

    /**
     * As above; {@code tracing} is the generator's trace option switched on in the generated processor, the only
     * change (PR #70 re-review C1: a tracing build writes a record for the control events set-up dispatches).
     */
    static Path build(Path tmp, String name, String riskLimit, boolean tracing) throws Exception {
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
                if (tracing && rel.endsWith("DemoQuoteRecordedProcessor.java")) {
                    assertTrue(text.contains("eventLogger.trace = false;") && text.contains("eventLogger.traceLevel = LogLevel.NONE;"),
                            "the tracing build edits the generated processor's trace option");
                    text = text.replace("eventLogger.trace = false;", "eventLogger.trace = true;")
                            .replace("eventLogger.traceLevel = LogLevel.NONE;", "eventLogger.traceLevel = LogLevel.INFO;");
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

    /** How a test writes a manifest: the analyser's compact JSON, the same keys in reverse order, or indented. */
    enum Style { COMPACT, REORDERED, PRETTY }

    /** The manifest, as JSON values: every whole number a Long, as the analyser wrote it. */
    @SuppressWarnings("unchecked")
    static java.util.Map<String, Object> manifest(java.util.Map<String, byte[]> entries) {
        return (java.util.Map<String, Object>) whole(telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(
                new String(entries.get("manifest.json"), StandardCharsets.UTF_8)));
    }

    private static Object whole(Object v) {
        if (v instanceof Double d && d == Math.rint(d)) return (long) (double) d;
        if (v instanceof java.util.Map<?, ?> m) {
            var out = new java.util.LinkedHashMap<String, Object>();
            m.forEach((k, x) -> out.put((String) k, whole(x)));
            return out;
        }
        if (v instanceof List<?> l) return new ArrayList<>(l.stream().map(ReplayRunnerEndToEndTest::whole).toList());
        return v;
    }

    static void putManifest(java.util.Map<String, byte[]> entries, java.util.Map<String, Object> m, Style style) {
        entries.put("manifest.json", (json(m, style, "") + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** JSON in one of three spellings of the same value: what a reader must read alike. */
    static String json(Object v, Style style, String indent) {
        String in = indent + "  ", nl = style == Style.PRETTY ? "\n" : "", sp = style == Style.PRETTY ? " " : "";
        if (v instanceof java.util.Map<?, ?> m) {
            List<String> keys = new ArrayList<>(m.keySet().stream().map(String.class::cast).toList());
            if (style == Style.REORDERED) java.util.Collections.reverse(keys);
            List<String> parts = new ArrayList<>();
            for (String k : keys) {
                parts.add((style == Style.PRETTY ? in : "") + telamin.fluxtion.audit.analyser.analyser.llm.Json.write(k) + ":" + sp
                        + json(m.get(k), style, in));
            }
            return parts.isEmpty() ? "{}" : "{" + nl + String.join("," + nl, parts) + nl + (style == Style.PRETTY ? indent : "") + "}";
        }
        if (v instanceof List<?> l) {
            List<String> parts = new ArrayList<>();
            for (Object x : l) parts.add((style == Style.PRETTY ? in : "") + json(x, style, in));
            return parts.isEmpty() ? "[]" : "[" + nl + String.join("," + nl, parts) + nl + (style == Style.PRETTY ? indent : "") + "]";
        }
        return telamin.fluxtion.audit.analyser.analyser.llm.Json.write(v);
    }

    static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Re-stamp one member's sha256 and size in the manifest, as a bundle packed with those bytes would have them. */
    @SuppressWarnings("unchecked")
    static void restamp(java.util.Map<String, byte[]> entries, String member, byte[] bytes) throws Exception {
        var m = manifest(entries);
        boolean found = false;
        for (Object o : (List<Object>) m.get("members")) {
            var x = (java.util.Map<String, Object>) o;
            if (x.get("path").equals(member)) {
                x.put("sha256", sha256(bytes));
                x.put("bytes", (long) bytes.length);
                found = true;
            }
        }
        assertTrue(found, member + " is listed");
        putManifest(entries, m, Style.COMPACT);
    }

    /** Add a member and list it in the manifest, as a sender who packed it would. */
    @SuppressWarnings("unchecked")
    static void add(java.util.Map<String, byte[]> entries, String member, byte[] bytes) throws Exception {
        entries.put(member, bytes);
        var m = manifest(entries);
        var x = new java.util.LinkedHashMap<String, Object>();
        x.put("path", member);
        x.put("sha256", sha256(bytes));
        x.put("bytes", (long) bytes.length);
        ((List<Object>) m.get("members")).add(x);
        putManifest(entries, m, Style.COMPACT);
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
        assertTrue(r.out().contains("graph: your build's node ids and edges match the bundle's"), r.out());
        assertTrue(r.out().contains("this does not show it is the same code"), "graph compatibility, not identity: " + r.out());
        assertTrue(r.out().contains("replayed: 7 recorded inputs"), r.out());
        assertTrue(r.out().contains("(8 audit records)"), "the graph raised its own breach again, by itself: " + r.out());

        var c = ReplayCompare.compare(bundle, out, 256);
        assertTrue(c.agrees(), c.refusal() + " / " + c.divergence());
        assertEquals(8, c.records());
        assertFalse(Files.readString(out).lines().anyMatch(l -> l.strip().equals("event: EventLogControlEvent")),
                "the runner's own set-up is not in the replayed log");
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
            // and a filler member the runner does not otherwise read, listed as a sender would list it
            var entries = EvidenceBundleTest.entries(bundle);
            System.setProperty("replayBundle.maxMemberBytes", Long.toString(logBytes + 100_000));
            add(entries, "notes/filler.bin", new byte[(int) (logBytes + 200_000)]);
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
        byte[] changed = (new String(entries.get(log), StandardCharsets.UTF_8)
                + "---\neventLogRecord: \n    event: EventLogControlEvent\n").getBytes(StandardCharsets.UTF_8);
        entries.put(log, changed);
        restamp(entries, log, changed);           // a log packed with the change: the manifest agrees with it
        Run r = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("levels.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("r.yaml").toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("warning: the bundled log changes its audit level 1 time(s)"), r.out());
        Run control = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", tmp.resolve("s.yaml").toString());
        assertFalse(control.out().contains("warning:"), "control: " + control.out());
    }

    @Test
    @DisplayName("a build whose graph does not match the bundle's is refused by name; --skip-graph-check replays and says so")
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
        // the same size, one value changed: only the digest can see it
        var same = EvidenceBundleTest.entries(bundle);
        String text = new String(same.get(member), StandardCharsets.UTF_8);
        String flipped = text.replaceFirst("bid: 100\\.1,", "bid: 100.2,");
        assertNotEquals(text, flipped, "bid anchor moved");
        same.put(member, flipped.getBytes(StandardCharsets.UTF_8));
        Run digest = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("flipped.fexp"), same).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("f.yaml").toString());
        assertEquals(1, digest.code(), digest.out());
        assertTrue(digest.err().contains(member + " does not match the manifest"), digest.err());
        assertTrue(File.pathSeparator.length() == 1);
    }

    // ---- PR #70 review 2: the runner's resources are bounded in total, and cardinality is refused before reading ----

    /** The runner in its own JVM, with {@code -Xmx64m}: a bundle is refused by name, or replayed, never an OOM. */
    static Run child(Path tmp, List<String> jvm, String... args) throws Exception {
        Path classes = tmp.resolve("runner-classes");
        if (!Files.exists(classes.resolve("ReplayBundle.class"))) {
            Files.createDirectories(classes);
            compile(classes, List.of(RUNNER.toString()));
        }
        List<String> cmd = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx64m"));
        cmd.addAll(jvm);
        cmd.addAll(List.of("-cp", classes + File.pathSeparator + System.getProperty("java.class.path"), "ReplayBundle"));
        cmd.addAll(List.of(args));
        Path out = tmp.resolve("child.out"), err = tmp.resolve("child.err");
        Process p = new ProcessBuilder(cmd).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "the runner finishes");
        return new Run(p.exitValue(), Files.readString(out), Files.readString(err));
    }

    @Test
    @DisplayName("PR #70 review 2: many hashed replay members are refused by name in 64 MiB, before any is read")
    void manyReplayMembersAreRefusedBeforeAnyIsRead(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        // the review's archive: twelve more replay/ members of 6 MiB each, every one listed and correctly hashed
        var entries = EvidenceBundleTest.entries(bundle);
        for (int i = 0; i < 12; i++) add(entries, "replay/extra-" + i + ".yaml", new byte[6 << 20]);
        Path hostile = EvidenceBundleTest.zip(tmp.resolve("hostile.fexp"), entries);
        Path out = tmp.resolve("h.yaml");
        Run r = child(tmp, List.of(), "--bundle", hostile.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", out.toString());
        assertFalse(r.err().contains("OutOfMemoryError"), r.err());
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("REFUSED: the bundle lists 13 replay/ members, not one"), r.err());
        assertFalse(Files.exists(out), "nothing written");

        // the same members carried but NOT listed: refused at the first, never read
        var unlisted = EvidenceBundleTest.entries(bundle);
        for (int i = 0; i < 12; i++) unlisted.put("replay/extra-" + i + ".yaml", new byte[6 << 20]);
        Run u = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("unlisted.fexp"), unlisted).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertEquals(1, u.code(), u.out() + u.err());
        assertTrue(u.err().contains("REFUSED: replay/extra-0.yaml is not listed in the manifest"), u.err());
        assertFalse(Files.exists(out), "nothing written");
    }

    @Test
    @DisplayName("PR #70 review 2: a valid bundle far larger than the heap replays in 64 MiB; the total is bounded too")
    void aLargeValidBundleReplaysInBoundedMemory(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        var entries = EvidenceBundleTest.entries(bundle);
        String log = "log/demo-quote-recorded-audit.yaml";
        // a 48 MiB note, and a log with a 32 MiB line: both streamed past, neither held
        add(entries, "notes/large.bin", new byte[48 << 20]);
        byte[] longLine = new byte[32 << 20];
        java.util.Arrays.fill(longLine, (byte) 'x');
        byte[] grown = java.nio.ByteBuffer.allocate(entries.get(log).length + longLine.length)
                .put(entries.get(log)).put(longLine).array();
        entries.put(log, grown);
        restamp(entries, log, grown);
        Path large = EvidenceBundleTest.zip(tmp.resolve("large.fexp"), entries);
        Path out = tmp.resolve("l.yaml");
        Run r = child(tmp, List.of(), "--bundle", large.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", out.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("(8 audit records)"), r.out());

        // the members together are bounded: the same bundle, under a total limit it exceeds, is refused by name
        long limit = 64L << 20;
        Run t = child(tmp, List.of("-DreplayBundle.maxBundleBytes=" + limit), "--bundle", large.toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("t.yaml").toString());
        assertEquals(1, t.code(), t.out() + t.err());
        assertTrue(t.err().contains("REFUSED: the bundle's members are larger than the runner's limit of " + limit
                + " bytes together"), t.err());
    }

    @Test
    @DisplayName("PR #70 review 2: a member longer than the manifest declares is cut off as it streams, in 64 MiB")
    void aMemberLongerThanDeclaredIsCutOff(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        var entries = EvidenceBundleTest.entries(bundle);
        String graph = entries.keySet().stream().filter(n -> n.startsWith("graph/")).findFirst().orElseThrow();
        long declared = entries.get(graph).length;
        // the graph is the member the runner holds: 96 MiB where the manifest declares a few KiB, and does not re-stamp
        entries.put(graph, new byte[96 << 20]);
        Path out = tmp.resolve("g.yaml");
        Run r = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("long.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("REFUSED: " + graph + " does not match the manifest: it is larger than the manifest's "
                + declared + " bytes"), r.err());
        assertFalse(Files.exists(out), "nothing written");
    }

    /** A GraphML document of about {@code bytes} bytes: nodes named by {@code id} of their index. */
    static byte[] graphml(int bytes, java.util.function.IntFunction<String> id) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\"?>\n<graphml><graph edgedefault=\"directed\">\n");
        for (int i = 0; b.length() < bytes; i++) b.append("<node id=\"").append(id.apply(i)).append("\"/>\n");
        return b.append("</graph></graphml>\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("PR #70 re-review S3: the graph is bounded in bytes and elements, and read as a stream, in 64 MiB")
    void aLargeGraphIsReadAsAStream(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        String graph = EvidenceBundleTest.entries(bundle).keySet().stream().filter(n -> n.startsWith("graph/")).findFirst().orElseThrow();
        // the re-review's graph (~32 MiB of one id, listed and hashed) ended in an OOM: now over the graph limit
        var huge = EvidenceBundleTest.entries(bundle);
        byte[] reviewers = graphml(32 << 20, i -> "n");
        restamp(huge, graph, reviewers);
        huge.put(graph, reviewers);
        Run h = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("huge.fexp"), huge).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("h.yaml").toString());
        assertFalse(h.err().contains("OutOfMemoryError") || h.err().contains("out of memory"), h.err());
        assertEquals(1, h.code(), h.out() + h.err());
        assertTrue(h.err().contains("REFUSED: " + graph + " is larger than the runner's graph limit of " + (8 << 20)), h.err());
        // within the byte limit, one id repeated: the element count is bounded
        var same = EvidenceBundleTest.entries(bundle);
        byte[] repeated = graphml(7 << 20, i -> "n");
        restamp(same, graph, repeated);
        same.put(graph, repeated);
        Run r = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("same-id.fexp"), same).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("s.yaml").toString());
        assertFalse(r.err().contains("OutOfMemoryError"), r.err());
        assertEquals(1, r.code(), r.out() + r.err());
        assertTrue(r.err().contains("REFUSED: " + graph + " has more than 100000 nodes and edges"), r.err());
        // the bundle's own graph with 7 MiB of comment in it: within the limits, it matches, and the replay runs
        var padded = EvidenceBundleTest.entries(bundle);
        String own = new String(padded.get(graph), StandardCharsets.UTF_8);
        int at = own.indexOf("<graphml");
        assertTrue(at >= 0, "graph anchor moved");
        byte[] big = (own.substring(0, at) + "<!-- " + "x".repeat(7 << 20) + " -->\n" + own.substring(at))
                .getBytes(StandardCharsets.UTF_8);
        restamp(padded, graph, big);
        padded.put(graph, big);
        Run p = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("padded.fexp"), padded).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("p.yaml").toString());
        assertEquals(0, p.code(), p.err());
        assertTrue(p.out().contains("(8 audit records)"), p.out());
        // untrusted XML: the bundle's own graph with a DOCTYPE is refused by name, as the DOM parser refused it
        var typed = EvidenceBundleTest.entries(bundle);
        byte[] doctype = (own.substring(0, at) + "<!DOCTYPE graphml [<!ENTITY demo \"DEMO\">]>\n" + own.substring(at))
                .getBytes(StandardCharsets.UTF_8);
        restamp(typed, graph, doctype);
        typed.put(graph, doctype);
        Run t = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("doctype.fexp"), typed).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("t.yaml").toString());
        assertEquals(1, t.code(), t.out() + t.err());
        assertTrue(t.err().contains("REFUSED: " + graph + " has a DOCTYPE, which the runner does not read"), t.err());
        // distinct ids, as many as fit: the count is bounded, so it is refused by name before the heap is
        var distinct = EvidenceBundleTest.entries(bundle);
        byte[] many = graphml(7 << 20, i -> "n" + i);
        restamp(distinct, graph, many);
        distinct.put(graph, many);
        Path out = tmp.resolve("d.yaml");
        Run d = child(tmp, List.of(), "--bundle", EvidenceBundleTest.zip(tmp.resolve("distinct.fexp"), distinct).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertFalse(d.err().contains("OutOfMemoryError"), d.err());
        assertEquals(1, d.code(), d.out() + d.err());
        assertTrue(d.err().contains("REFUSED: " + graph + " has more than 100000 nodes and edges"), d.err());
        assertFalse(Files.exists(out), "nothing written");
    }

    // ---- PR #70 review 3: the runner's set-up is left out by when it happens, never by what a record says --------

    @Test
    @DisplayName("PR #70 review 3: an input whose text names the control event keeps its record, and agrees with a direct capture")
    void anInputNamingTheControlEventKeepsItsRecord(@TempDir Path tmp) throws Exception {
        Path build = build(tmp, "same", null);
        String payload = Files.readString(ReplayBundleTest.REPLAY)
                .replaceFirst("symbol: \"DEMO-A\"", "symbol: \"DEMO event: EventLogControlEvent\"");
        assertNotEquals(Files.readString(ReplayBundleTest.REPLAY), payload, "payload anchor moved");
        Path replay = tmp.resolve("payload.replay.yaml");
        Files.writeString(replay, payload);

        // what the same build logs for these inputs when it is driven directly, not through the runner
        String[] direct = LiveRecording.run(tmp, build, payload, false);
        Path log = tmp.resolve("direct-audit.yaml");
        Files.writeString(log, direct[1]);
        assertTrue(direct[1].contains("symbol=DEMO event: EventLogControlEvent"), "the processor logs the input: " + direct[1]);

        ReplayPairing.Observed o;
        try (var store = telamin.fluxtion.audit.analyser.analyser.parse.LogStores.open(log, 256)) {
            o = ReplayPairing.observe(replay, store.index(), store.size());
        }
        assertTrue(o.pairs(), o.problem());
        Path bundle = tmp.resolve("payload.fexp");
        BundleWriter.write(new BundleWriter.Job(bundle, log, ReplayBundleTest.GRAPH, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, java.time.Instant.now(), "test", 256, null, false,
                replay, o.records(), o.serviceCalls(), o.sha256()));

        Path out = tmp.resolve("replayed.yaml");
        Run r = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                "--out", out.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("(8 audit records)"), "the first input's record was dropped for its text: " + r.out());
        assertTrue(Files.readString(out).contains("symbol=DEMO event: EventLogControlEvent"), "the payload record remains");
        var c = ReplayCompare.compare(bundle, out, 256);
        assertTrue(c.agrees(), c.refusal() + " / " + c.divergence());
        assertEquals(8, c.records());
    }

    @Test
    @DisplayName("PR #70 re-review C1: a build generated with tracing on replays its own bundle and AGREES, no set-up record")
    void aTracingBuildAgreesWithItsOwnBundle(@TempDir Path tmp) throws Exception {
        Path build = build(tmp, "tracing", null, true);
        // the tracing build's own run, driven directly as a producer drives it, bundled with its replay records
        String replayText = Files.readString(ReplayBundleTest.REPLAY);
        String[] direct = LiveRecording.run(tmp, build, replayText, false);
        Path log = tmp.resolve("tracing-audit.yaml");
        Files.writeString(log, direct[1]);
        ReplayPairing.Observed o;
        try (var store = telamin.fluxtion.audit.analyser.analyser.parse.LogStores.open(log, 256)) {
            o = ReplayPairing.observe(ReplayBundleTest.REPLAY, store.index(), store.size());
        }
        assertTrue(o.pairs(), o.problem());
        Path own = tmp.resolve("tracing.fexp");
        BundleWriter.write(new BundleWriter.Job(own, log, ReplayBundleTest.GRAPH, "project.fluxtion-settings",
                Files.readAllBytes(BundleProfileTest.FIXTURE), null, null, java.time.Instant.now(), "test", 256, null, false,
                ReplayBundleTest.REPLAY, o.records(), o.serviceCalls(), o.sha256()));
        // and the committed bundle, which a non-tracing build wrote: the reviewer's probe
        for (Path bundle : List.of(own, ReplayCompareTest.bundle(tmp.resolve("committed")))) {
            Path out = tmp.resolve(bundle.getFileName() + ".replayed.yaml");
            Run r = runner(tmp, "--bundle", bundle.toString(), "--processor", PROCESSOR, "--cp", build.toString(),
                    "--out", out.toString());
            assertEquals(0, r.code(), r.err());
            assertFalse(Files.readString(out).lines().anyMatch(l -> l.strip().equals("event: EventLogControlEvent")),
                    bundle.getFileName() + ": the runner's set-up is not in the replayed log");
            assertTrue(r.out().contains("(8 audit records)"), bundle.getFileName() + ": " + r.out());
            var c = ReplayCompare.compare(bundle, out, 256);
            assertTrue(c.agrees(), bundle.getFileName() + ": " + c.refusal() + " / " + c.divergence());
            assertEquals(8, c.records());
        }
    }

    // ---- PR #70 review 4: a replay is read whole, and counted, before your processor runs --------------------------

    @Test
    @DisplayName("PR #70 review 4: records run together, or fewer than the manifest declares, are refused before any output")
    void aReplayThatLosesInputIsRefused(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        String member = "replay/demo-quote-recorded.replay.yaml";
        var entries = EvidenceBundleTest.entries(bundle);
        String good = new String(entries.get(member), StandardCharsets.UTF_8);

        // the review's document: the separator between the first two records removed, the manifest re-stamped
        int second = good.indexOf("---\n", 1);
        byte[] joined = (good.substring(0, second) + good.substring(second + 4)).getBytes(StandardCharsets.UTF_8);
        entries.put(member, joined);
        restamp(entries, member, joined);
        Path out = tmp.resolve("j.yaml");
        Run j = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("joined.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertEquals(1, j.code(), "refused, not six inputs of seven: " + j.out());
        assertTrue(j.err().contains("REFUSED: line 5 is not part of a replay record"), j.err());
        assertFalse(Files.exists(out), "nothing written");

        // well-formed, but a record fewer than the manifest's count: the member is not the one that was packed
        int last = good.lastIndexOf("---\n");
        byte[] shorter = good.substring(0, last).getBytes(StandardCharsets.UTF_8);
        entries.put(member, shorter);
        restamp(entries, member, shorter);
        Run s6 = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("short.fexp"), entries).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
        assertEquals(1, s6.code(), s6.out());
        assertTrue(s6.err().contains("REFUSED: the replay member holds 6 records, and the manifest says 7"), s6.err());
        assertFalse(Files.exists(out), "nothing written");
    }

    // ---- PR #70 review 7: the manifest is JSON, read as JSON ------------------------------------------------------

    @Test
    @DisplayName("PR #70 review 7: compact, reordered and pretty-printed manifests all verify and replay; malformed JSON is refused")
    void anyValidSpellingOfTheManifestReplays(@TempDir Path tmp) throws Exception {
        Path bundle = ReplayCompareTest.bundle(tmp);
        Path build = build(tmp, "same", null);
        java.util.Set<String> identities = new java.util.HashSet<>();
        for (Style style : Style.values()) {
            var entries = EvidenceBundleTest.entries(bundle);
            putManifest(entries, manifest(entries), style);
            Path b = EvidenceBundleTest.zip(tmp.resolve(style + ".fexp"), entries);
            var v = EvidenceBundle.verify(b);
            assertTrue(v.ok(), style + ": " + v.refusal());
            identities.add(v.identity());
            Path out = tmp.resolve(style + ".yaml");
            Run r = runner(tmp, "--bundle", b.toString(), "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
            assertEquals(0, r.code(), style + ": " + r.err());
            assertTrue(ReplayCompare.compare(b, out, 256).agrees(), style.toString());
        }
        assertEquals(3, identities.size(), "each spelling is its own exact-byte bundle identity");

        // a size written as 3375.0 is the same JSON number
        var decimal = EvidenceBundleTest.entries(bundle);
        String m = new String(decimal.get("manifest.json"), StandardCharsets.UTF_8).replaceFirst("\"bytes\":(\\d+)", "\"bytes\":$1.0");
        decimal.put("manifest.json", m.getBytes(StandardCharsets.UTF_8));
        Run d = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("decimal.fexp"), decimal).toString(),
                "--processor", PROCESSOR, "--cp", build.toString(), "--out", tmp.resolve("d.yaml").toString());
        assertEquals(0, d.code(), d.err());

        String compact = new String(EvidenceBundleTest.entries(bundle).get("manifest.json"), StandardCharsets.UTF_8).strip();
        for (String bad : List.of(compact.substring(0, compact.length() - 1), compact + " {}", compact.replaceFirst("\"format\":", "format:"),
                compact.replaceFirst("\\{", "{\"format\":1,"))) {
            var broken = EvidenceBundleTest.entries(bundle);
            broken.put("manifest.json", bad.getBytes(StandardCharsets.UTF_8));
            Path out = tmp.resolve("bad.yaml");
            Run r = runner(tmp, "--bundle", EvidenceBundleTest.zip(tmp.resolve("bad.fexp"), broken).toString(),
                    "--processor", PROCESSOR, "--cp", build.toString(), "--out", out.toString());
            assertEquals(1, r.code(), bad + " / " + r.out());
            assertTrue(r.err().contains("REFUSED: manifest.json is not valid JSON"), bad + " / " + r.err());
            assertFalse(Files.exists(out), "nothing written");
        }
    }
}
