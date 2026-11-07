package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TemplateCatalogueTest {

    @Test
    void compilerSignInDeclarationsSurviveTheCatalogueAndReviewFacts() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"AOT","description":"AOT","file":"aot.starter.json","mode":"aot","keyNeed":"sign-in-build"},
                  {"name":"Bundle","description":"Bundle","file":"bundle.starter.json","mode":"aot","keyNeed":"none",
                   "regenerationKeyNeed":"sign-in-build"}
                ]}
                """, "dev");
        var aot = catalogue.templates().getFirst();
        assertTrue(aot.disclosure().contains("Build authentication: compiler sign-in at build"));
        assertEquals("sign-in-build", Starters.of(aot, null).keyNeed());
        String facts = Starters.facts("spring", "mongoose", "aot", "sign-in-build");
        assertTrue(facts.contains("build needs compiler sign-in"));
        assertEquals(facts, Starters.facts("spring", "mongoose", "aot", "build"),
                "RAPID-OFF: an older catalogue's build key need is compiler sign-in too");
        assertTrue(catalogue.templates().getLast().disclosure()
                .contains("Regeneration authentication: compiler sign-in at build"));
    }

    @Test
    void recommendationDoesNotHideOtherTemplates() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"Other","description":"Not for this picker","file":"other.starter.json","type":"fluxtion"},
                  {"name":"Audit bundle","description":"A runnable audit example","file":"audit.starter.json",
                   "type":"hosted","mode":"aot","keyNeed":"none","tags":["onboarding"]}
                ]}
                """, "1.2.3");

        var selection = catalogue.forPicker();
        assertEquals(List.of("Audit bundle", "Other"), selection.entries().stream().map(TemplateCatalogue.Entry::name).toList(),
                "recommended first (owner, 2026-11-07); the others are still in the selection");
        assertTrue(selection.note().contains("All templates"));
        assertTrue(selection.entries().getFirst().recommended());
        assertTrue(selection.entries().getFirst().displayName().contains("Recommended"));
        assertEquals("none", selection.entries().getFirst().keyNeed());
        assertEquals(List.of("Audit bundle"), names(TemplateCatalogue.shown(catalogue.templates(), false)),
                "unticked, only the recommended entries are shown");
        assertEquals(List.of("Audit bundle", "Other"), names(TemplateCatalogue.shown(catalogue.templates(), true)));
    }

    static List<String> names(List<TemplateCatalogue.Entry> entries) {
        return entries.stream().map(TemplateCatalogue.Entry::name).toList();
    }

    /** The catalogue as served before 2026-11-07: the Spring bundle first, the Java-authoring starter second. */
    public static final String OLDER = """
            {"catalogue":1,"templates":[
              {"name":"Audit analyser bundle","description":"spring","file":"analyser-bundle.starter.json",
               "type":"mongoose","tags":["onboarding","web-admin","analyser"]},
              {"name":"Java authoring (Mongoose)","description":"java","file":"java-authoring.starter.json",
               "type":"mongoose","tags":["onboarding","web-admin","analyser","java-builder"]},
              {"name":"Fluxtion embedded","description":"e","file":"fluxtion-embedded.starter.json","type":"fluxtion"},
              {"name":"Fluxtion Spring in Mongoose","description":"s","file":"fluxtion-spring-mongoose.starter.json",
               "type":"hosted","tags":["onboarding","spring"]},
              {"name":"Mongoose dynamic feeds","description":"d","file":"mongoose-dynamic-feed.starter.json","type":"mongoose"}
            ]}
            """;

    @Test
    void javaAuthoringIsFirstEvenInAnOlderCatalogueAndRecommendedOnesPrecedeTheRest() {
        var catalogue = TemplateCatalogue.parse(OLDER, "dev");
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion Spring in Mongoose",
                        "Fluxtion embedded", "Mongoose dynamic feeds"), names(catalogue.forPicker().entries()),
                "Java authoring, the other recommended in catalogue order, then the rest in catalogue order");
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion Spring in Mongoose"),
                names(TemplateCatalogue.shown(catalogue.templates(), false)), "unticked: recommended only");
        // with the Java entry untagged for onboarding it is still first and recommended
        var untagged = TemplateCatalogue.parse(OLDER.replace("[\"onboarding\",\"web-admin\",\"analyser\",\"java-builder\"]",
                "[\"java-builder\"]"), "dev");
        assertEquals("Java authoring (Mongoose)", untagged.forPicker().entries().getFirst().name());
        assertTrue(untagged.forPicker().entries().getFirst().recommended());
        // and the current catalogue (2026-11-07: Java first, the Spring-in-Mongoose starter no longer onboarding)
        var current = TemplateCatalogue.parse(OLDER.replace("[\"onboarding\",\"spring\"]", "[\"spring\"]"), "dev");
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle"),
                names(TemplateCatalogue.shown(current.templates(), false)));
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion embedded",
                "Fluxtion Spring in Mongoose", "Mongoose dynamic feeds"), names(TemplateCatalogue.shown(current.templates(), true)));
    }

    @Test
    void theTemplateFileMarksTheJavaAuthoringStarterAndStartersOrderLikeTheCatalogue() {
        var t = TemplateClient.Template.parse("""
                {"artifact":"java-authoring","group":"com.example","basePackage":"com.example.myapp",
                 "type":{"fluxtion":true,"mongoose":true},"fluxtion":{"definition":"builder"},"javaAuthoring":true}
                """);
        assertEquals(Starters.JAVA, t.authoring(), "javaAuthoring wins over fluxtion.definition");
        assertEquals("builder", TemplateClient.Template.parse("{\"artifact\":\"a\",\"group\":\"g\",\"basePackage\":\"g.a\","
                + "\"fluxtion\":{\"definition\":\"builder\"}}").authoring());
        var catalogue = TemplateCatalogue.parse(OLDER, "dev");
        List<Starters.Entry> read = new ArrayList<>();
        for (var e : catalogue.templates()) {
            read.add(Starters.of(e, e.file().startsWith("java-") ? t : null));
        }
        assertEquals(names(catalogue.forPicker().entries()), Starters.ordered(read).stream().map(Starters.Entry::name).toList(),
                "the frame's order is the picker's");
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion Spring in Mongoose"),
                Starters.shown(read, false).stream().map(Starters.Entry::name).toList());
        String facts = Starters.facts(Starters.JAVA, "mongoose", "aot", "sign-in-build");
        assertEquals("Java · in a Mongoose server · AOT · no processor ships; the first build generates it · "
                + "build needs compiler sign-in (see README)", facts);
    }

    @Test
    void missingTagsStillShowEveryTypeWithoutRecommendations() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"DSL","description":"dsl","file":"dsl.starter.json","type":"fluxtion"},
                  {"name":"Server","description":"server","file":"server.starter.json","type":"mongoose"},
                  {"name":"Hosted","description":"hosted","file":"hosted.starter.json","type":"hosted"}
                ]}
                """, "dev");

        var selection = catalogue.forPicker();
        assertEquals(List.of("DSL", "Server", "Hosted"), selection.entries().stream().map(TemplateCatalogue.Entry::name).toList());
        assertTrue(selection.note().contains("not declared"));
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

    @Test void absentEmptyAndDeclaredBootstrapAndKeyRequirementsStayDistinct() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"Unknown","description":"unknown","file":"u.starter.json","mode":"aot","tags":["onboarding"]},
                  {"name":"None","description":"none","file":"n.starter.json","agentBootstrap":[],"keyNeed":"none","regenerationKeyNeed":"build"},
                  {"name":"Declared","description":"declared","file":"d.starter.json","agentBootstrap":["PROJECT.md","runbooks/author.md"],"keyNeed":"future","regenerationKeyNeed":"run"}
                ]}
                """, "dev");
        assertTrue(catalogue.templates().get(0).disclosure().contains("Build authentication: not declared"));
        assertTrue(catalogue.templates().get(0).disclosure().contains("Agent entry files: not declared"));
        assertTrue(catalogue.templates().get(1).disclosure().contains("Agent entry files: explicitly none"));
        assertTrue(catalogue.templates().get(1).disclosure().contains("Regeneration authentication: compiler sign-in at build"));
        assertTrue(catalogue.templates().get(2).disclosure().contains("PROJECT.md, runbooks/author.md"));
        assertTrue(catalogue.templates().get(2).disclosure().contains("unrecognised catalogue value 'future'"));
    }

    @Test void malformedBootstrapIsRefusedRatherThanClaimedAbsent() {
        for (String value : List.of("null", "true", "[1]", "[\"../outside.md\"]", "[\"/absolute.md\"]")) {
            String json = "{\"catalogue\":1,\"templates\":[{\"name\":\"X\",\"description\":\"X\",\"file\":\"x.starter.json\",\"agentBootstrap\":" + value + "}]}";
            assertThrows(IllegalArgumentException.class, () -> TemplateCatalogue.parse(json, "dev"));
        }
    }

    private static TemplateClient.Reply json(String body) {
        return new TemplateClient.Reply(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }
}
