package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.design.DesignDiscovery;
import telamin.fluxtion.audit.analyser.analyser.design.DesignEvents;
import telamin.fluxtion.audit.analyser.analyser.design.DesignFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Follow-up to #198 (review F2 and the deferred watched-log folders): an accepted profile reload replaces the project's
 * settings without being a session boundary. The views worked out from the old settings — Design discovery and the
 * project-output watch — are refreshed, and the open log and graph stay. Actual generated processor, recording adapter.
 */
class ProjectSettingsReplacedTest {

    static final String PROFILE = "/work/DEMO/profile";
    static final String KEY = "DEMO-key";

    static final class Session {
        final FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(PROFILE);
        final SessionDriver driver = LocalAgentSessionTest.driver(adapter);
        /** The source roots in force, as the adapter's discovery would read them. */
        List<String> roots = List.of();
        private int answered;

        Session open() {
            driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), PROFILE,
                    TransitionKind.EXPLICIT_SWITCH, "test"));
            return this;
        }

        <T> List<T> effects(Class<T> type) {
            return adapter.performed.stream().filter(type::isInstance).map(type::cast).toList();
        }

        /** Perform the discoveries the session asked for, as the frame does: scan the roots in force, report the result. */
        void discover() {
            var asked = effects(SessionEffects.DiscoverDesignEffect.class);
            for (var e : new ArrayList<>(asked.subList(answered, asked.size()))) {
                driver.post(new DesignEvents.DiscoveryCompleted(DesignDiscovery.scan(new DesignFiles(roots, null)),
                        e.generation()));
            }
            answered = asked.size();
        }

        /** The person (or {@code open {project, reload: true}}) takes the changed file: the session asks, the adapter reloads. */
        void reload() {
            driver.post(new SessionEvents.ProjectProfileObserved(KEY, "disk-new", "disk-old", false));
            driver.post(new SessionEvents.ProjectProfileChoice(KEY, SessionEvents.ProfileChoice.RELOAD, "socket"));
        }

        List<String> discovered() {
            return driver.processor().designSession.discovery().files().stream().map(DesignDiscovery.FileRef::path).toList();
        }
    }

    static Path definition(Path root) throws Exception {
        Files.createDirectories(root);
        return Files.writeString(root.resolve("design.xml"),
                "<beans><bean class='com.acme.FluxtionSpringConfig'/></beans>").toRealPath();
    }

    @Test
    @DisplayName("★ F2: a reload that moves the source root from A to B discovers B's definition, drops A's, and keeps the log and graph")
    void aTwoRootReloadRediscoversAndKeepsTheLogAndGraph(@TempDir Path tmp) throws Exception {
        Path a = definition(tmp.resolve("root-a"));
        Path b = definition(tmp.resolve("root-b"));
        Session s = new Session().open();
        s.roots = List.of(tmp.resolve("root-a").toString());
        s.discover();
        assertEquals(List.of(a.toString()), s.discovered(), "control: A's definition is discovered");
        long before = s.driver.processor().designSession.discoveryGeneration();
        s.driver.submit(new SessionEvents.OpenLogRequested(s.driver.nextOpId(), "/logs/DEMO.yaml", null, "DECLARED", false));
        s.driver.post(new SessionEvents.GraphOpened("/graphs/DEMO.graphml", "OPENED", Set.of("rootNode"), List.of("EventLogManager")));
        assertEquals("/logs/DEMO.yaml", s.driver.processor().openLog.logPath(), "control: the log is open");

        s.roots = List.of(tmp.resolve("root-b").toString());          // the file on disk now names root B
        s.reload();
        s.discover();

        assertTrue(s.driver.processor().designSession.discoveryGeneration() > before,
                "★ the reload requests fresh discovery (red before: the old-root candidates were kept)");
        assertEquals(List.of(b.toString()), s.discovered(), "★ B's definition is discovered and A's is gone");
        assertEquals("/logs/DEMO.yaml", s.driver.processor().openLog.logPath(), "★ the log stays open: a reload is not a switch");
        assertEquals("/graphs/DEMO.graphml", s.driver.processor().openGraph.graphPath(), "★ and so does the graph");
        assertTrue(s.effects(SessionEffects.CloseLogEffect.class).isEmpty(), "nothing was closed");
        assertTrue(s.effects(SessionEffects.CloseGraphEffect.class).isEmpty(), "nothing was closed");

        // a discovery that began before the reload completes late: it is withheld, never shown as current
        s.driver.post(new DesignEvents.DiscoveryCompleted(DesignDiscovery.scan(new DesignFiles(List.of(tmp.resolve("root-a").toString()), null)), before));
        assertEquals(List.of(b.toString()), s.discovered(), "★ a stale completion from before the reload is withheld");
    }

    @Test
    @DisplayName("★ F2: a reload that leaves the source roots as they were keeps the discovered candidates")
    void aReloadThatKeepsTheRootsKeepsDiscovery() {
        Session s = new Session().open();
        s.adapter.replacedBasis = new String[]{"roots-1", "roots-1", "logs-1", "logs-1"};
        long before = s.driver.processor().designSession.discoveryGeneration();
        int asked = s.effects(SessionEffects.DiscoverDesignEffect.class).size();

        s.reload();

        assertEquals(1, s.effects(SessionEffects.DescribeSettingsReplacementEffect.class).size(), "the session asked what was replaced");
        assertEquals(before, s.driver.processor().designSession.discoveryGeneration(), "★ same roots: discovery is still current");
        assertEquals(asked, s.effects(SessionEffects.DiscoverDesignEffect.class).size(), "and no scan is asked for");
        assertEquals(1, s.effects(SessionEffects.ObserveProjectOutputsEffect.class).size(), "★ same log folders: the watch stays");
    }

    @Test
    @DisplayName("★ a replacement that is not the answer to the newest reload of this project is withheld")
    void aStaleReplacementIsWithheld() {
        Session s = new Session().open();
        s.adapter.replacedBasis = new String[]{"roots-1", "roots-1", "logs-1", "logs-1"};
        s.reload();
        var asked = s.effects(SessionEffects.DescribeSettingsReplacementEffect.class).getLast();
        long before = s.driver.processor().designSession.discoveryGeneration();

        // an answer with another ticket, the answered ticket again, another profile: none of them is current
        s.driver.post(new SessionEvents.ProjectSettingsReplaced(0, asked.ticket() + 1, KEY, "a", "b", "c", "d"));
        s.driver.post(new SessionEvents.ProjectSettingsReplaced(0, asked.ticket(), KEY, "a", "b", "c", "d"));
        s.driver.post(new SessionEvents.ProjectSettingsReplaced(0, asked.ticket(), "DEMO-other", "a", "b", "c", "d"));
        assertEquals(before, s.driver.processor().designSession.discoveryGeneration(), "★ withheld: nothing is invalidated");

        // a project switch between the request and its answer: the answer is about the project that was left
        s.adapter.describePending = true;              // the adapter has not answered yet
        s.reload();
        long ticket = s.effects(SessionEffects.DescribeSettingsReplacementEffect.class).getLast().ticket();
        long during = s.driver.processor().designSession.discoveryGeneration();
        s.adapter.withProfile("/work/DEMO/other");
        s.driver.submit(new SessionEvents.OpenProjectRequested(s.driver.nextOpId(), "/work/DEMO/other", TransitionKind.EXPLICIT_SWITCH, "test"));
        long switched = s.driver.processor().designSession.discoveryGeneration();
        assertTrue(switched > during, "control: the switch itself starts discovery");
        s.driver.post(new SessionEvents.ProjectSettingsReplaced(0, ticket, KEY, null, null, null, null));
        assertEquals(switched, s.driver.processor().designSession.discoveryGeneration(), "★ a late answer from before a switch is withheld");
    }

    @Test
    @DisplayName("★ a failed reload replaced nothing: no question is asked and nothing is invalidated")
    void aFailedReloadReplacesNothing() {
        Session s = new Session().open();
        long before = s.driver.processor().designSession.discoveryGeneration();
        s.driver.post(new SessionEvents.ProjectProfileReloaded(0, KEY, false, "not reloaded", "socket"));
        assertTrue(s.effects(SessionEffects.DescribeSettingsReplacementEffect.class).isEmpty());
        assertEquals(before, s.driver.processor().designSession.discoveryGeneration());
    }

    static ProjectOutputsState.File log(long ref, String key, long revision) {
        return ProjectOutputsTest.file(ProjectOutputsState.Kind.LOG, ref, key, revision);
    }

    @Test
    @DisplayName("★ watched folders: a reload that changes the log folders reinstalls the watch — the new folder offers, the old one no longer does")
    void aReloadReinstallsTheOutputWatchForTheNewLogFolders() {
        Session s = new Session().open();
        long generation = s.driver.processor().activeProject.generation();
        var observed = s.effects(SessionEffects.ObserveProjectOutputsEffect.class);
        assertEquals(1, observed.size(), "control: the open project is observed once");
        long watch = observed.getFirst().watch();
        s.driver.post(new SessionEvents.ProjectOutputsBasisObserved(0, watch, generation, 10, false, List.of(20L)));
        var first = s.effects(SessionEffects.WatchProjectOutputsEffect.class).getLast();
        assertEquals(List.of(20L), first.logDirs());
        var graph = ProjectOutputsTest.graph(1);
        var oldLog = log(51, "logs-a/DEMO-old", 1);
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 0, List.of(graph, oldLog), true, first.basis()));
        var rebuilt = ProjectOutputsTest.graph(2);       // a rebuilt graph, settled: offered before the reload
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 4000, List.of(rebuilt, oldLog), true, first.basis()));
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 8000, List.of(rebuilt, oldLog), true, first.basis()));
        var state = s.driver.processor().projectOutputs.state();
        assertEquals(1, state.offers().size(), "control: the rebuilt graph is offered");
        long graphOffer = state.offers().getFirst().id();

        s.adapter.replacedBasis = new String[]{"roots-1", "roots-1", "logs-a", "logs-b"};
        s.reload();

        assertEquals(2, s.effects(SessionEffects.ObserveProjectOutputsEffect.class).size(),
                "★ the reload asks for the watch basis again (red before: the old folders stayed watched)");
        assertEquals(watch, s.effects(SessionEffects.ObserveProjectOutputsEffect.class).getLast().watch(),
                "the same watch: its offers and their answers stay valid");
        s.driver.post(new SessionEvents.ProjectOutputsBasisObserved(0, watch, generation, 10, false, List.of(21L)));
        var second = s.effects(SessionEffects.WatchProjectOutputsEffect.class).getLast();
        assertEquals(List.of(21L), second.logDirs(), "★ the watch now observes the new log folder");
        assertNotEquals(first.basis(), second.basis(), "under a new basis");
        assertEquals(List.of(graphOffer), s.driver.processor().projectOutputs.state().offers().stream()
                .map(ProjectOutputsState.Offer::id).toList(), "★ the pending offer is kept across the reinstall");

        // a scan begun under the old folders completes late, with a changed log there: withheld
        var oldRewritten = log(51, "logs-a/DEMO-old", 2);
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 12000, List.of(rebuilt, oldRewritten), true, first.basis()));
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 16000, List.of(rebuilt, oldRewritten), true, first.basis()));
        assertEquals(1, s.driver.processor().projectOutputs.state().offers().size(), "★ the old folder no longer produces offers");

        // the reinstalled watch: what is already in the new folder is its baseline; a log written there after is offered
        var existing = log(61, "logs-b/DEMO-yesterday", 1);
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 20000, List.of(rebuilt, existing), true, second.basis()));
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 24000, List.of(rebuilt, existing), true, second.basis()));
        assertEquals(1, s.driver.processor().projectOutputs.state().offers().size(), "a log already in the new folder is not a new output");
        var fresh = log(62, "logs-b/DEMO-new", 1);
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 28000, List.of(rebuilt, existing, fresh), true, second.basis()));
        s.driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, 32000, List.of(rebuilt, existing, fresh), true, second.basis()));
        var offers = s.driver.processor().projectOutputs.state().offers();
        assertEquals(2, offers.size(), "★ a log written to the new folder is offered: " + offers);
        assertEquals("logs-b/DEMO-new", offers.getLast().file().pathKey());
        assertEquals(graphOffer, offers.getFirst().id(), "and the graph's offer is the same one");
        assertTrue(s.driver.processor().projectOutputs.permitsWatch(watch, generation), "the watch is current");
    }

    @Test
    @DisplayName("★ a bundle working copy's reload refreshes discovery and keeps the bundle's provenance and anchor")
    void aBundleWorkingCopyReloadKeepsOwnership() {
        var plan = new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
                "/bundle/log.yaml", "sha256:DEMO-identity", "/bundle", "DEMO limits", "",
                "/demo/evidence.fexp", null, "DEMO-profile-digest");
        var adapter = new FakeSessionAdapter().withProfile(plan.profilePath());
        var driver = new SessionDriver(adapter);
        long id = driver.nextOpId();
        driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp", TransitionKind.OPEN_BUNDLE, "start"));
        driver.submit(new SessionEvents.ProfileLoaded(id, plan.profilePath(), true, null, 0, null, plan));
        assertTrue(driver.snapshot().bundle().fromBundle(), "control: a bundle is in force");
        long before = driver.processor().designSession.discoveryGeneration();
        int remembered = adapter.anchorRemembers.size();

        driver.post(new SessionEvents.ProjectProfileObserved(KEY, "disk-new", "disk-old", false));
        driver.post(new SessionEvents.ProjectProfileChoice(KEY, SessionEvents.ProfileChoice.RELOAD, "socket"));

        assertTrue(driver.processor().designSession.discoveryGeneration() > before, "★ discovery is refreshed for the working copy too");
        assertTrue(driver.snapshot().bundle().fromBundle(), "★ the bundle is still the session's provenance");
        assertEquals("sha256:DEMO-identity", driver.snapshot().bundle().identity());
        assertEquals("/demo/evidence.fexp", driver.snapshot().bundle().source());
        assertEquals(remembered, adapter.anchorRemembers.size(), "★ a reload is not the person choosing roots for the bundle");
        assertTrue(adapter.performed.stream().noneMatch(SessionEffects.ObserveProjectOutputsEffect.class::isInstance),
                "a bundle's working copy is never watched for outputs, reload or not");
    }
}
