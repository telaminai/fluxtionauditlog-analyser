package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Review B1 / F3 on a REAL frame, driven through its own action executor with EDT sequencing: open log A and
 * graph A, then start log B and open graph B in one EDT turn (so B's load cannot have landed), read context in
 * that same turn, then wait for the load and read it again. Skipped where there is no display (CI is
 * headless): this is the reviewer's reproduction, pinned so it cannot regress silently on a developer machine.
 */
class PairingDuringLoadFrameTest {

    private static String log(String node) {
        return "---\n#00:00:01.000 [t] INFO L\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n"
                + "    - " + node + ": { v: 1}\n---\n";
    }

    private static String graph(String node) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:jGraph=\"http://www.jgraph.com/\">\n"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>\n"
                + "<graph edgedefault=\"undirected\">\n<node id=\"" + node + "\"><data key=\"vertex_label\">"
                + "<jGraph:ShapeNode><jGraph:Geometry height=\"70\" width=\"160\" x=\"20\" y=\"20\"/>"
                + "<jGraph:label text=\"id:" + node + "&#10;class:com.acme." + node + "\"/>"
                + "<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>\n</graph></graphml>\n";
    }

    /** Constructed file and rolled-reader negative control, driven through the real open verb. */
    @Test
    void assistantFollowEchoAndHumanControlsAgree(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path audit = Files.writeString(tmp.resolve("follow.yaml"), log("rootNode"));
        Path other = Files.writeString(tmp.resolve("other.yaml"), log("rootNode"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            var ex = executorOf(frame.get());
            var field = MainFrame.class.getDeclaredField("followButton"); field.setAccessible(true);
            var button = (javax.swing.JToggleButton)field.get(frame.get());
            onEdt(() -> {
                assertFalse(ex.render("open", Map.of("follow", true)).ok());
                render(ex, "open", Map.of("log", audit.toString()));
                assertFalse(ex.render("open", Map.of("follow", true)).ok(), "pending open cannot follow the old reader");
            });
            awaitLoaded(ex);
            onEdt(() -> {
                assertEquals(true, find(render(ex, "open", Map.of("follow", true)), "following"));
                assertTrue(button.isSelected());
                assertTrue(menuItem(frame.get(), "followMenuItem").isSelected());
                assertEquals(true, find(render(ex, "context", Map.of()), "following"));
                assertFalse(ex.render("open", Map.of("follow", "true")).ok());
                assertFalse(ex.render("open", Map.of("follow", false, "posture", "research")).ok());
                assertTrue(button.isSelected(), "mixed call must not partially apply");
                button.doClick(); // person can still stop it
                assertEquals(false, find(render(ex, "context", Map.of()), "following"));
                render(ex, "open", Map.of("follow", true));
                assertEquals(false, find(render(ex, "open", Map.of("follow", false)), "following"));
                assertFalse(button.isSelected());
                render(ex, "open", Map.of("logs", List.of(audit.toString(), other.toString())));
            });
            awaitLoaded(ex);
            onEdt(() -> {
                var refusal = ex.render("open", Map.of("follow", true));
                assertFalse(refusal.ok());
                assertTrue(refusal.error().contains("Not following"));
                assertFalse(button.isSelected());
                assertEquals(false, find(render(ex, "context", Map.of()), "following"));
                assertEquals(false, find(render(ex, "context", Map.of()), "supportsFollow"));
            });
        } finally {
            if (frame.get() != null) onEdt(() -> { try { executorOf(frame.get()).render("open", Map.of("follow", false)); } catch (Exception e) { throw new RuntimeException(e); } frame.get().dispose(); });
            System.setProperty("user.home", home);
        }
    }

    @Test
    void pendingTrailingRecordIsVisibleInContextAndFollowStatus(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path audit = Files.writeString(tmp.resolve("pending.yaml"), log("rootNode")
                + "eventLogRecord:\n  logTime: 2000\n");
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            var ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("log", audit.toString())));
            awaitLoaded(ex);
            onEdt(() -> {
                var ctx = render(ex, "context", Map.of());
                assertEquals(0, find(ctx, "trailingRecordsPending"));
                assertEquals(1, find(ctx, "trailingRecordsIncluded"));
                render(ex, "open", Map.of("follow", true));
            });
            awaitLoaded(ex);
            onEdt(() -> assertEquals(1, find(render(ex, "context", Map.of()), "trailingRecordsPending")));
            var follow = MainFrame.class.getDeclaredMethod("setFollowing", boolean.class);
            follow.setAccessible(true);
            var status = MainFrame.class.getDeclaredField("status"); status.setAccessible(true);
            onEdt(() -> {
                try {
                    follow.invoke(frame.get(), true);
                    assertTrue(((javax.swing.JLabel)status.get(frame.get())).getText().contains("1 trailing record pending"));
                    follow.invoke(frame.get(), false);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void openingCommittedCopiesAnnouncesDisagreementWithoutRefusing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path fixtures = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures");
        Path source = Files.createDirectories(tmp.resolve("source")).resolve("MarketProcessor.graphml");
        Path stale = Files.createDirectories(tmp.resolve("classes")).resolve("MarketProcessor.graphml");
        Files.copy(fixtures.resolve("MarketProcessor.src-round3.graphml"), source);
        Files.copy(fixtures.resolve("MarketProcessor.target-stale.graphml"), stale);
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            var configField = MainFrame.class.getDeclaredField("config");
            configField.setAccessible(true);
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) configField.get(frame.get());
            config.sourceRoots.add(tmp.toString());
            var ex = executorOf(frame.get());
            for (Path path : List.of(source, stale)) {
                AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
                onEdt(() -> echo.set(render(ex, "open", Map.of("graphml", path.toString()))));
                assertEquals(true, echo.get().get("ok"));
                AtomicReference<Map<String, Object>> pair = new AtomicReference<>();
                long deadline = System.nanoTime() + 10_000_000_000L;
                do {
                    onEdt(() -> pair.set(pairing(ex)));
                    if ("complete".equals(((Map<?, ?>) pair.get().get("copyComparison")).get("state"))) break;
                    Thread.sleep(20);
                } while (System.nanoTime() < deadline);
                var comparison = (Map<String, Object>) pair.get().get("copyComparison");
                assertEquals("complete", comparison.get("state"));
                var groups = (List<Map<String, Object>>) comparison.get("groups");
                assertEquals("disagree", groups.get(0).get("agreement"));
                assertEquals(path.toString(), pair.get().get("graphPath"));
                var panelField = MainFrame.class.getDeclaredField("topologyPanel");
                panelField.setAccessible(true);
                var panel = (TopologyPanel) panelField.get(frame.get());
                onEdt(() -> assertTrue(((javax.swing.JLabel) panel.statusComponent()).getText().contains("Graph copies disagree")));
            }
            onEdt(() -> render(ex, "open", Map.of("close", "graph")));
            onEdt(() -> assertFalse(pairing(ex).containsKey("copyComparison")));
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    /**
     * Round 4, O-i — a Follow append must not spend the session's audit ring. The ring holds 2,000 records, described
     * as "a long investigation's worth of transitions"; one record per append on a live log would evict them in about
     * half an hour. The session's copy of the pairing has one consumer, the coverage claim, so it is refreshed when
     * coverage reads it — and the claim must then count the appended record.
     */
    @Test
    void aFollowAppendWritesNoSessionAuditRecordUntilCoverageReadsIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path graph = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            yaml.append("---\neventLogRecord:\n  logTime: ").append(1000 + i).append("\n  event: Tick\n  nodeLogs:\n")
                .append("    - rootNode: { v: 1}\n    - riskCheck: { v: 1}\n    - output: { v: 1}\n");
        }
        Path audit = Files.writeString(tmp.resolve("churn-600.yaml"), yaml.append("---\n").toString());
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("log", audit.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graph.toAbsolutePath().toString())));
            awaitVerdict(ex);
            var sessionField = MainFrame.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) sessionField.get(frame.get());
            render(ex, "open", Map.of("follow", true));
            Thread.sleep(1500);                                   // let Follow settle before counting
            long before = onEdtGet(() -> session.auditSink().total());
            for (int n = 0; n < 5; n++) {
                Files.writeString(audit, "eventLogRecord:\n  logTime: " + (5000 + n) + "\n  event: Tick\n  nodeLogs:\n"
                        + "    - rootNode: { v: 1}\n---\n", java.nio.file.StandardOpenOption.APPEND);
                long deadline = System.currentTimeMillis() + 15_000;
                int want = 601 + n;
                while (System.currentTimeMillis() < deadline) {
                    Object log = find(onEdtGet(() -> render(ex, "context", Map.of())), "log");
                    if (log instanceof Map<?, ?> l && Integer.valueOf(want).equals(l.get("records"))) break;
                    Thread.sleep(100);
                }
            }
            Thread.sleep(1200);                                   // one more poll past the last append
            long after = onEdtGet(() -> session.auditSink().total());
            assertEquals(0, after - before, "session audit records written by five Follow appends: " + (after - before));
            Map<String, Object> reply = render(ex, "coverage", Map.of());
            String claim = String.valueOf(find(reply, "claimNote"));
            assertTrue(claim.contains("of 605 records"), "the claim counts the appended records when read: " + claim);
            render(ex, "open", Map.of("follow", false));
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    /**
     * Round 3, N1 — exactly the re-review's reproduction, through Follow on a real store: a 600-record log whose ids are
     * all declared, whole-log coverage, Follow on, one appended record writing an undeclared id. The qualification
     * must stop claiming to confirm the whole log, and the published pairing's scope must count the new record.
     */
    @Test
    void aFollowAppendMakesTheWholeLogVerdictStale(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path graph = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            yaml.append("---\neventLogRecord:\n  logTime: ").append(1000 + i).append("\n  event: Tick\n  nodeLogs:\n")
                .append("    - rootNode: { v: 1}\n    - riskCheck: { v: 1}\n    - output: { v: 1}\n");
        }
        Path audit = Files.writeString(tmp.resolve("followed-600.yaml"), yaml.append("---\n").toString());
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("log", audit.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graph.toAbsolutePath().toString())));
            awaitVerdict(ex);
            render(ex, "coverage", Map.of());
            Map<String, Object> before = onEdtGet(() -> pairing(ex));
            assertTrue(String.valueOf(((Map<?, ?>) before.get("qualifiedBy")).get("note"))
                    .contains("confirms the sampled pairing for the whole log"), "setup: " + before);

            render(ex, "open", Map.of("follow", true));
            Files.writeString(audit, "eventLogRecord:\n  logTime: 5000\n  event: Tick\n  nodeLogs:\n"
                    + "    - lateForeign: { v: 1}\n---\n", java.nio.file.StandardOpenOption.APPEND);
            long deadline = System.currentTimeMillis() + 15_000;
            Map<String, Object> after = null;
            while (System.currentTimeMillis() < deadline) {
                Map<String, Object> ctx = onEdtGet(() -> render(ex, "context", Map.of()));
                Object log = find(ctx, "log");
                if (log instanceof Map<?, ?> l && Integer.valueOf(601).equals(l.get("records"))) {
                    after = onEdtGet(() -> pairing(ex));
                    break;
                }
                Thread.sleep(100);
            }
            assertNotNull(after, "Follow never delivered the appended record");
            Map<?, ?> q = (Map<?, ?>) after.get("qualifiedBy");
            assertEquals(Boolean.TRUE, q.get("stale"), "the whole-log verdict is about the old revision: " + q);
            assertFalse(String.valueOf(q.get("note")).contains("confirms the sampled pairing for the whole log"),
                    "a stale verdict must not claim the whole log: " + q.get("note"));
            assertEquals("first 500 of 601 records", after.get("pairingScope"), "the published pairing counts it too");
            var panelField = MainFrame.class.getDeclaredField("topologyPanel");
            panelField.setAccessible(true);
            var panel = (TopologyPanel) panelField.get(frame.get());
            String line = onEdtGet(panel::statusLine);
            assertTrue(line.startsWith("first 600 of 601 records: all 3 logged id(s) declared"), "panel leads: " + line);
            assertFalse(line.contains("confirms"), line);
            render(ex, "open", Map.of("follow", false));
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    /**
     * Re-review O-c: the parity case above uses a one-record log, so it never compares a SAMPLED verdict. Three
     * loops collect the sample (the frame's pairingAgainst, discovery's discoverGraphs0 and the session's
     * observation); with 600 records all three must state the same 500-of-600 verdict.
     */
    @Test
    void aSampledPairingAgreesAcrossFrameDiscoveryAndSession(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path graph = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            yaml.append("---\neventLogRecord:\n  logTime: ").append(1000 + i).append("\n  event: Tick\n  nodeLogs:\n")
                .append("    - rootNode: { v: 1}\n    - riskCheck: { v: 1}\n    - output: { v: 1}\n");
        }
        Path audit = Files.writeString(tmp.resolve("constructed-600.yaml"), yaml.append("---\n").toString());
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("log", audit.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graph.toAbsolutePath().toString())));
            awaitVerdict(ex);
            onEdt(() -> render(ex, "source_root", Map.of("add", List.of(graph.toAbsolutePath().getParent().toString()))));
            var discover = MainFrame.class.getDeclaredMethod("discoverGraphs0");
            discover.setAccessible(true);
            var judge = MainFrame.class.getDeclaredMethod("pairingAgainst",
                    telamin.fluxtion.audit.analyser.analyser.parse.LogStore.class);
            judge.setAccessible(true);
            var storeField = MainFrame.class.getDeclaredField("store");
            storeField.setAccessible(true);
            var sessionField = MainFrame.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            Path wanted = graph.toAbsolutePath().normalize();
            onEdt(() -> {
                try {
                    var discovered = ((telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery.Result)
                            discover.invoke(frame.get())).candidates().stream()
                            .filter(c -> c.file().toAbsolutePath().normalize().equals(wanted))
                            .findFirst().orElseThrow().pairing();
                    var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) sessionField.get(frame.get());
                    assertEquals("first 500 of 600 records", discovered.scope(), "discovery is sampled");
                    assertEquals(discovered, judge.invoke(frame.get(), storeField.get(frame.get())), "frame/discovery, sampled");
                    assertEquals(discovered, session.processor().pairing.verdict(), "session/discovery, sampled");
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    /** TA-1: the producer graph is committed; the three-node audit record is constructed. */
    @Test
    void committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real frame");
        Path graph = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");
        Path audit = Files.writeString(tmp.resolve("constructed.yaml"), log("rootNode")
                .replace("    - rootNode: { v: 1}", "    - rootNode: { v: 1}\n    - riskCheck: { v: 1}\n    - output: { v: 1}"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("log", audit.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graph.toAbsolutePath().toString())));
            var context = awaitVerdict(ex);
            assertEquals(3, context.get("declaredByGraph"), "session includes the framework logger");
            assertEquals(Boolean.TRUE, context.get("applies"));
            // M68.1 re-review R1/R2: discovery is read through the PRODUCT's own path (source root + the frame's
            // discoverGraphs0), not a hand-built GraphmlDiscovery.scan call. The hand-built call passed an id
            // set with no scope, so it could only ever agree with an unscoped verdict — it tested the fixture's
            // arguments, not what an agent is shown. The three equality assertions below are unchanged.
            onEdt(() -> render(ex, "source_root", Map.of("add", List.of(graph.toAbsolutePath().getParent().toString()))));
            var discover = MainFrame.class.getDeclaredMethod("discoverGraphs0");
            discover.setAccessible(true);
            Path wanted = graph.toAbsolutePath().normalize();
            var discovered = onEdtGet(() -> {
                try {
                    var result = (telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery.Result)
                            discover.invoke(frame.get());
                    return result.candidates().stream()
                            .filter(c -> c.file().toAbsolutePath().normalize().equals(wanted))
                            .findFirst().orElseThrow().pairing();
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            assertEquals("all 1 records", discovered.scope(), "discovery states its scope, as the frame does");
            var sessionField = MainFrame.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) sessionField.get(frame.get());
            var storeField = MainFrame.class.getDeclaredField("store");
            storeField.setAccessible(true);
            var judge = MainFrame.class.getDeclaredMethod("pairingAgainst", telamin.fluxtion.audit.analyser.analyser.parse.LogStore.class);
            judge.setAccessible(true);
            onEdt(() -> {
                try {
                    assertEquals(discovered, judge.invoke(frame.get(), storeField.get(frame.get())), "frame/discovery parity");
                    assertEquals(discovered, session.processor().pairing.verdict(), "session/discovery parity");
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            assertEquals(3, discovered.matched());
            assertFalse(discovered.reason().contains("different build"));
            // review O1: the pairing verdict leads the Topology panel's status line, so it is the part that
            // survives clipping, and the whole line is the label's tooltip
            var panelField = MainFrame.class.getDeclaredField("topologyPanel");
            panelField.setAccessible(true);
            var panel = (TopologyPanel) panelField.get(frame.get());
            String line = onEdtGet(panel::statusLine);
            assertTrue(line.startsWith("every node id checked is declared (3/3"), "pairing first: " + line);
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) onEdt(() -> frame.get().dispose());
        }
    }

    @Test
    void aGraphOpenedWhileTheNextLogLoads_contextSaysPending_thenJudgesTheNewPair(@TempDir Path tmp) throws Exception {
        pendingThenJudged(tmp, false);
    }

    /** Review R2-B1: the explicit-{@code format} socket path never started the pending lifecycle. */
    @Test
    void theSameThroughAnExplicitReaderFormat(@TempDir Path tmp) throws Exception {
        pendingThenJudged(tmp, true);
    }

    @SuppressWarnings("unchecked")
    private void pendingThenJudged(Path tmp, boolean explicitFormat) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display: a real MainFrame is constructed");
        Path logA = Files.writeString(tmp.resolve("a.yaml"), log("nodeA"));
        Path logB = Files.writeString(tmp.resolve("b.yaml"), log("nodeB"));
        Path graphA = Files.writeString(tmp.resolve("a.graphml"), graph("nodeA"));
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));

        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());

            // A/A, in a fresh window with NO project opened (review F3: the session driver must not depend on one)
            onEdt(() -> render(ex, "open", Map.of("log", logA.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graphA.toString())));
            Map<String, Object> pairAA = awaitVerdict(ex);
            assertEquals(Boolean.TRUE, pairAA.get("applies"), "A/A: " + pairAA);
            assertEquals(1, pairAA.get("declaredByGraph"));

            // start B, open graph B and read context — all in ONE EDT turn, so B's load cannot have landed
            AtomicReference<Map<String, Object>> during = new AtomicReference<>();
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            Map<String, Object> openB = explicitFormat
                    ? Map.of("log", logB.toString(), "format", "yaml")
                    : Map.of("log", logB.toString());
            onEdt(() -> {
                render(ex, "open", openB);
                echo.set(render(ex, "open", Map.of("graphml", graphB.toString())));
                during.set(pairing(ex));
            });
            Map<String, Object> g = (Map<String, Object>) ((Map<String, Object>) echo.get().get("opened")).get("graphml");
            assertEquals(ActionExecutor.PAIRING_PENDING, g.get("pairing"), "echo: " + g);
            Map<String, Object> d = during.get();
            assertTrue(String.valueOf(d.get("pairing")).startsWith("pending"), "context during the load: " + d);
            assertEquals(Boolean.TRUE, d.get("loading"));
            assertFalse(d.containsKey("applies"), "review B1: graph A's verdict must not be attached to graph B: " + d);
            assertFalse(d.containsKey("declaredByGraph"), d.toString());

            // and once B lands, the pair is B/B — judged, not left null (review F3)
            Map<String, Object> pairBB = awaitVerdict(ex);
            assertEquals(Boolean.TRUE, pairBB.get("applies"), "B/B: " + pairBB);
            assertEquals(1, pairBB.get("declaredByGraph"));
            assertEquals(1, pairBB.get("loggedNodes"));
            assertTrue(String.valueOf(pairBB.get("graphPath")).endsWith("b.graphml"), pairBB.toString());
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    /**
     * Review R2-B2: in a fresh window, a socket-opened graph that a socket-opened log then contradicts is
     * closed by the session processor — and that decision must NOT wait on a modal nobody can dismiss.
     */
    @Test
    @SuppressWarnings("unchecked")
    void freshWindow_socketGraphThenMismatchingSocketLog_closesTheGraphWithoutADialog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display: a real MainFrame is constructed");
        Path logB = Files.writeString(tmp.resolve("b.yaml"), log("nodeB"));
        Path graphA = Files.writeString(tmp.resolve("a.graphml"), graph("nodeA"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        DialogWatchdog dialogs = new DialogWatchdog();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> render(ex, "open", Map.of("graphml", graphA.toString())));
            onEdt(() -> render(ex, "open", Map.of("log", logB.toString())));
            awaitLoaded(ex);
            AtomicReference<Map<String, Object>> after = new AtomicReference<>();
            onEdt(() -> after.set(pairing(ex)));
            assertNull(after.get().get("graph"), "the mismatched graph is closed: " + after.get());
            assertEquals(0, dialogs.seen(), "a socket-driven arrival must not block on a modal (review R2-B2)");
        } finally {
            dialogs.stop();
            System.setProperty("user.home", home);
            if (frame.get() != null) SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    /**
     * Review R3-B1: a person opened the log, so the arrival's audience was human — and a LATER socket close
     * inherited it and showed a modal. The audience belongs to the operation, so the socket close must not.
     */
    @Test
    void humanArrivalThenSocketGraphOpenAndClose_noDialogForTheSocketOperations(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display: a real MainFrame is constructed");
        Path logA = Files.writeString(tmp.resolve("a.yaml"), log("nodeA"));
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        DialogWatchdog dialogs = new DialogWatchdog();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> frame.get().openFile(logA, OpenRequest.HUMAN));      // the File-menu entrance
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graphB.toString())));   // kept, mismatch announced
            onEdt(() -> render(ex, "open", Map.of("close", "graph")));
            AtomicReference<Map<String, Object>> after = new AtomicReference<>();
            onEdt(() -> after.set(pairing(ex)));
            assertNull(after.get().get("graph"), "the graph is closed: " + after.get());
            assertEquals(0, dialogs.seen(), "a socket close after a human arrival must not inherit the human audience (R3-B1)");
        } finally {
            dialogs.stop();
            System.setProperty("user.home", home);
            if (frame.get() != null) SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    /**
     * Review R4-F1: the stronger sequence — a HUMAN operation immediately before the socket close, driven through
     * the real Recent-GraphML menu item (no chooser). Discriminates the close verb's own declaration: with only
     * that line removed, the socket close inherits the person's audience and shows the modal. Positive control:
     * the same warning IS a dialog when a person closes the graph from the File menu.
     */
    @Test
    void recentGraphmlByAPerson_thenSocketClose_noDialog_andAHumanCloseStillWarns(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display: a real MainFrame is constructed");
        Path logA = Files.writeString(tmp.resolve("a.yaml"), log("nodeA"));
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        DialogWatchdog dialogs = new DialogWatchdog();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());
            onEdt(() -> frame.get().openFile(logA, OpenRequest.HUMAN));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graphB.toString())));   // socket: kept, added to Recent
            onEdt(() -> clickRecentGraphml(frame.get(), graphB));                       // a PERSON re-opens it
            onEdt(() -> render(ex, "open", Map.of("close", "graph")));                 // socket close
            AtomicReference<Map<String, Object>> after = new AtomicReference<>();
            onEdt(() -> after.set(pairing(ex)));
            assertNull(after.get().get("graph"), "closed: " + after.get());
            assertEquals(0, dialogs.seen(), "the socket close declares its own audience (R4-F1 / R3-B1)");

            // positive control: the same warning IS a dialog for a person — a HUMAN arrival that finds a
            // mismatching graph closes it and says so. (Before M44.3a a human File-menu close also warned,
            // because a refresh observation re-judged the log; that spurious warning is gone, so a person
            // closing a graph deliberately is no longer told it did not fit.)
            onEdt(() -> render(ex, "open", Map.of("graphml", graphB.toString())));   // B, mismatching, kept
            onEdt(() -> frame.get().openFile(logA, OpenRequest.HUMAN));               // A arrives, by a person
            awaitLoaded(ex);
            assertEquals(1, dialogs.seen(), "a human arrival renders the graph-closed warning as a dialog");
            AtomicReference<Map<String, Object>> end = new AtomicReference<>();
            onEdt(() -> end.set(pairing(ex)));
            assertNull(end.get().get("graph"), "and the mismatching graph was closed by that arrival");
        } finally {
            dialogs.stop();
            System.setProperty("user.home", home);
            if (frame.get() != null) SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    private static void clickRecentGraphml(MainFrame f, Path graph) {
        try {
            var field = MainFrame.class.getDeclaredField("recentGraphmlMenu");
            field.setAccessible(true);
            javax.swing.JMenu menu = (javax.swing.JMenu) field.get(f);
            for (int i = 0; i < menu.getItemCount(); i++) {
                javax.swing.JMenuItem item = menu.getItem(i);
                if (item != null && graph.toString().equals(item.getText())) { item.doClick(); return; }
            }
            fail("no Recent GraphML item for " + graph + " among " + menu.getItemCount() + " items");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static javax.swing.JMenuItem menuItem(MainFrame f, String fieldName) {
        try {
            var field = MainFrame.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (javax.swing.JMenuItem) field.get(f);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Counts and disposes any visible dialog, so a modal cannot hang the test — it fails it instead. */
    private static final class DialogWatchdog {
        // distinct dialog INSTANCES: the poll is faster than the EDT's disposal, so a count per sighting over-counts
        private final java.util.Set<java.awt.Window> seen =
                java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        private volatile boolean running = true;
        private final Thread thread = new Thread(() -> {
            while (running) {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    if (w instanceof javax.swing.JDialog d && d.isShowing() && seen.add(d)) {
                        SwingUtilities.invokeLater(d::dispose);
                    }
                }
                try { Thread.sleep(25); } catch (InterruptedException e) { return; }
            }
        }, "dialog-watchdog");
        DialogWatchdog() { thread.setDaemon(true); thread.start(); }
        int seen() { return seen.size(); }
        void stop() { running = false; }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static ActionExecutor executorOf(MainFrame f) throws Exception {
        var field = MainFrame.class.getDeclaredField("actionExecutor");
        field.setAccessible(true);
        return (ActionExecutor) field.get(f);
    }

    private static Map<String, Object> render(ActionExecutor ex, String verb, Map<String, Object> params) {
        ActionResult r = ex.render(verb, new java.util.LinkedHashMap<>(params));
        assertTrue(r.ok(), verb + " " + params + " → " + r.toMap());
        return r.toMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pairing(ActionExecutor ex) {
        Map<String, Object> ctx = render(ex, "context", Map.of());
        return (Map<String, Object>) find(ctx, "graphPairing");
    }

    private static Object find(Object node, String key) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey(key)) return m.get(key);
            for (Object v : m.values()) { Object hit = find(v, key); if (hit != null) return hit; }
        } else if (node instanceof List<?> l) {
            for (Object v : l) { Object hit = find(v, key); if (hit != null) return hit; }
        }
        return null;
    }

    /** Poll context on the EDT until the pairing carries a verdict (the background load landed). */
    private static Map<String, Object> awaitVerdict(ActionExecutor ex) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        AtomicReference<Map<String, Object>> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> last.set(pairing(ex)));
            Map<String, Object> p = last.get();
            if (p != null && p.containsKey("applies")) return p;
            Thread.sleep(50);
        }
        fail("no pairing verdict within 20s; last context.graphPairing = " + last.get());
        return null;
    }

    /** Poll until no load is in flight (context.graphPairing carries no `loading`). */
    private static void awaitLoaded(ActionExecutor ex) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        AtomicReference<Map<String, Object>> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> last.set(pairing(ex)));
            if (last.get() != null && !last.get().containsKey("loading")) return;
            Thread.sleep(50);
        }
        fail("load still in flight after 20s: " + last.get());
    }

    private static <T> T onEdtGet(java.util.function.Supplier<T> body) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        onEdt(() -> out.set(body.get()));
        return out.get();
    }

    private static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { r.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() instanceof AssertionError e) throw e;
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }
}
