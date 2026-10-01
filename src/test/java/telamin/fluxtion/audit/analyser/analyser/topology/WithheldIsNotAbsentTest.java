package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.export.RecordExporter;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy;
import telamin.fluxtion.audit.analyser.analyser.ui.LogTableModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPS-1, the review of 9474c687, finding 3: a broken record's node logs are WITHHELD — output was recorded and the
 * analyser declined to read it — so no surface may say a node never wrote, that no node output was recorded, or that a
 * record carries 0 node logs. Every record of the fixture is broken, and each withheld block holds genuine
 * {@code alarmMonitor} output (the second also {@code alarmPublisher}); the graph is the one generated for that
 * processor. DEMO values only.
 */
class WithheldIsNotAbsentTest {

    private static String resource(String path) {
        try (InputStream in = WithheldIsNotAbsentTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static HeapLogStore allBroken() {
        return new HeapLogStore(resource("/ups1/all-broken-with-node-output.yaml"));
    }

    static ProcessorTopology cycleAlarmGraph() {
        return GraphMlParser.parse(resource("/topology/demo-cycle-alarm-processor.graphml"));
    }

    @Test
    @DisplayName("the fixture is what it says: two records, both broken, none of their node logs read")
    void theFixtureIsAllBroken() {
        HeapLogStore store = allBroken();
        assertEquals(2, store.size());
        for (int row = 0; row < store.size(); row++) {
            assertTrue(store.record(row).brokenAtLine() > 0, "record " + row + " is broken");
            assertTrue(store.index().nodeLogsWithheld(row), "record " + row + " is marked withheld in the index");
            assertTrue(store.rawText(row).contains("alarmMonitor"), "and its text holds genuine node output");
        }
    }

    @Test
    @DisplayName("coverage reports the nodes as not read, and never as 'never wrote', on the verb and the report ledger")
    void withheldLogsMustNotClaimNeverLogged() {
        ProcessorTopology topology = cycleAlarmGraph();
        CoverageService.Result result = CoverageService.assess(allBroken(), false, null,
                new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
        Map<String, Object> echo = result.echo();
        assertEquals(2, echo.get("nodeLogsWithheld"), "the echo states how many records were withheld: " + echo);
        String all = echo.toString() + result.ledger() + result.notes() + result.scalarLine();
        assertFalse(all.contains("never wrote audit output"), "no surface says a node never wrote: " + all);
        assertFalse(all.contains("make absence conclusive."), "and none says a build setting makes absence conclusive");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> never = (List<Map<String, Object>>) echo.get("neverLogged");
        assertFalse(never.isEmpty(), "the nodes are still counted uncovered: annotate, never excuse");
        for (Map<String, Object> row : never) {
            assertTrue(String.valueOf(row.get("reason")).contains("withheld"), "the reason says withheld: " + row);
        }
        for (Map<String, Object> row : result.ledger()) {
            if ("uncovered".equals(row.get("status"))) {
                assertTrue(String.valueOf(row.get("reason")).contains("not known"), "the report row: " + row);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> membership = (Map<String, Object>) echo.get("membership");
        assertTrue(String.valueOf(membership.get("note")).contains("withheld"), "membership: " + membership);
        assertTrue(String.valueOf(membership.get("note")).contains("nor that the log wrote no node output"));
        assertTrue(result.scalarLine().contains("2 with node logs withheld"), result.scalarLine());
    }

    @Test
    @DisplayName("a log read whole keeps its 'never wrote' wording: the qualification is for withheld logs only")
    void aWholeLogIsUnchanged() {
        ProcessorTopology topology = GraphMlParser.parse(resource("/topology/demo-quote-processor-noaudit.graphml"));
        CoverageService.Result result = CoverageService.assess(new HeapLogStore(resource("/topology/demo-quote-audit.yaml")),
                false, null, new CoverageService.Input(topology, Scaffolding.authoredNodes(topology), null));
        assertNull(result.echo().get("nodeLogsWithheld"));
        assertFalse(result.echo().toString().contains("withheld"));
    }

    @Test
    @DisplayName("the pairing and the coverage policy say no node output was READ, never that none was recorded")
    void withheldLogsMustNotClaimNoOutputRecorded() {
        GraphPairing pairing = GraphPairing.of(GraphPairing.declaredNodeIds(cycleAlarmGraph()), Set.of(), 2);
        assertEquals(2, pairing.nodeLogsWithheld());
        assertFalse(pairing.reason().contains("recorded"), pairing.reason());
        assertTrue(pairing.reason().contains("no node output was read"), pairing.reason());
        assertTrue(pairing.note().contains("2 withheld"), pairing.note());
        assertEquals(2, pairing.facts().get("nodeLogsWithheld"));
        assertEquals(2, pairing.withScope(2, 2).nodeLogsWithheld(), "the count survives a scope");
        assertEquals(2, pairing.withScope(2, 2).rescoped(3).nodeLogsWithheld(), "and a rescope");
        CoveragePolicy.Assessment a = CoveragePolicy.decide(true, true, "OPENED", CoveragePolicy.AuditInstalled.YES, pairing, 2, 2, "INFO");
        assertFalse(a.reason().contains("no node output was recorded"), a.reason());
        assertTrue(a.reason().contains("no node output was read"), a.reason());

        GraphPairing partial = GraphPairing.of(Set.of("alarmMonitor", "alarmPublisher"), Set.of("alarmMonitor"), 1);
        assertTrue(partial.reason().contains("1 record(s) checked had their node logs withheld"), partial.reason());
        assertEquals(GraphPairing.of(Set.of("a"), Set.of("a")).reason(), GraphPairing.of(Set.of("a"), Set.of("a"), 0).reason(),
                "nothing withheld: the reason is unchanged");
    }

    @Test
    @DisplayName("the CSV export and the records table distinguish withheld from zero")
    void exportedCountsDistinguishUnreadFromZero() {
        HeapLogStore store = allBroken();
        String[] csv = RecordExporter.toCsv(store, new FilterState()).split("\n");
        assertEquals(3, csv.length, String.join("\n", csv));
        for (int i = 1; i < csv.length; i++) {
            assertTrue(csv[i].endsWith(",withheld"), "the nodeLogs cell says withheld, not 0: " + csv[i]);
        }
        LogTableModel model = new LogTableModel(store);
        for (int row = 0; row < model.getRowCount(); row++) {
            assertNull(model.getValueAt(row, LogTableModel.COL_NODE_LOGS), "the table cell is empty, never 0");
        }
    }

    @Test
    @DisplayName("an all-broken log is named as broken values, never as a log in which no node logged")
    void theFindingIsTheBreakNotNoNodeLogs() {
        HeapLogStore store = allBroken();
        ProducerDiagnostics d = ProducerDiagnostics.of(store.index(), store::rawText);
        assertTrue(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.BROKEN_VALUE), d.messages().toString());
        assertTrue(d.findings().stream().noneMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_NODE_LOGS),
                "'no node logged anything … built without addEventAudit()' would blame the build: " + d.messages());
    }
}
