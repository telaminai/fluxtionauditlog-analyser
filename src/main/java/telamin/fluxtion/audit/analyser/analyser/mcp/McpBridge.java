package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.ui.ReleaseNotes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MCP stdio bridge (spec-assistant-actions-mcp §9, M13.2): a hand-rolled JSON-RPC 2.0 loop over
 * stdin/stdout, launched by an MCP client as {@code java -jar analyser.jar --mcp}. It publishes one tool
 * per action verb and (from M13.3) forwards calls to the running app's REST {@code /action}.
 *
 * <p><b>Dual-era.</b> MCP split into two eras and this bridge answers both, which the protocol explicitly
 * sanctions:
 * <ul>
 *   <li><b>Legacy</b> ({@code 2025-11-25} and earlier) — an {@code initialize} handshake negotiates the
 *       protocol version, then {@code tools/list} / {@code tools/call}.</li>
 *   <li><b>Modern</b> ({@code 2026-07-28}) — no handshake. Every request carries its version in
 *       {@code _meta}, servers must implement {@code server/discover}, results carry {@code resultType},
 *       and an unsupported version is answered with {@code -32022} listing what we do support.</li>
 * </ul>
 * The era is chosen per request by how the client opens: an {@code initialize} is legacy; a request
 * carrying {@code _meta.io.modelcontextprotocol/protocolVersion} is modern.
 *
 * <p><b>Headless.</b> An MCP client launches this in an arbitrary environment, and initializing AWT (on
 * macOS especially) can fail oddly, so {@link #main} sets {@code java.awt.headless} first and this path
 * touches no Swing/AWT class — {@code McpBridgeHeadlessTest} enforces that against the compiled bytecode.
 *
 * <p><b>stdout is the wire.</b> One JSON message per line, no embedded newlines, and nothing else may be
 * written to it — all diagnostics go to stderr.
 */
public final class McpBridge {

    /** The modern, stateless revision: per-request {@code _meta} versioning + {@code server/discover}. */
    static final String MODERN = "2026-07-28";

    /** Handshake-based revisions we answer, newest first — what {@code initialize} may negotiate to. */
    static final List<String> LEGACY = List.of("2025-11-25", "2025-06-18");

    /** Advertised by {@code server/discover} and by the {@code -32022} error. */
    static final List<String> SUPPORTED = List.of(MODERN, "2025-11-25", "2025-06-18");

    static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    static final String SERVER_NAME = "fluxtion-audit-log-analyser";

    private static final String INSTRUCTIONS =
            "Drives a running Fluxtion Audit Log Analyser over its localhost action socket. "
            + "Query verbs (analyser_aggregate, analyser_read) read the loaded audit log; the render verbs "
            + "change what the desktop app shows (filter, graph, goto, flag) and are all reversible. "
            + "The analyser must be running with the REST transport enabled (Settings > Assistant).";

    /** Tool list and identity are static for a process, so a client may cache them. */
    private static final long CACHE_TTL_MS = 3_600_000L;

    public static void main(String[] args) {
        // FIRST: an MCP client may launch us anywhere (no display, no window server) and AWT
        // initialization can fail oddly there. Nothing on this path touches Swing/AWT regardless.
        System.setProperty("java.awt.headless", "true");
        try {
            new McpBridge().run(System.in, System.out);
        } catch (IOException e) {
            System.err.println("[analyser-mcp] transport closed: " + e);
        }
    }

    /**
     * Read newline-delimited JSON-RPC from {@code in}, write responses to {@code out}. Returns when the
     * stream ends — a client shuts a stdio server down by closing its stdin, so EOF means exit.
     */
    void run(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String response = handle(line);
            if (response != null) {          // notifications get no response, ever
                writer.write(response);
                writer.write('\n');
                writer.flush();
            }
        }
    }

    /**
     * Handle one JSON-RPC message. Returns the response line, or {@code null} for a notification — a
     * message with no {@code id} must never be answered, and {@code notifications/initialized} arrives
     * on every legacy connection, so answering it with {@code -32601} would break the handshake.
     */
    String handle(String line) {
        Object root;
        try {
            root = Json.parse(line);
        } catch (RuntimeException e) {
            return error(null, -32700, "parse error: " + e.getMessage(), null);
        }
        if (!(root instanceof Map<?, ?> message)) {
            return error(null, -32600, "invalid request: expected a JSON-RPC object", null);
        }

        Object id = normalizeNumbers(message.get("id"));
        if (id == null) return null;                       // notification
        try {
            if (!(message.get("method") instanceof String method)) {
                return error(id, -32600, "invalid request: missing 'method'", null);
            }
            Map<?, ?> params = message.get("params") instanceof Map<?, ?> p ? p : Map.of();

            // Era selection. A version in _meta is the modern mechanism, so anything there that is not
            // the modern revision is answered with the list of versions we can actually speak.
            String requested = protocolVersionOf(params);
            boolean modern = requested != null;
            if (modern && !MODERN.equals(requested)) return unsupportedVersion(id, requested);

            return switch (method) {
                case "initialize" -> initialize(id, params);
                case "server/discover" -> discover(id, modern);
                case "tools/list" -> toolsList(id, modern);
                // "tools/call" arrives in M13.3, where it forwards to the app's REST /action
                default -> error(id, -32601, "method not found: " + method, null);
            };
        } catch (RuntimeException e) {
            return error(id, -32603, "internal error: " + e, null);   // one bad message must not kill the loop
        }
    }

    // ---- methods --------------------------------------------------------------------------------

    /**
     * Legacy handshake. If we speak the client's version we must echo it back; otherwise we answer with
     * the newest we do speak and the client decides whether it can continue.
     */
    private String initialize(Object id, Map<?, ?> params) {
        Object asked = params.get("protocolVersion");
        String negotiated = asked != null && LEGACY.contains(asked.toString())
                ? asked.toString()
                : LEGACY.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiated);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", serverInfo());
        result.put("instructions", INSTRUCTIONS);
        return response(id, result);
    }

    /**
     * Modern discovery — mandatory in {@code 2026-07-28}, and also the probe a dual-era client sends
     * first to learn which era it is talking to. Answered whatever era the caller claims, since its whole
     * purpose is to advertise what we support.
     */
    private String discover(Object id, boolean modern) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", "complete");
        result.put("supportedVersions", SUPPORTED);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("instructions", INSTRUCTIONS);
        result.put("ttlMs", CACHE_TTL_MS);
        result.put("cacheScope", "private");     // a local, per-user app; never cache in a shared proxy
        result.put("_meta", Map.of(META_SERVER_INFO, serverInfo()));
        return response(id, modern ? result : withoutModernFields(result));
    }

    /** One tool per verb, straight from {@link McpTools}. */
    private String toolsList(Object id, boolean modern) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (modern) {
            result.put("resultType", "complete");
        }
        result.put("tools", McpTools.list());
        if (modern) {
            // CacheableResult: required on modern list results; our tool set is fixed for the process
            result.put("ttlMs", CACHE_TTL_MS);
            result.put("cacheScope", "private");
            result.put("_meta", Map.of(META_SERVER_INFO, serverInfo()));
        }
        return response(id, result);
    }

    // ---- JSON-RPC plumbing ----------------------------------------------------------------------

    private static String protocolVersionOf(Map<?, ?> params) {
        if (!(params.get("_meta") instanceof Map<?, ?> meta)) return null;
        Object v = meta.get(META_PROTOCOL_VERSION);
        return v == null ? null : v.toString();
    }

    private static Map<String, Object> serverInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", SERVER_NAME);
        info.put("version", ReleaseNotes.version());   // manifest lookup only — no Swing/AWT on this path
        return info;
    }

    /** Strip the fields a legacy client has no schema for, so an old handshake sees an old-shaped result. */
    private static Map<String, Object> withoutModernFields(Map<String, Object> result) {
        Map<String, Object> legacy = new LinkedHashMap<>(result);
        legacy.remove("resultType");
        legacy.remove("ttlMs");
        legacy.remove("cacheScope");
        legacy.remove("_meta");
        return legacy;
    }

    private String unsupportedVersion(Object id, String requested) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supported", SUPPORTED);
        data.put("requested", requested);
        return error(id, -32022, "Unsupported protocol version", data);
    }

    static String response(Object id, Map<String, Object> result) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("result", result);
        return Json.write(m);
    }

    static String error(Object id, int code, String message, Map<String, Object> data) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (data != null) err.put("data", data);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("error", err);
        return Json.write(m);
    }

    /**
     * {@link Json} parses every number to {@code Double}, and writing that back emits {@code 1.0}. A
     * JSON-RPC {@code id} must be echoed <em>exactly</em> — a client that sent {@code 1} and is handed
     * {@code 1.0} may never match the response to its request — so integral doubles are narrowed back to
     * {@code long} here. String ids pass through untouched.
     */
    static Object normalizeNumbers(Object value) {
        return switch (value) {
            case Double d when d == Math.rint(d) && !d.isInfinite() && Math.abs(d) <= 9.007199254740992E15 ->
                    (long) (double) d;
            case Map<?, ?> m -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    out.put(String.valueOf(e.getKey()), normalizeNumbers(e.getValue()));
                }
                yield out;
            }
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                for (Object o : list) out.add(normalizeNumbers(o));
                yield out;
            }
            case null, default -> value;
        };
    }

    /** The methods this bridge answers — used by the tests and by the docs to stay honest. */
    static Set<String> methods() {
        return Set.of("initialize", "server/discover", "tools/list");
    }
}
