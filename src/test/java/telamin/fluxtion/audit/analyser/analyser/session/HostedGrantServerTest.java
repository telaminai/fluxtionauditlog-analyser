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

    /**
     * ★ Review P3 S1 (2026-10-17): a grant bound to a tool_use_id is not consumed by a call that carries none. Witness:
     * the old {@code && e.toolUseIdDigest() != null} in {@code LocalAgentPermission.consume} — the header-less call is
     * admitted (200).
     */
    @Test
    @DisplayName("★ a grant with a tool_use_id refuses a call without the header (403); the call with it is admitted")
    void aGrantWithAnIdNeedsTheId() throws Exception {
        allowOnce(205);
        HttpResponse<String> bare = hostedCall(BODY, null);
        assertEquals(403, bare.statusCode(), bare.body());
        assertTrue(rendered.isEmpty(), "nothing reached the dispatcher");
        assertEquals(200, hostedCall(BODY, TOOL_USE).statusCode(), "the grant is still there for the approved call");
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

    /**
     * ★ Review M74 usability #10b (a refinement of OD-74.16): a sub-action that changes nothing stored is admitted without
     * a grant, judged from the call's own input; every other sub-action of the same verb still needs one. Witnesses:
     * drop the NON_MUTATING branch in {@code hostedRefusal} — the play is refused (403) for want of a grant; classify
     * {@code walk {delete}} as non-mutating — the delete reaches the dispatcher with no approval.
     */
    @Test
    @DisplayName("★ walk {play} and the bins' listings need no grant; walk {delete} and every write still do")
    void nonMutatingSubActionsNeedNoGrant() throws Exception {
        HttpResponse<String> play = hostedCall(Json.write(Map.of("action", "walk", "params",
                Map.of("name", "DEMO first breach", "play", true))), TOOL_USE);
        assertEquals(200, play.statusCode(), play.body());
        assertEquals(List.of("walk"), rendered, "the play reached the dispatcher");
        HttpResponse<String> list = hostedCall(Json.write(Map.of("action", "report", "params", Map.of("restore", true))),
                TOOL_USE);
        assertEquals(200, list.statusCode(), list.body());
        HttpResponse<String> delete = hostedCall(Json.write(Map.of("action", "walk", "params",
                Map.of("name", "DEMO first breach", "delete", true))), TOOL_USE);
        assertEquals(403, delete.statusCode(), delete.body());
        assertTrue(delete.body().contains("needs your approval"), delete.body());
        HttpResponse<String> playAndDelete = hostedCall(Json.write(Map.of("action", "walk", "params",
                Map.of("name", "x", "play", true, "delete", true))), TOOL_USE);
        assertEquals(403, playAndDelete.statusCode(), "a play carrying another operation's field is not a play");
        assertEquals(List.of("walk", "report"), rendered, "neither the delete nor the mixed call ran");
    }
    static final String PLAY = Json.write(Map.of("action", "walk", "params", Map.of("name", "DEMO first breach",
            "play", true)));

    /**
     * ★ Final review #3 (rule 8, 2026-10-17): a pre-allowed sub-action (NON_MUTATING) still goes through the session's
     * admission — a live credential whose generation the session has not admitted is refused. Witness: {@code return
     * null} instead of {@code gate.admit(generation, verb)} in the NON_MUTATING branch of {@code hostedRefusal} — the
     * play is served (200) for a session that was never posture-checked.
     */
    @Test
    @DisplayName("★ walk {play} with a credential whose generation is not admitted: 403, nothing reaches the dispatcher")
    void aNonMutatingCallStillNeedsTheSessionsAdmission() throws Exception {
        credential = credentials.mint(start.generation() + 7);      // live, but for a generation never admitted
        HttpResponse<String> play = hostedCall(PLAY, TOOL_USE);
        assertEquals(403, play.statusCode(), play.body());
        assertTrue(play.body().contains("not connected to this analyser yet"), play.body());
        assertTrue(rendered.isEmpty(), "nothing reached the dispatcher");
    }

    /**
     * ★ Final review #5 (defence in depth, OD-74.16): under {@code auto} the asked verbs are hidden and the node's policy
     * denies every request for them, so the session's admission refuses them too — the four pre-allowed shapes
     * included; a pre-allowed verb is still served. Witness: drop the askable-and-not-asks check in
     * {@code LocalAgentSession.onCallAdmission} — the play is served (200) under auto.
     */
    @Test
    @DisplayName("★ under auto, walk {play} and report {restore: true} are refused by the session's admission; filter is served")
    void anAskedVerbIsRefusedInAModeThatDoesNotAsk() throws Exception {
        driver = sessionThread.submit(() -> LocalAgentSessionTest.driver(adapter)).get();
        start = sessionThread.submit(() -> {
            var s = LocalAgentFullPostureTest.startFull(driver, adapter, LocalAgentPosture.FullMode.AUTO);
            var t = LocalAgentSessionTest.send(driver, adapter, 10,
                    LocalAgentFullPostureTest.full(LocalAgentPosture.FullMode.AUTO));
            driver.post(LocalAgentFullPostureTest.fullInit(s, t.ticket(), "auto", List.of(), "connected"));
            return s;
        }).get();
        assertEquals(start.generation(), sessionThread.submit(() -> driver.snapshot().localAgent().admittedGeneration())
                .get(), "the auto session is admitted");
        credential = credentials.mint(start.generation());
        HttpResponse<String> play = hostedCall(PLAY, TOOL_USE);
        assertEquals(403, play.statusCode(), play.body());
        assertTrue(play.body().contains("not available to Claude Code in this mode"), play.body());
        HttpResponse<String> list = hostedCall(Json.write(Map.of("action", "report", "params", Map.of("restore", true))),
                TOOL_USE);
        assertEquals(403, list.statusCode(), list.body());
        assertTrue(rendered.isEmpty(), "neither reached the dispatcher");
        HttpResponse<String> filter = hostedCall(Json.write(Map.of("action", "filter", "params", Map.of("text", "x"))),
                TOOL_USE);
        assertEquals(200, filter.statusCode(), "control: a pre-allowed verb is served under auto: " + filter.body());
    }
}
