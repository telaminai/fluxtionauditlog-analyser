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
        return new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
                "/bundle/log.yaml", "sha256:DEMO-identity", "/bundle", "DEMO limits");
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
