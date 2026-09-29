package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the session came from, for the session's whole life.
 *
 * <p>The behaviour these pin is that the provenance is <b>kept</b>. It was always computed — verification
 * produced an identity and a working copy — and then dropped when the transition settled, so every surface
 * that wanted to say "this is received evidence" had nothing to read.
 */
class BundleProvenanceTest {

    private static SessionEvents.BundlePlan plan() {
        // the 8th component is the .fexp the person chose; the 1st is the profile inside the working copy
        return new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
                "/bundle/log.yaml", "sha256:DEMO-identity", "/bundle", "DEMO limits", "",
                "/demo/evidence.fexp");
    }

    private static SessionDriver openedBundle(FakeSessionAdapter adapter) {
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));
        return driver;
    }

    @Test
    @DisplayName("a bundle's provenance is still published after the transition that produced it has settled")
    void provenanceOutlivesTheTransition() {
        var driver = openedBundle(new FakeSessionAdapter().withProfile(plan().profilePath()));

        var bundle = driver.snapshot().bundle();
        assertTrue(bundle.fromBundle(), "the settled session came from a verified bundle");
        assertEquals("sha256:DEMO-identity", bundle.identity(), "identityIsPublished");
        assertEquals("/demo/evidence.fexp", bundle.source(),
                "sourceIsTheFexpThePersonChose, not the unpacked profile inside it");
        assertEquals("/bundle", bundle.workingCopy(), "workingCopyIsPublished");
        assertEquals("DEMO limits", bundle.limits(), "limitsArePublished");
    }

    @Test
    @DisplayName("provenance is not consumed by reading it, nor cleared by later unrelated traffic")
    void provenanceSurvivesLaterEvents() {
        var driver = openedBundle(new FakeSessionAdapter().withProfile(plan().profilePath()));
        assertTrue(driver.snapshot().bundle().fromBundle());

        driver.submit(new SessionEvents.LogCleared(1));

        assertEquals("sha256:DEMO-identity", driver.snapshot().bundle().identity(),
                "stillEvidenceAfterUnrelatedTraffic");
    }

    @Test
    @DisplayName("the claim is about the PROJECT, so it survives opening an unrelated log — deliberately")
    void theClaimIsAboutTheProjectNotTheLogOnScreen() {
        // Not a defect, but the most reachable over-read in the feature: the window can say
        // "evidence bundle X" while the records on screen are the person's own. Clearing it here would be a
        // lie in the other direction — the bundle's charts, walks, reports and source anchor are all still
        // the ones in force. Pinned so a later "fix" has to argue with this rather than discover it.
        var driver = openedBundle(new FakeSessionAdapter().withProfile(plan().profilePath()));
        assertTrue(driver.snapshot().bundle().fromBundle());

        driver.submit(new SessionEvents.LogOpened(driver.nextOpId(), "/somewhere/unrelated.yaml",
                null, java.util.Set.of(), 0, 0, null, null, false));

        assertTrue(driver.snapshot().bundle().fromBundle(),
                "theProjectIsStillTheBundles, whatever log is on screen");
        assertEquals("sha256:DEMO-identity", driver.snapshot().bundle().identity());
    }

    @Test
    @DisplayName("an ordinary project is not evidence")
    void ordinaryProjectIsNotABundle() {
        var adapter = new FakeSessionAdapter().withProfile("/own/project.fluxtion-settings");
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(id, "/own/project.fluxtion-settings", true, null, 0, null));

        assertFalse(driver.snapshot().bundle().fromBundle(), "ownProjectIsNotEvidence");
        assertNull(driver.snapshot().bundle().identity());
    }

    @Test
    @DisplayName("switching from a bundle to an ordinary project stops the session being evidence")
    void switchingAwayClearsTheBundle() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath())
                .withProfile("/own/project.fluxtion-settings");
        var driver = openedBundle(adapter);
        assertTrue(driver.snapshot().bundle().fromBundle());

        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(id, "/own/project.fluxtion-settings", true, null, 0, null));

        assertFalse(driver.snapshot().bundle().fromBundle(), "clearedOnSwitchToOwnProject");
    }

    @Test
    @DisplayName("an ordinary project opened after a bundle is not labelled with that bundle, even if its apply throws")
    void anOrdinaryProjectIsNeverLabelledWithThePreviousBundle() {
        // ProjectSession has ALREADY swapped the settings by the time the apply effect runs, so a throw
        // there left the session believing the OLD project was in force, and the window said
        // "evidence bundle A" over the person's own work.
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath())
                .withProfile("/own/project.fluxtion-settings");
        var driver = openedBundle(adapter);
        assertTrue(driver.snapshot().bundle().fromBundle(), "precondition: bundle A is in force");

        adapter.applyRenderThrows = true;
        long own = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(own, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(own, "/own/project.fluxtion-settings", true, null, 0, null));

        assertTrue(adapter.applyRenderFailed, "the render really failed");
        assertFalse(driver.snapshot().bundle().fromBundle(),
                "myOwnProjectIsNotLabelledWithAStrangersBundle");
    }

    @Test
    @DisplayName("a bundle aborted mid-apply cannot label the ordinary project opened after it")
    void anAbortedBundleNeverLabelsTheNextProject() {
        // The reviewer's ProtocolViolation probe. The batch is abandoned with a verified plan held and no
        // ProfileApplied and no EffectFailed — the one shape where nothing tidies up after the transition.
        // What stops the next project being labelled is that its profile is not the bundle's.
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath())
                .withProfile("/own/project.fluxtion-settings");
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        adapter.applyViolates = true;
        assertThrows(SessionDriver.ProtocolViolation.class, () -> driver.submit(
                new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan())));
        assertFalse(driver.snapshot().bundle().fromBundle(), "the aborted bundle claims nothing");

        long own = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(own, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(own, "/own/project.fluxtion-settings", true, null, 0, null));

        assertFalse(driver.snapshot().bundle().fromBundle(), "aPlanDoesNotLabelAnUnrelatedProfile");
    }

    @Test
    @DisplayName("a bundle whose own effects fail along the way is still evidence once its profile applies")
    void aBundleIsStillEvidenceWhenAnUnrelatedEffectFailed() {
        // Dropping the pending plan on ANY failed effect killed a bundle whose closeLog failed but which
        // then applied perfectly well — the same lie in the quieter direction.
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.EffectFailed(id, "closeLog", "DEMO the log would not close"));
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));

        assertTrue(driver.snapshot().bundle().fromBundle(),
                "anUnrelatedFailureDoesNotUnmakeTheEvidence");
        assertEquals("sha256:DEMO-identity", driver.snapshot().bundle().identity());
    }

    @Test
    @DisplayName("a close whose RENDER fails still ends the evidence claim — the project is already gone")
    void aCloseWhoseRenderFailsStillEndsTheClaim() {
        // The mirror of the apply case, and the one the last fix missed: project.close() is the real half
        // and has already run, so a throw in the rendering half must not leave the window saying
        // [evidence bundle X] over a session with no project at all.
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = openedBundle(adapter);
        assertTrue(driver.snapshot().bundle().fromBundle(), "precondition: the bundle is in force");

        adapter.restoreRenderThrows = true;
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), null,
                TransitionKind.CLOSE, "menu"));

        assertTrue(adapter.restoreRenderFailed, "the render really failed");
        assertTrue(adapter.settingsRestored, "and the project is genuinely gone");
        assertFalse(driver.snapshot().bundle().fromBundle(), "aFailedRenderDoesNotKeepTheClaim");
    }

    @Test
    @DisplayName("closing a bundle's project back to your own settings stops the session being evidence")
    void closingBackToOwnSettingsClearsTheBundle() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = openedBundle(adapter);
        assertTrue(driver.snapshot().bundle().fromBundle());

        // a real CLOSE: the boundary asks for the restore and the adapter answers SettingsRestored with the
        // matching opId. Submitting the fact alone does nothing, because the gate refuses an unexpected id.
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), null,
                TransitionKind.CLOSE, "menu"));

        assertTrue(adapter.settingsRestored, "the close really restored the person's own settings");
        assertFalse(driver.snapshot().bundle().fromBundle(), "clearedOnSettingsRestored");
    }

    @Test
    @DisplayName("provenance is NOT published between the verified plan and the profile actually applying")
    void nothingIsClaimedUntilTheProfileIsInForce() {
        var adapter = new FakeSessionAdapter();          // no profile registered: the apply cannot complete
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));

        assertFalse(driver.snapshot().bundle().fromBundle(),
                "aVerifiedPlanIsNotYetASessionThatCameFromABundle");
    }

    @Test
    @DisplayName("a bundle that verified but never applied cannot label the next ordinary project as evidence")
    void abandonedBundleDoesNotLeakIntoTheNextProject() {
        var adapter = new FakeSessionAdapter().withProfile("/own/project.fluxtion-settings");
        var driver = new SessionDriver(adapter);
        long abandoned = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(abandoned, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));

        long chosen = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(chosen, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(abandoned, plan().profilePath(), true, null, 0, null, plan()));
        driver.submit(new SessionEvents.ProfileLoaded(chosen, "/own/project.fluxtion-settings", true, null, 0, null));

        assertFalse(driver.snapshot().bundle().fromBundle(),
                "staleBundleMustNotLabelTheProjectTheUserActuallyChose");
    }
}
