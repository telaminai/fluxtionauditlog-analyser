package telamin.fluxtion.audit.analyser.analyser.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.ActionServer;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 §4.2 — the hosted bridge: given its per-instance endpoint file it proves which spawn it is bound to
 * ({@code serverInfo.version = <version>+bind.<nonce>}) and sends that generation's hosted credential with every call;
 * without the argument it is the ordinary bridge, with neither.
 */
class McpBridgeBindingTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("--endpoint-file: the version carries +bind.<nonce> and every call carries X-Analyser-Hosted")
    void theHostedBridgeBindsAndIdentifiesItself() throws Exception {
        Map<String, String> seen = new ConcurrentHashMap<>();
        HttpServer fake = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        fake.createContext("/action", ex -> {
            seen.put("token", String.valueOf(ex.getRequestHeaders().getFirst("X-Analyser-Token")));
            seen.put("hosted", String.valueOf(ex.getRequestHeaders().getFirst(ActionServer.HOSTED_HEADER)));
            byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        fake.start();
        try {
            Path file = tmp.resolve("endpoint-1-1.json");
            new RestEndpointFile(file).writeHosted("http://127.0.0.1:" + fake.getAddress().getPort(), "shared",
                    "6f0ccc1c850248be", "hosted-credential");
            McpBridge bridge = McpBridge.forArguments(new String[]{"--mcp", McpBridge.ENDPOINT_FILE_FLAG, file.toString()});
            Map<?, ?> init = (Map<?, ?>) Json.parse(bridge.handle(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}"));
            String version = String.valueOf(((Map<?, ?>) ((Map<?, ?>) init.get("result")).get("serverInfo")).get("version"));
            assertTrue(version.endsWith("+bind.6f0ccc1c850248be"), version);
            bridge.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"analyser_context\","
                    + "\"arguments\":{}}}");
            assertEquals("shared", seen.get("token"));
            assertEquals("hosted-credential", seen.get("hosted"), "the hosted bridge identifies itself by its credential");

            seen.clear();
            McpBridge plain = new McpBridge(new RestEndpointFile(file));      // the ordinary bridge, same file
            assertFalse(plain.version().contains("+bind."), "only the hosted bridge reports a binding");
            plain.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"analyser_context\","
                    + "\"arguments\":{}}}");
            assertEquals("null", seen.get("hosted"), "an ordinary bridge never sends the hosted credential");
        } finally {
            fake.stop(0);
        }
        assertEquals(ActionServer.HOSTED_HEADER, McpBridge.HOSTED_HEADER, "one header name on both ends");
        assertEquals(McpBridge.ENDPOINT_FILE_FLAG,
                telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.ENDPOINT_FILE_FLAG);
    }
}
