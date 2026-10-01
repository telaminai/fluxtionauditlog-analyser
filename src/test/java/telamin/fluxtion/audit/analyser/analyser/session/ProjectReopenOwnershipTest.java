package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import java.util.List;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen;
import static org.junit.jupiter.api.Assertions.*;

class ProjectReopenOwnershipTest {
    private static long open(SessionDriver driver, boolean person, boolean permitted) {
        long op = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(op, "/DEMO/profile", TransitionKind.EXPLICIT_SWITCH, "test", null,
                new SessionEvents.ProjectAudience(person ? SessionEvents.OperationOrigin.PERSON
                        : SessionEvents.OperationOrigin.MACHINE, permitted)));
        return op;
    }

    @Test void permissionAndOriginBelongToTheOperation() {
        for (boolean person : List.of(false, true)) for (boolean permitted : List.of(false, true)) {
            var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
            var driver = new SessionDriver(fake);
            open(driver, person, permitted);
            assertEquals(person && permitted ? 1 : 0, fake.reopenOffers.size(), "onlyAnAuthorisedPersonOperationOffers");
            if (!person || !permitted) assertFalse(driver.auditSink().matching("offerProjectReopenSkipped").isEmpty(),
                    "disabledOrMachineOfferIsRecordedAsSkipped");
        }
    }

    @Test void aLaterOperationCannotInheritOrReviveAnOffer() {
        var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
        var driver = new SessionDriver(fake);
        long person = open(driver, true, true);
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), null, TransitionKind.CLOSE, "test"));
        open(driver, false, false);
        driver.post(new SessionEvents.ProjectReopenReady(person, "/DEMO/profile", "DEMO",
                new ProjectReopen(List.of("/DEMO/log"), List.of()), false));
        assertEquals(0, fake.countOf(SessionEffects.ShowProjectReopenEffect.class), "aNewOperationMustNotInheritThePersonsOffer");
        assertFalse(driver.processor().projectReopenOffer.mayPresent(person, "/DEMO/profile"));
    }

    @Test void startupCannotLendItsAudienceToAnOperationBeforeTheWindowAppears() {
        var fake = new FakeSessionAdapter().withProfile("/DEMO/profile");
        var driver = new SessionDriver(fake);
        open(driver, false, false);
        driver.post(new SessionEvents.ProjectReopenRequested(driver.nextOpId(), "/DEMO/profile",
                new SessionEvents.ProjectAudience(SessionEvents.OperationOrigin.PERSON, true)));
        assertEquals(0, fake.reopenOffers.size(), "startupMustNotReopenAnOfferAfterANewerOperation");
    }
}
