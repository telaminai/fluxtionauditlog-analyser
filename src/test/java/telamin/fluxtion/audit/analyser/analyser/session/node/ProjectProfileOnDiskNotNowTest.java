package telamin.fluxtion.audit.analyser.analyser.session.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #177, owner decision on #198: "Not now" puts the banner away for THAT content only. When the file on disk changes
 * again the banner comes back, so "Keep mine" stays reachable — including a change back to content offered before.
 */
class ProjectProfileOnDiskNotNowTest {

    static final String KEY = "profile-key";
    static final String OURS = "digest-ours";

    private final ProjectProfileOnDisk node = new ProjectProfileOnDisk(new EffectQueue());

    private void observe(String onDisk, boolean saveRefused) {
        node.onObserved(new SessionEvents.ProjectProfileObserved(KEY, onDisk, OURS, saveRefused));
    }

    private void notNow() {
        node.onChoice(new SessionEvents.ProjectProfileChoice(KEY, SessionEvents.ProfileChoice.LATER, "person"));
    }

    @Test
    @DisplayName("Not now hides the banner; the same content (a refused save re-reporting it) does not bring it back")
    void notNowHidesItForThatContent() {
        observe("digest-a", false);
        assertNotNull(node.banner(), "control: the change is offered");
        notNow();
        assertNull(node.banner(), "Not now puts it away");
        observe("digest-a", true);
        assertNull(node.banner(), "the same content is not offered again");
        assertTrue(node.changed(), "the change still stands");
    }

    @Test
    @DisplayName("★ after Not now, a further change on disk brings the banner back, so Keep mine is reachable")
    void aFurtherChangeBringsItBack() {
        observe("digest-a", false);
        notNow();
        observe("digest-b", false);
        assertNotNull(node.banner(), "★ a new change is a new offer");
        assertTrue(node.mayOffer(KEY));
    }

    @Test
    @DisplayName("★ after Not now and a return to step, the file changing to the earlier content again is offered again")
    void aChangeBackToEarlierContentIsOfferedAgain() {
        observe("digest-a", false);
        notNow();
        observe(OURS, false);                       // reverted on disk: back in step
        assertNull(node.banner());
        assertFalse(node.changed());
        observe("digest-a", false);                 // the same outside edit is made again
        assertNotNull(node.banner(), "★ it changed on disk again, so it is offered again (red before: stayed hidden)");
    }
}
