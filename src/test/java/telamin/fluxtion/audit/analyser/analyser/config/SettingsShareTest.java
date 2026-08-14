package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.ImportPlan;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingsShareTest {

    private static final Set<Category> ALL = EnumSet.allOf(Category.class);
    private final SettingsShare share = new SettingsShare("/home/tester");

    /** A config with something set in every category, plus secrets that must never travel. */
    private static AppConfig populated() {
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.addAll(List.of("/home/tester/proj/src", "/opt/other/src"));
        c.mavenRepos.clear();
        c.mavenRepos.add("/home/tester/.m2/repository");
        c.searchMavenRepos = false;
        c.eventProcessorFqns.clear();
        c.eventProcessorFqns.add("com.acme.Strategy");
        c.selectedEventProcessor = "com.acme.Strategy";
        c.hiddenColumns.clear();
        c.hiddenColumns.addAll(List.of("eventTime", "endTime"));
        c.savedGraphs.add(new GraphSpec("Spreads", List.of("bookA.mid", "bookB.mid")));
        c.savedGraphs.add(new GraphSpec("Pick-off",
                List.of("askMakerOrder.price"),
                List.of(new GraphSpec.ExprSpec("spread", "askMakerOrder.price − bidMakerOrder.price", "LOCF")),
                null, null));
        c.assistantActionsInProcess = false;
        c.assistantActionsRest = true;
        c.maxActionRounds = 7;
        c.maxActionsPerReply = 9;
        c.llmProvider = "openai";
        c.llmModel = "gpt-x";
        c.llmBaseUrl = "https://internal-proxy.local";
        // secrets / machine-local — must be excluded from any share
        c.apiKey = "sk-secret-123";
        c.awsProfile = "prod-account";
        c.awsRegion = "eu-west-2";
        c.logFile = "/home/tester/secret/audit.yaml";
        c.addRecent("/home/tester/secret/audit.yaml");
        c.addSearch("customer-account-42");
        return c;
    }

    private static AppConfig emptyTarget() {
        AppConfig d = new AppConfig();
        d.sourceRoots.clear();
        d.mavenRepos.clear();
        d.eventProcessorFqns.clear();
        d.hiddenColumns.clear();
        d.savedGraphs.clear();
        return d;
    }

    @Test
    void roundTripsWhitelistedFields() {
        AppConfig c = populated();
        String text = share.export(c, ALL);

        AppConfig d = emptyTarget();
        ImportPlan plan = share.preview(text, d);
        share.apply(plan, plan.present(), d);

        assertEquals(List.of("/home/tester/proj/src", "/opt/other/src"), d.sourceRoots);
        assertEquals(List.of("/home/tester/.m2/repository"), d.mavenRepos);
        assertFalse(d.searchMavenRepos);
        assertEquals(List.of("com.acme.Strategy"), d.eventProcessorFqns);
        assertEquals("com.acme.Strategy", d.selectedEventProcessor);
        assertEquals(List.of("eventTime", "endTime"), d.hiddenColumns);
        assertEquals(2, d.savedGraphs.size());
        assertEquals("Spreads", d.savedGraphs.get(0).name());
        assertEquals("Pick-off", d.savedGraphs.get(1).name());
        assertEquals("askMakerOrder.price − bidMakerOrder.price", d.savedGraphs.get(1).exprs().get(0).expr());
        assertFalse(d.assistantActionsInProcess);
        assertTrue(d.assistantActionsRest);
        assertEquals(7, d.maxActionRounds);
        assertEquals(9, d.maxActionsPerReply);
        assertEquals("openai", d.llmProvider);
        assertEquals("gpt-x", d.llmModel);
        assertEquals("https://internal-proxy.local", d.llmBaseUrl);
    }

    @Test
    void exportNeverContainsSecrets() {
        String text = share.export(populated(), ALL);
        assertFalse(text.contains("sk-secret-123"), "api key value leaked");
        assertFalse(text.contains("apiKey"), "apiKey key leaked");
        assertFalse(text.contains("awsProfile"));
        assertFalse(text.contains("prod-account"));
        assertFalse(text.contains("awsRegion"));
        assertFalse(text.contains("recentFile"));
        assertFalse(text.contains("searchHistory"));
        assertFalse(text.contains("customer-account-42"));
        assertFalse(text.contains("logFile"));
    }

    @Test
    void llmOffByDefaultOnExport() {
        String text = share.export(populated(), Category.defaults());
        assertFalse(Category.defaults().contains(Category.LLM));
        assertFalse(text.contains("llmProvider"));
        assertFalse(text.contains("internal-proxy"));
        // but a default category is present
        assertTrue(text.contains("sourceRoot.count"));
    }

    @Test
    void importIgnoresNonWhitelistedKeys() {
        // a hand-crafted file trying to plant a secret and a machine-local path
        String malicious = """
                share.version=1
                apiKey=evil-key
                awsProfile=attacker
                logFile=/etc/shadow
                sourceRoot.count=1
                sourceRoot.0=/opt/legit/src
                """;
        AppConfig d = emptyTarget();
        d.apiKey = "my-own-key";
        d.awsProfile = "my-profile";

        ImportPlan plan = share.preview(malicious, d);
        share.apply(plan, plan.present(), d);

        assertEquals("my-own-key", d.apiKey, "import must never overwrite a local secret");
        assertEquals("my-profile", d.awsProfile);
        assertNull(d.logFile);
        assertEquals(List.of("/opt/legit/src"), d.sourceRoots, "whitelisted key still imported");
    }

    @Test
    void additiveListsDedup() {
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.addAll(List.of("/a", "/b"));
        String text = share.export(c, EnumSet.of(Category.SOURCE_ROOTS));

        AppConfig d = new AppConfig();
        d.sourceRoots.clear();
        d.sourceRoots.addAll(List.of("/b", "/c"));   // /b already present locally
        ImportPlan plan = share.preview(text, d);
        share.apply(plan, plan.present(), d);

        assertEquals(List.of("/b", "/c", "/a"), d.sourceRoots, "existing kept, only genuinely-new appended");
    }

    @Test
    void graphCollisionReplacesByNameNewNamesAppend() {
        AppConfig c = new AppConfig();
        c.savedGraphs.add(new GraphSpec("Spreads", List.of("x.new")));
        c.savedGraphs.add(new GraphSpec("Fresh", List.of("y.val")));
        String text = share.export(c, EnumSet.of(Category.GRAPHS));

        AppConfig d = emptyTarget();
        d.savedGraphs.add(new GraphSpec("Spreads", List.of("x.old")));   // same name, different series
        d.savedGraphs.add(new GraphSpec("Keep", List.of("z.val")));

        ImportPlan plan = share.preview(text, d);
        share.apply(plan, plan.present(), d);

        assertEquals(3, d.savedGraphs.size());
        assertEquals("Spreads", d.savedGraphs.get(0).name());
        assertEquals(List.of("x.new"), d.savedGraphs.get(0).series(), "collision replaced in place");
        assertEquals("Keep", d.savedGraphs.get(1).name(), "untouched local graph stays");
        assertEquals("Fresh", d.savedGraphs.get(2).name(), "new name appended");
    }

    @Test
    void deselectedCategoryIsUntouched() {
        AppConfig c = populated();
        String text = share.export(c, ALL);

        AppConfig d = emptyTarget();
        d.llmProvider = "anthropic";
        ImportPlan plan = share.preview(text, d);
        // apply everything EXCEPT llm
        Set<Category> selected = EnumSet.copyOf(plan.present());
        selected.remove(Category.LLM);
        share.apply(plan, selected, d);

        assertEquals("anthropic", d.llmProvider, "deselected LLM category left alone");
        assertEquals(List.of("com.acme.Strategy"), d.eventProcessorFqns, "selected category still applied");
    }

    @Test
    void homeRelativePathsRoundTripAcrossMachines() {
        assertEquals("~/proj/src", share.toPortable("/home/tester/proj/src"));
        assertEquals("~", share.toPortable("/home/tester"));
        assertEquals("/opt/other/src", share.toPortable("/opt/other/src"), "outside home stays verbatim");

        // a different importer home expands ~ to its own home
        SettingsShare bob = new SettingsShare("/home/bob");
        assertEquals("/home/bob/proj/src", bob.fromPortable("~/proj/src"));
        assertEquals("/home/bob", bob.fromPortable("~"));
        assertEquals("/opt/other/src", bob.fromPortable("/opt/other/src"));
    }

    @Test
    void missingPathImportsWithoutError() {
        String text = share.export(makeRoots("/home/tester/does/not/exist"), EnumSet.of(Category.SOURCE_ROOTS));
        AppConfig d = emptyTarget();
        ImportPlan plan = share.preview(text, d);   // no filesystem check — never fails on a missing dir
        share.apply(plan, plan.present(), d);
        assertEquals(List.of("/home/tester/does/not/exist"), d.sourceRoots);
    }

    @Test
    void versionGateRejectsNewerMajor() {
        String future = "share.version=2\nsourceRoot.count=1\nsourceRoot.0=/x\n";
        SettingsShare.IncompatibleVersionException ex = assertThrows(
                SettingsShare.IncompatibleVersionException.class,
                () -> share.preview(future, new AppConfig()));
        assertTrue(ex.getMessage().contains("newer"), "message should explain the version problem");
    }

    @Test
    void missingVersionTreatedAsOne() {
        String noVersion = "sourceRoot.count=1\nsourceRoot.0=/x\n";
        ImportPlan plan = share.preview(noVersion, emptyTarget());
        assertEquals(1, plan.version());
        assertEquals(List.of("/x"), plan.sourceRoots());
    }

    private static AppConfig makeRoots(String... roots) {
        AppConfig c = new AppConfig();
        c.sourceRoots.clear();
        c.sourceRoots.addAll(List.of(roots));
        return c;
    }
}