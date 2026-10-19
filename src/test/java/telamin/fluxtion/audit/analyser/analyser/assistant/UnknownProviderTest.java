package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.LlmClient;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §8.2 (h), §10 P2): an unknown {@code llmProvider} is refused and said — never sent to Anthropic with the
 * stored key.
 */
class UnknownProviderTest {

    /** ★ Witness: the old {@code default -> new AnthropicClient(...)} branch — the throw assertion fails. */
    @Test
    @DisplayName("★ forProvider(\"acme\") throws; blank is anthropic; the known ones build")
    void forProviderRefusesTheUnknown() {
        var e = assertThrows(LlmClient.UnknownProvider.class, () -> LlmClient.forProvider("acme", "k", "", ""));
        assertEquals("unknown provider 'acme'", e.getMessage());
        assertEquals("AnthropicClient", LlmClient.forProvider("", "k", "", "").getClass().getSimpleName());
        assertEquals("AnthropicClient", LlmClient.forProvider(null, "k", "", "").getClass().getSimpleName());
        assertEquals("OpenAiClient", LlmClient.forProvider("openai", "k", "", "").getClass().getSimpleName());
        assertEquals(List.of("anthropic", "openai"), LlmClient.KNOWN_PROVIDERS);
    }

    @Test
    @DisplayName("the in-app assistant shows the words, and the stored key reaches no server")
    void theAssistantSaysSoAndSendsNothing() throws Exception {
        try (FakeProvider provider = new FakeProvider()) {
            var h = new AssistantAdapterTest.Harness(provider);
            try {
                var route = new SessionEvents.AssistantRoute("acme", "", provider.url(), true, false, 3, 20, 30);
                h.adapter.perform(new SessionEffects.RequestAssistantCompletionEffect(0, 1, 1, List.of(), route));
                Object fact = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (fact == null && System.nanoTime() < deadline) {
                    fact = h.posted.poll();
                    if (fact == null) Thread.sleep(10);
                }
                var failed = assertInstanceOf(SessionEvents.AssistantCompletionFailed.class, fact);
                assertEquals("the configured provider 'acme' is not one the analyser knows — choose one in Settings ▸ "
                        + "Assistant", failed.reason());
                assertTrue(provider.bodies.isEmpty() && provider.keys.isEmpty(), "nothing was sent anywhere");
            } finally {
                h.adapter.shutdown();
            }
        }
    }
}
