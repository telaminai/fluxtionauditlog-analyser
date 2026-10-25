package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition;
import telamin.fluxtion.audit.analyser.analyser.session.resume.ResumeEvents;
import telamin.fluxtion.audit.analyser.analyser.session.resume.SessionResumeStore;
import java.util.List;
import java.util.Set;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FirstMinuteTest {
    @Test void shippedCommentaryIsLabelledBundledRatherThanPersonOrLiveAssistant() {
        assertEquals("bundled commentary", DiamondDefinition.walk().authorLabel(), "the shipped author is disclosed as bundled");
    }
    @Test void aReelCannotWeakenTheShippedWalksWholeRecordClaim() {
        Run r = playing();
        r.driver.post(new SessionEvents.WalkPlayRequested(0, DiamondDefinition.walk(), 2, "reel"));
        var w = r.driver.snapshot().walkPlayback();
        assertEquals("reel", w.origin(), "the recorder really took over pacing");
        var targets = new java.util.ArrayList<SessionEvents.WalkTargetState>();
        var named = w.definition().steps().get(2).targets();
        for (int i = 0; i < named.size(); i++) targets.add(new SessionEvents.WalkTargetState(i + 1,
                named.get(i).target(), named.get(i).caption(), i == 0 ? "UNRESOLVED" : "CURRENT",
                i != 0, i == 0 ? "record changed" : ""));
        r.driver.post(new SessionEvents.WalkStepPrepared(w.ticket(), r.driver.snapshot().logGeneration(), targets, ""));
        assertTrue(r.driver.snapshot().walkPlayback().targets().stream().allMatch(t -> !t.available() && t.caption().isEmpty()),
                "reel takeover must withhold every caption when the shipped record is not current");
        var light = r.adapter.performed.stream().filter(SessionEffects.LightWalkTargetsEffect.class::isInstance)
                .map(SessionEffects.LightWalkTargetsEffect.class::cast).reduce((a,b) -> b).orElseThrow();
        assertTrue(light.targets().isEmpty(), "no current graph caption reaches the overlay beside a substituted record");
    }
    @Test void readOnlyDesignNavigationPreservesFirstContactAndStartupAdmission() {
        for (String kind : List.of("source", "diagnostics")) {
            Run r = playing(); showStep(r);
            r.driver.post(new SessionEvents.WalkEndRequested("Esc"));
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested(kind));
            assertEquals("DOORS", r.driver.snapshot().firstMinute().phase(), "read-only " + kind + " keeps the doors");
            assertEquals(WalkPlaybackState.Stage.TOPOLOGY, r.driver.snapshot().walkPlayback().stage(),
                    "read-only navigation keeps the first-contact stage");
            Run pending = opening();
            pending.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested(kind));
            assertTrue(pending.driver.processor().firstMinute.permitsOpen(pending.ticket),
                    "a read-only lookup is not a competing open");
        }
    }

    @Test void anAcceptedProducerResultAfterCloseOrCancelledInvestigateRemainsRecoverable() {
        for (boolean investigate : List.of(false, true)) {
            Run r = playing(); showStep(r);
            r.driver.post(new SessionEvents.WalkEndRequested("Esc"));
            if (investigate) r.driver.post(new SessionEvents.FirstMinuteDoorChosen("investigate"));
            else r.driver.post(new SessionEvents.FirstMinuteCardClosed());
            var design = r.driver.processor().designSession;
            var result = new telamin.fluxtion.audit.analyser.analyser.design.ProducerResult(
                    "/demo/result.json", "build", "", "", List.of(), Map.of(), Map.of());
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted(result, "", design.generation() - 1));
            observeCapture(r, 201);
            assertFalse(capture(r).capture(), "a stale result is not personal content");
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted(null, "refused", design.generation()));
            observeCapture(r, 202);
            assertFalse(capture(r).capture(), "a refused result is not personal content");
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ResultReadCompleted(result, "", design.generation()));
            assertSame(result, design.result(), "the session accepted the result");
            observeCapture(r, 203);
            assertTrue(capture(r).capture(), "an accepted producer result after Close or cancelled Investigate must survive recovery");
        }
    }

    @Test void graphAndDesignChoicesLeaveTheFailedLanding() {
        for (boolean design : List.of(false, true)) {
            Run r = opening();
            r.driver.post(new SessionEvents.FirstMinuteOpenFailed(r.ticket, "DEMO failure"));
            assertEquals("FAILED", r.driver.snapshot().firstMinute().phase());
            if (design) r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open"));
            else r.driver.post(SessionFixtures.graph("/demo/chosen.graphml"));
            assertEquals("DONE", r.driver.snapshot().firstMinute().phase(),
                    "a " + (design ? "design" : "graph") + " choice must leave the failed landing");
            assertFalse(r.driver.processor().firstMinute.permitsDoor(r.ticket));
        }
    }

    @Test void anUnverifiedFirstMinuteNeverBecomesAnOrdinaryRecoveryByFailing() {
        for (int failureAt = 0; failureAt < 3; failureAt++) {
            Run r = opening(); land(r, "FIRST_MINUTE");
            long g = r.driver.snapshot().logGeneration();
            r.driver.post(new SessionEvents.FirstMinuteLogReadObserved(r.ticket, g,
                    failureAt == 0 ? "substituted" : DiamondDefinition.LOG_SHA256, null));
            if (failureAt > 0) r.driver.post(new SessionEvents.FirstMinutePresentationObserved(r.ticket, g,
                    failureAt == 1 ? "substituted" : DiamondDefinition.GRAPH_SHA256, DiamondDefinition.DESIGN_SHA256, null));
            if (failureAt == 2) r.driver.post(new SessionEvents.FirstMinuteOpenFailed(r.ticket, "walk unavailable"));
            assertEquals("FAILED", r.driver.snapshot().firstMinute().phase());
            r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(204, g, r.ticket,
                    failureAt == 0 ? "substituted" : DiamondDefinition.LOG_SHA256, false));
            assertFalse(capture(r).capture(), "a failed first-minute generation cannot be saved as an ordinary personal session");
            r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(205, g, r.ticket, null, true));
            assertTrue(capture(r).capture(), "personal additions still survive an unverified launch");
            SessionFixtures.openLog(r.driver, r.adapter, "/demo/ordinary.yaml");
            r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(206, r.driver.snapshot().logGeneration(), 0, null, false));
            assertTrue(capture(r).capture(), "an ordinary replacement keeps normal recovery");
        }
    }

    @Test void startupVocabularyAndFailureFactsNeverCarryPrivatePaths() {
        var observed = new SessionEvents.FirstMinuteStartupObserved(true, 0, 0, 0, false, false, false,
                "/DEMO/private/domain", false);
        assertEquals("generic", observed.rememberedDomain(), "remembered domain is catalogue vocabulary, not arbitrary config text");
        String failure = "unavailable: /DEMO/private/diamond.xml";
        for (String reason : List.of(new SessionEvents.FirstMinuteOpenFailed(1, failure).reason(),
                new SessionEvents.FirstMinuteLogReadObserved(1, 1, null, failure).reason(),
                new SessionEvents.FirstMinutePresentationObserved(1, 1, null, null, failure).reason())) {
            assertFalse(FactWords.holdsPath(reason), "first-minute failure facts strip private paths");
            assertTrue(reason.contains("…/diamond.xml"));
        }
    }
    record Run(SessionDriver driver, FakeSessionAdapter adapter, long ticket, long operation) { }

    static SessionEvents.FirstMinuteStartupObserved startup(int refusalMask) {
        return new SessionEvents.FirstMinuteStartupObserved((refusalMask & 1) == 0,
                (refusalMask & 2) == 0 ? 0 : 1, (refusalMask & 4) == 0 ? 0 : 1,
                (refusalMask & 8) == 0 ? 0 : 1, (refusalMask & 16) != 0, (refusalMask & 32) != 0,
                (refusalMask & 64) != 0, "generic", (refusalMask & 128) != 0);
    }

    static Run opening() {
        var a = new FakeSessionAdapter();
        var d = new SessionDriver(a);
        d.post(new ResumeEvents.Activated(null));
        d.post(startup(0));
        d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(), SessionResumeStore.NO_PROJECT, null, null, null));
        var effect = a.performed.stream().filter(SessionEffects.OpenDiamondEffect.class::isInstance)
                .map(SessionEffects.OpenDiamondEffect.class::cast).findFirst().orElseThrow();
        return new Run(d, a, effect.ticket(), d.nextOpId());
    }

    static void land(Run r, String launch) {
        r.adapter.pendingOpens = true;
        r.driver.post(new SessionEvents.FirstMinuteLaunchAssigned(r.ticket, r.operation));
        assertTrue(r.driver.processor().firstMinute.permitsAssignedLaunch(r.ticket, r.operation), "the reserved operation is owned before it starts");
        r.driver.post(new SessionEvents.OpenLogRequested(r.operation, "/demo/diamond.yaml", null, null, false));
        r.driver.post(new SessionEvents.LogOpened(r.operation, "/demo/diamond.yaml", null, Set.of("a", "b", "c", "d"),
                6, 6, "INFO", null, false, 0, 1, launch, r.ticket));
    }

    static Run playing() {
        Run r = opening();
        land(r, "FIRST_MINUTE");
        long g = r.driver.snapshot().logGeneration();
        r.driver.post(new SessionEvents.FirstMinuteLogReadObserved(r.ticket, g, DiamondDefinition.LOG_SHA256, null));
        r.driver.post(new SessionEvents.FirstMinutePresentationObserved(r.ticket, g, DiamondDefinition.GRAPH_SHA256,
                DiamondDefinition.DESIGN_SHA256, null));
        assertEquals("PLAYING", r.driver.snapshot().firstMinute().phase());
        r.driver.post(new SessionEvents.WalkPlayRequested(0, DiamondDefinition.walk(), 0, "FIRST_MINUTE"));
        return r;
    }

    static void showStep(Run r) {
        var w = r.driver.snapshot().walkPlayback();
        var named = w.definition().steps().get(w.step()).targets();
        var states = new java.util.ArrayList<SessionEvents.WalkTargetState>();
        for (int i = 0; i < named.size(); i++) states.add(new SessionEvents.WalkTargetState(i + 1, named.get(i).target(),
                named.get(i).caption(), "CURRENT", true, ""));
        r.driver.post(new SessionEvents.WalkStepPrepared(w.ticket(), r.driver.snapshot().logGeneration(), states, ""));
    }

    @Test void admissionRequiresEveryInputAndWaitsForRecovery() {
        for (int mask = 0; mask < 256; mask++) {
            var a = new FakeSessionAdapter(); var d = new SessionDriver(a);
            d.post(new ResumeEvents.Activated(null));
            d.post(startup(mask));
            assertFalse(a.performed.stream().anyMatch(SessionEffects.OpenDiamondEffect.class::isInstance), "checking recovery is not a no-offer verdict");
            d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(), SessionResumeStore.NO_PROJECT, null, null, null));
            assertEquals(mask == 0 ? 1 : 0, a.performed.stream().filter(SessionEffects.OpenDiamondEffect.class::isInstance).count(), "startup mask " + mask);
            assertFalse(a.performed.stream().anyMatch(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance), "startup must never mark played");
            d.post(startup(0));
            assertEquals(mask == 0 ? 1 : 0, a.performed.stream().filter(SessionEffects.OpenDiamondEffect.class::isInstance).count(), "startup is observed once");
        }
    }

    @Test void aProjectChosenBeforeRecoverySettlesNeverQualifiesForStartupAutoplay() {
        for (boolean projectBeforeStartup : List.of(false, true)) {
            var a = new FakeSessionAdapter(); var d = new SessionDriver(a);
            a.withProfile("/demo/project/profile");
            d.post(new ResumeEvents.Activated(null));
            if (!projectBeforeStartup) d.post(startup(0));
            d.post(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/demo/project/profile",
                    TransitionKind.EXPLICIT_SWITCH, "person"));
            assertEquals("/demo/project/profile", a.appliedProfile, "the fixture really opened the project");
            if (projectBeforeStartup) d.post(startup(0));
            d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(),
                    SessionResumeStore.NO_PROJECT, null, null, null));
            assertFalse(a.performed.stream().anyMatch(SessionEffects.OpenDiamondEffect.class::isInstance),
                    "a project chosen while startup/recovery was pending must not be overwritten by the diamond");
        }
    }

    @Test void aDesignChosenBeforeRecoverySettlesNeverQualifiesForStartupAutoplay() {
        for (boolean designBeforeStartup : List.of(false, true)) {
            var a = new FakeSessionAdapter(); var d = new SessionDriver(a);
            d.post(new ResumeEvents.Activated(null));
            if (!designBeforeStartup) d.post(startup(0));
            // The person's read is still pending: startup must not replace it before it completes.
            d.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open"));
            if (designBeforeStartup) d.post(startup(0));
            d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(),
                    SessionResumeStore.NO_PROJECT, null, null, null));
            assertFalse(a.performed.stream().anyMatch(SessionEffects.OpenDiamondEffect.class::isInstance),
                    "a design chosen while startup/recovery was pending must not be overwritten by the diamond");
        }
    }

    @Test void aReplacementGraphAfterCloseRemainsRecoverable() throws Exception { replacementAfterDone(false, false); }
    @Test void aReplacementGraphAfterCancelledInvestigateRemainsRecoverable() throws Exception { replacementAfterDone(true, false); }
    @Test void aReplacementDesignAfterCloseRemainsRecoverable() throws Exception { replacementAfterDone(false, true); }
    @Test void aReplacementDesignAfterCancelledInvestigateRemainsRecoverable() throws Exception { replacementAfterDone(true, true); }

    private static void replacementAfterDone(boolean investigate, boolean design) throws Exception {
        Run r = playing(); showStep(r);
        r.driver.post(new SessionEvents.WalkEndRequested("Esc"));
        assertEquals("DOORS", r.driver.snapshot().firstMinute().phase());
        if (investigate) r.driver.post(new SessionEvents.FirstMinuteDoorChosen("investigate"));
        else r.driver.post(new SessionEvents.FirstMinuteCardClosed());
        assertEquals("DONE", r.driver.snapshot().firstMinute().phase());
        observeCapture(r, 100);
        assertFalse(capture(r).capture(), "closing or cancelling the chooser alone leaves the demo untouched");

        if (design) {
            var doc = telamin.fluxtion.audit.analyser.analyser.design.DesignDocument.parse(
                    "/demo/person-chosen.xml", "<beans><bean id=\"chosen\" class=\"com.acme.Chosen\"/></beans>");
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open"));
            long epoch = r.driver.processor().designSession.generation();
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted(
                    doc.file(), doc, "", true, epoch));
            assertEquals(doc.file(), r.driver.processor().designSession.path(), "the replacement really became the design");
        } else r.driver.post(SessionFixtures.graph("/demo/person-chosen.graphml"));
        observeCapture(r, 101);
        assertTrue(capture(r).capture(), "the person's replacement " + (design ? "design" : "graph")
                + " after " + (investigate ? "Investigate then cancel" : "Close") + " must survive recovery");
        assertFalse(r.driver.snapshot().firstMinute().bundled(), "a replacement is no longer the verified bundled presentation");
        assertFalse(r.driver.processor().firstMinute.permitsDoor(r.ticket), "a replacement also retires any deferred first-minute door");
    }

    private static void observeCapture(Run r, long request) {
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(request, r.driver.snapshot().logGeneration(),
                r.ticket, DiamondDefinition.LOG_SHA256, false));
    }

    @Test void theOwnedDesignReadDoesNotCancelPreparationOrInventPersonalContent() throws Exception {
        Run r = playingWithOwnedDesign();
        observeCapture(r, 102);
        assertFalse(capture(r).capture(), "the diamond's own receipt-bearing design is still untouched evidence");
    }

    @Test void aStaleDesignCompletionDoesNotInventPersonalContent() throws Exception {
        Run r = playingWithOwnedDesign();
        var state = r.driver.processor().designSession;
        r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted(
                state.path(), state.document(), "", true, 0));
        observeCapture(r, 103);
        assertFalse(capture(r).capture(), "a refused old design epoch is not a person's replacement");
    }

    @Test void aPlainOpenOfTheSameDesignIsPersonalContent() throws Exception {
        Run r = playingWithOwnedDesign();
        r.driver.post(new SessionEvents.FirstMinuteCardClosed());
        var state = r.driver.processor().designSession;
        var doc = state.document();
        r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open"));
        r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted(
                doc.file(), doc, "", true, state.generation()));
        observeCapture(r, 104);
        assertTrue(capture(r).capture(), "the same pathname and bytes do not grant an ordinary design open the diamond's receipt");
    }

    @Test void aDesignChoiceSupersedesPreparationAndTheShowingWalk() {
        for (boolean showing : List.of(false, true)) {
            Run r = showing ? playing() : opening();
            if (showing) showStep(r);
            r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open"));
            assertEquals("DONE", r.driver.snapshot().firstMinute().phase(), "a design request retires pending first contact");
            assertFalse(r.driver.processor().firstMinute.permitsOpen(r.ticket));
            assertFalse(r.driver.snapshot().walkPlayback().showing(), "a replacement design must not retain the diamond overlay");
            assertEquals(WalkPlaybackState.Stage.RECORDS, r.driver.snapshot().walkPlayback().stage());
        }
    }

    private static Run playingWithOwnedDesign() throws Exception {
        Run r = opening(); land(r, "FIRST_MINUTE");
        long generation = r.driver.snapshot().logGeneration();
        r.driver.post(new SessionEvents.FirstMinuteLogReadObserved(r.ticket, generation, DiamondDefinition.LOG_SHA256, null));
        r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadRequested("open", r.ticket));
        assertTrue(r.driver.processor().firstMinute.permitsPreparation(r.ticket, generation),
                "the diamond's own design request must not supersede its preparation");
        long epoch = r.driver.processor().designSession.generation();
        var doc = telamin.fluxtion.audit.analyser.analyser.design.DesignDocument.parse(
                "/demo/diamond.xml", "<beans><bean id=\"a\" class=\"com.acme.A\"/></beans>");
        r.driver.post(new telamin.fluxtion.audit.analyser.analyser.design.DesignEvents.ReadCompleted(doc.file(), doc, "", true, epoch));
        assertEquals(doc.file(), r.driver.processor().designSession.path());
        r.driver.post(new SessionEvents.FirstMinutePresentationObserved(r.ticket, generation,
                DiamondDefinition.GRAPH_SHA256, DiamondDefinition.DESIGN_SHA256, null));
        assertEquals("PLAYING", r.driver.snapshot().firstMinute().phase());
        return r;
    }

    @Test void cancellingBundlePreparationCannotMakeAnActiveProjectLookLikeFreshStartup() {
        var a = new FakeSessionAdapter().withProfile("/demo/project/profile"); var d = new SessionDriver(a);
        d.post(new ResumeEvents.Activated(null)); d.post(startup(0));
        d.post(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/demo/project/profile",
                TransitionKind.EXPLICIT_SWITCH, "person"));
        d.post(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/demo/pending.fexp",
                TransitionKind.OPEN_BUNDLE, "person"));
        d.post(SessionFixtures.graph("/demo/chosen.graphml"));
        assertEquals(-1, d.processor().operationGate.expectedOpId(), "explicit graph cancels the pending bundle");
        assertEquals("/demo/project/profile", a.appliedProfile, "the earlier project is still active");
        d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(),
                SessionResumeStore.NO_PROJECT, null, null, null));
        assertFalse(a.performed.stream().anyMatch(SessionEffects.OpenDiamondEffect.class::isInstance),
                "a resettable operation id cannot make the active project eligible for autoplay");
    }

    @Test void aGraphChoiceSupersedesExtractionReadingAndPresentationButNotTheOwnedGraph() {
        for (int phase = 0; phase < 3; phase++) {
            Run r = opening();
            if (phase > 0) land(r, "FIRST_MINUTE");
            if (phase > 1) {
                long generation = r.driver.snapshot().logGeneration();
                r.driver.post(new SessionEvents.FirstMinuteLogReadObserved(r.ticket, generation, DiamondDefinition.LOG_SHA256, null));
                r.driver.post(new SessionEvents.GraphOpened("/demo/diamond.graphml", "OPENED", Set.of("a", "b", "c", "d"),
                        List.of(), null, r.ticket));
                assertTrue(r.driver.processor().firstMinute.permitsPreparation(r.ticket, generation),
                        "the graph fact carrying this preparation's receipt must not cancel itself");
            }
            r.driver.post(SessionFixtures.graph("/demo/person-chosen.graphml"));
            assertEquals("DONE", r.driver.snapshot().firstMinute().phase(), "the person's graph supersedes startup at phase " + phase);
            assertFalse(r.driver.processor().firstMinute.permitsOpen(r.ticket));
            assertFalse(r.driver.processor().firstMinute.permitsPreparation(r.ticket, r.driver.snapshot().logGeneration()),
                    "late read/design callbacks cannot replace the graph the person chose");
        }
    }

    @Test void aGraphOpenedDuringFirstContactRestoresTheInvestigationStage() {
        Run r = playing(); showStep(r);
        r.driver.post(SessionFixtures.graph("/demo/person-chosen.graphml"));
        assertEquals("DONE", r.driver.snapshot().firstMinute().phase());
        assertEquals(WalkPlaybackState.Stage.RECORDS, r.driver.snapshot().walkPlayback().stage(),
                "another graph is an ordinary investigation, not continued first contact");
        assertFalse(r.driver.snapshot().walkPlayback().showing());
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(99, r.driver.snapshot().logGeneration(),
                r.ticket, DiamondDefinition.LOG_SHA256, false));
        assertTrue(capture(r).capture(), "a person's replacement topology is recoverable content");
    }

    @Test void anOfferedRecoveryDefersWithoutMarkingPlayedEvenIfItIsLaterDismissed() {
        var a = new FakeSessionAdapter(); var d = new SessionDriver(a);
        d.post(new ResumeEvents.Activated(null)); d.post(startup(0));
        var saved = new SessionResumeStore.Snapshot(SessionResumeStore.NO_PROJECT, "2026-10-19T00:00:00Z", List.of(), Map.of());
        d.post(new ResumeEvents.OfferLoaded(d.processor().sessionRecovery.generation(), SessionResumeStore.NO_PROJECT, saved, null, null));
        d.post(new ResumeEvents.Requested(d.processor().sessionRecovery.generation(), false));
        assertFalse(a.performed.stream().anyMatch(SessionEffects.OpenDiamondEffect.class::isInstance));
        assertFalse(a.performed.stream().anyMatch(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance));
    }

    @Test void ownedLaunchAndMatchingReadGraphAndDesignAreAllRequired() {
        Run ordinary = opening(); land(ordinary, "NONE");
        assertFalse(ordinary.adapter.performed.stream().anyMatch(SessionEffects.VerifyDiamondReadEffect.class::isInstance),
                "an ordinary open at the same pathname must not borrow first-minute standing");
        for (boolean badLog : List.of(true, false)) {
            Run r = opening(); land(r, "FIRST_MINUTE");
            long g = r.driver.snapshot().logGeneration();
            r.driver.post(new SessionEvents.FirstMinuteLogReadObserved(r.ticket, g, badLog ? "edited" : DiamondDefinition.LOG_SHA256, null));
            if (!badLog) r.driver.post(new SessionEvents.FirstMinutePresentationObserved(r.ticket, g, "edited", DiamondDefinition.DESIGN_SHA256, null));
            assertEquals("FAILED", r.driver.snapshot().firstMinute().phase());
            assertFalse(r.driver.snapshot().firstMinute().bundled());
            assertFalse(r.adapter.performed.stream().anyMatch(SessionEffects.PlayBundledWalkEffect.class::isInstance));
            assertFalse(r.adapter.performed.stream().anyMatch(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance));
        }
    }

    @Test void firstShownMarksOnceAndAutoplayRejectsOldTicketsAndStepsThenEndsAtTheDoors() {
        Run r = playing();
        assertFalse(r.adapter.performed.stream().anyMatch(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance));
        for (int step = 0; step < 4; step++) {
            showStep(r);
            var w = r.driver.snapshot().walkPlayback();
            var schedule = r.adapter.performed.stream().filter(SessionEffects.ScheduleWalkAdvanceEffect.class::isInstance)
                    .map(SessionEffects.ScheduleWalkAdvanceEffect.class::cast).reduce((a, b) -> b).orElseThrow();
            assertTrue(schedule.dwellMs() >= 4_000 && schedule.dwellMs() <= 8_000);
            r.driver.post(new SessionEvents.WalkAdvanceDue(w.ticket() - 1, step));
            r.driver.post(new SessionEvents.WalkAdvanceDue(w.ticket(), step - 1));
            assertEquals(step, r.driver.snapshot().walkPlayback().step(), "stale timers must not move the walk");
            r.driver.post(new SessionEvents.WalkAdvanceDue(w.ticket(), step));
        }
        assertEquals(1, r.adapter.performed.stream().filter(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance).count());
        assertEquals("DOORS", r.driver.snapshot().firstMinute().phase());
        assertEquals(List.of("learn", "investigate", "build"), r.driver.snapshot().firstMinute().doors());
        assertEquals(WalkPlaybackState.Stage.TOPOLOGY, r.driver.snapshot().walkPlayback().stage(), "first contact keeps the stage");
        r.driver.post(new SessionEvents.FirstMinuteCardClosed());
        assertEquals(WalkPlaybackState.Stage.RECORDS, r.driver.snapshot().walkPlayback().stage());
        assertEquals("DONE", r.driver.snapshot().firstMinute().phase());
    }

    @Test void navigationMakesTheShowingManualAndEscapeStillOffersTheDoors() {
        Run r = playing(); showStep(r);
        r.driver.post(new SessionEvents.WalkNavigated(1)); showStep(r);
        var w = r.driver.snapshot().walkPlayback();
        r.driver.post(new SessionEvents.WalkAdvanceDue(w.ticket(), w.step()));
        assertEquals(1, r.driver.snapshot().walkPlayback().step(), "manual pace must not be taken over by a timer");
        r.driver.post(new SessionEvents.WalkEndRequested("Esc"));
        assertEquals("DOORS", r.driver.snapshot().firstMinute().phase());
    }

    @Test void aDoorRestoresRecordsEvenAfterThePersonExplicitlyChoseTopology() {
        Run r = playing(); showStep(r);
        r.driver.post(new SessionEvents.StageToggleRequested(WalkPlaybackState.Stage.TOPOLOGY));
        assertEquals("DOORS", r.driver.snapshot().firstMinute().phase(), "ending the walk still offers the doors");
        r.driver.post(new SessionEvents.FirstMinuteDoorChosen("investigate"));
        assertEquals(WalkPlaybackState.Stage.RECORDS, r.driver.snapshot().walkPlayback().stage(),
                "choosing a door restores investigation even when that chooser is later cancelled");
        assertEquals("DONE", r.driver.snapshot().firstMinute().phase());
    }

    @Test void aLaterOpenSupersedesTheDeferredDoorBeforeItsReaderCompletes() {
        Run r = playing(); showStep(r);
        r.driver.post(new SessionEvents.FirstMinuteDoorChosen("investigate"));
        assertTrue(r.driver.processor().firstMinute.permitsDoor(r.ticket), "the accepted door is initially permitted");
        r.driver.post(new SessionEvents.OpenLogRequested(r.driver.nextOpId(), "/demo/later.yaml", null, null, false));
        assertTrue(r.driver.snapshot().logOpen(), "the later reader is still pending over the old log");
        assertFalse(r.driver.processor().firstMinute.permitsDoor(r.ticket),
                "a deferred door must not open its chooser or sample over a later pending open");
    }

    @Test void aRequestDuringExtractionSupersedesItsCallbackAndAPlainOpenEndsFirstContact() {
        Run r = opening();
        SessionFixtures.openLog(r.driver, r.adapter, "/demo/ordinary.yaml");
        assertFalse(r.driver.processor().firstMinute.permitsOpen(r.ticket));
        r.driver.post(new SessionEvents.FirstMinuteOpenFailed(r.ticket, "old failure"));
        assertEquals("DONE", r.driver.snapshot().firstMinute().phase());
        Run shown = playing(); showStep(shown);
        SessionFixtures.openLog(shown.driver, shown.adapter, "/demo/ordinary.yaml");
        assertEquals(WalkPlaybackState.Stage.RECORDS, shown.driver.snapshot().walkPlayback().stage());
        assertFalse(shown.driver.snapshot().firstMinute().bundled());
        observeCapture(shown, 207);
        assertTrue(capture(shown).capture(), "ordinary replacement recovery is admitted by the node");
    }

    @Test void aFollowRotationCannotBorrowTheOwnedGenerationStage() {
        Run r = playing(); showStep(r);
        var original = new telamin.fluxtion.audit.analyser.analyser.ui.OpenRequest(false, null,
                telamin.fluxtion.audit.analyser.analyser.ui.OpenRequest.Launch.FIRST_MINUTE, null, null, r.ticket);
        var rotation = telamin.fluxtion.audit.analyser.analyser.ui.OpenRequest.reload(original, null);
        assertTrue(rotation.suppressDialogs(), "an unattended rotation must retain its original silent audience");
        assertEquals(0, rotation.firstMinuteTicket(), "a reader rotation is not a new OpenDiamondEffect");
        long operation = r.driver.nextOpId();
        r.driver.post(new SessionEvents.OpenLogRequested(operation, "/demo/diamond.yaml", null, null, false));
        r.driver.post(new SessionEvents.LogOpened(operation, "/demo/diamond.yaml", null, Set.of("a"), 6, 6,
                "INFO", null, false, 0, 2, rotation.launch().name()));
        assertEquals(WalkPlaybackState.Stage.RECORDS, r.driver.snapshot().walkPlayback().stage(),
                "only a new owned diamond open, not an ordinary reader rotation, may retain first contact");
        assertFalse(r.driver.snapshot().firstMinute().bundled());
    }

    @Test void onlyTheCausallyOwnedUntouchedReadIsOmittedFromRecovery() {
        Run r = playing();
        long generation = r.driver.snapshot().logGeneration();
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(1, generation, r.ticket, DiamondDefinition.LOG_SHA256, false));
        assertFalse(capture(r).capture(), "an untouched verified first-minute capture is omitted");
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(2, generation, 0, DiamondDefinition.LOG_SHA256, false));
        assertTrue(capture(r).capture(), "an ordinary open of byte-identical evidence cannot inherit the omission");
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(3, generation, r.ticket, "edited", false));
        assertTrue(capture(r).capture(), "launch identity without a matching read cannot discard a session");
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(4, generation, r.ticket, DiamondDefinition.LOG_SHA256, true));
        assertTrue(capture(r).capture(), "a person's flag, note, chart, report or walk makes the session recoverable");
        r.driver.post(new SessionEvents.FirstMinuteCaptureObserved(5, generation, r.ticket, DiamondDefinition.LOG_SHA256, false));
        assertTrue(capture(r).capture(), "removing an addition later does not turn a person's session back into untouched evidence");

        Run outgoing = playing();
        long oldGeneration = outgoing.driver.snapshot().logGeneration();
        SessionFixtures.openLog(outgoing.driver, outgoing.adapter, "/demo/other.yaml");
        outgoing.driver.post(new SessionEvents.FirstMinuteCaptureObserved(6, oldGeneration, outgoing.ticket, DiamondDefinition.LOG_SHA256, false));
        assertFalse(capture(outgoing).capture(), "a queued outgoing capture retains its own accepted read after a replacement open");
        outgoing.driver.post(new SessionEvents.FirstMinuteCaptureObserved(7, outgoing.driver.snapshot().logGeneration(),
                0, DiamondDefinition.LOG_SHA256, false));
        assertTrue(capture(outgoing).capture(), "the replacement cannot inherit the outgoing capture's identity");
    }

    private static SessionEffects.AdmitSessionCaptureEffect capture(Run r) {
        return r.adapter.performed.stream().filter(SessionEffects.AdmitSessionCaptureEffect.class::isInstance)
                .map(SessionEffects.AdmitSessionCaptureEffect.class::cast).reduce((a, b) -> b).orElseThrow();
    }

    @Test void aPartlyShownStepNeverAutoplaysPastItsRefusal() {
        Run r = playing();
        var w = r.driver.snapshot().walkPlayback();
        var states = new java.util.ArrayList<SessionEvents.WalkTargetState>();
        for (int n = 0; n < w.definition().steps().getFirst().targets().size(); n++) {
            var t = w.definition().steps().getFirst().targets().get(n);
            states.add(new SessionEvents.WalkTargetState(n + 1, t.target(), t.caption(), "CURRENT", n != 0, "not visible"));
        }
        r.driver.post(new SessionEvents.WalkStepPrepared(w.ticket(), r.driver.snapshot().logGeneration(), states, ""));
        assertEquals("PARTLY_SHOWN", r.driver.snapshot().walkPlayback().phase());
        assertFalse(r.adapter.performed.stream().anyMatch(SessionEffects.ScheduleWalkAdvanceEffect.class::isInstance),
                "a partial step waits for the person, never silently scrolls past its refusal");
        assertFalse(r.adapter.performed.stream().anyMatch(SessionEffects.MarkFirstMinutePlayedEffect.class::isInstance),
                "a partial first step is not the first SHOWN step and must not mark played");
        r.driver.post(new SessionEvents.WalkAdvanceDue(w.ticket(), 0));
        assertEquals(0, r.driver.snapshot().walkPlayback().step());
    }

    @Test void anOrdinaryWalkRestoresItsPriorStageAndTheReelAsksTheNodeToAdvance() {
        var a = new FakeSessionAdapter(); var d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/demo/ordinary.yaml");
        var shipped = DiamondDefinition.walk();
        d.post(new SessionEvents.WalkPlayRequested(0, shipped, 0, "reel"));
        assertEquals(WalkPlaybackState.Stage.TOPOLOGY, d.snapshot().walkPlayback().stage());
        var run = new Run(d, a, 0, 0); showStep(run);
        var w = d.snapshot().walkPlayback();
        d.post(new SessionEvents.WalkAdvanceDue(w.ticket(), 0));
        assertEquals(0, d.snapshot().walkPlayback().step(), "a reel cannot advance before it captures this step");
        d.post(new SessionEvents.WalkReelFrameCaptured(w.ticket() - 1, 0));
        assertFalse(a.performed.stream().anyMatch(SessionEffects.ScheduleWalkAdvanceEffect.class::isInstance));
        d.post(new SessionEvents.WalkReelFrameCaptured(w.ticket(), 0));
        assertTrue(a.performed.stream().anyMatch(SessionEffects.ScheduleWalkAdvanceEffect.class::isInstance),
                "the reel reports capture; the node requests the next advance");
        d.post(new SessionEvents.WalkAdvanceDue(w.ticket(), 0));
        assertEquals(1, d.snapshot().walkPlayback().step());
        d.post(new SessionEvents.WalkEndRequested("finished"));
        assertEquals(WalkPlaybackState.Stage.RECORDS, d.snapshot().walkPlayback().stage(), "ordinary playback restores its prior stage");
        d.post(new SessionEvents.StageToggleRequested(WalkPlaybackState.Stage.TOPOLOGY));
        d.post(new SessionEvents.WalkPlayRequested(0, shipped, 0, "person"));
        d.post(new SessionEvents.WalkEndRequested("finished"));
        assertEquals(WalkPlaybackState.Stage.TOPOLOGY, d.snapshot().walkPlayback().stage(), "a pre-existing topology stage is restored too");
        d.post(new SessionEvents.StageToggleRequested(WalkPlaybackState.Stage.RECORDS));
        assertEquals(WalkPlaybackState.Stage.RECORDS, d.snapshot().walkPlayback().stage());
    }
}
