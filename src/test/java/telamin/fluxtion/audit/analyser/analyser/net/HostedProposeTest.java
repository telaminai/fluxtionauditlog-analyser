package telamin.fluxtion.audit.analyser.analyser.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.ProposeShape;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §8.2 (a), §10 P2): {@code propose} is the hosted bridge's alone — in {@code VerbSchemas.hosted()} and
 * nowhere else, answered on the REAL action server only for the hosted credential of the admitted generation.
 */
class HostedProposeTest {

    private ActionServer server;
    private final HostedCredentials credentials = new HostedCredentials();
    private final AtomicLong admitted = new AtomicLong();
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Long> proposedFor = new ArrayList<>();

    static final String PROPOSE = "{\"action\":\"propose\",\"params\":{\"items\":[{\"kind\":\"sourceRoot\",\"path\":\"src\","
            + "\"reason\":\"the code\"}]}}";

    @BeforeEach
    void start() throws Exception {
        ActionDispatcher d = new ActionDispatcher(false, null, () -> null, null);
        d.setProposer((generation, call) -> {
            proposedFor.add(generation);
            return ActionResult.ok("propose", "proposal", Map.of("proposalId", "p-1"));
        });
        server = new ActionServer(d, "shared", 20, 1000.0);
        server.setHostedGate(credentials.gate(g -> g == admitted.get() ? null : "not admitted"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> post(String body, String hosted) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + "/action"))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (hosted != null) b.header(ActionServer.HOSTED_HEADER, hosted);
        else b.header("X-Analyser-Token", "shared");
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** ★ Witness: in hostedRefusal, check the credential's presence and not its admission — generation 8 is proposed for. */
    @Test
    @DisplayName("★ propose is answered only for HOSTED(g) with g admitted; external, in-process, revoked and unadmitted get the unknown verb or a refusal")
    void onlyTheAdmittedHostedCallerProposes() throws Exception {
        String nine = credentials.mint(9);
        String eight = credentials.mint(8);
        admitted.set(9);
        var ok = post(PROPOSE, nine);
        assertEquals(200, ok.statusCode(), ok.body());
        assertEquals(List.of(9L), proposedFor, "the admitted generation reached the proposer");
        assertEquals(403, post(PROPOSE, eight).statusCode(), "an unadmitted generation is refused before the dispatcher");
        var external = post(PROPOSE, null);
        assertEquals(400, external.statusCode());
        assertTrue(external.body().contains("unknown verb 'propose'"), external.body());
        credentials.revoke(9);
        assertEquals(403, post(PROPOSE, nine).statusCode(), "a revoked credential");
        assertEquals(List.of(9L), proposedFor);
        // in-process: the same words as any unknown verb
        ActionDispatcher inProcess = new ActionDispatcher(false, null, () -> null, null);
        inProcess.setProposer((g, c) -> ActionResult.ok("propose", "proposal", Map.of()));
        assertEquals("unknown verb 'propose'", inProcess.dispatch(PROPOSE).toMap().get("error"));
        assertEquals("unknown verb 'nosuch'", inProcess.dispatch("{\"action\":\"nosuch\"}").toMap().get("error"));
    }

    /** ★ Witness: add propose to all() — the manifest, the MCP list, the prompt and the in-app names each fail. */
    @Test
    @DisplayName("★ all() is the 18 verbs; hosted() is all() then propose; propose leaks into no public surface")
    void proposeIsInNoPublicSurface() throws Exception {
        List<String> all = new ArrayList<>(VerbSchemas.all().keySet());
        assertEquals(18, all.size());
        List<String> hosted = new ArrayList<>(VerbSchemas.hosted().keySet());
        List<String> expected = new ArrayList<>(all);
        expected.add("propose");
        assertEquals(expected, hosted, "hosted() is all() then propose, in order");
        var manifest = http.send(HttpRequest.newBuilder(URI.create(server.url() + "/manifest")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertFalse(manifest.contains("\"propose\""), "not in /manifest");
        assertTrue(McpTools.list().stream().noneMatch(t -> "analyser_propose".equals(t.get("name"))), "not in McpTools.list()");
        assertTrue(McpTools.hostedList().stream().anyMatch(t -> "analyser_propose".equals(t.get("name"))),
                "the hosted bridge lists it");
        assertNull(McpTools.verbFor("analyser_propose"), "the public bridge does not route it");
        assertEquals("propose", McpTools.hostedVerbFor("analyser_propose"));
        assertFalse(telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.inProcessActionManifest(5).contains("propose"),
                "not in the prompt's verb list");
        assertFalse(telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder.restActionManifest("http://127.0.0.1:1", "t", 5)
                .contains("\"propose\""));
    }

    @Test
    @DisplayName("propose's whole-call shape: not an object, no items, not an array, empty, over 10, a non-object item, an unknown key")
    void wholeCallShape() {
        assertNotNull(ProposeShape.of(null).refusal());
        assertNotNull(ProposeShape.of(Map.of()).refusal());
        assertNotNull(ProposeShape.of(Map.of("items", "x")).refusal());
        assertNotNull(ProposeShape.of(Map.of("items", List.of())).refusal());
        List<Object> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add(Map.of("kind", "sourceRoot", "path", "p" + i, "reason", "r"));
        assertTrue(ProposeShape.of(Map.of("items", eleven)).refusal().contains("at most 10"));
        assertNotNull(ProposeShape.of(Map.of("items", List.of("x"))).refusal());
        assertTrue(ProposeShape.of(Map.of("items", List.of(Map.of("kind", "x")), "apply", true)).refusal()
                .contains("'apply'"));
        var ok = ProposeShape.of(Map.of("items", List.of(Map.of("kind", "processor", "className", "1bad", "reason", "r"))));
        assertNull(ok.refusal());
        assertTrue(ok.call().items().get(0).classNameMalformed(), "observed, for the node to decide");
    }
}
