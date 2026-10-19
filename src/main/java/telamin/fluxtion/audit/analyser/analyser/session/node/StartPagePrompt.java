package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M74 P2 (spec-claude-code-provider.md §5.8, OD-74.19, OD-74.21–74.23): the start page's "What would you like to do?" —
 * where its button goes. A sibling of the assistants, downstream of the mode, the local agent and the open log.
 *
 * <p><b>Rule 9.</b> Choosing an entry is local to the page and posts nothing (r8 F10). The button is the person's act:
 * {@link SessionEvents.StartPagePromptSent}. This node decides the route — the demo, the person's own flow, a draft for
 * the assistant that can answer (choosing that mode when the one in force cannot), the pop-out host when nothing is open,
 * or the no-AI fallback — and asks for it. A flow's end is a fact, after which the node asks for the draft to be
 * composed. <b>It never starts Claude Code</b>: a Claude Code draft is placed and Start ARMED by
 * {@code localAgentSession}, only when Start would be accepted (OD-74.1, r8 F2).
 *
 * <p><b>The list</b> is the built-in S1–S8 (S7 and S8, the build and learn pathways, owner priority 2026-10-17) (owner, 2026-10-17: the configurable list of OD-74.20 is deferred until after
 * P3), so the node publishes no list of its own: the page reads the catalogue's built-in entries.
 */
public class StartPagePrompt implements EventLogSource {

    private final AssistantModeChoice mode;
    private final LocalAgentSession localAgent;
    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    /** P2b: the flow id of S2 toward Claude Code — the set-up folder's chooser (§5.6 step 1). */
    public static final String SETUP_FLOW = "S2.setup";
    /** The learning pathway with no AI: the sample project's DEMO guided tour, no draft after it. */
    public static final String TEACH_TOUR_FLOW = "S8.tour";

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private boolean claudeCodeAllowed;
    private boolean claudeCodeFound;
    private boolean inAppCanAnswer;
    private boolean inProcessActions = true;
    private Map<Long, Pending> pending = new LinkedHashMap<>();
    private String fallbackEntry;
    private String note = "";
    private long answered;
    /** The dropped prompt pack in force (session only), or 0; and why the last drop was refused. */
    private long packRef;
    private telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Cause packRefusal;
    private int packRefusalLine;
    private StartPageState published = StartPageState.IDLE;
    private boolean dirty = true;
    /**
     * M74 UX, M77: {@code aiSetup} (the profile and Set up AI), a JavaBean parent so the generator wires it without
     * changing the constructor — a first-use question it answered by opening the choices gets no no-AI fallback here,
     * and a carried question is placed only once the wizard's Finish was pressed (§12). Null only in a processor
     * generated before the profile existed.
     */
    private AiSetup aiSetup;

    public AiSetup getAiSetup() {
        return aiSetup;
    }

    public void setAiSetup(AiSetup aiSetup) {
        this.aiSetup = aiSetup;
    }

    /**
     * Robot re-walk #3: the hold of an evidence question asked with no log open — a JavaBean parent, upstream, so a press
     * it holds is not routed here until the person acts. Null only in a processor generated before it existed.
     */
    private StartPageNoLogHint noLogHint;
    /** The hint's revision last published. */
    private long noLogRevision = -1;

    public StartPageNoLogHint getNoLogHint() {
        return noLogHint;
    }

    public void setNoLogHint(StartPageNoLogHint noLogHint) {
        this.noLogHint = noLogHint;
    }

    /** A press waiting for its flow: what to compose once the flow is done. */
    private record Pending(String entryId, StartPageState.Route route, long textRef, boolean popOut) {
    }

    /**
     * Review M74 usability #3 (2026-10-17): the question that opened the profile cards, CARRIED through Apply — the person
     * already pressed Send once. Kept until the cards are applied (then placed for the chosen assistant, Start armed
     * where it applies) or closed (then dropped, and the box keeps its words). Never sent: placing is all this does.
     */
    private Pending carried;
    private long carriedRequest;
    private telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile carriedProfile;
    /** Apply succeeded; the machine's new facts (consent, what can answer, the profile) follow as their own operations. */
    private boolean carriedAwaitingObservation;

    /** What a no-AI fallback says — once, as the fallback row's label (or the AI line), never again as the note. */
    public static final String NO_AI_FALLBACK = "No AI assistant can answer here yet — these run without one";

    public StartPagePrompt(AssistantModeChoice mode, LocalAgentSession localAgent, OpenLog openLog, EffectQueue effects) {
        this.mode = mode;
        this.localAgent = localAgent;
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- what can answer ----------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onAvailability(SessionEvents.StartPageAvailability e) {
        if (e.claudeCodeAllowed() == claudeCodeAllowed && e.claudeCodeFound() == claudeCodeFound
                && e.inAppCanAnswer() == inAppCanAnswer && e.inProcessActions() == inProcessActions) {
            return false;
        }
        claudeCodeAllowed = e.claudeCodeAllowed();
        claudeCodeFound = e.claudeCodeFound();
        inAppCanAnswer = e.inAppCanAnswer();
        inProcessActions = e.inProcessActions();
        auditLog.info("startPageRoute", route(mode.localAgent(), claudeCodeAllowed, claudeCodeFound, inAppCanAnswer).name());
        return changed();
    }

    @OnEventHandler
    public boolean onModeChosen(SessionEvents.AssistantModeChosen e) {
        return changed();                       // the mode node is upstream: the route follows it
    }

    /**
     * OD-74.23: the mode in force if its assistant can answer; otherwise the other one, if it can (the button then reads
     * "Ask with …" and pressing it chooses that mode); otherwise the no-AI column. Claude Code can answer when its switch
     * is on AND the resolver found {@code claude} — a file check, nothing run.
     */
    public static StartPageState.Route route(boolean localAgentMode, boolean claudeCodeAllowed, boolean claudeCodeFound,
                                             boolean inAppCanAnswer) {
        boolean claudeCan = claudeCodeAllowed && claudeCodeFound;
        if (localAgentMode && claudeCan) return StartPageState.Route.CLAUDE_CODE;
        if (!localAgentMode && inAppCanAnswer) return StartPageState.Route.IN_APP;
        if (localAgentMode && inAppCanAnswer) return StartPageState.Route.SWITCH_TO_IN_APP;
        if (!localAgentMode && claudeCan) return StartPageState.Route.SWITCH_TO_CLAUDE_CODE;
        return StartPageState.Route.NO_AI;
    }

    // ---- a dropped prompt pack (owner request 2026-10-17): which list is in force, session only ------------------------

    /**
     * A valid pack replaces the numbered list under its title until the person returns; an invalid one is refused with
     * its one named cause and the list in force stays. Nothing is persisted: this is the session's memory of the drop.
     */
    @OnEventHandler
    public boolean onPackDropped(SessionEvents.StartPromptPackDropped e) {
        if (e.packRef() != 0) {
            packRef = e.packRef();
            packRefusal = null;
            packRefusalLine = 0;
            auditLog.info("startPromptPack", "IN_FORCE");
        } else {
            packRefusal = e.refusal();
            packRefusalLine = e.refusalLine();
            auditLog.info("startPromptPack", "REFUSED").info("cause", String.valueOf(e.refusal()))
                    .info("line", e.refusalLine());
        }
        return changed();
    }

    @OnEventHandler
    public boolean onReturnToMainOptions(SessionEvents.ReturnToMainOptions e) {
        if (packRef == 0 && packRefusal == null) return false;
        packRef = 0;
        packRefusal = null;
        packRefusalLine = 0;
        auditLog.info("startPromptPack", "RETURNED");
        return changed();
    }

    // ---- the button ---------------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onSent(SessionEvents.StartPagePromptSent e) {
        claudeCodeFound = e.claudeCodeFound();
        inAppCanAnswer = e.inAppCanAnswer();
        inProcessActions = e.inProcessActions();
        claudeCodeAllowed = e.consent() != null && e.consent().allowed();
        StartPageState.Route route = route(mode.localAgent(), claudeCodeAllowed, claudeCodeFound, inAppCanAnswer);
        // the in-app assistant can follow "Start with analyser_context" only with in-process actions (r8 F4)
        if ((route == StartPageState.Route.IN_APP || route == StartPageState.Route.SWITCH_TO_IN_APP) && !inProcessActions
                && "S5".equals(e.entryId())) {
            route = StartPageState.Route.NO_AI;
        }
        String id = e.entryId() == null ? "NONE" : e.entryId();
        boolean popOut = !e.workspaceOpen();
        fallbackEntry = null;
        note = "";
        answered = e.request();
        auditLog.info("startPageSent", id).info("route", route.name());
        if (noLogHint != null && noLogHint.holds(e.request())) {
            // robot re-walk #3: about evidence with no log open — held until the person acts on the page's one line
            auditLog.info("startPageRoute", "NO_LOG_HELD");
            return changed();
        }

        // the demo plays the same in every column: the walk path, no model (r8 F13)
        if (id.equals("S1")) {
            effects.request(new SessionEffects.PlayDemoJourneyEffect(0L, e.request()));
            return changed();
        }
        if (aiSetup != null && aiSetup.interceptedStartPage(e.request())) {
            // M74 UX: the first question with no profile chosen opened the choices; nothing else is asked for.
            // Review M74 usability #3: the question is CARRIED — placed for the chosen assistant after Apply
            carried = new Pending(id, route, e.textRef(), popOut);
            carriedRequest = e.request();
            carriedProfile = null;
            carriedAwaitingObservation = false;
            // M77: the question waits behind the Set up AI wizard, placed only after its Finish (§12)
            note = telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.QUESTION_WAITS;
            auditLog.info("startPageRoute", "PROFILE_CHOICES").info("carried", true);
            return changed();
        }
        if (id.equals("NONE") && e.textRef() == 0) {
            note = "there is nothing to send: choose an entry or write a question";
            return changed();
        }
        if (!route.canAnswer() && id.equals("S8")) {
            // the learning pathway with no AI: the DEMO guided tour plays (as the demo does), and the page keeps S8's
            // buttons — Assistant settings, Copy prompt — for the lesson an assistant would give. No window over the tour.
            fallbackEntry = id;
            note = "No AI assistant can answer here yet — the DEMO guided tour plays instead";
            effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), TEACH_TOUR_FLOW));
            return changed();
        }
        if (!route.canAnswer()) {
            // S3 and S5 open what the person chose in every column, then offer their fallback (§5.8)
            if (id.equals("S3") || (id.equals("S5") && !openLog.isOpen())) {
                pending.put(e.request(), new Pending(id, route, e.textRef(), popOut));
                effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), id));
                return changed();
            }
            fallbackEntry = id;
            // authoring walk (shot 03): the fallback row's own label says this, and the AI line when it is shown — the
            // page said it twice. The words still go with the effect, for the window over a workspace
            note = "";
            effects.request(new SessionEffects.ShowStartPageFallbackEffect(0L, e.request(), id, NO_AI_FALLBACK));
            return changed();
        }
        switch (id) {
            // P2b (§5.6): toward Claude Code, S2 is onboarding — the person's chooser for the set-up folder, then the
            // SETUP draft placed and Start armed (Claude investigates and calls propose; the analyser applies). The in-app
            // assistant cannot read a folder, so its column opens New project…, which offers and never selects.
            case "S2" -> {
                if (route.toClaudeCode()) {
                    pending.put(e.request(), new Pending(id, route, e.textRef(), popOut));
                    effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), SETUP_FLOW));
                } else {
                    effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), id));
                }
            }
            case "S3", "S4", "S8" -> {
                pending.put(e.request(), new Pending(id, route, e.textRef(), popOut));
                effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), id));
            }
            // the build pathway: S4's flow reads the starter catalogue at the button, then the build question is drafted
            case "S7" -> {
                pending.put(e.request(), new Pending(id, route, e.textRef(), popOut));
                effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), "S4"));
            }
            case "S5" -> {
                if (openLog.isOpen()) {
                    compose(e.request(), id, route, openLog.generation(), 0, 0, e.textRef(), popOut);
                } else {
                    pending.put(e.request(), new Pending(id, route, e.textRef(), popOut));
                    effects.request(new SessionEffects.RunStartPageFlowEffect(0L, e.request(), id));
                }
            }
            default -> compose(e.request(), id, route, openLog.isOpen() ? openLog.generation() : 0, 0, 0, e.textRef(),
                    popOut);
        }
        return changed();
    }

    @OnEventHandler
    public boolean onFlowDone(SessionEvents.StartPageFlowDone e) {
        // a held press's open (the "No log is open" line) is startPageNoLogHint's, upstream — never placed here
        if (noLogHint != null && noLogHint.holds(e.request())) return false;
        Pending p = pending.remove(e.request());
        if (p == null) {
            auditLog.info("staleFact", "StartPageFlowDone").info("request", e.request());
            return false;
        }
        auditLog.info("startPageFlow", p.entryId()).info("outcome", e.outcome().name());
        if (e.outcome() != SessionEvents.FlowOutcome.DONE) {
            note = switch (e.outcome()) {
                case CANCELLED -> "nothing was chosen, so nothing was drafted";
                case REFUSED -> "it was not opened, so nothing was drafted";
                default -> "the flow did not finish, so nothing was drafted";
            };
            return changed();
        }
        if (!p.route().canAnswer()) {
            fallbackEntry = p.entryId();
            note = "";                                // the fallback row's label says it (authoring walk, shot 03)
            effects.request(new SessionEffects.ShowStartPageFallbackEffect(0L, e.request(), p.entryId(), NO_AI_FALLBACK));
            return changed();
        }
        compose(e.request(), p.entryId(), p.route(), e.logGeneration(), e.bundleNameRef(), e.templateNamesRef(),
                p.textRef(), p.popOut());
        return changed();
    }

    // ---- review M74 usability #3: the carried question --------------------------------------------------------------

    /** Apply wrote the settings: the question is placed once the machine's new facts have been reported. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.AssistantProfileApplied e) {
        if (carried == null || !e.ok()) return false;              // a refused Apply leaves the cards open, still carried
        if ((e.origin() != telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileOrigin.SETUP
                || aiSetup == null || aiSetup.finished() != e.request())) {
            // M77 §12 (review R1-2): placement waits for the wizard's Finish, its Apply, the observation and the mode —
            // an Apply from elsewhere, or one the wizard has not finished on (Both's first half), places nothing yet
            auditLog.info("startPageCarried", "WAITING_FOR_FINISH").info("request", e.request());
            return false;
        }
        carriedProfile = e.profile();
        carriedAwaitingObservation = true;
        auditLog.info("startPageCarried", "APPLIED").info("profile", String.valueOf(e.profile()));
        return false;
    }

    /**
     * The profile in force, reported AFTER the consent and what can answer (the Apply performer posts them in that
     * order, each its own operation): the carried question is routed as a start-page press would be now, to the
     * chosen profile's assistant — placed, never sent.
     */
    @OnEventHandler
    public boolean onProfileObserved(SessionEvents.AssistantProfileObserved e) {
        if (carried == null || !carriedAwaitingObservation) return false;
        Pending p = carried;
        long request = carriedRequest;
        var profile = carriedProfile;
        carried = null;
        carriedAwaitingObservation = false;
        carriedProfile = null;
        if (profile == null || profile == telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile.NONE
                || profile == telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile.OFF) {
            note = telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.CANNOT_ANSWER_BOX;
            auditLog.info("startPageCarried", "OFF");
            return changed();
        }
        boolean toClaude = profile.mode() == AssistantMode.LOCAL_AGENT;
        StartPageState.Route route = route(toClaude, claudeCodeAllowed, claudeCodeFound, inAppCanAnswer);
        if (!route.canAnswer() || route.switchesMode()) {
            note = (toClaude ? "Claude Code" : "The in-app assistant") + " cannot answer yet"
                    + (toClaude && !claudeCodeFound ? " (claude was not found on this machine)"
                    : !toClaude ? " (no API key is stored)" : "") + ", so your question was not placed — it is still in the box";
            auditLog.info("startPageCarried", "CANNOT_ANSWER").info("route", route.name());
            return changed();
        }
        note = "";
        answered = request;
        auditLog.info("startPageCarried", "PLACED").info("route", route.name());
        if (AiSetup.BUILD_ENTRY.equals(p.entryId())) {
            // the build pathway, as a press would route it now: the starter catalogue is read first, then the draft
            pending.put(request, new Pending(p.entryId(), route, p.textRef(), p.popOut()));
            effects.request(new SessionEffects.RunStartPageFlowEffect(0L, request, "S4"));
            return changed();
        }
        compose(request, p.entryId(), route, openLog.isOpen() ? openLog.generation() : 0, 0, 0, p.textRef(), p.popOut());
        return changed();
    }

    /** M77 §7.4: the wizard was cancelled before its Apply: the question stays in the box, and the page says so. */
    @OnEventHandler
    public boolean onSetupCancelled(SessionEvents.AiSetupCancelled e) {
        if (carried == null || carriedAwaitingObservation) return false;
        carried = null;
        note = telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.CANCELLED_BOX;
        auditLog.info("startPageCarried", "CANCELLED");
        return changed();
    }

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.StartPageEffectStarted e) {
        auditLog.info("startPageEffect", e.what()).info("request", e.request());
        return false;
    }

    private void compose(long request, String id, StartPageState.Route route, long generation, long bundleNameRef,
                         long templateNamesRef, long textRef, boolean popOut) {
        AssistantMode target = route.target();
        auditLog.info("startPageDraft", id).info("target", target.name()).info("popOut", popOut);
        effects.request(new SessionEffects.ComposeStartPageDraftEffect(0L, request, id, target, route.switchesMode(),
                generation, bundleNameRef, templateNamesRef, textRef, popOut));
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    /** The published state — immutable, for the snapshot, rebuilt only after a change. */
    public StartPageState state() {
        long hintRevision = noLogHint == null ? 0 : noLogHint.revision();
        if (dirty || hintRevision != noLogRevision) {
            published = new StartPageState(route(mode.localAgent(), claudeCodeAllowed, claudeCodeFound, inAppCanAnswer),
                    claudeCodeAllowed && !claudeCodeFound, fallbackEntry, note, answered, packRef, packRefusal,
                    packRefusalLine, noLogHint == null ? 0 : noLogHint.held(), noLogHint != null && noLogHint.opening());
            noLogRevision = hintRevision;
            dirty = false;
        }
        return published;
    }

    /** For the tests: the local agent the node is downstream of (it never asks it to start). */
    LocalAgentSession localAgent() {
        return localAgent;
    }
}
