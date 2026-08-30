package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AX10 — the loopback-only playground origin override.
 *
 * <p>The point of this hole is that it is narrow, so the tests that matter are the REFUSALS. A test suite
 * that only proved {@code http://127.0.0.1} works would pass just as happily if the HTTPS rule had been
 * deleted outright.
 */
class PlaygroundOriginTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(TemplateClient.ORIGIN_PROPERTY);
    }

    private static void set(String value) {
        System.setProperty(TemplateClient.ORIGIN_PROPERTY, value);
    }

    @Test
    void withNoPropertyTheShippedBehaviourIsUnchanged() {
        assertFalse(TemplateClient.originOverridden());
        assertEquals(TemplateClient.PLAYGROUND, TemplateClient.configuredOrigin());
    }

    @Test
    void aBlankPropertyIsNotAnOverride() {
        set("   ");
        assertFalse(TemplateClient.originOverridden(), "blank must not read as 'somewhere else'");
        assertEquals(TemplateClient.PLAYGROUND, TemplateClient.configuredOrigin());
    }

    @Test
    void loopbackHttpIsAcceptedInAllThreeSpellings() {
        for (String host : new String[]{"http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080"}) {
            set(host);
            assertTrue(TemplateClient.originOverridden(), host);
            assertEquals(host + "/", TemplateClient.configuredOrigin().toString(), host);
        }
    }

    @Test
    void plainHttpToAnyNonLoopbackHostIsREFUSED() {
        // the whole value of the override is that it cannot be widened into an arbitrary http fetch
        for (String origin : new String[]{
                "http://evil.example",
                "http://templates.example:8080",
                "http://127.0.0.1.evil.example",      // loopback as a PREFIX of a real host
                "http://localhost.evil.example",
                "http://192.168.0.10",                 // private, but not loopback
                "http://127.0.0.2"}) {                 // loopback range, but not the exact spelling
            set(origin);
            TemplateClient.Failure e = assertThrows(TemplateClient.Failure.class,
                    TemplateClient::configuredOrigin, origin + " must be refused");
            assertTrue(e.getMessage().contains("loopback"), e.getMessage());
        }
    }

    @Test
    void httpsIsStillAcceptedForAnyHost_soTheRuleWasNotSimplyDeleted() {
        set("https://templates.example");
        assertEquals("https://templates.example/", TemplateClient.configuredOrigin().toString());
    }

    @Test
    void anOriginMustSTILLBeBareEvenOnLoopback() {
        for (String bad : new String[]{
                "http://127.0.0.1:8080/starter-templates",   // a path
                "http://127.0.0.1:8080?x=1",                 // a query
                "http://user:pw@127.0.0.1:8080",             // credentials
                "file:///etc/passwd",
                "not a uri at all"}) {
            set(bad);
            assertThrows(TemplateClient.Failure.class, TemplateClient::configuredOrigin, bad);
        }
    }

    @Test
    void theFailureNamesThePropertySoItCanBeUNSET() {
        set("http://evil.example");
        TemplateClient.Failure e = assertThrows(TemplateClient.Failure.class, TemplateClient::configuredOrigin);
        assertTrue(e.getMessage().contains(TemplateClient.ORIGIN_PROPERTY),
                "a refusal the user cannot act on is a dead end: " + e.getMessage());
    }

    @Test
    void everyRequestGoesToTheOVERRIDDENoriginAndNowhereElse() {
        java.util.List<URI> asked = new java.util.ArrayList<>();
        TemplateClient client = new TemplateClient(URI.create("http://127.0.0.1:8080"), (uri, max) -> {
            asked.add(uri);
            return new TemplateClient.Reply(404, "text/plain", new byte[0]);
        });
        assertEquals("http://127.0.0.1:8080/", client.origin().toString());
        assertThrows(TemplateClient.Failure.class, () -> client.catalogue("1.0.0"));
        assertEquals(1, asked.size());
        assertEquals("http://127.0.0.1:8080/starter-templates/index.json", asked.get(0).toString(),
                "an overridden origin must redirect the request, not merely be recorded");
        assertTrue(client.manualUrl().toString().startsWith("http://127.0.0.1:8080/"),
                "the manual fallback URL must not send the user to the pinned playground instead");
    }

    @Test
    void theOverrideIsNotAPersistableProfileKey() {
        // it is a JVM property by design: a stored origin outlives the experiment and travels with a
        // shared project. If this ever becomes a profile key, that must be a deliberate decision.
        assertFalse(telamin.fluxtion.audit.analyser.analyser.config.KnownKeys.PROFILE_FAMILIES
                        .contains(TemplateClient.ORIGIN_PROPERTY),
                "the playground origin must not be storable in a project profile");
        assertFalse(telamin.fluxtion.audit.analyser.analyser.config.KnownKeys.CONFIG_FAMILIES
                        .contains(TemplateClient.ORIGIN_PROPERTY),
                "the playground origin must not be storable in app settings either");
    }
}
