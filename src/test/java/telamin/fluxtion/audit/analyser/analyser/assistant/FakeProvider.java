package telamin.fluxtion.audit.analyser.analyser.assistant;

import com.sun.net.httpserver.HttpServer;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * A loopback stand-in for the provider's messages endpoint (OA-1 tests): the analyser's own {@code AnthropicClient} talks
 * to it through its configurable base URL, so the adapter, the client's request building and its reply parsing are all
 * the real ones. Scripted replies; every request body and key header kept; an optional gate holds a reply; a status makes
 * it fail. Nothing leaves the machine, and no real key is ever used.
 */
public final class FakeProvider implements AutoCloseable {

    public static final String KEY = "sk-DEMO-not-a-real-key-000000";

    final HttpServer server;
    public final List<String> bodies = new CopyOnWriteArrayList<>();
    public final List<String> keys = new CopyOnWriteArrayList<>();
    public final LinkedBlockingDeque<String> replies = new LinkedBlockingDeque<>();
    public volatile CountDownLatch gate;
    public volatile int status = 200;

    public FakeProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(ex.getRequestHeaders().getFirst("x-api-key"));
            try {
                CountDownLatch g = gate;
                if (g != null) g.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            String reply = replies.pollFirst();
            String body = status == 200
                    ? Json.write(Map.of("content", List.of(Map.of("type", "text", "text", reply == null ? "done" : reply))))
                    : "{\"error\":\"denied for key " + KEY + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A fenced action block as a model writes one. */
    public static String action(String json) {
        return "```analyser-action\n" + json + "\n```";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
