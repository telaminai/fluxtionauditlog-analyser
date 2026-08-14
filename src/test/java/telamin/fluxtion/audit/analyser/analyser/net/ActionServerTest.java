package telamin.fluxtion.audit.analyser.analyser.net;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
