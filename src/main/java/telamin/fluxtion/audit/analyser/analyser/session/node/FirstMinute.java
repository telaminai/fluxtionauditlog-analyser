package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition;
import telamin.fluxtion.audit.analyser.analyser.firstminute.FirstMinuteLines;
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents;
import telamin.fluxtion.audit.analyser.analyser.session.FirstMinuteState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import java.util.List;

/** M79: startup admission, causal shipped identity, playback and doors. No paths, I/O, timers or Swing. */
public class FirstMinute implements EventLogSource {
    private final OpenLog openLog;
    private final WalkPlayback walkPlayback;
    private final SessionRecovery sessionRecovery;
    private final OperationGate gate;
    private final DesignSession designSession;
    @PushReference private final EffectQueue effects;
    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private SessionEvents.FirstMinuteStartupObserved startup;
    private boolean startupDecided, startupSuperseded;
    private String phase = "NONE", reason = "";
    private long ticket, operationAtStart, launchOperation = -1, generation = -1;
    private long walkTicketAtStart;
    private boolean readRequested, presentationRequested, bundled, marked, personalContent;
    private long verifiedGeneration = -1, verifiedTicket = -1;
    private long failedGeneration = -1, failedTicket = -1;
    private long ownedDesignReadGeneration = -1;

    public FirstMinute(OpenLog openLog, WalkPlayback walkPlayback, SessionRecovery sessionRecovery,
                       OperationGate gate, DesignSession designSession, EffectQueue effects) {
        this.openLog = openLog; this.walkPlayback = walkPlayback; this.sessionRecovery = sessionRecovery;
        this.gate = gate; this.designSession = designSession; this.effects = effects;
    }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }

    @OnEventHandler public boolean startup(SessionEvents.FirstMinuteStartupObserved e) {
        if (startup != null) return false;
        startup = e;
        decideStartup();
        return true;
    }

    @OnEventHandler public boolean projectChosen(SessionEvents.OpenProjectRequested e) { return supersedeStartup(); }
    @OnEventHandler public boolean logChosen(SessionEvents.OpenLogRequested e) { return supersedeStartup(); }
    @OnEventHandler public boolean graphChosen(SessionEvents.GraphOpened e) {
        if (presentationRequested && e.firstMinuteTicket() == ticket) return false;
        return personalPresentation();
    }

    @OnEventHandler public boolean designChosen(DesignEvents.ReadRequested e) {
        if (!"open".equals(e.kind())) return false;
        if (presentationRequested && e.firstMinuteTicket() > 0
                && permitsPreparation(e.firstMinuteTicket(), generation)) {
            ownedDesignReadGeneration = designSession.generation();
            return true;
        }
        // Intent supersedes startup immediately, even while the person's file is still being read.
        boolean moved = supersedeStartup();
        return cancelPresentation() || moved;
    }

    @OnEventHandler public boolean designOpened(DesignEvents.ReadCompleted e) {
        // The parent accepts the epoch first. A stale/refused completion cannot invent an addition.
        if (!e.sessionOpen() || !designSession.acceptedRead()
                || e.generation() == ownedDesignReadGeneration) return false;
        return personalPresentation();
    }

    @OnEventHandler public boolean resultOpened(DesignEvents.ResultReadCompleted e) {
        if (e.result() == null || !designSession.acceptedResult()) return false;
        return personalPresentation();
    }

    private boolean personalPresentation() {
        boolean moved = supersedeStartup();
        if (!personalContent) { personalContent = true; moved = true; }
        bundled = false;
        // Ownership is sticky in every phase, including after Close or a cancelled door.
        return cancelPresentation() || moved;
    }

    private boolean cancelPresentation() {
        // DONE can still own a queued door callback; another choice retires that receipt too.
        if (List.of("OPENING", "PLAYING", "DOORS", "DONE", "FAILED").contains(phase)) {
            phase = "DONE"; reason = ""; bundled = false; ticket++;
            return true;
        }
        return false;
    }

    private boolean supersedeStartup() {
        if (startupDecided || startupSuperseded) return false;
        startupSuperseded = true;
        return true;
    }

    private boolean decideStartup() {
        if (startup == null || startupDecided) return false;
        String recovery = sessionRecovery.state();
        if ("idle".equals(recovery) || "checking".equals(recovery)) return false;
        startupDecided = true;
        boolean fresh = startup.firstRunAtStart() && startup.recentProjects() == 0 && startup.recentFiles() == 0
                && startup.recentBundles() == 0 && !startup.played() && !startup.restLaunch() && !startup.launchFile()
                && !startup.startupSurfaceDue() && !openLog.isOpen()
                // Unlike the operation gate's resettable id, this remembers any earlier choice at startup.
                && !startupSuperseded && gate.inFlightWhat() == null && !walkPlayback.state().showing();
        if (!fresh || "offered".equals(recovery) || "verifying".equals(recovery) || "restoring".equals(recovery)) {
            auditLog.info("firstMinute", "deferred");
            return true;
        }
        phase = "OPENING";
        operationAtStart = gate.expectedOpId();
        generation = openLog.generation();
        walkTicketAtStart = walkPlayback.state().ticket();
        effects.request(new SessionEffects.OpenDiamondEffect(0, ++ticket));
        auditLog.info("firstMinute", "opening").info("ticket", ticket);
        return true;
    }

    /** Admission of the install callback. A request made while extraction ran supersedes it, even if that request failed. */
    public boolean permitsOpen(long named) {
        return named == ticket && "OPENING".equals(phase) && launchOperation < 0
                && openLog.generation() == generation && gate.expectedOpId() == operationAtStart
                && walkPlayback.state().ticket() == walkTicketAtStart;
    }

    @OnEventHandler public boolean launchAssigned(SessionEvents.FirstMinuteLaunchAssigned e) {
        if (!permitsOpen(e.ticket())) return false;
        launchOperation = e.operation();
        return true;
    }

    public boolean permitsAssignedLaunch(long named, long operation) {
        return named == ticket && "OPENING".equals(phase) && launchOperation == operation
                && gate.expectedOpId() == operationAtStart && openLog.generation() == generation;
    }

    @OnEventHandler public boolean failed(SessionEvents.FirstMinuteOpenFailed e) {
        if (e.ticket() != ticket || !("OPENING".equals(phase) || "PLAYING".equals(phase) && !marked)) return false;
        phase = "FAILED"; reason = e.reason() == null ? "the evidence could not be opened" : e.reason();
        if (openLog.isOpen() && WalkPlayback.FIRST_MINUTE.equals(openLog.launch()) && openLog.firstMinuteTicket() == ticket) {
            failedGeneration = openLog.generation();
            failedTicket = ticket;
        }
        auditLog.warn("firstMinute", "failed").warn("reason", reason);
        return true;
    }

    public boolean permitsPreparation(long named, long logGeneration) {
        return named == ticket && "OPENING".equals(phase) && openLog.isOpen()
                && logGeneration == generation && generation == openLog.generation()
                && WalkPlayback.FIRST_MINUTE.equals(openLog.launch()) && openLog.firstMinuteTicket() == ticket;
    }

    public boolean permitsPlayback(long named) {
        return named == ticket && "PLAYING".equals(phase) && bundled && openLog.isOpen()
                && generation == openLog.generation() && gate.expectedOpId() == launchOperation
                && walkPlayback.state().ticket() == walkTicketAtStart;
    }

    public boolean permitsDoor(long named) {
        return named == ticket && "DONE".equals(phase) && gate.expectedOpId() == launchOperation;
    }

    @OnEventHandler public boolean read(SessionEvents.FirstMinuteLogReadObserved e) {
        if (!permitsPreparation(e.ticket(), e.generation()) || presentationRequested) return false;
        if (!DiamondDefinition.LOG_SHA256.equals(e.sha256())) return failed(new SessionEvents.FirstMinuteOpenFailed(ticket,
                e.reason() == null ? "the loaded log does not match the bundled diamond manifest" : e.reason()));
        presentationRequested = true;
        effects.request(new SessionEffects.PrepareDiamondPresentationEffect(0, ticket, generation));
        return true;
    }

    @OnEventHandler public boolean presentation(SessionEvents.FirstMinutePresentationObserved e) {
        if (!permitsPreparation(e.ticket(), e.generation()) || !presentationRequested) return false;
        if (!DiamondDefinition.GRAPH_SHA256.equals(e.graphSha256())
                || !DiamondDefinition.DESIGN_SHA256.equals(e.designSha256()))
            return failed(new SessionEvents.FirstMinuteOpenFailed(ticket,
                    e.reason() == null ? "the graph or design does not match the bundled diamond manifest" : e.reason()));
        bundled = true;
        verifiedGeneration = generation;
        verifiedTicket = ticket;
        phase = "PLAYING";
        effects.request(new SessionEffects.PlayBundledWalkEffect(0, ticket, DiamondDefinition.RESOURCE,
                DiamondDefinition.NAME, WalkPlayback.FIRST_MINUTE, DiamondDefinition.WALK_SHA256,
                telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState.Stage.TOPOLOGY));
        return true;
    }

    /** A queued outgoing capture retains its own generation/read/launch facts across a subsequent open. */
    @OnEventHandler public boolean capture(SessionEvents.FirstMinuteCaptureObserved e) {
        boolean owned = e.generation() == verifiedGeneration && e.launchTicket() == verifiedTicket
                && DiamondDefinition.LOG_SHA256.equals(e.readDigest());
        // A failed launch never grants bundled standing, but its exact receipt still prevents an
        // unverified teaching load being mistaken for the person's ordinary recovery evidence.
        owned |= e.generation() == failedGeneration && e.launchTicket() == failedTicket && failedTicket > 0;
        if (owned) personalContent |= e.personalContent();
        effects.request(new SessionEffects.AdmitSessionCaptureEffect(0, e.request(), !owned || personalContent));
        auditLog.info("sessionCapture", owned && !personalContent ? "omitted first minute" : "ordinary session");
        return true;
    }

    @OnEventHandler public boolean door(SessionEvents.FirstMinuteDoorChosen e) {
        if (!List.of("PLAYING", "DOORS").contains(phase) || !List.of("learn", "investigate", "build", "sample").contains(e.door())) return false;
        phase = "DONE";
        effects.request(new SessionEffects.RunFirstMinuteDoorEffect(0, ticket, e.door()));
        return true;
    }

    @OnEventHandler public boolean close(SessionEvents.FirstMinuteCardClosed e) {
        if (!List.of("PLAYING", "DOORS").contains(phase)) return false;
        phase = "DONE";
        return true;
    }

    @OnTrigger public boolean changed() {
        boolean moved = decideStartup();
        if (bundled && (!openLog.isOpen() || generation != openLog.generation()
                || !WalkPlayback.FIRST_MINUTE.equals(openLog.launch()))) {
            bundled = false; phase = "DONE"; ticket++;
            return true;
        }
        if ("FAILED".equals(phase) && (generation != openLog.generation()
                || gate.expectedOpId() != (launchOperation < 0 ? operationAtStart : launchOperation))) {
            phase = "DONE"; reason = "";
            return true;
        }
        if (!List.of("OPENING", "PLAYING", "DOORS").contains(phase)) return moved;
        var playback = walkPlayback.state();
        if (playback.ticket() != walkTicketAtStart && !WalkPlayback.FIRST_MINUTE.equals(playback.origin())) {
            phase = "DONE"; bundled = false; ticket++;
            return true;
        }
        long expected = launchOperation < 0 ? operationAtStart : launchOperation;
        if (gate.expectedOpId() != expected || (readRequested || bundled)
                && (!openLog.isOpen() || openLog.generation() != generation)) {
            phase = "DONE"; bundled = false; ticket++;
            return true;
        }
        if ("OPENING".equals(phase) && openLog.isOpen() && WalkPlayback.FIRST_MINUTE.equals(openLog.launch())
                && openLog.firstMinuteTicket() == ticket && !readRequested) {
            generation = openLog.generation();
            readRequested = true;
            effects.request(new SessionEffects.VerifyDiamondReadEffect(0, ticket, generation));
            return true;
        }
        if ("PLAYING".equals(phase) && WalkPlayback.FIRST_MINUTE.equals(playback.origin())) {
            if (!marked && playback.step() == 0 && playback.accepted() == 0 && "SHOWN".equals(playback.phase())) {
                marked = true;
                effects.request(new SessionEffects.MarkFirstMinutePlayedEffect(0, ticket));
                moved = true;
            }
            if (!playback.showing()) { phase = "DOORS"; moved = true; }
        }
        return moved;
    }

    public FirstMinuteState state() {
        return new FirstMinuteState(phase, "generic", "DOORS".equals(phase) ? List.of("learn", "investigate", "build") : List.of(),
                "DOORS".equals(phase) ? FirstMinuteLines.CALLOUT : "", reason, bundled);
    }
}
