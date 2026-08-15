package telamin.fluxtion.audit.analyser.analyser.net;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The localhost REST transport for assistant actions (spec-assistant-actions §5.2, §6): a JDK
 * {@link HttpServer} bound to <b>127.0.0.1</b> on an ephemeral port, so an external agentic client can
 * {@code POST /action} the same JSON the in-process path uses. Guards, in order:
 * <ul>
 *   <li><b>Reject any request carrying an {@code Origin} header</b> (agents/curl never send one; browsers
 *       always do) — strictly simpler and safer than parsing origins, moots DNS-rebinding.</li>
 *   <li><b>Token</b> via the {@code X-Analyser-Token} header (per-run nonce) — the server checks it, so
 *       the wrapped dispatcher is token-free.</li>
 *   <li><b>Token-bucket rate limit</b> → {@code 429}, so a runaway agent degrades gracefully.</li>
 * </ul>
 * {@code GET /manifest} advertises the verbs + caps so a client can pace itself instead of failing.
 */
public final class ActionServer {

    private static final String TOKEN_HEADER = "X-Analyser-Token";

    private final HttpServer server;
    private final ActionDispatcher dispatcher;
    private final String token;
    private final int maxActionsPerReply;
    private final double ratePerSec;
    private final RateLimiter limiter;
    private final int port;
    private final RestEndpointFile endpointFile;   // null = publish nothing (the default; tests, embedders)

    public ActionServer(ActionDispatcher dispatcher, String token, int maxActionsPerReply, double ratePerSec)
            throws IOException {
        this(dispatcher, token, maxActionsPerReply, ratePerSec, null);
    }

    /**
     * @param endpointFile where to publish the live url+token while the server runs (M13.1), or
     *                     {@code null} to publish nothing. Opt-in on purpose: the server is started for
     *                     real inside unit tests, and publishing to the well-known path from there would
     *                     clobber a developer's running analyser.
     */
    public ActionServer(ActionDispatcher dispatcher, String token, int maxActionsPerReply, double ratePerSec,
                        RestEndpointFile endpointFile) throws IOException {
        this.dispatcher = dispatcher;
        this.token = token;
        this.maxActionsPerReply = maxActionsPerReply;
        this.ratePerSec = ratePerSec;
        this.endpointFile = endpointFile;
        this.limiter = new RateLimiter(ratePerSec, Math.max(1, ratePerSec));
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.port = server.getAddress().getPort();
        server.createContext("/action", this::handleAction);
        server.createContext("/manifest", this::handleManifest);
        server.setExecutor(Executors.newFixedThreadPool(2));
    }

    public void start() {
        server.start();
        if (endpointFile != null) {
            try {
                endpointFile.write(url(), token);
            } catch (IOException e) {
                // REST itself is up and usable — only MCP discovery is degraded, so warn, never fail
                System.out.println("[analyser] could not publish " + endpointFile.path() + ": " + e);
            }
        }
    }

    public void stop() {
        if (endpointFile != null) endpointFile.delete();
        server.stop(0);
        if (server.getExecutor() instanceof java.util.concurrent.ExecutorService es) es.shutdownNow();
    }

    public int port() {
        return port;
    }

    public String url() {
        return "http://127.0.0.1:" + port;
    }

    private void handleAction(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, err("POST only"));
                return;
            }
            if (ex.getRequestHeaders().containsKey("Origin")) {   // browser cross-origin → reject outright
                send(ex, 403, err("requests carrying an Origin header are rejected"));
                return;
            }
            String provided = ex.getRequestHeaders().getFirst(TOKEN_HEADER);
            if (provided == null || !provided.equals(token)) {
                send(ex, 401, err("missing or bad " + TOKEN_HEADER));
                return;
            }
            if (!limiter.tryAcquire()) {
                send(ex, 429, err("rate limited (" + ratePerSec + "/s)"));
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ActionResult r = dispatcher.dispatch(body);
            send(ex, r.ok() ? 200 : 400, r.toJson());
        } catch (RuntimeException e) {
            send(ex, 500, err("server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private void handleManifest(HttpExchange ex) throws IOException {
        try {
            if (ex.getRequestHeaders().containsKey("Origin")) {
                send(ex, 403, err("requests carrying an Origin header are rejected"));
                return;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("v", ActionDispatcher.SCHEMA_VERSION);
            m.put("tokenHeader", TOKEN_HEADER);
            m.put("verbs", List.of("aggregate", "read", "filter", "graph", "goto", "flag"));
            m.put("schemas", telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all());
            m.put("maxActionsPerReply", maxActionsPerReply);
            m.put("rateLimitPerSec", ratePerSec);
            send(ex, 200, Json.write(m));
        } catch (RuntimeException e) {   // never abort the exchange bodiless (parity with handleAction)
            send(ex, 500, err("server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private static String err(String msg) {
        return Json.write(Map.of("ok", false, "error", msg));
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** A tiny thread-safe token bucket: {@code rate} tokens/sec, up to {@code capacity} burst. */
    static final class RateLimiter {
        private final double ratePerSec;
        private final double capacity;
        private double tokens;
        private long lastNanos;

        RateLimiter(double ratePerSec, double capacity) {
            this.ratePerSec = ratePerSec;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastNanos) / 1e9 * ratePerSec);
            lastNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
