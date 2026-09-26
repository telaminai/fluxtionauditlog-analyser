package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportCoverage;
import telamin.fluxtion.audit.analyser.analyser.report.ReportRenderer;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportVerb;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.topology.CoverageService;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review R2 (2026-09-26): an exported coverage table obeys the SESSION's coverage verdict, the same one the
 * {@code coverage} verb states. On a retained graph that does not describe the log the verb refused and the PDF printed
 * "declared 3 · covered 0 · ratio 0.0" ({@code review-probe/ReportCoverageProbe}). Each case here puts the action and
 * the exported artefact side by side on one session state, and the positive control shows the artefact still prints a
 * ratio when the session allows one — so a refusal is not simply every table going blank.
 */
class ReportCoverageTest {

    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");

    private static String record(String node) {
        return "eventLogRecord:\n  event: Tick\n  logTime: 1\n  level: TRACE\n  nodeLogs:\n    - " + node + ": {v: 1}\n---\n";
    }

    /** One session state and both surfaces over it. */
    private record Sides(ActionResult action, ReportVerb.CoverageData data, String pdf,
                         telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy.Assessment claim) { }

    private static Sides sides(String loggedId, String graphSource, List<String> nodeTypes) throws Exception {
        var store = new HeapLogStore(record(loggedId));
        var executor = new AtomicReference<ActionExecutor>();
        var report = new AtomicReference<Sides>();
        // the session is confined to the thread that made it, and the report is assembled on the EDT, as in the app
        SwingUtilities.invokeAndWait(() -> {
            var d = new SessionDriver(e -> e instanceof SessionEffects.OpenLogEffect
                    ? new SessionEvents.Pending(e.opId(), "openLog") : new SessionEvents.StatusShown(e.opId(), "shown"));
            long id = d.nextOpId();
            d.submit(new SessionEvents.OpenLogRequested(id, "demo.log", null, "DECLARED", false));
            d.submit(new SessionEvents.LogOpened(id, "demo.log", "DECLARED", Set.of(loggedId), 1, 1, "TRACE"));
            d.post(new SessionEvents.GraphOpened("demo.graphml", graphSource, Set.of("rootNode"), nodeTypes));
            var panel = new TopologyPanel();
            panel.load(GRAPH);
            var filter = new FilterState();
            var tabs = new GraphTabs();
            tabs.bind(store, filter);
            var ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
            ex.bind(panel, null);
            ex.bindSessionSnapshot(d::snapshot);
            executor.set(ex);

            var claim = d.snapshot().claim();
            var input = new CoverageService.Input(panel.fullTopology(), panel.authoredNodeIds(), panel.sourceResolver());
            var data = ReportCoverage.forReport(store, input, claim, false, filter);
            var section = ReportSpec.SectionSpec.table(Map.of("verb", "coverage"), List.of(), null, null);
            var assembled = ReportVerb.assembleTable(section, store, filtered -> data);
            var spec = new ReportSpec("cov", "Coverage review", "2026-09-26T10:00:00Z", "",
                    LogFingerprint.of(store.index(), "demo.yaml"), FilterSnapshot.all(), List.of(section));
            var resolution = ReportResolver.resolve(spec, store.index(), Map.of(), Set.of(), Set.of(), new FilterState());
            byte[] pdf = ReportRenderer.render(spec, resolution, List.of(new ReportRenderer.SectionContent(
                    "Coverage", null, null, assembled.table(), assembled.notes())), "demo.yaml", null);
            report.set(new Sides(null, data, new String(pdf, StandardCharsets.ISO_8859_1), claim));
        });
        // the verb is asked from the socket side, off the EDT, exactly as a client asks it
        ActionResult action = executor.get().render("coverage", Map.of());
        var r = report.get();
        return new Sides(action, r.data(), r.pdf(), r.claim());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"retained mismatched graph", "inferred graph", "no auditor on the graph"})
    @DisplayName("R2: where the coverage verb refuses, the exported table states the same refusal and prints no ratio")
    void theArtefactStatesTheActionsRefusal(String kind) throws Exception {
        Sides s = switch (kind) {
            case "retained mismatched graph" -> sides("foreignOnly", "OPENED", List.of("EventLogManager"));
            case "inferred graph" -> sides("rootNode", "INFERRED", List.of("EventLogManager"));
            default -> sides("rootNode", "OPENED", List.of("RootNode"));
        };
        assertNotNull(s.claim(), kind);
        assertFalse(s.claim().allowed(), kind + ": the session refuses — the precondition of this case: " + s.claim());

        assertFalse(s.action().ok(), kind + ": the verb refuses: " + s.action().toMap());
        String said = String.valueOf(s.action().toMap().get("error"));
        assertEquals(s.claim().reason() + ".", said, kind + ": the verb states the session's reason");

        assertEquals(ReportCoverage.refusal(s.claim()), s.data().emptyReason(),
                kind + ": R2 the table's reason is the same refusal: " + s.data());
        assertNull(s.data().scalarLine(), kind + ": R2 no scalar line: " + s.data());
        assertTrue(s.data().rows().isEmpty(), kind + ": R2 no ledger rows under a refusal");
        assertTrue(s.pdf().contains("coverage REFUSED"), kind + ": R2 the exported PDF states the refusal");
        assertFalse(s.pdf().contains(" ratio "), kind + ": R2 the exported PDF prints no ratio");
    }

    @Test
    @DisplayName("R2 positive control: where the session allows coverage, the artefact still carries the whole ledger and a ratio")
    void anAllowedClaimStillPrintsTheLedger() throws Exception {
        Sides s = sides("rootNode", "OPENED", List.of("EventLogManager"));
        assertTrue(s.claim() == null || s.claim().allowed(), "precondition: allowed — " + s.claim());
        assertTrue(s.action().ok(), s.action().toMap().toString());
        assertFalse(s.data().rows().isEmpty(), "the whole ledger");
        assertTrue(s.pdf().contains(" ratio "), "and its ratio");
    }

    @Test
    @DisplayName("R2 static guard: the frame asks the session-bound helper; it scores and permits nothing itself")
    void theFrameDoesNotDecideCoverageForAReport() throws Exception {
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        List<String> offenders = new ArrayList<>();
        for (String call : List.of("CoverageService.assess(", "supportsCoverage()")) {
            if (frame.contains(call)) offenders.add(call);
        }
        assertEquals(List.of(), offenders, "a second coverage decision in the frame is what R2 found");
        assertTrue(frame.contains("ReportCoverage.forReport("), "the report's table goes through the session-bound helper");
    }
}
