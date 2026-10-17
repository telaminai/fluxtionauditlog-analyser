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
                                         boolean includeRecordContext, int maxActionsPerReply)
            implements SessionEffects {
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

    /** Write transcript entry {@code draft} to the CLI as one user line, with a freshly minted uuid for {@code ticket}. */
    record SendLocalAgentTurnEffect(long opId, long generation, long ticket, long draft) implements SessionEffects {
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
    record PrimeLocalAgentDraftEffect(long opId, long draftRef, boolean highlight, String armedWhy)
            implements SessionEffects {
    }

    /** The same for the in-app composer: in-app transcript entry {@code draftRef}, appended and highlighted. */
    record PrimeAssistantDraftEffect(long opId, long draftRef, boolean highlight) implements SessionEffects {
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
    record ServeHostedBridgeEffect(long opId, long generation) implements SessionEffects {
    }

    /**
     * Review H4: no Claude Code process needs the bridge any more; a hosted-only server is stopped (the external REST
     * server, when it is on, is the person's and stays).
     */
    record ReleaseHostedBridgeEffect(long opId, long generation) implements SessionEffects {
    }

    /** Review M6: the node's verdict on hosted call {@code call}: {@code refusal} null admits it. */
    record AnswerHostedCallEffect(long opId, long call, String refusal) implements SessionEffects {
    }

    // ---- M74 P2b: propose onboarding (spec §5.6) ------------------------------------------------------------------------

    /** One item to preview or apply: the node's id and what Claude named. */
    record ProposalItemRef(String itemId, String kind, String path, String className) {
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
    record ApplyProposalItemEffect(long opId, String proposalId, ProposalItemRef item, String setupDir)
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
    record UnpackExampleEffect(long opId, long request, long bytesRef, String targetDir) implements SessionEffects {
    }

    /** Drop the fetched bytes (refused digest, cancelled, or after the unpack). */
    record DiscardExampleBytesEffect(long opId, long request, long bytesRef) implements SessionEffects {
    }

    /** Open the M73 project offer for {@code root} (the person's New project… path); the person confirms. */
    record OfferExampleProjectEffect(long opId, long request, String root) implements SessionEffects {
    }
}
