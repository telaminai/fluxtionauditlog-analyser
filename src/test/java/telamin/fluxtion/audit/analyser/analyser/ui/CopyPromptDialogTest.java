package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SF-1, the dialog: what reaches the clipboard when Copy prompt opens, and when the box is ticked and unticked. Headless
 * — the body is a panel; only showing it needs a window.
 *
 * <p>Witness: change {@code compose.apply(false)} in {@link CopyPromptDialog#body} to {@code apply(true)} (or make the
 * box start ticked) and {@link #theFirstCopyNeverCarriesTheToken()} goes red.
 */
class CopyPromptDialogTest {

    private static final String TOKEN = "tok-DEMO-0123456789";

    private static Function<Boolean, PromptBuilder.CopiedPrompt> compose(String restUrl) {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        List<LogRecord> two = List.of(store.record(0), store.record(1));
        return withToken -> PromptBuilder.copyPrompt(two, null, null, null, "DEMO glossary", "why?", restUrl,
                restUrl == null ? null : TOKEN, 20, withToken);
    }

    @Test
    @DisplayName("SF-1: the first copy carries no token; the box adds it and taking it away removes it")
    void theFirstCopyNeverCarriesTheToken() throws Exception {
        List<String> clipboard = new ArrayList<>();
        UiTextTest.onEdt(() -> {
            JPanel body = CopyPromptDialog.body(compose("http://127.0.0.1:4711"), clipboard::add);
            assertEquals(1, clipboard.size(), "opening the dialog copies once");
            assertFalse(clipboard.get(0).contains(TOKEN), "and that copy has no token");
            JTextArea parts = (JTextArea) named(body, CopyPromptDialog.PARTS);
            assertTrue(parts.getText().startsWith("Copied: the analyser's standing instructions, the glossary, "
                    + "the raw text of 2 records and your question."), parts.getText());
            assertTrue(parts.getText().contains("Not copied: your local REST token."), parts.getText());
            JCheckBox box = (JCheckBox) named(body, CopyPromptDialog.TOKEN);
            assertNotNull(box, "REST is on, so the box is offered");
            assertEquals(PromptBuilder.TOKEN_BOX, box.getText());
            assertFalse(box.isSelected(), "unticked when the dialog opens");

            box.doClick();
            assertTrue(clipboard.get(clipboard.size() - 1).contains("X-Analyser-Token: " + TOKEN), "ticked: re-copied with it");
            assertTrue(parts.getText().contains(PromptBuilder.TOKEN_PART), parts.getText());
            assertTrue(((JTextArea) named(body, CopyPromptDialog.TEXT)).getText().contains(TOKEN), "and shown");
            box.doClick();
            assertFalse(clipboard.get(clipboard.size() - 1).contains(TOKEN), "unticked: re-copied without it");
        });
    }

    @Test
    @DisplayName("SF-1: with REST off there is no box and no token")
    void noBoxWithoutRest() throws Exception {
        List<String> clipboard = new ArrayList<>();
        UiTextTest.onEdt(() -> {
            JPanel body = CopyPromptDialog.body(compose(null), clipboard::add);
            assertNull(named(body, CopyPromptDialog.TOKEN), "nothing to offer");
            assertFalse(((JTextArea) named(body, CopyPromptDialog.PARTS)).getText().contains("REST token"));
            assertFalse(clipboard.get(0).contains("X-Analyser-Token"));
        });
    }

    private static Component named(JPanel root, String name) {
        for (Component c : UiTextTest.all(root)) if (name.equals(c.getName())) return c;
        return null;
    }
}
