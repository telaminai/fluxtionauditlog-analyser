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
                                       AssistantActionOrigin assistantOrigin, ProjectAudience audience,
                                       CreateRequest create) {
        /** Every kind but {@link TransitionKind#CREATE}: there is nothing observed to carry. */
        public OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source,
                                    AssistantActionOrigin assistantOrigin, ProjectAudience audience) {
            this(opId, profilePath, kind, source, assistantOrigin, audience, null);
        }
        public OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source,
                                    AssistantActionOrigin assistantOrigin) {
            this(opId, profilePath, kind, source, assistantOrigin, new ProjectAudience(OperationOrigin.MACHINE, false));
        }
        public OpenProjectRequested(long opId, String profilePath, TransitionKind kind, String source) {
            this(opId, profilePath, kind, source, null);
        }
    }

    // ---- M73: creating a project (spec-create-project.md §4). Plain strings only: the offer is OBSERVED by the
    // adapter and carried here, so the node can compute `selection - offer` without a ui type crossing into session/.

    /**
     * One source root the create offer observed.
     *
     * @param relative project-relative, forward slashes; the directory itself is {@code "."} — the identity a
     *                 selection names
     * @param absolute where it is, as the profile would store it
     * @param outside  it resolves (through a link) outside the project directory, so it can never be adopted
     */
    public record OfferedRoot(String relative, String absolute, boolean outside) { }

    /** One skill pointer the create offer observed; its identity is {@code path} (project-relative). */
    public record OfferedSkill(String path, String name, String description, boolean declared) { }

    /**
     * What discovery offered in the directory, in offer order.
     *
     * @param referenceGuide a {@code ReferenceSet.Outcome} name: CAN_CREATE, EXISTS or NOTHING_AGREED
     * @param projectKind    "spring", or null for a plain project
     * @param digest         sha256 over the offered strings in offer order — what a preview's {@code offerDigest} must equal
     */
    public record CreateOffer(java.util.List<OfferedRoot> sourceRoots, java.util.List<OfferedSkill> skills,
                              String referenceGuide, String projectKind, String digest) {
        public CreateOffer {
            sourceRoots = sourceRoots == null ? java.util.List.of() : java.util.List.copyOf(sourceRoots);
            skills = skills == null ? java.util.List.of() : java.util.List.copyOf(skills);
        }

        public static CreateOffer empty() {
            return new CreateOffer(java.util.List.of(), java.util.List.of(), "NOTHING_AGREED", null, null);
        }
    }

    /**
     * The filesystem as the adapter found it, just before the request — facts, never a decision.
     *
     * @param dir               the project directory (its real path when it exists)
     * @param absolute          the path given was absolute
     * @param directory         it is an existing directory
     * @param profileExists     {@code <dir>/.analyser/project.fluxtion-settings} exists, links NOT followed (a dangling
     *                          link counts as existing: writing through it is what a create must never do)
     * @param analyserProblem   null when {@code <dir>/.analyser} is absent or a real directory under {@code <dir>};
     *                          otherwise what is wrong with it (a link, or a file)
     * @param bundleWorkingCopy the directory is inside an evidence bundle's working copy
     * @param exchangeDir       the assistant exchange directory containing it, or null
     * @param enclosingProject  the nearest enclosing project's profile ({@code findNear(dir.getParent())}), or null
     * @param namedProfiles     {@code project.<x>.fluxtion-settings} files already in {@code .analyser/}: not the
     *                          canonical file, so not "existing", but named in the echo
     * @param offer             what discovery offered, observed now
     * @param createAllowed     the RAW {@code assistant.createProjects} switch; the node combines it with the origin
     */
    public record CreateFacts(String dir, boolean absolute, boolean directory, boolean profileExists,
                              String analyserProblem, boolean bundleWorkingCopy, String exchangeDir,
                              String enclosingProject, java.util.List<String> namedProfiles, CreateOffer offer,
                              boolean createAllowed) {
        public CreateFacts {
            namedProfiles = namedProfiles == null ? java.util.List.of() : java.util.List.copyOf(namedProfiles);
            offer = offer == null ? CreateOffer.empty() : offer;
        }
    }

    /** What the caller chose, by identity: relative root strings and skill paths. Nothing is chosen by default. */
    public record CreateSelection(java.util.List<String> sourceRoots, java.util.List<String> skills,
                                  boolean referenceGuide, String offerDigest) {
        public CreateSelection {
            sourceRoots = sourceRoots == null ? java.util.List.of() : java.util.List.copyOf(sourceRoots);
            skills = skills == null ? java.util.List.of() : java.util.List.copyOf(skills);
        }

        public static CreateSelection empty() {
            return new CreateSelection(java.util.List.of(), java.util.List.of(), false, null);
        }
    }

    /** A create's payload on {@link OpenProjectRequested}: what was observed, and what was chosen from it. */
    public record CreateRequest(CreateFacts facts, CreateSelection selection) {
        public CreateRequest {
            selection = selection == null ? CreateSelection.empty() : selection;
        }
    }

    /**
     * What {@code sessionBoundary} approved for adoption, in OFFER order (G3): the performer adopts exactly this and
     * never re-discovers (D-73.4).
     *
     * @param sourceRoots absolute root strings
     */
    public record CreateApproved(String root, java.util.List<String> sourceRoots, java.util.List<OfferedSkill> skills,
                                 boolean referenceGuide, String projectKind) {
        public CreateApproved {
            sourceRoots = sourceRoots == null ? java.util.List.of() : java.util.List.copyOf(sourceRoots);
            skills = skills == null ? java.util.List.of() : java.util.List.copyOf(skills);
        }
    }

    public enum OperationOrigin { PERSON, MACHINE }
    public record ProjectAudience(OperationOrigin origin, boolean offersAllowed) { }
    /** A restored project became visible; it did not cross ProfileApplied in this process. */
    public record ProjectReopenRequested(long opId, String profilePath, ProjectAudience audience) { }
    /** Filesystem/display observations, never a decision to show a modal. */
    public record ProjectReopenReady(long opId, String profilePath, String label,
                                     telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen candidates,
                                     boolean occupied) implements Result { }
    public record ProjectReopenPresented(long opId, boolean shown) implements Result { }

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
                            boolean followable, int nodeLogsWithheld, long openedAtMillis) implements Result {
        public LogOpened {
            loggedNodeIds = loggedNodeIds == null ? java.util.Set.of() : java.util.Set.copyOf(loggedNodeIds);
        }

        /**
         * Final review #12: {@code openedAtMillis} — when the adapter opened it (epoch millis; 0 unknown) — is what the
         * composer's binding chip says in place of the log generation ("this log · opened HH:MM").
         */
        public LogOpened(long opId, String logPath, String provenance, java.util.Set<String> loggedNodeIds,
                         int sampled, int total, String mostVerboseLevel, String provenanceSource, boolean followable,
                         int nodeLogsWithheld) {
            this(opId, logPath, provenance, loggedNodeIds, sampled, total, mostVerboseLevel, provenanceSource, followable,
                    nodeLogsWithheld, 0);
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
                                int unknownKeys, String reason, BundlePlan bundlePlan, String contentDigest) implements Result {
        public ProfileLoaded(long opId, String profilePath, boolean ok, String name,
                             int unknownKeys, String reason, BundlePlan bundlePlan) {
            this(opId, profilePath, ok, name, unknownKeys, reason, bundlePlan, null);
        }
        public ProfileLoaded(long opId, String profilePath, boolean ok, String name,
                             int unknownKeys, String reason) {
            this(opId, profilePath, ok, name, unknownKeys, reason, null);
        }
    }

    /**
     * The source roots now in force after a non-transition config change. This may be unrelated
     * to source: the node compares it with restored visible roots before deciding whether an edit occurred.
     *
     * <p>Deliberately NOT reported while a transition is rendering. A close puts the person's own settings
     * back before the render runs, so reporting then would state somebody else's roots as this session's —
     * which is exactly how a bundle came to be anchored to 19 unrelated repositories (2026-09-30). The
     * adapter reports what it sees; the node decides which visible roots were deliberately added or removed.
     */
    public record SourceRootsObserved(java.util.List<String> roots) {
        public SourceRootsObserved {
            roots = java.util.List.copyOf(roots == null ? java.util.List.of() : roots);
        }
    }

    /** Restoration is not an edit: unavailable remembered roots remain owned by this bundle. */
    public record BundleRootsRestored(long opId, String bundleSource, java.util.List<String> remembered,
                                      java.util.List<String> visible) implements Result {
        public BundleRootsRestored {
            remembered = java.util.List.copyOf(remembered);
            visible = java.util.List.copyOf(visible);
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
                             String processor, String profileDigest) {
        public BundlePlan(String profilePath, String graphPath, String logPath, String identity,
                          String workingCopy, String limits, String notes, String source, String processor) {
            this(profilePath, graphPath, logPath, identity, workingCopy, limits, notes, source, processor, null);
        }
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
    public record ProfileApplied(long opId, String profilePath, String name, java.util.List<String> droppedBindings)
            implements Result {
        /**
         * {@code droppedBindings} (M74 P2b §6; review P2 N-b): the walk bindings the load dropped, in the analyser's words
         * with paths removed — sessionBoundary says them.
         */
        public ProfileApplied {
            droppedBindings = droppedBindings == null ? java.util.List.of() : java.util.List.copyOf(droppedBindings);
        }
        public ProfileApplied(long opId, String profilePath, String name) {
            this(opId, profilePath, name, java.util.List.of());
        }
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

    /** A native tab change; -1 means an external action, otherwise the effect's walk ticket. */
    public record WalkViewChanged(long ownerTicket) { }

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

    /**
     * Send pressed: the draft is transcript entry {@code draft}; {@code request} correlates the answer (0 = none).
     * {@code draftBindings} (M74 P2, §8.2; review S2): every {@link AssistantDraftComposed} draft the composer still holds,
     * possibly none — the node compares each binding with the open log, refuses if any is stale or unknown, and a composed
     * draft never carries the live selection's record text.
     */
    public record AssistantSendRequested(long request, long draft, AssistantRoute route, java.util.List<Long> draftBindings) {
        public AssistantSendRequested {
            draftBindings = bindings(draftBindings);
        }
        public AssistantSendRequested(long request, long draft, AssistantRoute route, long draftBinding) {
            this(request, draft, route, draftBinding == 0 ? java.util.List.of() : java.util.List.of(draftBinding));
        }
        public AssistantSendRequested(long request, long draft, AssistantRoute route) {
            this(request, draft, route, java.util.List.of());
        }
    }

    /** The composer's bindings as a fact: no nulls, no zero ids, immutable. */
    static java.util.List<Long> bindings(java.util.List<Long> in) {
        if (in == null) return java.util.List.of();
        return in.stream().filter(java.util.Objects::nonNull).filter(b -> b != 0).toList();
    }

    /** Cancel pressed, or the application is exiting. */
    public record AssistantCancelRequested(String reason) {
    }

    /** New chat: invalidate any pending turn, then start an empty conversation. */
    public record AssistantNewChatRequested(String reason) {
    }

    /**
     * Robot re-walk #1 (2026-10-18): pointing at something on screen took the canvas away from the DOCKED assistant's
     * tab, to tab {@code shown} ("Topology") — an observation of what the reveal did, posted by the frame. The answer the
     * person was reading is now behind another tab; {@code assistantLoop} decides what the canvas says about it.
     */
    public record CanvasLeftAssistant(String shown) {
    }

    /** The canvas shows the docked assistant's tab again (the person chose it, or "↩ Back to the answer" did). */
    public record AssistantTabShown(String origin) {
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

    // ---- M74: the local agent (Claude Code), decided by the localAgentSession node, performed by LocalAgentAdapter ----
    //
    // Facts carry ids, a process generation, a turn ticket and enums: never the words of a turn, account data or a path.
    // Words live in the bounded LocalAgentTranscript and are named here by reference (spec §4.1).

    /** The person chose an assistant mode on the Assistant tab. */
    public record AssistantModeChosen(AssistantMode mode, String origin) {
    }

    // ---- M74 UX (owner, 2026-10-17): the assistant profile, decided by the assistantProfile node --------------------
    //
    // Enums and ids only: the overlay's details (an API key, a model, a mode) stay in the overlay and are written to this
    // machine's settings by ApplyAssistantProfileEffect's performer; no fact carries them.

    /**
     * The profile in force on this machine, as the frame observes its settings — at session start and after every
     * settings change (the overlay's Apply, Settings ▸ Assistant). {@link AssistantProfile#NONE}: nothing is chosen.
     */
    public record AssistantProfileObserved(AssistantProfile profile) {
    }

    /** The person opened the profile choices (the assistant header's button). */
    public record AssistantProfilesRequested(String origin) {
    }

    /** The person closed the profile choices without applying (Close, Escape). */
    public record AssistantProfilesClosed(String origin) {
    }

    /**
     * M77 (spec-ai-setup-wizard.md §8.3, review R1-14): which surface asked for a profile — the Set up AI wizard, the
     * Assistant ▾ quick switch, Settings, or the profile cards (kept until M77-4 retires them). The Full guard reads the
     * surface that asked: the wizard's {@code open} for {@link #SETUP}, the cards' for {@link #CARDS}, nothing for
     * {@link #MENU}, whose confirmation is the whole interaction.
     */
    public enum ProfileOrigin { SETUP, MENU, SETTINGS, CARDS }

    /** The person pressed Apply on a card: the node decides whether it may be applied now. */
    public record AssistantProfileChosen(long request, AssistantProfile profile, ProfileOrigin origin) {
        public AssistantProfileChosen {
            origin = origin == null ? ProfileOrigin.CARDS : origin;
        }

        /** The cards' Apply (the shape before M77). */
        public AssistantProfileChosen(long request, AssistantProfile profile) {
            this(request, profile, ProfileOrigin.CARDS);
        }
    }

    /**
     * Review PR151 F4 (owner, 2026-10-18): the person's answer to {@code ConfirmFullProfileEffect{request}} — the full
     * warning, as Settings ▸ Assistant shows it; {@code confirmed} only on an explicit press of <i>Turn on full Claude
     * Code</i> (Cancel, Escape and closing the window are not).
     */
    public record AssistantFullProfileAnswered(long request, boolean confirmed) {
    }

    /** Answers {@code ApplyAssistantProfileEffect}: the settings were written ({@code ok}) and the consent re-reported. */
    public record AssistantProfileApplied(long opId, long request, AssistantProfile profile, boolean ok, String reason,
                                          ProfileOrigin origin) implements Result {
        public AssistantProfileApplied {
            origin = origin == null ? ProfileOrigin.CARDS : origin;
        }

        /** The shape before M77: an Apply from the cards. */
        public AssistantProfileApplied(long opId, long request, AssistantProfile profile, boolean ok, String reason) {
            this(opId, request, profile, ok, reason, ProfileOrigin.CARDS);
        }
    }

    /** Answers {@code ShowAssistantProfilesEffect}: the assistant was made visible for the choices. */
    public record AssistantProfilesShown(long opId) implements Result {
    }

    // ---- M77 (spec-ai-setup-wizard.md §8.3): Set up AI, decided by the aiSetup node ---------------------------------
    //
    // Enums, ids and times only: never a path, a key, raw CLI output, an e-mail or an organisation (S7).

    /** The person asked for Set up AI (a menu item, the start page's link, Settings' button). */
    public record AiSetupRequested(AiSetupState.Origin origin) {
    }

    /**
     * Reported once at start-up with the other start-up facts: the two machine-tier keys of M77 and whether this process
     * was started with {@code --rest} (an agent drives it; nobody at the screen — review R1-16 a, b).
     */
    public record AiSetupStartupObserved(boolean dismissed, AiSetupState.Client client, boolean restLaunch) {
    }

    /** Screen 1 (or the first-run block): the person picked a choice. Picking applies nothing. */
    public record AiSetupChoiceMade(AiSetupState.Choice choice) {
    }

    /** Screen 2a: the Safe / Full sub-choice, and Full's mode. */
    public record AiSetupPostureChosen(AiSetupState.Posture posture, String fullMode) {
    }

    /** Screen 2b: which external client (M77-3). */
    public record AiSetupClientChosen(AiSetupState.Client client) {
    }

    /** Back, pressed on {@code step}; a press on a screen that is no longer current is stale. */
    public record AiSetupBack(AiSetupState.Step step) {
    }

    /**
     * Continue (or Finish) pressed on {@code step}. {@code keyEntered}: on Other options, whether the key field holds
     * text — never the text; the details reach the settings through the Apply performer, as the cards' do.
     */
    public record AiSetupContinue(AiSetupState.Step step, boolean keyEntered) {
        public AiSetupContinue(AiSetupState.Step step) {
            this(step, false);
        }
    }

    /** Cancel, Escape or the window's close, on any screen before the Summary (or Done on it). */
    public record AiSetupCancelled(String origin) {
    }

    /** The first-run block's <i>Not now</i>. */
    public record AiSetupFirstRunDismissed() {
    }

    /** The Summary's suggested first action was pressed (§6.4). */
    public record AiSetupSuggestedTaken(AiSetupState.Suggested suggested) {
    }

    /** <i>Check again</i> on one check. */
    public record AiSetupCheckAgain(AiSetupState.CheckKind check) {
    }

    /** What the resolver says about Codex (file existence only; nothing run). Claude's is in StartPageAvailability. */
    public record AiClientsObserved(boolean codexFound) {
    }

    /** How {@code claude --version} went. */
    public enum ProbeOutcome { FOUND, NOT_FOUND, DID_NOT_RUN, TIMED_OUT }

    /**
     * {@code claude --version} for probe {@code probe}: the outcome, the version's digits, and where {@code claude} is,
     * by reference into the frame's store (never the path).
     */
    public record ClaudeObserved(long probe, ProbeOutcome outcome, String version, long pathRef) {
    }

    /** How {@code claude auth status --json} went: the JSON was read, or it could not be told. */
    public enum AuthOutcome { READ, UNREADABLE, TIMED_OUT, DID_NOT_RUN }

    /**
     * {@code claude auth status --json} for probe {@code probe}, parsed at the boundary (§8.6): ONLY {@code loggedIn},
     * {@code authMethod}, {@code subscriptionType} and {@code apiKeySource}, each as an enum. The rest — an e-mail, an
     * organisation — never leaves the parser.
     */
    public record ClaudeAuthObserved(long probe, AuthOutcome outcome, boolean loggedIn, AiSetupState.Method method,
                                     AiSetupState.Plan plan, AiSetupState.KeySource keySource) {
    }

    /** Answered at once by each of aiSetup's effects whose outcome arrives later (or is the window itself). */
    public record AiSetupEffectStarted(long opId, String what) implements Result {
    }

    /**
     * What the person's machine says about starting Claude Code now, observed by the frame when Start or Send is
     * asked for. The node combines these; the frame decides none of them.
     *
     * @param allowed             the raw {@code assistant.claudeCode} switch (machine tier)
     * @param folderConfirmed     the working directory is confirmed on this machine for the DEFAULT posture (or is the
     *                            analyser's own scratch)
     * @param bundleWorkingCopy   the working directory lies in an evidence-bundle working copy
     * @param postureRef          a digest of the posture (kind, mode, directory, note, pre-allow list) — an id
     * @param budgetMicros        the session spend limit, in millionths of a US dollar; 0 when none is set (owner,
     *                            2026-10-17: optional, off by default)
     * @param folder              whether the working directory may be used at all (review M4, S10)
     * @param full                the raw {@code assistant.claudeCode.full} switch (machine tier, P1b)
     * @param fullMode            the full posture's mode, already normalised by {@code ConfigStore} (never bypass, never
     *                            dontAsk)
     * @param fullFolderConfirmed a FULL-posture trust record exists for the folder AND its digest equals the digest of
     *                            the folder's files Start would load (OD-74.12, review H6); the scratch folder counts,
     *                            holding nothing to run
     * @param model               the configured Claude Code model, normalised (blank means the default); null when not
     *                            observed — owner, 2026-10-18: a change of it while a session runs is offered as a
     *                            restart, where it used to take effect silently only at the next Start
     * @param folderRef           a digest of the real working folder (never the path); null when not observed — tells a
     *                            folder change apart from a change of the posture's kind or mode
     */
    public record LocalAgentConsent(boolean allowed, boolean folderConfirmed, boolean bundleWorkingCopy,
                                    String postureRef, long budgetMicros, FolderVerdict folder, boolean full,
                                    LocalAgentPosture.FullMode fullMode, boolean fullFolderConfirmed, String model,
                                    String folderRef) {
        /** The shape before the restart offer (owner, 2026-10-18): neither the model nor the folder observed. */
        public LocalAgentConsent(boolean allowed, boolean folderConfirmed, boolean bundleWorkingCopy,
                                 String postureRef, long budgetMicros, FolderVerdict folder, boolean full,
                                 LocalAgentPosture.FullMode fullMode, boolean fullFolderConfirmed) {
            this(allowed, folderConfirmed, bundleWorkingCopy, postureRef, budgetMicros, folder, full, fullMode,
                    fullFolderConfirmed, null, null);
        }

        /** The default posture, with a folder verdict. */
        public LocalAgentConsent(boolean allowed, boolean folderConfirmed, boolean bundleWorkingCopy,
                                 String postureRef, long budgetMicros, FolderVerdict folder) {
            this(allowed, folderConfirmed, bundleWorkingCopy, postureRef, budgetMicros, folder, false, null, false);
        }

        /** A folder that has been checked and may be used, in the default posture. */
        public LocalAgentConsent(boolean allowed, boolean folderConfirmed, boolean bundleWorkingCopy,
                                 String postureRef, long budgetMicros) {
            this(allowed, folderConfirmed, bundleWorkingCopy, postureRef, budgetMicros, FolderVerdict.OK);
        }

        public LocalAgentConsent {
            folder = folder == null ? FolderVerdict.UNAVAILABLE : folder;
            fullMode = fullMode == null ? LocalAgentPosture.FullMode.DEFAULT : fullMode;
        }

        /** The posture a start under this consent runs in. */
        public LocalAgentPosture posture() {
            return full ? LocalAgentPosture.full(fullMode) : LocalAgentPosture.ANALYSER_ASSISTANT;
        }

        /** Whether the folder is confirmed for the posture in force (the two trusts are separate, OD-74.12). */
        public boolean confirmedForPosture() {
            return full ? fullFolderConfirmed : folderConfirmed;
        }
    }

    /**
     * Whether a working directory may be given to the local agent at all, observed by the frame (review M4, S10).
     * {@code EXPOSES_SECRETS}: it is, or contains, the home directory, {@code ~/.fluxtion-analyser} (the endpoint files
     * with the REST token and the hosted credential) or {@code ~/.claude}, or lies inside one of the last two — Read and
     * Grep confined to it would still reach credentials. {@code UNAVAILABLE}: there is no folder (the private scratch
     * folder could not be made).
     */
    public enum FolderVerdict { OK, EXPOSES_SECRETS, UNAVAILABLE }

    /** Start pressed: the one way a process is started (warm start was cut by the owner, 2026-10-17). */
    public record LocalAgentStartRequested(long request, LocalAgentConsent consent) {
    }

    /** How a draft reads as a command, classified by the provider's table (§5.4); the node decides what follows. */
    public enum SlashClass {
        /** Not a command: ordinary words. */
        TEXT,
        /** A classified command that is sent as a turn. */
        PASS,
        /** {@code /clear} and its aliases: never sent; New chat is offered instead. */
        NEW_CHAT,
        /** A classified command that would change the posture, the session's identity or act outside it. */
        BLOCK,
        /** A command name the table does not classify: refused, as an unknown tool would be. */
        UNCLASSIFIED,
        /** A terminal shortcut ({@code !} or {@code #} first): refused. An {@code @path} mention is not one (owner, 2026-10-16). */
        SHORTCUT
    }

    /**
     * Send pressed under the local agent: the draft is transcript entry {@code draft}. {@code command} is the command's
     * canonical name when the CLI or the table knows it, else null — CLI vocabulary, never the person's words.
     */
    public record LocalAgentSendRequested(long request, long draft, SlashClass slash, String command,
                                          LocalAgentConsent consent, java.util.List<Long> draftBindings) {
        /** M74 P2 (§8.2; review S2): {@code draftBindings} is every composed draft the composer still holds, possibly none. */
        public LocalAgentSendRequested {
            draftBindings = bindings(draftBindings);
        }
        public LocalAgentSendRequested(long request, long draft, SlashClass slash, String command,
                                       LocalAgentConsent consent, long draftBinding) {
            this(request, draft, slash, command, consent,
                    draftBinding == 0 ? java.util.List.of() : java.util.List.of(draftBinding));
        }
        public LocalAgentSendRequested(long request, long draft, SlashClass slash, String command,
                                       LocalAgentConsent consent) {
            this(request, draft, slash, command, consent, java.util.List.of());
        }
    }

    /**
     * Settings were applied by the person (review finding 4): what the machine now says about starting Claude Code.
     * The node decides whether the running session still has the consent and the posture it was started under.
     */
    public record LocalAgentConsentChanged(LocalAgentConsent consent) {
    }

    /** Stop pressed: interrupt the one open turn. */
    public record LocalAgentStopRequested(String origin) {
    }

    /**
     * Owner, 2026-10-18: the person's answer to the restart offer {@code offer} ({@code LocalAgentState.Restart}) —
     * {@code restart} for <i>Restart now</i>, false for <i>Later</i>. {@code request} is the frame's id for the Start a
     * restart may need (a folder confirmation answers it); {@code consent} is what the machine says NOW. The node decides
     * whether the offer still stands and what Yes does.
     */
    public record LocalAgentRestartAnswered(long request, long offer, boolean restart, LocalAgentConsent consent) {
    }

    /** Owner, 2026-10-18: the person clicked the "settings changed — restart to apply" chip: show the offer again. */
    public record LocalAgentRestartOfferRequested(String origin) {
    }

    /** New chat under the local agent: end any process, and the next Start begins a new conversation id. */
    public record LocalAgentNewChatRequested(String origin) {
    }

    /**
     * Review M8: the person answered the folder confirmation the node asked for with
     * {@code ConfirmLocalAgentFolderEffect{request}}. {@code consent} is what the machine says NOW (observed after the
     * answer); {@code contentUnchanged} says whether the files the full-posture dialog named still read as they did
     * when it was shown (always true for the default posture). The node decides whether the answer still applies, and
     * asks for the trust record and the start.
     */
    public record LocalAgentFolderAnswered(long request, boolean yes, boolean contentUnchanged, LocalAgentConsent consent) {
    }

    /**
     * Review H4: the answer to {@code ServeHostedBridgeEffect} — whether a listener the hosted bridge can reach is up for
     * {@code generation} ({@code hostedOnly}: it serves only hosted credentials and published no well-known file).
     */
    public record LocalAgentBridgeServed(long opId, long generation, boolean listening, boolean hostedOnly, String endpoint)
            implements Result {
        /** Review B5: {@code endpoint} — the loopback address it listens on (never a token), or null. */
        public LocalAgentBridgeServed(long opId, long generation, boolean listening, boolean hostedOnly) {
            this(opId, generation, listening, hostedOnly, null);
        }
    }

    /**
     * Review B5 (2026-10-17): the person's REST switch (Settings ▸ Assistant ▸ external MCP / REST access), as the frame
     * observes it at start-up and after a Settings change; actionListener decides what it means for the listener.
     */
    public record RestSwitchObserved(boolean on) {
    }

    /**
     * Review B2 (2026-10-17): the person's answer to "Close external access while this session runs", for the Claude
     * Code session of {@code generation}; actionListener decides.
     */
    public record ExternalAccessChosen(long generation, boolean closeWhileSession) {
    }

    /**
     * Review B5: what a {@link SessionEffects.ConfigureActionListenerEffect} did — up or not, open to external clients or
     * not, the loopback {@code endpoint} (never a token), or why it could not start ({@code failure}, the analyser's
     * words with paths removed).
     */
    public record ActionListenerConfigured(long opId, boolean listening, boolean external, String endpoint, String failure)
            implements Result {
    }

    /**
     * Review M6: the hosted bridge presented a live credential of {@code generation}; call {@code call} waits for the
     * session's verdict, decided on the session's thread in its order — never from a snapshot on the HTTP thread.
     */
    public record LocalAgentCallAdmissionRequested(long call, long generation, String verb) {
        /**
         * Scorecard R1: the verb the call names, as the hosted vocabulary knows it ({@code McpTools.hostedVerbFor}), or
         * null when it names none — never the call's input. The turn it belongs to is the node's (it records the open
         * ticket beside its verdict).
         */
        public LocalAgentCallAdmissionRequested(long call, long generation) {
            this(call, generation, null);
        }
    }

    /**
     * Scorecard R1: the action server refused a hosted call itself, before asking the session — {@code graph {delete}},
     * a verb that is neither pre-allowed nor askable, a malformed body, an unknown or revoked credential. {@code verb} is
     * the hosted vocabulary's or null; {@code reason} is a code: {@code GRAPH_DELETE}, {@code NOT_LISTED},
     * {@code BAD_CREDENTIAL}.
     */
    public record HostedCallRefusedByServer(long generation, String verb, String reason) {
    }

    /** Answered at once by every local-agent effect whose outcome arrives later. */
    public record LocalAgentEffectStarted(long opId, long generation, String what) implements Result {
    }

    /** Why a spawn did not produce a process. */
    public enum SpawnFailure { NONE, NOT_FOUND, LAUNCHER_UNAVAILABLE, LOCKED, ENDPOINT_FAILED, LAUNCH_FAILED,
        /** This analyser's own previous process for the conversation had not finished stopping (review M3). */
        PREVIOUS_STILL_STOPPING,
        /** The Claude Code model setting is not a model name (review S7). */
        INVALID_MODEL,
        /** The working directory at spawn is not the one the posture was decided for (review M7). */
        FOLDER_CHANGED,
        /** Review B7: in the full posture, the folder's full trust digest at spawn is not one the person confirmed. */
        TRUST_CHANGED,
        /**
         * Authoring walk (2026-10-18): in the full posture, no record names the folder at all — it was never trusted, so
         * nothing "changed"; the next Start asks about it.
         */
        TRUST_NEEDED }

    /** The process for {@code generation} started ({@code failure} NONE), or did not; {@code detailRef} names the words. */
    public record LocalAgentSpawned(long generation, SpawnFailure failure, long detailRef) {
    }

    /**
     * The {@code initialize} control request answered. Only what the handshake needs: whether the CLI is logged in
     * (decided from {@code tokenSource}, {@code subscriptionType} and {@code apiKeySource}, read at the parse boundary
     * — the account's e-mail and organisation are never read), the permission mode it reports, and the header's
     * reference.
     */
    public record LocalAgentInitialized(long generation, boolean loggedIn, String permissionMode, long headerRef,
                                        Billing billing) {
        public LocalAgentInitialized {
            billing = billing == null ? Billing.UNKNOWN : billing;
        }

        public LocalAgentInitialized(long generation, boolean loggedIn, String permissionMode, long headerRef) {
            this(generation, loggedIn, permissionMode, headerRef, Billing.UNKNOWN);
        }
    }

    /**
     * How a local-agent session is paid for, as an enum read from {@code initialize.account} at the parse boundary
     * ({@code subscriptionType} present: a Claude plan; {@code apiKeySource}: an API key). Never the source's or the
     * plan's value: those are header words in the store. It decides only whether a dollar figure means anything (owner,
     * 2026-10-17).
     */
    public enum Billing { SUBSCRIPTION, API_KEY, UNKNOWN }

    /**
     * One {@code mcp_status} answer. {@code servers} are the configured server names; {@code hostedStatus} is the
     * hosted bridge's status ({@code connected}, {@code pending}, {@code failed}, or null when it is absent);
     * {@code hostedNonce} is what follows {@code +bind.} in its {@code serverInfo.version}, or null when there is no
     * {@code +bind.} — an older bridge. The nonce arrives as {@code LocalAgentPosture.digest}, so the record never holds it.
     */
    public record LocalAgentStatusObserved(long generation, long elapsedMs, java.util.List<String> servers,
                                           String hostedStatus, String hostedNonceDigest) {
        public LocalAgentStatusObserved {
            servers = java.util.List.copyOf(servers == null ? java.util.List.of() : servers);
        }
    }

    /** 35 s have passed since the spawn of {@code generation}; the node decides whether that ends it. */
    public record LocalAgentStartDeadline(long generation) {
    }

    /**
     * A turn's {@code system/init}: what the CLI says the session is running with. {@code servers} are
     * {@code name:status}; {@code sessionDigest} is {@code LocalAgentPosture.digest} of the session id the CLI reports,
     * compared with the node's own: the id itself never enters the record.
     */
    public record LocalAgentInitObserved(long generation, long ticket, String sessionDigest, String permissionMode,
                                         java.util.List<String> tools, java.util.List<String> servers,
                                         java.util.List<String> capabilities, long headerRef,
                                         java.util.List<String> skills, java.util.List<String> plugins) {
        /**
         * Scorecard R7: {@code skills} and {@code plugins} are the NAMES the turn's {@code system/init} reports (a plugin's
         * name only, never its path), so the record says what loaded — the person's own configuration, as the server
         * names already are; never their contents.
         */
        public LocalAgentInitObserved {
            tools = java.util.List.copyOf(tools == null ? java.util.List.of() : tools);
            servers = java.util.List.copyOf(servers == null ? java.util.List.of() : servers);
            capabilities = java.util.List.copyOf(capabilities == null ? java.util.List.of() : capabilities);
            skills = java.util.List.copyOf(skills == null ? java.util.List.of() : skills);
            plugins = java.util.List.copyOf(plugins == null ? java.util.List.of() : plugins);
        }

        public LocalAgentInitObserved(long generation, long ticket, String sessionDigest, String permissionMode,
                                      java.util.List<String> tools, java.util.List<String> servers,
                                      java.util.List<String> capabilities, long headerRef) {
            this(generation, ticket, sessionDigest, permissionMode, tools, servers, capabilities, headerRef, null, null);
        }
    }

    /** The CLI's lifecycle for the turn's own uuid reached {@code started}. */
    public record LocalAgentTurnStarted(long generation, long ticket) {
    }

    /** The answer text of {@code ticket} is transcript entry {@code textRef}, now at {@code revision} (coalesced). */
    public record LocalAgentTextUpdated(long generation, long ticket, long textRef, long revision) {
    }

    /** What a turn's end was. */
    public enum TurnOutcome { COMPLETED, CANCELLED, ERROR, BUDGET, PROCESS_EXIT }

    /** The first end signal of {@code ticket}: lifecycle completed/cancelled, its {@code result}, or process exit. */
    public record LocalAgentTurnEnded(long generation, long ticket, TurnOutcome outcome) {
    }

    /** A {@code result}'s {@code total_cost_usd}, a running total for the SESSION (it survives resume), in micro-USD. */
    public record LocalAgentCostObserved(long generation, long totalCostMicros, long ticket, long durationMs) {
        /** No turn named: only the running total. */
        public LocalAgentCostObserved(long generation, long totalCostMicros) {
            this(generation, totalCostMicros, 0, -1);
        }
    }

    /** Something the stream said that the person should see as a status line. */
    public enum AgentNote { COMPACTED, RATE_LIMITED, AUTH_FAILED, PERMISSION_DENIED,
        /**
         * A request the mode would ASK the person about, denied because nobody can be asked: {@code --permission-prompts
         * none} (P1b, P2) reports it as {@code decision_reason_type:"asyncAgent"} (PB-1).
         */
        APPROVAL_UNAVAILABLE }

    /** {@code note} happened in {@code ticket}; {@code epochSeconds} is a rate limit's reset time, else 0. */
    public record LocalAgentNoted(long generation, long ticket, AgentNote note, long epochSeconds, String tool) {
        /**
         * Scorecard R2: {@code tool} names the tool a denial was about from a fixed vocabulary ({@code ToolVocabulary.word}:
         * {@code Read}, {@code Bash}, {@code analyser_report}, {@code other-mcp}, …), or null for a note about none.
         */
        public LocalAgentNoted(long generation, long ticket, AgentNote note, long epochSeconds) {
            this(generation, ticket, note, epochSeconds, null);
        }
    }

    /** The CLI's hook vocabulary, as {@code system/hook_*} names it; anything else is {@link #OTHER}. */
    public enum HookEvent { SessionStart, SessionEnd, UserPromptSubmit, PreToolUse, PostToolUse, Notification, Stop,
        SubagentStop, PreCompact, OTHER;

        /** The event a {@code hook_event} name reports, never the name itself when it is not in the vocabulary. */
        public static HookEvent of(String name) {
            if (name != null) for (HookEvent e : values()) if (e != OTHER && e.name().equals(name)) return e;
            return OTHER;
        }
    }

    /** A hook's progress: {@code hook_started}, or {@code hook_response} with {@code outcome} success or not. */
    public enum HookOutcome { STARTED, OK, FAILED }

    /**
     * A hook ran (P1b §8.1). Only its event and outcome: the hook's output, stdout and stderr are never read at the
     * parse boundary (PB hook shapes), so they cannot reach a fact, the store or the snapshot. {@code ticket} is the
     * turn it ran in, 0 for {@code SessionStart} at spawn.
     */
    public record LocalAgentHookObserved(long generation, long ticket, HookEvent event, HookOutcome outcome) {
    }

    /**
     * {@code system/status} reported {@code permissionMode} in the middle of a session (review finding 2): a mode change
     * between turns' {@code system/init}s. The node checks it against the posture and kills at once on a mismatch.
     */
    public record LocalAgentModeObserved(long generation, long ticket, String permissionMode) {
    }

    /** The interrupt for {@code ticket} was acknowledged by a {@code control_response}. */
    public record LocalAgentInterruptAcked(long generation, long ticket) {
    }

    /** Which Stop deadline passed: no acknowledgement in 5 s, or no end in 15 s. */
    public enum StopDeadline { ACK, END }

    /** A Stop deadline for {@code ticket} passed; the node decides whether the process tree is killed. */
    public record LocalAgentStopDeadline(long generation, long ticket, StopDeadline which) {
    }

    /** The process of {@code generation} exited with {@code code}; {@code stderrRef} names its redacted last line. */
    public record LocalAgentExited(long generation, int code, long stderrRef) {
    }

    // ---- M74 P2: drafts from the Ask catalogue and the start page (spec §5.5, §5.8, §8.2) -----------------------------
    //
    // Words never travel in these facts: a draft is a transcript reference, a configured source a store reference and a
    // words-free shape, a flow's result references. Each is recorded in the session audit record as it is.

    /**
     * A draft was composed for Claude Code (the pop-up's Yes, a journey handoff, a start-page entry): transcript entry
     * {@code draftRef} of the local agent's store. {@code questionId} is an {@code AskCatalogue} id, or {@code JOURNEY},
     * {@code START_PAGE}, {@code FREE_TEXT}, {@code CONFIGURED}. {@code logIdentity} (a digest) and {@code logGeneration}
     * bind it to the log it names, null and 0 for an unbound draft. {@code consent} is what the machine says now, so the
     * node arms Start only when Start would be accepted (r8 F2).
     */
    public record LocalAgentDraftComposed(long draftRef, String questionId, String logIdentity, long logGeneration,
                                          LocalAgentConsent consent, String origin) {
    }

    /** The same for the in-app assistant: entry {@code draftRef} of the in-app transcript. */
    public record AssistantDraftComposed(long draftRef, String questionId, String logIdentity, long logGeneration,
                                         String origin) {
    }

    /**
     * Robot walkthrough #1 (2026-10-18): a question was composed (an Ask from a record, a chart, the topology) while NO
     * assistant is chosen — reported instead of a draft for the assistant the mode silently defaults to. Its words are in
     * the frame's store under {@code draftRef}, never here. {@code assistantProfile} holds it, unbound, and asks for it to
     * be placed for the assistant the person applies; the node in force then binds it.
     */
    public record AssistantDraftPending(long draftRef, String questionId, String origin) {
    }

    /**
     * Robot walkthrough #1: the person pressed "Ask again for Claude Code" on a refusal that the composer holds a question
     * drafted for the other assistant. {@code draftBindings} is every composed draft the composer holds.
     */
    public record LocalAgentAskAgainRequested(long request, java.util.List<Long> draftBindings) {
        public LocalAgentAskAgainRequested {
            draftBindings = java.util.List.copyOf(draftBindings == null ? java.util.List.of() : draftBindings);
        }
    }

    /** A prime effect was performed: the draft is in the composer (or {@code ok} false and why). */
    public record DraftPlaced(long opId, long draftRef, boolean ok, String reason) implements Result {
    }

    /**
     * What can answer on the start page, observed by the frame (OD-74.23): the switch, whether the resolver finds
     * {@code claude} (a file check, nothing run), whether the in-app assistant has a provider that can answer, and whether
     * in-process actions are on (the catalogue needs them in-app, r8 F4).
     */
    public record StartPageAvailability(boolean claudeCodeAllowed, boolean claudeCodeFound, boolean inAppCanAnswer,
                                        boolean inProcessActions) {
    }

    /**
     * The start page's button was pressed (§5.8): the person's act. {@code entryId} is a built-in id ({@code S1}…) or
     * {@code NONE} (free text alone).
     * {@code textRef} is the box's words in the start page's store, 0 when empty. {@code workspaceOpen}: a workspace is
     * behind the page (Help ▸ Start page), so the docked tab can be revealed; with none the assistant pops out (OD-74.21).
     * Robot re-walk #3: {@code aboutEvidence} — the box's words read as being about evidence (the frame's word check;
     * the words never travel); {@code noLogAcknowledged} — the person already chose to ask it with no log open (Ask
     * anyway), or a log was opened for it, so it is never held again.
     */
    public record StartPagePromptSent(long request, String entryId, long textRef,
                                      LocalAgentConsent consent, boolean claudeCodeFound, boolean inAppCanAnswer,
                                      boolean inProcessActions, boolean workspaceOpen, boolean aboutEvidence,
                                      boolean noLogAcknowledged) {

        /** The shape before robot re-walk #3: not read as evidence, so never held for want of a log. */
        public StartPagePromptSent(long request, String entryId, long textRef, LocalAgentConsent consent,
                                   boolean claudeCodeFound, boolean inAppCanAnswer, boolean inProcessActions,
                                   boolean workspaceOpen) {
            this(request, entryId, textRef, consent, claudeCodeFound, inAppCanAnswer, inProcessActions, workspaceOpen,
                    false, false);
        }
    }

    /** Robot re-walk #3: what the person chose on the start page's "No log is open" line. */
    public enum NoLogChoice { OPEN_SAMPLE, OPEN_LOG, ASK_ANYWAY }

    /** The person pressed one of the "No log is open" line's actions for held press {@code request}. */
    public record StartPageNoLogAnswered(long request, NoLogChoice choice) {
    }

    /** How a start-page flow ended. */
    public enum FlowOutcome { DONE, CANCELLED, REFUSED, FAILED }

    /**
     * A start-page flow ended (the chooser and load of S5, the chooser and verification of S3, the catalogue fetch of S4).
     * {@code logGeneration} is the log it opened (S5, S3), {@code bundleNameRef} the verified bundle's file name and
     * {@code templateNamesRef} the fetched names, each in the start page's store (0 when none; for S4, 0 = unreachable).
     */
    public record StartPageFlowDone(long request, FlowOutcome outcome, long logGeneration, long bundleNameRef,
                                    long templateNamesRef) {
    }

    /**
     * The person dropped a Markdown prompt pack on the start page (owner request 2026-10-17, §5.8): {@code packRef} names
     * the parsed pack in the frame's session-only store (0 when refused), or {@code refusal} and {@code refusalLine} say
     * why it was not used — a code and a line, never the file's words. Nothing of it is ever persisted.
     */
    public record StartPromptPackDropped(long packRef,
                                         telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Cause refusal,
                                         int refusalLine) {
    }

    /** "← Return to main options": the built-in list again. */
    public record ReturnToMainOptions(String origin) {
    }

    /** Answered at once by each start-page effect whose outcome arrives later (or never: a fallback shown). */
    public record StartPageEffectStarted(long opId, long request, String what) implements Result {
    }

    // ---- M74 P2b: propose onboarding (spec §5.6, OD-74.13) ------------------------------------------------------------

    /**
     * One proposed item as the dispatcher received it: Claude's KIND, and its path, class name and reason by REFERENCE
     * into the transcript store (0 = absent) with digests to compare them by — never the words themselves, which the
     * session record would otherwise carry (spec §4.1; scorecard R6). Also what was observed about the fields' lengths and
     * the class name's form. The node decides every item-level refusal from these.
     */
    public record ProposalItemFact(String kind, long pathRef, String pathDigest, long classNameRef, String classNameDigest,
                                   long reasonRef, boolean tooLong, boolean classNameMalformed) {
        public boolean hasPath() {
            return pathRef != 0;
        }

        public boolean hasClassName() {
            return classNameRef != 0;
        }

        public boolean hasReason() {
            return reasonRef != 0;
        }
    }

    /**
     * A hosted {@code propose} call whose shape passed, posted on the session thread by the dispatcher's
     * {@code propose} case for the admitted {@code generation}. The node records the offer and answers in
     * {@code ProposalState.answer(request)}; it changes NOTHING but the preview it asks for.
     */
    public record ProposalReceived(long request, long generation, long summaryRef, java.util.List<ProposalItemFact> items) {
        public ProposalReceived {
            items = java.util.List.copyOf(items == null ? java.util.List.of() : items);
        }
    }

    /**
     * The analyser's own read-only check of one item (§5.6 step 5): its verdict word ({@code PAIRED}, {@code FITS},
     * {@code READABLE}, {@code DIRECTORY}, {@code MARKDOWN}, {@code UNKNOWN}, …), whether it lies {@code outside} the set-up
     * directory after resolving links, and a refusal code ({@code NOT_FOUND}, {@code WRONG_TYPE}) or null.
     */
    public record ProposalItemPreviewed(String proposalId, String itemId, String verdict, boolean outside, String refusal) {
    }

    /**
     * Every item was checked: the set-up directory as the session uses it — by reference ({@code setupDirRef}, the frame
     * resolves it) and shown as {@code …/name} — and whether it already holds a project.
     */
    public record ProposalPreviewDone(String proposalId, long setupDirRef, String setupDirShown, boolean projectExists) {
    }

    /**
     * Scorecard R5 (spec §8.2): the person's answer to ONE row of the review dialog — Yes or No, for each row they could
     * answer, the create row's id being {@code create} — posted before {@link ProposalApplyRequested}. So the record tells
     * a No from a row never looked at.
     */
    public record ProposalItemAnswered(String proposalId, String itemId, boolean yes) {
    }

    /** The person's Apply chosen: the items answered Yes (all others are No), and the analyser's own create row. */
    public record ProposalApplyRequested(String proposalId, java.util.List<String> yes, boolean createProject) {
        public ProposalApplyRequested {
            yes = java.util.List.copyOf(yes == null ? java.util.List.of() : yes);
        }
    }

    /** The person closed the banner without applying: every pending item is declined. */
    public record ProposalDismissed(String proposalId) {
    }

    /**
     * What applying one item through the person's own path did: {@code outcome} {@code APPLIED}, {@code REFUSED} (the path's
     * own gate, said in its words) or {@code FAILED}. The id {@code create} is the analyser's own create row.
     */
    public record ProposalItemApplied(long opId, String proposalId, String itemId, String outcome, String why)
            implements Result {
    }

    /** The analyser's own verdicts after the last item (§5.6 step 8). */
    public record ProposalChecked(long opId, String proposalId, String pairing, String fit) implements Result {
    }

    /** Answered at once by a proposal effect whose outcome arrives later. */
    public record ProposalEffectStarted(long opId, String proposalId, String what) implements Result {
    }

    /**
     * The person chose (or cancelled) the set-up directory (§5.6 step 1): its real path by reference ({@code dirRef}; 0
     * when cancelled) and shown as {@code …/name}, and the folder verdict computed as for Start. Held for this analyser
     * run only; it is not a project. (R6: no absolute path in a fact.)
     */
    public record LocalAgentSetupDirectoryChosen(long request, long dirRef, String dirShown, FolderVerdict verdict) {
        public boolean cancelled() {
            return dirRef == 0;
        }
    }

    // ---- M74 P2b: a project switch while Claude Code runs (the simplified Move, owner 2026-10-17) ---------------------

    /** What the person chose in the switch offer: stop and get Start ready in the new project, keep it running, cancel. */
    public enum SwitchChoice { STOP_AND_ARM, KEEP, CANCEL }

    public record LocalAgentSwitchChosen(long request, SwitchChoice choice) {
    }

    /**
     * §5.2 the diagnostics line (P2): counts the adapter observed for {@code generation}, posted at a turn's end and at
     * initialize only when one changed. Never a person's line: command names come from {@code initialize.commands}.
     */
    public record LocalAgentDiagnostics(long generation, int unknownEvents, int unclassifiedCommands, int droppedLines) {
    }

    // ---- M74 P3: permission prompts in the analyser (spec §5.7, §8.3; OD-74.16, OD-74.17) ------------------------------
    //
    // A request's words (the tool's name, its server, its input) live in the bounded LocalAgentTranscript; these facts
    // carry the store reference, the tool's CLASS, a verb or built-in name from a fixed vocabulary, and digests.

    /** What kind of tool a {@code can_use_tool} names — the class the policy of OD-74.16 is decided on (§5.7). */
    public enum ToolClass {
        /**
         * One of the analyser's hosted ASKED verbs: the destructive ones ({@code open, source_root, screenshot, report,
         * walk, import}) and, since 2026-10-18, {@code graph}, judged call by call.
         */
        HOSTED_DESTRUCTIVE,
        /** Another hosted verb — pre-allowed, so a request for it is an anomaly. */
        HOSTED_OTHER,
        /** A tool of the person's own {@code fluxtion-analyser} registration: never this analyser. */
        PERSONS_ANALYSER_REGISTRATION,
        /** One of the CLI's own tools (Bash, Edit, Write, WebFetch, …), named from the CLI's fixed vocabulary. */
        BUILT_IN,
        /** A tool of one of the person's own MCP servers. */
        OTHER_MCP,
        /** Anything else. */
        UNKNOWN
    }

    /** Who decided a permission request's answer (§5.7 *Audit*). */
    /**
     * Who decided. {@code NON_MUTATING} (review M74 usability #10b, a refinement of OD-74.16): the analyser allowed, without
     * a dialog, a sub-action of an asked verb that changes nothing ({@code walk {play}}, {@code walk {end}}, the bins'
     * listings) — only ever for {@link HostedCallKind#NON_MUTATING}, and the server re-checks the call itself.
     */
    public enum PermissionBy { PERSON, TIMEOUT, STOP, POLICY, BUSY, TOO_LARGE, CANCELLED, NON_MUTATING, PRE_ALLOWED }

    /**
     * Review M74 usability #10 (2026-10-17): what a hosted call of an asked verb does, classified by the adapter from the
     * input it holds ({@code McpTools.hostedCallKind}) — never its words. {@code NON_MUTATING}: a sub-action that changes
     * nothing stored (playing or ending a walk, listing a bin); {@code WRITES_FILE}: it writes a file in the exchange
     * folder (a screenshot, a report's PDF or CSV, a bundle, a walk's reel); {@code PRE_ALLOWED} (owner decision
     * 2026-10-18): a call of a verb judged call by call ({@code graph}) that the pre-allow list covers — it creates a new
     * chart or changes only the view, never an existing chart's stored definition; {@code CHANGES}: everything else.
     */
    public enum HostedCallKind { CHANGES, NON_MUTATING, WRITES_FILE, PRE_ALLOWED }

    /**
     * Review M74 usability #10a: whether the assistant's file exchange (Settings ▸ Assistant) is on — the frame's
     * observation, so {@code localAgentPermission} never asks the person about a write the settings would refuse.
     */
    public record AssistantFileExchangeObserved(boolean on) {
    }

    /**
     * The adapter observed a {@code can_use_tool} for {@code generation}'s turn {@code ticket}. {@code requestRef} names the
     * request in the store (its words and the CLI's request id stay in the adapter); {@code name} is the hosted verb (for
     * a hosted class) or the built-in's name (for {@code BUILT_IN}) from a fixed vocabulary, else null.
     * {@code inputDigest} and {@code toolUseIdDigest} are {@code CanonicalJson.digest} of the input and
     * {@code LocalAgentPosture.digest} of the id; {@code showable} says whether the input can be shown in full (§5.7: at
     * most 64 KB canonical, 32 levels, JSON).
     */
    public record LocalAgentPermissionRequested(long generation, long ticket, long requestRef, ToolClass toolClass,
                                                String name, String inputDigest, String toolUseIdDigest,
                                                boolean showable, HostedCallKind kind) {
        /** A request whose call has not been classified: it changes something, as every asked call did before #10. */
        public LocalAgentPermissionRequested(long generation, long ticket, long requestRef, ToolClass toolClass,
                                             String name, String inputDigest, String toolUseIdDigest, boolean showable) {
            this(generation, ticket, requestRef, toolClass, name, inputDigest, toolUseIdDigest, showable,
                    HostedCallKind.CHANGES);
        }

        public HostedCallKind kind() {
            return kind == null ? HostedCallKind.CHANGES : kind;
        }
    }

    /**
     * Scorecard R8: the answer the adapter WROTE to the CLI for {@code requestRef} — the result of
     * {@code AnswerPermissionEffect}, so the session record holds every answer as a fact: the turn, the class, the
     * vocabulary name, the decision and who decided it. {@code written} false: the process was gone.
     */
    public record LocalAgentPermissionAnswered(long opId, long generation, long ticket, long requestRef, ToolClass toolClass,
                                               String name, boolean allow, PermissionBy by, boolean written)
            implements Result {
    }

    /** The person pressed a button of the dialog for {@code requestRef} (closing it is Deny). */
    public record LocalAgentPermissionChosen(long requestRef, boolean allow) {
    }

    /** The adapter's clock: {@code deadlineMs} have passed since the dialog for {@code requestRef} was asked for. */
    public record LocalAgentPermissionDeadline(long generation, long requestRef) {
    }

    /** The CLI withdrew request {@code requestRef} itself ({@code control_cancel_request}, P3-2). */
    public record LocalAgentPermissionCancelled(long generation, long requestRef) {
    }

    /** The adapter's clock: 60 s have passed since grant {@code grantId} was issued (OD-74.17). */
    public record LocalAgentGrantExpired(long generation, long grantId) {
    }

    /**
     * OD-74.17: the hosted bridge presented a call of a hosted DESTRUCTIVE verb with this generation's credential. Call
     * {@code call} waits (bounded) for the session's verdict, which consumes the one matching unconsumed grant or refuses
     * — decided on the session's thread, in its order, never on the HTTP thread. {@code inputDigest} is
     * {@code CanonicalJson.digest} of the call's params; {@code toolUseIdDigest} is the digest of the
     * {@code claudecode/toolUseId} the bridge forwarded (P3-3), or null.
     */
    public record HostedCallPresented(long call, long generation, String verb, String inputDigest, String toolUseIdDigest) {
    }

    /**
     * Holistic review B4 (2026-10-17): hosted call {@code call} stopped waiting for the session's verdict (its bounded
     * wait passed) and the server refused it. The session may still admit it afterwards — consuming a grant for a call
     * that never ran — so this fact says so, and {@code localAgentPermission} voids what that admission consumed.
     */
    public record HostedCallAbandoned(long call, long generation) {
    }

    // ---- M74 P2b: prompt-pack example entries (owner request 2026-10-17) -----------------------------------------------

    /**
     * The person pressed an example entry's own Download button: the entry's parsed source, from the pack the node holds.
     * Nothing was fetched on the drop or on choosing; the node decides each step from here.
     */
    public record ExampleDownloadRequested(long request, long packRef, int entryIndex,
                                           telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource source) {
    }

    /**
     * The person answered the confirmation that showed the exact URL, the publisher, the pinned commit and the target
     * folder: {@code yes}, the folder chosen ({@code targetDir}, real path) and what was observed about it
     * ({@code targetProblem}: null, {@code EXISTS_NOT_EMPTY}, {@code NOT_A_DIRECTORY}, {@code NO_PARENT}).
     */
    public record ExampleDownloadConfirmed(long request, boolean yes, long targetRef, String targetShown,
                                           String targetProblem) {
    }

    /**
     * The archive arrived (or did not): {@code bytesRef} names the bytes in the frame's store (never in a fact), with
     * their SHA-256 and size as observed; or {@code failure} in words. The node verifies the digest before any unpack.
     */
    public record ExampleFetched(long request, boolean ok, long bytesRef, String sha256, long size, String failure) {
    }

    /** The unpack through the template archive machinery: the project root it made, or why not. */
    public record ExampleUnpacked(long request, boolean ok, long rootRef, String rootShown, String failure) {
    }

    /** Answered at once by each example effect. */
    public record ExampleEffectStarted(long opId, long request, String what) implements Result {
    }

    /** Review S7: the deadline a {@link SessionEffects.StartExampleClockEffect} started has passed. */
    public record ExampleDeadlinePassed(long request, String phase) {
    }
}
