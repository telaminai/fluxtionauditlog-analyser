package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.ui.ReleaseNotes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /** Implementation-defined JSON-RPC error: the analyser isn't reachable (MCP reserves -32000..-32019). */
    static final int ERR_ANALYSER_UNREACHABLE = -32001;

    static final String NOT_RUNNING =
            "analyser not running, or REST transport disabled — enable it in Settings ▸ Assistant";

    /** An aggregate over a multi-GB log can take a while; a hung call must still eventually fail. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private final RestEndpointFile endpointFile;
    private final HttpClient http;

    public McpBridge() {
        this(RestEndpointFile.wellKnown());
    }

    McpBridge(RestEndpointFile endpointFile) {
        this.endpointFile = endpointFile;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

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
                case "tools/call" -> toolsCall(id, params, modern);
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

    /**
     * Forward one tool call to the running app's REST {@code /action} (spec §6). The two failure modes are
     * kept distinct on purpose:
     * <ul>
     *   <li>the <b>tool</b> failed (bad params, no log loaded, rate limited) → a result with
     *       {@code isError:true} carrying the dispatcher's own message, which is actionable feedback the
     *       model can self-correct from — the same feedback the in-process path gives it;</li>
     *   <li>the <b>transport</b> failed (app not running, REST disabled) → a JSON-RPC error, because no
     *       amount of re-prompting fixes it; the user has to start the app or enable the socket.</li>
     * </ul>
     */
    private String toolsCall(Object id, Map<?, ?> params, boolean modern) {
        Object rawName = params.get("name");
        String verb = McpTools.verbFor(rawName == null ? null : rawName.toString());
        if (verb == null) {
            // an unknown tool is a protocol error, not something the model can fix by retrying
            return error(id, -32602, "Unknown tool: " + rawName, null);
        }
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> a
                ? asStringKeyed(a) : Map.of();

        // read the endpoint on every call, not once at startup: the app may have restarted (new port and
        // token) or had its REST transport toggled since the last tool call
        RestEndpointFile.Endpoint endpoint = endpointFile.read();
        if (endpoint == null) return error(id, ERR_ANALYSER_UNREACHABLE, NOT_RUNNING, null);
        if (!endpoint.alive()) {
            // a file stranded by a crash — say so plainly rather than letting the POST fail obscurely
            return error(id, ERR_ANALYSER_UNREACHABLE, NOT_RUNNING,
                    Map.of("detail", "the analyser that published " + endpointFile.path()
                            + " (pid " + endpoint.pid() + ") is no longer running"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("action", verb);
        body.put("params", normalizeNumbers(arguments));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.url() + "/action"))
                    .header("Content-Type", "application/json")
                    .header("X-Analyser-Token", endpoint.token())
                    .timeout(CALL_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return toolResult(id, response, modern);
        } catch (IOException e) {
            // the endpoint file said alive, but nothing answered: REST toggled off, or the app is starting
            return error(id, ERR_ANALYSER_UNREACHABLE, NOT_RUNNING, Map.of("detail", String.valueOf(e)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(id, ERR_ANALYSER_UNREACHABLE, "interrupted while calling the analyser", null);
        }
    }

    /**
     * Wrap a REST reply as an MCP tool result. Any response carrying an {@code ok:false} body — including
     * {@code 401}/{@code 403}/{@code 429} — is a tool error, since {@code ActionServer} reports all of
     * them in the dispatcher's own shape and a rate limit in particular is worth retrying.
     */
    private String toolResult(Object id, HttpResponse<String> response, boolean modern) {
        String text = response.body() == null ? "" : response.body();
        boolean failed = response.statusCode() != 200;

        Object parsed = null;
        try {
            parsed = Json.parse(text);
        } catch (RuntimeException ignore) {
            // a non-JSON body (should not happen against our own server) is reported verbatim below
        }
        if (parsed instanceof Map<?, ?> m) {
            failed = !Boolean.TRUE.equals(m.get("ok"));
            if (failed && m.get("error") != null) text = m.get("error").toString();
        } else if (failed) {
            text = "the analyser returned HTTP " + response.statusCode() + ": " + text;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (modern) result.put("resultType", "complete");
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("isError", failed);
        if (modern) result.put("_meta", Map.of(META_SERVER_INFO, serverInfo()));
        return response(id, result);
    }

    private static Map<String, Object> asStringKeyed(Map<?, ?> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : in.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
        return out;
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
        return Set.of("initialize", "server/discover", "tools/list", "tools/call");
    }
}
