package telamin.fluxtion.audit.analyser.analyser.assistant;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.llm.LogFileInfo;
import telamin.fluxtion.audit.analyser.analyser.llm.RenderExecutor;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-1 through the REAL adapter and the REAL provider client (spec §9: "injected provider transports and local fake
 * servers … do not bypass actual parsing, adapter or dispatcher boundaries"). A loopback HTTP server stands in for the
 * provider's endpoint; the analyser's own {@code AnthropicClient} talks to it; {@code ActionParser} finds the actions;
 * the shared {@code ActionDispatcher} runs them against a recording render executor with NO log loaded; the generated
 * session processor decides every step. No key is real and no network leaves the machine.
 */
class AssistantAdapterTest {

    private static final String KEY = FakeProvider.KEY;

    /** The render verbs the dispatcher reaches, recorded; each answers as the real one would, with its own result. */
    static final class RecordingRender implements RenderExecutor {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public ActionResult render(String action, Map<String, Object> params) {
            calls.add(action);
            return switch (action) {
                case "context" -> ActionResult.ok("context", "context", Map.of("log", Map.of("open", false), "marker", "CTX-7"));
                case "spotlight" -> ActionResult.ok("spotlight", "lit", Map.of("lit", 1, "marker", "LIT-3"));
                default -> ActionResult.error(action + " is not scripted here");
            };
        }
    }

    /** A session adapter that performs ONLY the assistant's effects, through the real AssistantAdapter. */
    static final class Harness implements SessionDriver.Adapter {
        final AppConfig config = new AppConfig();
        final RecordingRender render = new RecordingRender();
        final ConcurrentLinkedQueue<Object> posted = new ConcurrentLinkedQueue<>();
        final AssistantTranscript transcript = new AssistantTranscript();
        final AssistantAdapter adapter;
        SessionDriver driver;

        Harness(FakeProvider provider) {
            config.apiKey = KEY;
            config.llmProvider = "anthropic";
            config.llmBaseUrl = provider.url();
            adapter = new AssistantAdapter(transcript, new AssistantAdapter.Environment() {
                @Override public AppConfig config() { return config; }
                @Override public List<LogRecord> selection() { return List.of(); }
                @Override public String epFqn() { return null; }
                @Override public LogFileInfo fileInfo() { return null; }
                @Override public String vocabulary() { return null; }
                @Override public SourceService sources() { return null; }
                @Override public ActionDispatcher dispatcher() {
                    // constructed exactly as the frame's actionDispatcher(): record verbs refuse with no log loaded
                    return new ActionDispatcher(false, null,
                            () -> { throw new IllegalStateException("no log loaded"); },
                            row -> null, row -> null, render);
                }
                @Override public AssistantState current() { return driver.snapshot().assistant(); }
                @Override public void post(Object fact) { posted.add(fact); }
                @Override public String showHost(boolean docked) { return null; }
            }, AssistantAdapter.PROVIDERS);
        }

        @Override
        public SessionEvents.Result perform(SessionEffects effect) {
            if (!AssistantAdapter.handles(effect)) throw new IllegalStateException("unexpected " + effect);
            return adapter.perform(effect);
        }

        SessionEvents.AssistantRoute route() {
            return new SessionEvents.AssistantRoute("anthropic", "", config.llmBaseUrl, true, true, 3, 20, 30);
        }

        void send(String question) {
            long id = transcript.add(AssistantTranscript.Kind.USER, question);
            post(new SessionEvents.AssistantSendRequested(id, id, route()));
        }

        /**
         * The session runs on the event thread, as in the application (the frame builds its driver there). That is what
         * orders the action worker's event-thread check AFTER the operation that asked for the action has published.
         */
        void post(Object fact) {
            try {
                javax.swing.SwingUtilities.invokeAndWait(() -> driver.post(fact));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        /** Feed posted facts into the session until the turn is no longer busy and nothing is left to feed. */
        void settle() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            int quiet = 0;
            while (System.nanoTime() < deadline) {
                Object fact = posted.poll();
                if (fact != null) {
                    post(fact);
                    quiet = 0;
                    continue;
                }
                if (!driver.snapshot().assistant().busy() && ++quiet > 20) return;
                Thread.sleep(10);
            }
            fail("the turn did not settle: " + driver.snapshot().assistant());
        }

        AssistantState state() {
            return driver.snapshot().assistant();
        }
    }

    private FakeProvider provider;
    private Harness h;

    private Harness start() throws Exception {
        provider = new FakeProvider();
        h = new Harness(provider);
        javax.swing.SwingUtilities.invokeAndWait(() -> h.driver = new SessionDriver(h));
        return h;
    }

    @AfterEach
    void stop() {
        if (h != null) h.adapter.shutdown();
        if (provider != null) provider.close();
    }

    private static String action(String json) {
        return FakeProvider.action(json);
    }

    @Test
    @DisplayName("OA-A2: a real round trip — the reply's actions run, and their ACTUAL results go back in the next request")
    void actualResultsAreFedBack() throws Exception {
        start();
        provider.replies.add("Let me look.\n" + action("{\"action\":\"context\"}") + "\n" + action("{\"action\":\"spotlight\",\"params\":{\"target\":\"status\"}}"));
        provider.replies.add("The log is not open, as context says.");
        h.send("What is open?");
        h.settle();
        assertEquals("COMPLETE", h.state().phase(), h.state().reason());
        assertEquals(2, provider.bodies.size(), "two provider requests: the question, then the results");
        assertEquals(List.of("context", "spotlight"), h.render.calls, "both actions ran, in order");
        String second = provider.bodies.get(1);
        assertTrue(second.contains("CTX-7"), "the context verb's ACTUAL result was sent back: " + second);
        assertTrue(second.contains("LIT-3"), "the render success's ACTUAL result was sent back too: " + second);
        var shown = h.state().entries().stream().filter(e -> AssistantState.ACTION.equals(e.kind())).toList();
        assertEquals(List.of("OK", "OK"), shown.stream().map(AssistantState.Entry::status).toList());
        assertTrue(h.transcript.text(shown.get(0).result()).contains("CTX-7"),
                "what the person sees is the dispatcher's result, not the model's prose");
        assertEquals(List.of(KEY, KEY), provider.keys, "the configured key reached the provider, and only there");
    }

    @Test
    @DisplayName("OA-A3: with no log, context runs onboard and a record verb refuses by name")
    void noLogKeepsTheManifestAndRefusesRecordVerbs() throws Exception {
        start();
        provider.replies.add(action("{\"action\":\"context\"}") + "\n" + action("{\"action\":\"aggregate\",\"params\":{\"groupBy\":\"dimension\"}}"));
        provider.replies.add("Nothing is loaded yet.");
        h.send("How many records?");
        h.settle();
        var shown = h.state().entries().stream().filter(e -> AssistantState.ACTION.equals(e.kind())).toList();
        assertEquals("OK", shown.get(0).status(), "context works without a log: " + h.transcript.text(shown.get(0).result())
                + " | " + h.transcript.text(shown.get(0).id()));
        assertEquals("REFUSED", shown.get(1).status(), "aggregate refuses without a log");
        assertTrue(h.transcript.text(shown.get(1).result()).contains("no log loaded"), h.transcript.text(shown.get(1).result()));
        assertTrue(provider.bodies.get(0).contains("analyser-action"), "the action manifest was sent although no log is open");
    }

    @Test
    @DisplayName("OA-A4: cancel while the provider holds its reply; the late reply's action never runs; a new Send works")
    void aHeldReplyReleasedAfterCancelDoesNothing() throws Exception {
        start();
        provider.gate = new CountDownLatch(1);
        provider.replies.add(action("{\"action\":\"spotlight\",\"params\":{\"target\":\"status\"}}"));
        h.send("Light the status line");
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (provider.bodies.isEmpty() && System.nanoTime() < until) {
            Object f = h.posted.poll();
            if (f != null) h.post(f); else Thread.sleep(10);
        }
        assertEquals(1, provider.bodies.size(), "the request is in flight");
        h.post(new SessionEvents.AssistantCancelRequested("test"));
        int entries = h.state().entries().size();
        provider.gate.countDown();          // the provider answers anyway
        Thread.sleep(500);
        h.settle();
        assertEquals(List.of(), h.render.calls, "the late reply's action never ran");
        assertEquals("CANCELLED", h.state().phase());
        assertEquals(entries, h.state().entries().size(), "nothing was appended after the cancel");
        provider.gate = null;
        provider.replies.clear();
        provider.replies.add("Fresh answer.");
        h.send("Try again");
        h.settle();
        assertEquals("COMPLETE", h.state().phase(), "a later Send succeeds");
    }

    @Test
    @DisplayName("OA-A16: a provider error is terminal, bounded, and never repeats the key")
    void anErrorIsBoundedAndKeyless() throws Exception {
        start();
        provider.status = 401;
        h.send("Hello");
        h.settle();
        assertEquals("FAILED", h.state().phase());
        String reason = h.state().reason();
        assertTrue(reason.contains("401"), reason);
        assertFalse(reason.contains(KEY), "the key never appears in an error: " + reason);
        assertTrue(reason.length() < 500, "bounded");
        for (int id = 1; id <= h.transcript.size(); id++) {
            assertFalse(h.transcript.text(id).contains(KEY), "no transcript entry holds the key");
        }
    }

    @Test
    @DisplayName("OA-A16: an unknown verb and malformed JSON are refused results the model sees, not crashes")
    void unknownAndMalformedActionsAreRefused() throws Exception {
        start();
        provider.replies.add(action("{\"action\":\"format_disk\"}") + "\n" + action("{not json"));
        provider.replies.add("Understood.");
        h.send("Do something odd");
        h.settle();
        var shown = h.state().entries().stream().filter(e -> AssistantState.ACTION.equals(e.kind())).toList();
        assertEquals(List.of("REFUSED", "REFUSED"), shown.stream().map(AssistantState.Entry::status).toList());
        assertTrue(provider.bodies.get(1).contains("unknown verb"), "the refusal went back to the model");
        assertEquals(List.of(), h.render.calls, "nothing was rendered");
    }
}
