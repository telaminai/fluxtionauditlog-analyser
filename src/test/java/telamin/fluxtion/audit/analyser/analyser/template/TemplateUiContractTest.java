package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUiContractTest {

    @Test
    void projectMenuOffersTemplateProjectAndUsesBackgroundIo() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertTrue(source.contains("projectMenu.add(newProjectFromTemplateItem())"));
        assertTrue(source.contains("new JMenuItem(\"New project from template…\")"));
        // Option 2 (owner, 2026-10-18): the menu states ONE intent; the session processor's starterProject decides each
        // step, and each step has one performer — the same chain a proposal's starter takes (OneDispatchModelTest)
        String menu = source.substring(source.indexOf("private void chooseTemplateProject()"),
                source.indexOf("private void chooseAndOpenProject()"));
        assertTrue(menu.contains("StarterMenuRequested("), "the menu is a fact");
        String download = source.substring(source.indexOf("private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result downloadStarter("),
                source.indexOf("private static String orDefault("));
        assertTrue(download.contains("Background.run("), "catalogue/download IO must stay off the EDT");
        assertTrue(download.contains("showProgress"), "network/archive work must expose progress and cancellation");
        assertTrue(download.contains("templateArchive.install"));
        String open = source.substring(source.indexOf("private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result openStarterProject("),
                source.indexOf("private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result openStarterDesign("));
        // M44: the template flow still adopts the installed project, but it no longer calls
        // ProjectSession directly — it states an intent and the session processor decides what that
        // transition means. The assertion tracks the intent, not the old call.
        assertTrue(open.contains("requestProject(profile"), "the installed template must be adopted as the active project");
        assertTrue(open.contains("TransitionKind.EXPLICIT_SWITCH"),
                "and it must say WHICH kind of transition it is — inferring that from the surface is "
                        + "exactly what M44 removed");
    }

    @Test
    void templatePathHasNoExecutionPrimitiveAndCommandsAreCopyOnly() throws Exception {
        String dialog = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/TemplateProjectDialog.java"));
        String archive = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/template/TemplateArchive.java"));
        String client = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/template/TemplateClient.java"));
        String combined = dialog + archive + client;
        assertFalse(combined.contains("ProcessBuilder"));
        assertFalse(combined.contains("Runtime.getRuntime"));
        assertFalse(combined.contains(".exec("));
        assertTrue(dialog.contains("Copy commands"));
        assertTrue(dialog.contains("commands are not executed"));
        // The catalogue entry now owns the complete disclosure; its behavioural tests pin keyNeed
        // independently of mode and tags, including absent metadata.
        assertTrue(dialog.contains("chosen.disclosure()"));
        assertFalse(dialog.contains("entry.mode()"), "the picker must not infer key need from AOT mode");
    }

    @Test
    void realHttpClientDoesNotFollowRedirects() throws Exception {
        String client = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/template/TemplateClient.java"));
        assertTrue(client.contains("HttpClient.Redirect.NEVER"));
        assertTrue(client.contains("https"));
        assertFalse(client.contains("Redirect.ALWAYS"));
    }
}
