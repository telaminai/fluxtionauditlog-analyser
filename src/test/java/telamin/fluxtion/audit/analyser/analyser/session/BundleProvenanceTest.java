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
    @DisplayName("a bundle whose apply FAILS cannot label the next ordinary project as evidence")
    void aBundleWhoseApplyFailsCannotLabelTheNextProject() {
        // The bundle verifies and its plan is accepted, then applying the SENDER's profile throws. An effect
        // failure does not stop the batch, so the plan was left held with nothing to settle it, and the next
        // ordinary project inherited a stranger's identity, working copy and notes.
        var adapter = new FakeSessionAdapter().withProfile("/own/project.fluxtion-settings");
        var driver = new SessionDriver(adapter);
        long bundleId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(bundleId, "/demo/evidence.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(bundleId, plan().profilePath(), true, null, 0, null, plan()));
        driver.submit(new SessionEvents.EffectFailed(bundleId, "applyProfile", "DEMO the sender's profile threw"));
        assertFalse(driver.snapshot().bundle().fromBundle(), "aDeadTransitionClaimsNothing");

        long own = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(own, "/own/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(own, "/own/project.fluxtion-settings", true, null, 0, null));

        assertFalse(driver.snapshot().bundle().fromBundle(),
                "myOwnProjectIsNotLabelledWithAStrangersBundle");
        assertNull(driver.snapshot().bundle().identity());
        assertEquals("", driver.snapshot().bundle().notes(), "norDoesItInheritTheSendersWords");
    }

    @Test
    @DisplayName("an effect failing AFTER a bundle is in force stops the session claiming to be evidence")
    void aFailureAfterTheBundleIsInForceEndsTheClaim() {
        // Isolates the EffectFailed handler: the bundle really is in force, and no later request follows,
        // so nothing else can clear it. The live case is a CLOSE whose restore throws — the project is
        // genuinely gone while the title still says [evidence bundle ...].
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = openedBundle(adapter);
        assertTrue(driver.snapshot().bundle().fromBundle(), "precondition: the bundle is in force");

        adapter.restoreThrows = true;
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), null,
                TransitionKind.CLOSE, "menu"));

        assertFalse(adapter.settingsRestored, "the restore really failed");
        assertFalse(driver.snapshot().bundle().fromBundle(), "aFailedCloseClaimsNothing");
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
