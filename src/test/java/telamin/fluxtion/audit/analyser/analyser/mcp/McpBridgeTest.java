package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hand-rolled JSON-RPC stdio loop (spec-assistant-actions-mcp §2/§9, M13.2), driven by feeding it
 * frames. Covers both eras: the legacy {@code initialize} handshake and the modern per-request
 * {@code _meta} versioning with {@code server/discover}.
 */
class McpBridgeTest {

    private final McpBridge bridge = new McpBridge();

    private Object reply(String line) {
        String out = bridge.handle(line);
        assertNotNull(out, "expected a response for: " + line);
        assertFalse(out.contains("\n"), "a stdio frame must not contain embedded newlines");
        return Json.parse(out);
    }

    private Object result(String line, Object... path) {
        Object r = reply(line);
        assertNull(Json.at(r, "error"), "unexpected JSON-RPC error: " + Json.write(Json.at(r, "error")));
        Object result = Json.at(r, "result");
        assertNotNull(result, "a non-error response must carry a result");
        return path.length == 0 ? result : Json.at(result, path);
    }

    private int errorCode(String line) {
        Object r = reply(line);
        return ((Number) Json.at(r, "error", "code")).intValue();
    }

    // ---- legacy era ------------------------------------------------------------------------------

    @Test
    void initializeEchoesASupportedLegacyVersionAndAdvertisesTools() {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        assertEquals("2025-06-18", result(line, "protocolVersion"), "a supported version must be echoed back");
        assertNotNull(Json.at(result(line), "capabilities", "tools"), "we serve tools");
        assertEquals("fluxtion-audit-log-analyser", Json.at(result(line), "serverInfo", "name"));
        assertNotNull(Json.at(result(line), "serverInfo", "version"));
    }

    @Test
    void initializeFallsBackToOurNewestLegacyVersionWhenTheClientAsksForOneWeLack() {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"1999-01-01\"}}";
        assertEquals("2025-11-25", result(line, "protocolVersion"));
    }

    @Test
    void legacyResultsCarryNoModernFields() {
        Object r = result("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertNull(Json.at(r, "resultType"), "resultType is a modern-only field");
        assertNull(Json.at(r, "ttlMs"));
        assertNull(Json.at(r, "_meta"));
    }

    // ---- notifications ---------------------------------------------------------------------------

    @Test
    void notificationsAreNeverAnswered() {
        // every legacy client sends this straight after initialize; answering a message with no id
        // (even with -32601) violates JSON-RPC and breaks the handshake
        assertNull(bridge.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertNull(bridge.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                + "\"params\":{\"requestId\":1}}"));
        assertNull(bridge.handle("{\"jsonrpc\":\"2.0\",\"method\":\"some/unknown/notification\"}"),
                "unknown *notifications* are silently dropped, not answered with -32601");
    }

    // ---- tools/list ------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void toolsListReturnsOneToolPerVerb() {
        Object r = result("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        List<Object> tools = (List<Object>) Json.at(r, "tools");
        assertEquals(VerbSchemas.all().size(), tools.size());
        List<String> names = tools.stream().map(t -> (String) ((Map<String, Object>) t).get("name")).toList();
        assertTrue(names.contains("analyser_aggregate"));
        assertTrue(names.contains("analyser_read"));
        assertTrue(names.stream().allMatch(n -> n.startsWith("analyser_")));
    }

    // ---- modern era ------------------------------------------------------------------------------

    private static String modern(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":{\"_meta\":{"
                + "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}}";
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverDiscoverAdvertisesSupportedVersionsAndIdentity() {
        Object r = result(modern(3, "server/discover"));
        assertEquals("complete", Json.at(r, "resultType"));
        List<Object> versions = (List<Object>) Json.at(r, "supportedVersions");
        assertEquals(McpBridge.SUPPORTED, versions, "must advertise every version we can actually speak");
        assertTrue(versions.contains("2026-07-28"), "the modern revision");
        assertNotNull(Json.at(r, "capabilities", "tools"));
        assertEquals("fluxtion-audit-log-analyser",
                Json.at(r, "_meta", "io.modelcontextprotocol/serverInfo", "name"));
    }

    @Test
    void modernResultsCarryResultTypeAndCacheHints() {
        Object r = result(modern(4, "tools/list"));
        assertEquals("complete", Json.at(r, "resultType"), "required on every modern result");
        assertNotNull(Json.at(r, "ttlMs"), "CacheableResult: required on modern list results");
        assertEquals("private", Json.at(r, "cacheScope"), "a local per-user app is never shared-cacheable");
        assertNotNull(Json.at(r, "_meta", "io.modelcontextprotocol/serverInfo"));
        assertNotNull(Json.at(r, "tools"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnsupportedMetaVersionIsRejectedWithTheSupportedList() {
        String line = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\",\"params\":{\"_meta\":{"
                + "\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\"}}}";
        Object r = reply(line);
        assertEquals(-32022, ((Number) Json.at(r, "error", "code")).intValue(),
                "UnsupportedProtocolVersionError");
        assertEquals("1900-01-01", Json.at(r, "error", "data", "requested"));
        assertEquals(McpBridge.SUPPORTED, (List<Object>) Json.at(r, "error", "data", "supported"),
                "the client picks a mutually supported version from this list and retries");
    }

    // ---- protocol errors -------------------------------------------------------------------------

    @Test
    void unknownMethodWithAnIdIsMethodNotFound() {
        assertEquals(-32601, errorCode("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/list\"}"));
    }

    @Test
    void toolsCallIsNotServedUntilTheNextSlice() {
        // M13.3 replaces this with a REST forward; pinned so the slice boundary is visible
        assertEquals(-32601, errorCode("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"analyser_aggregate\",\"arguments\":{}}}"));
    }

    @Test
    void malformedJsonIsAParseError() {
        Object r = reply("{not json");
        assertEquals(-32700, ((Number) Json.at(r, "error", "code")).intValue());
        assertTrue(Json.write(r).contains("\"id\":null"), "a parse error carries a null id");
    }

    @Test
    void aNonObjectMessageIsAnInvalidRequest() {
        assertEquals(-32600, errorCode("[1,2,3]"));
    }

    // ---- id handling -----------------------------------------------------------------------------

    @Test
    void integerIdsAreEchoedAsIntegersNotDoubles() {
        // llm.Json parses every number to Double; echoing "id":1 back as "id":1.0 can stop a strict
        // client matching the response to its request
        String out = bridge.handle("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/list\"}");
        assertTrue(out.contains("\"id\":42,"), "expected an integral id, got: " + out);
        assertFalse(out.contains("42.0"));
    }

    @Test
    void stringIdsArePreserved() {
        String out = bridge.handle(modern(0, "server/discover").replace("\"id\":0", "\"id\":\"discover-1\""));
        assertTrue(out.contains("\"id\":\"discover-1\""), out);
    }

    @Test
    void normalizeNumbersLeavesRealsAlone() {
        assertEquals(1L, McpBridge.normalizeNumbers(1.0d));
        assertEquals(1.5d, McpBridge.normalizeNumbers(1.5d));
        assertEquals("x", McpBridge.normalizeNumbers("x"));
        assertNull(McpBridge.normalizeNumbers(null));
        assertEquals(List.of(1L, 2L), McpBridge.normalizeNumbers(List.of(1.0d, 2.0d)));
        assertEquals(Map.of("a", 3L), McpBridge.normalizeNumbers(Map.of("a", 3.0d)));
    }

    // ---- the loop --------------------------------------------------------------------------------

    @Test
    void runStreamsOneResponsePerLineAndStopsAtEndOfInput() throws IOException {
        String in = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                + "\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bridge.run(new ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)), out);

        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size(), "the notification and the blank line produce no output");
        assertTrue(lines.get(0).contains("\"id\":1"));
        assertTrue(lines.get(1).contains("analyser_aggregate"));
    }
}
