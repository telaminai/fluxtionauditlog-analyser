package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;
import telamin.fluxtion.audit.analyser.analyser.topology.PairingQualifications;

/**
 * M44.4b (spec §13, D-S13.1/.3): what the session has decided, as of the last operation that completed.
 *
 * <p>Immutable, and published by {@link SessionDriver} through a {@code volatile} reference after every operation, so
 * any thread may read it: the socket thread's coverage verb used to read the processor's live fields while the EDT
 * could be mid-dispatch, and a reader could see half a cycle. A surface RENDERS this; it never computes a verdict of
 * its own beside it — that duplicate is what four rounds of M68.1 review kept finding out of step.
 *
 * <p>{@code logGeneration} and {@code graphRevision} identify the PAIR a verdict is about: a later snapshot with both
 * unchanged is the same log and graph re-scoped (a Follow append), so anything qualifying the earlier verdict still
 * describes this one; a change in either means a different pair.
 *
 * @param pairing        null when either artefact is missing — "cannot say" is a verdict, not a gap
 * @param pending        a log open is in flight (M44.4c): the verdict in force is about the log being replaced, so a
 *                       surface states it as pending rather than as current (review B1)
 * @param qualifications what wider comparisons say about {@code pairing}, or null — an independent, READ-ONLY copy
 *                       (M44.4c; independent review R4)
 * @param filterKey      the view filter in force, by identity, so a comparison made under another reads as stale
 * @param logIdentity    M68.5: Follow's verdict about the log FILE ({@code VERIFIED}/{@code UNVERIFIED}/{@code REPLACEMENT}),
 *                       {@code REOPENED} after a replacement, or null before Follow has polled
 * @param localAgent     M74: the local agent (Claude Code) as localAgentSession decided it — ids and enums only
 * @param startPage      M74 P2: the start page's prompt as startPagePrompt decided it — its list and its route
 * @param proposal       M74 P2b: Claude Code's set-up proposal as onboardingProposal decided it
 * @param example        M74 P2b: a prompt-pack example's download as exampleDownload decided it
 * @param permission     M74 P3: the permission prompt as localAgentPermission decided it — ids, a class, a vocabulary name
 * @param profiles       M74 UX: the assistant profile and its overlay, as assistantProfile decided them
 * @param listener       review B5: the action listener (REST switch, external access, the bridge), as actionListener decided it
 * @param aiSetup        M77: Set up AI — the wizard, the first-run block, the AI line — as aiSetup decided it
 * @param recordsView    what the Records view shows and what each filter hides, as recordsView last measured it — null
 *                       before the first measurement of the open log, and with no log
 * @param starter        Option 2: the starter catalogue and a starter project being created, as starterProject decided them
 */
public record SessionSnapshot(boolean logOpen, String logPath, long logGeneration, int sampled, int total,
                              boolean graphOpen, String graphPath, String graphSource, long graphRevision,
                              GraphPairing pairing, CoveragePolicy.Assessment claim, boolean pending,
                              PairingQualifications qualifications, String filterKey,
                              String logIdentity, String logIdentityReason,
                              telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics producerFindings,
                              telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport timeOrder,
                              String provenance, String provenanceSource, boolean following,
                              String contentSignature, String followReadFailure, boolean evidencePending,
                              WalkPlaybackState walkPlayback, CaptureState capture, AssistantState assistant,
                              BundleProvenance bundle, LocalAgentState localAgent, StartPageState startPage,
                              ProposalState proposal, ExampleDownloadState example, PermissionState permission,
                              ProfileState profiles, ListenerState listener, long logOpenedAt,
                              AiSetupState aiSetup,
                              telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts recordsView,
                              StarterState starter) {

    /**
     * Independent review R4: immutable in fact, not only by the reference that publishes it. A volatile field cannot make
     * its object graph read-only, and the qualifications were an ordinary mutable holder — a consumer's {@code clear()}
     * erased them from every later read. Whatever is passed in is frozen here.
     */
    public SessionSnapshot {
        if (qualifications != null) qualifications = qualifications.frozenCopy();
        if (walkPlayback == null) walkPlayback = WalkPlaybackState.IDLE;
        if (capture == null) capture = CaptureState.IDLE;
        if (assistant == null) assistant = AssistantState.IDLE;
        if (bundle == null) bundle = BundleProvenance.NONE;
        if (localAgent == null) localAgent = LocalAgentState.IDLE;
        if (startPage == null) startPage = StartPageState.IDLE;
        if (proposal == null) proposal = ProposalState.NONE;
        if (example == null) example = ExampleDownloadState.IDLE;
        if (permission == null) permission = PermissionState.NONE;
        if (profiles == null) profiles = ProfileState.NONE;
        if (listener == null) listener = ListenerState.NONE;
        if (aiSetup == null) aiSetup = AiSetupState.IDLE;
        if (starter == null) starter = StarterState.IDLE;
    }

    /** Before the first operation: nothing is open and nothing may be claimed. */
    public static final SessionSnapshot EMPTY =
            new SessionSnapshot(false, null, 0, 0, 0, false, null, null, 0, null, null, false, null, null, null, null,
                    null, null, null, null, false, null, null, false, WalkPlaybackState.IDLE, CaptureState.IDLE,
                    AssistantState.IDLE, BundleProvenance.NONE, LocalAgentState.IDLE, StartPageState.IDLE,
                    ProposalState.NONE, ExampleDownloadState.IDLE, PermissionState.NONE, ProfileState.NONE,
                    ListenerState.NONE, 0, AiSetupState.IDLE, null, StarterState.IDLE);

    static SessionSnapshot of(SessionProcessor p) {
        return new SessionSnapshot(p.openLog.isOpen(), p.openLog.logPath(), p.openLog.generation(),
                p.openLog.sampled(), p.openLog.total(),
                p.openGraph.isOpen(), p.openGraph.graphPath(), p.openGraph.source(), p.openGraph.revision(),
                p.pairing.verdict(), p.coverageClaim.assessment(),
                p.operationGate.inFlightWhat() != null,
                p.pairingQualifier.qualifications(), p.pairingQualifier.filterKey(),
                p.openLog.identity(), p.openLog.identityReason(),
                // M44.5: the log's own derived state, owned by logEvidence and openLog — every surface renders these
                p.logEvidence.findings(), p.logEvidence.timeOrder(),
                p.openLog.provenance(), p.openLog.provenanceSource(), p.openLog.following(),
                p.logEvidence.signature(), p.logEvidence.readFailure(),
                p.logEvidence.scanPending(),
                p.walkPlayback.state(),             // M69: the showing walk, as walkPlayback decided it
                p.evidenceCapture.state(),          // evidence bundle capture, as evidenceCapture decided it
                p.assistantLoop.state(),            // OA-1: the onboard assistant, as assistantLoop decided it
                p.openBundle.provenance(),          // where this session came from, as openBundle decided it
                p.localAgentSession.state(),        // M74: the local agent, as localAgentSession decided it
                p.startPagePrompt.state(),          // M74 P2: the start page's prompt, as startPagePrompt decided it
                p.onboardingProposal.state(),       // M74 P2b: the set-up proposal, as onboardingProposal decided it
                p.exampleDownload.state(),          // M74 P2b: an example's download, as exampleDownload decided it
                p.localAgentPermission.state(),     // M74 P3: the permission prompt, as localAgentPermission decided it
                p.aiSetup.state(),                  // M74 UX: the assistant profile and its overlay, as aiSetup decided them
                p.actionListener.state(),           // review B5: the action listener, as actionListener decided it
                p.openLog.openedAtMillis(),         // final review #12: when the open log was opened (the binding chip)
                p.aiSetup.setupState(),             // M77: Set up AI, as aiSetup decided it
                p.recordsView.counts(),             // the Records view's counts, as recordsView measured them
                p.starterProject.state());          // Option 2: the starter catalogue and chain, as starterProject decided
    }

    /** The verdict a surface may state as CURRENT: none while a log open is pending. */
    public GraphPairing publishedPairing() {
        return pending ? null : pairing;
    }

    /** Whether {@code other} is about the same log and the same graph — the qualifications' binding. */
    public boolean samePairAs(SessionSnapshot other) {
        return other != null && logOpen && graphOpen && other.logOpen && other.graphOpen
                && logGeneration == other.logGeneration && graphRevision == other.graphRevision;
    }
}
