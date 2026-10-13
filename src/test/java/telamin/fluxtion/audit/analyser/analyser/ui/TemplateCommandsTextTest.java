package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR #139 review: on Windows no catalogue template has a first run (none ships a .cmd run script), so the dialog's
 * empty-list line must say that, not that the template "declares no recognised lifecycle scripts" — every template
 * declares them. Headless: the text is rendered from the install result, nothing else.
 */
class TemplateCommandsTextTest {

    private static final Path PROJECT = Path.of("demo-project");

    @Test
    void anEmptyWindowsListSaysThereIsNoWindowsFirstRun() {
        String text = TemplateProjectDialog.commandsText(PROJECT, List.of(), true);
        assertEquals("# No Windows form of this template's first run. See its README.", afterCd(text));
        assertFalse(text.contains("declares no"), "every catalogue template declares its scripts");
    }

    @Test
    void anEmptyListElsewhereSaysNoScriptIsRecognised() {
        assertEquals("# This template ships no first-run script the analyser recognises. See its README.",
                afterCd(TemplateProjectDialog.commandsText(PROJECT, List.of(), false)));
    }

    @Test
    void offeredCommandsAreListedInOrder() {
        assertEquals("./mvnw package\n./run-and-export.sh",
                afterCd(TemplateProjectDialog.commandsText(PROJECT, List.of("./mvnw package", "./run-and-export.sh"), false)));
    }

    /** The first line is {@code cd <project>}, quoted per platform; the assertions are about what follows. */
    private static String afterCd(String text) {
        assertTrue(text.startsWith("cd ") && text.contains("demo-project"), text);
        return text.substring(text.indexOf('\n') + 1);
    }
}
