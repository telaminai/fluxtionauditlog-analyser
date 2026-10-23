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
        assertFalse(shown.driver.snapshot().firstMinute().omitRecovery());
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
