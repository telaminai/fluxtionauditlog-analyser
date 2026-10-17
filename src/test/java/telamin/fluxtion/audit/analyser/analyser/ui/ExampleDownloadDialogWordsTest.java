package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource;

import static org.junit.jupiter.api.Assertions.*;

/** Review S6 (P2, 2026-10-17; OD-74.30): the confirmation says what is proven — an address — and no more. */
class ExampleDownloadDialogWordsTest {

    static final String C = "0123456789abcdef0123456789abcdef01234567";

    /** ★ Witness: the old "   — Telamin" — the line claims Telamin published it. */
    @Test
    @DisplayName("★ a Telamin address is called an address that may be a fork; a third party is still third-party")
    void publisherLine() {
        var t = ExampleSource.parse("https://github.com/telaminai/fluxtion-examples/archive/" + C + ".zip", null).source();
        String line = ExampleDownloadDialog.publisherLine(t, t.thirdParty());
        assertTrue(line.endsWith("— a Telamin repository address (the code may come from a fork)"), line);
        assertFalse(line.matches(".*— Telamin$"), "never 'Telamin' as if authorship were proven");
        var x = ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", null).source();
        assertTrue(ExampleDownloadDialog.publisherLine(x, x.thirdParty()).endsWith("third-party: not published by Telamin"));
        assertTrue(StartPromptView.exampleDoes(t).contains("may come from a fork"), StartPromptView.exampleDoes(t));
    }

    /** ★ Review S7, the static half: the confirmation performer answers even when it throws (witness: remove the catch). */
    @Test
    @DisplayName("★ the confirmation performer catches and answers CONFIRM_FAILED")
    void theConfirmPerformerAlwaysAnswers() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int at = src.indexOf("private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result confirmExample(");
        assertTrue(at > 0);
        String body = src.substring(at, src.indexOf("/** FetchExampleEffect", at));
        assertTrue(body.contains("catch (RuntimeException ex)") && body.contains("\"CONFIRM_FAILED\""), body);
    }
}
