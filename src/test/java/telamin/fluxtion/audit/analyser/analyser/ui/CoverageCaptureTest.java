package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.AppControl;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review R1 (2026-09-26): coverage used to take the store, the session snapshot and the graph at three
 * different instants, and read the LIVE filter while it scanned. A log opened between the first two was stamped onto
 * the old store's comparison, and the session accepted a foreign id as a qualification of the new log
 * ({@code review-probe/CoverageRaceProbe}). The inputs are now captured together, in one task on the EDT, where every
 * log, graph and filter change happens; the scan runs off it, on a copy of the filter and a fixed bound.
 *
 * <p>Deterministic, no timing: a supplier the capture calls queues the competing change itself. Called on the EDT, it
 * can only queue the change behind the capture; called off it (the old shape), it runs the change before returning,
 * which is the review's interleaving exactly.
 */
class CoverageCaptureTest {

    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");

    private static String record(String node) {
        return "eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - " + node + ": {v: 1}\n---\n";
    }

    private static void open(SessionDriver d, String path, int total) {
        long id = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(id, path, null, "DECLARED", false));
        d.submit(new SessionEvents.LogOpened(id, path, "DECLARED", Set.of("rootNode"), total, total, "TRACE"));
    }

    /** Run {@code change} as the capture would meet it: behind the capture on the EDT, or immediately off it. */
    private static void competing(Runnable change) {
        if (SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(change);
        } else {
            try {
                SwingUtilities.invokeAndWait(change);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Everything a coverage call meets, built on the EDT (the driver is confined to the thread that made it). */
    private static final class Rig {
        SessionDriver driver;
        TopologyPanel panel;
        ActionExecutor executor;
        final AtomicReference<LogStore> current = new AtomicReference<>();
        final AtomicReference<FilterState> filter = new AtomicReference<>(new FilterState());

        Rig(LogStore first, java.util.function.Function<Rig, java.util.function.Supplier<LogStore>> storeSupplier,
            java.util.function.Function<Rig, java.util.function.Supplier<telamin.fluxtion.audit.analyser.analyser.session.SessionSnapshot>> snapshotSupplier)
                throws Exception {
            current.set(first);
            SwingUtilities.invokeAndWait(() -> {
                driver = new SessionDriver(e -> e instanceof SessionEffects.OpenLogEffect
                        ? new SessionEvents.Pending(e.opId(), "openLog") : new SessionEvents.StatusShown(e.opId(), "shown"));
                open(driver, "old.log", first.size());
                panel = new TopologyPanel();
                panel.load(GRAPH);
                driver.post(new SessionEvents.GraphOpened("demo.graphml", "OPENED", Set.of("rootNode"), List.of("EventLogManager")));
                var tabs = new GraphTabs();
                tabs.bind(first, filter.get());
                executor = new ActionExecutor(storeSupplier.apply(this), filter::get, tabs, new LogTablePanel(), (r, n, f, k) -> { });
                AppControl app = (AppControl) Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                        new Class<?>[]{AppControl.class}, (o, m, a) -> {
                            if (m.getName().equals("qualifyPublishedPairing")) {
                                @SuppressWarnings("unchecked") Map<String, Object> echo = (Map<String, Object>) a[0];
                                driver.post(new SessionEvents.MembershipCompared((Long) echo.get(ActionExecutor.PAIR_LOG_GENERATION),
                                        (Long) echo.get(ActionExecutor.PAIR_GRAPH_REVISION), echo));
                                return driver.processor().pairingQualifier.lastSaid();
                            }
                            if (m.isDefault()) return InvocationHandler.invokeDefault(o, m, a);
                            return null;
                        });
                executor.bind(panel, app);
                executor.bindSessionSnapshot(snapshotSupplier.apply(this));
            });
        }

        String qualification() {
            var s = driver.snapshot();
            var q = s.qualifications();
            return q == null ? null : String.valueOf(q.toMap(s.total(), s.filterKey()));
        }
    }

    @Test
    @DisplayName("R1: a log opened while coverage prepares cannot give the old log's comparison the new log's identity")
    void oldInputsCannotAcquireANewIdentity() throws Exception {
        var old = new HeapLogStore(record("foreignOldLog"));
        var fresh = new HeapLogStore(record("rootNode") + record("rootNode"));
        var switched = new AtomicBoolean();
        var rig = new Rig(old, r -> () -> {
            LogStore got = r.current.get();
            if (!switched.getAndSet(true)) competing(() -> { r.current.set(fresh); open(r.driver, "new.log", 2); });
            return got;
        }, r -> () -> r.driver.snapshot());

        ActionResult reply = rig.executor.render("coverage", Map.of());
        flushEdt();

        assertEquals(2, rig.driver.snapshot().logGeneration(), "the competing open ran");
        assertTrue(reply.ok(), String.valueOf(reply.toMap()));
        String q = rig.qualification();
        assertTrue(q == null || !q.contains("foreignOldLog"),
                "R1: the new log must not be qualified by the old log's comparison: " + q);
        assertTrue(String.valueOf(reply.payload().get("superseded")).contains("log generation 1"),
                "R1: the reply says it describes the pair it was captured with: " + reply.toMap());
    }

    @Test
    @DisplayName("R1: a graph opened while coverage prepares is not scored under the old graph's revision, or vice versa")
    void aGraphChangeDuringPreparationIsNotStampedNew() throws Exception {
        var store = new HeapLogStore(record("foreignId"));
        var switched = new AtomicBoolean();
        var rig = new Rig(store, r -> r.current::get, r -> () -> {
            var snap = r.driver.snapshot();
            if (!switched.getAndSet(true)) competing(() -> {
                r.panel.load(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
                r.driver.post(new SessionEvents.GraphOpened("other.graphml", "OPENED", Set.of("quoteNode"), List.of("EventLogManager")));
            });
            return snap;
        });
        long revisionBefore = rig.driver.snapshot().graphRevision();

        ActionResult reply = rig.executor.render("coverage", Map.of());
        flushEdt();

        assertNotEquals(revisionBefore, rig.driver.snapshot().graphRevision(), "the competing graph open ran");
        String q = rig.qualification();
        assertTrue(q == null || !q.contains("foreignId"),
                "R1: the new graph must not be qualified by a comparison scored against the old one: " + q);
        assertNotNull(reply.payload().get("superseded"), "R1: and the reply says so: " + reply.toMap());
    }

    @Test
    @DisplayName("R1: the scan uses the filter it captured, not the live one changed while it ran")
    void theScanUsesTheFilterItCaptured() throws Exception {
        var inner = new HeapLogStore(record("rootNode") + record("rootNode") + record("rootNode"));
        var rig = new AtomicReference<Rig>();
        var changed = new AtomicBoolean();
        LogStore watched = new ForwardingStore(inner) {
            @Override public LogRecord record(int row) {
                // only from INSIDE the scan: other readers touch record 0 before coverage captures anything
                if (!SwingUtilities.isEventDispatchThread() && inScan() && !changed.getAndSet(true)) {
                    competing(() -> rig.get().filter.get().setText("matches nothing at all"));
                }
                return super.record(row);
            }
        };
        rig.set(new Rig(watched, r -> r.current::get, r -> () -> r.driver.snapshot()));

        ActionResult reply = rig.get().executor.render("coverage", Map.of("filtered", true));
        flushEdt();

        assertTrue(changed.get(), "the live filter was changed during the scan");
        assertEquals(3, ((Number) reply.payload().get("recordsScanned")).intValue(),
                "R1: every record passed the filter as it was when coverage was asked: " + reply.toMap());
    }

    @Test
    @DisplayName("R1: the scan itself runs off the EDT — only the capture is on it")
    void theScanRunsOffTheEdt() throws Exception {
        var inner = new HeapLogStore(record("rootNode"));
        var onEdt = new AtomicBoolean();
        LogStore watched = new ForwardingStore(inner) {
            @Override public LogRecord record(int row) {
                if (SwingUtilities.isEventDispatchThread()) onEdt.set(true);
                return super.record(row);
            }
        };
        var rig = new Rig(watched, r -> r.current::get, r -> () -> r.driver.snapshot());
        rig.executor.render("coverage", Map.of());
        assertFalse(onEdt.get(), "the whole-log scan must not hold the UI thread");
    }

    private static boolean inScan() {
        return java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(f -> f.getClassName().endsWith("CoverageService") && f.getMethodName().equals("assess"));
    }

    /** A store that forwards everything, so a test can watch one call. */
    private static class ForwardingStore implements LogStore {
        private final LogStore d;
        ForwardingStore(LogStore d) { this.d = d; }
        @Override public int size() { return d.size(); }
        @Override public LogIndex index() { return d.index(); }
        @Override public LogRecord record(int row) { return d.record(row); }
        @Override public String rawText(int row) { return d.rawText(row); }
        @Override public Long minLogTime() { return d.minLogTime(); }
        @Override public Long maxLogTime() { return d.maxLogTime(); }
    }
}
