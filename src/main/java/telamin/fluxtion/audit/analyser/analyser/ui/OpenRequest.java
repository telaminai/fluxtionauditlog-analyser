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
 * @param launch           whether the open came from STARTING the app rather than from anyone acting
 *                         in this session (M46 A4). A fresh {@code --rest} instance that restored the
 *                         previous session's log used to report {@code openedBy: "you"} — so an agent
 *                         that had opened nothing was told it had opened a sibling run's log.
 */
public record OpenRequest(boolean fromActionSocket, String provenance, Launch launch) {

    /** How a startup open differs from one somebody asked for in this session. */
    public enum Launch {
        /** Not a startup open: a person or an agent asked, in this session. */
        NONE,
        /** A log path given on the command line that started the app. */
        COMMAND_LINE,
        /** The previous session's log, reopened because the app remembers it. Nobody asked. */
        RESTORED,
        /** A person or agent explicitly accepted a session offer in this session. */
        EXPLICIT_RESTORE
    }

    /** A person opened it — chooser, drag-drop, recent menu, S3 dialog. Dialogs are for them. */
    public static final OpenRequest HUMAN = new OpenRequest(false, null);

    public OpenRequest {
        provenance = provenance == null || provenance.isBlank() ? null : provenance.trim();
        launch = launch == null ? Launch.NONE : launch;
    }

    public OpenRequest(boolean fromActionSocket, String provenance) {
        this(fromActionSocket, provenance, Launch.NONE);
    }

    /** The startup open: a path from the command line, or the remembered log of the last session. */
    public static OpenRequest atStartup(boolean restored) {
        return new OpenRequest(false, null, restored ? Launch.RESTORED : Launch.COMMAND_LINE);
    }

    /**
     * Who opened the log, as {@code context.log.openedBy} says it and as the Project panel prints it
     * after the words "opened by". An agent reads this to decide whether the log on screen is one it
     * chose, so a log nobody chose in this session must not be attributed to anybody in it.
     */
    public String openedBy() {
        if (launch == Launch.EXPLICIT_RESTORE) return "explicit session restore";
        if (fromActionSocket) return "action socket";
        return switch (launch) {
            case RESTORED -> "the previous session — restored at startup, not opened in this one";
            case COMMAND_LINE -> "the command line that started this analyser";
            case NONE -> "you";
            case EXPLICIT_RESTORE -> "explicit session restore";
        };
    }

    /** Restoration reports failures in its shared outcome; it never waits on a load dialog. */
    public boolean suppressDialogs() { return fromActionSocket || launch == Launch.EXPLICIT_RESTORE; }

    public static OpenRequest explicitRestore(String provenance) {
        return new OpenRequest(false, provenance, Launch.EXPLICIT_RESTORE);
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
        // the launch travels too: a rotation of a restored log is still a log nobody opened here
        return new OpenRequest(original != null && original.fromActionSocket(), provenance,
                original == null ? Launch.NONE : original.launch());
    }
}
