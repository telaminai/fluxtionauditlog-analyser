package telamin.fluxtion.audit.analyser.analyser.report;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy;
import telamin.fluxtion.audit.analyser.analyser.topology.CoverageService;

import java.util.ArrayList;
import java.util.List;

/**
 * A report's coverage table, under the SESSION's verdict about coverage — independent review R2 (2026-09-26).
 *
 * <p>The report used to decide for itself: it checked that a graph was loaded and that its provenance allowed coverage,
 * then scored. The session's {@code CoverageClaim} refuses coverage four ways, and the report knew one. So on a
 * retained graph that does not describe the log, the {@code coverage} verb refused ("scoring against it would still be
 * wrong") while the exported PDF printed "declared 3 · covered 0 · ratio 0.0". Sharing the arithmetic is not sharing
 * the verdict about whether the arithmetic means anything.
 *
 * <p>This class DECIDES NOTHING. It renders the session's {@link CoveragePolicy.Assessment} for the inputs the caller
 * captured with it — the same store, graph and moment — and keeps the whole ledger when the claim allows one:
 * <ul>
 *   <li>REFUSED: no ledger and no scalar line; the refusal, in the session's own words, is the table's reason.</li>
 *   <li>QUALIFIED: the whole ledger, with the claim's reason as the first note — the number carries what it hides.</li>
 *   <li>FULL, or no session (nothing has been decided, so nothing may be refused): the whole ledger.</li>
 * </ul>
 */
public final class ReportCoverage {

    static final String NEEDS_TOPOLOGY = "coverage needs a loaded declared topology";

    private ReportCoverage() {
    }

    /**
     * @param input null when no topology is loaded
     * @param claim the session's assessment, captured together with {@code store} and {@code input}; null when there is
     *              no session yet
     */
    public static ReportVerb.CoverageData forReport(LogStore store, CoverageService.Input input,
                                                    CoveragePolicy.Assessment claim, boolean filtered,
                                                    FilterState filter) {
        if (store == null || input == null || input.topology() == null || input.topology().nodes().isEmpty()) {
            return new ReportVerb.CoverageData(List.of(), null, List.of(NEEDS_TOPOLOGY), NEEDS_TOPOLOGY);
        }
        if (claim != null && !claim.allowed()) {
            String refused = refusal(claim);
            return new ReportVerb.CoverageData(List.of(), null, List.of(refused), refused);
        }
        var assessed = CoverageService.assess(store, filtered, filter, input);
        List<String> notes = new ArrayList<>();
        if (claim != null && claim.claim() == CoveragePolicy.Claim.QUALIFIED) {
            notes.add("coverage QUALIFIED: " + claim.reason());
        }
        notes.addAll(assessed.notes());
        return new ReportVerb.CoverageData(assessed.ledger(), assessed.scalarLine(), notes,
                assessed.ledger().isEmpty() ? "the topology declares no reportable nodes" : null);
    }

    /** The sentence a refused table prints — the session's reason, as the coverage verb states it. */
    public static String refusal(CoveragePolicy.Assessment claim) {
        return "coverage REFUSED: " + claim.reason();
    }
}
