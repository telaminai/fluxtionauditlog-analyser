package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * Why a project is being opened — <b>carried, never inferred</b> (M44 §4).
 *
 * <p>The spec's first draft inferred this from the surface the request arrived on (menu / recent /
 * template / socket). The review showed that is unsound, and the existing code is the proof.
 * {@code MainFrame.applyProjectResult(result, false)} exists for exactly one path — adopting the project
 * that was offered <em>because a log was just opened</em> — and its comment says why: <i>"closing there
 * would destroy the log that just arrived."</i>
 *
 * <p>So an explicit menu switch <b>with a log open</b> must close that log, while adoption <b>for that
 * same log</b> must keep it. Same surface, same state, opposite rule. No amount of looking at what is
 * open can separate them; only the caller's intent can, so the caller states it.
 */
public enum TransitionKind {

    /** Menu, recent list, or socket {@code open {project}} — the profile is the session boundary (M35.5). */
    EXPLICIT_SWITCH(true, true),

    /**
     * The M35 offer path: a log was opened, a project was found for it, and the user accepted. Closing
     * here would destroy the log that caused the offer, so this transition ends nothing.
     */
    ADOPT_FOR_OPEN_LOG(false, false),

    /** Applying the remembered project at start-up. Nothing is open yet, so there is nothing to close. */
    STARTUP_ACTIVATION(false, true),

    /** A new project is a new session. */
    CREATE(true, false),

    /** Save-as / fork adopts the new profile as active, which is a switch. */
    FORK(true, false),

    /** Closing the project reverts to the user's own settings, and takes the log and graph with it. */
    CLOSE(true, false);

    private final boolean endsSession;
    private final boolean mayNoOp;

    TransitionKind(boolean endsSession, boolean mayNoOp) {
        this.endsSession = endsSession;
        this.mayNoOp = mayNoOp;
    }

    /** Whether this transition closes the open log and graph. */
    public boolean endsSession() {
        return endsSession;
    }

    /**
     * Whether re-requesting the already-active project is a no-op. True for the two kinds that mean
     * "make this project active": if it already is, nothing should be re-applied. False for
     * {@link #CREATE}, {@link #FORK} and {@link #CLOSE}, which mean something regardless, and for
     * {@link #ADOPT_FOR_OPEN_LOG}, whose whole purpose is a state change nobody else requested.
     */
    public boolean mayNoOp() {
        return mayNoOp;
    }
}
