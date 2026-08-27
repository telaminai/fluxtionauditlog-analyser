package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders the portable, no-token generic MCP stdio record from the same vector the probe launches. */
public final class GenericMcpConfiguration {

    private GenericMcpConfiguration() {
    }

    /**
     * A generic client's conventional {@code mcpServers} JSON. The first argument is the executable;
     * every later argument (including {@code --mcp}) remains a separate JSON string, never a shell split.
     */
    public static String render(McpLaunchCommand bridge) {
        if (bridge == null) throw new IllegalArgumentException("a bridge command is required");
        List<String> vector = bridge.command();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("command", vector.get(0));
        server.put("args", vector.subList(1, vector.size()));
        Map<String, Object> servers = new LinkedHashMap<>();
        servers.put(CodexMcpClient.SERVER_NAME, server);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mcpServers", servers);
        return Json.write(root);
    }
}
