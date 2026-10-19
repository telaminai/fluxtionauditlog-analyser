package telamin.fluxtion.audit.analyser.analyser.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Admission transports the node's original ticket; it neither guesses one nor grants a new permission. */
class HostedOutputOriginTest {
    @Test void onlyAnAlreadyAllowedFullOpenCarriesTheOriginalAdmittedScope(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("DEMO"));
        Path graph = Files.writeString(root.resolve("out.graphml"), "DEMO");
        var origin = new AtomicReference<ActionDispatcher.HostedOpenOrigin>();
        var gate = gate(41);
        var full = new McpTools.OpenScope(root, true, null, 9, null, new McpTools.OutputScope(17, 6));
        String open = Json.write(Map.of("action", "open", "params", Map.of("graphml", graph.toString())));
        assertNull(ActionServer.hostedRefusal(gate, "fixture", open, null, null, g -> full, null, origin));
        assertEquals(new ActionDispatcher.HostedOpenOrigin(9, 41, 17, 6), origin.get(),
                "transport must retain the node's original ticket/watch, not read a newer ambient turn");

        origin.set(null);
        var safe = new McpTools.OpenScope(root, false, null, 9, null, full.outputs());
        assertNotNull(ActionServer.hostedRefusal(gate, "fixture", open, null, null, g -> safe, null, origin));
        assertNull(origin.get(), "an asked Safe open has no Full completion origin");
        assertNotNull(ActionServer.hostedRefusal(gate, "unknown", open, null, null, g -> full, null, origin));
        assertNull(origin.get(), "an unknown credential cannot acquire hosted origin");
        assertNull(ActionServer.hostedRefusal(gate(0), "fixture", open, null, null, g -> full, null, origin));
        assertNull(origin.get(), "legacy admission may work as before, but unknown ticket cannot suppress offers");
        assertNull(ActionServer.hostedRefusal(gate, "fixture", "{\"action\":\"context\",\"params\":{}}", null, null,
                g -> full, null, origin));
        assertNull(origin.get(), "a read verb must not acquire an open-completion origin");
    }
    private static HostedCredentials.Gate gate(long ticket) {
        return new HostedCredentials.Gate() {
            public long generationOf(String credential) { return "fixture".equals(credential) ? 9 : -1; }
            public String admit(long generation) { return null; }
            public HostedCredentials.Admission admitScoped(long generation, String verb) {
                return new HostedCredentials.Admission(null, ticket);
            }
        };
    }
}
