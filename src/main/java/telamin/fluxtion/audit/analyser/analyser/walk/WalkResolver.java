package telamin.fluxtion.audit.analyser.analyser.walk;

import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget;

import java.util.List;

/**
 * M69 — a target's identity verdict against what is loaded now (spec-spotlight-walks.md §3.5). Pure: the frame supplies
 * {@link Facts}, and nothing here reads a store, a chart or a panel directly. Whether a target is ON SCREEN is a
 * separate question the frame answers after this one.
 *
 * <p>The rules, per basis kind:
 * <ul>
 *   <li><b>record</b> — the record must be in range, its representation known and equal, and its digest must match.
 *       A record whose digest differs, or was
 *       never taken, is <b>not lit</b>: pointing at it would re-point the author's caption at other text (W-A5);</li>
 *   <li><b>chart</b> — lit only when it drew (§3.6); its state is the worse of its definition digest and the walk's run
 *       basis, and a chart with external series is unresolved, because its population is not the log's alone;</li>
 *   <li><b>graph</b> — the node must exist; its state is the graph digest's, unknown for a source-supplied graph;</li>
 *   <li><b>none</b> — a UI region: current whenever it is there.</li>
 * </ul>
 */
public final class WalkResolver {

    private WalkResolver() {
    }

    /** What the frame knows now. Any method may answer null or empty for "unknown". */
    public interface Facts {
        int recordCount();

        String recordDigest(int index);

        /** The current raw-text representation, or null when it is not established. */
        String recordRepresentation();

        List<String> runBasis();

        /** The named chart's definition digest, or null when no such chart is saved or open. */
        String chartDefinitionDigest(String chart);

        boolean chartHasExternalSeries(String chart);

        Drawn chartDrawn(String chart);

        String graphDigest();

        boolean nodeExists(String instanceId);
    }

    /** Whether a chart drew, and why not. */
    public record Drawn(boolean drawn, String reason) { }

    /** The verdict for one target. */
    public record Verdict(WalkIdentity.State state, boolean available, String reason) { }

    public static Verdict verdict(WalkSpec.Target t, WalkSpec.View view, List<String> savedRunBasis, Facts now) {
        SpotlightTarget.Parsed parsed = SpotlightTarget.parse(t.target());
        if (!parsed.ok()) return new Verdict(WalkIdentity.State.UNRESOLVED, false, parsed.error());
        SpotlightTarget target = parsed.target();
        return switch (WalkSteps.basisKind(t.target())) {
            case "record" -> {
                int index = target.family() == SpotlightTarget.Family.RECORDS_ROW ? target.number()
                        : view.record() == null ? -1 : view.record();
                if (index < 0 || index >= now.recordCount()) {
                    yield new Verdict(WalkIdentity.State.UNRESOLVED, false,
                            "record " + index + " is not in this log (" + now.recordCount() + " records)");
                }
                String representation = now.recordRepresentation();
                if (representation == null || representation.isBlank() || !representation.equals(t.basis().representation())) {
                    yield new Verdict(WalkIdentity.State.UNRESOLVED, false,
                            "record " + index + "'s representation is unknown or differs from when this walk was saved");
                }
                WalkIdentity.State s = WalkIdentity.compare(t.basis().digest(), now.recordDigest(index));
                yield switch (s) {
                    case CURRENT -> new Verdict(s, true, "");
                    case HISTORICAL -> new Verdict(s, false, "record " + index + " differs from when this walk was saved");
                    case UNRESOLVED -> new Verdict(s, false, "record " + index + "'s identity is unknown, so it is not pointed at");
                };
            }
            case "chart" -> {
                String chart = target.graph() != null ? target.graph() : view.graph();
                String def = chart == null ? null : now.chartDefinitionDigest(chart);
                if (def == null) {
                    yield new Verdict(WalkIdentity.State.UNRESOLVED, false, "no chart named '" + chart + "'");
                }
                WalkIdentity.State s = WalkIdentity.worse(WalkIdentity.compare(t.basis().digest(), def),
                        WalkIdentity.compareRuns(savedRunBasis, now.runBasis()));
                String why = s == WalkIdentity.State.CURRENT ? ""
                        : s == WalkIdentity.State.HISTORICAL ? "the chart or its log differs from when this walk was saved"
                        : "the chart's population identity is unknown";
                if (now.chartHasExternalSeries(chart)) {
                    s = WalkIdentity.State.UNRESOLVED;
                    why = "the chart plots external series, so its population is not the log's alone";
                }
                Drawn drawn = now.chartDrawn(chart);
                if (drawn == null || !drawn.drawn()) {
                    yield new Verdict(s, false, drawn == null ? "the chart is not open" : drawn.reason());
                }
                yield new Verdict(s, true, why);
            }
            case "graph" -> {
                if (!now.nodeExists(target.argument())) {
                    yield new Verdict(WalkIdentity.State.UNRESOLVED, false,
                            "node '" + target.argument() + "' is not in this graph");
                }
                WalkIdentity.State s = WalkIdentity.compare(t.basis().digest(), now.graphDigest());
                yield new Verdict(s, true, s == WalkIdentity.State.CURRENT ? ""
                        : s == WalkIdentity.State.HISTORICAL ? "the topology differs from when this walk was saved"
                        : "the topology's identity is unknown");
            }
            default -> new Verdict(WalkIdentity.State.CURRENT, true, target.javaSource()
                    ? "name/line lookup only: source revision was not compared with the saved caption" : "");
        };
    }

    /** A lit target's caption, marked when it is not current — never presented as current when it is not. */
    public static String caption(String caption, WalkIdentity.State state) {
        return caption(caption, state, false);
    }

    public static String caption(String caption, WalkIdentity.State state, boolean javaSource) {
        String c = caption == null ? "" : caption;
        if (state == WalkIdentity.State.CURRENT && !javaSource) return c;
        String mark = javaSource ? " (saved source revision not compared)"
                : state == WalkIdentity.State.HISTORICAL ? " (historical)" : " (unresolved)";
        int room = SpotlightTarget.MAX_CAPTION - mark.length();
        if (c.length() > room) c = c.substring(0, Math.max(0, room - 1)) + "…";
        return c + mark;
    }
}
