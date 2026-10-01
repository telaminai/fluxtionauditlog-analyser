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
        return assess(store, filtered, currentFilter, input, store == null ? 0 : store.size());
    }

    /**
     * @param bound the number of records to score, fixed by the caller when it captured its inputs (independent review
     *              R1): a qualification built from this echo then describes exactly the log revision it was stamped with
     */
    public static Result assess(LogStore store, boolean filtered, FilterState currentFilter, Input input, int bound) {
        if (store == null) throw new IllegalArgumentException("no log is loaded");
        if (input == null || input.topology() == null || input.topology().nodes().isEmpty()) {
            throw new IllegalArgumentException("no topology is loaded");
        }

        CoverageScope.Scope scope = CoverageScope.of(input.topology(), input.authored(), input.sourceResolver());
        Set<String> logged = new LinkedHashSet<>();
        List<String> levels = new ArrayList<>();
        int scanned = 0;
        int withheld = 0;   // UPS-1: records in scope whose node logs were not read (review of 9474c687, finding 3)
        // Round 3, N1: the bound is fixed before the scan and reported, so a qualification built from this echo
        // knows exactly which log revision it describes even if Follow appends while the scan runs. Integration with
        // MA-8: the rows in view, and the level changes annotating them, are read within that same bound.
        int rows = Math.max(0, Math.min(bound, store.size()));
        int[] inView = new int[rows];
        for (int row = 0; row < rows; row++) {
            if (filtered && currentFilter != null && !currentFilter.test(store.index(), row)) continue;
            inView[scanned] = row;
            scanned++;
            var record = store.record(row);
            levels.add(record.level());
            if (record.brokenAtLine() > 0) withheld++;
            for (var nodeLog : record.nodeLogs()) logged.add(nodeLog.instanceId());
        }
        NodeCoverage coverage = NodeCoverage.of(scope.loggable(), logged, Set.of());
        AuditLevel auditLevel = AuditLevel.of(levels);
        // MA-8. Changes are read UNFILTERED on purpose: a level change is configuration state, not an
        // event you happen to be looking at, so a filter that hides the control record must not drop the
        // annotation. What the filter decides is which records the annotation must be ABOUT: the rows in
        // view, by position. The earlier time-range version widened an EMPTY selection to all time, and
        // let a record at exactly a restore's instant fall inside the window it closed (review F5).
        PerNodeLevelChanges levelChanges = PerNodeLevelChanges.of(store, rows);
        inView = java.util.Arrays.copyOf(inView, scanned);

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
        echo.put("logRecords", rows);
        echo.put("scope", filtered ? "current filter" : "whole log");
        if (!coverage.uncovered().isEmpty()) echo.putAll(auditLevel.echo());
        // Annotate, never excuse (MA-8.2): the node stays in `uncovered` and in the ratio above, and
        // this says why the log may be silent about it. Excusing it would hide a node that never ran
        // whenever the qualifying record is wrong — and a control-LOOKING record can be content until
        // every writer escapes (MA-7).
        Map<String, String> annotations = new LinkedHashMap<>();
        if (levelChanges.any()) {
            for (String node : coverage.uncovered()) {
                String note = levelChanges.annotationFor(node, inView);
                if (note != null) annotations.put(node, note);
            }
            if (!annotations.isEmpty()) {
                echo.put("levelAnnotations", annotations);
                echo.put("levelAnnotationsNote",
                        "These nodes are still counted as uncovered. A level change explains why the log "
                                + "may be silent about them; it is not evidence that they ran. Records are "
                                + "matched to a processor by the grouping each one declares, so records that "
                                + "share a grouping are read as one processor's — which nothing in a record "
                                + "establishes.");
            }
        }

        // UPS-1 (review of 9474c687, finding 3): a broken record's node logs were WITHHELD, not absent. A node that wrote
        // only in such records is still counted uncovered (annotate, never excuse), but no sentence may say it never
        // wrote: output was recorded, and the analyser declined to read it.
        String uncoveredReason = uncoveredReason(withheld);
        if (withheld > 0) {
            echo.put("nodeLogsWithheld", withheld);
            echo.put("withheldNote", withheldNote(withheld));
        }
        List<Map<String, Object>> never = new ArrayList<>();
        for (String id : coverage.uncovered()) never.add(node(id, input.topology(), "uncovered", uncoveredReason));
        echo.put("neverLogged", never);
        echo.put("note", withheld > 0
                ? "a node appears here if none of its audit output was READ in this scope. " + withheld + " record(s) "
                        + "here break their own structure and their node logs were withheld, so this is not "
                        + "'never logged': whether these nodes wrote anything in those records is not known, and no "
                        + "build setting makes absence conclusive until the producer quotes its values. The covered "
                        + "count is a lower bound."
                : "a node appears here if it never wrote audit output. That is 'never logged', "
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
        if (withheld > 0) member.put("nodeLogsWithheld", withheld);
        if (logged.isEmpty()) {
            member.put("note", withheld > 0
                    ? "no node output was read in scope — " + withheld + " record(s) had their node logs withheld "
                            + "because their structure breaks — so no membership comparison was possible. This is not "
                            + "evidence that the graph describes the log, nor that the log wrote no node output"
                    : "no node output in scope, so no membership comparison was possible — this is "
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

        List<Map<String, Object>> ledger = ledger(input.topology(), input.authored(), scope, logged, uncoveredReason);
        // MA-8's report path: the ledger a report prints carries the same annotations the verb returns. The row
        // stays `uncovered` — annotate, never excuse (MA-8.2) — and gains the sentence; the notes say what it is not.
        for (Map<String, Object> row : ledger) {
            String note = annotations.get(String.valueOf(row.get("instanceId")));
            if (note != null && "uncovered".equals(row.get("status"))) row.put("levelChange", note);
        }
        List<String> notes = new ArrayList<>();
        notes.add(EntryPointResolver.HIERARCHY_NOTE);
        if (!annotations.isEmpty()) {
            notes.add(String.valueOf(echo.get("levelAnnotationsNote")));
            annotations.forEach((node, note) -> notes.add(node + ": " + note));
        }
        if (scope.note() != null) notes.add(scope.note());
        if (!coverage.uncovered().isEmpty() && auditLevel.note() != null) notes.add(auditLevel.note());
        if (echo.get("warning") != null) notes.add(echo.get("warning").toString());
        if (withheld > 0) notes.add(withheldNote(withheld));
        String scalars = "declared " + coverage.declaredCount() + " · covered " + coverage.covered().size()
                + " · uncovered " + coverage.uncovered().size() + " · ratio "
                + (ratioAvailable ? echo.get("ratio") : "none (nothing eligible to score)")
                + " · " + scanned + " records" + (withheld > 0 ? " (" + withheld + " with node logs withheld)" : "")
                + " · scope: " + echo.get("scope");
        return new Result(echo, ledger, scalars, notes);
    }

    /** The reason an uncovered node carries: "never wrote" only when every record in scope was read whole. */
    static String uncoveredReason(int withheld) {
        return withheld == 0 ? "never wrote audit output in this scope"
                : "no audit output of it was read in this scope; " + withheld + " record(s) had their node logs "
                + "withheld, so whether it wrote any is not known";
    }

    private static String withheldNote(int withheld) {
        return withheld + " record(s) in scope break their own structure (a value written unquoted with a line break in "
                + "it), so their node logs were withheld, not read. Coverage counts only what was read: covered is a "
                + "lower bound, and an uncovered node may have written output in those records.";
    }

    private static List<Map<String, Object>> ledger(ProcessorTopology topology, Set<String> authored,
                                                     CoverageScope.Scope scope, Set<String> logged,
                                                     String uncoveredReason) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> included = new LinkedHashSet<>();
        included.addAll(scope.loggable());
        included.addAll(scope.excluded().keySet());
        Set<String> seen = new LinkedHashSet<>();
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (included.contains(node.id()) && seen.add(node.id())) {
                rows.add(ledgerRow(node.id(), topology, scope, logged, uncoveredReason));
            }
        }
        for (String id : new java.util.TreeSet<>(included)) {
            if (seen.add(id)) rows.add(ledgerRow(id, topology, scope, logged, uncoveredReason));
        }
        return rows;
    }

    private static Map<String, Object> ledgerRow(String id, ProcessorTopology topology,
                                                   CoverageScope.Scope scope, Set<String> logged,
                                                   String uncoveredReason) {
        if (scope.excluded().containsKey(id)) {
            return node(id, topology, "excluded", scope.excluded().get(id));
        }
        return node(id, topology, logged.contains(id) ? "covered" : "uncovered",
                logged.contains(id) ? "wrote audit output" : uncoveredReason);
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
