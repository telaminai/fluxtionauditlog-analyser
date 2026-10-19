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
 *
 * <p><b>M77-3 (spec-ai-setup-wizard.md §9.2, review R1-7, OD-77.4): the one owner of turning access on or off.</b> Every
 * door — the Set up AI wizard, the AI menu's checkbox, Settings ▸ Assistant on save and the client dialog — posts
 * {@link SessionEvents.ExternalAccessRequested}. Turning ON asks for the confirmation first
 * ({@link SessionEffects.ConfirmExternalAccessEffect}); only a confirmed answer for that request writes the setting
 * ({@link SessionEffects.SetExternalAccessEffect}). Turning OFF writes it at once. So <i>when to confirm</i> is decided
 * here, for every door, never in a view. ({@code --rest} stays M19.7's start-up write: nobody is at the screen.)
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
    /** M77-3: the turn-on request whose confirmation is showing; 0 for none. */
    private long confirming;
    /** M77-3: how many Full sessions have needed the bridge (a watch compares counts, §9.4). */
    private long fullSessions;
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
    public static final String EXTERNAL_OPEN = "External MCP/REST access is on: listening on %s for external clients — the token "
            + "is in the endpoint file (~/.fluxtion-analyser/rest-endpoint); Copy prompt includes it only if you tick "
            + "\"Include my local REST token\"";
    public static final String HOSTED_ONLY = "Claude Code's bridge listener is up (external clients are refused: External "
            + "MCP/REST access is off)";
    public static final String CLOSED_FOR_SESSION = "External MCP/REST access is closed while this Claude Code session "
            + "runs; Claude Code keeps its own connection. It opens again when the session ends.";
    public static final String EXTERNAL_CLOSED = "External MCP/REST access stopped for external clients; Claude Code in "
            + "the Assistant tab keeps its own connection.";
    public static final String STOPPED = "External MCP/REST access stopped.";

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
        if (full) fullSessions++;
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

    /**
     * M77-3 (§9.2, OD-77.4): a door asked to turn external access on or off. On: the confirmation first, never the
     * write. Off: at once, needing no confirmation (turning off is the safe direction). A request that changes nothing
     * (already on, already off) asks for nothing.
     */
    @OnEventHandler
    public boolean onExternalAccessRequested(SessionEvents.ExternalAccessRequested e) {
        auditLog.info("externalAccessRequested", e.on()).info("request", e.request()).info("origin", e.origin().name());
        if (e.on()) {
            if (restObserved && restOn) {
                auditLog.info("externalAccess", "ALREADY_ON");
                return false;
            }
            if (confirming != 0) {
                // one confirmation at a time: a second door's press while it shows changes nothing
                auditLog.info("externalAccess", "ALREADY_CONFIRMING").info("open", confirming);
                return false;
            }
            confirming = e.request();
            effects.request(new SessionEffects.ConfirmExternalAccessEffect(0L, e.request()));
            return changed();
        }
        boolean wasConfirming = confirming != 0;
        confirming = 0;
        if (!restOn) return wasConfirming && changed();
        auditLog.info("externalAccess", "OFF").info("request", e.request());
        effects.request(new SessionEffects.SetExternalAccessEffect(0L, e.request(), false));
        return changed();
    }

    /** M77-3: the person's answer. Only a confirmed answer for the request that is showing turns access on. */
    @OnEventHandler
    public boolean onExternalAccessAnswered(SessionEvents.ExternalAccessAnswered e) {
        if (confirming == 0 || e.request() != confirming) {
            auditLog.info("staleFact", "ExternalAccessAnswered").info("request", e.request()).info("open", confirming);
            return false;
        }
        confirming = 0;
        auditLog.info("externalAccessAnswered", e.confirmed()).info("request", e.request());
        if (e.confirmed()) effects.request(new SessionEffects.SetExternalAccessEffect(0L, e.request(), true));
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
            published = new ListenerState(restOn, listening, external, bridgeFor, fullSession, closedForSession, offer,
                    confirming != 0, fullSessions);
            dirty = false;
        }
        return published;
    }
}
