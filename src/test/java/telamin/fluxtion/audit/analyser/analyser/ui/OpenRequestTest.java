package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M46 A4 — who {@code context.log.openedBy} says opened the log.
 *
 * <p>The defect: a fresh {@code --rest} instance sharing a home with a sibling run restored that run's
 * log at startup and reported {@code openedBy: "you"}. Two independent agents read that as their own
 * open. The value is an agent's only way to tell a log it chose from one it inherited, so a log nobody
 * opened in this session must not be attributed to anybody in it.
 */
class OpenRequestTest {

    @Test
    void aLogRestoredAtStartupIsAttributedToNobodyInThisSession() {
        String by = OpenRequest.atStartup(true).openedBy();

        assertFalse(by.equals("you") || by.equals("action socket"), by);
        assertTrue(by.contains("restored at startup"), by);
        assertTrue(by.contains("previous session"), by);
    }

    @Test
    void aPathOnTheCommandLineIsNotARestore_andNotAPersonAtTheScreenEither() {
        String by = OpenRequest.atStartup(false).openedBy();

        assertTrue(by.contains("command line"), by);
        assertFalse(by.contains("restored"), by);
    }

    @Test
    void theTwoSessionAudiencesAreUnchanged() {
        assertEquals("you", OpenRequest.HUMAN.openedBy());
        assertEquals("action socket", OpenRequest.socket("uat").openedBy());
        assertEquals("you", new OpenRequest(false, null).openedBy(), "the two-argument form is a session open");
    }

    @Test
    void everyValueReadsAfterTheProjectPanelsWords_openedBy() {
        // ProjectModel prints "opened by " + openedBy, so no value may start with a verb or a capital
        for (OpenRequest r : new OpenRequest[]{OpenRequest.HUMAN, OpenRequest.socket(null),
                OpenRequest.atStartup(true), OpenRequest.atStartup(false)}) {
            String by = r.openedBy();
            assertFalse(by.startsWith("opened") || by.startsWith("restored"), by);
            assertTrue(Character.isLowerCase(by.charAt(0)), by);
        }
    }

    @Test
    void aFollowRotationKeepsTheLaunch_aRestoredLogIsStillOneNobodyOpenedHere() {
        OpenRequest rotated = OpenRequest.reload(OpenRequest.atStartup(true), "uat");

        assertEquals(OpenRequest.Launch.RESTORED, rotated.launch());
        assertEquals("uat", rotated.provenance());
        assertFalse(rotated.fromActionSocket());
        assertEquals(OpenRequest.Launch.NONE, OpenRequest.reload(null, null).launch());
    }

    @Test
    void aStartupOpenIsStillAHumanAudience_itsDialogsAreForThePersonAtTheScreen() {
        // the launch changes ATTRIBUTION only; routing dialogs to data is the socket's rule (M35.9)
        assertFalse(OpenRequest.atStartup(true).fromActionSocket());
        assertFalse(OpenRequest.atStartup(false).fromActionSocket());
    }
}
