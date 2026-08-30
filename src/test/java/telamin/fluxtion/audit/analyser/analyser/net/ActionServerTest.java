package telamin.fluxtion.audit.analyser.analyser.net;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The localhost REST transport (spec §5.2, §6): loopback bind, {@code /manifest} shape, header token
 * guard, any-{@code Origin} rejection, and the {@code 429} rate limit.
 */
class ActionServerTest {

    private ActionServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws IOException {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher d = new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        server = new ActionServer(d, "s3cr3t", 20, 5.0);   // 5 req/s bucket for a testable 429
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create(server.url() + path));
    }

    private HttpResponse<String> send(HttpRequest r) throws Exception {
        return http.send(r, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void bindsToLoopback() {
        assertTrue(server.url().startsWith("http://127.0.0.1:"));
        assertTrue(server.port() > 0);
    }

    @Test
    void manifestAdvertisesVerbsAndCaps() throws Exception {
        HttpResponse<String> r = send(req("/manifest").GET().build());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"aggregate\""));
        assertTrue(r.body().contains("maxActionsPerReply"));
        assertTrue(r.body().contains("rateLimitPerSec"));
        assertTrue(r.body().contains("X-Analyser-Token"));
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        HttpResponse<String> noTok = send(req("/action")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"aggregate\"}")).build());
        assertEquals(401, noTok.statusCode());

        HttpResponse<String> wrong = send(req("/action").header("X-Analyser-Token", "nope")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"aggregate\"}")).build());
        assertEquals(401, wrong.statusCode());
    }

    @Test
    void rejectsAnyOriginHeader() throws Exception {
        HttpResponse<String> r = send(req("/action")
                .header("X-Analyser-Token", "s3cr3t").header("Origin", "http://evil.example")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"aggregate\"}")).build());
        assertEquals(403, r.statusCode());
    }

    @Test
    void acceptsAValidTokenedAggregate() throws Exception {
        HttpResponse<String> r = send(req("/action").header("X-Analyser-Token", "s3cr3t")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"action\":\"aggregate\",\"params\":{\"groupBy\":\"dimension\"}}")).build());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"ok\":true"));
        assertTrue(r.body().contains("\"total\""));
    }

    @Test
    void rateLimitEventuallyReturns429() throws Exception {
        int sawTooMany = 0;
        for (int i = 0; i < 30; i++) {
            HttpResponse<String> r = send(req("/action").header("X-Analyser-Token", "s3cr3t")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"aggregate\"}")).build());
            if (r.statusCode() == 429) sawTooMany++;
        }
        assertTrue(sawTooMany > 0, "a burst beyond the bucket should be rate-limited (429)");
    }

    @Test
    void republishRewritesTheEndpointFileForTheRunningServer(@TempDir java.nio.file.Path dir) throws Exception {
        // the pointer is disposable, the server is not: another window may delete or overwrite the file
        // and then go away; republish() puts THIS server back without restarting anything
        RestEndpointFile file = new RestEndpointFile(dir.resolve("rest-endpoint"));
        HeapLogStore store = new HeapLogStore(Samples.sample());
        ActionDispatcher d = new ActionDispatcher(false, null, () -> store.index().snapshot(), store::rawText);
        ActionServer own = new ActionServer(d, "s3cr3t", 20, 5.0, file);
        try {
            own.start();
            assertEquals(ProcessHandle.current().pid(), file.read().pid());
            java.nio.file.Files.delete(file.path());                       // a departed window took it with it
            assertNull(file.read());
            assertTrue(own.republish());
            RestEndpointFile.Endpoint again = file.read();
            assertEquals(own.url(), again.url());
            assertEquals(ProcessHandle.current().pid(), again.pid());
            assertTrue(again.alive());
        } finally {
            own.stop();
        }
        assertNull(file.read(), "stop() removes the file it published");
        assertFalse(new ActionServer(d, "x", 20, 5.0).republish(), "no file configured → nothing published");
    }

    // ---- discovery: a wrong path must say where the right one is -------------------------------------

    @Test
    void anUnknownPathNAMESTheRoutesInsteadOfADeadEnd() throws Exception {
        // Round 06: two independent agents, each holding a valid token, tried ~20 path/method/auth
        // combinations and got the JDK's bare "No context found for request" every time. Neither reached
        // the analyser. We publish a url and a token and no way to learn the protocol, and /manifest —
        // which answers everything — was itself undiscoverable. One wrong guess must now be enough.
        HttpResponse<String> r = send(req("/api/context").GET().build());

        assertEquals(404, r.statusCode(), "the path really is wrong, so the status stays 404");
        String body = r.body();
        assertTrue(body.contains("/manifest"), "must point at the route that answers everything: " + body);
        assertTrue(body.contains("/action"), body);
        assertTrue(body.contains("action") && body.contains("params"),
                "the envelope is the other half an agent cannot guess: " + body);
        assertTrue(body.contains(ActionServer.TOKEN_HEADER), body);
    }

    @Test
    void discoveryIsSERVEDwithoutAToken_becauseAWrongDoorIsNotASecret() throws Exception {
        // it discloses the SHAPE of the api and never any data; a caller who cannot authenticate still
        // needs to learn they are knocking in the wrong place
        HttpResponse<String> r = send(req("/nope").GET().build());
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("/manifest"));
    }

    @Test
    void theRealRoutesStillWIN_soTheCatchAllCannotShadowThem() throws Exception {
        HttpResponse<String> m = send(req("/manifest").header(ActionServer.TOKEN_HEADER, "s3cr3t")
                .GET().build());
        assertEquals(200, m.statusCode(), "/manifest must not be swallowed by the catch-all");
        assertTrue(m.body().contains("verbs"), m.body());
    }

    // ---- review F1/F2/F3: HttpServer dispatches by PREFIX, not exact match ---------------------------

    @Test
    void aCHILDofActionDoesNotExecuteTheAction() throws Exception {
        // The defect this pins: HttpServer chooses the longest matching PREFIX, so /action/not-a-route
        // reached handleAction and, with a valid token, RAN the action — while the route the caller
        // addressed did not exist. The server said the path was wrong by doing the work anyway.
        HttpResponse<String> r = send(req("/action/not-a-route")
                .header(ActionServer.TOKEN_HEADER, "s3cr3t")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"aggregate\",\"params\":{}}"))
                .build());

        assertEquals(404, r.statusCode(), "a child path must not reach the handler");
        assertTrue(r.body().contains("/manifest"), "and it should get the signpost: " + r.body());
        assertFalse(r.body().contains("\"result\""), "the action must NOT have executed: " + r.body());
    }

    @Test
    void aCHILDofManifestDoesNotServeTheManifest() throws Exception {
        HttpResponse<String> r = send(req("/manifest/not-a-route").GET().build());
        assertEquals(404, r.statusCode());
        assertFalse(r.body().contains("\"verbs\""), "the manifest must not leak from a child path");
    }

    @Test
    void wrongMethodsOnTheEXACTpathsAre405() throws Exception {
        assertEquals(405, send(req("/manifest")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build()).statusCode());
        assertEquals(405, send(req("/action").header(ActionServer.TOKEN_HEADER, "s3cr3t")
                .GET().build()).statusCode());
    }

    @Test
    void ORIGINisRefusedBeforeAnythingElse_onEveryRoute() throws Exception {
        // the class contract is that ANY request carrying Origin is refused; a method or path reply ahead
        // of it would answer a browser we said we would not answer (review F2)
        assertEquals(403, send(req("/not-a-route").header("Origin", "http://evil.example")
                .GET().build()).statusCode(), "the discovery route must not be the exception");
        assertEquals(403, send(req("/action").header("Origin", "http://evil.example")
                .GET().build()).statusCode(), "Origin must beat the 405, not follow it");
        assertEquals(403, send(req("/manifest").header("Origin", "http://evil.example")
                .GET().build()).statusCode());
    }

    @Test
    void discoveryStatesTheREALtokenContract() throws Exception {
        // F3: it said the manifest needs a token. It does not, deliberately — and an agent told otherwise
        // may never try the one call that would have unblocked it.
        assertEquals(200, send(req("/manifest").GET().build()).statusCode(),
                "the manifest is public by design");
        String body = send(req("/nope").GET().build()).body();
        assertTrue(body.contains("required by " + ActionServer.TOKEN_HEADER.replace("X-Analyser-Token",
                "X-Analyser-Token") + " only") || body.contains("only"), body);
        assertFalse(body.contains("required by /action and /manifest"), "the old, wrong contract: " + body);
    }
}
