package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Condition bands (M28.6): the time intervals over which a condition expression held, computed from
 * the condition's own extracted series — the same evaluator walk, same LOCF carry, same filter — so a
 * band can never disagree with a plotted series about when the condition was true.
 *
 * <p>The series' existing NaN-is-omitted rule gives bands their unknown-handling: an unknowable
 * condition produces no sample, so it neither opens nor closes a band — a band runs from the first
 * sample where the condition held to the next sample where it measurably did not.
 */
public final class ConditionBands {

    private ConditionBands() {
    }

    /** {@code [enter, exit]} pairs (epoch millis) where the series is truthy (finite and non-zero). */
    public static List<long[]> intervals(Series s) {
        List<long[]> out = new ArrayList<>();
        long enter = 0;
        boolean in = false;
        for (int i = 0; i < s.size(); i++) {
            boolean truthy = Double.isFinite(s.y(i)) && s.y(i) != 0.0;
            if (truthy && !in) {
                enter = s.x(i);
                in = true;
            } else if (!truthy && in) {
                out.add(new long[]{enter, s.x(i)});
                in = false;
            }
        }
        if (in && s.size() > 0) out.add(new long[]{enter, s.x(s.size() - 1)});
        return out;
    }
}
