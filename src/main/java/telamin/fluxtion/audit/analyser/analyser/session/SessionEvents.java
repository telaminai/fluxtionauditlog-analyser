package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * The typed facts the session processor accepts. No UI type, no {@code Path}, no infrastructure.
 *
 * <p><b>Three kinds of fact, and the distinction is the whole of M44 §0.</b>
 *
 * <ul>
 *   <li><b>Requests</b> — someone asked. They carry an {@code opId} and they change nothing by
 *       themselves. A state node that advanced on a request would be recording an intention as if it
 *       were an outcome, which is exactly the failure this milestone exists to remove.</li>
 *   <li><b>Results</b> — an adapter finished, and says what happened. They carry the {@code opId} they
 *       answer, so a result arriving for an operation that is no longer in flight is detectable rather
 *       than silently believed. <b>Only these advance state.</b></li>
 *   <li><b>Observations</b> — the adapter reporting state it still owns. These carry no {@code opId}
 *       because nobody asked for them.</li>
 * </ul>
 */
public final class SessionEvents {

    private SessionEvents() {
    }

    /**
     * Marker for the facts an adapter reports back. It exists so {@code SessionDriver.Adapter} can be
     * typed — "perform this effect and tell me what happened" — rather than returning {@code Object}
     * and trusting a comment.
     *
     * <p>It is never named in an {@code @OnEventHandler}: Fluxtion dispatches on the concrete record
     * type, and handling a marker would make every result look alike to the graph, which is the exact
     * conflation this package exists to prevent.
     */
    public interface Result {
        long opId();
    }

    // ---------------------------------------------------------------- requests

    /**
     * @param opId        correlates this request with the results that answer it
     * @param profilePath the profile being opened, as a string — the graph never holds a {@code Path}
     * @param kind        why (see {@link TransitionKind}); carried, never inferred from {@code source}
     * @param source      which surface asked, for the record only — it must not drive a decision
     */
    public record OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source) {
    }

    // ---------------------------------------------------------------- results

    /**
     * The adapter read the profile file. <b>This is not "the project is active"</b> — it means the file
     * parsed. Nothing is in force until {@link ProfileApplied}, and confusing the two is how an audit
     * log starts describing intentions.
     */
    public record ProfileLoaded(long opId, String profilePath, boolean ok, String name,
                                int unknownKeys, String reason) implements Result {
    }

    /** The profile's settings are now genuinely in force. This is the authoritative fact. */
    public record ProfileApplied(long opId, String profilePath, String name) implements Result {
    }

    /** The pre-project settings are back in force after a {@link TransitionKind#CLOSE}. */
    public record SettingsRestored(long opId) implements Result {
    }

    /** A {@code CloseLogEffect} completed. Not "we asked it to close" — it closed. */
    public record LogClosed(long opId) implements Result {
    }

    /** A {@code CloseGraphEffect} completed. */
    public record GraphClosed(long opId) implements Result {
    }

    /**
     * The catch-all that stops an effect failing silently. Every effect the processor emits is answered
     * by a typed success or by this.
     */
    public record EffectFailed(long opId, String effect, String reason) implements Result {
    }

    /**
     * A notification effect reached the surface.
     *
     * <p>It looks like ceremony for something that cannot fail, and it is here for one reason: the
     * contract is <b>every effect is answered</b>. The moment one class of effect is exempt, "no result
     * arrived" stops meaning "the effect did not complete" and starts meaning "maybe it was one of the
     * exempt ones" — and the audit record has to be read with a footnote. The driver enforces the
     * contract rather than documenting an exception to it.
     */
    public record StatusShown(long opId, String kind) implements Result {
    }

    // ---------------------------------------------------------------- observations

    /**
     * A log arrived, or went — and what it says about itself.
     *
     * <p><b>This is a permanent input, not the scaffold slice 1 left behind.</b> Loading a log is
     * asynchronous ({@code Background.run}) and the v1 driver is synchronous and single-in-flight by
     * design (D-S0.3), so making the open an effect the processor requests would mean either an
     * asynchronous driver or a lie about when the load finished. The load therefore stays in the
     * adapter and reports what it found. What M44.2 removed is the DUPLICATION: this no longer says
     * only "a log is open", it carries the evidence a decision needs.
     *
     * @param loggedNodeIds distinct {@code instanceId}s seen in the sampled records — raw, so the
     *                      graph computes the pairing rather than being handed a verdict
     * @param sampled       how many records were scanned, and {@code total} how many exist: a pairing
     *                      drawn from a sample must never be stated as a whole-log claim
     */
    public record LogObserved(boolean open, String logPath, String provenance,
                              java.util.Set<String> loggedNodeIds, int sampled, int total,
                              String mostVerboseLevel) {

        public LogObserved {
            loggedNodeIds = loggedNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(loggedNodeIds);
        }

        /** The shape slice 1 used, for callers with nothing to say about pairing. */
        public LogObserved(boolean open, String logPath, String provenance) {
            this(open, logPath, provenance, java.util.Set.of(), 0, 0, null);
        }

        public LogObserved(boolean open, String logPath, String provenance,
                           java.util.Set<String> loggedNodeIds, int sampled, int total) {
            this(open, logPath, provenance, loggedNodeIds, sampled, total, null);
        }
    }

    /**
     * A topology arrived, or went, with the raw facts a decision needs.
     *
     * @param declaredNodeIds the authored node ids the graph declares
     * @param nodeTypes       every node's simple type name, which is how audit installation is read —
     *                        the compiler installs {@code EventLogManager} as a node, so its presence
     *                        is the evidence and its absence is the finding
     */
    public record GraphObserved(boolean open, String graphPath, String source,
                                java.util.Set<String> declaredNodeIds, java.util.List<String> nodeTypes) {

        public GraphObserved {
            declaredNodeIds = declaredNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(declaredNodeIds);
            nodeTypes = nodeTypes == null ? java.util.List.of() : java.util.List.copyOf(nodeTypes);
        }

        public GraphObserved(boolean open, String graphPath, String source) {
            this(open, graphPath, source, java.util.Set.of(), java.util.List.of());
        }
    }
}
