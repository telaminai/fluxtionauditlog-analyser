package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Deterministic late-worker witnesses for the Start page's asynchronous bundle entrance. */
class BundleOpenReplayTest {
    private static SessionEvents.BundlePlan plan() {
        return new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
                "/bundle/log.yaml", "DEMO-identity", "/bundle", "DEMO limits", "", null, null, "DEMO-first-digest");
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
                "/second/graph.graphml", "/second/log.yaml", "DEMO-second", "/second", "DEMO limits", "", null, null, "DEMO-second-digest");
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

    private static SessionEvents.GraphOpened graph(String source) {
        return new SessionEvents.GraphOpened("/chosen/DEMO.graphml", source, java.util.Set.of("DEMO"), java.util.List.of("DEMO"));
    }

    @Test void newerExplicitGraphSupersedesBundlePreparation() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/slow.fexp", TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(graph("OPENED"));
        assertFalse(driver.snapshot().pending(), "a newer explicit graph retires the pending bundle immediately");
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));
        assertFalse(driver.processor().operationGate.accepted(), "the older bundle must be refused after the graph choice");
        assertNull(adapter.appliedProfile, "the bundle must not replace the project after newer graph navigation");
        assertEquals("/chosen/DEMO.graphml", driver.snapshot().graphPath(), "the newer graph remains in the snapshot");
        assertEquals(0, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class), "the old bundle must not load its evidence");
        driver.submit(new SessionEvents.ProfileLoaded(id, "/slow.fexp", false, null, 0, "DEMO obsolete failure"));
        assertNull(adapter.lastWarning, "an obsolete bundle failure must not disturb the newer graph either");
    }

    @Test void acceptedBundlesOwnGraphDoesNotCancelItsLog() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/ready.fexp", TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));
        driver.submit(graph("OPENED"));
        assertEquals(id, driver.processor().operationGate.expectedOpId(), "the accepted bundle's own graph must retain its log operation");
        assertTrue(driver.snapshot().pending(), "the accepted bundle's log is still loading");
        assertEquals(plan().profilePath(), adapter.appliedProfile);
        assertEquals(1, adapter.countOf(SessionEffects.OpenBundleEvidenceEffect.class));
    }

    @Test void readerSuppliedGraphDoesNotCancelBundlePreparation() {
        var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/ready.fexp", TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(graph("READER_DECLARED"));
        assertEquals(id, driver.processor().operationGate.expectedOpId(), "a reader fact is not a newer explicit graph choice");
        driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));
        assertEquals(plan().profilePath(), adapter.appliedProfile);
    }
}
