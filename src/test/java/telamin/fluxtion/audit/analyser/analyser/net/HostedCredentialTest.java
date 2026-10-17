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
     * fails; or drop the delete check — the hosted delete is dispatched.
     */
    @Test
    @DisplayName("★ graph {delete} from the hosted bridge is refused; the shared token alone is an external client, never hosted")
    void hostedDeleteIsRefused() throws Exception {
        String token = credentials.mint(3);
        admitted.set(3);
        HttpResponse<String> external = post(DELETE, null);
        assertEquals(400, external.statusCode(), "the shared token alone: an ordinary client, gated as one");
        assertFalse(external.body().contains("Claude Code"), external.body());
        HttpResponse<String> hosted = post(DELETE, token);
        assertEquals(403, hosted.statusCode(), "never reached the dispatcher");
        assertTrue(hosted.body().contains("graph {delete} is not available to Claude Code"), hosted.body());
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

    /** ★ Witness (review S1): drop the destructive-verb refusal in {@code hostedRefusal} — the hosted open is dispatched. */
    @Test
    @DisplayName("★ S1: every destructive verb is refused for the hosted bridge server-side; an external client is unaffected")
    void destructiveVerbsAreRefusedForTheHostedBridge() throws Exception {
        String token = credentials.mint(4);
        admitted.set(4);
        for (String verb : telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.destructiveVerbs()) {
            String body = "{\"action\":\"" + verb + "\",\"params\":{}}";
            HttpResponse<String> hostedCall = post(body, token);
            assertEquals(403, hostedCall.statusCode(), verb);
            assertTrue(hostedCall.body().contains("not available to Claude Code"), hostedCall.body());
            assertEquals(400, post(body, null).statusCode(), verb + ": an external client reaches the dispatcher as before");
        }
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
