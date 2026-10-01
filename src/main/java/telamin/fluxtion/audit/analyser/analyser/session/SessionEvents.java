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
    public record OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source,
                                       AssistantActionOrigin assistantOrigin) {
        public OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source) {
            this(opId, profilePath, kind, source, null);
        }
    }

    /**
     * A combined {@code open} request arrived, and these are the parameter names it supplied.
     *
     * <p>Only the NAMES. The decision this feeds is about which act a request means, not about any
     * path or value — and keeping the values out means the record of that decision can be read by
     * anyone without carrying a filesystem into it.
     */
    public record OpenRequestReceived(long opId, java.util.Set<String> supplied) {

        public OpenRequestReceived {
            supplied = supplied == null ? java.util.Set.of() : java.util.Set.copyOf(supplied);
        }
    }

    /**
     * M44.3: someone asked for a log to be opened. The processor decides (it always says yes today —
     * the point is that the open is now an OPERATION with an id, so a load that lands late, or for a
     * request that was superseded, is refused rather than believed).
     *
     * @param location   a local path or an {@code s3://} URI, as a string — the graph never holds a Path
     * @param format     an explicit reader format, or null for auto-detection
     * @param provenance which system the log came from, as the opener declared it (§E), or null
     * @param fromSocket whether an agent asked; carried for the record and for the adapter's audience
     */
    public record OpenLogRequested(long opId, String location, String format, String provenance,
                                   boolean fromSocket, AssistantActionOrigin assistantOrigin) {
        public OpenLogRequested(long opId, String location, String format, String provenance,
                                boolean fromSocket) {
            this(opId, location, format, provenance, fromSocket, null);
        }
    }

    /**
     * M44.3b: someone asked for something to be CLOSED — Audit log ▸ Close log, Reset, or {@code open {close}}
     * over the socket. The close itself is still performed by the adapter, as it always was; what this
     * event adds is that the processor HEARS the request, because a request is what supersedes.
     *
     * <p><b>The policy (owner, 2026-09-17): a close supersedes a pending open OF THE SAME KIND.</b> Before
     * this, a close during a pending open closed the previous log and the pending one still landed and was
     * accepted — someone who asked for nothing to be open had a log arrive two seconds later. The rule is
     * D-A3's, unchanged: the last deliberate request wins. A close that covers the log is a newer deliberate
     * request about the log, so the outstanding open's late result is refused like any superseded one.
     *
     * <p><b>And only of the same kind.</b> Closing the GRAPH says nothing about the log, so it leaves a
     * pending open alone. The finish-first review's warning is the constraint here: <i>do not give every
     * close a new meaning to fix an unrelated finding</i>.
     *
     * @param target what the close covers
     */
    public record CloseRequested(long opId, Target target, AssistantActionOrigin assistantOrigin) {
        public CloseRequested(long opId, Target target) {
            this(opId, target, null);
        }

        /** What a close covers. Leaving a project is NOT here: that is a project transition, which already supersedes. */
        public enum Target {
            LOG, GRAPH, ALL;

            /** Whether this close is a request about the LOG — the only thing an open can be pending for. */
            public boolean coversLog() {
                return this == LOG || this == ALL;
            }
        }

        public CloseRequested {
            if (target == null) throw new IllegalArgumentException("a close names what it covers");
        }
    }

    // ---------------------------------------------------------------- results
    /**
     * M44.3 D-A2: the adapter has STARTED the work and will submit the real result later, with this
     * opId. Nothing is closed, nothing applied; the operation is in flight. Recorded like any result so
     * a reader sees asked → pending → outcome rather than a gap (D-A5).
     */
    public record Pending(long opId, String what) implements Result {
    }
    /**
     * The load landed and the log is open in the adapter. THE arrival: {@code LogArrival} judges an
     * open graph on this fact, never on a refresh observation (M44.3a).
     *
     * @param loggedNodeIds distinct instanceIds in the sampled records — raw, so the graph computes the
     *                      pairing; {@code sampled} of {@code total} records were scanned
     */
    public record LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                            int sampled, int total, String mostVerboseLevel, String provenanceSource,
                            boolean followable, int nodeLogsWithheld) implements Result {
        public LogOpened {
            loggedNodeIds = loggedNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(loggedNodeIds);
        }

        /** UPS-1: no sampled record withheld its node logs (the count is drawn by the same sample as the ids). */
        public LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                         int sampled, int total, String mostVerboseLevel, String provenanceSource, boolean followable) {
            this(opId, logPath, provenance, loggedNodeIds, sampled, total, mostVerboseLevel, provenanceSource, followable, 0);
        }

        /** M44.5 stage 1: no follow capability stated — the log is taken as one that cannot be followed. */
        public LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                         int sampled, int total, String mostVerboseLevel, String provenanceSource) {
            this(opId, logPath, provenance, loggedNodeIds, sampled, total, mostVerboseLevel, provenanceSource, false);
        }

        /** Before M44.5: no provenance source. It is "declared by the opener" when a provenance was given. */
        public LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                         int sampled, int total, String mostVerboseLevel) {
            this(opId, logPath, provenance, loggedNodeIds, sampled, total, mostVerboseLevel,
                    provenance == null ? null : "declared by the opener");
        }
    }
    /** The load did not land. The previously open log, if any, is still the open one. */
    public record LogOpenFailed(long opId, String location, String reason) implements Result {
    }

    /**
     * The adapter read the profile file. <b>This is not "the project is active"</b> — it means the file
     * parsed. Nothing is in force until {@link ProfileApplied}, and confusing the two is how an audit
     * log starts describing intentions.
     */
    public record ProfileLoaded(long opId, String profilePath, boolean ok, String name,
                                int unknownKeys, String reason, BundlePlan bundlePlan) implements Result {
        public ProfileLoaded(long opId, String profilePath, boolean ok, String name,
                             int unknownKeys, String reason) {
            this(opId, profilePath, ok, name, unknownKeys, reason, null);
        }
    }

    /**
     * The source roots now in force, as the adapter observes them after a person changed them.
     *
     * <p>Deliberately NOT reported while a transition is rendering. A close puts the person's own settings
     * back before the render runs, so reporting then would state somebody else's roots as this session's —
     * which is exactly how a bundle came to be anchored to 19 unrelated repositories (2026-09-30). The
     * adapter reports what it sees when a person changes it; the node decides what that means.
     */
    public record SourceRootsObserved(java.util.List<String> roots) {
        public SourceRootsObserved {
            roots = java.util.List.copyOf(roots == null ? java.util.List.of() : roots);
        }
    }

    /** Facts extracted from a verified bundle, before any active project is changed. */
    /**
     * @param profilePath the profile INSIDE the unpacked working copy
     * @param source      the {@code .fexp} the person actually chose. Carried here, not kept beside the
     *                    session by whoever asked: the surface used to hold its own copy, which is the
     *                    "value recomputed at a second call site" rule 9 forbids.
     */
    public record BundlePlan(String profilePath, String graphPath, String logPath,
                             String identity, String workingCopy, String limits, String notes, String source,
                             String processor) {
        /** A plan with nothing the sender wrote — the shape every pre-#73 caller uses. */
        public BundlePlan(String profilePath, String graphPath, String logPath,
                          String identity, String workingCopy, String limits) {
            this(profilePath, graphPath, logPath, identity, workingCopy, limits, "", null, null);
        }

        public BundlePlan(String profilePath, String graphPath, String logPath,
                          String identity, String workingCopy, String limits, String notes) {
            this(profilePath, graphPath, logPath, identity, workingCopy, limits, notes, null, null);
        }

        /** Without the processor — the shape callers before 2026-09-30 use. */
        public BundlePlan(String profilePath, String graphPath, String logPath, String identity,
                          String workingCopy, String limits, String notes, String source) {
            this(profilePath, graphPath, logPath, identity, workingCopy, limits, notes, source, null);
        }

        public BundlePlan {
            notes = notes == null ? "" : notes;
        }
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

    // ---------------------------------------------------------------- facts
    //
    // M44.4a (spec §13, D-S13.2). These replace LogObserved and GraphObserved, which were state snapshots modelled
    // as events ("here is what is open") and needed a mirror guard and dirty-on-change logic to be safe. A fact says
    // what HAPPENED. Nobody requested it, so it carries no opId; a fact about a log carries the log GENERATION it
    // was read from instead, so a fact about a log that has since been closed or replaced is refused as staleFact.

    /**
     * A topology graph is now the one on screen, however it got there: a file opened from a menu or the socket,
     * a candidate chosen in discovery, or a graph a log's reader supplied. One entrance, one record.
     *
     * @param source          {@code OPENED} / {@code READER_DECLARED} / {@code READER_INFERRED} — the graph's provenance
     * @param declaredNodeIds every node id the graph declares, raw, so the processor computes the pairing
     * @param nodeTypes       every node's simple type name, which is how audit installation is read
     */
    public record GraphOpened(String graphPath, String source, java.util.Set<String> declaredNodeIds,
                              java.util.List<String> nodeTypes, AssistantActionOrigin assistantOrigin) {
        public GraphOpened(String graphPath, String source, java.util.Set<String> declaredNodeIds,
                           java.util.List<String> nodeTypes) {
            this(graphPath, source, declaredNodeIds, nodeTypes, null);
        }
        public GraphOpened {
            declaredNodeIds = declaredNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(declaredNodeIds);
            nodeTypes = nodeTypes == null ? java.util.List.of() : java.util.List.copyOf(nodeTypes);
        }
    }

    /**
     * The graph left the screen outside a transition: Sources ▸ Close graph, Project ▸ Close log and topology, or a
     * reader's graph retired with its log. Inside a transition the processor already learned it from {@link GraphClosed}, and this one then
     * arrives after the operation and changes nothing — which the record shows, rather than the frame guessing.
     */
    public record GraphCleared(AssistantActionOrigin assistantOrigin) {
        public GraphCleared() { this(null); }
    }

    /** The log of {@code generation} closed outside a transition. The counterpart of {@link GraphCleared}. */
    public record LogCleared(long generation, AssistantActionOrigin assistantOrigin) {
        public LogCleared(long generation) { this(generation, null); }
    }

    /**
     * The open log of {@code generation} grew (Follow). Carries the new total and the arrival sample as it now
     * stands — the sample only changes while the log is shorter than the sample size, so above that only the total
     * moves, and with it the pairing's scope ("first 500 of 601").
     */
    public record LogAppended(long generation, java.util.Set<String> loggedNodeIds, int sampled, int total,
                              String mostVerboseLevel, int nodeLogsWithheld) {
        public LogAppended {
            loggedNodeIds = loggedNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(loggedNodeIds);
        }

        /** UPS-1: no sampled record withheld its node logs. */
        public LogAppended(long generation, java.util.Set<String> loggedNodeIds, int sampled, int total,
                           String mostVerboseLevel) {
            this(generation, loggedNodeIds, sampled, total, mostVerboseLevel, 0);
        }
    }

    /**
     * M44.4c: a whole-scope membership comparison was made (the {@code coverage} verb). It carries the identity of the
     * pair it was made against, CAPTURED BEFORE THE SCAN, because the scan runs off the EDT: a comparison of one log
     * must never qualify the verdict about another that opened while it ran. {@code echo} is the coverage result.
     */
    public record MembershipCompared(long logGeneration, long graphRevision, java.util.Map<String, Object> echo) {
        public MembershipCompared {
            echo = echo == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(echo));
        }
    }

    /**
     * M44.4c: the view filter changed. A comparison made under another filter is then stale, and the snapshot must know
     * which filter is in force to say so. {@code filterKey} is null for no filter.
     */
    public record ViewFilterChanged(String filterKey, AssistantActionOrigin assistantOrigin) {
        public ViewFilterChanged(String filterKey) { this(filterKey, null); }
    }

    /**
     * M68.5 (spec-evidence-integrity D-E6): what a Follow poll established about the open log's FILE — {@code UNCHANGED},
     * {@code APPEND}, {@code REPLACEMENT} or {@code UNVERIFIED} — with its reason. Posted only when the verdict changes.
     * A replacement is followed by a reopen, which is a new log generation, so nothing said about the old content
     * survives it; this fact is what lets the reopened log say WHY it was reopened.
     */
    public record LogIdentityObserved(long generation, String verdict, String reason) {
    }

    // ---------------------------------------------------------------- M44.5: the log's own derived state

    /**
     * M44.5: the adapter has SCHEDULED a {@code ScanLogEvidenceEffect}; the findings and the time order arrive later as
     * facts. Deliberately not {@link Pending}: a scan is not an operation, so the gate must not see it in flight, and
     * the published pairing must not be withdrawn while a scan runs.
     */
    public record ScanScheduled(long opId, long generation) implements Result {
    }

    /**
     * M44.5: what a Follow poll found about the open log's CONTENT — its records, the frame still being written, what
     * the stream end reports (its state and runs: a marker can arrive with no record), how much damage the reader has
     * recorded, and why the read failed ({@code readFailure}, null when it read). {@code LogEvidence} compares it with the last one and asks for a rescan
     * only when it moved, so a repeated identical failure costs nothing; the frame no longer decides when findings are
     * stale.
     */
    public record LogContentObserved(long generation, int total, int pendingChars, String streamEnd, int damage,
                                     String readFailure) {
    }

    /** M44.5: the log's producer findings, computed by the adapter for the generation it names. */
    public record ProducerFindingsObserved(long generation,
                                           telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics findings) {
    }

    /** M44.5: the log's time-order report, computed by the adapter for the generation it names. */
    public record TimeOrderObserved(long generation,
                                    telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport report) {
    }

    /** M44.5: Follow was switched on or off for the open log — state the status line is composed from. */
    public record FollowToggled(long generation, boolean on) {
    }

    // ---------------------------------------------------------------- M69 spotlight walks
    //
    // Playback is the walkPlayback node's (the M69 walk spec, §3.8; owner 2026-09-27): these facts are what the
    // person, the agent and the adapter REPORT; every transition and every decision about them is the node's.

    /** One target of a showing step, as the adapter resolved it and the node publishes it. */
    public record WalkTargetState(int n, String target, String caption, String state, boolean available, String reason) {
        public WalkTargetState {
            caption = caption == null ? "" : caption;
            state = state == null ? "UNRESOLVED" : state;
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * Play a saved walk. {@code step} is 0-based; {@code -1} means "from the step last shown" (Play from step N).
     * Review PR57 R6: the fact carries the DEFINITION, which the node holds frozen while it shows it, so no effect ever
     * reads a step from mutable configuration.
     * Review PR57 R7: {@code request} identifies THIS request; the node publishes its answer to it
     * ({@link WalkPlaybackState#answer()}), so a caller never infers acceptance from whatever happens to be showing.
     * {@code 0} means the caller does not await an answer.
     */
    public record WalkPlayRequested(long request, telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec walk, int step, String origin) {
    }

    /**
     * Review PR57 R6: a walk definition changed — saved (created or replaced), deleted ({@code now} null), or renamed
     * ({@code renamedTo}). Posted by the adapter on EVERY such change, deciding nothing: whether a showing walk ends
     * or keeps its frozen version is the node's decision.
     */
    public record WalkDefinitionChanged(String name, telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec now, String renamedTo) {
    }

    /** ◀ ▶ or ← →: move by {@code delta} steps. */
    public record WalkNavigated(int delta) {
    }

    /** Esc, ✕, a press outside the strip, or a view change from outside the walk. */
    public record WalkEndRequested(String reason) {
    }

    /**
     * The adapter prepared the step it was asked for (§3.4) — view applied, charts ready or timed out, every target
     * resolved against what is on screen now. Refused when its ticket or generation is not the node's current one.
     */
    public record WalkStepPrepared(long ticket, long generation, java.util.List<WalkTargetState> targets, String note) {
        public WalkStepPrepared {
            targets = java.util.List.copyOf(targets == null ? java.util.List.of() : targets);
            note = note == null ? "" : note;
        }
    }

    /** Answer to {@link SessionEffects.ApplyWalkViewEffect}: the view was validated and applied, or refused whole. */
    public record WalkViewApplied(long opId, long ticket, boolean ok, String reason) implements Result {
    }

    /** Answer to {@link SessionEffects.LightWalkTargetsEffect}: how many targets the overlay lit. */
    public record WalkTargetsLit(long opId, long ticket, int lit, String reason) implements Result {
    }

    /** Answer to {@link SessionEffects.ResolveWalkTargetsEffect} and {@link SessionEffects.EndWalkEffect}. */
    public record WalkAcknowledged(long opId, long ticket, String what) implements Result {
    }

    // ---- evidence bundle capture (convergence, 2026-09-28): the capture skill's steps, as session facts ----------

    /**
     * Someone asked for a bundle. The adapter carries what it OBSERVED of the log at the request, as facts, and decides
     * nothing: the node refuses or proceeds.
     *
     * @param request          the caller's id, answered in {@link telamin.fluxtion.audit.analyser.analyser.session.CaptureState}
     * @param path             where the bundle goes, already resolved inside the exchange directory
     * @param observedIdentity the read-through identity observed at this request, lower case, or null when none ran
     * @param freshness        the log file's freshness state ({@code unchanged-metadata}, {@code changed-on-disk}, …)
     * @param onePlainFile     whether the log is exactly one regular local file
     * @param windowRecords    for a window ({@code from}/{@code to}), how many records it selects in the open log, as
     *                         observed at this request; -1 when no window was asked for
     * @param replay           the replay file the author named (replay spec §4.1), or null for none
     * @param replayRecords    how many replay records the pairing read from it
     * @param serviceCalls     the open log's exported-service calls, which a replay does not carry
     * @param replayProblem    why the replay does not pair with the open log, in words, or null when it pairs
     * @param replaySha256     the digest of exactly the bytes that were paired
     */
    public record BundleCaptureRequested(long request, String path, String notes, Long from, Long to,
                                         String observedIdentity, String freshness, boolean onePlainFile,
                                         int windowRecords, String origin, String replay, int replayRecords,
                                         int serviceCalls, String replayProblem, String replaySha256) {
        /** A request with no replay. */
        public BundleCaptureRequested(long request, String path, String notes, Long from, Long to,
                                      String observedIdentity, String freshness, boolean onePlainFile,
                                      int windowRecords, String origin) {
            this(request, path, notes, from, to, observedIdentity, freshness, onePlainFile, windowRecords, origin,
                    null, 0, 0, null, null);
        }
    }

    public record FollowSet(long opId, long ticket, boolean on) implements Result {
    }

    public record CaptureStarted(long opId, long ticket) implements Result {
    }

    public record BundleDeleted(long opId, long ticket, boolean ok, String reason) implements Result {
    }

    /** The bundle was written, under the generation the capture was decided in. The node decides whether it stands. */
    public record BundleWritten(long ticket, long generation, String path, String identity, java.util.List<String> lines) {
        public BundleWritten {
            lines = java.util.List.copyOf(lines == null ? java.util.List.of() : lines);
        }
    }

    /** The file work failed; nothing was left behind (the writer deletes what it started). */
    public record BundleWriteFailed(long ticket, long generation, String reason) {
    }

    // ---- the onboard assistant: decided by the assistantLoop node, performed by the frame (OA-1) -------------------
    //
    // No event carries a credential, and none carries the words of a question or an answer: the adapter writes them to
    // the append-only AssistantTranscript and names them by id. The session audit record therefore holds what was
    // decided about a conversation, never its content.

    /**
     * The route a turn is sent by, captured when Send was pressed: which provider and model, whether a key exists (not
     * the key), and the configured budgets. Immutable, so a Settings change during a turn cannot reach it.
     */
    public record AssistantRoute(String provider, String model, String baseUrl, boolean hasKey, boolean actions,
                                 int maxRounds, int maxActionsPerReply, int maxActionsPerTurn) {
        public AssistantRoute {
            provider = provider == null || provider.isBlank() ? "anthropic" : provider.trim();
            model = model == null ? "" : model.trim();
            baseUrl = baseUrl == null ? "" : baseUrl.trim();
        }
    }

    /** Send pressed: the draft is transcript entry {@code draft}; {@code request} correlates the answer (0 = none). */
    public record AssistantSendRequested(long request, long draft, AssistantRoute route) {
    }

    /** Cancel pressed, or the application is exiting. */
    public record AssistantCancelRequested(String reason) {
    }

    /** New chat: invalidate any pending turn, then start an empty conversation. */
    public record AssistantNewChatRequested(String reason) {
    }

    /** Dock ({@code docked} true) or pop out the assistant. Presentation only. */
    public record AssistantHostRequested(boolean docked, String origin) {
    }

    /**
     * "Ask about this evidence" (§6.2): end the demonstration and open a fresh live thread. It never inherits the walk's
     * dialogue: the new conversation starts empty and nothing simulated is sent anywhere.
     */
    public record AssistantHandoffRequested(String origin) {
    }

    /** Answered at once by every assistant effect whose outcome arrives later. */
    public record AssistantEffectStarted(long opId, long ticket, String what) implements Result {
    }

    /** The host was moved (or not): answers {@code ShowAssistantHostEffect}. */
    public record AssistantHostShown(long opId, boolean docked, boolean ok, String reason) implements Result {
    }

    /** The first-turn content was composed as transcript entry {@code prompt} (hidden), for {@code ticket}. */
    public record AssistantContextPrepared(long ticket, long prompt) {
    }

    /** The context could not be assembled; nothing was sent. */
    public record AssistantContextFailed(long ticket, String reason) {
    }

    /**
     * The provider replied in {@code round}: the reply is transcript entry {@code reply}, and the {@code analyser-action}
     * blocks found in it are entries {@code actions}, in order. The node decides which of them run.
     */
    public record AssistantCompletionReceived(long ticket, int round, long reply, java.util.List<Long> actions) {
        public AssistantCompletionReceived {
            actions = java.util.List.copyOf(actions == null ? java.util.List.of() : actions);
        }
    }

    /** The provider request failed (a transport error, a timeout, an HTTP error); {@code reason} is already bounded. */
    public record AssistantCompletionFailed(long ticket, int round, String reason) {
    }

    /**
     * Action entry {@code action} was dispatched through the same dispatcher as the external bridge. {@code result} is the
     * transcript entry holding the ACTUAL structured result; {@code ok} is that result's own ok, never the model's claim.
     */
    public record AssistantActionFinished(long ticket, long action, String verb, boolean ok, long result) {
    }

    /** The ticket and action that caused a workspace fact, or null for a person, another client or a later poll. */
    public record AssistantActionOrigin(long ticket, long action) { }

    /** The accepted log has finished applying to the frame, including its source graph and reset view. */
    public record AssistantOpenApplied(long opId, AssistantActionOrigin assistantOrigin) { }

    /** An accepted load failed during its final frame apply; no assistant continuation may use its partial view. */
    public record AssistantOpenApplyFailed(long opId, AssistantActionOrigin assistantOrigin) { }
}
