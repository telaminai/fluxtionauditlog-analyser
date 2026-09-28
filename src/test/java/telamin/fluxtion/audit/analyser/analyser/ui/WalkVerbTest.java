package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S4 (spec-spotlight-walks.md §3.9, §3.10; W-A7, W-A9, W-A10, W-A17): the {@code walk} verb. One operation per call,
 * refused whole before anything changes; saves go through the one save path and say who saved them; delete, rename
 * and restore are the bin's; play and end REPORT to the session and read back what it published. Headless.
 */
class WalkVerbTest {

    static final class Rig implements WalkVerb.Frame, WalkAuthoring.Frame {
        final AppConfig config = new AppConfig();
        LogStore store = new HeapLogStore(Samples.sample());
        final FilterState filter = new FilterState();
        final GraphTabs graphs = new GraphTabs();
        final TopologyPanel topology = new TopologyPanel();
        final List<Object> posted = new ArrayList<>();
        WalkPlaybackState state = WalkPlaybackState.IDLE;
        /** What the session publishes when a play request arrives: by default, it starts the walk. */
        boolean sessionStarts = true;
        List<String> run = List.of("sha256:run");
        int persisted;
        final WalkAuthoring authoring = new WalkAuthoring(this);

        Rig() {
            graphs.bind(store, filter);
        }

        // WalkVerb.Frame
        public AppConfig config() { return config; }
        public WalkAuthoring authoring() { return authoring; }
        public boolean logOpen() { return store != null; }
        public List<String> runBasisNow() { return run; }
        public void persist() { persisted++; }
        public void post(Object fact) {
            posted.add(fact);
            if (fact instanceof SessionEvents.WalkPlayRequested p) {
                state = sessionStarts
                        ? new WalkPlaybackState(p.walk().name(), p.step(), p.walk().steps().size(), "SHOWN", "", 1,
                                List.of(new SessionEvents.WalkTargetState(1, "status", "", "CURRENT", true, "")), Map.of(), p.walk())
                        : new WalkPlaybackState(null, 0, 0, "IDLE", "the session refused it", 0, List.of(), Map.of(), null);
            }
        }
        public WalkPlaybackState state() { return state; }

        // WalkAuthoring.Frame
        public LogStore store() { return store; }
        public FilterState filter() { return filter; }
        public GraphTabs graphs() { return graphs; }
        public TopologyPanel topology() { return topology; }
        public String selectedTabWord() { return null; }
        public int selectedRecord() { return -1; }
        public List<SpotlightOverlay.Lit> lit() { return List.of(); }
        public LogFingerprint fingerprint() { return null; }
        public long generation() { return 1; }
    }

    private static List<Object> steps(String... targets) {
        List<Object> out = new ArrayList<>();
        for (String t : targets) out.add(Map.of("caption", "look", "targets", List.of(Map.of("target", t, "caption", "here"))));
        return out;
    }

    private static ActionResult save(Rig rig, String name, String... targets) {
        return new WalkVerb(rig).run(Map.of("name", name, "steps", steps(targets)));
    }

    @Test
    @DisplayName("W-A7: two operations, or a field of another operation, are refused before anything changes")
    void oneOperationPerCall() {
        Rig rig = new Rig();
        assertTrue(save(rig, "w", "status").ok());
        int before = rig.persisted;
        ActionResult both = new WalkVerb(rig).run(Map.of("name", "w", "steps", steps("status"), "delete", true));
        assertFalse(both.ok(), "steps beside delete is two instructions");
        assertTrue(both.error().contains("ONE operation"), both.error());
        ActionResult alien = new WalkVerb(rig).run(Map.of("name", "w", "delete", true, "title", "x"));
        assertFalse(alien.ok(), "delete does not take a title");
        assertTrue(alien.error().contains("[title]"), alien.error());
        ActionResult none = new WalkVerb(rig).run(Map.of("name", "w"));
        assertFalse(none.ok(), "a name alone is no operation");
        assertEquals(1, rig.config.walks.size(), "nothing was deleted");
        assertEquals(before, rig.persisted, "and nothing was stored");
    }

    @Test
    @DisplayName("W-A7: a step naming a field outside the view allow-list is refused at save, naming it")
    void anUnknownViewFieldIsRefused() {
        Rig rig = new Rig();
        ActionResult r = new WalkVerb(rig).run(Map.of("name", "w", "steps",
                List.of(Map.of("view", Map.of("window", 5), "targets", List.of("status")))));
        assertFalse(r.ok());
        assertTrue(r.error().contains("view.window"), r.error());
        assertTrue(rig.config.walks.isEmpty());
    }

    @Test
    @DisplayName("a verb save is bound by the analyser and attributed to the assistant — never 'you'")
    void aSaveIsBoundAndAttributed() {
        Rig rig = new Rig();
        ActionResult r = save(rig, "w", "records:row:1", "status");
        assertTrue(r.ok(), String.valueOf(r.error()));
        WalkSpec w = rig.config.walks.get(0);
        assertEquals(WalkSpec.AUTHOR_ASSISTANT, w.author());
        assertFalse(w.steps().get(0).targets().get(0).basis().digest().isBlank(), "the analyser digested the record");
        assertEquals(List.of("sha256:run"), w.runBasis());
        assertEquals(1, rig.persisted, "stored once, through the edit funnel");
        assertNotEquals("you", r.payload().get("author"));
        assertEquals(false, r.payload().get("replaced"));
        assertEquals(true, save(rig, "w", "status").payload().get("replaced"), "saving the same name replaces it");
    }

    @Test
    @DisplayName("§3.9: a step pointing at records needs a log; a structural walk saves without one")
    void observationalStepsNeedALog() {
        Rig rig = new Rig();
        rig.store = null;
        ActionResult refused = save(rig, "w", "records:row:1");
        assertFalse(refused.ok());
        assertTrue(refused.error().contains("open the log first"), refused.error());
        assertTrue(rig.config.walks.isEmpty());
        assertTrue(save(rig, "tabs", "tab:topology").ok(), "a walk of tabs needs no log");
    }

    @Test
    @DisplayName("W-A9: delete moves to the bin, restore brings it back, rename keeps the steps; down to an empty bin")
    void theBin() {
        Rig rig = new Rig();
        save(rig, "w", "status", "tab:topology");
        WalkVerb v = new WalkVerb(rig);
        assertTrue(v.run(Map.of("name", "w", "delete", true)).ok());
        assertTrue(rig.config.walks.isEmpty());
        assertEquals(List.of("w"), v.run(Map.of("restore", true)).payload().get("restorable"));
        assertTrue(v.run(Map.of("restore", "w")).ok());
        assertEquals(1, rig.config.walks.size());
        assertEquals(List.of(), v.run(Map.of("restore", true)).payload().get("restorable"), "the bin is empty again");
        assertTrue(v.run(Map.of("name", "w", "rename", "tour")).ok());
        assertEquals("tour", rig.config.walks.get(0).name());
        assertEquals(2, rig.config.walks.get(0).steps().size(), "a rename keeps the steps");
        assertFalse(v.run(Map.of("name", "nope", "delete", true)).ok());
    }

    @Test
    @DisplayName("review PR57 R6: a delete or rename is REPORTED to the session — the verb no longer decides to end a walk")
    void deletingTheShowingWalkEndsIt() {
        Rig rig = new Rig();
        save(rig, "w", "status");
        WalkVerb v = new WalkVerb(rig);
        assertTrue(v.run(Map.of("name", "w", "play", true)).ok());
        rig.posted.clear();
        v.run(Map.of("name", "w", "delete", true));
        assertEquals(List.of(new SessionEvents.WalkDefinitionChanged("w", null, null)), rig.posted,
                "the delete is reported as it happened, and nothing else is decided here: " + rig.posted);
        save(rig, "x", "status");
        rig.posted.clear();
        new WalkVerb(rig).run(Map.of("name", "x", "rename", "y"));
        assertEquals(List.of(new SessionEvents.WalkDefinitionChanged("x", null, "y")), rig.posted,
                "so is a rename, whether or not that walk is showing");
    }

    @Test
    @DisplayName("play reports the request (step counted from 1) and replies with what the session published")
    void playReportsAndReadsBack() {
        Rig rig = new Rig();
        save(rig, "w", "status", "status", "status");
        WalkVerb v = new WalkVerb(rig);
        ActionResult r = v.run(Map.of("name", "w", "play", true, "step", 2));
        assertTrue(r.ok(), String.valueOf(r.error()));
        var request = (SessionEvents.WalkPlayRequested) rig.posted.get(rig.posted.size() - 1);
        assertEquals(1, request.step(), "step 2, counted from 1, is index 1");
        assertEquals(3, request.walk().steps().size(), "the play fact carries the whole definition");
        assertEquals(2, r.payload().get("step"));
        assertFalse(v.run(Map.of("name", "w", "play", true, "step", 0)).ok(), "there is no step 0");
        // review PR57 R9: 2^32 + 2 must not narrow to step 2
        ActionResult wrapped = v.run(Map.of("name", "w", "play", true, "step", 4294967298L));
        assertFalse(wrapped.ok(), "a step number beyond the supported range is refused, not narrowed");
        assertTrue(wrapped.error().contains("'step'"), wrapped.error());
        assertFalse(v.run(Map.of("name", "nope", "play", true)).ok());

        rig.sessionStarts = false;
        rig.state = WalkPlaybackState.IDLE;
        ActionResult refused = v.run(Map.of("name", "w", "play", true));
        assertFalse(refused.ok(), "the session decides; a play it refused is not reported as playing");
        assertTrue(refused.error().contains("the session refused it"), refused.error());
    }

    @Test
    @DisplayName("end asks the session to end a showing walk, and says when none was showing")
    void end() {
        Rig rig = new Rig();
        WalkVerb v = new WalkVerb(rig);
        assertEquals(false, v.run(Map.of("end", true)).payload().get("ended"));
        assertTrue(rig.posted.isEmpty(), "nothing to end, nothing reported");
        save(rig, "w", "status");
        v.run(Map.of("name", "w", "play", true));
        assertEquals(true, v.run(Map.of("end", true)).payload().get("ended"));
        assertTrue(rig.posted.get(rig.posted.size() - 1) instanceof SessionEvents.WalkEndRequested);
    }

    @Test
    @DisplayName("§3.10: context.walks lists each walk with its author and warning, and the showing step's states")
    @SuppressWarnings("unchecked")
    void context() {
        Rig rig = new Rig();
        assertNull(WalkVerb.context(rig.config, WalkPlaybackState.IDLE, true, rig.run, "own settings"),
                "no walks, nothing showing: nothing to say");
        save(rig, "w", "records:row:1");
        Map<String, Object> same = WalkVerb.context(rig.config, WalkPlaybackState.IDLE, true, rig.run, "own settings");
        Map<String, Object> one = ((List<Map<String, Object>>) same.get("saved")).get(0);
        assertEquals("assistant", one.get("author"));
        assertEquals(1, one.get("steps"));
        assertNull(one.get("warning"), "the same run: nothing to warn about");
        Map<String, Object> other = WalkVerb.context(rig.config, WalkPlaybackState.IDLE, true, List.of("sha256:other"), "own settings");
        String warning = (String) ((List<Map<String, Object>>) other.get("saved")).get(0).get("warning");
        assertNotNull(warning, "a record walk played against another run says so");
        assertTrue(warning.contains("different run"), warning);

        new WalkVerb(rig).run(Map.of("name", "w", "play", true));
        Map<String, Object> showing = (Map<String, Object>) WalkVerb.context(rig.config, rig.state, true, rig.run, "own settings").get("showing");
        assertEquals("w", showing.get("walk"));
        assertEquals(1, showing.get("step"));
        assertEquals("CURRENT", ((List<Map<String, Object>>) showing.get("targets")).get(0).get("state"),
                "context reports the states the strip shows — the published ones");
    }

    @Test
    @DisplayName("§3.9 read identity: saving is a records read; playing and the bin are not")
    void readIdentityPolicyPerOperation() {
        assertTrue(telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcherAccess.readsRecords("walk", Map.of("steps", List.of())));
        assertFalse(telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcherAccess.readsRecords("walk", Map.of("play", true)));
        assertFalse(telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcherAccess.readsRecords("walk", Map.of("restore", true)));
    }
}
