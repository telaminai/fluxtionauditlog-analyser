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
     * The adapter reporting whether a log is open, and which.
     *
     * <p><b>This input exists because slice 1 does not own log opening yet.</b> M35's open path still
     * lives in {@code MainFrame}, so the processor cannot know a log arrived unless it is told. It is
     * an observation and not a result: nobody requested it, so it carries no {@code opId} and the gate
     * does not check one.
     *
     * <p><b>Scheduled for deletion</b> when the slice that moves log opening lands — at which point a
     * log becomes open because {@code LogOpened} answered an {@code OpenLogEffect}, and this input
     * becomes a second source of truth for the same fact. It is written down here so that removal is a
     * planned step rather than a discovery.
     */
    public record LogObserved(boolean open, String logPath, String provenance) {
    }

    /** As {@link LogObserved}, for the topology graph, and deleted at the same time. */
    public record GraphObserved(boolean open, String graphPath, String source) {
    }
}
