package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectSession;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-M20-3 regression: with a project ACTIVE, a graph created through the DISPATCHER (the same path the
 * {@code graph} verb takes — {@code ActionExecutor.render}) must reach the project profile file, and
 * must NOT be written into the global tier. Before the fix, {@code config.savedGraphs} was only synced
 * at export/exit, so every profile flush wrote a stale (empty) graph list while the graphs themselves
 * leaked to the global file via the exit path — four of the owner's production graphs were lost this
 * way on 2026-08-17.
 */
class GraphTierPersistenceTest {

    @TempDir
    Path dir;

    private final SettingsShare share = new SettingsShare(System.getProperty("user.home"));

    /** Replicates MainFrame's wiring: change listener syncs tabs → config and marks the project dirty. */
    private static void wire(GraphTabs tabs, AppConfig config, ProjectSession session, HeapLogStore store) {
        Runnable sync = () -> {
            if (store != null) {
                config.savedGraphs.clear();
                config.savedGraphs.addAll(tabs.specs());
            }
        };
        tabs.setChangeListener(() -> {
            sync.run();
            session.requestSave();
        });
        session.setPreSave(sync);
    }

    @Test
    void dispatcherCreatedGraphReachesTheActiveProfileAndNotGlobal() throws Exception {
        // an active project
        Path projectDir = Files.createDirectory(dir.resolve("proj"));
        AppConfig config = new AppConfig();
        ProjectSession session = new ProjectSession(config, share, () -> { });
        session.create(ProjectProfile.pathFor(projectDir));
        assertTrue(session.hasProject());

        // the app surface the verb path drives
        HeapLogStore store = new HeapLogStore(Samples.sample());
        FilterState filter = new FilterState();
        GraphTabs tabs = new GraphTabs();
        tabs.bind(store, filter);
        LogTablePanel table = new LogTablePanel();
        ActionExecutor executor = new ActionExecutor(() -> store, () -> filter, tabs, table, (r, n, f) -> { });
        wire(tabs, config, session, store);

        // create a graph EXACTLY as the verb does
        var result = executor.render("graph", Map.of(
                "name", "tier test graph",
                "newTab", true,
                "series", List.of("bidMakerOrder.price")));
        assertTrue(result.ok(), () -> "graph action failed: " + result);

        // the change listener must have marked the project dirty; flush and inspect BOTH tiers
        assertTrue(session.isDirty(), "a dispatcher-created graph must mark the active project dirty");
        session.flush();
        String profile = Files.readString(session.activeFile());
        assertTrue(profile.contains("tier test graph"),
                "the active project profile must gain the dispatcher-created graph");

        Path globalFile = dir.resolve("global-config");
        new telamin.fluxtion.audit.analyser.analyser.config.ConfigStore(globalFile)
                .save(config, session.globalTier());
        String global = Files.readString(globalFile);
        assertFalse(global.contains("tier test graph"),
                "the global tier must NOT gain a graph created while a project is active");
    }

    @Test
    void staleConfigCanNeverBeFlushed_preSaveResyncsFromTheLiveTabs() throws Exception {
        Path projectDir = Files.createDirectory(dir.resolve("proj2"));
        AppConfig config = new AppConfig();
        ProjectSession session = new ProjectSession(config, share, () -> { });
        session.create(ProjectProfile.pathFor(projectDir));

        HeapLogStore store = new HeapLogStore(Samples.sample());
        GraphTabs tabs = new GraphTabs();
        tabs.bind(store, new FilterState());
        wire(tabs, config, session, store);

        tabs.graphForAction("safety net graph", true);
        config.savedGraphs.clear();          // simulate a missed sync — config is now stale
        session.requestSave();
        session.flush();                     // preSave must re-capture the live tabs

        assertTrue(Files.readString(session.activeFile()).contains("safety net graph"),
                "flush must never write a stale graph list — preSave re-syncs from the live tabs");
    }

    @Test
    void restoreDoesNotEchoPersistedStateBackAsAnEdit() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        GraphTabs tabs = new GraphTabs();
        tabs.bind(store, new FilterState());
        int[] fired = {0};
        // seed one saved graph through the live API, then count listener firings during restore
        tabs.graphForAction("seed", true);
        List<telamin.fluxtion.audit.analyser.analyser.config.GraphSpec> saved = tabs.specs();
        tabs.setChangeListener(() -> fired[0]++);
        tabs.restore(saved);
        assertEquals(0, fired[0], "rebuilding from persisted state is not a user edit");
    }
}
