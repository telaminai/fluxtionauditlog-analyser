package telamin.fluxtion.audit.analyser.analyser.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 r6 §4.2 — the hosted caller's own credential, on the REAL action server. What is decided "for the hosted
 * bridge" — refused before it is bound and checked, refused {@code graph {delete}} — keys on the credential and the
 * session's admitted generation, never on the shared token.
 */
class HostedCredentialTest {

    private ActionServer server;
    private final HostedCredentials credentials = new HostedCredentials();
    private final AtomicLong admitted = new AtomicLong();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        // no log and no render executor: every dispatched call answers 400 in the dispatcher's own words, so a 403 is
        // provably the hosted guard's
        server = new ActionServer(new ActionDispatcher(false, null, () -> null, null), "shared", 20, 1000.0);
        server.setHostedGate(credentials.gate(g -> g == admitted.get() ? null
                : "the Claude Code session is not bound and checked yet"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> post(String body, String hosted) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + "/action"))
                .header("X-Analyser-Token", "shared").POST(HttpRequest.BodyPublishers.ofString(body));
        if (hosted != null) b.header(ActionServer.HOSTED_HEADER, hosted);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** A request with the hosted header and NO shared token, as the hosted bridge now sends (review finding 6). */
    private HttpResponse<String> postHostedOnly(String body, String hosted) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + "/action"))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (hosted != null) b.header(ActionServer.HOSTED_HEADER, hosted);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * ★ Review finding 6: the hosted bridge authenticates with its credential alone, so its endpoint file needs no shared
     * token. Witness: keep requiring the shared token first (the P1a order) — the admitted hosted call is 401 and the
     * first assertion fails; or accept any hosted header — the made-up one is dispatched.
     */
    @Test
    @DisplayName("★ the hosted credential alone authenticates the hosted bridge; an unknown one, or neither header, is refused")
    void theHostedCredentialAloneAuthenticates() throws Exception {
        String token = credentials.mint(9);
        admitted.set(9);
        assertEquals(400, postHostedOnly(READ, token).statusCode(), "reached the dispatcher (400: no log open)");
        assertEquals(403, postHostedOnly(DELETE, token).statusCode(), "and every hosted refusal still applies");
        assertEquals(403, postHostedOnly(READ, "made-up").statusCode(), "an unknown credential is never external");
        assertEquals(401, postHostedOnly(READ, null).statusCode(), "no credential and no token: refused");
    }

    static final String DELETE = "{\"action\":\"graph\",\"params\":{\"name\":\"Chart 1\",\"delete\":true}}";
    static final String READ = "{\"action\":\"aggregate\",\"params\":{\"metric\":\"count\"}}";

    /**
     * ★ Witness: in {@code ActionServer.handleAction}, treat a request with only the shared token as hosted (apply
     * {@code hostedRefusal} when the header is absent) — the external client's call is refused and the first assertion
     * fails; or admit graph {delete} without the person's approval (classify it PRE_ALLOWED) — the hosted delete is
     * dispatched. Owner decision 2026-10-18: the delete is ASKED now (it can be restored), no longer refused outright.
     */
    @Test
    @DisplayName("★ graph {delete} from the hosted bridge needs the person's approval; the shared token alone is an external client")
    void hostedDeleteIsRefused() throws Exception {
        String token = credentials.mint(3);
        admitted.set(3);
        HttpResponse<String> external = post(DELETE, null);
        assertEquals(400, external.statusCode(), "the shared token alone: an ordinary client, gated as one");
        assertFalse(external.body().contains("Claude Code"), external.body());
        HttpResponse<String> hosted = post(DELETE, token);
        assertEquals(403, hosted.statusCode(), "never reached the dispatcher");
        assertTrue(hosted.body().contains("`graph` needs your approval in the analyser"), hosted.body());
        assertEquals(400, post(READ, token).statusCode(), "a pre-allowed verb reaches the dispatcher");
        assertEquals(403, post("{\"action\":\"graph\",\"params\":{\"name\":\"x\",\"delete\":\"true\"}}", token).statusCode(),
                "a string true is still a delete");
    }

    /** ★ Witness: return null from the gate's refusal for any generation — the unbound call is dispatched. */
    @Test
    @DisplayName("★ the hosted bridge is refused before its generation is admitted, after revocation, and for another generation")
    void hostedCallsWaitForAdmission() throws Exception {
        String token = credentials.mint(5);
        HttpResponse<String> early = post(READ, token);
        assertEquals(403, early.statusCode(), "bound and checked comes first");
        assertTrue(early.body().contains("not bound and checked yet"));
        admitted.set(5);
        assertEquals(400, post(READ, token).statusCode());
        String next = credentials.mint(6);
        assertEquals(403, post(READ, next).statusCode(), "generation 6 is not the admitted one");
        credentials.revoke(5);
        HttpResponse<String> revoked = post(READ, token);
        assertEquals(403, revoked.statusCode());
        assertTrue(revoked.body().contains("revoked"), revoked.body());
        assertEquals(403, post(READ, "made-up").statusCode(), "an unknown credential");
    }

    /**
     * ★ Witness (review S1; P3): admit a destructive call in {@code hostedRefusal} instead of presenting it — the hosted
     * open is dispatched. P3 lifts the refusal only by the person's grant (OD-74.17); with none (this gate's default
     * presentation), every destructive call is still refused server-side, in P3's words.
     */
    @Test
    @DisplayName("★ S1/P3: a destructive verb without the person's grant is refused for the hosted bridge; external unaffected")
    void destructiveVerbsAreRefusedForTheHostedBridge() throws Exception {
        String token = credentials.mint(4);
        admitted.set(4);
        for (String verb : telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.destructiveVerbs()) {
            String body = "{\"action\":\"" + verb + "\",\"params\":{}}";
            HttpResponse<String> hostedCall = post(body, token);
            assertEquals(403, hostedCall.statusCode(), verb);
            assertTrue(hostedCall.body().contains("needs your approval in the analyser for this exact call"),
                    hostedCall.body());
            assertEquals(400, post(body, null).statusCode(), verb + ": an external client reaches the dispatcher as before");
        }
    }

    /**
     * ★ Review H5. Witness: refuse only {@code McpTools.destructiveVerbs()} in {@code hostedRefusal} (the old deny-list)
     * — the unlisted verb reaches the dispatcher (400, not 403) and the first assertion fails.
     */
    @Test
    @DisplayName("★ H5: a verb not on the pre-allow list is refused for the hosted credential; an external client is unaffected")
    void anUnlistedVerbIsRefusedForTheHostedBridge() throws Exception {
        String token = credentials.mint(8);
        admitted.set(8);
        String body = "{\"action\":\"brand_new_verb\",\"params\":{}}";
        HttpResponse<String> hostedCall = post(body, token);
        assertEquals(403, hostedCall.statusCode(), "an unlisted verb never reaches the dispatcher for the hosted bridge");
        assertTrue(hostedCall.body().contains("not available to Claude Code"), hostedCall.body());
        HttpResponse<String> external = post(body, null);
        assertEquals(400, external.statusCode(), "an external client reaches the dispatcher, which says it is unknown");
        assertTrue(external.body().contains("unknown verb"), external.body());
        assertEquals(403, post("{not json", token).statusCode(), "a body naming no listed verb is refused too");
    }

    /**
     * ★ Review L2. Witness: use one bucket for both callers in {@code handleAction} — the hosted call after the external
     * client spent the bucket is 429 and the named assertion fails.
     */
    @Test
    @DisplayName("★ L2: the hosted credential has its own rate bucket — an external client cannot starve it, nor the reverse")
    void theHostedBridgeHasItsOwnRateBucket() throws Exception {
        server.stop();
        server = new ActionServer(new ActionDispatcher(false, null, () -> null, null), "shared", 20, 1.0);
        server.setHostedGate(credentials.gate(g -> g == admitted.get() ? null : "not admitted"));
        server.start();
        String token = credentials.mint(9);
        admitted.set(9);
        int externalLimited = 0;
        for (int i = 0; i < 5; i++) if (post(READ, null).statusCode() == 429) externalLimited++;
        assertTrue(externalLimited > 0, "the external bucket (1/s) is spent");
        assertEquals(400, post(READ, token).statusCode(), "the hosted call still runs: its own bucket");
        int hostedLimited = 0;
        for (int i = 0; i < 5; i++) if (post(READ, token).statusCode() == 429) hostedLimited++;
        assertTrue(hostedLimited > 0, "the hosted bucket is limited as well");
    }

    /**
     * ★ Holistic review L-a (2026-10-17): a made-up hosted credential is refused BEFORE the hosted bucket, so it cannot
     * spend the analyser's own session's rate limit. Witness: move the credential check after {@code tryAcquire} (the
     * old order) — the made-up calls drain the 1/s bucket and the real hosted call is 429.
     */
    @Test
    @DisplayName("★ L-a: an unauthenticated hosted caller cannot spend the hosted bucket")
    void aBadCredentialSpendsNoBucket() throws Exception {
        server.stop();
        server = new ActionServer(new ActionDispatcher(false, null, () -> null, null), "shared", 20, 1.0);
        server.setHostedGate(credentials.gate(g -> g == admitted.get() ? null : "not admitted"));
        server.start();
        String token = credentials.mint(9);
        admitted.set(9);
        for (int i = 0; i < 5; i++) {
            var r = postHostedOnly(READ, "made-up-" + i);
            assertEquals(403, r.statusCode(), r.body());
            assertTrue(r.body().contains("not one this analyser issued"), r.body());
        }
        assertEquals(400, postHostedOnly(READ, token).statusCode(), "the real hosted call still has its bucket");
    }

    /**
     * ★ Review H4. Witness: let {@code refusedAsExternal} admit the shared token (return false), or publish in
     * {@code republish} regardless of the mode — the external call is dispatched (400) or the well-known file appears,
     * and the named assertion fails.
     */
    @Test
    @DisplayName("★ H4: a hosted-only server refuses the shared token (403), writes no well-known file, and serves the hosted bridge")
    void aHostedOnlyServerRefusesExternalClients(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        server.stop();
        RestEndpointFile wellKnown = new RestEndpointFile(dir.resolve("rest-endpoint"));
        server = ActionServer.hostedOnly(new ActionDispatcher(false, null, () -> null, null), "shared", 20, 1000.0,
                wellKnown);
        server.setHostedGate(credentials.gate(g -> g == admitted.get() ? null : "not admitted"));
        server.start();
        assertFalse(java.nio.file.Files.exists(wellKnown.path()), "hosted-only publishes no well-known endpoint file");
        assertFalse(server.republish(), "and republishing publishes nothing");
        assertFalse(java.nio.file.Files.exists(wellKnown.path()));
        HttpResponse<String> external = post(READ, null);
        assertEquals(403, external.statusCode(), "the shared token alone is refused: external REST is off");
        assertTrue(external.body().contains("External MCP/REST access is off"), external.body());
        HttpResponse<String> manifest = http.send(HttpRequest.newBuilder(URI.create(server.url() + "/manifest")).GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, manifest.statusCode(), "nor does it answer an external manifest request");
        String token = credentials.mint(11);
        admitted.set(11);
        assertEquals(400, post(READ, token).statusCode(), "the hosted bridge is served (the dispatcher answers)");

        // the REST switch turned on while the session runs: the same listener opens to external clients and publishes
        server.setExternalAllowed(true);
        assertTrue(java.nio.file.Files.exists(wellKnown.path()), "published once external clients are allowed");
        assertEquals(400, post(READ, null).statusCode(), "an external client is served now");
        server.setExternalAllowed(false);
        assertFalse(java.nio.file.Files.exists(wellKnown.path()), "withdrawn again when the switch goes off");
        assertEquals(403, post(READ, null).statusCode());
        assertEquals(400, post(READ, token).statusCode(), "while the hosted bridge keeps its listener");
    }

    /**
     * ★ Review M74 usability #11: with "Close external access while this session runs" in force, an external client is
     * told THAT — not that the REST switch is off, which Settings contradicts and which sends an incident responder to
     * the wrong place. Witness: send {@code EXTERNAL_OFF} whatever the reason — the first assertion fails.
     */
    @Test
    @DisplayName("★ closed for an in-app session: the external client is told so, accurately; switch off: told that")
    void theRefusalSaysWhyExternalAccessIsClosed() throws Exception {
        server.setExternalAllowed(false);
        server.setClosedForSession(true);
        HttpResponse<String> closed = post(READ, null);
        assertEquals(403, closed.statusCode());
        assertTrue(closed.body().contains("external access is closed while an in-app Claude Code session runs"),
                closed.body());
        assertFalse(closed.body().contains("is off"), "never the switch's words: " + closed.body());
        server.setClosedForSession(false);
        HttpResponse<String> off = post(READ, null);
        assertTrue(off.body().contains("External MCP/REST access is off"), off.body());
        server.setExternalAllowed(true);
    }

    @Test
    @DisplayName("a credential is minted per generation, maps to it only, and minting again replaces it")
    void mintingAndRevocation() {
        String a = credentials.mint(1);
        String b = credentials.mint(2);
        assertNotEquals(a, b);
        assertEquals(64, a.length());
        assertEquals(1, credentials.generationOf(a));
        assertEquals(2, credentials.generationOf(b));
        String a2 = credentials.mint(1);
        assertEquals(-1, credentials.generationOf(a), "a new spawn of the same generation replaces the old credential");
        assertEquals(1, credentials.generationOf(a2));
        assertEquals(-1, credentials.generationOf("shared"), "the shared token is never a hosted credential");
        assertEquals(-1, credentials.generationOf(null));
    }
}
