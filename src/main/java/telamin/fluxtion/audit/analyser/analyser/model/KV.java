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
 *
 * <p><b>{@code quoted}</b> is true when the value arrived as a double-quoted scalar (format-spec §3:
 * {@code "…"} with backslash escapes) and has been decoded. A quoted value is a STRING whatever it
 * spells: {@code "42.0"} is not a figure, {@code "null"} is not null, {@code "true"} is not a flag. The
 * binary reader quotes exactly the strings the tokenizer would otherwise mistype or mis-split, so a
 * logged String can no longer manufacture a numeric figure — which is what a review found it could.
 */
public record KV(String key, String rawValue, boolean quoted) {

    /** An unquoted value: typed by inspection, as every text-log value always has been. */
    public KV(String key, String rawValue) {
        this(key, rawValue, false);
    }

    // strictly numeric literal (no letters/spaces) so we never mis-read "connected=true" as a number
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /**
     * What kind of value this is, by the model's own interpretation - the one the scorer, the chart and
     * the diff must all share. A quoted value is TEXT whatever it spells; a bare value is typed by
     * inspection, as every text-log value always has been.
     */
    public enum Kind { NULL, BOOLEAN, NUMBER, TEXT }

    public Kind kind() {
        if (isNull()) return Kind.NULL;
        if (asBoolean() != null) return Kind.BOOLEAN;
        if (numeric().isPresent()) return Kind.NUMBER;
        return Kind.TEXT;
    }

    /** True when the value is the literal {@code null} or absent. */
    public boolean isNull() {
        return rawValue == null || (!quoted && rawValue.equals("null"));
    }

    /** {@code true}/{@code false} → Boolean, otherwise {@code null}. */
    public Boolean asBoolean() {
        if (rawValue == null || quoted) return null;
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
        if (rawValue == null || quoted) return OptionalDouble.empty();
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