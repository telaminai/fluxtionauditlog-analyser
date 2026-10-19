package telamin.fluxtion.audit.analyser.analyser.llm;

import java.io.IOException;
import java.util.List;

/** Abstraction over a chat LLM provider (spec §10). Implementations use {@code java.net.http}. */
public interface LlmClient {

    /** Sends the system prompt + full conversation and returns the assistant's reply text. */
    String complete(String system, List<Message> messages) throws IOException, InterruptedException;

    /** Builds a client for the configured provider; blank model falls back to a sensible default. */
    /**
     * The client for {@code provider}. M74 P2b (§8.2 (h)): a provider outside the ones implemented here is REFUSED —
     * never silently sent to Anthropic with the stored key; a blank or absent provider is a fresh config's
     * {@code anthropic}.
     */
    static LlmClient forProvider(String provider, String apiKey, String model, String baseUrl) {
        String p = provider == null || provider.isBlank() ? "anthropic" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (p) {
            case "openai" -> new OpenAiClient(apiKey, blankOr(model, "gpt-4o"), baseUrl);
            case "anthropic" -> new AnthropicClient(apiKey, blankOr(model, "claude-sonnet-5"), baseUrl);
            default -> throw new UnknownProvider(provider);
        };
    }

    /** The providers {@link #forProvider} implements (read from the code above, not from a spec). */
    java.util.List<String> KNOWN_PROVIDERS = java.util.List.of("anthropic", "openai");

    /** A configured provider the analyser does not know: said to the person, never sent anywhere. */
    final class UnknownProvider extends IllegalArgumentException {
        public UnknownProvider(String provider) {
            super("unknown provider '" + provider + "'");
        }

        /** What the in-app assistant shows (§8.2 (h)). */
        public static String words(String provider) {
            return "the configured provider '" + provider + "' is not one the analyser knows — choose one in Settings ▸ "
                    + "Assistant";
        }
    }

    private static String blankOr(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
