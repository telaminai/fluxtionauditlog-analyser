package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The topology-aware half of {@code coverage}, shared by the action echo and report tables. A coverage
 * report must use the same denominator, exclusions and audit-level caveat as the interactive answer;
 * two implementations would eventually disagree about the one number a reader is meant to check.
 */
public final class CoverageService {

    private CoverageService() {
    }

    /** The topology facts needed to score coverage without a Swing dependency. */
    public record Input(ProcessorTopology topology, Set<String> authored,
                        Function<String, Optional<String>> sourceResolver) {
        public Input {
            authored = authored == null ? Set.of() : Set.copyOf(authored);
        }
    }

    /** The regular action echo, plus the complete graph-ordered ledger for a report table. */
    public record Result(Map<String, Object> echo, List<Map<String, Object>> ledger,
                         String scalarLine, List<String> notes) {
        public Result {
            echo = Map.copyOf(echo);
            ledger = List.copyOf(ledger);
            notes = List.copyOf(notes);
        }
    }

    public static Result assess(LogStore store, boolean filtered, FilterState currentFilter, Input input) {
        if (store == null) throw new IllegalArgumentException("no log is loaded");
        if (input == null || input.topology() == null || input.topology().nodes().isEmpty()) {
            throw new IllegalArgumentException("no topology is loaded");
        }

        CoverageScope.Scope scope = CoverageScope.of(input.topology(), input.authored(), input.sourceResolver());
        Set<String> logged = new LinkedHashSet<>();
        List<String> levels = new ArrayList<>();
        int scanned = 0;
        for (int row = 0; row < store.size(); row++) {
            if (filtered && currentFilter != null && !currentFilter.test(store.index(), row)) continue;
            scanned++;
            levels.add(store.record(row).level());
            for (var nodeLog : store.record(row).nodeLogs()) logged.add(nodeLog.instanceId());
        }
        NodeCoverage coverage = NodeCoverage.of(scope.loggable(), logged, Set.of());
        AuditLevel auditLevel = AuditLevel.of(levels);

        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("declared", coverage.declaredCount());
        if (!scope.excluded().isEmpty()) {
            echo.put("excludedFromDenominator", scope.excluded());
            echo.put("excludedNote", scope.note());
        }
        echo.put("covered", coverage.covered().size());
        echo.put("uncovered", coverage.uncovered().size());
        echo.put("ratio", Math.round(coverage.ratio() * 1000) / 1000.0);
        echo.put("recordsScanned", scanned);
        echo.put("scope", filtered ? "current filter" : "whole log");
        if (!coverage.uncovered().isEmpty()) echo.putAll(auditLevel.echo());

        List<Map<String, Object>> never = new ArrayList<>();
        for (String id : coverage.uncovered()) never.add(node(id, input.topology(), "uncovered",
                "never wrote audit output in this scope"));
        echo.put("neverLogged", never);
        echo.put("note", "a node appears here if it never wrote audit output. That is 'never logged', "
                + "not proven 'never ran' — a node with no auditLog call, or one whose dirty contract "
                + "stops it early, is silent by design. Build with addEventAudit(LogLevel.TRACE) to make "
                + "absence conclusive.");
        Set<String> outOfTopology = new LinkedHashSet<>(logged);
        outOfTopology.removeAll(scope.loggable());
        outOfTopology.removeAll(scope.excluded().keySet());
        if (!outOfTopology.isEmpty()) {
            echo.put("loggedButNotInTopology", outOfTopology.stream().limit(20).toList());
            echo.put("warning", "instanceIds in the log are absent from the topology — the graphml is "
                    + "probably from a different build, which makes every other figure here suspect");
        }

        List<Map<String, Object>> ledger = ledger(input.topology(), input.authored(), scope, logged);
        List<String> notes = new ArrayList<>();
        if (scope.note() != null) notes.add(scope.note());
        if (!coverage.uncovered().isEmpty() && auditLevel.note() != null) notes.add(auditLevel.note());
        if (echo.get("warning") != null) notes.add(echo.get("warning").toString());
        String scalars = "declared " + coverage.declaredCount() + " · covered " + coverage.covered().size()
                + " · uncovered " + coverage.uncovered().size() + " · ratio " + echo.get("ratio")
                + " · " + scanned + " records · scope: " + echo.get("scope");
        return new Result(echo, ledger, scalars, notes);
    }

    private static List<Map<String, Object>> ledger(ProcessorTopology topology, Set<String> authored,
                                                     CoverageScope.Scope scope, Set<String> logged) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> included = new LinkedHashSet<>();
        included.addAll(scope.loggable());
        included.addAll(scope.excluded().keySet());
        Set<String> seen = new LinkedHashSet<>();
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (included.contains(node.id()) && seen.add(node.id())) {
                rows.add(ledgerRow(node.id(), topology, scope, logged));
            }
        }
        for (String id : new java.util.TreeSet<>(included)) {
            if (seen.add(id)) rows.add(ledgerRow(id, topology, scope, logged));
        }
        return rows;
    }

    private static Map<String, Object> ledgerRow(String id, ProcessorTopology topology,
                                                   CoverageScope.Scope scope, Set<String> logged) {
        if (scope.excluded().containsKey(id)) {
            return node(id, topology, "excluded", scope.excluded().get(id));
        }
        return node(id, topology, logged.contains(id) ? "covered" : "uncovered",
                logged.contains(id) ? "wrote audit output" : "never wrote audit output in this scope");
    }

    private static Map<String, Object> node(String id, ProcessorTopology topology, String status, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("instanceId", id);
        ProcessorTopology.Node node = topology.node(id);
        if (node != null && node.className() != null) row.put("class", node.className());
        row.put("status", status);
        row.put("reason", reason);
        return row;
    }
}
