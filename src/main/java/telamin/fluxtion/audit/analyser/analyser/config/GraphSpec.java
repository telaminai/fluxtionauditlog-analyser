package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.List;

/**
 * A saved graph: a user-visible {@code name}, its raw {@code series} (each encoded {@code instanceId<SEP>key}),
 * its derived {@code exprs} (formula series), and an optional pinned window ({@code from}/{@code to}).
 * Persisted in the profile so graphs — names, formulas and pins — reopen on load, and named/addressable
 * through the assistant {@code graph} action (spec-assistant-actions §4.3, spec-graph-artifacts §D).
 */
public record GraphSpec(String name, List<String> series, List<ExprSpec> exprs, Long from, Long to,
                        String note, String explanation, List<NoteSpec> notes, List<String> rightAxis,
                        List<GuideSpec> guides, List<BandSpec> bands) {

    /** A derived (formula) series: a display {@code label}, the {@code expr} text, and a resolve policy. */
    public record ExprSpec(String label, String expr, String resolve) {
    }

    /**
     * A labelled horizontal threshold rule (M28.5) — the line the eye otherwise has to interpolate
     * ("where is 0.004?"). {@code rightAxis} reads the value against the second scale.
     */
    public record GuideSpec(double value, String label, boolean rightAxis) {
    }

    /**
     * A condition band (M28.6): the time intervals where {@code expr} held, shaded on the plot. The
     * CONDITION is persisted, never the intervals — they are data, recomputed per extraction exactly
     * like the series themselves.
     */
    public record BandSpec(String expr, String label) {
    }

    /**
     * A note pinned to a moment on the plot.
     *
     * <p>Persisted with the graph because a note that does not survive a restart is one nobody bothers to
     * write. The whole point of annotating a chart is that the reading outlives the session.
     */
    public record NoteSpec(long at, String text, String series) {
    }

    /** The pre-M28.5 shape (no guides/bands) — every earlier caller. */
    public GraphSpec(String name, List<String> series, List<ExprSpec> exprs, Long from, Long to,
                     String note, String explanation, List<NoteSpec> notes, List<String> rightAxis) {
        this(name, series, exprs, from, to, note, explanation, notes, rightAxis, List.of(), List.of());
    }

    /** The canonical shape without annotations — every pre-M23 caller. */
    public GraphSpec(String name, List<String> series, List<ExprSpec> exprs, Long from, Long to, String note) {
        this(name, series, exprs, from, to, note, null, List.of(), List.of());
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

    public List<NoteSpec> notes() {
        return notes == null ? List.of() : notes;
    }

    public List<String> rightAxis() {
        return rightAxis == null ? List.of() : rightAxis;
    }

    public String explanation() {
        return explanation == null ? "" : explanation;
    }

    public List<GuideSpec> guides() {
        return guides == null ? List.of() : guides;
    }

    public List<BandSpec> bands() {
        return bands == null ? List.of() : bands;
    }
}
