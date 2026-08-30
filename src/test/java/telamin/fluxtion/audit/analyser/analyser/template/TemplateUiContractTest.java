package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUiContractTest {

    @Test
    void fileMenuOffersTemplateProjectAndUsesBackgroundIo() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertTrue(source.contains("file.add(newProjectFromTemplateItem())"));
        assertTrue(source.contains("new JMenuItem(\"New project from template…\")"));
        String flow = source.substring(source.indexOf("private void chooseTemplateProject()"),
                source.indexOf("private void chooseAndOpenProject()"));
        assertTrue(flow.contains("Background.run("), "catalogue/download IO must stay off the EDT");
        assertTrue(flow.contains("showProgress"), "network/archive work must expose progress and cancellation");
        assertTrue(flow.contains("templateArchive.install"));
        assertTrue(flow.contains("project.open(installed.profile())"));
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
        assertTrue(dialog.contains("entry.keyNeed()"));
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
