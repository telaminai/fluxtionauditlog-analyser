package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.7 — rewrite what you own, preserve what you do not understand. Found live: an older analyser that opened
 * a profile written by a newer one dropped every key it did not know on its next save. Both writers now carry
 * over unknown key FAMILIES, while a family they own is still rewritten wholesale (so a list can shrink).
 */
class PreserveUnknownKeysTest {

    private static Properties load(Path f) throws Exception {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(f)) { p.load(r); }
        return p;
    }

    @Test
    void familiesAreTheKeyUpToTheFirstDot_andUnknownOnesAreListed() {
        assertEquals("sourceRoot", KnownKeys.family("sourceRoot.2"));
        assertEquals("theme", KnownKeys.family("theme"));
        Properties prev = new Properties();
        prev.setProperty("futureThing.0.name", "x");
        prev.setProperty("sourceRoot.7", "/stale");
        prev.setProperty("sourceRoot.count", "8");
        var unknown = KnownKeys.unknown(prev, KnownKeys.PROFILE_FAMILIES);
        assertEquals(java.util.Map.of("futureThing.0.name", "x"), unknown, "a known family is the writer's, however stale its members");
        assertTrue(KnownKeys.CONFIG_FAMILIES.containsAll(KnownKeys.PROFILE_FAMILIES), "the own-settings writer owns everything the profile writer does, and more");
    }

    @Test
    void theProfileWriterCarriesOverANewerVersionsFacts_andStillShrinksItsOwnLists(@TempDir Path dir) throws Exception {
        SettingsShare share = new SettingsShare();
        Path file = dir.resolve(ProjectProfile.CANONICAL_RELATIVE);
        Files.createDirectories(file.getParent());
        AppConfig c = new AppConfig();
        c.sourceRoots.add(dir.resolve("src/main/java").toString());
        c.runbooks.put("deploy", Runbooks.Pointer.of("ops/deploy.md"));
        assertTrue(ProjectProfile.save(file, c, share));

        // "a newer version" wrote two things this build has never heard of; and this build's user removed the runbook
        Files.writeString(file, Files.readString(file) + "futureThing.count=1\nfutureThing.0.name=baseline-a\nfutureScalar=42\n");
        c.runbooks.clear();
        assertTrue(ProjectProfile.save(file, c, share), "content changed, so it writes");

        Properties after = load(file);
        assertEquals("baseline-a", after.getProperty("futureThing.0.name"), "unknown family preserved");
        assertEquals("1", after.getProperty("futureThing.count"));
        assertEquals("42", after.getProperty("futureScalar"));
        assertNull(after.getProperty("runbook.0.name"), "a family this build OWNS is rewritten: the removed runbook is gone");
        assertNull(after.getProperty("runbook.count"));
        assertEquals("src/main/java", after.getProperty("sourceRoot.0"), "and the owned content is what this build meant");

        assertFalse(ProjectProfile.save(file, c, share), "idempotent: preserved keys are stable, so a no-op edit is a no-op write");

        AppConfig back = new AppConfig();
        assertTrue(ProjectProfile.load(file, back, share).loaded(), "the loader still ignores what it does not know — ignore, never reject");
        assertTrue(back.runbooks.isEmpty());
    }

    @Test
    void theOwnSettingsWriterDoesTheSame(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("config");
        ConfigStore store = new ConfigStore(f);
        AppConfig c = new AppConfig();
        c.recentProjects.add("/p/one");
        c.recentProjects.add("/p/two");
        store.save(c);
        Files.writeString(f, Files.readString(f) + "newerSetting=1\nnewer.count=1\nnewer.0=a\n");
        c.recentProjects.remove("/p/two");
        store.save(c);
        Properties after = load(f);
        assertEquals("1", after.getProperty("newerSetting"));
        assertEquals("a", after.getProperty("newer.0"));
        assertNull(after.getProperty("recentProject.1"), "an owned list shrank and the stale member did not survive");
        assertEquals("/p/one", after.getProperty("recentProject.0"));
    }
}
