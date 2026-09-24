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
                        SourceResolver sourceResolver) {
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
        echo.put("dispatchHierarchy", "unknown");
        echo.put("dispatchNote", EntryPointResolver.HIERARCHY_NOTE);
        echo.put("declared", coverage.declaredCount());
        if (!scope.excluded().isEmpty()) {
            echo.put("excludedFromDenominator", scope.excluded());
            echo.put("excludedNote", scope.note());
        }
        echo.put("covered", coverage.covered().size());
        echo.put("uncovered", coverage.uncovered().size());
        // M68.1 (D-E1): nothing eligible to score is NO RATIO, not full coverage. NodeCoverage.ratio() is
        // vacuously 1.0 there, which printed a confident 100% from no evidence. Membership is reported
        // separately below and can still be fully established when this is absent.
        boolean ratioAvailable = coverage.denominator() > 0;
        echo.put("ratioAvailable", ratioAvailable);
        if (ratioAvailable) {
            echo.put("ratio", Math.round(coverage.ratio() * 1000) / 1000.0);
        } else {
            echo.put("ratioNote", "no ratio: this graph has no node eligible to score in coverage, so there "
                    + "is nothing to divide by. That is not full coverage, and it says nothing about whether "
                    + "the log's node ids are declared — see membership");
        }
        echo.put("authorshipBasis", Scaffolding.authorshipBasis(input.topology()));
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
        // M68.1 (D-E1): MEMBERSHIP is answered against every declared node vertex, and against nothing
        // else. It used to subtract the authored coverage population from the logged ids, so a framework
        // node that logs — or a framework-classed node the author used and named — was reported as absent
        // from a graph that declares it, and the warning then concluded a different build. Two defects
        // compounded; the membership half is fixed here, independently of how authorship is classified,
        // so it holds even for a legacy graph whose authorship still has to be inferred.
        ProcessorTopology.Match membership = input.topology().match(logged);
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("basis", "every declared node vertex in the graph (" + input.topology().nodeCount() + ")");
        member.put("established", !logged.isEmpty());
        member.put("loggedIds", logged.size());
        member.put("declaredOfLogged", membership.matched().size());
        member.put("scope", filtered ? "current filter" : "whole log");
        if (logged.isEmpty()) {
            member.put("note", "no node output in scope, so no membership comparison was possible — this is "
                    + "not evidence that the graph describes the log");
        }
        echo.put("membership", member);
        Set<String> outOfTopology = membership.unknownToTopology();
        if (!outOfTopology.isEmpty()) {
            echo.put("loggedButNotInTopology", outOfTopology.stream().limit(20).toList());
            echo.put("warning", outOfTopology.size() + " node id(s) written in this log are not declared "
                    + "anywhere in the graph (" + input.topology().nodeCount() + " declared nodes compared). "
                    + "Coverage figures above describe the graph, not those ids. Which artefact is right is "
                    + "for the reader to decide: a name mismatch does not establish a build.");
        }

        // Framework nodes leave the scored population by construction, and until now they left the
        // report with it: "every exclusion disclosed" needs a destination that is NOT the denominator.
        List<String> framework = new ArrayList<>();
        for (ProcessorTopology.Node n : input.topology().nodes()) {
            if (n.kind() == ProcessorTopology.Kind.EVENT || n.kind() == ProcessorTopology.Kind.EXPORT_SERVICE) continue;
            if (!input.authored().contains(n.id())) framework.add(n.id());
        }
        if (!framework.isEmpty()) {
            Map<String, Object> fw = new LinkedHashMap<>();
            fw.put("count", framework.size());
            fw.put("ids", framework.stream().limit(20).toList());
            fw.put("basis", Scaffolding.authorshipBasis(input.topology()));
            fw.put("note", "framework plumbing: declared in the graph, never scored in coverage, and not "
                    + "counted in 'declared' above");
            echo.put("frameworkNodesNotScored", fw);
        }

        List<Map<String, Object>> ledger = ledger(input.topology(), input.authored(), scope, logged);
        List<String> notes = new ArrayList<>();
        notes.add(EntryPointResolver.HIERARCHY_NOTE);
        if (scope.note() != null) notes.add(scope.note());
        if (!coverage.uncovered().isEmpty() && auditLevel.note() != null) notes.add(auditLevel.note());
        if (echo.get("warning") != null) notes.add(echo.get("warning").toString());
        String scalars = "declared " + coverage.declaredCount() + " · covered " + coverage.covered().size()
                + " · uncovered " + coverage.uncovered().size() + " · ratio "
                + (ratioAvailable ? echo.get("ratio") : "none (nothing eligible to score)")
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
