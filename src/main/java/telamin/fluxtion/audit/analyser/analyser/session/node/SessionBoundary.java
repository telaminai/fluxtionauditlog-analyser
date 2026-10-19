package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.TransitionKind;

/**
 * <b>The rule M35 spent eleven slices getting right, in one readable place:</b> a project change is a
 * session boundary, so it closes the log and the graph — except when it is not, and the exceptions are
 * the part that lived in Swing callbacks.
 *
 * <p>A project owns the source roots, event processors, named graphs, focuses and reports. Swap it and
 * every one of those changes underneath the open log, so keeping the log would mean reading one
 * project's records through another project's settings, with focuses pointing at nodes from a graph
 * that is no longer the right one.
 *
 * <p><b>Two decisions, at two different moments, and separating them is the point.</b>
 *
 * <ol>
 *   <li>On the <b>request</b>: is this worth doing at all? Re-opening the already-active project is a
 *       no-op, and says so rather than silently re-applying everything.</li>
 *   <li>On the <b>result</b>: the profile actually loaded — so now, does this transition close what is
 *       open? A failed load closes nothing, which is the behaviour the old code already had and which
 *       deciding on the request would have destroyed.</li>
 * </ol>
 *
 * <p>What it never does is touch a log, a graph or a widget. It appends effect requests to
 * {@link EffectQueue} and the driver performs them after dispatch. The audit record separates
 * {@code decision} from the {@code EffectOutcome} the driver logs afterwards, so <i>"asked to close"</i>
 * can never be read as <i>"closed"</i>.
 */
public class SessionBoundary implements EventLogSource {

    private final OperationGate gate;
    private final ActiveProject activeProject;
    private final OpenLog openLog;
    private final OpenGraph openGraph;

    /**
     * Push, not pull. Without this the queue would be an ordinary dependency and the event wave would
     * visit it <em>before</em> the decision that fills it — which is backwards, and would draw the
     * GraphML backwards too.
     */
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;

    /**
     * M74 P2b (the simplified Move, owner 2026-10-17): JavaBean parents, so the generator wires them without changing the
     * constructor. Null in a processor generated before P2b: then nothing is ever held.
     */
    private LocalAgentSession localAgentSession;
    private OnboardingProposal onboardingProposal;
    /** A person's project switch held while Claude Code runs, awaiting the person's choice; and that offer's id. */
    private SessionEvents.OpenProjectRequested held;
    private long heldRequest;

    public LocalAgentSession getLocalAgentSession() {
        return localAgentSession;
    }

    public void setLocalAgentSession(LocalAgentSession localAgentSession) {
        this.localAgentSession = localAgentSession;
    }

    public OnboardingProposal getOnboardingProposal() {
        return onboardingProposal;
    }

    public void setOnboardingProposal(OnboardingProposal onboardingProposal) {
        this.onboardingProposal = onboardingProposal;
    }

    /** Option 2: the starter chain, a JavaBean parent — its own open of the starter's project is never held. */
    private StarterProject starterProject;

    public StarterProject getStarterProject() {
        return starterProject;
    }

    public void setStarterProject(StarterProject starterProject) {
        this.starterProject = starterProject;
    }

    /** The kind of the request currently awaiting its {@code ProfileLoaded}. */
    private TransitionKind inFlightKind;
    private long inFlightOpId = -1;
    private SessionEvents.BundlePlan inFlightBundle;

    /** M81 D: offers cannot borrow a project transition's still-undecided context. */
    public boolean projectTransitionPending() { return held != null || inFlightKind != null; }

    public SessionBoundary(OperationGate gate, ActiveProject activeProject, OpenLog openLog,
                           OpenGraph openGraph, EffectQueue effects) {
        this.gate = gate;
        this.activeProject = activeProject;
        this.openLog = openLog;
        this.openGraph = openGraph;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /**
     * Decision one — admit or no-op. Nothing has changed yet and nothing may change here: this only
     * asks for the profile to be read.
     */
    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        if (event.starterRequest() != 0 && (starterProject == null || !starterProject.admitProjectOpen(event))) {
            gate.held(event.opId()); // a cancelled continuation must not supersede an unrelated pending operation
            auditLog.info("decision", "staleStarterOpen").info("opId", event.opId());
            return false;
        }
        if (holds(event)) {
            // a newer switch while one is held REPLACES its target; the offer is made again for the new one
            held = event;
            heldRequest++;
            gate.held(event.opId()); // review S1: a held switch supersedes nothing until the person releases it
            auditLog.info("decision", "holdForLocalAgent").info("kind", event.kind().name())
                    .info("opId", event.opId()).info("request", heldRequest);
            effects.request(new SessionEffects.OfferLocalAgentSwitchEffect(event.opId(), heldRequest,
                    activeProject.name(), targetName(event)));
            return true;
        }
        return decide(event);
    }

    /**
     * M74 P2b: a PERSON's switch (open another project, close, create) while a Claude Code process runs is held and the
     * person asked. An assistant's {@code open {project}} is never held; a re-open of the active project is a no-op as
     * before; the analyser's OWN create from an onboarding proposal is never a switch (§5.6 step 7).
     *
     * <p>Fix (owner's 1.36.0 report): {@code ADOPT_FOR_OPEN_LOG} — accepting "this log sits inside project X — load
     * it?" ({@code MainFrame.maybeOfferProject}) — is held too. It was left out because M35.5's rule for it is about a
     * DIFFERENT question ("does this transition close the open log?" — never, so the log that caused the offer
     * survives); that rule says nothing about whether a running Claude Code session should be offered a restart, and
     * the omission meant accepting the offer applied the switch at once, leaving the session bound to whatever
     * folder it already had (commonly the scratch one, for a session started before any project was open) with no
     * warning. Witness: {@code LocalAgentSwitchTest.adoptingTheProjectForAnOpenLogIsHeldWhileClaudeCodeRuns}.
     */
    private boolean holds(SessionEvents.OpenProjectRequested event) {
        if (localAgentSession == null || !localAgentSession.processRunning()) return false;
        TransitionKind k = event.kind();
        if (k != TransitionKind.EXPLICIT_SWITCH && k != TransitionKind.CLOSE && k != TransitionKind.CREATE
                && k != TransitionKind.FORK && k != TransitionKind.ADOPT_FOR_OPEN_LOG) return false;
        boolean person = event.audience() != null
                && event.audience().origin() == SessionEvents.OperationOrigin.PERSON && event.assistantOrigin() == null;
        if (!person) return false;
        if (k.mayNoOp() && activeProject.isAt(event.profilePath())) return false;
        // Option 2: the starter the person just confirmed opens as their Apply said; localAgentSession then offers the
        // restart in the new folder (its folder-change offer), so a running answer is never cut off by this open
        if ((k == TransitionKind.EXPLICIT_SWITCH || k == TransitionKind.CREATE) && starterProject != null
                && starterProject.openingOwnProject(event)) return false;
        return !(k == TransitionKind.CREATE && onboardingProposal != null && onboardingProposal.applyingOwnCreate());
    }

    private static String targetName(SessionEvents.OpenProjectRequested event) {
        if (event.kind() == TransitionKind.CLOSE) return null;
        String p = event.profilePath() == null ? "" : event.profilePath().replace('\\', '/');
        String[] parts = p.split("/");
        // <root>/.analyser/<file>: the project is named by its root folder
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty() && !parts[i].startsWith(".") && !parts[i].endsWith(".fluxtion-settings")) return parts[i];
        }
        return p;
    }

    /** The person's answer to the switch offer: release the held switch (Stop, Keep) or drop it (Cancel). */
    @OnEventHandler
    public boolean onSwitchChosen(SessionEvents.LocalAgentSwitchChosen e) {
        if (held == null || e.request() != heldRequest) {
            auditLog.info("staleFact", "LocalAgentSwitchChosen").info("request", e.request());
            return false;
        }
        SessionEvents.OpenProjectRequested event = held;
        held = null;
        auditLog.info("decision", "switchChosen").info("choice", e.choice().name()).info("opId", event.opId());
        if (e.choice() == SessionEvents.SwitchChoice.CANCEL) {
            effects.request(new SessionEffects.ShowStatusEffect(event.opId(),
                    "the project switch was cancelled — Claude Code keeps running here"));
            return true;
        }
        gate.released(event); // review S1: its result is now the one awaited (an open during the offer is superseded now)
        return decide(event);
    }

    /** Decision one, for a request that is not (or no longer) held. */
    private boolean decide(SessionEvents.OpenProjectRequested event) {
        inFlightBundle = null;
        if (event.kind().mayNoOp() && activeProject.isAt(event.profilePath())) {
            inFlightKind = null;
            inFlightOpId = -1;
            auditLog.info("decision", "noOp")
                    .info("noOp", true)
                    .info("kind", event.kind().name())
                    .info("reason", "alreadyActive")
                    .info("opId", event.opId());
            // No effect. The record is the whole output — a no-op that says nothing is
            // indistinguishable from a request that was dropped.
            return true;
        }
        if (event.kind() == TransitionKind.CLOSE) {
            // Leaving a project loads nothing, so there is no result to wait for and the decision is
            // whole here. It is still a session boundary: the settings that are about to be replaced
            // by your own are the ones the open log was being read through.
            inFlightKind = null;
            inFlightOpId = -1;
            String note = close(event.opId(), TransitionKind.CLOSE, activeProject.name());
            effects.request(new SessionEffects.ShowStatusEffect(event.opId(), note));
            return true;
        }
        SessionEvents.CreateApproved approved = null;
        if (event.kind() == TransitionKind.CREATE) {
            // M73 (spec-create-project.md §3.5, §4): every refusal of a create is decided HERE, from the facts the
            // adapter observed. A refused create still superseded whatever was pending (D-73.8): the gate has
            // already taken this request as the newest, as for any project request.
            Refusal refusal = refuseCreate(event);
            if (refusal != null) {
                inFlightKind = null;
                inFlightOpId = -1;
                auditLog.info("decision", "refuseCreate")
                        .info("kind", event.kind().name())
                        .info("reason", refusal.code())
                        .info("source", event.source())
                        .info("opId", event.opId());
                effects.request(refusal.openExisting()
                        ? new SessionEffects.OfferOpenExistingEffect(event.opId(), event.profilePath())
                        : new SessionEffects.ShowWarningEffect(event.opId(), refusal.text()));
                return true;
            }
            approved = approve(event.create());
        }
        inFlightKind = event.kind();
        inFlightOpId = event.opId();
        effects.request(event.kind() == TransitionKind.CREATE
                ? new SessionEffects.CreateProfileEffect(event.opId(), event.profilePath(), approved)
                : event.kind() == TransitionKind.OPEN_BUNDLE
                ? new SessionEffects.PrepareBundleEffect(event.opId(), event.profilePath())
                : new SessionEffects.LoadProfileEffect(event.opId(), event.profilePath()));
        auditLog.info("decision", event.kind() == TransitionKind.CREATE ? "create"
                : event.kind() == TransitionKind.OPEN_BUNDLE ? "verifyBundle" : "load")
                .info("kind", event.kind().name())
                .info("source", event.source())
                .info("opId", event.opId());
        return true;
    }

    /** A refused create: a stable code for the record, the sentence for the caller, and whether to offer the open. */
    private record Refusal(String code, String text, boolean openExisting) {
        Refusal(String code, String text) {
            this(code, text, false);
        }
    }

    /**
     * The create's refusals, in the order a caller can act on them (spec §3.2, §3.5). Each names the next call.
     * Path refusals beyond "exists" and "not a directory" are for ASSISTANT origin only — a person's menu
     * behaviour is unchanged [R r2-1] — and so is the consent switch (D-73.7): a person is never gated.
     */
    private static Refusal refuseCreate(SessionEvents.OpenProjectRequested event) {
        SessionEvents.CreateRequest create = event.create();
        if (create == null || create.facts() == null) {
            return new Refusal("noFacts", "a create must carry what was observed in the directory; nothing was created");
        }
        SessionEvents.CreateFacts facts = create.facts();
        String dir = facts.dir();
        if (!facts.absolute()) {
            return new Refusal("relative", "the project directory must be an absolute path (a relative one would "
                    + "resolve against the analyser's working directory): " + dir + " — nothing was created");
        }
        boolean person = event.audience() != null
                && event.audience().origin() == SessionEvents.OperationOrigin.PERSON
                && event.assistantOrigin() == null;
        if (!facts.directory()) {
            // review m2: a person gets their own wording — a create never makes the project folder itself
            return new Refusal("notDirectory", person
                    ? dir + " is not an existing folder. New project… does not create one: make the folder, then "
                            + "choose it; nothing was created"
                    : dir + " is not an existing directory — create it first, then preview "
                            + "with open {project: \"" + dir + "\", create: true}; nothing was created");
        }
        if (facts.profileExists()) {
            return new Refusal("profileExists", dir + " already has a project (" + event.profilePath()
                    + ") — open it with open {project: \"" + dir + "\"}; nothing was created or overwritten", person);
        }
        if (!person) {
            if (!facts.createAllowed()) {
                return new Refusal("consentOff", "the assistant may not create projects on this machine: Settings ▸ "
                        + "Assistant ▸ Allow the assistant to create projects is off. Ask the person to turn it on, or "
                        + "to create the project with Project ▸ New project…; nothing was created");
            }
            if (facts.analyserProblem() != null) {
                return new Refusal("analyserDir", dir + "/.analyser " + facts.analyserProblem()
                        + " — a create writes only into a real .analyser directory under the project; nothing was created");
            }
            if (facts.bundleWorkingCopy()) {
                return new Refusal("bundleWorkingCopy", dir + " is inside an evidence bundle's working copy, which is "
                        + "unpacked afresh on every open — open the bundle with open {bundle} instead; nothing was created");
            }
            if (facts.exchangeDir() != null) {
                return new Refusal("exchangeDir", dir + " is inside the assistant's exchange directory ("
                        + facts.exchangeDir() + ") — choose a project directory outside it; nothing was created");
            }
            if (facts.enclosingProject() != null) {
                return new Refusal("nested", dir + " is inside the existing project " + facts.enclosingProject()
                        + " — open that one with open {project}, or ask the person to create a nested project with "
                        + "Project ▸ New project…; nothing was created");
            }
        }
        SessionEvents.CreateSelection selection = create.selection();
        SessionEvents.CreateOffer offer = facts.offer();
        java.util.List<String> unoffered = new java.util.ArrayList<>();
        for (String root : selection.sourceRoots()) {
            SessionEvents.OfferedRoot offered = offer.sourceRoots().stream()
                    .filter(r -> r.relative().equals(root)).findFirst().orElse(null);
            if (offered == null) unoffered.add("sourceRoot '" + root + "'");
            else if (offered.outside()) unoffered.add("sourceRoot '" + root + "' (it resolves outside " + dir + ")");
        }
        for (String skill : selection.skills()) {
            if (offer.skills().stream().noneMatch(k -> k.path().equals(skill))) unoffered.add("skill '" + skill + "'");
        }
        if (selection.referenceGuide() && !"CAN_CREATE".equals(offer.referenceGuide())) {
            unoffered.add("referenceGuide (the offer says " + offer.referenceGuide() + ")");
        }
        if (!unoffered.isEmpty()) {
            return new Refusal("notOffered", "not in the offer: " + String.join(", ", unoffered)
                    + " — preview with open {project: \"" + dir + "\", create: true} and choose from what it lists; "
                    + "nothing was created");
        }
        if (selection.offerDigest() != null && !selection.offerDigest().equals(offer.digest())) {
            return new Refusal("digestMismatch", "the offer changed since your preview — preview again with "
                    + "open {project: \"" + dir + "\", create: true}; nothing was created");
        }
        return null;
    }

    /** The node-approved items, in OFFER order (G3) — never the selection's order, which is the caller's. */
    private static SessionEvents.CreateApproved approve(SessionEvents.CreateRequest create) {
        SessionEvents.CreateFacts facts = create.facts();
        SessionEvents.CreateSelection selection = create.selection();
        java.util.List<String> roots = new java.util.ArrayList<>();
        for (SessionEvents.OfferedRoot r : facts.offer().sourceRoots()) {
            if (!r.outside() && selection.sourceRoots().contains(r.relative())) roots.add(r.absolute());
        }
        java.util.List<SessionEvents.OfferedSkill> skills = new java.util.ArrayList<>();
        for (SessionEvents.OfferedSkill k : facts.offer().skills()) {
            if (selection.skills().contains(k.path())) skills.add(k);
        }
        return new SessionEvents.CreateApproved(facts.dir(), roots, skills, selection.referenceGuide(),
                facts.offer().projectKind());
    }

    /**
     * M73 G2 [R F1]: a create or a load that THREW. Before this arm the failure was recorded and nothing else
     * happened, so the caller saw success and adopted into a config that was never written. Now it is a warning, and
     * nothing is left waiting for a result that will not come.
     */
    @OnEventHandler
    public boolean onEffectFailed(SessionEvents.EffectFailed event) {
        if (!gate.accepted() || inFlightKind == null || event.opId() != inFlightOpId) return false;
        boolean create = "createProfile".equals(event.effect());
        if (!create && !"loadProfile".equals(event.effect())) return false;
        TransitionKind kind = inFlightKind;
        inFlightKind = null;
        inFlightOpId = -1;
        inFlightBundle = null;
        auditLog.info("decision", "keep")
                .info("closingLog", false)
                .info("closingGraph", false)
                .info("kind", kind.name())
                .info("reason", create ? "createFailed" : "loadFailed")
                .info("opId", event.opId());
        effects.request(new SessionEffects.ShowWarningEffect(event.opId(),
                (create ? "Could not create the project: " : "Could not open the project: ") + event.reason()
                        + " — the settings in force before are unchanged"));
        return true;
    }

    /**
     * Decision two — close or keep. The profile has been read; now, and only now, is it known whether
     * there is anything to transition to.
     */
    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        if (!gate.accepted()) {
            // The gate has already recorded staleResult. Acting here would apply an outcome for an
            // operation that is no longer in flight.
            return false;
        }
        TransitionKind kind = inFlightKind;
        long opId = inFlightOpId;
        if (kind == TransitionKind.OPEN_BUNDLE && event.ok() && event.bundlePlan() != null) {
            inFlightBundle = event.bundlePlan();
            effects.request(new SessionEffects.LoadProfileEffect(opId, inFlightBundle.profilePath(), inFlightBundle.profileDigest()));
            auditLog.info("decision", "loadVerifiedBundleProfile").info("opId", opId);
            return true;
        }
        inFlightKind = null;
        inFlightOpId = -1;
        SessionEvents.BundlePlan bundle = inFlightBundle;
        inFlightBundle = null;

        if (kind == null) {
            auditLog.warn("decision", "ignored").warn("reason", "noRequestInFlight").warn("opId", opId);
            return true;
        }

        if (!event.ok()) {
            auditLog.info("decision", "keep")
                    .info("closingLog", false)
                    .info("closingGraph", false)
                    .info("kind", kind.name())
                    .info("reason", "loadFailed")
                    .info("opId", opId);
            effects.request(new SessionEffects.ShowWarningEffect(opId, event.reason()));
            return true;
        }

        String note = close(opId, kind, event.name());
        if (kind == TransitionKind.CREATE && event.reason() != null && !event.reason().isBlank()) {
            // what the adapter reports it adopted — a fact from the performer, composed into the one status line here
            note = note + "  ·  " + event.reason();
        }
        // Apply BEFORE the status line: the note describes settings that are in force by the time it
        // is read. The old code had the same order for the same reason.
        effects.request(new SessionEffects.ApplyProfileEffect(opId, event.profilePath(), event.name()));
        if (bundle != null) effects.request(new SessionEffects.OpenBundleEvidenceEffect(opId, bundle));
        effects.request(new SessionEffects.ShowStatusEffect(opId, note));
        return true;
    }

    /**
     * M74 P2b (§6; review P2 N-b): a walk binding the load dropped is said, not hidden — decided here, after the profile is
     * in force, rather than composed by the performer from a static buffer.
     */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (!gate.accepted() || event.droppedBindings().isEmpty()) return false;
        auditLog.info("decision", "sayDroppedBindings").info("count", event.droppedBindings().size())
                .info("opId", event.opId());
        effects.request(new SessionEffects.ShowStatusEffect(event.opId(),
                "walk bindings dropped on load: " + String.join("; ", event.droppedBindings())));
        return true;
    }

    /**
     * The rule itself, in one place — used by both moments it can be reached: after a load succeeded,
     * and immediately for {@link TransitionKind#CLOSE}, which has nothing to load.
     *
     * @return what to say about it; the caller decides where that sits relative to applying the profile
     */
    private String close(long opId, TransitionKind kind, String name) {
        boolean endsSession = kind.endsSession();
        boolean closingLog = endsSession && openLog.isOpen();
        boolean closingGraph = endsSession && openGraph.isOpen();

        if (closingLog) {
            effects.request(new SessionEffects.CloseLogEffect(opId));
        }
        if (closingGraph) {
            effects.request(new SessionEffects.CloseGraphEffect(opId, openGraph.graphPath()));
        }
        if (kind == TransitionKind.CLOSE) {
            effects.request(new SessionEffects.RestoreSettingsEffect(opId));
        }
        auditLog.info("decision", endsSession ? "close" : "keep")
                .info("closingLog", closingLog)
                .info("closingGraph", closingGraph)
                .info("kind", kind.name())
                .info("reason", reason(kind, endsSession))
                .info("opId", opId);
        return note(kind, name, closingLog, closingGraph);
    }

    /**
     * Why, in the vocabulary of the rule rather than of the surface. {@code ADOPT_FOR_OPEN_LOG} gets
     * its own reason because it is the exception a reader is most likely to think is a bug.
     */
    private static String reason(TransitionKind kind, boolean endsSession) {
        if (endsSession) {
            return "projectChanged";
        }
        return switch (kind) {
            case ADOPT_FOR_OPEN_LOG -> "adoptedForOpenLog";
            case STARTUP_ACTIVATION -> "startup";
            default -> "doesNotEndSession";
        };
    }

    private static String note(TransitionKind kind, String name, boolean closingLog, boolean closingGraph) {
        String head = kind == TransitionKind.CLOSE
                ? "closed project " + (name == null ? "" : name) + " — back to your own settings"
                : kind == TransitionKind.CREATE ? "created project " + name
                : "opened project " + name;
        if (!closingLog && !closingGraph) {
            return head;
        }
        String what = closingLog && closingGraph ? "log and graph" : closingLog ? "log" : "graph";
        return head + "  ·  closed the " + what + " — a project is a session boundary";
    }
}
