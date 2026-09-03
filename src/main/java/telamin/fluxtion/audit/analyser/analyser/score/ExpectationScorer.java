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
import java.util.TreeSet;

/**
 * Compares two audit logs on BUSINESS OUTCOMES — the value of every published figure after every
 * scored event — and refuses to report a score it cannot stand behind.
 *
 * <p>This exists because hand-rolled comparisons in this project's experiment history were wrong
 * repeatedly, and <b>every one of them erred in the direction of agreeing with its author</b>. Each
 * guard is named after the defect it prevents, so removing one is a deliberate act:
 *
 * <ul>
 *   <li><b>G1 length</b> — 12 expected events zipped against 0 actual printed "12/12 identical".
 *       <b>Unequal lengths are a hard failure, never a rate.</b></li>
 *   <li><b>G2 alignment</b> — an event publishing nothing was dropped, shifting the tail.
 *       <b>Silent events are kept.</b></li>
 *   <li><b>G3 carry-forward</b> — the fix for G2 back-dated the file-final state onto silent events.
 *       <b>State carries forward only.</b></li>
 *   <li><b>G4 absence</b> — {@code get(k, 0)} conflated "different value" with "no value".
 *       <b>Missing is a distinct verdict.</b></li>
 *   <li><b>G5 empty</b> — two empty logs agreed vacuously. <b>An empty expectation is fatal.</b></li>
 * </ul>
 *
 * <p><b>Three further guards were added after independent review, which found the first five
 * insufficient by executing against them. Every defect it found erred toward agreement — the same
 * direction as all the historical ones:</b>
 *
 * <ul>
 *   <li><b>G6 event identity</b> — comparison was by index only, so expected {@code [config, tick]}
 *       against actual {@code [tick, config]} with coinciding values scored a clean PASS.
 *       <b>Event names must match at every index.</b></li>
 *   <li><b>G7 extra figures</b> — only keys present in expected were compared, so an actual log
 *       publishing a rogue figure passed while the verdict claimed "every figure identical".
 *       <b>This is an equality, not a subset test.</b></li>
 *   <li><b>G8 non-finite</b> — {@code Math.abs(NaN - x) > tolerance} is {@code false}, so a
 *       {@code NaN} in actual compared EQUAL to any expected value. <b>A non-finite value is never
 *       identical to anything.</b></li>
 * </ul>
 *
 * <h2>Dialect is declared, never inferred</h2>
 *
 * <p>The published record format states a node-log is {@code - instanceId: { key: value, … }} and that
 * <b>every top-level numeric key is an {@code instanceId.key} series</b>. It reserves no key names. An
 * earlier version of this class switched dialect on seeing {@code stage}/{@code value}, which
 * mis-read a conforming record using those ordinary names ({@code - book: { stage: 3, value: 4}}
 * reduced to {@code {3=4.0}}), and let a half-written tag silently delete an obligation.
 *
 * <p>So {@link Dialect#NATURAL} is the default and the format is the source of truth.
 * {@link Dialect#TAGGED} — this project's fixture convention — must be asked for explicitly, and a
 * malformed tagged record throws rather than reducing to nothing.
 */
public final class ExpectationScorer {

    /** How a node-log's entries become figures. Declared by the caller; never sniffed. */
    public enum Dialect {
        /** The published format: every numeric key becomes {@code instanceId.key}. */
        NATURAL,
        /** Fixture convention: {@code stage} names the figure, {@code value} carries it. */
        TAGGED
    }

    /** Thrown when a record cannot be reduced under the DECLARED dialect. Fails closed. */
    public static final class MalformedRecordException extends RuntimeException {
        public MalformedRecordException(String message) { super(message); }
    }

    /** Events whose records carry published figures. Lifecycle and control records are not scored. */
    private static final Set<String> DEFAULT_SCORED = Set.of("config", "tick", "rate", "trade");

    private final Dialect dialect;
    private final String figureKey;
    private final String valueKey;
    private final Set<String> scoredEvents;
    private final double tolerance;

    /** Format-conforming default: natural dialect, the four business events, 1e-6. */
    public ExpectationScorer() {
        this(Dialect.NATURAL, "stage", "value", DEFAULT_SCORED, 1e-6);
    }

    public ExpectationScorer(Dialect dialect) {
        this(dialect, "stage", "value", DEFAULT_SCORED, 1e-6);
    }

    public ExpectationScorer(Dialect dialect, String figureKey, String valueKey,
                             Set<String> scoredEvents, double tolerance) {
        this.dialect = dialect;
        this.figureKey = figureKey;
        this.valueKey = valueKey;
        this.scoredEvents = scoredEvents;
        this.tolerance = tolerance;
    }

    /**
     * One scored event. {@code event} is the lowercase simple name, used only to decide whether an
     * event is scored at all; {@code eventType} is the identity as the log carries it.
     *
     * <p><b>G9</b> — comparing simple names made {@code com.a.Tick} and {@code com.b.Tick} equal,
     * which is the same defect class as the generator bug this project filed upstream: a simple name
     * is not an identity when packages differ.
     */
    public record Snapshot(String event, String eventType, Map<String, Double> figures) {}

    /**
     * A single disagreement. Either side may be {@code null}: {@code actual} null means the figure was
     * never published (G4); {@code expected} null means actual published a figure the contract does not
     * contain (G7).
     */
    public record Difference(int index, String event, String figure, Double expected, Double actual) {
        @Override
        public String toString() {
            if (actual == null)
                return "[%d %s] %s: expected %s, NEVER PUBLISHED".formatted(index, event, figure, fmt(expected));
            if (expected == null)
                return "[%d %s] %s: NOT IN THE CONTRACT, actual published %s".formatted(index, event, figure, fmt(actual));
            return "[%d %s] %s: expected %s, got %s".formatted(index, event, figure, fmt(expected), fmt(actual));
        }
        private static String fmt(Double d) {
            if (d == null) return "—";
            return Double.isFinite(d) ? "%.6f".formatted(d) : d.toString();
        }
    }

    /**
     * The verdict. {@code fatal} is set when the comparison itself could not be trusted — in which case
     * {@code matched}/{@code total} are meaningless and MUST NOT be reported as a rate.
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
     * @throws MalformedRecordException under {@link Dialect#TAGGED} when a node-log carries only one of
     *         the two tag keys, or a non-numeric tagged value. Under the natural dialect nothing is
     *         malformed — the format admits any keys.
     */
    public List<Snapshot> snapshots(List<LogRecord> records) {
        List<Snapshot> out = new ArrayList<>();
        Map<String, Double> running = new LinkedHashMap<>();
        int recordIndex = 0;
        for (LogRecord r : records) {
            recordIndex++;
            String event = simpleEventName(r.event());
            if (event == null || !scoredEvents.contains(event)) continue;
            for (NodeLog nl : r.nodeLogs()) {
                if (dialect == Dialect.TAGGED) reduceTagged(nl, running, recordIndex);
                else reduceNatural(nl, running);
            }
            out.add(new Snapshot(event, r.event() == null ? "" : r.event().trim(),
                                 new LinkedHashMap<>(running)));
        }
        return out;
    }

    /** The published format: every numeric key is an {@code instanceId.key} series. */
    private void reduceNatural(NodeLog nl, Map<String, Double> running) {
        for (KV kv : nl.entries()) {
            var n = kv.numeric();
            if (n.isPresent()) running.put(nl.instanceId() + "." + kv.key(), n.getAsDouble());
        }
    }

    /** Fixture convention. A half-written tag is a hard failure, never a silently empty node-log. */
    private void reduceTagged(NodeLog nl, Map<String, Double> running, int recordIndex) {
        String figure = null;
        Double value = null;
        boolean sawFigure = false, sawValue = false;
        for (KV kv : nl.entries()) {
            if (figureKey.equals(kv.key())) { sawFigure = true; figure = kv.rawValue(); }
            else if (valueKey.equals(kv.key())) {
                sawValue = true;
                var n = kv.numeric();
                value = n.isPresent() ? n.getAsDouble() : null;
            }
        }
        if (!sawFigure && !sawValue) return;                        // a node-log carrying neither tag
        if (!sawFigure || !sawValue || figure == null || value == null) {
            throw new MalformedRecordException(
                    "record %d, node '%s': TAGGED dialect requires both '%s' and a numeric '%s' — found keys %s"
                            .formatted(recordIndex, nl.instanceId(), figureKey, valueKey,
                                       nl.entries().stream().map(KV::key).toList()));
        }
        running.put(figure, value);
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
     * Compare actual against expected as an EQUALITY of published figures at every scored event.
     * Returns an untrustworthy {@link Result} rather than a score whenever a guard fires.
     */
    public Result score(List<Snapshot> expected, List<Snapshot> actual) {
        if (expected.isEmpty()) {                                             // G5
            return untrustworthy("expected log has no scored events — a vacuous comparison", 0);
        }
        if (figuresIn(expected).isEmpty()) {                                  // G10
            return untrustworthy(
                    "expected log has %d scored events but publishes NO figures — nothing would be compared. "
                    .formatted(expected.size())
                    + "Most often the declared dialect does not match the log", expected.size());
        }
        if (expected.size() != actual.size()) {                               // G1
            return untrustworthy("event count differs: expected %d, actual %d — refusing to score a misaligned pair"
                    .formatted(expected.size(), actual.size()), expected.size());
        }
        for (int i = 0; i < expected.size(); i++) {                           // G6 + G9
            String e = expected.get(i).eventType(), a = actual.get(i).eventType();
            if (!e.equals(a)) {
                return untrustworthy(
                        "event sequence differs at index %d: expected '%s', actual '%s' — the logs describe different runs"
                                .formatted(i, e, a), expected.size());
            }
        }
        List<Difference> diffs = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < expected.size(); i++) {
            Snapshot e = expected.get(i), a = actual.get(i);
            int before = diffs.size();
            for (Map.Entry<String, Double> fig : e.figures().entrySet()) {
                Double got = a.figures().get(fig.getKey());                   // G4: null means absent
                if (got == null || !equalValues(fig.getValue(), got)) {       // G8 inside
                    diffs.add(new Difference(i, e.event(), fig.getKey(), fig.getValue(), got));
                }
            }
            for (String extra : new TreeSet<>(a.figures().keySet())) {        // G7
                if (!e.figures().containsKey(extra)) {
                    diffs.add(new Difference(i, e.event(), extra, null, a.figures().get(extra)));
                }
            }
            if (diffs.size() == before) matched++;
        }
        return new Result(true, null, expected.size(), matched, diffs);
    }

    /**
     * G8 — a non-finite value is never identical to anything, including another non-finite.
     * {@code Math.abs(NaN - x) > tolerance} is {@code false}, which made NaN compare EQUAL to any
     * expected value; a scorer whose job is to refuse to flatter must fail closed here.
     */
    private boolean equalValues(double expected, double actual) {
        if (!Double.isFinite(expected) || !Double.isFinite(actual)) return false;
        return Math.abs(expected - actual) <= tolerance;
    }

    private static Result untrustworthy(String why, int total) {
        return new Result(false, why, total, 0, List.of());
    }

    /** Figures seen anywhere in a snapshot list — useful for reporting coverage of a comparison. */
    public static Set<String> figuresIn(List<Snapshot> snapshots) {
        Set<String> out = new LinkedHashSet<>();
        for (Snapshot s : snapshots) out.addAll(s.figures().keySet());
        return out;
    }
}
