package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in for {@code MainFrame} / {@code ProjectSession}: it performs effects against an in-memory
 * world and reports what happened.
 *
 * <p><b>It records what it was ASKED and what it DID separately</b>, because that is the distinction
 * the replay tests exist to check. A test asserting only on {@link #performed} would prove the
 * processor emitted a request; asserting on {@link #logClosed} proves the close happened. The whole of
 * M44 §0 is the gap between those two lists.
 *
 * <p>It contains no policy, deliberately. Every {@code perform} branch does what it is told, including
 * when what it is told looks wrong — an adapter that second-guessed a decision would hide exactly the
 * defects these tests are looking for.
 */
final class FakeSessionAdapter implements SessionDriver.Adapter {

    /** Profiles that exist, by path. Anything else fails to load. */
    private final List<String> loadable = new ArrayList<>();

    final List<SessionEffects> performed = new ArrayList<>();
    /** M69: what walkPlayback asked the adapter to do, in order. */
    final List<SessionEffects.ApplyWalkViewEffect> walkViews = new ArrayList<>();
    final List<SessionEffects.LightWalkTargetsEffect> walkLights = new ArrayList<>();
    final List<SessionEffects.ResolveWalkTargetsEffect> walkResolves = new ArrayList<>();
    final List<SessionEffects.SetFollowEffect> followSets = new java.util.ArrayList<>();
    final List<SessionEffects.CaptureBundleEffect> captures = new java.util.ArrayList<>();
    final List<SessionEffects.DeleteBundleEffect> deletes = new java.util.ArrayList<>();
    final List<SessionEffects.RememberBundleAnchorEffect> anchorRemembers = new java.util.ArrayList<>();
    final List<SessionEffects.RestoreBundleAnchorEffect> anchorRestores = new java.util.ArrayList<>();
    final List<SessionEffects.OfferProjectReopenEffect> reopenOffers = new java.util.ArrayList<>();
    final List<SessionEffects.EndWalkEffect> walkEnds = new ArrayList<>();
    /** OA-1: what assistantLoop asked the adapter to do, in order. */
    final List<SessionEffects.PrepareAssistantContextEffect> assistantContexts = new ArrayList<>();
    final List<SessionEffects.RequestAssistantCompletionEffect> assistantRequests = new ArrayList<>();
    final List<SessionEffects.RunAssistantActionEffect> assistantActions = new ArrayList<>();
    final List<SessionEffects.CancelAssistantTransportEffect> assistantCancels = new ArrayList<>();
    final List<SessionEffects.ShowAssistantHostEffect> assistantHosts = new ArrayList<>();
    /** The verb each action entry names, as the real adapter would read it from the block. */
    final java.util.Map<Long, String> assistantVerbs = new java.util.HashMap<>();
    /** M74: what localAgentSession asked the adapter to do, in order. */
    final List<SessionEffects.StartLocalAgentEffect> agentStarts = new ArrayList<>();
    final List<SessionEffects.PollLocalAgentStatusEffect> agentPolls = new ArrayList<>();
    final List<SessionEffects.SendLocalAgentTurnEffect> agentSends = new ArrayList<>();
    final List<SessionEffects.StopLocalAgentEffect> agentStops = new ArrayList<>();
    final List<SessionEffects.ConfirmLocalAgentFolderEffect> agentConfirms = new ArrayList<>();
    /** M74 P2: what the Ask catalogue and the start page asked the adapter to do, in order. */
    final List<SessionEffects.PrimeLocalAgentDraftEffect> agentPrimes = new ArrayList<>();
    final List<SessionEffects.PrimeAssistantDraftEffect> assistantPrimes = new ArrayList<>();
    final List<SessionEffects.PlayDemoJourneyEffect> demos = new ArrayList<>();
    final List<SessionEffects.RunStartPageFlowEffect> startFlows = new ArrayList<>();
    final List<SessionEffects.ShowStartPageFallbackEffect> startFallbacks = new ArrayList<>();
    final List<SessionEffects.ComposeStartPageDraftEffect> startComposes = new ArrayList<>();
    /** Set to have the fake refuse a walk step's view. */
    boolean refuseWalkViews;

    boolean logClosed;
    boolean graphClosed;
    boolean settingsRestored;
    String appliedProfile;
    String lastStatus;
    String lastWarning;

    /** Set to have the very next load throw rather than return a failure result. */
    boolean loadThrows;
    /** M73: set to have the very next create throw, as a failed CREATE_NEW write does (G2). */
    boolean createThrows;
    /** M73: what sessionBoundary asked to be created, with the approved selection. */
    final List<SessionEffects.CreateProfileEffect> creates = new ArrayList<>();
    /** M73: the "Open it instead?" offers sessionBoundary asked for. */
    final List<SessionEffects.OfferOpenExistingEffect> openExistingOffers = new ArrayList<>();
    /**
     * Set to model a restore whose RENDER fails. project.close() has already happened, so the project IS
     * gone and SettingsRestored is still reported; only the window did not finish updating.
     */
    boolean restoreRenderThrows;
    /** Whether that render failure happened, so a test can assert the adapter still told the truth. */
    boolean restoreRenderFailed;
    /**
     * Set to model an apply whose RENDER fails. The real adapter swaps the settings in ProjectSession before
     * the effect runs, so the profile IS in force and ProfileApplied is still reported; only the window did
     * not finish updating. Reporting a failure here instead is what let the session believe the old project
     * was still active (review 2026-09-29).
     */
    boolean applyRenderThrows;
    /** Whether that render failure happened, so a test can assert the adapter still told the truth. */
    boolean applyRenderFailed;
    /** Set to abort the batch mid-apply with a protocol violation — no ProfileApplied, no EffectFailed. */
    boolean applyViolates;
    /** M44.3: when true, an OpenLogEffect answers Pending (the real adapter's shape); else it lands at once. */
    boolean pendingOpens;
    /** What a synchronous open reports as logged node ids, keyed by location. */
    final java.util.Map<String, java.util.Set<String>> openable = new java.util.HashMap<>();
    String closedGraphPath;

    FakeSessionAdapter withProfile(String path) {
        loadable.add(path);
        return this;
    }

    @Override
    public SessionEvents.Result perform(SessionEffects effect) throws Exception {
        performed.add(effect);
        return switch (effect) {
            case SessionEffects.PrepareBundleEffect e -> new SessionEvents.Pending(e.opId(), "verifying " + e.bundlePath());
            case SessionEffects.ShowProjectReopenEffect e -> new SessionEvents.ProjectReopenPresented(e.opId(), true);
            case SessionEffects.OpenBundleEvidenceEffect e -> new SessionEvents.Pending(e.opId(), "opening " + e.plan().logPath());
            case SessionEffects.ScanLogEvidenceEffect e -> new SessionEvents.ScanScheduled(e.opId(), e.generation());
            // M69: the recording backend for walk playback — what the node ASKED, and a plain answer
            case SessionEffects.ApplyWalkViewEffect e -> {
                walkViews.add(e);
                yield new SessionEvents.WalkViewApplied(e.opId(), e.ticket(), !refuseWalkViews, refuseWalkViews ? "refused by the fake" : "");
            }
            case SessionEffects.LightWalkTargetsEffect e -> {
                walkLights.add(e);
                yield new SessionEvents.WalkTargetsLit(e.opId(), e.ticket(), e.targets().size(), "");
            }
            case SessionEffects.ResolveWalkTargetsEffect e -> {
                walkResolves.add(e);
                yield new SessionEvents.WalkAcknowledged(e.opId(), e.ticket(), "resolve");
            }
            case SessionEffects.EndWalkEffect e -> {
                walkEnds.add(e);
                yield new SessionEvents.WalkAcknowledged(e.opId(), e.ticket(), "end");
            }
            case SessionEffects.SetFollowEffect e -> {
                followSets.add(e);
                yield new SessionEvents.FollowSet(e.opId(), e.ticket(), e.on());
            }
            case SessionEffects.CaptureBundleEffect e -> {
                captures.add(e);
                yield new SessionEvents.CaptureStarted(e.opId(), e.ticket());
            }
            // OA-1: the assistant's effects are recorded, answered at once, and their outcomes posted by the test
            case SessionEffects.PrepareAssistantContextEffect e -> {
                assistantContexts.add(e);
                yield new SessionEvents.AssistantEffectStarted(e.opId(), e.ticket(), "prepareContext");
            }
            case SessionEffects.RequestAssistantCompletionEffect e -> {
                assistantRequests.add(e);
                yield new SessionEvents.AssistantEffectStarted(e.opId(), e.ticket(), "requestCompletion:" + e.round());
            }
            case SessionEffects.RunAssistantActionEffect e -> {
                assistantActions.add(e);
                yield new SessionEvents.AssistantEffectStarted(e.opId(), e.ticket(),
                        "action:" + assistantVerbs.getOrDefault(e.action(), "?"));
            }
            case SessionEffects.CancelAssistantTransportEffect e -> {
                assistantCancels.add(e);
                yield new SessionEvents.AssistantEffectStarted(e.opId(), e.ticket(), "cancelTransport");
            }
            case SessionEffects.ShowAssistantHostEffect e -> {
                assistantHosts.add(e);
                yield new SessionEvents.AssistantHostShown(e.opId(), e.docked(), true, "");
            }
            // M74: the local agent's effects are recorded and answered at once; the test posts the CLI's facts
            case SessionEffects.StartLocalAgentEffect e -> {
                agentStarts.add(e);
                yield new SessionEvents.LocalAgentEffectStarted(e.opId(), e.generation(), "start");
            }
            case SessionEffects.PollLocalAgentStatusEffect e -> {
                agentPolls.add(e);
                yield new SessionEvents.LocalAgentEffectStarted(e.opId(), e.generation(), "poll");
            }
            case SessionEffects.SendLocalAgentTurnEffect e -> {
                agentSends.add(e);
                yield new SessionEvents.LocalAgentEffectStarted(e.opId(), e.generation(), "send");
            }
            case SessionEffects.StopLocalAgentEffect e -> {
                agentStops.add(e);
                yield new SessionEvents.LocalAgentEffectStarted(e.opId(), e.generation(), "stop:" + e.how());
            }
            case SessionEffects.ConfirmLocalAgentFolderEffect e -> {
                agentConfirms.add(e);
                yield new SessionEvents.LocalAgentEffectStarted(e.opId(), 0, "confirmFolder");
            }
            case SessionEffects.PrimeLocalAgentDraftEffect e -> {
                agentPrimes.add(e);
                yield new SessionEvents.DraftPlaced(e.opId(), e.draftRef(), true, "");
            }
            case SessionEffects.PrimeAssistantDraftEffect e -> {
                assistantPrimes.add(e);
                yield new SessionEvents.DraftPlaced(e.opId(), e.draftRef(), true, "");
            }
            case SessionEffects.PlayDemoJourneyEffect e -> {
                demos.add(e);
                yield new SessionEvents.StartPageEffectStarted(e.opId(), e.request(), "demo");
            }
            case SessionEffects.RunStartPageFlowEffect e -> {
                startFlows.add(e);
                yield new SessionEvents.StartPageEffectStarted(e.opId(), e.request(), "flow:" + e.entryId());
            }
            case SessionEffects.ShowStartPageFallbackEffect e -> {
                startFallbacks.add(e);
                yield new SessionEvents.StartPageEffectStarted(e.opId(), e.request(), "fallback:" + e.entryId());
            }
            case SessionEffects.ComposeStartPageDraftEffect e -> {
                startComposes.add(e);
                yield new SessionEvents.StartPageEffectStarted(e.opId(), e.request(), "compose:" + e.entryId());
            }
            case SessionEffects.DeleteBundleEffect e -> {
                deletes.add(e);
                yield new SessionEvents.BundleDeleted(e.opId(), e.ticket(), true, null);
            }
            case SessionEffects.LoadProfileEffect e -> {
                if (loadThrows) {
                    loadThrows = false;
                    throw new java.io.IOException("disk went away");
                }
                boolean ok = loadable.contains(e.profilePath());
                yield new SessionEvents.ProfileLoaded(e.opId(), e.profilePath(), ok,
                        ok ? nameOf(e.profilePath()) : null, 0,
                        ok ? null : "no such profile: " + e.profilePath(), null, e.expectedDigest());
            }
            case SessionEffects.CreateProfileEffect e -> {
                creates.add(e);
                if (createThrows) {
                    createThrows = false;
                    throw new java.io.IOException("DEMO the profile could not be written");
                }
                loadable.add(e.profilePath());
                yield new SessionEvents.ProfileLoaded(e.opId(), e.profilePath(), true,
                        nameOf(e.profilePath()), 0, null);
            }
            case SessionEffects.ApplyProfileEffect e -> {
                appliedProfile = e.profilePath();
                if (applyViolates) {
                    applyViolates = false;
                    throw new SessionDriver.ProtocolViolation("DEMO the batch was aborted mid-apply");
                }
                if (applyRenderThrows) {
                    applyRenderThrows = false;
                    applyRenderFailed = true;      // the settings are in force regardless; say so
                }
                yield new SessionEvents.ProfileApplied(e.opId(), e.profilePath(), e.name());
            }
            case SessionEffects.RestoreSettingsEffect e -> {
                if (restoreRenderThrows) {
                    restoreRenderThrows = false;
                    restoreRenderFailed = true;      // the project is gone regardless; say so
                }
                settingsRestored = true;
                appliedProfile = null;
                yield new SessionEvents.SettingsRestored(e.opId());
            }
            case SessionEffects.CloseLogEffect e -> {
                logClosed = true;
                yield new SessionEvents.LogClosed(e.opId());
            }
            case SessionEffects.CloseGraphEffect e -> {
                graphClosed = true;
                closedGraphPath = e.graphPath();
                yield new SessionEvents.GraphClosed(e.opId());
            }
            case SessionEffects.OpenLogEffect e -> {
                if (pendingOpens) {
                    yield new SessionEvents.Pending(e.opId(), "opening " + e.location());
                }
                java.util.Set<String> ids = openable.getOrDefault(e.location(), java.util.Set.of());
                yield new SessionEvents.LogOpened(e.opId(), e.location(), e.provenance(), ids, ids.size(), ids.size(), null);
            }
            case SessionEffects.OfferOpenExistingEffect e -> {
                openExistingOffers.add(e);
                yield new SessionEvents.StatusShown(e.opId(), "offerOpenExisting");
            }
            case SessionEffects.OfferProjectReopenEffect e -> {
                reopenOffers.add(e);
                yield new SessionEvents.StatusShown(e.opId(), "offerProjectReopen");
            }
            case SessionEffects.RememberBundleAnchorEffect e -> {
                anchorRemembers.add(e);
                yield new SessionEvents.StatusShown(e.opId(), "rememberBundleAnchor");
            }
            case SessionEffects.RestoreBundleAnchorEffect e -> {
                anchorRestores.add(e);
                yield new SessionEvents.StatusShown(e.opId(), "restoreBundleAnchor");
            }
            case SessionEffects.ShowStatusEffect e -> {
                lastStatus = e.text();
                yield new SessionEvents.StatusShown(e.opId(), "showStatus");
            }
            case SessionEffects.ShowWarningEffect e -> {
                lastWarning = e.text();
                yield new SessionEvents.StatusShown(e.opId(), "showWarning");
            }
        };
    }

    /** How many effects of this type were performed — the count, not merely "at least one". */
    long countOf(Class<? extends SessionEffects> type) {
        return performed.stream().filter(type::isInstance).count();
    }

    void forget() {
        performed.clear();
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        return file.endsWith(".properties") ? file.substring(0, file.length() - 11) : file;
    }
}
