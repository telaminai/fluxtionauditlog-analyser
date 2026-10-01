package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** #93: an address cannot confer ownership, even within one accepted operation. */
class BundleOperationOwnershipTest {
    private static SessionEvents.BundlePlan plan(String identity, String digest) {
        return new SessionEvents.BundlePlan("/DEMO/profile", null, "/DEMO/log", identity,
                "/DEMO", "", "", "/DEMO/bundle.fexp", null, digest);
    }

    @Test void appliedPathMustStillBeTheVerifiedProfile() {
        var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
        var driver = new SessionDriver(effect -> effect instanceof SessionEffects.ApplyProfileEffect e
                ? new SessionEvents.ProfileApplied(e.opId(), "/DEMO/replacement", "replacement") : fake.perform(effect));
        long op = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(op, "/DEMO/bundle.fexp", TransitionKind.OPEN_BUNDLE, "test"));
        driver.post(new SessionEvents.ProfileLoaded(op, "/DEMO/profile", true, "DEMO", 0, null, plan("first", "verified")));
        assertFalse(driver.snapshot().bundle().fromBundle(), "aDifferentAppliedProfileCannotBorrowThePlan");
    }

    @Test void aDifferentLoadedDigestCannotClaimThePlan() {
        var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
        var driver = new SessionDriver(effect -> effect instanceof SessionEffects.LoadProfileEffect e
                ? new SessionEvents.ProfileLoaded(e.opId(), e.profilePath(), true, "DEMO", 0, null, null, "changed")
                : fake.perform(effect));
        long op = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(op, "/DEMO/bundle.fexp", TransitionKind.OPEN_BUNDLE, "test"));
        driver.post(new SessionEvents.ProfileLoaded(op, "/DEMO/profile", true, "DEMO", 0, null, plan("first", "verified")));
        assertFalse(driver.snapshot().bundle().fromBundle(), "loadedContentMustMatchTheVerifiedPlan");
    }

    @Test void twoAcceptedBundlesThenAnUnrelatedFailureKeepTheSecondIdentity() {
        var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
        var driver = new SessionDriver(fake);
        for (String identity : java.util.List.of("first", "second")) {
            long op = driver.nextOpId();
            driver.submit(new SessionEvents.OpenProjectRequested(op, "/DEMO/bundle.fexp", TransitionKind.OPEN_BUNDLE, "test"));
            driver.post(new SessionEvents.ProfileLoaded(op, "/DEMO/profile", true, "DEMO", 0, null, plan(identity, identity)));
            assertEquals(identity, driver.snapshot().bundle().identity(), "acceptedBundleKeepsItsOwnIdentity");
        }
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), "/DEMO/missing",
                TransitionKind.EXPLICIT_SWITCH, "test"));
        assertEquals("second", driver.snapshot().bundle().identity(), "failureDoesNotRevokeTheBundleStillInForce");
    }
}
