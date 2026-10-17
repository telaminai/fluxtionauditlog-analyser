package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.ListenerState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Holistic review B5 (2026-10-17): the action listener's lifecycle — whether a listener is up, and whether it serves
 * external clients — decided HERE (rule 9). It used to be decided by the frame from its own field
 * ({@code hostedBridgeGeneration}), with the status line composed beside it.
 *
 * <p>Inputs: the person's REST switch ({@link SessionEvents.RestSwitchObserved}); a Claude Code session needing the
 * hosted bridge and releasing it ({@link #need} / {@link #released}, pushed by {@code localAgentSession} in the same
 * dispatch, so the listener is asked for BEFORE the process that connects to it); and the person's choice to close
 * external access while a session runs ({@link SessionEvents.ExternalAccessChosen}, review B2). Outputs: the effects
 * that open or close the listener and its external access, and the words the status line says.
 *
 * <p>The rule: a listener is up while the switch is on or a session needs the bridge; it is open to external clients
 * while the switch is on, unless the person closed it for the session that runs now. B2: when a FULL session starts with
 * external access open, its own tools can drive the analyser as an external client (the well-known endpoint file), so
 * the panel says so and offers to close it — the person's choice, decided here.
 */
public class ActionListener implements EventLogSource {

    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private boolean restOn;
    private boolean restObserved;
    private long bridgeFor;
    private boolean fullSession;
    private boolean closedForSession;
    private boolean listening;
    private boolean external;
    private boolean wantListen;
    private boolean wantExternal;
    private ListenerState published = ListenerState.NONE;
    private boolean dirty = true;

    public ActionListener(EffectQueue effects) {
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** The words the status line says (the node's, never the performer's). */
    public static final String EXTERNAL_OPEN = "Assistant REST transport listening on %s for external clients — the token "
            + "is in the endpoint file (~/.fluxtion-analyser/rest-endpoint); Copy prompt includes it only if you tick "
            + "\"Include my local REST token\"";
    public static final String HOSTED_ONLY = "Claude Code's bridge listener is up (external clients are refused: the REST "
            + "transport is off)";
    public static final String CLOSED_FOR_SESSION = "External MCP/REST access is closed while this Claude Code session "
            + "runs; Claude Code keeps its own connection. It opens again when the session ends.";
    public static final String EXTERNAL_CLOSED = "Assistant REST transport stopped for external clients; Claude Code in "
            + "the Assistant tab keeps its own connection.";
    public static final String STOPPED = "Assistant REST transport stopped.";

    @OnEventHandler
    public boolean onRestSwitch(SessionEvents.RestSwitchObserved e) {
        if (restObserved && e.on() == restOn) return false;
        restObserved = true;
        restOn = e.on();
        auditLog.info("restSwitch", restOn);
        decide();
        return changed();
    }

    /** Pushed by {@code localAgentSession} at spawn: generation {@code generation} needs the hosted bridge. */
    public void need(long generation, boolean full) {
        bridgeFor = generation;
        fullSession = full;
        closedForSession = false;
        wantListen = true;
        wantExternal = restOn;
        auditLog.info("listenerNeeded", generation).info("external", wantExternal).info("full", full);
        effects.request(new SessionEffects.ServeHostedBridgeEffect(0L, generation, wantExternal));
        changed();
    }

    /** Pushed by {@code localAgentSession} when no process needs the bridge any more. */
    public void released(long generation) {
        if (generation != bridgeFor) {
            auditLog.info("staleRelease", generation).info("current", bridgeFor);
            return;
        }
        bridgeFor = 0;
        fullSession = false;
        boolean reopen = closedForSession;
        closedForSession = false;
        wantListen = restOn;
        wantExternal = restOn;
        auditLog.info("listenerReleased", generation).info("keep", wantListen).info("reopenExternal", reopen && restOn);
        // final review #6: the session that closed external access has ended, so it is open again — this node's choice
        effects.request(new SessionEffects.ReleaseHostedBridgeEffect(0L, generation, wantListen, wantExternal,
                closedForSession));
        changed();
    }

    /** Review B2: the person's answer to "Close external access while this session runs". */
    @OnEventHandler
    public boolean onExternalChosen(SessionEvents.ExternalAccessChosen e) {
        if (bridgeFor == 0 || e.generation() != bridgeFor) {
            auditLog.info("staleFact", "ExternalAccessChosen").info("generation", e.generation()).info("current", bridgeFor);
            return false;
        }
        if (closedForSession == e.closeWhileSession()) return false;
        closedForSession = e.closeWhileSession();
        auditLog.info("externalClosedForSession", closedForSession);
        decide();
        return changed();
    }

    /** What a configuration did — the performer's report; the words are this node's. */
    @OnEventHandler
    public boolean onConfigured(SessionEvents.ActionListenerConfigured e) {
        boolean wasExternal = external;
        boolean wasListening = listening;
        listening = e.listening();
        external = e.external();
        auditLog.info("listenerConfigured", listening).info("external", external).info("failed", e.failure() != null);
        String words;
        if (e.failure() != null) words = "Could not start the assistant REST transport: " + e.failure();
        else if (external && !wasExternal) words = String.format(EXTERNAL_OPEN, e.endpoint());
        else if (!external && wasExternal && listening) words = closedForSession ? CLOSED_FOR_SESSION : EXTERNAL_CLOSED;
        else if (!listening && wasListening) words = STOPPED;
        else words = null;
        if (words != null) effects.request(new SessionEffects.ShowStatusEffect(0L, words));
        return changed();
    }

    /** The bridge's listener for a session: what it is now (localAgentSession reads the same fact for the spawn). */
    @OnEventHandler
    public boolean onBridgeServed(SessionEvents.LocalAgentBridgeServed e) {
        if (e.generation() != bridgeFor) return false;
        boolean wasListening = listening;
        boolean wasExternal = external;
        listening = e.listening();
        external = e.listening() && !e.hostedOnly();
        auditLog.info("listenerServed", listening).info("external", external);
        if (listening && !external && !wasListening) {
            effects.request(new SessionEffects.ShowStatusEffect(0L, HOSTED_ONLY));
        } else if (external && !wasExternal && e.endpoint() != null) {
            effects.request(new SessionEffects.ShowStatusEffect(0L, String.format(EXTERNAL_OPEN, e.endpoint())));
        }
        return changed();
    }

    /** The rule: up while the switch is on or a session needs it; external while the switch is on and not closed. */
    private void decide() {
        boolean listen = restOn || bridgeFor != 0;
        boolean ext = restOn && !closedForSession;
        if (listen == wantListen && ext == wantExternal) return;   // already asked for: nothing to change
        wantListen = listen;
        wantExternal = ext;
        auditLog.info("listenerDecision", listen).info("external", ext);
        effects.request(new SessionEffects.ConfigureActionListenerEffect(0L, listen, ext, restOn && closedForSession));
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    public ListenerState state() {
        if (dirty) {
            boolean offer = bridgeFor != 0 && fullSession && external && !closedForSession;
            published = new ListenerState(restOn, listening, external, bridgeFor, fullSession, closedForSession, offer);
            dirty = false;
        }
        return published;
    }
}
