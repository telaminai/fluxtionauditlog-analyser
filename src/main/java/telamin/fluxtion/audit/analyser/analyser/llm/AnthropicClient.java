package telamin.fluxtion.audit.analyser.analyser.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API client (spec §10). */
public final class AnthropicClient implements LlmClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AnthropicClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com" : baseUrl.trim();
    }

    @Override
    public String complete(String system, List<Message> messages) throws IOException, InterruptedException {
        List<Object> msgs = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role());
            mm.put("content", m.content());
            msgs.add(mm);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 2048);
        body.put("system", system);
        body.put("messages", msgs);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey == null ? "" : apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Anthropic HTTP " + resp.statusCode() + ": " + snippet(resp.body()));
        }
        Object root = Json.parse(resp.body());
        // content is an array of blocks; concatenate the text blocks
        Object content = Json.at(root, "content");
        StringBuilder text = new StringBuilder();
        if (content instanceof List<?> blocks) {
            for (Object b : blocks) {
                Object t = Json.at(b, "text");
                if (t instanceof String s) text.append(s);
            }
        }
        return text.length() > 0 ? text.toString() : resp.body();
    }

    private static String snippet(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
    }
}
