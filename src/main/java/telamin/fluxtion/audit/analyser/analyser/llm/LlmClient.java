package telamin.fluxtion.audit.analyser.analyser.llm;

import java.io.IOException;
import java.util.List;

/** Abstraction over a chat LLM provider (spec §10). Implementations use {@code java.net.http}. */
public interface LlmClient {

    /** Sends the system prompt + full conversation and returns the assistant's reply text. */
    String complete(String system, List<Message> messages) throws IOException, InterruptedException;

    /** Builds a client for the configured provider; blank model falls back to a sensible default. */
    static LlmClient forProvider(String provider, String apiKey, String model, String baseUrl) {
        String p = provider == null ? "anthropic" : provider.trim().toLowerCase();
        return switch (p) {
            case "openai" -> new OpenAiClient(apiKey, blankOr(model, "gpt-4o"), baseUrl);
            default -> new AnthropicClient(apiKey, blankOr(model, "claude-sonnet-5"), baseUrl);
        };
    }

    private static String blankOr(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
