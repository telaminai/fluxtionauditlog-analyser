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

    /**
     * A combined {@code open} request arrived, and these are the parameter names it supplied.
     *
     * <p>Only the NAMES. The decision this feeds is about which act a request means, not about any
     * path or value — and keeping the values out means the record of that decision can be read by
     * anyone without carrying a filesystem into it.
     */
    public record OpenRequestReceived(long opId, java.util.Set<String> supplied) {

        public OpenRequestReceived {
            supplied = supplied == null ? java.util.Set.of() : java.util.Set.copyOf(supplied);
        }
    }

    /**
     * M44.3: someone asked for a log to be opened. The processor decides (it always says yes today —
     * the point is that the open is now an OPERATION with an id, so a load that lands late, or for a
     * request that was superseded, is refused rather than believed).
     *
     * @param location   a local path or an {@code s3://} URI, as a string — the graph never holds a Path
     * @param format     an explicit reader format, or null for auto-detection
     * @param provenance which system the log came from, as the opener declared it (§E), or null
     * @param fromSocket whether an agent asked; carried for the record and for the adapter's audience
     */
    public record OpenLogRequested(long opId, String location, String format, String provenance,
                                   boolean fromSocket) {
    }

    // ---------------------------------------------------------------- results
    /**
     * M44.3 D-A2: the adapter has STARTED the work and will submit the real result later, with this
     * opId. Nothing is closed, nothing applied; the operation is in flight. Recorded like any result so
     * a reader sees asked → pending → outcome rather than a gap (D-A5).
     */
    public record Pending(long opId, String what) implements Result {
    }
    /**
     * The load landed and the log is open in the adapter. THE arrival: {@code LogArrival} judges an
     * open graph on this fact, never on a refresh observation (M44.3a).
     *
     * @param loggedNodeIds distinct instanceIds in the sampled records — raw, so the graph computes the
     *                      pairing; {@code sampled} of {@code total} records were scanned
     */
    public record LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                            int sampled, int total, String mostVerboseLevel) implements Result {
        public LogOpened {
            loggedNodeIds = loggedNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(loggedNodeIds);
        }
    }
    /** The load did not land. The previously open log, if any, is still the open one. */
    public record LogOpenFailed(long opId, String location, String reason) implements Result {
    }

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
     * <p><b>Since M44.3 this is the route for CLOSES and menu refreshes only.</b> A log ARRIVING is
     * {@link LogOpened}, the result of an {@code OpenLogEffect} the processor asked for; {@code LogArrival}
     * judges on that and never on this, so a refresh cannot re-judge an unchanged log (M44.3a). The
     * {@code open} flag and the evidence stay until the slice that retires observations altogether.
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
