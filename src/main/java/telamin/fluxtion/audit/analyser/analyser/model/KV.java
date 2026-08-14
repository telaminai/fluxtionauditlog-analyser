package telamin.fluxtion.audit.analyser.analyser.model;

import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * One {@code key: value} pair from a node-log map. The value is kept as the raw {@code toString()}
 * text emitted by the node; typing is done lazily and defensively (see {@link #numeric()}), because
 * node-log values are arbitrary Java strings, not YAML scalars (e.g. {@code NaN},
 * {@code MutableOrder(a=1, b=2)}, {@code connected=true requiredOrderVenues=[x]}).
 *
 * <p>{@code key} may be {@code null} for a bare/unstructured token (lenient fallback).
 */
public record KV(String key, String rawValue) {

    // strictly numeric literal (no letters/spaces) so we never mis-read "connected=true" as a number
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** True when the value is the literal {@code null} or absent. */
    public boolean isNull() {
        return rawValue == null || rawValue.equals("null");
    }

    /** {@code true}/{@code false} → Boolean, otherwise {@code null}. */
    public Boolean asBoolean() {
        if (rawValue == null) return null;
        String v = rawValue.trim();
        if (v.equals("true")) return Boolean.TRUE;
        if (v.equals("false")) return Boolean.FALSE;
        return null;
    }

    /**
     * A numeric interpretation suitable for graphing. Present for integer/decimal literals and for
     * {@code NaN}/{@code Infinity}/{@code -Infinity} (NaN/Inf are returned as their double values so a
     * chart can render them as gaps); empty for any non-numeric value.
     */
    public OptionalDouble numeric() {
        if (rawValue == null) return OptionalDouble.empty();
        String v = rawValue.trim();
        switch (v) {
            case "NaN": return OptionalDouble.of(Double.NaN);
            case "Infinity": return OptionalDouble.of(Double.POSITIVE_INFINITY);
            case "-Infinity": return OptionalDouble.of(Double.NEGATIVE_INFINITY);
            default: /* fall through */
        }
        try {
            return OptionalDouble.of(Long.parseLong(v));
        } catch (NumberFormatException ignore) {
            // not a long; try a strict decimal
        }
        if (DECIMAL.matcher(v).matches()) {
            try {
                return OptionalDouble.of(Double.parseDouble(v));
            } catch (NumberFormatException ignore) {
                // fall through
            }
        }
        return OptionalDouble.empty();
    }

    /** True if {@link #numeric()} yields a finite (non-NaN, non-Inf) value. */
    public boolean isFiniteNumber() {
        OptionalDouble d = numeric();
        return d.isPresent() && Double.isFinite(d.getAsDouble());
    }

    /**
     * A value suitable for plotting: numeric literals as-is (incl. NaN/Inf), and booleans mapped to
     * {@code +1.0} (true) / {@code -1.0} (false) — symmetric around zero so flips are visually
     * obvious. Empty for non-graphable values.
     */
    public OptionalDouble graphValue() {
        OptionalDouble n = numeric();
        if (n.isPresent()) return n;
        Boolean b = asBoolean();
        if (b != null) return OptionalDouble.of(b ? 1.0 : -1.0);
        return OptionalDouble.empty();
    }
}