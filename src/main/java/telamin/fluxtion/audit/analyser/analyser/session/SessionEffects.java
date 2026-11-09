package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * What the processor <b>asks be done</b>. Never what was done — that is a result in
 * {@link SessionEvents}.
 *
 * <p>The processor emits these into {@code EffectQueue}; the driver drains them <em>after</em>
 * {@code onEvent} returns and performs them outside Fluxtion dispatch. <b>If an adapter ever decides
 * whether to perform one, the decision has leaked back out</b> (M44 §7).
 *
 * <p>Every effect is answered — by its typed success result or by
 * {@link SessionEvents.EffectFailed}. An effect with no answer is a hole in the audit record, so
 * {@code SessionDriver} treats one as a programming error rather than letting it pass.
 */
public sealed interface SessionEffects {

    /** The operation this effect belongs to; the answering result must carry the same id. */
    long opId();

    record ObserveProjectOutputsEffect(long opId, long watch, long projectGeneration, String profilePath)
            implements SessionEffects { }
    record WatchProjectOutputsEffect(long opId, long watch, long projectGeneration, long rootRef,
                                    java.util.List<Long> logDirs, int pollMillis) implements SessionEffects {
        public WatchProjectOutputsEffect { logDirs = java.util.List.copyOf(logDirs); }
    }
    record StopProjectOutputsWatchEffect(long opId, long watch) implements SessionEffects { }
    record PublishProjectOutputChangedEffect(long opId, long watch, long projectGeneration, long change,
                                            ProjectOutputsState.File file) implements SessionEffects { }
    record OfferProjectOutputEffect(long opId, long watch, long offer) implements SessionEffects { }
    record OpenProjectOutputEffect(long opId, long watch, long projectGeneration, long offer,
                                    ProjectOutputsState.File file, ProjectOutputsState.File graph)
            implements SessionEffects { }
    /** M80: observe authorised definition candidates off the UI thread; the node owns the ticket and selection. */
    record DiscoverDesignEffect(long opId, long generation) implements SessionEffects { }
    /** Present the node's discovered zero/one/many state. No session design is silently replaced. */
    record PresentDesignDiscoveryEffect(long opId, long generation, String file) implements SessionEffects { }
    /** Render an accepted Follow revision; stale background completions never request this effect. */
    record PresentDesignRefreshEffect(long opId, long generation, String previousRevision) implements SessionEffects { }

    /** Answer the final, EDT-confined report-write check. The adapter acts only on this verdict. */
    record AnswerHostedReportApplyEffect(long opId, String refusal) implements SessionEffects { }

    /** Read the profile file at this path and report {@link SessionEvents.ProfileLoaded}. */
    record LoadProfileEffect(long opId, String profilePath, String expectedDigest) implements SessionEffects {
        public LoadProfileEffect(long opId, String profilePath) { this(opId, profilePath, null); }
    }

    /** Verify and unpack a bundle off the event thread; report the result with the same operation id. */
    record PrepareBundleEffect(long opId, String bundlePath) implements SessionEffects { }

    /** Open the verified graph and log after the bundled project has been applied. */
    record OpenBundleEvidenceEffect(long opId, SessionEvents.BundlePlan plan) implements SessionEffects { }

    /** Remember, for this bundle, the source trees the person chose while it was open (#75). */
    record RememberBundleAnchorEffect(long opId, String bundleSource,
                                      java.util.List<String> roots) implements SessionEffects { }

    /**
     * Offer the logs and topologies this machine has opened inside the project now in force (O3).
     *
     * <p>The effect asks for an OFFER, never for an open: which files exist is the adapter's knowledge,
     * and whether to take one is the person's. The session's part is knowing that a project just came
     * into force and that it did not arrive from a bundle -- a bundle brings its own evidence and must
     * never be asked this.
     */
    record OfferProjectReopenEffect(long opId, String profilePath) implements SessionEffects { }

    /** The node accepted these observed candidates. Present outside the driver cycle and report. */
    record ShowProjectReopenEffect(long opId, String profilePath, String label,
                                   telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen candidates)
            implements SessionEffects { }

    /** Put back the source trees remembered for this bundle, so reopening it does not ask again (#75). */
    record RestoreBundleAnchorEffect(long opId, String bundleSource) implements SessionEffects { }

    /**
     * #177: the open project's profile changed on disk — OFFER the person the choice (reload from disk, or keep this
     * session's version). Never a reload by itself: whose version wins is the person's to say.
     */
    record OfferProjectProfileReloadEffect(long opId, String profileKey, String reason) implements SessionEffects { }

    /** #177: read the active profile again, replacing the in-memory project settings — the person or the socket asked. */
    record ReloadProjectProfileEffect(long opId, String profileKey, String origin) implements SessionEffects { }

    /** #177: write this session's version over the newer file on disk — only ever on the person's choice. */
    record KeepProjectProfileEffect(long opId, String profileKey) implements SessionEffects { }

    /**
     * #178: put the flags this bundle carries back on the bundle's log, generation {@code generation} — each only on a
     * record whose text has the digest it was flagged with. {@code bundleFlags} decided that this log is the bundle's;
     * the adapter reads the working copy's profile, compares the records and reports what it put back and what not.
     */
    record RestoreBundleFlagsEffect(long opId, long generation, String bundleIdentity) implements SessionEffects { }

    /**
     * Start a new, empty project at this path and make it the loaded one.
     *
     * <p>Distinct from {@link LoadProfileEffect} rather than a flag on it, because it is a different
     * act with a different failure: loading a profile that is not there is an error, and creating one
     * where a project already exists would be destruction. An adapter told to "load, and create if
     * missing" would have to decide which — and adapters do not decide.
     */
    record CreateProfileEffect(long opId, String profilePath, SessionEvents.CreateApproved approved)
            implements SessionEffects {
        /** An empty project: nothing adopted. */
        public CreateProfileEffect(long opId, String profilePath) {
            this(opId, profilePath, null);
        }
    }

    /**
     * M73: a PERSON asked to create a project where one already exists. Ask "Open it instead?" outside the dispatch
     * ({@code invokeLater}, the {@link ShowProjectReopenEffect} pattern) and, on yes, submit a NEW
     * {@code OpenProjectRequested(EXPLICIT_SWITCH)} from there — never a re-entrant submit.
     */
    record OfferOpenExistingEffect(long opId, String profilePath) implements SessionEffects {
    }

    /** Put the loaded profile's settings genuinely in force, then report. */
    record ApplyProfileEffect(long opId, String profilePath, String name) implements SessionEffects {
    }

    /** Revert to the user's own pre-project settings, then report. */
    record RestoreSettingsEffect(long opId) implements SessionEffects {
    }

    /** Close the open log because this transition is a session boundary. */
    record CloseLogEffect(long opId) implements SessionEffects {
    }

    /**
     * Close the open topology graph. {@code graphPath} names the graph the decision judged (M44.3a): an
     * adapter holding a DIFFERENT graph by the time the effect runs closes nothing and says so.
     */
    record CloseGraphEffect(long opId, String graphPath) implements SessionEffects {
    }
    /**
     * M44.3: start loading a log. The adapter answers {@link SessionEvents.Pending} at once and
     * {@link SessionEvents.LogOpened} / {@link SessionEvents.LogOpenFailed} when the load lands.
     */
    record OpenLogEffect(long opId, String location, String format, String provenance, boolean fromSocket,
                         SessionEvents.AssistantActionOrigin assistantOrigin)
            implements SessionEffects {
        public OpenLogEffect(long opId, String location, String format, String provenance, boolean fromSocket) {
            this(opId, location, format, provenance, fromSocket, null);
        }
    }

    /** Say something in the status line. Infallible by construction, but still answered. */
    record ShowStatusEffect(long opId, String text) implements SessionEffects {
    }

    /** Warn — the louder surface, for a transition that did not do what was asked. */
    record ShowWarningEffect(long opId, String text) implements SessionEffects {
    }

    /**
     * M44.5: compute the open log's producer findings and time order for {@code generation}. The adapter answers
     * {@link SessionEvents.ScanScheduled} at once and runs the scan after the current task — never inside this dispatch,
     * because the store for a just-opened generation is installed after {@code LogOpened} returns — then posts
     * {@link SessionEvents.ProducerFindingsObserved} and {@link SessionEvents.TimeOrderObserved}.
     */
    record ScanLogEvidenceEffect(long opId, long generation) implements SessionEffects {
    }

    /**
     * Measure the Records view of {@code generation}: how many records it shows, how many each active filter hides, and
     * each event type's shown and total counts. Decided by {@code recordsView}; the adapter counts against the view it
     * has installed and answers {@link SessionEvents.RecordsViewMeasured} at once. The counting reads the store and the
     * filter and changes nothing.
     */
    record MeasureRecordsViewEffect(long opId, long generation) implements SessionEffects {
    }

    // ---- M69 spotlight walks: decided by the walkPlayback node, performed by the frame (spec §3.8) ----------

    /**
     * Apply step {@code step} of walk {@code walk}'s view, then prepare it: wait (non-blocking, bounded) for the charts
     * it involves, and resolve its targets. Answered at once by {@link SessionEvents.WalkViewApplied}; the prepared
     * step arrives later as {@link SessionEvents.WalkStepPrepared}, carrying {@code ticket} and {@code generation}.
     */
    record ApplyWalkViewEffect(long opId, long ticket, long generation, telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec walk, int step,
                               boolean recordsTrusted) implements SessionEffects {
    }

    /** Light these targets (the available ones, numbered in step order) on the overlay. */
    record LightWalkTargetsEffect(long opId, long ticket, java.util.List<SessionEvents.WalkTargetState> targets)
            implements SessionEffects {
        public LightWalkTargetsEffect {
            targets = java.util.List.copyOf(targets);
        }
    }

    /** Re-resolve the showing step's targets without re-applying its view — the log's identity changed. */
    record ResolveWalkTargetsEffect(long opId, long ticket, long generation, telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec walk, int step, String why,
                                    boolean recordsTrusted)
            implements SessionEffects {
    }

    /** The walk ended: put its lit targets and its strip away, restore keyboard focus. */
    record EndWalkEffect(long opId, long ticket, String reason) implements SessionEffects {
    }

    // ---- evidence bundle capture: decided by the evidenceCapture node, performed by the frame -------------------

    /** Turn Follow on or off, then answer {@link SessionEvents.FollowSet}: capture pauses it and restores it. */
    record SetFollowEffect(long opId, long ticket, boolean on) implements SessionEffects {
    }

    /**
     * Write the bundle: flush the project's pending write, take what the capture needs from the live session (the
     * settings bytes, and for a window the excerpt), and start the file work off this thread. Answered at once by
     * {@link SessionEvents.CaptureStarted}; the outcome arrives later as {@link SessionEvents.BundleWritten} or
     * {@link SessionEvents.BundleWriteFailed}, carrying {@code ticket} and {@code generation}.
     *
     * <p>{@code readSoFar} (owner, 2026-09-28, EB.F6): the log is still growing under Follow, so the bundle holds the
     * records read so far, as an excerpt of all of them, and never the file, which has more than was read.
     *
     * <p>{@code replay} (replay spec §4.1): the replay file the node allowed, already paired with the log, or null; the
     * writer packs it as the bundle's {@code replay/} member and holds the copy to {@code replaySha256}.
     */
    record CaptureBundleEffect(long opId, long ticket, long generation, String path, String notes, Long from, Long to,
                               boolean readSoFar, String replay, int replayRecords, int serviceCalls,
                               String replaySha256) implements SessionEffects {
        /** A capture with no replay. */
        public CaptureBundleEffect(long opId, long ticket, long generation, String path, String notes, Long from, Long to,
                                   boolean readSoFar) {
            this(opId, ticket, generation, path, notes, from, to, readSoFar, null, 0, 0, null);
        }
    }

    /** Delete a bundle this capture wrote, with any working folder left beside it; answer {@link SessionEvents.BundleDeleted}. */
    record DeleteBundleEffect(long opId, long ticket, String path) implements SessionEffects {
    }

    // ---- the onboard assistant: decided by the assistantLoop node, performed by the frame (OA-1) -------------------

    /**
     * Compose the turn's first content (the action manifest when it has not been sent in this conversation, the selected
     * records' context, then the question) into a hidden transcript entry. The selection is taken on the event thread
     * when this is performed; the record and source assembly runs off it. Answered at once by
     * {@link SessionEvents.AssistantEffectStarted}; later {@link SessionEvents.AssistantContextPrepared} or
     * {@link SessionEvents.AssistantContextFailed}, carrying {@code ticket}.
     */
    record PrepareAssistantContextEffect(long opId, long ticket, long draft, boolean includeManifest,
                                         boolean includeRecordContext, int maxActionsPerReply, long logGeneration)
            implements SessionEffects {
        /**
         * Owner decision (2026-10-17, final review #12): {@code logGeneration} — the log generation the message's
         * composed drafts were checked against at Send, or 0 for none — is told to the MODEL with the prompt; the
         * person's draft no longer carries it.
         */
        public PrepareAssistantContextEffect(long opId, long ticket, long draft, boolean includeManifest,
                                             boolean includeRecordContext, int maxActionsPerReply) {
            this(opId, ticket, draft, includeManifest, includeRecordContext, maxActionsPerReply, 0);
        }
    }

    /**
     * Send the provider this history: each message a role and the transcript entries it is made of ({@code RESULTS}
     * groups become one "action results" message). The route names the provider; the adapter reads the key when it
     * performs this, so no credential passes through the graph. Answered at once; later
     * {@link SessionEvents.AssistantCompletionReceived} or {@link SessionEvents.AssistantCompletionFailed}.
     */
    record RequestAssistantCompletionEffect(long opId, long ticket, int round, java.util.List<HistoryMessage> history,
                                            SessionEvents.AssistantRoute route) implements SessionEffects {
        public RequestAssistantCompletionEffect {
            history = java.util.List.copyOf(history);
        }
    }

    /** One provider message: {@code role} user or assistant, {@code kind} TEXT or RESULTS, made of these entries. */
    record HistoryMessage(String role, String kind, java.util.List<Long> entries) {
        public HistoryMessage {
            entries = java.util.List.copyOf(entries);
        }
    }

    /**
     * Run action entry {@code action}, one action, through the shared dispatcher, off the event thread, with every Swing
     * mutation it makes guarded by {@code ticket} (the node's decision is re-checked inside each event-thread task).
     * Answered at once; later {@link SessionEvents.AssistantActionFinished}.
     */
    record RunAssistantActionEffect(long opId, long ticket, long action) implements SessionEffects {
    }

    /** Stop the in-flight provider request or action of {@code ticket}, if any. An optimisation: staleness is decided. */
    record CancelAssistantTransportEffect(long opId, long ticket) implements SessionEffects {
    }

    /** Put the assistant in its side tab ({@code docked}) or its own window. */
    record ShowAssistantHostEffect(long opId, boolean docked) implements SessionEffects {
    }

    /**
     * M74 UX: write {@code profile} to this machine's settings — the same machine-tier fields Settings ▸ Assistant writes,
     * with the details the overlay holds — then report the consent and availability as every settings change does.
     * Answered by {@link SessionEvents.AssistantProfileApplied}.
     */
    record ApplyAssistantProfileEffect(long opId, long request, AssistantProfile profile,
                                       SessionEvents.ProfileOrigin origin, String fullMode) implements SessionEffects {
        /**
         * Owner follow-up (2026-10-18): {@code fullMode} — the full posture's mode the deciding node chose (the wizard's
         * sub-choice), or null for the surface's own details. Before it, the wizard's mode was never written: a Full
         * applied from Set up AI always ran {@code default}.
         */
        public ApplyAssistantProfileEffect {
            origin = origin == null ? SessionEvents.ProfileOrigin.MENU : origin;
        }

        public ApplyAssistantProfileEffect(long opId, long request, AssistantProfile profile,
                                           SessionEvents.ProfileOrigin origin) {
            this(opId, request, profile, origin, null);
        }

        /** A quick-switch Apply. */
        public ApplyAssistantProfileEffect(long opId, long request, AssistantProfile profile) {
            this(opId, request, profile, SessionEvents.ProfileOrigin.MENU);
        }
    }

    // ---- M77 (spec-ai-setup-wizard.md §8.4): Set up AI, decided by aiSetup, performed by the frame ------------------

    /**
     * Create the Set up AI window, or bring the one that is open to the front. What it shows is rendered from
     * {@link AiSetupState}; this only makes it visible. Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record ShowAiSetupEffect(long opId, AiSetupState.Step step) implements SessionEffects {
    }

    /**
     * Ask the one resolver whether {@code claude} and {@code codex} are on this machine — file existence only, NO process
     * (S2) — and report {@link SessionEvents.StartPageAvailability} and {@link SessionEvents.AiClientsObserved}.
     */
    record ObserveAiClientsEffect(long opId) implements SessionEffects {
    }

    /**
     * Resolve {@code claude}, then run {@code claude --version} (5 s) with the child environment of the posture
     * ({@code full}: the PATH extended as for Full), in a fresh EMPTY scratch folder (S10), and report
     * {@link SessionEvents.ClaudeObserved} for {@code probe}. Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record ProbeClaudeEffect(long opId, long probe, boolean full) implements SessionEffects {
    }

    /**
     * Run {@code claude auth status --json} (10 s), the same environment and the same kind of empty folder, and report
     * {@link SessionEvents.ClaudeAuthObserved} parsed at the boundary (§8.6): nothing personal leaves the parser (S7).
     */
    record ProbeClaudeAuthEffect(long opId, long probe, boolean full) implements SessionEffects {
    }

    /** Write {@code assistant.setup.dismissed=true} (machine tier). */
    record PersistSetupDismissedEffect(long opId) implements SessionEffects {
    }

    /** Write {@code assistant.setup.client} (machine tier); {@link AiSetupState.Client#NONE} clears it. */
    record PersistSetupClientEffect(long opId, AiSetupState.Client client) implements SessionEffects {
    }

    /**
     * The Summary's suggested first action (§6.4): open the demo sample, or start the guided tour, with the frame's
     * existing actions. Nothing is sent. Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record RunSuggestedActionEffect(long opId, AiSetupState.Suggested suggested) implements SessionEffects {
    }

    /**
     * M77-2's interim client path (review R1-11), until M77-3 builds Screen 2b: open today's {@code McpSetupDialog} at
     * {@code client}. Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record OpenClientSetupEffect(long opId, AiSetupState.Client client) implements SessionEffects {
    }

    // ---- M77-3 (spec-ai-setup-wizard.md §8.4, §9): the external client path ------------------------------------------

    /**
     * Requested by {@code actionListener} (§9.2): ask the person whether to turn External MCP/REST access on, under the
     * SafeChoice rule (<i>Turn on access</i> at the left, never default; <i>Cancel</i> the default). Reports
     * {@link SessionEvents.ExternalAccessAnswered}. Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record ConfirmExternalAccessEffect(long opId, long request) implements SessionEffects {
    }

    /**
     * Requested by {@code actionListener} ONLY: write {@code assistant.rest}, save, and report
     * {@link SessionEvents.RestSwitchObserved} — the one writer of the setting (besides M19.7's {@code --rest} start-up
     * write, before the session exists). Answered at once by {@link SessionEvents.AiSetupEffectStarted}.
     */
    record SetExternalAccessEffect(long opId, long request, boolean on) implements SessionEffects {
    }

    /**
     * Requested by {@code aiSetup} for the box under No AI (OD-77.5): ask {@code actionListener} to turn access off, as a
     * door does — the performer posts {@link SessionEvents.ExternalAccessRequested} with origin SETUP.
     */
    record RequestExternalAccessEffect(long opId, long request, boolean on) implements SessionEffects {
    }

    /**
     * <i>Check registration</i>: the client's own {@code mcp get} (Claude Code's may health-check the server), in a fresh
     * empty scratch folder (S10), with the client resolved by the one resolver. Reports
     * {@link SessionEvents.McpRegistrationObserved}.
     */
    record CheckMcpRegistrationEffect(long opId, long probe, AiSetupState.Client client) implements SessionEffects {
    }

    /**
     * Screen 2b's <i>Check connection</i>: start the bridge command a client is registered with ({@code --mcp}), off the
     * event thread, and ask it what is open ({@code McpConnectionProbe}, read-only; its call is marked, so it never counts
     * as the live check's client). Reports {@link SessionEvents.BridgeObserved} for {@code probe}.
     */
    record ProbeBridgeEffect(long opId, long probe) implements SessionEffects {
    }

    /**
     * <i>Register for me…</i>: show, under the SafeChoice rule, the verbatim argument vector(s) that will run ({@code
     * replace}: the remove, then the add). The vectors are kept for {@code request}; {@link RegisterMcpEffect} runs THOSE
     * (S5). Reports {@link SessionEvents.RegisterAnswered}.
     */
    record ConfirmRegisterEffect(long opId, long request, AiSetupState.Client client, boolean replace)
            implements SessionEffects {
    }

    /** Run the vectors the confirmation for {@code request} showed — never re-rendered. Reports McpRegistrationChanged. */
    record RegisterMcpEffect(long opId, long request, AiSetupState.Client client) implements SessionEffects {
    }

    /** Put {@code what} on the clipboard ({@code client} picks the register command or the configuration). */
    record CopyToClipboardEffect(long opId, SessionEvents.CopyWhat what, AiSetupState.Client client)
            implements SessionEffects {
    }

    /**
     * Arm the live check (§9.4): the action server reports the first counted external {@code /action} once, as
     * {@link SessionEvents.ExternalClientSeen} for {@code watch}. The adapter re-applies the watch to a rebuilt server.
     */
    record WatchExternalClientEffect(long opId, long watch) implements SessionEffects {
    }

    /** Disarm watch {@code watch} (a later sighting is not reported). */
    record UnwatchExternalClientEffect(long opId, long watch) implements SessionEffects {
    }

    /** Report {@link SessionEvents.AiSetupTimerFired} for {@code watch} after {@code seconds}. */
    record AiSetupTimerEffect(long opId, long watch, int seconds) implements SessionEffects {
    }

    /**
     * Review PR151 F4 (owner, 2026-10-18): turning on Full Claude Code from the cards asks what Settings ▸ Assistant asks —
     * the full warning, modal, Cancel the default (Enter and Escape), <i>Turn on full Claude Code</i> the explicit risky
     * choice (the SafeChoice rule). The performer shows it outside the dispatch and reports
     * {@link SessionEvents.AssistantFullProfileAnswered}; only a confirmed answer lets {@code assistantProfile} apply it.
     * Answered at once by {@link SessionEvents.AssistantProfilesShown}.
     */
    record ConfirmFullProfileEffect(long opId, long request) implements SessionEffects {
    }

    /**
     * Review P3+UX S4 (2026-10-17): put the tab in {@code mode} — requested by {@code assistantProfile} once a profile's
     * settings were written, so the mode change is the node's decision; the performer reports it as the person's mode
     * choice ({@link SessionEvents.AssistantModeChosen}), and {@code localAgentSession} and {@code assistantMode} decide what
     * it means exactly as for the selector.
     */
    record ChooseAssistantModeEffect(long opId, AssistantMode mode, String origin) implements SessionEffects {
    }

    // ---- M74: the local agent, decided by the localAgentSession node, performed by LocalAgentAdapter (§4.1) ----------

    /**
     * Spawn the local agent for {@code generation}: take the session lock, mint this generation's hosted credential,
     * write the per-instance endpoint file holding the REST URL, the token, {@code nonce} and the credential, start the
     * CLI with the default posture's command line (§4.3), and send {@code initialize}. Answered at once by
     * {@link SessionEvents.LocalAgentEffectStarted}; later {@link SessionEvents.LocalAgentSpawned} and the handshake facts.
     *
     * @param sessionId       the conversation id: {@code --session-id} when {@code resume} is false, else {@code --resume}
     * @param nonce           the binding nonce minted for THIS spawn; never reused across generations (§4.2)
     * @param maxBudgetMicros {@code --max-budget-usd}: the session budget less what the session total has spent (§4.6);
     *                        0 when no limit is set, and then the flag is not passed at all (owner, 2026-10-17)
     * @param postureRef      the posture digest decided for THIS spawn (it names the real working directory); the adapter
     *                        refuses to spawn when the folder it reads digests otherwise (review M7)
     * @param posture         the posture value the command line and the child's environment are computed from (§4.3)
     */
    record StartLocalAgentEffect(long opId, long generation, String sessionId, boolean resume, String nonce,
                                 long maxBudgetMicros, String postureRef, LocalAgentPosture posture) implements SessionEffects {
        public StartLocalAgentEffect {
            posture = posture == null ? LocalAgentPosture.ANALYSER_ASSISTANT : posture;
        }

        public StartLocalAgentEffect(long opId, long generation, String sessionId, boolean resume, String nonce,
                                     long maxBudgetMicros, String postureRef) {
            this(opId, generation, sessionId, resume, nonce, maxBudgetMicros, postureRef, LocalAgentPosture.ANALYSER_ASSISTANT);
        }
    }

    /** Ask the CLI for {@code mcp_status} after {@code delayMs}, and report {@link SessionEvents.LocalAgentStatusObserved}. */
    record PollLocalAgentStatusEffect(long opId, long generation, long delayMs) implements SessionEffects {
    }

    /** M81: tick while the published generation/ticket remains current; ticket 0 covers starting. */
    record StartActivityClockEffect(long opId, long generation, long ticket) implements SessionEffects { }

    /** M81: place the interrupted request plus the node's fixed recovery line; never send it. */
    record PlaceRecoveryDraftEffect(long opId, long generation, long draftRef, String fixedLine)
            implements SessionEffects { }

    /** Write transcript entry {@code draft} to the CLI as one user line, with a freshly minted uuid for {@code ticket}. */
    record SendLocalAgentTurnEffect(long opId, long generation, long ticket, long draft, long logGeneration)
            implements SessionEffects {
        /**
         * Owner decision (2026-10-17, final review #12): {@code logGeneration} — the log generation the message's
         * composed drafts were checked against at Send, or 0 for none — is told to the MODEL on the user line; the
         * person's words in the transcript do not carry it.
         */
        public SendLocalAgentTurnEffect(long opId, long generation, long ticket, long draft) {
            this(opId, generation, ticket, draft, 0);
        }
    }

    /** How a Stop is carried out. */
    enum StopHow {
        /** A stream-json {@code interrupt} control request for the one open turn; the deadlines are scheduled. */
        INTERRUPT,
        /** Kill the process tree: snapshot its descendants, SIGTERM, wait, kill the survivors (§4.7). */
        KILL
    }

    /** Stop {@code generation}'s turn {@code ticket} (0 when none is open) the way {@code how} says. */
    record StopLocalAgentEffect(long opId, long generation, long ticket, StopHow how) implements SessionEffects {
    }

    /**
     * Owner, 2026-10-18: {@code localAgentSession} decided that a settings change can wait for the person — the running
     * session keeps its old settings until they answer offer {@code offer} (rendered from {@code LocalAgentState.Restart}).
     * The performer makes the assistant panel visible and says {@code status} on the main window's status line; it
     * decides nothing. The answer arrives as {@link SessionEvents.LocalAgentRestartAnswered}. Answered at once by
     * {@link SessionEvents.LocalAgentEffectStarted}.
     */
    record OfferLocalAgentRestartEffect(long opId, long generation, long offer, String status) implements SessionEffects {
    }

    /**
     * Ask the person, outside the dispatch, whether this working directory may be used by Claude Code (OD-74.5). On yes
     * the frame records the confirmation on this machine and submits a NEW Start request; it never starts anything here.
     * {@code full} (OD-74.12): the full-posture dialog, which reads the folder's three files (bounded, names only), names
     * the hooks and servers that will run, and records the trust bound to their digest.
     */
    record ConfirmLocalAgentFolderEffect(long opId, long request, boolean full) implements SessionEffects {
        public ConfirmLocalAgentFolderEffect(long opId, long request) {
            this(opId, request, false);
        }
    }

    // ---- M74 P2: the Ask catalogue's drafts and the start page (spec §5.5, §5.8, §8.2) -------------------------------

    /**
     * Place local-agent transcript entry {@code draftRef} in the Claude Code composer: APPENDED after anything the person
     * wrote (OD-74.14), the appended range highlighted, the composer focused and lit. {@code armedWhy} non-null arms
     * Start with those words (the bundle's {@code armed.*} key); nothing starts because Start is armed. Answered by
     * {@link SessionEvents.DraftPlaced}.
     */
    record PrimeLocalAgentDraftEffect(long opId, long draftRef, boolean highlight, String armedWhy, long logGeneration)
            implements SessionEffects {
        /**
         * Final review #12: {@code logGeneration} is the log generation the draft is bound to (0: it names no log) — the
         * composer's binding chip renders it against the snapshot's open log; the draft's words no longer say it.
         */
        public PrimeLocalAgentDraftEffect(long opId, long draftRef, boolean highlight, String armedWhy) {
            this(opId, draftRef, highlight, armedWhy, 0);
        }
    }

    /**
     * Robot walkthrough #1 (2026-10-18): {@code assistantProfile} decided where the question held while no assistant was
     * chosen goes — the assistant of {@code mode}. The performer reports it composed for that assistant
     * ({@code LocalAgentDraftComposed} / {@code AssistantDraftComposed}), whose node binds and places it. Answered by
     * {@link SessionEvents.DraftPlaced}.
     */
    record PlacePendingDraftEffect(long opId, long draftRef, AssistantMode mode) implements SessionEffects {
    }

    /**
     * Robot walkthrough #1: {@code localAgentSession} decided to place again, for Claude Code, the composer's questions it
     * does not know ({@code draftRefs}: drafted for the in-app assistant). The performer composes each again from what it
     * composed before and reports {@code LocalAgentDraftComposed}; the placement replaces the old draft. Answered by
     * {@link SessionEvents.DraftPlaced}.
     */
    record RecomposeLocalAgentDraftsEffect(long opId, java.util.List<Long> draftRefs) implements SessionEffects {
        public RecomposeLocalAgentDraftsEffect {
            draftRefs = java.util.List.copyOf(draftRefs == null ? java.util.List.of() : draftRefs);
        }
    }

    /** The same for the in-app composer: in-app transcript entry {@code draftRef}, appended and highlighted. */
    record PrimeAssistantDraftEffect(long opId, long draftRef, boolean highlight, long logGeneration)
            implements SessionEffects {
        public PrimeAssistantDraftEffect(long opId, long draftRef, boolean highlight) {
            this(opId, draftRef, highlight, 0);
        }
    }

    /**
     * S1 (§5.8, r8 F13): open the sample project and play the shipped "DEMO first breach" journey through the walk path —
     * no model, no key, no network, nothing sent.
     */
    record PlayDemoJourneyEffect(long opId, long request) implements SessionEffects {
    }

    /**
     * Run start-page entry {@code entryId}'s own flow, the person's: S3's chooser and verification, S5's chooser and
     * load, S4's catalogue fetch, S2's New project…. Answered at once by {@link SessionEvents.StartPageEffectStarted};
     * later {@link SessionEvents.StartPageFlowDone} (not for S2, whose dialog is its own flow).
     */
    record RunStartPageFlowEffect(long opId, long request, String entryId) implements SessionEffects {
    }

    /**
     * Robot re-walk #3: the start-page question held for want of a log continues — the person chose Ask anyway, or a log
     * has opened for it. The performer presses it again as asked ({@code entryId}, the box's words by {@code textRef}),
     * acknowledged, reading what can answer now. Answered at once by {@link SessionEvents.StartPageEffectStarted}.
     */
    record ContinueStartPagePromptEffect(long opId, long request, String entryId, long textRef) implements SessionEffects {
    }

    /** Nothing can answer: show entry {@code entryId}'s no-AI fallback buttons (§5.8's last column), saying why. */
    record ShowStartPageFallbackEffect(long opId, long request, String entryId, String why) implements SessionEffects {
    }

    /**
     * Compose the start page's draft — entry {@code entryId} after its flow, with the person's words ({@code textRef})
     * appended — in {@code target}'s store,
     * and report it as {@link SessionEvents.LocalAgentDraftComposed} or {@link SessionEvents.AssistantDraftComposed}.
     * {@code chooseMode}: the mode in force could not answer and {@code target} can, so the person's button chose it
     * (OD-74.23). {@code popOut}: no workspace is open, so the assistant is shown in its own window (OD-74.21).
     */
    record ComposeStartPageDraftEffect(long opId, long request, String entryId, AssistantMode target, boolean chooseMode, long logGeneration, long bundleNameRef,
                                       long templateNamesRef, long textRef, boolean popOut) implements SessionEffects {
    }

    /**
     * Review M8: the person said yes to {@code ConfirmLocalAgentFolderEffect{request}} and the node accepted it: record,
     * on this machine, the folder that dialog named. The performer writes what it showed; it decides nothing.
     */
    record TrustLocalAgentFolderEffect(long opId, long request) implements SessionEffects {
    }

    /**
     * Review H4: make sure a listener the hosted bridge can reach is up for {@code generation} — the REST server when
     * external clients are allowed, otherwise a HOSTED-ONLY server that writes no well-known file and serves only hosted
     * credentials. Answered with {@link SessionEvents.LocalAgentBridgeServed}.
     */
    record ServeHostedBridgeEffect(long opId, long generation, boolean external) implements SessionEffects {
        /** Review B5: {@code external} — whether the listener serves external clients — is actionListener's decision. */
        public ServeHostedBridgeEffect(long opId, long generation) {
            this(opId, generation, false);
        }
    }

    /**
     * Review H4: no Claude Code process needs the bridge any more. Review B5: whether the listener stays up
     * ({@code keepListening}: the REST switch is on) and serves external clients is actionListener's decision; the
     * performer only makes the listener match. {@code closedForSession} (final review #6, rule 9): whether external
     * access stays closed for a session — actionListener's choice too (false: the session that closed it has ended), so
     * the performer sets no value of its own.
     */
    record ReleaseHostedBridgeEffect(long opId, long generation, boolean keepListening, boolean external,
                                     boolean closedForSession) implements SessionEffects {
        public ReleaseHostedBridgeEffect(long opId, long generation) {
            this(opId, generation, false, false, false);
        }
    }

    /**
     * Review B5 (2026-10-17): make the action listener match {@code listen} (up or not) and {@code external} (open to
     * external clients, with the well-known endpoint file, or not) — decided by actionListener; answered with
     * {@link SessionEvents.ActionListenerConfigured}. {@code closedForSession} (review M74 usability #11): external
     * clients are refused because the person closed external access while an in-app Claude Code session runs, not
     * because the switch is off — the refusal they are sent says which.
     */
    record ConfigureActionListenerEffect(long opId, boolean listen, boolean external, boolean closedForSession)
            implements SessionEffects {
        public ConfigureActionListenerEffect(long opId, boolean listen, boolean external) {
            this(opId, listen, external, false);
        }
    }

    /** Review M6: the node's verdict on hosted call {@code call}: {@code refusal} null admits it. */
    record AnswerHostedCallEffect(long opId, long call, String refusal, long ticket) implements SessionEffects {
        public AnswerHostedCallEffect(long opId, long call, String refusal) { this(opId, call, refusal, 0); }
    }

    // ---- M74 P3: permission prompts in the analyser (spec §5.7, §8.3) --------------------------------------------------

    /**
     * Show the permission dialog for {@code generation}'s request {@code requestRef} (its words are in the store): one
     * modal at a time, Deny the default, a visible countdown from {@code deadlineMs}. The person's buttons are reported as
     * {@link SessionEvents.LocalAgentPermissionChosen}; the dialog closes only on {@link ClosePermissionDialogEffect}.
     */
    record ShowPermissionDialogEffect(long opId, long generation, long requestRef, long deadlineMs,
                                      SessionEvents.ToolClass toolClass, String name) implements SessionEffects {
    }

    /** Close the permission dialog for {@code requestRef}, if it is still shown. */
    record ClosePermissionDialogEffect(long opId, long requestRef) implements SessionEffects {
    }

    /**
     * Answer the CLI's request {@code requestRef} of {@code generation}: {@code allow} writes {@code behavior:"allow"} with
     * {@code updatedInput} = the stored input, byte for byte; otherwise {@code behavior:"deny"} with {@code message}.
     * Nothing else — never {@code updatedPermissions}. {@code by} decides only the tool line's mark. {@code grantId}
     * non-zero: the adapter's clock reports {@link SessionEvents.LocalAgentGrantExpired} 60 s from now.
     */
    record AnswerPermissionEffect(long opId, long generation, long ticket, long requestRef, boolean allow, String message,
                                  SessionEvents.PermissionBy by, long grantId, SessionEvents.ToolClass toolClass,
                                  String name) implements SessionEffects {
    }

    /** Start the 120 s clock of request {@code requestRef}: {@link SessionEvents.LocalAgentPermissionDeadline} then. */
    record StartPermissionClockEffect(long opId, long generation, long requestRef, long deadlineMs) implements SessionEffects {
    }

    // ---- M74 P2b: propose onboarding (spec §5.6) ------------------------------------------------------------------------

    /** One item to preview or apply: the node's id and what Claude named. */
    record ProposalItemRef(String itemId, String kind, long pathRef, long classNameRef) {
    }

    /**
     * Check every item read-only, off the event thread (§5.6 step 5), relative to the set-up directory, and report each
     * as {@link SessionEvents.ProposalItemPreviewed}, then {@link SessionEvents.ProposalPreviewDone}.
     */
    record PreviewProposalEffect(long opId, String proposalId, java.util.List<ProposalItemRef> items) implements SessionEffects {
        public PreviewProposalEffect {
            items = java.util.List.copyOf(items);
        }
    }

    /**
     * Apply ONE item through the person's own path for its kind, with person origin (§5.6 step 6). The id {@code create}
     * is the analyser's own row: an empty project in {@code setupDir} through M73's person path. Answered with
     * {@link SessionEvents.ProposalItemApplied}; the next is asked for only after it.
     */
    record ApplyProposalItemEffect(long opId, String proposalId, ProposalItemRef item, long setupDirRef)
            implements SessionEffects {
    }

    /** After the last item: the analyser's own verdicts (pairing, graph–log fit), as {@link SessionEvents.ProposalChecked}. */
    record CheckProposalOutcomeEffect(long opId, String proposalId) implements SessionEffects {
    }

    // ---- M74 P2b: a project switch while Claude Code runs ----------------------------------------------------------------

    /**
     * Show the offer (never a modal from a model call: it answers the person's own switch): "Stop, and get Start ready in
     * the new project" / "Keep it running here" / Cancel. Answered with {@link SessionEvents.LocalAgentSwitchChosen}.
     */
    record OfferLocalAgentSwitchEffect(long opId, long request, String currentName, String targetName)
            implements SessionEffects {
    }

    // ---- M74 P2b: prompt-pack example entries -----------------------------------------------------------------------------

    /**
     * Ask the person, showing the exact URL, the publisher (host / owner / repo @ short commit, marked third-party unless
     * it is Telamin's), the pinned commit, the integrity digest when the entry has one, and a target folder they choose.
     * Answered with {@link SessionEvents.ExampleDownloadConfirmed}.
     */
    record ConfirmExampleDownloadEffect(long opId, long request,
                                        telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource source,
                                        boolean thirdParty) implements SessionEffects {
    }

    /** Fetch {@code url} over https from an allowed host, at most {@code maxBytes}; then {@link SessionEvents.ExampleFetched}. */
    record FetchExampleEffect(long opId, long request, String url, long maxBytes) implements SessionEffects {
    }

    /** Unpack {@code bytesRef} into {@code targetDir} through TemplateArchive, no executable bits; then {@link SessionEvents.ExampleUnpacked}. */
    record UnpackExampleEffect(long opId, long request, long bytesRef, long targetRef) implements SessionEffects {
    }

    /** Drop the fetched bytes (refused digest, cancelled, or after the unpack). */
    record DiscardExampleBytesEffect(long opId, long request, long bytesRef) implements SessionEffects {
    }

    /**
     * Review S7 (P2, 2026-10-17): start the deadline for {@code phase} (FETCHING, UNPACKING) of download {@code request};
     * when it passes, {@link SessionEvents.ExampleDeadlinePassed} — the node decides whether it still matters.
     */
    record StartExampleClockEffect(long opId, long request, String phase, long deadlineMs) implements SessionEffects {
    }

    /** Open the M73 project offer for {@code root} (the person's New project… path); the person confirms. */
    record OfferExampleProjectEffect(long opId, long request, long rootRef) implements SessionEffects {
    }

    // ---- Option 2 (owner, 2026-10-18): a project from a starter template — starterProject decides, these perform ------

    /**
     * Read the starter catalogue from the configured origin, and each entry's template file for its authoring and host;
     * answer {@link SessionEvents.StarterCatalogueObserved}. {@code purpose}: {@code menu}, {@code session} (a hosted
     * session started) — never {@code context}, which renders what was read and fetches nothing.
     */
    record ReadStarterCatalogueEffect(long opId, long request, String purpose) implements SessionEffects {
    }

    /** The menu's picker over the catalogue read for {@code request} ({@code entriesRef}); answered by {@link SessionEvents.StarterChosen}. */
    record ChooseStarterEffect(long opId, long request, long entriesRef) implements SessionEffects {
    }

    /** Read the chosen entry's template file for its defaults; answer {@link SessionEvents.StarterDefaultsObserved}. */
    record ReadStarterDefaultsEffect(long opId, long request, long entriesRef, String idDigest) implements SessionEffects {
    }

    /** The menu's own form (names, folder, guide links); answered by {@link SessionEvents.StarterConfigured}. */
    record ConfigureStarterEffect(long opId, long request, long entriesRef, String idDigest, long defaultsRef)
            implements SessionEffects {
    }

    /** Observe the folder the person chose for the starter row; answer {@link SessionEvents.StarterDirectoryObserved}. */
    record ObserveStarterDirectoryEffect(long opId, long request, long dirRef, String dirShown) implements SessionEffects {
    }

    /**
     * After the person's Apply (or the menu's Download and open): download the starter from the configured HTTPS origin
     * with the names given — the menu's form by reference ({@code namesRef}), or a proposal's own optional names
     * ({@code artifactRef}, {@code groupRef}, {@code basePackageRef}; 0 takes the template's default) — and unpack it into
     * {@code dirRef} through the template archive's containment checks. Nothing is built or run. Answered by
     * {@link SessionEvents.StarterInstalled} or {@link SessionEvents.StarterInstallFailed}.
     */
    record DownloadStarterEffect(long opId, long request, long entriesRef, String idDigest, long namesRef,
                                 long artifactRef, long groupRef, long basePackageRef, long defaultsRef, long dirRef,
                                 boolean referenceGuide) implements SessionEffects {
    }

    /**
     * Write the starter's skills — the canonical set for its authoring and host and, for Spring, the pinned playground
     * design skill and contract — from the analyser's own jar under {@code <root>/.claude/skills/}, never overwriting a
     * file; answer {@link SessionEvents.SkillsVendored}. {@code commandsRef} is the starter's first-run record (the
     * post-install command list; 0 when none) and {@code keyNeed} the template's key declaration: after the pins are
     * checked, every skill marker those two can answer ({@code add-a-node}'s regeneration, the run skills' start
     * command) is substituted from them, and every other marker is kept — all of them when there is no record.
     */
    record VendorSkillsEffect(long opId, long request, long rootRef, String authoring, String host, long commandsRef,
                              String keyNeed) implements SessionEffects {
    }

    /** Day-two discovery in the new project; answer {@link SessionEvents.StarterDiscoveryObserved}. */
    record RunDiscoveryEffect(long opId, long request, long rootRef) implements SessionEffects {
    }

    /**
     * The ONE combined review: the starter profile's declared roots and processor, the discovered skills and graphs,
     * every row at No except the Spring design's folder when {@code designDefaultYes} (owner, 2026-10-18: it is inside
     * the project just created, and needed to show the design); answered by {@link SessionEvents.StarterReviewAnswered}.
     */
    record ReviewStarterProjectEffect(long opId, long request, long rootRef, long offerRef, boolean profilePresent,
                                      java.util.List<String> vendored, boolean designDefaultYes)
            implements SessionEffects {
        public ReviewStarterProjectEffect {
            vendored = java.util.List.copyOf(vendored == null ? java.util.List.of() : vendored);
        }
    }

    /**
     * Open the project through the person's own path: the starter's profile with {@code OpenProjectRequested}
     * ({@code EXPLICIT_SWITCH}), or — with no profile — M73's create with the selection; then, when the review said so,
     * the Spring design's folder as a source root (the person's Sources path) and the chosen graph. Answered
     * by {@link SessionEvents.StarterProjectOpened}.
     */
    record OpenStarterProjectEffect(long opId, long request, long rootRef, long offerRef, boolean profilePresent,
                                    SessionEvents.CreateSelection selection, long graphRef, boolean designRoot)
            implements SessionEffects {
    }

    /** Open the project's Spring design (M66) at once; answered by {@link SessionEvents.StarterDesignOpened}. */
    record OpenStarterDesignEffect(long opId, long request, long rootRef) implements SessionEffects {
    }

    /** Cancel only this starter's outstanding work; do not remove files or erase a person's draft. */
    record CancelStarterWorkEffect(long opId, long request) implements SessionEffects { }

    /**
     * Place the pointer-only draft for Claude Code — the project and the starter by name, the files to read — and report
     * it as {@link SessionEvents.LocalAgentDraftComposed}; nothing is sent until the person presses Send.
     */
    record ArmStarterDraftEffect(long opId, long request, long rootRef, long entriesRef, String idDigest)
            implements SessionEffects {
    }

    /** The menu's ending: the starter's own first-run commands, shown to copy, never run. */
    record ShowStarterCommandsEffect(long opId, long request, long rootRef, long commandsRef) implements SessionEffects {
    }
}
