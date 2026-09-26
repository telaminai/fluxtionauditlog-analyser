package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.source.SourceDocument;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.SourcePanelRootChangeTest.settle;

/**
 * Edit-loop spec §C: an ordinary source pane rechecks its file on disk when navigated to, reads it off the EDT,
 * and installs text and the selected processor's model from one read. The session this came from kept showing
 * a processor rewritten by a regeneration, and after a class rename still headed the node pane with the
 * removed class.
 */
class SourcePanelFreshnessTest {

    private static final String PROCESSOR = "com.acme.generated.MarketProcessor";
    private static final String NODE = "com.acme.node.RiskCheck";

    @TempDir Path tmp;

    private Path root() throws Exception { return Files.createDirectories(tmp.resolve("src/main/java")); }
    private Path file(String fqn) throws Exception { return root().resolve(fqn.replace('.', '/') + ".java"); }
    private void write(String fqn, String body) throws Exception {
        Path f = file(fqn); Files.createDirectories(f.getParent()); Files.writeString(f, body);
    }
    private static String processor(String type, String field) {
        return "package com.acme.generated;\nimport com.acme.node." + type + ";\npublic class MarketProcessor {\n"
                + "    private final " + type + " " + field + " = new " + type + "();\n}\n";
    }
    private static String node(String type, String body) {
        return "package com.acme.node;\npublic class " + type + " {\n    " + body + "\n}\n";
    }
    private SourcePanel panel(SourceService service) throws Exception {
        service.configure(List.of(root().toString()), PROCESSOR);
        SourcePanel panel = new SourcePanel();
        panel.bind(service);
        return panel;
    }

    @Test
    void navigatingToTheSameClassAgainShowsItsFileAsItIsNow() throws Exception {
        write(PROCESSOR, processor("RiskCheck", "riskCheck"));
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        panel.openFqn(NODE); settle(panel);
        assertTrue(panel.nodePaneText().contains("int limit = 1;"));

        write(NODE, node("RiskCheck", "int limit = 2; RiskCheck(Object a, Object b) { }"));  // body + constructor change
        panel.openFqn(NODE); settle(panel);
        assertTrue(panel.nodePaneText().contains("int limit = 2;"), "same name, changed file: " + panel.nodePaneText());
        assertEquals(NODE, panel.nodeLabel(), "the header no longer says the text is unchecked");
    }

    @Test
    void aReplacementWithTheSameSizeAndTimestampIsStillSeen() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        panel.openFqn(NODE); settle(panel);
        var stamp = Files.getLastModifiedTime(file(NODE));
        write(NODE, node("RiskCheck", "int limit = 7;"));
        Files.setLastModifiedTime(file(NODE), stamp);
        panel.openFqn(NODE); settle(panel);
        assertTrue(panel.nodePaneText().contains("int limit = 7;"), "content, not size or time, decides");
    }

    @Test
    void aDeletedClassIsNotShownAsIfItStillExisted() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        panel.openFqn(NODE); settle(panel);
        Files.delete(file(NODE));
        panel.openFqn(NODE); settle(panel);
        assertFalse(panel.nodePaneText().contains("int limit = 1;"), "its former text is not shown");
        assertTrue(panel.nodeLabel().contains("it was shown before"), panel.nodeLabel());
    }

    @Test
    void aClassThatAppearsLaterIsFoundOnTheNextNavigation() throws Exception {
        SourcePanel panel = panel(new SourceService());
        panel.openFqn(NODE); settle(panel);
        assertTrue(panel.nodePaneText().startsWith("No source to show"));
        write(NODE, node("RiskCheck", "int limit = 3;"));
        panel.openFqn(NODE); settle(panel);
        assertTrue(panel.nodePaneText().contains("int limit = 3;"));
    }

    /** The second symptom: after RootNode → MarketData, node-id navigation must land on the new class. */
    @Test
    void afterAClassRenameNodeNavigationUsesTheProcessorAsItIsNow() throws Exception {
        write(PROCESSOR, processor("RootNode", "rootNode"));
        write("com.acme.node.RootNode", node("RootNode", "double price;"));
        SourceService service = new SourceService();
        SourcePanel panel = panel(service);
        panel.showSelectedProcessor(); settle(panel);
        panel.openInstance("rootNode", null); settle(panel);
        assertEquals("com.acme.node.RootNode", panel.nodePaneFqn());

        Files.delete(file("com.acme.node.RootNode"));
        write("com.acme.node.MarketData", node("MarketData", "double price;"));
        write(PROCESSOR, processor("MarketData", "marketData"));      // the regenerated processor
        panel.openInstance("marketData", null); settle(panel);
        assertEquals("com.acme.node.MarketData", panel.nodePaneFqn(), panel.nodeLabel());
        assertTrue(panel.processorPaneText().contains("MarketData marketData"), "the processor pane was reread with it");
        assertEquals("com.acme.node.MarketData", service.fqnForInstance("marketData"), "the service model came from that read");
    }

    /**
     * PR #30 review, finding 1: two panels share one SourceService. The embedded pane read the OLD processor and
     * stalled; the Source tab then read and installed the NEW model; the stalled read must not replace it.
     */
    @Test
    void anOlderReadFromTheOtherPaneCannotReplaceANewerModel() throws Exception {
        write(PROCESSOR, processor("RootNode", "rootNode"));
        write("com.acme.node.RootNode", node("RootNode", "double price;"));
        SourceService service = new SourceService();
        SourcePanel sourceTab = panel(service);
        SourcePanel embedded = new SourcePanel();
        embedded.bind(service);
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1);
        String oldText = Files.readString(file(PROCESSOR));
        embedded.reader = (lookup, fqn) -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.of(new SourceDocument(oldText, null, null, "old.jar", "x", SourceDocument.digest(oldText)));
        };
        SwingUtilities.invokeAndWait(embedded::showSelectedProcessor);          // captures first, then stalls
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        write("com.acme.node.MarketData", node("MarketData", "double price;"));
        write(PROCESSOR, processor("MarketData", "marketData"));             // regenerated
        sourceTab.showSelectedProcessor(); settle(sourceTab);                  // captures later, installs first
        assertEquals("com.acme.node.MarketData", service.fqnForInstance("marketData"), "control: the newer read installed");
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (embedded.reading() && System.nanoTime() < deadline) { SwingUtilities.invokeAndWait(() -> { }); Thread.sleep(5); }
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals("com.acme.node.MarketData", service.fqnForInstance("marketData"),
                "an older read landing last must not replace the newer model");
    }

    /** PR #30 review, finding 4: a new name whose read times out must not keep saying "Reading X …". */
    @Test
    void aTimedOutReadOfANewNameSaysItTimedOutInTheBodyToo() throws Exception {
        SourcePanel panel = panel(new SourceService());
        CountDownLatch release = new CountDownLatch(1);
        panel.readDeadline = java.time.Duration.ofMillis(100);
        panel.reader = (lookup, fqn) -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.empty();
        };
        try {
            SwingUtilities.invokeAndWait(() -> panel.openFqn(NODE));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!panel.nodeLabel().contains("timed out") && System.nanoTime() < deadline) Thread.sleep(10);
            SwingUtilities.invokeAndWait(() -> { });
            assertFalse(panel.nodePaneText().startsWith("Reading"), "the body still says it is reading: " + panel.nodePaneText());
            assertTrue(panel.nodePaneText().toLowerCase(java.util.Locale.ROOT).contains("timed out"),
                    "the body says the read timed out: " + panel.nodePaneText());
        } finally { release.countDown(); }
    }

    /**
     * PR #30 review, finding 4: on a filesystem that hangs and ignores interrupts, navigations must not pile up
     * blocked threads without bound.
     */
    @Test
    void readsThatHangDoNotAccumulateThreadsWithoutBound() throws Exception {
        SourcePanel panel = panel(new SourceService());
        panel.readDeadline = java.time.Duration.ofMillis(50);
        var running = new java.util.concurrent.atomic.AtomicInteger();
        var most = new java.util.concurrent.atomic.AtomicInteger();
        var release = new java.util.concurrent.atomic.AtomicBoolean();
        panel.reader = (lookup, fqn) -> {
            most.accumulateAndGet(running.incrementAndGet(), Math::max);
            try { while (!release.get()) { try { Thread.sleep(5); } catch (InterruptedException ignoredLikeAHungMount) { } } }
            finally { running.decrementAndGet(); }
            return Optional.empty();
        };
        try {
            for (int i = 0; i < 20; i++) {
                String name = "com.acme.node.N" + i;
                SwingUtilities.invokeAndWait(() -> panel.openFqn(name));
                Thread.sleep(10);
            }
            Thread.sleep(200);
            assertTrue(most.get() <= 2, "hung reads held " + most.get() + " threads at once; they must be bounded");
        } finally { release.set(true); }
    }

    /** PR #30 review, finding 5: if the processor read fails or times out, the requested node says why. */
    @Test
    void aNodeRequestWhoseProcessorReadTimesOutSaysWhyItDidNotOpen() throws Exception {
        write(PROCESSOR, processor("RiskCheck", "riskCheck"));
        SourcePanel panel = panel(new SourceService());
        CountDownLatch release = new CountDownLatch(1);
        panel.readDeadline = java.time.Duration.ofMillis(100);
        panel.reader = (lookup, fqn) -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.empty();
        };
        try {
            SwingUtilities.invokeAndWait(() -> panel.openInstance("riskCheck", null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!panel.nodeLabel().contains("riskCheck") && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(panel.nodeLabel().contains("could not open node 'riskCheck'") && panel.nodeLabel().contains("timed out"),
                    "the node pane says why the requested node did not open: " + panel.nodeLabel());
        } finally { release.countDown(); }
    }

    /** PR #30 review, finding 3: a Ctrl-click's existence check does not run on the EDT. */
    @Test
    void aTypeClickChecksExistenceOffTheEdtThenOpensIt() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        CountDownLatch release = new CountDownLatch(1);
        var real = panel.existence;
        panel.existence = (lookup, fqn) -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return real.apply(lookup, fqn);
        };
        try {
            SwingUtilities.invokeLater(() -> panel.openTypeIfPresent(NODE));
            FutureTask<Boolean> sentinel = new FutureTask<>(() -> true);
            SwingUtilities.invokeLater(sentinel);
            boolean answered;
            try { answered = sentinel.get(2, TimeUnit.SECONDS); }
            catch (java.util.concurrent.TimeoutException blocked) { answered = false; }
            assertTrue(answered, "the EDT must answer while a Ctrl-click's existence check is blocked");
        } finally { release.countDown(); }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!panel.nodePaneText().contains("int limit = 1;") && System.nanoTime() < deadline) { SwingUtilities.invokeAndWait(() -> { }); Thread.sleep(10); }
        settle(panel);
        assertTrue(panel.nodePaneText().contains("int limit = 1;"), "an existing type opens once checked");
    }

    // ---- PR #30 targeted re-review, R1: the pending-work and cancellation lifecycle ------------------------------

    /** Wait until no source work is running or queued; an observable condition, not a sleep. */
    private static void awaitSourceWorkIdle() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!SourcePanel.sourceWorkIdle() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(SourcePanel.sourceWorkIdle(), "source work did not drain: queued=" + SourcePanel.pendingSourceWork());
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** A read that holds its worker, ignoring interrupts like a hung mount, until released. */
    private static java.util.function.BiFunction<SourceService.Lookup, String, Optional<SourceDocument>> holding(
            CountDownLatch entered, java.util.concurrent.atomic.AtomicBoolean release, java.util.List<String> read,
            String blocks) {
        return (lookup, fqn) -> {
            read.add(fqn);
            if (fqn.equals(blocks)) {
                entered.countDown();
                while (!release.get()) { try { Thread.sleep(2); } catch (InterruptedException ignoredLikeAHungMount) { } }
            }
            return Optional.empty();
        };
    }

    /** R1 regression 1: two blocked workers plus repeated superseding requests — pending work stays bounded. */
    @Test
    void withBothWorkersHungPendingSourceWorkStaysBounded() throws Exception {
        SourceService service = new SourceService();
        SourcePanel a = panel(service), b = new SourcePanel(); b.bind(service);
        var release = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(2);
        var read = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        a.reader = holding(entered, release, read, "com.acme.BlockA");
        b.reader = holding(entered, release, read, "com.acme.BlockB");
        a.existence = (lookup, fqn) -> { read.add("exists " + fqn); return false; };
        try {
            SwingUtilities.invokeAndWait(() -> { a.openFqn("com.acme.BlockA"); b.openFqn("com.acme.BlockB"); });
            assertTrue(entered.await(5, TimeUnit.SECONDS), "control: both workers are held");
            for (int i = 0; i < 50; i++) {
                int n = i;
                SwingUtilities.invokeAndWait(() -> { a.openFqn("com.acme.N" + n); a.openTypeIfPresent("com.acme.T" + n); });
            }
            SwingUtilities.invokeAndWait(() -> { });
            assertTrue(SourcePanel.pendingSourceWork() <= 4,
                    "superseded source work must not pile up behind hung workers: queued=" + SourcePanel.pendingSourceWork());
        } finally { release.set(true); }
        awaitSourceWorkIdle();
    }

    /** R1 regression 2: superseded queued work never performs its lookup once the workers are released. */
    @Test
    void supersededQueuedWorkNeverPerformsItsLookup() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourceService service = new SourceService();
        SourcePanel a = panel(service), b = new SourcePanel(); b.bind(service);
        var release = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(2);
        var read = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        var realRead = a.reader;
        a.reader = (lookup, fqn) -> fqn.equals(NODE) ? realRead.apply(lookup, fqn) : holding(entered, release, read, "com.acme.BlockA").apply(lookup, fqn);
        b.reader = holding(entered, release, read, "com.acme.BlockB");
        a.existence = (lookup, fqn) -> { read.add("exists " + fqn); return false; };
        try {
            SwingUtilities.invokeAndWait(() -> { a.openFqn("com.acme.BlockA"); b.openFqn("com.acme.BlockB"); });
            assertTrue(entered.await(5, TimeUnit.SECONDS), "control: both workers are held");
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 1; i <= 5; i++) a.openTypeIfPresent("com.acme.C" + i);
                for (int i = 1; i <= 5; i++) a.openFqn("com.acme.S" + i);
                a.openFqn(NODE);                                   // the current request
            });
        } finally { release.set(true); }
        awaitSourceWorkIdle();
        assertTrue(a.nodePaneText().contains("int limit = 1;"), "control: the current request completed: " + a.nodePaneText());
        var obsolete = read.stream().filter(r -> r.matches("(exists )?com\\.acme\\.[CS]\\d")).toList();
        assertEquals(List.of(), obsolete, "superseded queued work must never perform its lookup");
    }

    /** R1 regression 3: a Ctrl-click check that expired never navigates when its result finally arrives. */
    @Test
    void anExpiredTypeClickCheckNeverNavigatesWhenItsResultArrives() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        panel.readDeadline = java.time.Duration.ofMillis(100);
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1);
        var outcome = new java.util.concurrent.LinkedBlockingQueue<String>();
        panel.decisions = d -> { if (d.startsWith("type-check")) outcome.add(d); };
        panel.existence = (lookup, fqn) -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return true;
        };
        try {
            SwingUtilities.invokeAndWait(() -> panel.openTypeIfPresent(NODE));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "control: the check is running");
            String expired = outcome.poll(2, TimeUnit.SECONDS);   // the fixed code reports its expiry; the old code never does
            assertNotNull(expired, "a Ctrl-click check past its deadline must say it expired");
            assertTrue(expired.contains("expired"), expired);
        } finally { release.countDown(); }
        awaitSourceWorkIdle();
        assertFalse(panel.nodePaneText().contains("int limit = 1;"), "an expired check must never navigate: " + panel.nodePaneText());
    }

    /** R1 regression 4: a newer navigation wins over an earlier Ctrl-click check; 5: ordinary navigation works. */
    @Test
    void aNewerNavigationWinsOverAnEarlierTypeClickCheck() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        write("com.acme.node.Other", node("Other", "int other = 2;"));
        SourcePanel panel = panel(new SourceService());
        CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1);
        var outcome = new java.util.concurrent.LinkedBlockingQueue<String>();
        panel.decisions = d -> { if (d.startsWith("type-check")) outcome.add(d); };
        panel.existence = (lookup, fqn) -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return true;
        };
        try {
            SwingUtilities.invokeAndWait(() -> panel.openTypeIfPresent("com.acme.node.Other"));
            assertTrue(entered.await(5, TimeUnit.SECONDS), "control: the check is running");
            SwingUtilities.invokeAndWait(() -> panel.openFqn(NODE));   // the newer navigation
        } finally { release.countDown(); }
        awaitSourceWorkIdle();
        String decided = outcome.poll(2, TimeUnit.SECONDS);
        assertTrue(decided == null || decided.contains("discarded"), "the earlier check must not navigate: " + decided);
        assertTrue(panel.nodePaneText().contains("int limit = 1;"), "the newer navigation is shown: " + panel.nodePaneText());
        assertEquals(NODE, panel.nodePaneFqn(), "ordinary navigation still lands");
    }

    /** A read that blocks never blocks the EDT; a project switch while it blocks drops its answer. */
    @Test
    void aBlockedReadLeavesTheEdtResponsiveAndASupersededAnswerIsDropped() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourceService service = new SourceService();
        SourcePanel panel = panel(service);
        CountDownLatch release = new CountDownLatch(1);
        var real = panel.reader;
        panel.reader = (lookup, fqn) -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.of(new SourceDocument("// stale answer\n", null, null, "blocked.jar", "x", "0".repeat(64)));
        };
        try {
            SwingUtilities.invokeLater(() -> panel.openFqn(NODE));
            FutureTask<Boolean> sentinel = new FutureTask<>(() -> true);
            SwingUtilities.invokeLater(sentinel);
            boolean answered;
            try { answered = sentinel.get(2, TimeUnit.SECONDS); }
            catch (java.util.concurrent.TimeoutException blocked) { answered = false; }
            assertTrue(answered, "the EDT must answer while the read is blocked");
            assertTrue(panel.reading());
            SwingUtilities.invokeAndWait(() -> service.configure(List.of(tmp.resolve("elsewhere").toString()), PROCESSOR));
        } finally { release.countDown(); }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (panel.reading() && System.nanoTime() < deadline) { SwingUtilities.invokeAndWait(() -> { }); Thread.sleep(5); }
        SwingUtilities.invokeAndWait(() -> { });
        assertFalse(panel.nodePaneText().contains("stale answer"), "an answer for the old configuration is not installed");
        panel.reader = real;
    }

    @Test
    void aReadPastItsDeadlineSaysSoAndItsLateAnswerIsIgnored() throws Exception {
        write(NODE, node("RiskCheck", "int limit = 1;"));
        SourcePanel panel = panel(new SourceService());
        panel.openFqn(NODE); settle(panel);
        CountDownLatch release = new CountDownLatch(1);
        panel.readDeadline = java.time.Duration.ofMillis(100);
        panel.reader = (lookup, fqn) -> {
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return Optional.of(new SourceDocument("// late answer\n", null, null, "late.jar", "x", "1".repeat(64)));
        };
        CountDownLatch answered = new CountDownLatch(1);
        var lateDecision = new java.util.concurrent.LinkedBlockingQueue<String>();
        panel.decisions = d -> { if (d.startsWith("discarded " + NODE)) lateDecision.add(d); };
        var late = panel.reader;
        panel.reader = (lookup, fqn) -> { try { return late.apply(lookup, fqn); } finally { answered.countDown(); } };
        try {
            SwingUtilities.invokeAndWait(() -> panel.openFqn(NODE));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!panel.nodeLabel().contains("timed out") && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(panel.nodeLabel().contains("timed out; the text below is unchecked"), panel.nodeLabel());
        } finally { release.countDown(); }
        assertTrue(answered.await(5, TimeUnit.SECONDS), "the late read returned");
        // O1: wait for the late completion to reach its decision on the EDT — not two flushes that can run before
        // it is even posted — then assert what it decided
        String decided = lateDecision.poll(5, TimeUnit.SECONDS);
        assertEquals("discarded " + NODE + ": superseded or expired", decided, "the late completion reached its decision");
        assertFalse(panel.nodePaneText().contains("late answer"), "an answer past its deadline must never be installed");
        assertTrue(panel.nodePaneText().contains("int limit = 1;"), "the earlier text stays, labelled unchecked");
    }
}
