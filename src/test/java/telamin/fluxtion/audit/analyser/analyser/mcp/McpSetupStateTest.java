package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The setup surface's local fact is headless/pure; Swing only renders this result. */
class McpSetupStateTest {

    private static final RestEndpointFile.Endpoint OURS =
            new RestEndpointFile.Endpoint("http://127.0.0.1:1234", "never-rendered", 42, null);
    private static final RestEndpointFile.Endpoint OTHER =
            new RestEndpointFile.Endpoint("http://127.0.0.1:5678", "never-rendered", 17, null);

    @Test
    void disabledTransportIsOffEvenIfAnOldEndpointExists() {
        var readiness = McpSetupState.classify(false, OTHER, true, 42);
        assertEquals(McpSetupState.LocalStatus.OFF, readiness.status());
        assertFalse(readiness.canProbe());
    }

    @Test
    void enabledTransportWaitsForItsOwnLiveEndpoint() {
        assertEquals(McpSetupState.LocalStatus.STARTING,
                McpSetupState.classify(true, null, false, 42).status());
        assertEquals(McpSetupState.LocalStatus.STARTING,
                McpSetupState.classify(true, OURS, false, 42).status());
    }

    @Test
    void onlyThisWindowsLiveEndpointCanBeProbed() {
        var ready = McpSetupState.classify(true, OURS, true, 42);
        assertEquals(McpSetupState.LocalStatus.READY, ready.status());
        assertTrue(ready.canProbe());
        assertEquals(McpSetupState.LocalStatus.OTHER_INSTANCE,
                McpSetupState.classify(true, OTHER, true, 42).status());
    }
}
