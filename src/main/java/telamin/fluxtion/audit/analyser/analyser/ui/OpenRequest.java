package telamin.fluxtion.audit.analyser.analyser.ui;

/**
 * What a load needs to know about WHO asked for it and WHAT they declared — captured once when the
 * open starts, carried through the asynchronous load unchanged, read by {@code onLoaded} (M35.9).
 *
 * <p>Before this record the same two facts lived in mutable fields on {@code MainFrame}
 * ({@code openFromActionSocket}, {@code pendingProvenance}) that were set before a load and consumed
 * during it. That shape failed four times in one milestone, in the same way each time: a field with
 * two consumers, or two loads, and the second reader found it spent or crossed. The last instance
 * was a modal the socket path had been "suppressing" for a week without ever suppressing it — the
 * project offer consumed the flag 59 lines before the time-order gate read it. A value that travels
 * WITH the load cannot be consumed by the wrong step or by the wrong load.
 *
 * <p>Two kinds of state are deliberately NOT here. What the LOG is — its provenance once loaded, the
 * project it sits in, the graph its source offered — belongs to the open log and clears with it in
 * {@code closeLog}. What the APP is — the active project, the loaded graph — belongs to the session.
 * This record is only the request: it is dead once {@code onLoaded} has read it.
 *
 * @param fromActionSocket true when an agent asked over the action socket. Nobody is at the screen,
 *                         so every dialog the load path would show is instead recorded as DATA
 *                         (status bar, {@code context}): the project offer, the time-order report,
 *                         the rolled-set offer. A human open shows them, as before.
 * @param provenance       WHERE the log came from, as the requester DECLARED it (§E) — or null,
 *                         which means "not declared" and is reported as nothing, never inferred.
 *                         A follow re-open re-declares the value the log already had, because it
 *                         is the same log.
 */
public record OpenRequest(boolean fromActionSocket, String provenance) {

    /** A person opened it — chooser, drag-drop, recent menu, S3 dialog. Dialogs are for them. */
    public static final OpenRequest HUMAN = new OpenRequest(false, null);

    public OpenRequest {
        provenance = provenance == null || provenance.isBlank() ? null : provenance.trim();
    }

    /** An agent asked over the action socket, declaring (or not) where the log came from. */
    public static OpenRequest socket(String provenance) {
        return new OpenRequest(true, provenance);
    }

    /**
     * A reload of the SAME log (follow rotation). It keeps BOTH of the original request's answers:
     * what it declared, and <b>who asked</b>.
     *
     * <p>The audience of a rotation is whoever was there for the open that started it. A rotation of
     * an agent-opened log has nobody at the screen either — and this is not hypothetical, it is the
     * flagship path in spec-agent-brokered-dev-loop: <i>edit → approve restart → watch the live log
     * move</i>. Rebuilding the request as human-context would return every dialog this record exists
     * to route, on the one path where a modal is guaranteed to be unanswered.
     */
    public static OpenRequest reload(OpenRequest original, String provenance) {
        return new OpenRequest(original != null && original.fromActionSocket(), provenance);
    }
}
