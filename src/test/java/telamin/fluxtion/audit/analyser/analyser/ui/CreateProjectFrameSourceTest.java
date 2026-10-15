package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M73 review m2 and m3 — two properties of the frame's create entrances that only its source can show headlessly.
 */
class CreateProjectFrameSourceTest {

    private static String frame() throws Exception {
        return Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
    }

    /**
     * m2: a missing folder is refused BEFORE the offer dialog, as an existing project is. Witness (run 2026-10-15):
     * drop {@code || !facts.directory()} from {@code createProjectAt}.
     */
    @Test
    @DisplayName("m2: the menu submits a missing folder to the node before discovering or showing an offer")
    void aMissingFolderIsRefusedBeforeTheOffer() throws Exception {
        String body = OneDispatchModelTest.bodyOf(frame(), "private void createProjectAt(");
        int check = body.indexOf("!facts.directory()");
        assertTrue(check >= 0, "createProjectAt checks the directory fact");
        assertTrue(check < body.indexOf("NewProjectDiscovery.discover("), "before any discovery");
        assertTrue(check < body.indexOf("NewProjectOfferDialog.show("), "and before the offer is shown");
    }

    /**
     * m3: the socket create reads the configuration only on the event thread. Witness (run 2026-10-15): pass
     * {@code config} to {@code CreateProjectObservation.facts} on the worker again.
     */
    @Test
    @DisplayName("m3: the worker observes the filesystem only; config-derived facts are taken inside onEdt")
    void configIsReadOnTheEventThread() throws Exception {
        String body = OneDispatchModelTest.bodyOf(frame(),
                "public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult createProject(");
        assertFalse(body.isEmpty(), "createProject must exist");
        assertTrue(body.contains("CreateProjectObservation.facts(given, offer, null)"), "the worker observes with no config");
        assertFalse(body.matches("(?s).*CreateProjectObservation\\.facts\\([^)]*config\\).*"),
                "no config read on the worker thread");
        int firstOnEdt = body.indexOf("actionExecutor.onEdt(");
        int firstWithConfig = body.indexOf("withConfig(");
        assertTrue(firstOnEdt >= 0 && firstWithConfig > firstOnEdt, "withConfig runs inside an onEdt block");
        int count = body.split("withConfig\\(", -1).length - 1;
        int onEdts = body.split("actionExecutor\\.onEdt\\(", -1).length - 1;
        assertEquals(2, count, "once for the preview, once for the apply");
        assertEquals(2, onEdts);
    }
}
