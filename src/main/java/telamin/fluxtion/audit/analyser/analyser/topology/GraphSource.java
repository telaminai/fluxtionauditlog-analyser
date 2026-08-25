package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

/**
 * Where the loaded topology came from, and who wins when two want the slot (M34.1).
 *
 * <p>Two things can now supply a graph: a <b>reader</b>, from the source itself
 * ({@link AuditLogReader#graph}), and a <b>person or agent</b>, via {@code open {graphml}}. The
 * question is not academic — a Mongoose server and a hand-picked build output can disagree, and
 * quietly preferring either one is how the analyser ends up confidently describing a system it is
 * not looking at.
 *
 * <p><b>The rule is M35.3's asymmetry, one level out.</b> That milestone settled it for logs and
 * graphs: a thing someone OPENED is <i>intent</i> and is respected; a thing that merely arrived is
 * <i>residue</i> or <i>convenience</i> and yields. Applied here:
 *
 * <ul>
 *   <li>a reader-supplied graph loads when the slot is empty — free, correct, nobody asked and
 *       nobody had to;</li>
 *   <li>opening a graphml REPLACES it, because someone named that file;</li>
 *   <li>and the view always says WHICH, because D-A2 is not satisfied by loading the right graph —
 *       only by saying which one was loaded and where it came from.</li>
 * </ul>
 *
 * <p><b>And a reader's graph belongs to its LOG</b> (review M34 F1). {@link #replacedBy} answers
 * "who wins the slot while this log is open" — a re-read of the same source must not churn a
 * declared graph for an inferred one. It does not answer what happens when the log goes: a
 * READER_* graph is log-DERIVED state, so it clears with the log ({@code closeLog}) and is cleared
 * before the next log is offered its own — otherwise log B is read through the graph that arrived
 * with log A, which is the M35 defect at the reader's level. An OPENED graph is intent and is
 * judged and kept exactly as M35.3 says.
 *
 * <p>The last point is the one that is easy to skip and expensive to skip. {@code coverage} is
 * "declared minus observed": against an INFERRED graph that subtraction always yields 100%, so the
 * feature that found 54 dead nodes in the POC becomes a tautology that still prints a number.
 */
public enum GraphSource {

    /** Nothing loaded. */
    NONE("no graph is loaded"),

    /** A person or agent named this file. Beats anything automatic. */
    OPENED("opened by you"),

    /** The reader supplied it and the source states its own structure — coverage is meaningful. */
    READER_DECLARED("supplied by the source (declared)"),

    /**
     * The reader reconstructed it from what ran. Coverage against this cannot find a dead node,
     * because nothing unobserved is in it — consumers must say so rather than print a ratio.
     */
    READER_INFERRED("supplied by the source (inferred from what ran)");

    public final String describe;

    GraphSource(String describe) {
        this.describe = describe;
    }

    public static GraphSource of(AuditLogReader.Provenance p) {
        return p == AuditLogReader.Provenance.DECLARED ? READER_DECLARED : READER_INFERRED;
    }

    /** Whether {@code candidate} may take the slot from {@code current} — intent beats automatic. */
    public boolean replacedBy(GraphSource candidate) {
        if (candidate == NONE) return false;
        if (this == NONE) return true;
        // an explicit open always wins; a reader must never silently displace a chosen graph
        return candidate == OPENED;
    }

    /**
     * Whether coverage's "declared minus observed" means anything here. False for an inferred graph,
     * where the answer is always 100% and the number is worse than no number.
     */
    public boolean supportsCoverage() {
        return this == OPENED || this == READER_DECLARED;
    }
}
