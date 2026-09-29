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
    record LoadProfileEffect(long opId, String profilePath) implements SessionEffects {
    }

    /** Verify and unpack a bundle off the event thread; report the result with the same operation id. */
    record PrepareBundleEffect(long opId, String bundlePath) implements SessionEffects { }

    /** Open the verified graph and log after the bundled project has been applied. */
    record OpenBundleEvidenceEffect(long opId, SessionEvents.BundlePlan plan) implements SessionEffects { }

    /**
     * Start a new, empty project at this path and make it the loaded one.
     *
     * <p>Distinct from {@link LoadProfileEffect} rather than a flag on it, because it is a different
     * act with a different failure: loading a profile that is not there is an error, and creating one
     * where a project already exists would be destruction. An adapter told to "load, and create if
     * missing" would have to decide which — and adapters do not decide.
     */
    record CreateProfileEffect(long opId, String profilePath) implements SessionEffects {
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
    record OpenLogEffect(long opId, String location, String format, String provenance, boolean fromSocket)
            implements SessionEffects {
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
}
