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

/** OpenAI Chat Completions client (spec §10). */
public final class OpenAiClient implements LlmClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiClient(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com" : baseUrl.trim();
    }

    @Override
    public String complete(String system, List<Message> messages) throws IOException, InterruptedException {
        List<Object> msgs = new ArrayList<>();
        msgs.add(msg("system", system));
        for (Message m : messages) msgs.add(msg(m.role(), m.content()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", msgs);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OpenAI HTTP " + resp.statusCode() + ": " + snippet(resp.body()));
        }
        Object root = Json.parse(resp.body());
        Object content = Json.at(root, "choices", 0, "message", "content");
        return content instanceof String s ? s : resp.body();
    }

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static String snippet(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
    }
}
