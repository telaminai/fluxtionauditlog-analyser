package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-AI9 — the status light's words, pinned here so they cannot drift in a Swing class. */
class McpIndicatorTest {

    private static McpIndicator.View view(McpSetupState.LocalStatus status) {
        return McpIndicator.of(new McpSetupState.LocalReadiness(status, "detail for " + status + "."));
    }

    @Test
    void greenSaysREADY_neverCONNECTED() {
        // the trap McpSetupState's javadoc names: this window serving is not a client talking to it
        var v = view(McpSetupState.LocalStatus.READY);
        assertEquals(McpIndicator.Level.GOOD, v.level());
        assertEquals("MCP ready", v.label());
        assertFalse(v.label().toLowerCase(Locale.ROOT).contains("connected"));
        assertTrue(v.detail().contains("not that a client is connected"),
                "the tooltip must retire the wrong reading explicitly: " + v.detail());
    }

    @Test
    void noStateSaysConnectedAnywhere() {
        for (McpSetupState.LocalStatus s : McpSetupState.LocalStatus.values()) {
            var v = view(s);
            assertFalse(v.label().toLowerCase(Locale.ROOT).contains("connect"),
                    s + " label claims a connection: " + v.label());
        }
    }

    @Test
    void anotherWindowOwningTheEndpointIsCalledOut_itIsTheExpensiveState() {
        // invisible today, and the one that has someone reading answers about a different log
        var v = view(McpSetupState.LocalStatus.OTHER_INSTANCE);
        assertEquals(McpIndicator.Level.ATTENTION, v.level());
        assertEquals("MCP elsewhere", v.label());
        assertTrue(v.detail().contains("not this one"), v.detail());
    }

    @Test
    void offIsNEUTRAL_becauseAChoiceIsNotAFault() {
        // colouring a deliberate decision as an error teaches people to ignore the light
        var v = view(McpSetupState.LocalStatus.OFF);
        assertEquals(McpIndicator.Level.NEUTRAL, v.level());
        assertTrue(v.detail().contains("Connect an AI client"), "say how to turn it on: " + v.detail());
        assertTrue(v.detail().contains("Nothing is wrong"),
                "off is a CHOICE — an indicator that alarms about a decision gets ignored: " + v.detail());
    }

    @Test
    void noStateIsEverAnALARM_redStaysUnspent() {
        // owner asked for red/orange/green (2026-08-28). There is no red: nothing in this set is broken,
        // so red stays available for the day something genuinely is.
        for (McpSetupState.LocalStatus s : McpSetupState.LocalStatus.values()) {
            var level = view(s).level();
            assertTrue(level == McpIndicator.Level.GOOD || level == McpIndicator.Level.ATTENTION
                    || level == McpIndicator.Level.NEUTRAL, s + " invented a level");
        }
        assertEquals(3, McpIndicator.Level.values().length, "three levels, and none of them is an alarm");
    }

    @Test
    void startingIsAttention() {
        assertEquals(McpIndicator.Level.ATTENTION, view(McpSetupState.LocalStatus.STARTING).level());
    }

    @Test
    void theTooltipCarriesTheEXISTINGdetail_notASecondWording() {
        // D-AI2: one message. A second sentence saying the same thing is a second thing to keep true.
        for (McpSetupState.LocalStatus s : McpSetupState.LocalStatus.values()) {
            assertTrue(view(s).detail().startsWith("detail for " + s + "."),
                    s + " dropped McpSetupState's own wording");
        }
    }

    @Test
    void everyStateHasWordsAndNoneIsNull() {
        for (McpSetupState.LocalStatus s : McpSetupState.LocalStatus.values()) {
            var v = view(s);
            assertNotNull(v.level());
            assertFalse(v.label().isBlank(), s + " has no label");
            assertFalse(v.detail().isBlank(), s + " has no tooltip");
        }
        var unknown = McpIndicator.of(null);
        assertEquals(McpIndicator.Level.NEUTRAL, unknown.level(), "not knowing is not an alarm");
    }
}
