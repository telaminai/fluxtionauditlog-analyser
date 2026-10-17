package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.HostedAdmission;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.llm.RenderExecutor;
import telamin.fluxtion.audit.analyser.analyser.net.ActionServer;
import telamin.fluxtion.audit.analyser.analyser.net.HostedCredentials;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P3 (OD-74.17, §10 P3): the single-use grant on the REAL action server, the REAL session processor on its own
 * thread, and the REAL correlator ({@link HostedAdmission}) between them — what the hosted bridge's HTTP call meets. A
 * hosted destructive call with no grant is refused; after the person's Allow once, exactly one identical call is
 * admitted and reaches the dispatcher; the second identical call is refused. Two identical calls in one burst admit one.
 */
class HostedGrantServerTest {

    static final Map<String, Object> PARAMS = Map.of("title", "DEMO findings", "path", "demo.pdf", "pages", 2);
    static final String BODY = Json.write(Map.of("action", "report", "params", PARAMS));
    static final String TOOL_USE = "toolu_01DEMO";

    private final ExecutorService sessionThread = Executors.newSingleThreadExecutor();
    private final FakeSessionAdapter adapter = new FakeSessionAdapter();
    private final HostedCredentials credentials = new HostedCredentials();
    private final List<String> rendered = new CopyOnWriteArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private SessionDriver driver;
    private ActionServer server;
    private SessionEffects.StartLocalAgentEffect start;
    private String credential;

    @BeforeEach
    void up() throws Exception {
        driver = sessionThread.submit(() -> LocalAgentSessionTest.driver(adapter)).get();
        start = sessionThread.submit(() -> LocalAgentPermissionTest.ready(driver, adapter)).get();
        HostedAdmission admission = new HostedAdmission(fact -> sessionThread.execute(() -> driver.post(fact)), 5_000);
        adapter.onAnswer = admission::answer;
        // a render executor that records the verbs that reach it: what "admitted" means, past every hosted refusal
        RenderExecutor render = (action, params) -> {
            rendered.add(action);
            return ActionResult.ok(action, "result", Map.of("written", "demo.pdf"));
        };
        server = new ActionServer(new ActionDispatcher(false, null, () -> null, null, render), "shared", 20, 1000.0);
        server.setHostedGate(credentials.gate(admission));
        server.start();
        credential = credentials.mint(start.generation());
    }

    @AfterEach
    void down() {
        server.stop();
        sessionThread.shutdownNow();
    }

    private HttpResponse<String> hostedCall(String body, String toolUse) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + "/action"))
                .header(ActionServer.HOSTED_HEADER, credential).POST(HttpRequest.BodyPublishers.ofString(body));
        if (toolUse != null) b.header(ActionServer.TOOL_USE_HEADER, toolUse);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The person's Allow once for the report call the CLI asked about — with the input as the CLI wrote it (doubles). */
    private void allowOnce(long ref) throws Exception {
        // the CLI's input parses its numbers as doubles; the bridge narrows them — the canonical digest agrees on both
        Object asTheCliSent = Json.parse(Json.write(Map.of("pages", 2.0, "path", "demo.pdf", "title", "DEMO findings")));
        String digest = CanonicalJson.digest(asTheCliSent);
        sessionThread.submit(() -> {
            driver.post(new SessionEvents.LocalAgentPermissionRequested(start.generation(), 1, ref,
                    SessionEvents.ToolClass.HOSTED_DESTRUCTIVE, "report", digest, LocalAgentPosture.digest(TOOL_USE), true));
            driver.post(new SessionEvents.LocalAgentPermissionChosen(ref, true));
        }).get();
    }

    /**
     * ★ Witnesses: check without consuming (leave the grant in {@code LocalAgentPermission.consume}) — the second
     * identical call is admitted (200) and the last assertion fails; admit a destructive verb in {@code hostedRefusal}
     * without presenting it — the first, ungranted call is admitted.
     */
    @Test
    @DisplayName("★ no grant: refused; after Allow once exactly one identical call is admitted; the second is refused")
    void exactlyOneCallPerApproval() throws Exception {
        HttpResponse<String> before = hostedCall(BODY, TOOL_USE);
        assertEquals(403, before.statusCode(), before.body());
        assertTrue(before.body().contains("needs your approval in the analyser for this exact call; none was given"),
                before.body());
        assertTrue(rendered.isEmpty(), "nothing reached the dispatcher");

        allowOnce(201);
        HttpResponse<String> once = hostedCall(BODY, TOOL_USE);
        assertEquals(200, once.statusCode(), once.body());
        assertEquals(List.of("report"), rendered, "the one approved call reached the dispatcher");

        HttpResponse<String> again = hostedCall(BODY, TOOL_USE);
        assertEquals(403, again.statusCode(), again.body());
        assertTrue(again.body().contains("has been used"), again.body());
        assertEquals(List.of("report"), rendered, "the second identical call never ran");
    }

    /** ★ Witness: consume on the HTTP thread from a published snapshot (no session round trip) — both calls pass. */
    @Test
    @DisplayName("★ two identical calls in one burst admit exactly one — the grant is consumed on the session's thread")
    void aBurstAdmitsOne() throws Exception {
        allowOnce(202);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> a = callers.submit(() -> hostedCall(BODY, TOOL_USE));
            Future<HttpResponse<String>> b = callers.submit(() -> hostedCall(BODY, TOOL_USE));
            int ok = (a.get().statusCode() == 200 ? 1 : 0) + (b.get().statusCode() == 200 ? 1 : 0);
            assertEquals(1, ok, a.get().body() + " / " + b.get().body());
            assertEquals(1, rendered.size());
        } finally {
            callers.shutdownNow();
        }
    }

    /** ★ Witness: ignore the forwarded tool_use_id (drop the header in hostedRefusal) — the other call's id is admitted. */
    @Test
    @DisplayName("★ the grant is bound to the call's tool_use_id the bridge forwards (P3-3); another id is refused")
    void theToolUseIdBindsTheGrant() throws Exception {
        allowOnce(203);
        assertEquals(403, hostedCall(BODY, "toolu_01OTHER").statusCode(), "another tool use's call");
        assertEquals(200, hostedCall(BODY, TOOL_USE).statusCode(), "this tool use's call");
    }

    /** ★ Witness: lift graph {delete} for a granted caller — the delete reaches the dispatcher. */
    @Test
    @DisplayName("★ graph {delete} stays refused whatever was approved; an admitted call still meets the dispatcher's own gate")
    void otherGatesStillApply() throws Exception {
        allowOnce(204);
        String delete = Json.write(Map.of("action", "graph", "params", Map.of("name", "Chart 1", "delete", true)));
        HttpResponse<String> del = hostedCall(delete, TOOL_USE);
        assertEquals(403, del.statusCode(), del.body());
        assertTrue(del.body().contains("graph {delete} is not available"), del.body());
        assertFalse(rendered.contains("graph"));
        // the approved report goes on to the dispatcher, whose own refusal (here: a malformed version) is its own words
        String badVersion = Json.write(Map.of("v", 9, "action", "report", "params", PARAMS));
        HttpResponse<String> gated = hostedCall(badVersion, TOOL_USE);
        assertEquals(400, gated.statusCode(), gated.body());
        assertTrue(gated.body().contains("unsupported schema version"), "the grant lifts only the hosted refusal: "
                + gated.body());
        assertTrue(String.join("\n", sessionThread.submit(() -> driver.auditSink().records()).get())
                .contains("GRAPH_DELETE"), "the server's own refusal is a fact (R1)");
    }
}
