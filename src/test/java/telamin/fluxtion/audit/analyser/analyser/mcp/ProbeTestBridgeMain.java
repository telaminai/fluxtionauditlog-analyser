package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** A tiny child-process bridge double for M42.1's modern/legacy probe negotiation tests. */
public final class ProbeTestBridgeMain {
    private ProbeTestBridgeMain() { }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        boolean legacy = args.length > 0 && "legacy".equals(args[0]);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Map<String, Object> request = (Map<String, Object>) Json.parse(line);
                String method = String.valueOf(request.get("method"));
                Object id = request.get("id");
                Map<String, Object> response;
                if (legacy && "server/discover".equals(method)) {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "error", Map.of("code", -32601, "message", "method not found"));
                } else if ("server/discover".equals(method)) {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "result", Map.of("supportedVersions", List.of(McpBridge.MODERN)));
                } else if ("initialize".equals(method)) {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "result", Map.of("protocolVersion", McpBridge.LEGACY.get(0)));
                } else if ("tools/list".equals(method)) {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "result", Map.of("tools", List.of(Map.of("name", "analyser_context"))));
                } else if ("tools/call".equals(method)) {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "result", Map.of("isError", false, "content", List.of(Map.of("type", "text", "text", "{}"))));
                } else {
                    response = Map.of("jsonrpc", "2.0", "id", id,
                            "error", Map.of("code", -32601, "message", "method not found"));
                }
                out.write(Json.write(response));
                out.write('\n');
                out.flush();
            }
        }
    }
}
