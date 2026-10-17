package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.StreamJson;

import javax.swing.JButton;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 UX (P3 leftover c): a transcript tool line shows the call's input as the line carried it — the value the permission
 * dialog shows — never a re-serialisation of the parsed value, which wrote an integer {@code 3} as {@code 3.0}.
 */
class ToolLineRawInputTest {

    static final String LINE = "{\"type\":\"assistant\",\"message\":{\"id\":\"msg_1\",\"model\":\"m\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"Reading.\"},"
            + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"mcp__fluxtion-analyser-hosted__analyser_read\","
            + "\"input\":{\"count\":3,\"from\":0,\"ratio\":1.50}}]}}";

    /**
     * ★ Witness: build the tool line from {@code Json.write(input)} as before — the line reads {@code "count":3.0} and
     * the first assertion fails.
     */
    @Test
    @DisplayName("★ the tool line keeps an integer as the line carried it: count 3, not 3.0")
    void theToolLineIsTheRawInput() {
        var e = (StreamJson.AssistantMessage) StreamJson.parse(LINE);
        assertEquals("{\"count\":3,\"from\":0,\"ratio\":1.50}", e.toolInput(), "the raw slice, byte for byte");
        assertEquals("toolu_1", e.toolUseId());
        assertEquals("Reading.", e.text());
    }

    /** ★ Witness: re-parse the input when the line is expanded (the old {@code pretty}) — 3.0 and 1.5 come back. */
    @Test
    @DisplayName("★ expanded, the tool line's input is re-indented token by token: 3 stays 3, 1.50 stays 1.50")
    void theExpandedInputKeepsItsTokens() throws Exception {
        String shown = TranscriptViewTest.onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(TranscriptViewTest.answer("a",
                    "▸ analyser_read {\"count\":3,\"from\":0,\"ratio\":1.50} — ✓")));
            List<JButton> toggles = v.buttons("▸");
            assertEquals(1, toggles.size(), "one collapsible tool line");
            toggles.get(0).doClick();
            return v.getText();
        });
        assertTrue(shown.contains("\"count\": 3,"), shown);
        assertTrue(shown.contains("\"ratio\": 1.50"), shown);
        assertFalse(shown.contains("3.0"), shown);
    }

    @Test
    @DisplayName("a second tool_use in one message is sliced at its own index")
    void theSliceFollowsTheContentIndex() {
        String two = "{\"type\":\"assistant\",\"message\":{\"id\":\"m\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"a\",\"name\":\"Bash\",\"input\":{\"n\":1}},"
                + "{\"type\":\"tool_use\",\"id\":\"b\",\"name\":\"Bash\",\"input\":{\"n\":2, \"s\":\"x,]}\"}}]}}";
        var e = (StreamJson.AssistantMessage) StreamJson.parse(two);
        assertEquals("b", e.toolUseId(), "the parser keeps the last tool use, as before");
        assertEquals("{\"n\":2, \"s\":\"x,]}\"}", e.toolInput());
    }
}
