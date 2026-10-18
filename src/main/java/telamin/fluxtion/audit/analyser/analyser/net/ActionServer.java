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
 *   <li><b>Token-bucket rate limit</b> → {@code 429}, so a runaway agent degrades gracefully. The hosted bridge has its
 *       own bucket (review L2), so an external client cannot starve the analyser's own Claude Code session, nor the
 *       reverse.</li>
 * </ul>
 * <p><b>Two modes (review H4).</b> With external clients allowed (the REST switch on) it serves both external clients
 * and the hosted bridge, and publishes the well-known endpoint file. <b>Hosted-only</b> (the REST switch off while a
 * Claude Code session needs the bridge) it publishes nothing and refuses every request that does not carry a hosted
 * credential, whatever shared token it presents: turning on Claude Code never opens the analyser to external
 * clients.
 * {@code GET /manifest} advertises the verbs + caps so a client can pace itself instead of failing.
 */
public final class ActionServer {

    /** Package-visible so a test can assert the discovery reply names the real header. */
    static final String TOKEN_HEADER = "X-Analyser-Token";

    /**
     * M74 r6 §4.2: the hosted bridge's own credential, minted per generation and written only to the per-instance
     * endpoint file. A request without it is an external client, whatever token it carries.
     */
    public static final String HOSTED_HEADER = "X-Analyser-Hosted";

    /**
     * M74 P3 (P3-3): the {@code tool_use_id} the CLI put in the call's {@code _meta["claudecode/toolUseId"]}, forwarded
     * by the hosted bridge. A single-use grant matches it (OD-74.17); an external caller's is ignored.
     */
    public static final String TOOL_USE_HEADER = "X-Analyser-Tool-Use";

    /**
     * M77-3 (spec-ai-setup-wizard.md §9.4): set by the analyser's OWN connection probe ({@code McpConnectionProbe}, the
     * client dialog's <i>Check connection</i>) through its bridge child — a marked call never counts as an external
     * client reaching this window. An external caller can set it too: that only hides it from the live check (a false
     * negative, never a false positive).
     */
    public static final String PROBE_HEADER = "X-Analyser-Probe";

    /**
     * M77-3 (OD-77.8): the client's self-reported name ({@code clientInfo.name}), forwarded by the bridge as a display
     * label only — ASCII, at most {@value #LABEL_LIMIT} characters, never trusted for anything else.
     */
    public static final String CLIENT_LABEL_HEADER = "X-Analyser-Client";
    static final int LABEL_LIMIT = 40;

    /** M77-3 (§9.4): the live check's one-shot report — the first counted external call after it was armed. */
    @FunctionalInterface
    public interface ClientWatch {
        /** {@code label}: the client's self-reported name, sanitised, or "" when it gave none. */
        void seen(String label);
    }

    private final java.util.concurrent.atomic.AtomicReference<ClientWatch> clientWatch =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * Arm the live check: the FIRST request to {@code /action} after this that carries no hosted credential, passed the
     * shared-token check and the rate limit, and is not the analyser's own probe, is reported once — then the watch is
     * gone. Not {@code /manifest}, not a refused (401/403/429) call; a 400 from the dispatcher still counts (the client
     * reached this window). Replaces a watch already armed.
     */
    public void watchExternalClient(ClientWatch watch) {
        clientWatch.set(watch);
    }

    /** Disarm the live check: a later call is not reported. */
    public void unwatchExternalClient() {
        clientWatch.set(null);
    }

    /** OD-77.8: a display label from what a client said of itself — printable ASCII only, trimmed and cut short. */
    static String sanitisedLabel(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length() && b.length() < LABEL_LIMIT; i++) {
            char c = raw.charAt(i);
            if (c >= 0x20 && c < 0x7f && c != '"' && c != '\\' && c != '<' && c != '>') b.append(c);
        }
        return b.toString().trim();
    }

    /** Null until the local agent is installed: then every hosted credential is unknown and refused. */
    private volatile HostedCredentials.Gate hostedGate;

    private final HttpServer server;
    private final ActionDispatcher dispatcher;
    private final String token;
    private final int maxActionsPerReply;
    private final double ratePerSec;
    private final RateLimiter limiter;
    /** Review L2: the hosted bridge's own bucket. */
    private final RateLimiter hostedLimiter;
    private final int port;
    private final RestEndpointFile endpointFile;   // null = publish nothing (the default; tests, embedders)
    /** Review H4: false = hosted-only — no well-known file, and only a hosted credential is served. */
    private volatile boolean externalAllowed = true;
    /** Review M74 usability #11: why external clients are refused — the switch is off, or closed for a session. */
    private volatile boolean closedForSession;

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
        this.hostedLimiter = new RateLimiter(ratePerSec, Math.max(1, ratePerSec));
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.port = server.getAddress().getPort();
        server.createContext("/action", this::handleAction);
        server.createContext("/manifest", this::handleManifest);
        // Round 06, both arms: two independent agents holding a valid token tried ~20 path/method/auth
        // combinations each and got the JDK's bare "No context found for request" every time. Neither
        // reached the analyser; both abandoned it and read the YAML directly. We publish a url and a
        // token and no way to learn the protocol — and /manifest, which answers everything, is itself
        // undiscoverable. A wrong path must therefore say where the right one is.
        server.createContext("/", this::handleUnknownPath);
        server.setExecutor(Executors.newFixedThreadPool(2));
    }

    public void start() {
        server.start();
        republish();
    }

    /**
     * Review H4: a server that serves ONLY the hosted bridge — it never writes the well-known endpoint file, and a
     * request without a hosted credential is refused (403) even with the shared token. Used when the external REST
     * switch is off but a Claude Code session needs the bridge.
     */
    public static ActionServer hostedOnly(ActionDispatcher dispatcher, String token, int maxActionsPerReply,
                                          double ratePerSec, RestEndpointFile endpointFile) throws IOException {
        ActionServer s = new ActionServer(dispatcher, token, maxActionsPerReply, ratePerSec, endpointFile);
        s.externalAllowed = false;
        return s;
    }

    /**
     * Allow or refuse external clients on this running server (review H4): the REST switch turned on while a hosted-only
     * server runs opens it to external clients and publishes the well-known file; turned off while a Claude Code
     * session still needs it, withdraws the file and refuses them again. The port, and so the session's per-instance
     * endpoint file, stay valid either way.
     */
    public void setExternalAllowed(boolean allowed) {
        externalAllowed = allowed;
        if (allowed) republish();
        else if (endpointFile != null) endpointFile.deleteIfOwnedByThisProcess();
    }

    public boolean externalAllowed() {
        return externalAllowed;
    }

    /**
     * Review M74 usability #11: external clients are refused because the person closed external access while an in-app
     * Claude Code session runs — they are told so, never that the REST switch is off (Settings still says it is on).
     */
    public void setClosedForSession(boolean closed) {
        closedForSession = closed;
    }

    /** What a refused external client is told, now. */
    String externalRefusal() {
        return closedForSession ? EXTERNAL_CLOSED_FOR_SESSION : EXTERNAL_OFF;
    }

    /** M74: how a hosted credential maps to its generation, and whether the session admits that generation now. */
    public void setHostedGate(HostedCredentials.Gate gate) {
        this.hostedGate = gate;
    }

    /**
     * Owner decision 2026-10-18: which charts exist now, so a hosted {@code graph} call that creates a chart is told
     * apart from one that replaces an existing chart's definition. Unset, every name is taken to exist (fail-closed).
     */
    private volatile telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ChartState chartState;

    public void setChartState(telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ChartState charts) {
        this.chartState = charts;
    }

    /**
     * Owner 2026-10-18: a hosted generation's project folder and posture ({@code McpTools.OpenScope}), so an in-project
     * {@code open} under Full is admitted as the adapter judged it. Unset or null: every hosted {@code open} needs the
     * grant. Never read for an external caller.
     */
    private volatile java.util.function.LongFunction<telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.OpenScope> hostedScopes;

    public void setHostedScopes(
            java.util.function.LongFunction<telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.OpenScope> scopes) {
        this.hostedScopes = scopes;
    }

    /**
     * (Re)write the well-known endpoint file for this running server. Called at start, and again by the
     * owner window when the file has gone missing or names a dead process while this server is still
     * listening — the pointer is disposable, the server is not. Returns false when nothing was published
     * (no file configured, or the write failed — which is reported, never thrown: REST itself is up and
     * usable, only MCP discovery is degraded).
     */
    public boolean republish() {
        if (endpointFile == null || !externalAllowed) return false;   // H4: hosted-only publishes nothing
        try {
            endpointFile.write(url(), token);
            return true;
        } catch (IOException e) {
            System.out.println("[analyser] could not publish " + endpointFile.path() + ": " + e);
            return false;
        }
    }

    public void stop() {
        if (endpointFile != null && externalAllowed) endpointFile.delete();
        server.stop(0);
        if (server.getExecutor() instanceof java.util.concurrent.ExecutorService es) es.shutdownNow();
    }

    public int port() {
        return port;
    }

    public String url() {
        return "http://127.0.0.1:" + port;
    }

    /**
     * The guards every route shares, in the order the security contract requires.
     *
     * <p><b>Origin comes first, on every route</b> (review F2). The class contract is that any request
     * carrying {@code Origin} is refused outright — the deliberately blunt DNS-rebinding posture — and a
     * method or path reply ahead of it would answer a browser we said we would not answer.
     *
     * <p><b>Then the EXACT path</b> (review F1). {@code HttpServer} dispatches by longest matching
     * PREFIX, not by exact match, so {@code /action/not-a-route} reaches this handler and, with a valid
     * token, executed the action. The route said the path was wrong while doing the work anyway.
     *
     * @return true when the request has been answered and the caller must stop.
     */
    private boolean refusedByCommonGuards(HttpExchange ex, String exactPath, String method)
            throws IOException {
        if (ex.getRequestHeaders().containsKey("Origin")) {   // browser cross-origin → reject outright
            send(ex, 403, err("requests carrying an Origin header are rejected"));
            return true;
        }
        if (refusedAsExternal(ex)) return true;
        if (!exactPath.equals(ex.getRequestURI().getPath())) {
            sendDiscovery(ex);           // a near-miss gets the signpost, never the handler
            return true;
        }
        if (!method.equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, err(method + " only"));
            return true;
        }
        return false;
    }

    private void handleAction(HttpExchange ex) throws IOException {
        try {
            if (refusedByCommonGuards(ex, "/action", "POST")) return;
            String hosted = ex.getRequestHeaders().getFirst(HOSTED_HEADER);
            // M74 review finding 6: the hosted bridge authenticates with its own per-generation credential alone (its
            // endpoint file no longer carries the shared token); every other caller needs the shared token. A presented
            // hosted credential that is not live is refused below, never treated as an external call.
            if (hosted == null) {
                String provided = ex.getRequestHeaders().getFirst(TOKEN_HEADER);
                if (provided == null || !provided.equals(token)) {
                    send(ex, 401, err("missing or bad " + TOKEN_HEADER));
                    return;
                }
            }
            // holistic review L-a (2026-10-17): the hosted credential is checked BEFORE its bucket, so a caller with a
            // made-up credential cannot spend the analyser's own Claude Code session's rate limit
            if (hosted != null && (hostedGate == null || hostedGate.generationOf(hosted) < 0)) {
                send(ex, 403, err(BAD_HOSTED_CREDENTIAL));
                return;
            }
            if (!(hosted != null ? hostedLimiter : limiter).tryAcquire()) {   // L2: separate buckets
                send(ex, 429, err("rate limited (" + ratePerSec + "/s)"));
                return;
            }
            if (hosted == null && !ex.getRequestHeaders().containsKey(PROBE_HEADER)) {
                // M77-3 (§9.4): an authenticated external call reached THIS window — reported once to an armed watch
                ClientWatch w = clientWatch.getAndSet(null);
                if (w != null) {
                    try {
                        w.seen(sanitisedLabel(ex.getRequestHeaders().getFirst(CLIENT_LABEL_HEADER)));
                    } catch (RuntimeException ignored) {
                        // the report is the watcher's; a failure there never fails the client's call
                    }
                }
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (hosted != null) {
                var charts = chartState;
                String refused = hostedRefusal(hostedGate, hosted, body, ex.getRequestHeaders().getFirst(TOOL_USE_HEADER),
                        charts, hostedScopes);
                if (refused != null) {
                    send(ex, 403, err(refused));
                    return;
                }
                body = hostedConstrained(body, charts);
            }
            // M74 P2: a hosted call carries its admitted generation, the only way to reach `propose`
            ActionResult r = hosted != null && hostedGate != null
                    ? dispatcher.dispatchHosted(body, hostedGate.generationOf(hosted))
                    : dispatcher.dispatch(body);
            send(ex, r.ok() ? 200 : 400, r.toJson());
        } catch (RuntimeException e) {
            send(ex, 500, err("server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    /**
     * Anything that is not {@code /action} or {@code /manifest}. Still a 404 — the path really is wrong —
     * but the body names the two routes, the envelope and the auth header, so one wrong guess is enough
     * to find the right one. Deliberately served WITHOUT a token: it discloses the shape of the API, never
     * any data, and a caller who cannot authenticate still needs to learn they are at the wrong door.
     */
    private void handleUnknownPath(HttpExchange ex) throws IOException {
        // Origin first here too (F2): this is the browser-addressable surface, so it must not be the one
        // route that answers a cross-origin caller.
        if (ex.getRequestHeaders().containsKey("Origin")) {
            send(ex, 403, err("requests carrying an Origin header are rejected"));
            return;
        }
        if (refusedAsExternal(ex)) return;
        sendDiscovery(ex);
    }

    /**
     * H4: in hosted-only mode, a request without a LIVE hosted credential is refused before anything else is said. The
     * header merely being present is not enough (review R3, PR #145): a made-up or revoked value is an external client.
     */
    private boolean refusedAsExternal(HttpExchange ex) throws IOException {
        if (externalAllowed) return false;
        HostedCredentials.Gate gate = hostedGate;
        String hosted = ex.getRequestHeaders().getFirst(HOSTED_HEADER);
        if (gate != null && hosted != null && gate.generationOf(hosted) >= 0) return false;
        send(ex, 403, err(externalRefusal()));
        return true;
    }

    /** A hosted credential this analyser did not issue, or has revoked. */
    static final String BAD_HOSTED_CREDENTIAL = "the hosted credential is not one this analyser issued, or it was revoked "
            + "when its Claude Code session stopped";

    /** What an external client is told by a hosted-only server. */
    static final String EXTERNAL_OFF = "External MCP/REST access is off in this analyser (Settings ▸ Assistant); "
            + "this listener serves only the analyser's own Claude Code session";

    /** Review M74 usability #11: what an external client is told while external access is closed for a session. */
    static final String EXTERNAL_CLOSED_FOR_SESSION = "external access is closed while an in-app Claude Code session runs "
            + "(the person chose \"Close external access while this session runs\"); it reopens when that session ends — "
            + "the External MCP/REST access setting is still on";

    /** The signpost: still 404, but enough to reach a working call on the next attempt. */
    private void sendDiscovery(HttpExchange ex) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", "no such path: " + ex.getRequestURI().getPath());
        body.put("routes", Map.of(
                "/manifest", "GET — every verb and its JSON schema. No token needed. Start here.",
                "/action", "POST — run one verb"));
        body.put("actionEnvelope", Map.of("action", "<verb name>", "params", Map.of()));
        // F3: the manifest is deliberately public — it is the bootstrap, and a token you cannot yet
        // obtain is no use for discovering how to use the token you have.
        body.put("tokenHeader", TOKEN_HEADER + " (required by /action only)");
        send(ex, 404, Json.write(body));
    }

    private void handleManifest(HttpExchange ex) throws IOException {
        try {
            // Deliberately tokenless: the manifest is the bootstrap, and a token you cannot yet obtain is
            // no use for learning how to use the token you have. Guarded on Origin, exact path and GET.
            if (refusedByCommonGuards(ex, "/manifest", "GET")) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("v", ActionDispatcher.SCHEMA_VERSION);
            m.put("tokenHeader", TOKEN_HEADER);
            // DERIVED, never listed. This field was hardcoded to six verbs and stayed there while seven
            // more shipped, so the manifest contradicted itself: `verbs` said six, `schemas` said
            // thirteen, and an agent reading the obvious field never learned the rest existed.
            // VerbSchemas calls itself the single source of truth; this is what honouring that means.
            m.put("verbs", List.copyOf(
                    telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all().keySet()));
            m.put("schemas", telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all());
            m.put("maxActionsPerReply", maxActionsPerReply);
            m.put("rateLimitPerSec", ratePerSec);
            // the one text every MCP client is handed before it picks a tool — served here too, so a REST client (and
            // tools/heldout-client.py) gets exactly the same words, not a copy (main review 2026-09-18 F2)
            m.put("instructions", telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge.INSTRUCTIONS);
            send(ex, 200, Json.write(m));
        } catch (RuntimeException e) {   // never abort the exchange bodiless (parity with handleAction)
            send(ex, 500, err("server error: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    /**
     * M74 §4.2 step 5 and §3: what the hosted bridge may not do, decided from its credential's generation and the
     * session's admission — never from the shared token. Null when the call may run. The admission is asked of the
     * session on ITS thread, in its order ({@link HostedCredentials.Gate#admit}, review M6), never read from a snapshot
     * here; the cheap, local refusal (an unknown verb) comes first so it never waits on it.
     */
    static String hostedRefusal(HostedCredentials.Gate gate, String credential, String body) {
        return hostedRefusal(gate, credential, body, null);
    }

    /**
     * {@link #hostedRefusal(HostedCredentials.Gate, String, String)}, with the forwarded {@code tool_use_id} (P3-3). From
     * P3 a hosted DESTRUCTIVE verb is not refused here: it is PRESENTED to the session ({@link HostedCredentials.Gate#present}),
     * which admits it only by consuming the person's single-use grant for this exact call (OD-74.17) — decided on the
     * session's thread. Everything refused here is reported to the session as a fact too (scorecard R1).
     */
    static String hostedRefusal(HostedCredentials.Gate gate, String credential, String body, String toolUseId) {
        return hostedRefusal(gate, credential, body, toolUseId, null);
    }

    /**
     * Owner decision 2026-10-18: the body a hosted call admitted by {@link #hostedRefusal} is dispatched with — a
     * {@code graph} create admitted without an approval runs as a create ({@code McpTools.hostedConstrained}).
     */
    @SuppressWarnings("unchecked")
    static String hostedConstrained(String body, telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ChartState charts) {
        Object root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException e) {
            return body;
        }
        if (!(root instanceof Map<?, ?> m) || !(m.get("params") instanceof Map<?, ?> params)) return body;
        String action = String.valueOf(m.get("action"));
        Map<String, Object> constrained = telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.hostedConstrained(action,
                (Map<String, Object>) params, charts);
        if (constrained == params) return body;
        Map<String, Object> out = new java.util.LinkedHashMap<>((Map<String, Object>) m);
        out.put("params", constrained);
        return Json.write(out);
    }

    /** {@link #hostedRefusal(HostedCredentials.Gate, String, String, String)}, judging a graph call against {@code charts}. */
    static String hostedRefusal(HostedCredentials.Gate gate, String credential, String body, String toolUseId,
                                telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ChartState charts) {
        return hostedRefusal(gate, credential, body, toolUseId, charts, null);
    }

    /** {@link #hostedRefusal(HostedCredentials.Gate, String, String, String, McpTools.ChartState)} with the generation's open scope. */
    static String hostedRefusal(HostedCredentials.Gate gate, String credential, String body, String toolUseId,
                                telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ChartState charts,
                                java.util.function.LongFunction<telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.OpenScope> scopes) {
        long generation = gate == null ? -1 : gate.generationOf(credential);
        if (generation < 0) {
            return BAD_HOSTED_CREDENTIAL;
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException e) {
            root = null;                       // malformed: refused below as no listed verb
        }
        // review H5: an ALLOW-list — only a verb on the pre-allow list is served for the hosted bridge (until P3 asks the
        // person for the others), so a verb the schema gains is refused here until someone lists it. Defence in depth
        // beside --disallowedTools.
        String action = root instanceof Map<?, ?> m0 ? String.valueOf(m0.get("action")) : null;
        // the verb as the hosted vocabulary knows it — what a fact may carry (never the call's input)
        String verb = action != null && telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.hosted().containsKey(action)
                ? action : null;
        if (telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.askable(action)) {
            // P3 (OD-74.17): the server-side refusal is lifted for exactly one call the person approved in the analyser;
            // the session consumes the grant, on its thread, or refuses. Every other gate still applies after it.
            Object params = root instanceof Map<?, ?> m1 ? m1.get("params") : null;
            // review M74 usability #10b (a refinement of OD-74.16): a sub-action that changes nothing stored — playing
            // or ending a walk, listing a bin — is admitted as a pre-allowed verb is, judged from THIS call's input;
            // every other call of the verb still needs the person's grant
            // owner decision 2026-10-18: a graph call that creates a chart or changes only the view is admitted as the
            // pre-allowed verbs are; one that deletes, renames, restores or replaces a chart's definition needs the grant
            var kind = telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.hostedCallKind(action, params, charts,
                    scopes == null ? null : scopes.apply(generation));
            if (kind == telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.NON_MUTATING
                    || kind == telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.PRE_ALLOWED) {
                return gate.admit(generation, verb);
            }
            String inputDigest = telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson.digest(
                    params instanceof Map<?, ?> ? params : Map.of());
            String toolUse = toolUseId == null || toolUseId.isBlank() ? null
                    : telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.digest(toolUseId.trim());
            return gate.present(generation, action, inputDigest, toolUse);
        }
        if (!telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.preAllowed(action)) {
            gate.refused(generation, verb, "NOT_LISTED");
            return "`" + action + "` is not available to Claude Code in the analyser: only the pre-allowed verbs are, "
                    + "and the verbs that change what is open or write files are asked of the person";
        }
        // owner decision 2026-10-18: graph {delete} is no longer refused here — graph is judged call by call above, and
        // a delete is asked of the person (it can be restored)
        return gate.admit(generation, verb);
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
