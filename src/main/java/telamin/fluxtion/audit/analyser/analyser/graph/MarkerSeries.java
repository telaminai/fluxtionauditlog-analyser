package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A marker series (spec-marker-series M32.2): discrete events {@code (time, y, payload)} drawn as
 * glyphs, never connected — the one legitimate path for categorical/per-event data onto a value
 * chart. Payloads are DISPLAY CARGO (hover, click, export), never computation (D-M2): the record is
 * the queryable form; the marker is a signpost to it, which is why log-sourced points carry their
 * {@code recordIndex}.
 */
public record MarkerSeries(String label, String glyph, List<MarkerPoint> points, String note,
                           String riddenSeries) {

    /**
     * The common shape: y is a key/expr or the axis lane — no series is ridden, so the LEFT scale
     * applies. {@code riddenSeries} is set only for {@code y: series:<label>} pins, and the CHART
     * resolves which scale that label is on at paint time — so a marker keeps the right height even
     * when its series is later moved between axes without re-extraction (review D12).
     */
    public MarkerSeries(String label, String glyph, List<MarkerPoint> points, String note) {
        this(label, glyph, points, note, null);
    }

    /** {@code y} is NaN for axis-lane (rug) markers; {@code recordIndex} is -1 for external points. */
    public record MarkerPoint(long time, double y, String payload, int recordIndex) {
    }

    /** The fixed glyph vocabulary (D-M1) — one meaning, one series, one glyph. */
    public static final List<String> GLYPHS =
            List.of("triangleUp", "triangleDown", "circle", "square", "diamond", "x");

    /**
     * Density degradation AS DATA (D-M3, headless-testable): markers per pixel column. A column past
     * {@code maxLegible} renders one count glyph; hover lists the first payloads and says how many
     * more — the presence of hidden markers is always visible, drawn instead of written.
     */
    public record ColumnAgg(int column, int count, List<MarkerPoint> first) {
    }

    public static List<ColumnAgg> aggregate(List<MarkerPoint> points, double vx0, double vx1,
                                            int plotW, int keepPerColumn) {
        int cols = Math.max(1, plotW);
        @SuppressWarnings("unchecked")
        List<MarkerPoint>[] byCol = new List[cols];
        int[] counts = new int[cols];
        for (MarkerPoint p : points) {
            int col = (int) Math.round((p.time() - vx0) / (vx1 - vx0) * plotW);
            if (col < 0 || col >= cols) continue;
            counts[col]++;
            if (byCol[col] == null) byCol[col] = new ArrayList<>();
            if (byCol[col].size() < keepPerColumn) byCol[col].add(p);
        }
        List<ColumnAgg> out = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            if (counts[c] > 0) out.add(new ColumnAgg(c, counts[c], List.copyOf(byCol[c])));
        }
        return out;
    }
}
