package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.List;

/**
 * A saved graph: a user-visible {@code name}, its raw {@code series} (each encoded {@code instanceId<SEP>key}),
 * its derived {@code exprs} (formula series), and an optional pinned window ({@code from}/{@code to}).
 * Persisted in the profile so graphs — names, formulas and pins — reopen on load, and named/addressable
 * through the assistant {@code graph} action (spec-assistant-actions §4.3, spec-graph-artifacts §D).
 */
public record GraphSpec(String name, List<String> series, List<ExprSpec> exprs, Long from, Long to, String note) {

    /** A derived (formula) series: a display {@code label}, the {@code expr} text, and a resolve policy. */
    public record ExprSpec(String label, String expr, String resolve) {
    }

    /** A following (non-pinned) raw-key graph — the common case. */
    public GraphSpec(String name, List<String> series) {
        this(name, series, List.of(), null, null, null);
    }

    /** A raw-key graph with a pinned window. */
    public GraphSpec(String name, List<String> series, Long from, Long to) {
        this(name, series, List.of(), from, to, null);
    }

    /** The pre-provenance shape (no caption note). */
    public GraphSpec(String name, List<String> series, List<ExprSpec> exprs, Long from, Long to) {
        this(name, series, exprs, from, to, null);
    }

    /** Pinned to a fixed window (assistant {@code graph} from/to, or "Pin to current window") vs following. */
    public boolean isPinned() {
        return from != null || to != null;
    }

    public List<ExprSpec> exprs() {
        return exprs == null ? List.of() : exprs;
    }
}
