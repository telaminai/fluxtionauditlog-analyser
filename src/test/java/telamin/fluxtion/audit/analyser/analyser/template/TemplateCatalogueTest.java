package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateCatalogueTest {

    @Test
    void taggedOnboardingSubsetComesFromCatalogue() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"Other","description":"Not for this picker","file":"other.starter.json","type":"fluxtion"},
                  {"name":"Audit bundle","description":"A runnable audit example","file":"audit.starter.json",
                   "type":"hosted","mode":"aot","keyNeed":"none","tags":["onboarding"]}
                ]}
                """, "1.2.3");

        var selection = catalogue.onboarding();
        assertEquals(List.of("Audit bundle"), selection.entries().stream().map(TemplateCatalogue.Entry::name).toList());
        assertEquals("", selection.note());
        assertEquals("none", selection.entries().getFirst().keyNeed());
    }

    @Test
    void missingTagsUseDeclaredMongooseFallbackAndSaySo() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"DSL","description":"dsl","file":"dsl.starter.json","type":"fluxtion"},
                  {"name":"Server","description":"server","file":"server.starter.json","type":"mongoose"},
                  {"name":"Hosted","description":"hosted","file":"hosted.starter.json","type":"hosted"}
                ]}
                """, "dev");

        var selection = catalogue.onboarding();
        assertEquals(List.of("Server", "Hosted"), selection.entries().stream().map(TemplateCatalogue.Entry::name).toList());
        assertTrue(selection.note().contains("not tagged"));
    }

    @Test
    void unsupportedOrMissingCatalogueVersionRefusesWithAnalyserVersion() {
        var unsupported = assertThrows(IllegalArgumentException.class,
                () -> TemplateCatalogue.parse("{\"catalogue\":2,\"templates\":[]}", "1.12.0"));
        assertTrue(unsupported.getMessage().contains("analyser 1.12.0"));
        assertTrue(unsupported.getMessage().contains("supports catalogue 1"));

        var missing = assertThrows(IllegalArgumentException.class,
                () -> TemplateCatalogue.parse("{\"templates\":[]}", "dev"));
        assertTrue(missing.getMessage().contains("version missing"));
    }

    @Test
    void catalogueFileCannotEscapeThePinnedTemplateDirectory() {
        var error = assertThrows(IllegalArgumentException.class, () -> TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"Bad","description":"bad","file":"../bad.starter.json","type":"hosted"}
                ]}
                """, "dev"));
        assertTrue(error.getMessage().contains("unsafe"));
    }

    @Test
    void clientBuildsEncodedRequestFromCatalogueAndTemplateDefaults() {
        List<URI> fetched = new ArrayList<>();
        TemplateClient client = new TemplateClient(URI.create("https://templates.example"), (uri, max) -> {
            fetched.add(uri);
            String path = uri.getPath();
            if (path.endsWith("index.json")) return json("""
                    {"catalogue":1,"templates":[
                      {"name":"Audit bundle","description":"audit","file":"audit.starter.json",
                       "type":"hosted","tags":["onboarding"]}
                    ]}
                    """);
            if (path.endsWith("audit.starter.json")) return json("""
                    {"artifact":"audit-default","group":"com.example","basePackage":"com.example.app"}
                    """);
            return new TemplateClient.Reply(200, "application/zip", new byte[]{'P', 'K', 3, 4});
        });

        var entry = client.catalogue("dev").entries().getFirst();
        var defaults = client.defaults(entry);
        assertEquals("audit-default", defaults.artifact());
        client.download(new TemplateClient.Download(entry, "my-audit", defaults.group(), defaults.basePackage()));
        assertTrue(fetched.getLast().getRawQuery().contains("artifact=my-audit"));
    }

    @Test
    void clientRefusesHttpOriginsAndOversizedBodies() {
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateClient(URI.create("http://templates.example"), (uri, max) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateClient(URI.create("https://templates.example/not-an-origin"), (uri, max) -> null));
        assertThrows(java.io.IOException.class,
                () -> TemplateClient.readBounded(new java.io.ByteArrayInputStream(new byte[5]), 4));
    }

    @Test
    void downloadValidationHappensBeforeAnyRequest() {
        var entry = new TemplateCatalogue.Entry("Audit", "audit", "audit.starter.json", "hosted", "aot", "none", List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateClient.Download(entry, "../bad", "com.example", "com.example.app"));
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateClient.Download(entry, "good", "bad-group-", "com.example.app"));
    }

    private static TemplateClient.Reply json(String body) {
        return new TemplateClient.Reply(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }
}
