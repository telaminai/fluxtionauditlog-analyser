package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stranger authoring trial, 2026-10-25: 0 of 5 connected Sonnet runs called the analyser; they answered "show me
 * evidence" and "explain what Fluxtion did with this event" from the YAML and told the person to open the analyser.
 * The MCP server instructions — the one text every client is handed before it picks a tool — now say what to do
 * FIRST when a project is open and a fresh export exists. That sentence is the only change to any external surface.
 *
 * <p>Witness: edit any other word of the instructions, or any schema — {@link #onlyTheInstructionsMoved} fails.
 */
class FirstMoveApiDeltaTest {

    /** The instructions' pin before this change (WorkModeKeepsTheApiTest held it from the Work-mode rename on). */
    static final String INSTRUCTIONS_BEFORE = "fdcf8b03dab8d45d9c197d230961a41518faa0319b6f5a9cc6a51662cfdf144a";

    /** Remove only {@link McpBridge#FIRST_MOVE}, recursively over any surface shape, so other delta chains compose. */
    @SuppressWarnings("unchecked")
    static <T> T withoutFirstMove(T value) {
        if (value instanceof String text) return (T) text.replace(McpBridge.FIRST_MOVE, "");
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutFirstMove(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(FirstMoveApiDeltaTest::withoutFirstMove).toList();
        return value;
    }

    @Test
    @DisplayName("★ the instructions gain only the first-move sentence: removed, they hash to the earlier pin")
    void removingTheSentenceGivesTheEarlierPinBack() throws Exception {
        assertEquals(INSTRUCTIONS_BEFORE, WorkModeKeepsTheApiTest.sha(withoutFirstMove(McpBridge.INSTRUCTIONS)));
        assertEquals(WorkModeKeepsTheApiTest.PINNED.get("McpBridge.INSTRUCTIONS"),
                WorkModeKeepsTheApiTest.sha(McpBridge.INSTRUCTIONS), "the pin now");
    }

    @Test
    void onlyTheInstructionsMoved() throws Exception {
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            if (surface.getKey().equals("McpBridge.INSTRUCTIONS")) continue;
            assertEquals(surface.getValue(), withoutFirstMove(surface.getValue()), surface.getKey() + " carries no first move");
        }
    }

    @Test
    void theSentenceSaysOpenThenWaitThenPointThenExplain() {
        String f = McpBridge.FIRST_MOVE;
        assertTrue(McpBridge.INSTRUCTIONS.contains(f));
        assertTrue(f.toLowerCase(java.util.Locale.ROOT).contains("fresh export"), "when it applies");
        assertTrue(f.contains("evidence") && f.contains("what Fluxtion did with"), "the trial's own words for the ask");
        int open = f.indexOf("analyser_open"), wait = f.indexOf("inFlight"), point = f.indexOf("analyser_spotlight"),
                flag = f.indexOf("analyser_flag"), explain = f.indexOf("only then explain");
        assertTrue(open >= 0 && open < wait && wait < point && point < flag && flag < explain,
                "open, wait, point, flag, explain — in that order (trial r2: 0 of 10 runs kept a flag)");
        assertTrue(f.contains("records:row:<recordIndex>"), "a record target in the vocabulary's own grammar");
        assertTrue(SpotlightTarget.parse("records:row:0").ok());
        assertTrue(f.contains("WHEN THE PERSON ASKS") && f.contains("ask first") && f.contains("never replace it silently"),
                "applies on request, and never silently replaces a log the person has open");
        assertFalse(f.contains("CLAUDE.md"), "no project file is assumed: only starters have one");
        assertTrue(f.contains("refused"), "and it says what a too-early record target does");
    }
}
