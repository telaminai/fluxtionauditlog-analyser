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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77-3 (spec-ai-setup-wizard.md §9.4, S8, ★ T11) — the live check on the REAL action server: a client reached THIS
 * window only when an authenticated external {@code /action} arrived after the watch was armed. Not {@code /manifest},
 * not a 401/403/429, not a hosted call, not the analyser's own marked probe, not a call before arming; reported once.
 */
class ActionServerLiveCheckTest {

    private ActionServer server;
    private final HostedCredentials credentials = new HostedCredentials();
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws Exception {
        // no log: every dispatched call answers 400 in the dispatcher's words — a client that reached this window
        server = new ActionServer(new ActionDispatcher(false, null, () -> null, null), "shared", 20, 2.0);
        server.setHostedGate(credentials.gate(g -> null));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpResponse<String> action(String token, String hosted, boolean probe, String label) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + "/action"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"context\",\"params\":{}}"));
        if (token != null) b.header("X-Analyser-Token", token);
        if (hosted != null) b.header(ActionServer.HOSTED_HEADER, hosted);
        if (probe) b.header(ActionServer.PROBE_HEADER, "1");
        if (label != null) b.header(ActionServer.CLIENT_LABEL_HEADER, label);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * ★ T11. Witness: count {@code /manifest} (or any call before the token check) — the first {@code seen} assertion
     * fails; or drop the probe-header exclusion — the marked call is reported.
     */
    @Test
    @DisplayName("★ T11: ✓ only after an authenticated external /action post-arm — never the manifest, a refusal, a hosted or probe call")
    void onlyAnAuthenticatedExternalCallAfterArmingCounts() throws Exception {
        assertEquals(400, action("shared", null, false, "early").statusCode(), "a call BEFORE arming");
        server.watchExternalClient(seen::add);
        assertTrue(seen.isEmpty(), "★ a call before arming does not count");

        assertEquals(200, http.send(HttpRequest.newBuilder(URI.create(server.url() + "/manifest")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, action(null, null, false, "x").statusCode());
        assertEquals(401, action("wrong", null, false, "x").statusCode());
        String hosted = credentials.mint(1);
        action(null, hosted, false, "x");                         // the analyser's own Claude Code session
        assertEquals(403, action(null, "made-up", false, "x").statusCode());
        assertEquals(400, action("shared", null, true, "probe").statusCode(), "the analyser's own probe");
        assertTrue(seen.isEmpty(), "★ none of those count: " + seen);

        // spend the bucket so the next call is refused 429 — a refused call never counts
        HttpResponse<String> limited;
        server.unwatchExternalClient();
        do {
            limited = action("shared", null, true, null);
        } while (limited.statusCode() != 429);
        server.watchExternalClient(seen::add);
        assertEquals(429, action("shared", null, false, "limited").statusCode());
        assertTrue(seen.isEmpty(), "★ a 429 does not count");

        Thread.sleep(1_200);                                     // the bucket refills
        assertEquals(400, action("shared", null, false, "claude-code\"<x>").statusCode(),
                "a 400 still counts: the client reached this window");
        assertEquals(List.of("claude-codex"), seen, "reported once, the label sanitised to printable ASCII (OD-77.8)");
        action("shared", null, false, "second");
        assertEquals(1, seen.size(), "★ once: the watch disarms itself");
    }

    /** A label is a display word only: printable ASCII, quotes and angle brackets dropped, at most 40 characters. */
    @Test
    @DisplayName("the client's self-reported name is sanitised: printable ASCII, no quotes or brackets, ≤ 40 characters")
    void theLabelIsSanitised() {
        assertEquals("claude-code", ActionServer.sanitisedLabel("  claude-code \n"));
        assertEquals("", ActionServer.sanitisedLabel(null));
        assertEquals(40, ActionServer.sanitisedLabel("x".repeat(100)).length());
        assertEquals("ab", ActionServer.sanitisedLabel("a<\"\\>b"));
    }
}
