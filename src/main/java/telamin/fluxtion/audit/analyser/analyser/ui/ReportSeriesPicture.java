package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.List;
import java.util.Map;

/**
 * The independent review's gap table, M68.2 (set 13): a report's SERIES section, drawn. It printed NOT RENDERED because
 * PDF assembly for series did not exist; the Graph tab's own extraction and chart are reused here, synchronously and
 * detached from the window, so the picture is framed for the page and nothing on screen changes.
 *
 * <p>The scope is the CURRENT filter, as every other section reads it, and the caption says how many points were drawn
 * — a chart with none still renders, and says it has no data, which is evidence rather than a gap.
 */
final class ReportSeriesPicture {

    /** Either an image with its caption, or the reason there is none. */
    record Result(java.awt.image.BufferedImage image, String caption, String problem) {
        static Result problem(String why) {
            return new Result(null, null, why);
        }
    }

    private ReportSeriesPicture() {
    }

    static Result of(LogStore store, FilterState filter, Map<String, ?> call, int width, int height) {
        if (store == null) return Result.problem("no log is loaded");
        Object key = call == null ? null : call.get("key");
        Object expr = call == null ? null : call.get("expr");
        Series series;
        String label;
        try {
            if (expr != null && !String.valueOf(expr).isBlank()) {
                label = String.valueOf(expr);
                series = SeriesExtractor.extractExpr(store, filter, Expr.parse(label), label, false,
                        SeriesExtractor.Resolve.LOCF);
            } else if (key != null && GraphKey.fromDisplay(String.valueOf(key)) != null) {
                label = String.valueOf(key);
                series = SeriesExtractor.extract(store, filter, GraphKey.fromDisplay(label));
            } else {
                return Result.problem("a series section's call names neither a 'key' of the form node.key nor an 'expr'");
            }
        } catch (RuntimeException e) {
            return Result.problem("the series could not be extracted: " + e.getMessage());
        }
        ChartPanel chart = new ChartPanel();
        chart.setSeries(List.of(series));
        java.awt.image.BufferedImage image = chart.toImage(width, height);
        if (image == null) return Result.problem("the chart produced no picture");
        return new Result(image, label + " · " + series.size() + " point" + (series.size() == 1 ? "" : "s")
                + " · under the current filter", null);
    }
}
