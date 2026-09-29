package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic late-worker witnesses for the Start page's asynchronous bundle entrance. */
class BundleOpenReplayTest {
    private static SessionEvents.BundlePlan plan() {
        return new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
                "/bundle/log.yaml", "DEMO-identity", "/bundle", "DEMO limits");
    }

    @Test void lateBundleCannotReplaceTheNewerProjectOrOpenItsEvidence() {
        var adapter = new FakeSessionAdapter().withProfile("/chosen/project.fluxtion-settings")
                .withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long bundleId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(bundleId, "/slow.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        assertEquals(1, adapter.countOf(SessionEffects.PrepareBundleEffect.class));
        assertNull(adapter.appliedProfile, "a pending verification must not change the current project");

        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),
                "/chosen/project.fluxtion-settings", TransitionKind.EXPLICIT_SWITCH, "recent"));
        driver.submit(new SessionEvents.ProfileLoaded(bundleId, plan().profilePath(), true,
                null, 0, null, plan()));

        assertFalse(driver.processor().operationGate.accepted(), "the slow worker's result is stale");
        assertEquals("/chosen/project.fluxtion-settings", adapter.appliedProfile);
        assertEquals(0, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class),
                "the bundle graph and log must not open after the user's newer choice");
    }

    @Test void acceptedBundleOpensItsOwnEvidenceOnlyAfterItsProfile() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long bundleId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(bundleId, "/ready.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(bundleId, plan().profilePath(), true,
                null, 0, null, plan()));

        assertEquals(plan().profilePath(), adapter.appliedProfile);
        assertEquals(1, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class));
        int apply = -1, evidence = -1;
        for (int i = 0; i < adapter.performed.size(); i++) {
            if (adapter.performed.get(i) instanceof SessionEffects.ApplyProfileEffect) apply = i;
            if (adapter.performed.get(i) instanceof SessionEffects.OpenBundleEvidenceEffect) evidence = i;
        }
        assertTrue(apply >= 0 && evidence > apply, "the processor orders project application before evidence opening");
    }

    @Test void lateFirstBundleCannotStealASecondPendingBundle() {
        var first = plan();
        var second = new SessionEvents.BundlePlan("/second/project.fluxtion-settings",
                "/second/graph.graphml", "/second/log.yaml", "DEMO-second", "/second", "DEMO limits");
        var adapter = new FakeSessionAdapter().withProfile(first.profilePath()).withProfile(second.profilePath());
        var driver = new SessionDriver(adapter);
        long firstId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(firstId, "/first.fexp", TransitionKind.OPEN_BUNDLE, "start"));
        long secondId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(secondId, "/second.fexp", TransitionKind.OPEN_BUNDLE, "start"));

        driver.submit(new SessionEvents.ProfileLoaded(firstId, first.profilePath(), true, null, 0, null, first));
        assertFalse(driver.processor().operationGate.accepted());
        assertNull(adapter.appliedProfile, "the older bundle must not apply while the newer one waits");
        assertEquals(0, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class));

        driver.submit(new SessionEvents.ProfileLoaded(secondId, second.profilePath(), true, null, 0, null, second));
        assertEquals(second.profilePath(), adapter.appliedProfile);
        assertEquals(1, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class));
    }

    @Test void refusedBundleLeavesProjectUntouchedAndClearsPendingStatus() {
        var adapter = new FakeSessionAdapter().withProfile("/current/project.fluxtion-settings");
        var driver = new SessionDriver(adapter);
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),
                "/current/project.fluxtion-settings", TransitionKind.EXPLICIT_SWITCH, "recent"));
        long bundleId = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(bundleId, "/refused.fexp",
                TransitionKind.OPEN_BUNDLE, "start"));
        assertNotNull(driver.processor().operationGate.inFlightWhat());
        driver.submit(new SessionEvents.ProfileLoaded(bundleId, "/refused.fexp", false,
                null, 0, "DEMO refusal"));
        assertEquals("/current/project.fluxtion-settings", adapter.appliedProfile);
        assertNull(driver.processor().operationGate.inFlightWhat());
        assertEquals(0, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class));
        assertEquals("DEMO refusal", adapter.lastWarning);
    }
}
