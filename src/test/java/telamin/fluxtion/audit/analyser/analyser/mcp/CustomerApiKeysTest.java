package telamin.fluxtion.audit.analyser.analyser.mcp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ContextSections;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ★ The customer API's key sets, pinned (integration of 2026-10-18; REVIEWER-ORIENTATION §6.4). External clients — the
 * customer's developers and their agents — read {@code context} and receive the bridge's requests.
 * {@code WorkModeKeepsTheApiTest} pins the schemas, the tool lists, the instructions, the manifests and
 * {@code context.handoff} by digest; it does NOT cover {@code context}'s top-level and section keys or the bridge's
 * outgoing HTTP headers. This test does, against the last RELEASE ({@code main}, 1.35.1), with every addition since
 * listed by name:
 *
 * <ul>
 *   <li>{@code context}: every section and every key 1.35.1 published is still there, in its section; the only new keys
 *       are {@link #CONTEXT_ADDITIVE} ({@code project.proposals}, M74 P2b; {@code project.starters}, Option 2; {@code view.hidden},
 *       the records-filter fix).
 *       {@code ContextSectionsTest.theSectionTableCoversEveryKeyTheBuilderPuts} proves the section table is exactly
 *       the set of top-level keys {@code context} puts, so this pins the reply's top-level key set too.</li>
 *   <li>{@code hidden}'s own keys: {@code count}, {@code byFilter[]} of {@code filter}, {@code hides} and, for event
 *       types, {@code excludedTypes} — read from its one builder.</li>
 *   <li>the MCP bridge's request to {@code /action}, for an external client: 1.35.1 sent {@code Content-Type} and
 *       {@code X-Analyser-Token}, and still does, with the same values; {@link #HEADERS_ADDITIVE}
 *       ({@code X-Analyser-Client}, the client's self-reported name, and {@code X-Analyser-Probe}, the analyser's own
 *       connection probe; M77-3) are sent only when they apply.</li>
 * </ul>
 *
 * <p>Renaming, removing or moving an existing key or header is a breaking change for a customer: this fails. Adding one
 * fails too until it is listed here as additive, which is where review sees it. Witnesses (run 2026-10-18): drop
 * {@code "selection"} from the view section of {@code ContextSections.SECTIONS} — the context pin fails; stop sending
 * {@code X-Analyser-Token} from {@code McpBridge.toolsCall} — the header pin fails.
 */
class CustomerApiKeysTest {

    /** {@code ContextSections.SECTIONS} as released in 1.35.1 ({@code main}): the customer's contract. */
    static final Map<String, List<String>> CONTEXT_RELEASED;
    static {
        Map<String, List<String>> s = new LinkedHashMap<>();
        s.put("log", List.of("log", "provenance", "provenanceSource", "files", "inFlight", "dispatchOrder",
                "timeOrder", "producer", "rolledSetOffer"));
        s.put("project", List.of("project", "skills", "projectOffer", "fluxtionKey", "environments", "runbooks",
                "vocabulary", "analyses", "reportDestinations", "exports", "reports", "walks", "capture",
                "assistant", "bundles"));
        s.put("pairing", List.of("graphPairing"));
        s.put("processors", List.of("processors", "processorDeclarations"));
        s.put("source", List.of("source"));
        s.put("topology", List.of("topology"));
        s.put("view", List.of("filter", "showing", "selection", "flags", "spotlight"));
        s.put("charts", List.of("graphs", "graphAddresses", "graphAddressUnavailable", "graphScopes", "savedGraphs"));
        s.put("menus", List.of("menus", "menuChanges"));
        s.put("design", List.of("design", "restoration", "designSpotlights"));
        s.put("handoff", List.of("handoff"));
        CONTEXT_RELEASED = java.util.Collections.unmodifiableMap(s);
    }

    /** The keys added since 1.35.1, by section — additive only: nothing existing changed for them. */
    static final Map<String, List<String>> CONTEXT_ADDITIVE = Map.of(
            "project", List.of("proposals", "starters"), // M74 P2b: Claude Code's set-up proposal; Option 2: the starters
            "view", List.of("hidden"));               // the records-filter fix: what each filter hides

    /** The headers 1.35.1's bridge sent on every {@code /action} call. */
    static final Set<String> HEADERS_RELEASED = Set.of("Content-Type", "X-Analyser-Token");

    /** The headers added since, sent only when they apply (M77-3, OD-77.8). */
    static final Set<String> HEADERS_ADDITIVE = Set.of("X-Analyser-Client", "X-Analyser-Probe");

    @Test
    @DisplayName("★ context: every released section and key is unchanged; the only new keys are the listed additive ones")
    void contextKeepsItsReleasedKeys() {
        Map<String, List<String>> now = ContextSections.SECTIONS;
        assertEquals(CONTEXT_RELEASED.keySet(), new LinkedHashSet<>(now.keySet()),
                "the context sections are the released ones (renaming or removing one breaks a client's `sections`)");
        assertEquals(List.copyOf(CONTEXT_RELEASED.keySet()), List.copyOf(now.keySet()), "…in the released order");
        for (var e : CONTEXT_RELEASED.entrySet()) {
            List<String> keys = now.get(e.getKey());
            for (String k : e.getValue()) {
                assertTrue(keys.contains(k), "★ context." + k + " (section " + e.getKey() + ") was released and is gone");
            }
            List<String> added = new ArrayList<>(keys);
            added.removeAll(e.getValue());
            assertEquals(CONTEXT_ADDITIVE.getOrDefault(e.getKey(), List.of()), added,
                    "the keys added to section " + e.getKey() + " since 1.35.1 must be listed in CONTEXT_ADDITIVE");
            List<String> releasedOrder = new ArrayList<>(keys);
            releasedOrder.retainAll(e.getValue());
            assertEquals(e.getValue(), releasedOrder, "section " + e.getKey() + " keeps its released keys' order");
        }
    }

    @Test
    @DisplayName("★ context.hidden has the documented shape: count, byFilter[] of filter, hides and excludedTypes")
    void hiddenHasItsDocumentedShape() throws Exception {
        String frame = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int start = frame.indexOf("private static Map<String, Object> contextHidden(");
        assertTrue(start > 0, "context.hidden's one builder, MainFrame.contextHidden, was not found");
        String body = frame.substring(start, frame.indexOf("\n    }\n", start));
        Set<String> keys = new LinkedHashSet<>();
        Matcher put = Pattern.compile("put\\(\"([A-Za-z]+)\"").matcher(body);
        while (put.find()) keys.add(put.group(1));
        Matcher of = Pattern.compile("Map\\.of\\(\"([A-Za-z]+)\", \"[A-Za-z]+\", \"([A-Za-z]+)\"").matcher(body);
        while (of.find()) { keys.add(of.group(1)); keys.add(of.group(2)); }
        assertEquals(Set.of("count", "byFilter", "filter", "hides", "excludedTypes"), keys, "context.hidden's keys");
        Set<String> filters = new LinkedHashSet<>();
        Matcher f = Pattern.compile("\"filter\", \"([A-Za-z]+)\"").matcher(body);
        while (f.find()) filters.add(f.group(1));
        assertEquals(Set.of("eventTypes", "text", "timeRange", "flaggedOnly"), filters, "context.hidden's filter names");
    }

    @Test
    @DisplayName("★ the bridge's request headers: the released two, unchanged; Client and Probe only when they apply")
    void theBridgeSendsItsReleasedHeaders(@TempDir Path dir) throws Exception {
        List<Map<String, String>> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/action", x -> {
            Map<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            x.getRequestHeaders().forEach((k, v) -> h.put(k, String.join(",", v)));
            seen.add(h);
            x.getRequestBody().readAllBytes();
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().add("Content-Type", "application/json");
            x.sendResponseHeaders(200, out.length);
            x.getResponseBody().write(out);
            x.close();
        });
        server.start();
        try {
            RestEndpointFile file = new RestEndpointFile(dir.resolve("rest-endpoint"));
            file.write("http://127.0.0.1:" + server.getAddress().getPort(), "DEMO-token");
            String call = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"analyser_context\",\"arguments\":{}}}";

            // a client that never named itself, not a probe: exactly what 1.35.1 sent
            McpBridge plain = new McpBridge(file);
            assertNotNull(plain.handle(call));
            Map<String, String> first = seen.get(seen.size() - 1);
            assertEquals(HEADERS_RELEASED, analyserHeaders(first), "★ the released headers, and only those");
            assertEquals("application/json", first.get("Content-Type"));
            assertEquals("DEMO-token", first.get("X-Analyser-Token"), "the token, as the endpoint file holds it");

            // a client that named itself at initialize, through the analyser's own probe: the additive two as well
            McpBridge named = new McpBridge(file);
            var probe = McpBridge.class.getDeclaredField("probe");
            probe.setAccessible(true);
            probe.setBoolean(named, true);
            named.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":"
                    + "\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"DEMO client\",\"version\":\"1\"}}}");
            assertNotNull(named.handle(call));
            Map<String, String> second = seen.get(seen.size() - 1);
            Set<String> expected = new LinkedHashSet<>(HEADERS_RELEASED);
            expected.addAll(HEADERS_ADDITIVE);
            assertEquals(expected, analyserHeaders(second), "the released headers plus the listed additive ones");
            assertEquals("application/json", second.get("Content-Type"), "★ an existing header keeps its value");
            assertEquals("DEMO-token", second.get("X-Analyser-Token"), "★ an existing header keeps its value");
            assertEquals("DEMO client", second.get("X-Analyser-Client"));
            assertEquals("1", second.get("X-Analyser-Probe"));
        } finally {
            server.stop(0);
        }
    }

    /** The request's headers other than those the JDK's HTTP client adds to every request. */
    static Set<String> analyserHeaders(Map<String, String> headers) {
        Set<String> transport = Set.of("host", "connection", "content-length", "user-agent", "upgrade", "http2-settings");
        Set<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String k : headers.keySet()) if (!transport.contains(k.toLowerCase(Locale.ROOT))) out.add(k);
        Set<String> canonical = new LinkedHashSet<>();
        for (String k : out) {
            String match = null;
            for (String known : HEADERS_RELEASED) if (known.equalsIgnoreCase(k)) match = known;
            for (String known : HEADERS_ADDITIVE) if (known.equalsIgnoreCase(k)) match = known;
            canonical.add(match == null ? k : match);
        }
        return canonical;
    }
}
