package telamin.fluxtion.audit.analyser.analyser.score;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares two audit logs on BUSINESS OUTCOMES — the value of every published figure after every
 * scored event — and refuses to report a score it cannot stand behind.
 *
 * <p>This exists because five hand-rolled comparisons in this project were wrong, three of them in a
 * single session, and <b>every one of them erred in the direction of agreeing with its author</b>.
 * Each guard below is named after the defect it prevents, so that removing one is a deliberate act
 * rather than an oversight:
 *
 * <ul>
 *   <li><b>G1 length</b> — a comparison over {@code zip()} of 12 expected against 0 actual events
 *       printed "12/12 identical", because zipping an empty list yields nothing and zero mismatches
 *       read as total agreement. <b>Unequal lengths are a hard failure, never a rate.</b></li>
 *   <li><b>G2 alignment</b> — an event that legitimately publishes nothing was dropped, shifting
 *       every later comparison by one and reporting the whole tail as wrong. <b>Silent events are
 *       kept and carry the state they had at that moment.</b></li>
 *   <li><b>G3 carry-forward</b> — a fix for G2 used the file-final state for silent events,
 *       back-dating the end state onto every one of them. <b>State is carried forward only.</b></li>
 *   <li><b>G4 absence</b> — a {@code get(k, 0)} default conflated "recorded a different value" with
 *       "recorded nothing". <b>Missing is a distinct verdict from differing.</b></li>
 *   <li><b>G5 empty</b> — comparing two logs that both parsed to zero records is a vacuous pass.
 *       <b>An empty expectation is a hard failure.</b></li>
 * </ul>
 *
 * <p>Two figure shapes are read. The <b>natural</b> Fluxtion form qualifies each numeric key by the
 * node that logged it — {@code - book: { mid: 17.1}} becomes {@code book.mid}. The <b>tagged</b>
 * convention used by this project's fixtures names the figure explicitly — {@code stage: risk.var,
 * value: 3.2} becomes {@code risk.var}. A node-log using the tagged keys is read that way; anything
 * else falls back to the natural form. Both key names are configurable.
 */
public final class ExpectationScorer {

    /** Events whose records carry published figures. Lifecycle and control records are not scored. */
    private static final Set<String> DEFAULT_SCORED =
            Set.of("config", "tick", "rate", "trade");

    private final String figureKey;
    private final String valueKey;
    private final Set<String> scoredEvents;
    private final double tolerance;

    public ExpectationScorer() {
        this("stage", "value", DEFAULT_SCORED, 1e-6);
    }

    public ExpectationScorer(String figureKey, String valueKey, Set<String> scoredEvents, double tolerance) {
        this.figureKey = figureKey;
        this.valueKey = valueKey;
        this.scoredEvents = scoredEvents;
        this.tolerance = tolerance;
    }

    /** One scored event: its lowercase event name and every figure's value as at that event. */
    public record Snapshot(String event, Map<String, Double> figures) {}

    /** A single disagreement. {@code actual} is null when the figure was never published (G4). */
    public record Difference(int index, String event, String figure, double expected, Double actual) {
        @Override
        public String toString() {
            return actual == null
                    ? "[%d %s] %s: expected %.6f, NEVER PUBLISHED".formatted(index, event, figure, expected)
                    : "[%d %s] %s: expected %.6f, got %.6f".formatted(index, event, figure, expected, actual);
        }
    }

    /**
     * The verdict. {@code fatal} is set when the comparison itself could not be trusted — in which
     * case {@code matched}/{@code total} are meaningless and MUST NOT be reported as a rate.
     */
    public record Result(boolean trustworthy, String fatal, int total, int matched, List<Difference> differences) {
        public boolean pass() { return trustworthy && differences.isEmpty(); }

        public String summary() {
            if (!trustworthy) return "UNTRUSTWORTHY — " + fatal;
            return pass() ? "PASS %d/%d events, every figure identical".formatted(matched, total)
                          : "FAIL %d/%d events identical, %d differences".formatted(matched, total, differences.size());
        }
    }

    /**
     * Reduce a parsed log to one snapshot per scored event, carrying figures forward.
     *
     * <p>G2/G3: an event that publishes no figure still yields a snapshot, holding the state as at
     * that moment — not the state at the end of the file.
     */
    public List<Snapshot> snapshots(List<LogRecord> records) {
        List<Snapshot> out = new ArrayList<>();
        Map<String, Double> running = new LinkedHashMap<>();
        for (LogRecord r : records) {
            String event = simpleEventName(r.event());
            if (event == null || !scoredEvents.contains(event)) continue;
            for (NodeLog nl : r.nodeLogs()) {
                String figure = null;
                Double tagged = null;
                boolean sawTag = false;
                for (KV kv : nl.entries()) {
                    if (figureKey.equals(kv.key())) { figure = kv.rawValue(); sawTag = true; }
                    else if (valueKey.equals(kv.key())) {
                        sawTag = true;
                        var n = kv.numeric();
                        tagged = n.isPresent() ? n.getAsDouble() : null;
                    }
                }
                if (sawTag) {
                    // Tagged convention: a `stage` names the figure and a `value` carries it.
                    if (figure != null && tagged != null) running.put(figure, tagged);
                } else {
                    // Natural form: the node's instanceId qualifies each numeric key it logged.
                    for (KV kv : nl.entries()) {
                        var n = kv.numeric();
                        if (n.isPresent()) running.put(nl.instanceId() + "." + kv.key(), n.getAsDouble());
                    }
                }
            }
            out.add(new Snapshot(event, new LinkedHashMap<>(running)));
        }
        return out;
    }

    /** Lowercase simple name of an event type; {@code null} when absent. */
    static String simpleEventName(String event) {
        if (event == null || event.isBlank()) return null;
        String s = event.trim();
        int dollar = s.lastIndexOf('$');
        if (dollar >= 0) s = s.substring(dollar + 1);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(dot + 1);
        return s.toLowerCase();
    }

    /**
     * Compare actual against expected. Returns an untrustworthy {@link Result} rather than a score
     * whenever a guard fires — a scorer that cannot align its inputs must say so, not average them.
     */
    public Result score(List<Snapshot> expected, List<Snapshot> actual) {
        if (expected.isEmpty()) {                                             // G5
            return new Result(false, "expected log has no scored events — a vacuous comparison",
                    0, 0, List.of());
        }
        if (expected.size() != actual.size()) {                               // G1
            return new Result(false,
                    "event count differs: expected %d, actual %d — refusing to score a misaligned pair"
                            .formatted(expected.size(), actual.size()),
                    expected.size(), 0, List.of());
        }
        List<Difference> diffs = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < expected.size(); i++) {
            Snapshot e = expected.get(i);
            Snapshot a = actual.get(i);
            boolean clean = true;
            for (Map.Entry<String, Double> fig : e.figures().entrySet()) {
                Double got = a.figures().get(fig.getKey());                   // G4: null means absent
                if (got == null || Math.abs(got - fig.getValue()) > tolerance) {
                    diffs.add(new Difference(i, e.event(), fig.getKey(), fig.getValue(), got));
                    clean = false;
                }
            }
            if (clean) matched++;
        }
        return new Result(true, null, expected.size(), matched, diffs);
    }

    /** Figures seen anywhere in a snapshot list — useful for reporting coverage of a comparison. */
    public static Set<String> figuresIn(List<Snapshot> snapshots) {
        Set<String> out = new LinkedHashSet<>();
        for (Snapshot s : snapshots) out.addAll(s.figures().keySet());
        return out;
    }
}
