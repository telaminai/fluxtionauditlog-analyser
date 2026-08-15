package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.ActionServer;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code tools/call} end-to-end (spec-assistant-actions-mcp §6, M13.3): a real bridge, a real
 * {@link ActionServer} on loopback, and the real endpoint file joining them — the full path an MCP
 * client takes, over actual HTTP, with only the desktop UI absent.
 *
 * <p>The distinction under test is §6's: a <b>tool</b> failure is feedback the model can act on
 * ({@code isError:true}), while a <b>transport</b> failure is a JSON-RPC error the user must fix.
 */
class McpToolCallTest {

    @TempDir
    Path dir;

    private ActionServer server;
    private McpBridge bridge;
    private RestEndpointFile endpointFile;

    @BeforeEach
    void start() throws IOException {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        // no RenderExecutor: exactly like the app before a log is graphable, so the render verbs
        // return the dispatcher's own ok:false — a real error string to assert against
        ActionDispatcher dispatcher =
                new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        endpointFile = new RestEndpointFile(dir.resolve("rest-endpoint"));
        server = new ActionServer(dispatcher, "s3cr3t", 20, 100.0, endpointFile);
        server.start();
        bridge = new McpBridge(endpointFile);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private Object call(String tool, String argumentsJson) {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"" + tool + "\",\"arguments\":" + argumentsJson + "}}";
        String out = bridge.handle(line);
        assertNotNull(out);
        return Json.parse(out);
    }

    @SuppressWarnings("unchecked")
    private String textOf(Object response) {
        List<Object> content = (List<Object>) Json.at(response, "result", "content");
        assertNotNull(content, "a tool result must carry content: " + Json.write(response));
        return (String) ((Map<String, Object>) content.get(0)).get("text");
    }

    @Test
    void aggregateRoundTripsThroughRestToTheDispatcher() {
        Object r = call("analyser_aggregate", "{\"metric\":\"count\",\"groupBy\":\"dimension\"}");
        assertNull(Json.at(r, "error"), "a working call is never a JSON-RPC error");
        assertEquals(false, Json.at(r, "result", "isError"));

        String text = textOf(r);
        assertTrue(text.contains("\"ok\":true"), text);
        assertTrue(text.contains("\"total\""), "the dispatcher's aggregate payload comes back intact: " + text);
    }

    @Test
    void readRoundTripsAndCarriesRecordText() {
        Object r = call("analyser_read", "{\"recordIndex\":0,\"count\":1}");
        assertEquals(false, Json.at(r, "result", "isError"));
        assertTrue(textOf(r).contains("\"ok\":true"));
    }

    @Test
    void aDispatcherRejectionIsAToolErrorNotATransportError() {
        // the model can fix this one itself, so it must arrive as isError:true with the message
        Object r = call("analyser_graph", "{\"series\":[\"a.b\"]}");
        assertNull(Json.at(r, "error"), "a rejected verb is not a JSON-RPC error");
        assertEquals(true, Json.at(r, "result", "isError"));
        assertTrue(textOf(r).contains("not enabled"), "carries the dispatcher's own message: " + textOf(r));
    }

    @Test
    void badParamsComeBackAsActionableText() {
        Object r = call("analyser_aggregate", "{\"metric\":\"no_such_metric\"}");
        assertEquals(true, Json.at(r, "result", "isError"));
        assertFalse(textOf(r).isBlank(), "the model needs something to correct against");
    }

    @Test
    void integerArgumentsSurviveTheHop() {
        // llm.Json parses to Double; a byteOffset must not reach the dispatcher as 1.0
        Object r = call("analyser_read", "{\"recordIndex\":1,\"count\":2}");
        assertEquals(false, Json.at(r, "result", "isError"), textOf(r));
    }

    @Test
    void theTokenIsSentAndTheCallFailsCleanlyWithoutIt() throws IOException {
        // rewrite the endpoint file with a wrong token: the server 401s, and per §6 an ok:false body
        // is a tool error rather than a dead transport
        endpointFile.write(server.url(), "not-the-token");
        Object r = call("analyser_aggregate", "{\"metric\":\"count\"}");
        assertNull(Json.at(r, "error"));
        assertEquals(true, Json.at(r, "result", "isError"));
        assertTrue(textOf(r).contains("X-Analyser-Token"), textOf(r));
    }

    @Test
    void aRestartedAppIsPickedUpWithoutRestartingTheBridge() throws IOException {
        assertEquals(false, Json.at(call("analyser_aggregate", "{\"metric\":\"count\"}"), "result", "isError"));

        // the app restarts: new ephemeral port, new token, same well-known file. The bridge re-reads the
        // endpoint on every call, so a long-lived MCP client survives an analyser restart.
        server.stop();
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher dispatcher =
                new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        server = new ActionServer(dispatcher, "a-fresh-token", 20, 100.0, endpointFile);
        server.start();

        Object r = call("analyser_aggregate", "{\"metric\":\"count\"}");
        assertNull(Json.at(r, "error"), "the bridge must find the new endpoint by itself");
        assertEquals(false, Json.at(r, "result", "isError"));
    }

    @Test
    void stoppingTheAppTurnsCallsIntoACleanTransportError() {
        server.stop();   // deletes the endpoint file
        Object r = call("analyser_aggregate", "{\"metric\":\"count\"}");
        assertEquals(McpBridge.ERR_ANALYSER_UNREACHABLE, ((Number) Json.at(r, "error", "code")).intValue());
        assertEquals(McpBridge.NOT_RUNNING, Json.at(r, "error", "message"),
                "the user is told what to do, not shown a connection-refused stack");
    }

    @Test
    void rateLimitingIsAToolErrorSoTheModelCanBackOff() throws IOException {
        server.stop();
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher dispatcher =
                new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        server = new ActionServer(dispatcher, "s3cr3t", 20, 1.0, endpointFile);   // 1/s bucket
        server.start();

        boolean sawRateLimit = false;
        for (int i = 0; i < 20 && !sawRateLimit; i++) {
            Object r = call("analyser_aggregate", "{\"metric\":\"count\"}");
            assertNull(Json.at(r, "error"), "a 429 must never read as a dead transport");
            if (Boolean.TRUE.equals(Json.at(r, "result", "isError")) && textOf(r).contains("rate limited")) {
                sawRateLimit = true;
            }
        }
        assertTrue(sawRateLimit, "a burst past the bucket should surface as a retryable tool error");
    }

    @Test
    void modernCallsCarryResultTypeAndServerInfo() {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"analyser_aggregate\",\"arguments\":{\"metric\":\"count\"},"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        Object r = Json.parse(bridge.handle(line));
        assertEquals("complete", Json.at(r, "result", "resultType"));
        assertNotNull(Json.at(r, "result", "_meta", "io.modelcontextprotocol/serverInfo"));
        assertEquals(false, Json.at(r, "result", "isError"));
    }
}
