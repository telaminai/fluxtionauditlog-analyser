package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** M27.3 — named focuses persist with the GRAPHS category and follow the project tier. */
class NamedFocusTest {

    @TempDir
    Path dir;

    private static FocusSpec hedgePath() {
        return new FocusSpec("hedge path", "the subsystem behind the flicker finding",
                List.of("hedgeToOrdersNode", "buyTakerOrder", "sellTakerOrder"));
    }

    @Test
    void roundTripsThroughTheConfigStore() {
        AppConfig c = new AppConfig();
        c.namedFocuses.add(hedgePath());
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        store.save(c);
        AppConfig back = store.load();
        assertEquals(List.of(hedgePath()), back.namedFocuses);
    }

    @Test
    void ridesTheGraphsShareCategoryAndMergesByName() {
        SettingsShare share = new SettingsShare("/home/tester");
        AppConfig sender = new AppConfig();
        sender.namedFocuses.add(hedgePath());
        String text = share.export(sender, java.util.EnumSet.of(SettingsShare.Category.GRAPHS));

        AppConfig receiver = new AppConfig();
        receiver.namedFocuses.add(new FocusSpec("hedge path", "stale rationale", List.of("old")));
        receiver.namedFocuses.add(new FocusSpec("keep me", "", List.of("x")));
        var plan = share.preview(text, receiver);
        assertTrue(plan.present().contains(SettingsShare.Category.GRAPHS));
        share.apply(plan, java.util.EnumSet.of(SettingsShare.Category.GRAPHS), receiver);

        assertEquals(2, receiver.namedFocuses.size());
        FocusSpec merged = receiver.namedFocuses.stream()
                .filter(f -> f.name().equals("hedge path")).findFirst().orElseThrow();
        assertEquals(hedgePath(), merged, "replace-by-name, like graphs");
    }

    @Test
    void isProjectScoped_snapshotRestoreAndReplaceCarryIt() {
        AppConfig c = new AppConfig();
        c.namedFocuses.add(hedgePath());
        ProjectProfile.Snapshot snap = ProjectProfile.snapshot(c);
        ProjectProfile.clearProjectScoped(c);
        assertTrue(c.namedFocuses.isEmpty(), "named focuses are project-scoped and clear with the tier");
        ProjectProfile.restore(snap, c);
        assertEquals(List.of(hedgePath()), c.namedFocuses);
    }

    @Test
    void neverLeaksTheApiKey() throws Exception {
        AppConfig c = new AppConfig();
        c.apiKey = "sk-never";
        c.namedFocuses.add(hedgePath());
        Path file = ProjectProfile.pathFor(Files.createDirectory(dir.resolve("proj")));
        ProjectProfile.save(file, c, new SettingsShare("/home/tester"));
        String text = Files.readString(file);
        assertTrue(text.contains("hedge path"), "the focus is in the profile");
        assertFalse(text.contains("sk-never"));
        Properties p = new Properties();
        p.load(Files.newBufferedReader(file));
        assertEquals("1", p.getProperty("focus.count"));
    }
}
