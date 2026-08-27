package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.3 (spec-portable-context D-C4) — environments and the §E provenance each stamps. The matcher is pure
 * and consulted only when nobody declared; the declaration is gated at every entrance; the category
 * travels by default (owner decision 2) with a label that names the cargo.
 */
class EnvironmentTest {

    private static final Environment PROD = new Environment("prod", "risk-engine · prod · ldn", "logs/prod");
    private static final Environment UAT = new Environment("uat", "risk-engine · uat", "logs/uat");
    private static final Environment DEV = new Environment("dev", "risk-engine · dev", null);

    @Test
    void theMatcherPicksTheLogDirectory_thenTheDefault_thenNothing(@TempDir Path root) {
        List<Environment> envs = List.of(PROD, UAT, DEV);
        var m = Environment.match(envs, "dev", root, root.resolve("logs/prod/2026-08-27/audit.yaml"));
        assertEquals("prod", m.get().environment().name());
        assertTrue(m.get().reason().contains("under logs/prod"), m.get().reason());

        m = Environment.match(envs, "dev", root, root.resolve("exports/audit.yaml"));
        assertEquals("dev", m.get().environment().name(), "outside every logDir: the default");
        assertEquals("project default environment 'dev'", m.get().reason());

        assertTrue(Environment.match(envs, "", root, root.resolve("exports/audit.yaml")).isEmpty(), "no default, no match: nothing — never a guess");
        assertTrue(Environment.match(List.of(), "prod", root, root.resolve("logs/prod/a.yaml")).isEmpty());
        assertEquals("prod", Environment.match(List.of(PROD), "prod", null, null).get().environment().name(),
                "no project root or local file (an S3 open): only the default can apply");
        assertTrue(Environment.match(List.of(PROD), "", null, root.resolve("logs/prod/a.yaml")).isEmpty(),
                "a logDir cannot match without a root to resolve against");
        assertTrue(Environment.match(envs, "dev", root, root.resolve("logs/production/a.yaml")).get().reason().contains("default"),
                "a directory-name PREFIX is not containment");
    }

    @Test
    void declarationsAreGated_withReasons() {
        assertTrue(Environment.refuse(PROD).isEmpty());
        assertTrue(Environment.refuse(DEV).isEmpty(), "logDir is optional");
        assertTrue(Environment.refuse(new Environment("bad name", "x", null)).get().contains("name"));
        assertTrue(Environment.refuse(new Environment("prod", "", null)).get().contains("no provenance"));
        assertTrue(Environment.refuse(new Environment("prod", "a\nb", null)).get().contains("one line"));
        assertTrue(Environment.refuse(new Environment("prod", "p".repeat(Environment.MAX_PROVENANCE + 1), null)).get().contains("longer"));
        assertTrue(Environment.refuse(new Environment("prod", "x", "/var/log")).get().contains("absolute"));
        assertTrue(Environment.refuse(new Environment("prod", "x", "../logs")).get().contains("escapes"));
        assertTrue(Environment.refuse(new Environment("prod", "x", "logs; rm -rf /")).get().contains("not a plain relative path"));
    }

    @Test
    void theLoaderDropsRefusedDeclarations_andTheShareCategoryTravelsByDefault(@TempDir Path dir) throws Exception {
        Properties p = new Properties();
        p.setProperty("environment.count", "2");
        p.setProperty("environment.0.name", "prod");
        p.setProperty("environment.0.provenance", "risk-engine · prod");
        p.setProperty("environment.0.logDir", "logs/prod");
        p.setProperty("environment.1.name", "evil");
        p.setProperty("environment.1.provenance", "x");
        p.setProperty("environment.1.logDir", "../../etc");
        List<Environment> out = new java.util.ArrayList<>();
        var refused = ConfigStore.readEnvironments(p, out);
        assertEquals(1, out.size());
        assertEquals(1, refused.size());

        assertTrue(SettingsShare.Category.ENVIRONMENTS.defaultOn, "owner decision 2: environments travel by default");
        String label = SettingsShare.Category.ENVIRONMENTS.label.toLowerCase();
        assertTrue(label.contains("hosts") && label.contains("never log data"), "the label names exactly what leaves: " + label);
        assertTrue(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.ENVIRONMENTS));

        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.environments.addAll(List.of(PROD, UAT));
        sender.defaultEnvironment = "uat";
        String text = share.export(sender, Set.of(SettingsShare.Category.ENVIRONMENTS), null);
        assertTrue(text.contains("environment.0.name=prod") && text.contains("environment.default=uat"), text);
        assertFalse(share.export(sender, Set.of(SettingsShare.Category.SOURCE_ROOTS), null).contains("environment"));

        AppConfig receiver = new AppConfig();
        var plan = share.preview(text + "environment.count=3\nenvironment.2.name=evil\nenvironment.2.provenance=x\nenvironment.2.logDir=/etc\n", receiver);
        assertTrue(plan.summary().get(SettingsShare.Category.ENVIRONMENTS).contains("1 REFUSED"), plan.summary().toString());
        share.apply(plan, Set.of(SettingsShare.Category.ENVIRONMENTS), receiver);
        assertEquals(List.of("prod", "uat"), receiver.environments.stream().map(Environment::name).toList());
        assertEquals("uat", receiver.defaultEnvironment);

        // profile round trip; a refused entry is announced by the loader and dropped
        Path file = dir.resolve(ProjectProfile.CANONICAL_RELATIVE);
        Files.createDirectories(file.getParent());
        assertTrue(ProjectProfile.save(file, sender, share));
        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded());
        assertEquals(2, back.environments.size());
        assertEquals("uat", back.defaultEnvironment);
        Files.writeString(file, Files.readString(file) + "environment.count=3\nenvironment.2.name=evil\nenvironment.2.provenance=x\nenvironment.2.logDir=../x\n");
        var loaded = ProjectProfile.load(file, back, share);
        assertTrue(loaded.message().contains("⚠ environments:"), loaded.message());
        assertEquals(2, back.environments.size());
        ProjectProfile.clearProjectScoped(back);
        assertTrue(back.environments.isEmpty() && back.defaultEnvironment.isEmpty());
    }
}
