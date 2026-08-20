package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Extracts a {@link MarkerSeries} from the log (spec-marker-series M32.2) — the same record walk,
 * the same filter, the same last-occurrence rule as every series, so a marker can never disagree
 * with a plotted series about what a record contained.
 *
 * <p>Sources ({@code MarkerSpec.when}): a bare {@code instanceId.key} fires wherever that key was
 * logged (the key-triple source); anything else parses as an {@link Expr} condition and fires where
 * it is truthy (numeric/boolean only — text never enters {@code Expr}). {@code y}: a key/expr, or
 * {@code series:<label>} to ride a plotted series' value at that moment, or {@code axis} for the
 * rug lane. <b>A dangling series pin degrades loudly</b> (review M2): no points, the note names the
 * missing label — and since specs are shared, the dangle can arrive on another machine.
 */
public final class MarkerExtractor {

    private MarkerExtractor() {
    }

    public static MarkerSeries extract(LogStore store, FilterState filter, GraphSpec.MarkerSpec spec,
                                       Function<String, Series> seriesByLabel) {
        String glyph = MarkerSeries.GLYPHS.contains(spec.glyph()) ? spec.glyph() : "circle";

        // --- when: key triple or condition ---
        GraphKey whenKey = null;
        Expr whenExpr = null;
        try {
            Expr parsed = Expr.parse(spec.when());
            if (parsed instanceof Expr.Ref ref) whenKey = ref.key();   // bare key → key-triple source
            else whenExpr = parsed;
        } catch (RuntimeException e) {
            return new MarkerSeries(spec.label(), glyph, List.of(),
                    "'" + spec.when() + "' does not parse: " + e.getMessage());
        }

        // --- y: axis | series:<label> | key/expr ---
        boolean axisLane = "axis".equalsIgnoreCase(spec.y() == null ? "axis" : spec.y());
        Series pinned = null;
        Expr yExpr = null;
        if (!axisLane) {
            String y = spec.y().trim();
            if (y.startsWith("series:")) {
                String label = y.substring("series:".length()).trim();
                pinned = seriesByLabel.apply(label);
                if (pinned == null) {
                    return new MarkerSeries(spec.label(), glyph, List.of(),
                            "y pinned to '" + label + "' — not on this graph");   // loud, never silent (M2)
                }
            } else {
                try {
                    yExpr = Expr.parse(y);
                } catch (RuntimeException e) {
                    return new MarkerSeries(spec.label(), glyph, List.of(),
                            "y '" + y + "' does not parse: " + e.getMessage());
                }
            }
        }

        GraphKey payloadKey = spec.payload() == null || spec.payload().isBlank()
                ? null : GraphKey.fromDisplay(spec.payload().trim());

        Evaluator whenEval = whenExpr == null ? null : whenExpr.newEvaluator();
        Evaluator yEval = yExpr == null ? null : yExpr.newEvaluator();
        Set<GraphKey> whenRefs = whenExpr == null ? Set.of() : whenExpr.refs();
        Set<GraphKey> yRefs = yExpr == null ? Set.of() : yExpr.refs();
        Map<GraphKey, Double> carry = new HashMap<>();   // LOCF, like band extraction — cross-node conditions

        List<MarkerSeries.MarkerPoint> points = new ArrayList<>();
        int skippedNoY = 0;
        var index = store.index();
        for (int row = 0; row < store.size(); row++) {
            if (!filter.testExceptTime(index, row)) continue;   // acrossAllTime — the chart windows the view
            Long logTime = index.logTime(row);
            if (logTime == null) continue;
            List<NodeLog> nodeLogs = store.record(row).nodeLogs();

            // update the LOCF carry from every touched ref (same rule as bands/series)
            for (GraphKey k : whenRefs) updateCarry(carry, nodeLogs, k);
            for (GraphKey k : yRefs) updateCarry(carry, nodeLogs, k);

            boolean fires;
            if (whenKey != null) {
                fires = SeriesExtractor.lastMatching(nodeLogs, whenKey) != null;
            } else {
                double c = whenEval.eval(logTime, carry);
                fires = Double.isFinite(c) && c != 0.0;
            }
            if (!fires) continue;

            double y;
            if (axisLane) {
                y = Double.NaN;
            } else if (pinned != null) {
                y = valueAtOrBefore(pinned, logTime);
                if (!Double.isFinite(y)) { skippedNoY++; continue; }
            } else {
                y = yEval.eval(logTime, carry);
                if (!Double.isFinite(y)) { skippedNoY++; continue; }
            }

            String payload = null;
            if (payloadKey != null) {
                KV kv = SeriesExtractor.lastMatching(nodeLogs, payloadKey);
                if (kv != null) payload = kv.rawValue();
            }
            points.add(new MarkerSeries.MarkerPoint(logTime, y, payload, row));
        }
        String note = skippedNoY > 0
                ? skippedNoY + " marker(s) skipped — fired but had no finite y at that moment" : null;
        // a marker that rides a series must ride its SCALE too (D12) — the chart resolves which axis
        return new MarkerSeries(spec.label(), glyph, List.copyOf(points), note,
                pinned == null ? null : pinned.label());
    }

    /** The rug's fixed label — one meaning, one series, one glyph (D-M1 applies to built-ins too). */
    public static final String FLAG_RUG_LABEL = "⚑ flags";

    /**
     * The built-in Flags rug (M32.6, D-M5's second half): every flagged record as an axis-lane tick,
     * carrying its finding note as the payload (display cargo — hover reads it, click opens the
     * record) and honouring the same filter as every marker. DERIVED from the flags, never persisted
     * or shared: unflagging is how a tick is removed, and the series' note says so. Returns
     * {@code null} when nothing is flagged — an empty built-in row on every chart would be noise,
     * and unlike a user's marker spec there is no declared intent to degrade loudly about.
     */
    public static MarkerSeries flagRug(telamin.fluxtion.audit.analyser.analyser.index.LogIndex index,
                                       FilterState filter, Map<Integer, String> flaggedNotes) {
        if (flaggedNotes == null || flaggedNotes.isEmpty()) return null;
        List<Integer> rows = new ArrayList<>(flaggedNotes.keySet());
        java.util.Collections.sort(rows);
        List<MarkerSeries.MarkerPoint> points = new ArrayList<>();
        for (int row : rows) {
            if (row < 0 || row >= index.size()) continue;
            if (!filter.testExceptTime(index, row)) continue;   // acrossAllTime, like every marker
            Long lt = index.logTime(row);
            if (lt == null) continue;
            points.add(new MarkerSeries.MarkerPoint(lt, Double.NaN, flaggedNotes.get(row), row));
        }
        if (points.isEmpty()) return null;
        return new MarkerSeries(FLAG_RUG_LABEL, "diamond", List.copyOf(points),
                "flagged records — unflag to remove; click a tick to open its record");
    }

    private static void updateCarry(Map<GraphKey, Double> carry, List<NodeLog> nodeLogs, GraphKey k) {
        KV kv = SeriesExtractor.lastMatching(nodeLogs, k);
        if (kv == null) return;
        var d = kv.graphValue();
        if (d.isPresent() && Double.isFinite(d.getAsDouble())) carry.put(k, d.getAsDouble());
        else carry.remove(k);
    }

    /** The pinned series' value at-or-before {@code time} (its points are in time order). */
    private static double valueAtOrBefore(Series s, long time) {
        int lo = 0, hi = s.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (s.x(mid) <= time) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        for (int i = ans; i >= 0; i--) {                 // walk past NaN gaps to the last finite value
            if (Double.isFinite(s.y(i))) return s.y(i);
        }
        return Double.NaN;
    }
}
