package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The generic copy path must retain every launcher argument as data, never turn it into a shell string. */
class GenericMcpConfigurationTest {

    @Test
    void rendersACompleteStdioRecordFromTheExactJavaAndJarVector() {
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/Applications/Java 21/bin/java", "-Duser.home=/safe/home",
                "-jar", "/tmp/Fluxtion Analyser.jar"));

        String json = GenericMcpConfiguration.render(bridge);
        Map<?, ?> root = (Map<?, ?>) Json.parse(json);
        Map<?, ?> servers = (Map<?, ?>) root.get("mcpServers");
        Map<?, ?> server = (Map<?, ?>) servers.get("fluxtion-analyser");

        assertEquals("/Applications/Java 21/bin/java", server.get("command"));
        assertEquals(List.of("-Duser.home=/safe/home", "-jar", "/tmp/Fluxtion Analyser.jar", "--mcp"),
                server.get("args"));
        assertFalse(json.contains("rest-endpoint"));
        assertFalse(json.contains("X-Analyser-Token"));
    }

    @Test
    void rendersAnInstalledLauncherWithoutInventingArguments() {
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/Users/example/.jbang/bin/analyser"));
        Map<?, ?> root = (Map<?, ?>) Json.parse(GenericMcpConfiguration.render(bridge));
        Map<?, ?> server = (Map<?, ?>) ((Map<?, ?>) root.get("mcpServers")).get("fluxtion-analyser");

        assertEquals("/Users/example/.jbang/bin/analyser", server.get("command"));
        assertEquals(List.of("--mcp"), server.get("args"));
    }

    @Test
    void refusesAnAbsentBridgeInsteadOfWritingAnEmptyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> GenericMcpConfiguration.render(null));
    }
}
