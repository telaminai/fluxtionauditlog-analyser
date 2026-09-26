package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A report's SERIES section, drawn from EXACTLY its stored call (the independent review's gap table, M68.2; re-review
 * N2). {@code ReportSpec.SectionSpec.call} is the set of parameters that produce the data, so the section draws the
 * series the {@code series} verb would compute for that call — same expression, same resolution (STRICT unless the
 * call says LOCF), same record scope — through {@link SeriesScan#parseCall}, the verb's own interpretation. The first
 * version read only the expression, forced LOCF and used the view's filter: a report drew 2 points where the verb
 * found 0, and all 3 where the call's filter selected 1.
 *
 * <p><b>Scope.</b> The call decides it. The view filter on screen does not apply to a series section, so a stored call
 * re-issues exactly, and the caption says so. <b>Refused, never substituted:</b> semantics a drawn series cannot
 * carry — {@code crossings}, {@code buckets} and {@code limit} (a table's shapes), both {@code key} and {@code expr},
 * any key the call does not define, an unknown {@code resolve}, a text filter — give NOT RENDERED and the reason.
 */
final class ReportSeriesPicture {

    /** Either an image with its caption, or the reason there is none. */
    record Result(java.awt.image.BufferedImage image, String caption, String problem) {
        static Result problem(String why) {
            return new Result(null, null, why);
        }
    }

    /** The keys a drawn series call may carry; {@code verb} is the report's own routing field. */
    private static final Set<String> DRAWABLE = Set.of("verb", "key", "expr", "resolve", "filter");
    /** Series-verb parameters that shape a TABLE (crossings, buckets) or cap one, not a single drawn series. */
    private static final Set<String> TABLE_ONLY = Set.of("crossings", "buckets", "limit");

    private ReportSeriesPicture() {
    }

    static Result of(LogStore store, Map<String, ?> call, int width, int height) {
        if (store == null) return Result.problem("no log is loaded");
        Map<String, ?> given = call == null ? Map.of() : call;
        for (String k : given.keySet()) {
            if (TABLE_ONLY.contains(k)) {
                return Result.problem("the call's '" + k + "' shapes a table, not one drawn series — use a table "
                        + "section with verb 'series' for it, or remove it here");
            }
            if (!DRAWABLE.contains(k)) {
                return Result.problem("the call names '" + k + "', which a series section does not define — nothing "
                        + "is drawn rather than a series that ignores it");
            }
        }
        Object key = given.get("key");
        Object expr = given.get("expr");
        if (key != null && expr != null) {
            return Result.problem("the call names both 'key' and 'expr' — which series is meant is not established");
        }
        Map<String, Object> asVerb = new LinkedHashMap<>(given);
        asVerb.remove("key");
        asVerb.remove("verb");
        GraphKey literalKey = null;
        if (key != null) {
            literalKey = GraphKey.fromDisplay(String.valueOf(key));
            if (literalKey == null) {
                return Result.problem("the call's 'key' '" + key + "' is not of the form node.key");
            }
        }
        SeriesScan.Call parsed;
        try {
            parsed = literalKey == null ? SeriesScan.parseCall(asVerb) : SeriesScan.parseKeyCall(asVerb, literalKey);
        } catch (RuntimeException e) {
            return Result.problem("the call cannot be drawn: " + e.getMessage());
        }
        Series series;
        try {
            series = SeriesExtractor.extractExpr(store,
                    parsed.filter(),
                    parsed.expr(), parsed.exprText(), false,
                    parsed.resolve());
        } catch (RuntimeException e) {
            return Result.problem("the series could not be extracted: " + e.getMessage());
        }
        ChartPanel chart = new ChartPanel();
        chart.setSeries(List.of(series));
        java.awt.image.BufferedImage image = chart.toImage(width, height);
        if (image == null) return Result.problem("the chart produced no picture");
        return new Result(image, parsed.exprText() + " · " + series.size() + " point" + (series.size() == 1 ? "" : "s")
                + " · " + parsed.resolve().name() + " · scope: " + parsed.scopeText() + "; the view filter does not apply",
                null);
    }
}
